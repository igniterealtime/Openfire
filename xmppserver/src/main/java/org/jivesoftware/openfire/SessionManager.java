/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.jivesoftware.openfire.audit.AuditStreamIDFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.http.HttpConnection;
import org.jivesoftware.openfire.http.HttpSession;
import org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Manages the sessions associated with an account. The information
 * maintained by the Session manager is entirely transient and does
 * not need to be preserved between server restarts.
 *
 * @author Derek DeMoro
 */
public class SessionManager extends BasicModule implements ClusterEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(SessionManager.class);
    private static final SystemProperty<Integer> CONFLICT_LIMIT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.session.conflict-limit")
        .setDynamic(true)
        .setDefaultValue(0)
        .setMinValue(-1)
        .build();

    public static final String COMPONENT_SESSION_CACHE_NAME = "Components Sessions";
    public static final String CM_CACHE_NAME = "Connection Managers Sessions";
    public static final String ISS_CACHE_NAME = "Incoming Server Sessions";
    public static final String HOSTNAME_SESSIONS_CACHE_NAME = "Sessions by Hostname";
    public static final String VALIDATED_DOMAINS_CACHE_NAME = "Validated Domains";
    public static final String C2S_INFO_CACHE_NAME = "Client Session Info Cache";

    public static final int NEVER_KICK = -1;

    private XMPPServer server;
    private PacketRouter router;
    private String serverName;
    private JID serverAddress;
    private UserManager userManager;
    private int conflictLimit;

    /**
     * Counter of user connections. A connection is counted just after it was created and not
     * after the user became available. This counter only considers sessions local to this JVM.
     * That means that when running inside of a cluster you will need to add up this counter
     * for each cluster node.
     */
    private final AtomicInteger connectionsCounter = new AtomicInteger(0);

    /**
     * Cache (unlimited, never expire) that holds information about client sessions (as soon as
     * a resource has been bound). The cache is used by Remote sessions to avoid generating big
     * number of remote calls.
     * Key: full JID, Value: ClientSessionInfo
     *
     * Note that, unlike other caches, this cache is populated only when clustering is enabled.
     */
    private Cache<String, ClientSessionInfo> sessionInfoCache;

    /**
     * Cache (unlimited, never expire) that holds external component sessions.
     * Key: component address, Value: identifier of each cluster node holding a local session
     * to the component.
     */
    private Cache<String, HashSet<NodeID>> componentSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds sessions of connection managers. For each
     * socket connection of the CM to the server there is going to be an entry in the cache.
     * Key: full address of the CM that identifies the socket, Value: nodeID
     */
    private Cache<String, NodeID> multiplexerSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds incoming sessions of remote servers.
     * Key: stream ID that identifies the socket/session, Value: nodeID
     */
    private Cache<StreamID, NodeID> incomingServerSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds list of incoming sessions
     * originated from the same remote server (domain/subdomain). For instance, jabber.org
     * may have 2 connections to the server running in jivesoftware.com (one socket to
     * jivesoftware.com and the other socket to conference.jivesoftware.com).
     * Key: remote hostname (domain/subdomain), Value: list of stream IDs that identify each socket.
     */
    private Cache<String, ArrayList<StreamID>> hostnameSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds domains, subdomains and virtual
     * hostnames of the remote server that were validated with this server for each
     * incoming server session.
     * Key: stream ID, Value: Domains and subdomains of the remote server that were
     * validated with this server.<p>
     *
     * This same information is stored in {@link LocalIncomingServerSession} but the
     * reason for this duplication is that when running in a cluster other nodes
     * will have access to this clustered cache even in the case of this node going
     * down.
     */
    private Cache<StreamID, HashSet<String>> validatedDomainsCache;

    private ClientSessionListener clientSessionListener = new ClientSessionListener();
    private IncomingServerSessionListener incomingServerListener = new IncomingServerSessionListener();
    private OutgoingServerSessionListener outgoingServerListener = new OutgoingServerSessionListener();
    private ConnectionMultiplexerSessionListener multiplexerSessionListener = new ConnectionMultiplexerSessionListener();

    /**
     * Sessions contained in this Map are (client?) sessions which are detached.
     * Sessions remaining here too long will be reaped, but they will be checked
     * to see if they have in fact resumed since.
     */
    private final Map<StreamID, LocalSession> detachedSessions = new ConcurrentHashMap<>();

    /**
     * Local session manager responsible for keeping sessions connected to this JVM that are not
     * present in the routing table.
     */
    private LocalSessionManager localSessionManager;
    /**
     * <p>Session manager must maintain the routing table as sessions are added and
     * removed.</p>
     */
    private RoutingTable routingTable;

    private StreamIDFactory streamIDFactory;

    /**
     * Returns the instance of <CODE>SessionManagerImpl</CODE> being used by the XMPPServer.
     *
     * @return the instance of <CODE>SessionManagerImpl</CODE> being used by the XMPPServer.
     */
    public static SessionManager getInstance() {
        return XMPPServer.getInstance().getSessionManager();
    }

    public SessionManager() {
        super("Session Manager");
        if (JiveGlobals.getBooleanProperty("xmpp.audit.active")) {
            streamIDFactory = new AuditStreamIDFactory();
        }
        else {
            streamIDFactory = new BasicStreamIDFactory();
        }
        localSessionManager = new LocalSessionManager();
        conflictLimit = CONFLICT_LIMIT.getValue();
    }

    /**
     * Record a session as being detached (ie, has no connection). This is idempotent.
     * This should really only be called by the LocalSession itself when it detaches.
     *
     * @param localSession the LocalSession (this) to mark as detached.
     */
    public void addDetached(LocalSession localSession) {
        Log.trace( "Marking session '{}' ({}) as detached.", localSession.getAddress(), localSession.getStreamID() );
        this.detachedSessions.put(localSession.getStreamID(), localSession);
    }

    /**
     * Checks if a session is currently in the detached state (ie, has no connection,
     * but has not been formally closed yet either).
     *
     * @param localSession A session
     * @return true if the session is currently in 'detached' state, otherwise 'false'.
     */
    public boolean isDetached(LocalSession localSession) {
        return this.detachedSessions.containsKey(localSession.getStreamID());
    }

    /**
     * Remove a session as being detached. This is idempotent.
     * This should be called by the LocalSession itself either when resumed or when
     * closed.
     *
     * @param localSession the LocalSession (this) which has been resumed or closed.
     */
    public synchronized void removeDetached(LocalSession localSession) {
        LocalSession other = this.detachedSessions.get(localSession.getStreamID());
        if (other == localSession) {
            Log.trace( "Removing detached session '{}' ({}).", localSession.getAddress(), localSession.getStreamID() );
            this.detachedSessions.remove(localSession.getStreamID());
        }
    }

    /**
     * Returns the session originated from the specified address or {@code null} if none was
     * found. The specified address MUST contain a resource that uniquely identifies the session.
     *
     * A single connection manager should connect to the same node.
     *
     * @param address the address of the connection manager (including resource that identifies specific socket)
     * @return the session originated from the specified address.
     */
    public ConnectionMultiplexerSession getConnectionMultiplexerSession(JID address) {
        // Search in the list of CMs connected to this JVM
        LocalConnectionMultiplexerSession session =
                localSessionManager.getConnnectionManagerSessions().get(address.toString());
        if (session == null && server.getRemoteSessionLocator() != null) {
            // Search in the list of CMs connected to other cluster members
            final NodeID nodeID = multiplexerSessionsCache.get(address.toString());
            if (nodeID != null) {
                return server.getRemoteSessionLocator().getConnectionMultiplexerSession(nodeID.toByteArray(), address);
            }
        }
        return null;
    }

    /**
     * Returns all sessions originated from connection managers.
     *
     * @return all sessions originated from connection managers.
     */
    public List<ConnectionMultiplexerSession> getConnectionMultiplexerSessions() {
        List<ConnectionMultiplexerSession> sessions = new ArrayList<>();
        // Add sessions of CMs connected to this JVM
        sessions.addAll(localSessionManager.getConnnectionManagerSessions().values());
        // Add sessions of CMs connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            for (Map.Entry<String, NodeID> entry : multiplexerSessionsCache.entrySet()) {
                if (!server.getNodeID().equals(entry.getValue())) {
                    sessions.add(locator.getConnectionMultiplexerSession(entry.getValue().toByteArray(), new JID(entry.getKey())));
                }
            }
        }
        return sessions;
    }

    /**
     * Returns a collection with all the sessions originated from the connection manager
     * whose domain matches the specified domain. If there is no connection manager with
     * the specified domain then an empty list is going to be returned.
     *
     * @param domain the domain of the connection manager.
     * @return a collection with all the sessions originated from the connection manager
     *         whose domain matches the specified domain.
     */
    public List<ConnectionMultiplexerSession> getConnectionMultiplexerSessions(String domain) {
        List<ConnectionMultiplexerSession> sessions = new ArrayList<>();
        // Add sessions of CMs connected to this JVM
        for (String address : localSessionManager.getConnnectionManagerSessions().keySet()) {
            JID jid = new JID(address);
            if (domain.equals(jid.getDomain())) {
                sessions.add(localSessionManager.getConnnectionManagerSessions().get(address));
            }
        }
        // Add sessions of CMs connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            for (Map.Entry<String, NodeID> entry : multiplexerSessionsCache.entrySet()) {
                if (!server.getNodeID().equals(entry.getValue())) {
                    JID jid = new JID(entry.getKey());
                    if (domain.equals(jid.getDomain())) {
                        sessions.add(
                                locator.getConnectionMultiplexerSession(entry.getValue().toByteArray(), new JID(entry.getKey())));
                    }
                }
            }
        }
        return sessions;
    }

    /**
     * Creates a new {@code ConnectionMultiplexerSession}.
     *
     * @param conn the connection to create the session from.
     * @param address the JID (may include a resource) of the connection manager's session.
     * @return a newly created session.
     */
    public LocalConnectionMultiplexerSession createMultiplexerSession(Connection conn, JID address) {
        if (serverName == null) {
            throw new IllegalStateException("Server not initialized");
        }
        StreamID id = nextStreamID();
        LocalConnectionMultiplexerSession session = new LocalConnectionMultiplexerSession(serverName, conn, id);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // figure out when users that were using this connection manager may become unavailable
        conn.registerCloseListener(multiplexerSessionListener, session);

        // Add to connection multiplexer session.
        boolean firstConnection = getConnectionMultiplexerSessions(address.getDomain()).isEmpty();
        localSessionManager.getConnnectionManagerSessions().put(address.toString(), session);
        // Keep track of the cluster node hosting the new CM connection
        multiplexerSessionsCache.put(address.toString(), server.getNodeID());
        if (firstConnection) {
            // Notify ConnectionMultiplexerManager that a new connection manager
            // is available
            ConnectionMultiplexerManager.getInstance().multiplexerAvailable(address.getDomain());
        }
        return session;
    }

    /**
     * Returns a randomly created ID to be used in a stream element.
     *
     * @return a randomly created ID to be used in a stream element.
     */
    public StreamID nextStreamID() {
        return streamIDFactory.createStreamID();
    }

    /**
     * Creates a new {@code ClientSession}. The new Client session will have a newly created
     * stream ID.
     *
     * @param conn the connection to create the session from.
     * @param language The language to use for the new session.
     * @return a newly created session.
     */
    public LocalClientSession createClientSession(Connection conn, Locale language) {
        return createClientSession(conn, nextStreamID(), language);
    }

    /**
     * Creates a new {@code ClientSession} with the specified streamID.
     *
     * @param conn the connection to create the session from.
     * @param id the streamID to use for the new session.
     * @return a newly created session.
     */
    public LocalClientSession createClientSession(Connection conn, StreamID id) {
        return createClientSession( conn, id, null);
    }

    /**
     * Creates a new {@code ClientSession} with the specified streamID.
     *
     * @param conn the connection to create the session from.
     * @param id the streamID to use for the new session.
     * @param language The language to use for the new session.
     * @return a newly created session.
     */
    public LocalClientSession createClientSession(Connection conn, StreamID id, Locale language) {
        if (serverName == null) {
            throw new IllegalStateException("Server not initialized");
        }
        LocalClientSession session = new LocalClientSession(serverName, conn, id, language);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove  and also send an unavailable presence if it wasn't
        // sent before
        conn.registerCloseListener(clientSessionListener, session);

        // Add to pre-authenticated sessions.
        localSessionManager.getPreAuthenticatedSessions().put(session.getAddress().getResource(), session);
        // Increment the counter of user sessions
        connectionsCounter.incrementAndGet();
        return session;
    }

    /**
     * Creates a new {@code ClientSession} with the specified streamID.
     *
     * @param connection the connection to create the session from.
     * @param id the streamID to use for the new session.
     * @param language The language to use for the session
     * @return a newly created session.
     * @throws UnauthorizedException if the server has not been initialised
     * @throws UnknownHostException if no IP address for the peer could be found,
     */
    public HttpSession createClientHttpSession(StreamID id, HttpConnection connection, Locale language)
        throws UnauthorizedException, UnknownHostException
    {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        PacketDeliverer backupDeliverer = server.getPacketDeliverer();
        HttpSession session = new HttpSession(backupDeliverer, serverName, id, connection, language);
        Connection conn = session.getConnection();
        conn.init(session);
        conn.registerCloseListener(clientSessionListener, session);
        localSessionManager.getPreAuthenticatedSessions().put(session.getAddress().getResource(), session);
        connectionsCounter.incrementAndGet();
        return session;
    }

    public LocalComponentSession createComponentSession(JID address, Connection conn) {
        if (serverName == null) {
            throw new IllegalStateException("Server not initialized");
        }
        StreamID id = nextStreamID();
        LocalComponentSession session = new LocalComponentSession(serverName, conn, id);
        conn.init(session);

        // Set the bind address as the address of the session
        session.setAddress(address);

        // Add to component session.
        localSessionManager.getComponentsSessions().add(session);

        // Keep track of the cluster node hosting the new external component
        CacheUtil.addValueToMultiValuedCache( componentSessionsCache, address.toString(), server.getNodeID(), HashSet::new );

        return session;
    }

    public void removeComponentSession( LocalComponentSession session )
    {
        // Remove the session
        localSessionManager.getComponentsSessions().remove(session);

        // Remove track of the cluster node hosting the external component.
        CacheUtil.removeValueFromMultiValuedCache( componentSessionsCache, session.getAddress().toString(), server.getNodeID() );
    }

    /**
     * Creates a session for a remote server. The session should be created only after the
     * remote server has been authenticated either using "server dialback" or SASL.
     *
     * @param conn the connection to the remote server.
     * @param id the stream ID used in the stream element when authenticating the server.
     * @param fromDomain The originating domain
     * @return the newly created {@link IncomingServerSession}.
     * @throws UnauthorizedException if the local server has not been initialized yet.
     */
    public LocalIncomingServerSession createIncomingServerSession(Connection conn, StreamID id, String fromDomain)
            throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        LocalIncomingServerSession session = new LocalIncomingServerSession(serverName, conn, id, fromDomain);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove its route from the sessions set
        conn.registerCloseListener(incomingServerListener, session);

        return session;
    }

    /**
     * Notification message that a new OutgoingServerSession has been created. Register a listener
     * that will react when the connection gets closed.
     *
     * @param session the newly created OutgoingServerSession.
     */
    public void outgoingServerSessionCreated(LocalOutgoingServerSession session) {
        // Register to receive close notification on this session so we can
        // remove its route from the sessions set
        session.getConnection().registerCloseListener(outgoingServerListener, session);
    }

    /**
     * Registers that a server session originated by a remote server is hosting a given hostname.
     * Notice that the remote server may be hosting several subdomains as well as virtual hosts so
     * the same IncomingServerSession may be associated with many keys. If the remote server
     * creates many sessions to this server (eg. one for each subdomain) then associate all
     * the sessions with the originating server that created all the sessions.
     *
     * @param hostname the hostname that is being served by the remote server.
     * @param session the incoming server session to the remote server.
     */
    public void registerIncomingServerSession(String hostname, LocalIncomingServerSession session) {
        // Keep local track of the incoming server session connected to this JVM
        StreamID streamID = session.getStreamID();
        localSessionManager.addIncomingServerSessions(streamID, session);
        // Keep track of the nodeID hosting the incoming server session
        incomingServerSessionsCache.put(streamID, server.getNodeID());
        // Update list of sockets/sessions coming from the same remote hostname
        Lock lock = hostnameSessionsCache.getLock(hostname);
        lock.lock();
        try {
            ArrayList<StreamID> streamIDs = hostnameSessionsCache.get(hostname);
            if (streamIDs == null) {
                streamIDs = new ArrayList<>();
            }
            streamIDs.add(streamID);
            hostnameSessionsCache.put(hostname, streamIDs);
        }
        finally {
            lock.unlock();
        }
        // Add to clustered cache
        lock = validatedDomainsCache.getLock(streamID);
        lock.lock();
        try {
            HashSet<String> validatedDomains = validatedDomainsCache.get(streamID);
            if (validatedDomains == null) {
                validatedDomains = new HashSet<>();
            }
            boolean added = validatedDomains.add(hostname);
            if (added) {
                validatedDomainsCache.put(streamID, validatedDomains);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Unregisters the specified remote server session originiated by the specified remote server.
     *
     * @param hostname the hostname that is being served by the remote server.
     * @param session the session to unregiser.
     */
    public void unregisterIncomingServerSession(String hostname, IncomingServerSession session) {
        // Remove local track of the incoming server session connected to this JVM
        StreamID streamID = session.getStreamID();
        localSessionManager.removeIncomingServerSessions(streamID);
        // Remove track of the nodeID hosting the incoming server session
        incomingServerSessionsCache.remove(streamID);

        unregisterIncomingServerSession( Collections.singleton( streamID ) );
    }

    /**
     * One or more incoming server session can become unavailable for a number of reasons:
     * <ul>
     *     <li>It's connection got terminated.</li>
     *     <li>The cluster node on which it is connected become disconnected</li>
     * </ul>
     *
     * When a incoming server session is unavailable, a cleanup of associated
     * metadata is needed.
     *
     * This method removes metadata from the following caches, based on the
     * stream identifiers of incoming server sessions:
     * <ul>
     *     <li>'sockets/sessions coming from the same remote hostname'</li>
     *     <li>'validated domains'</li>
     * </ul>
     *
     * @param streamIDs References to incoming server sessions that are no longer available (cannot be null, can be empty).
     */
    private void unregisterIncomingServerSession( final Collection<StreamID> streamIDs )
    {
        // Update the collection of 'sockets/sessions coming from the same remote hostname' as well as the collection of 'validated domains' to reflect the loss of incoming server sessions.
        for ( final StreamID streamID : streamIDs )
        {
            final Map<Boolean, Map<String, ArrayList<StreamID>>> modifiedHostnameSessions = CacheUtil.removeValueFromMultiValuedCache( hostnameSessionsCache, streamID );
            final Set<String> removedHostnameSessions = modifiedHostnameSessions.get( false ).keySet();
            removedHostnameSessions.forEach( removedHostname -> CacheUtil.removeValueFromMultiValuedCache( validatedDomainsCache, removedHostname ) );
        }
    }

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * validated. The remote server is allowed to send packets from any of these domains,
     * subdomains and virtual hosts.<p>
     *
     * Content is stored in a clustered cache so that even in the case of the node hosting
     * the sessions is lost we can still have access to this info to be able to perform
     * proper clean up logic.
     *
     * @param streamID id that uniquely identifies the session.
     * @return domains, subdomains and virtual hosts that where validated.
     */
    public Collection<String> getValidatedDomains(StreamID streamID) {
        Lock lock = validatedDomainsCache.getLock(streamID);
        lock.lock();
        try {
            Set<String> validatedDomains = validatedDomainsCache.get(streamID);
            if (validatedDomains == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableCollection(validatedDomains);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Add a new session to be managed. The session has been authenticated and resource
     * binding has been done.
     *
     * @param session the session that was authenticated.
     */
    public void addSession(LocalClientSession session) {
        // Add session to the routing table (routing table will know session is not available yet)
        routingTable.addClientRoute(session.getAddress(), session);
        // Remove the pre-Authenticated session but remember to use the temporary ID as the key
        localSessionManager.getPreAuthenticatedSessions().remove(session.getStreamID().toString());
        SessionEventDispatcher.EventType event = session.getAuthToken().isAnonymous() ?
                SessionEventDispatcher.EventType.anonymous_session_created :
                SessionEventDispatcher.EventType.session_created;
        // Fire session created event.
        SessionEventDispatcher.dispatchEvent(session, event);
        if (ClusterManager.isClusteringStarted()) {
            // Track information about the session and share it with other cluster nodes
            sessionInfoCache.put(session.getAddress().toString(), new ClientSessionInfo(session));
        }
    }

    /**
     * Notification message sent when a client sent an available presence for the session. Making
     * the session available means that the session is now eligible for receiving messages from
     * other clients. Sessions whose presence is not available may only receive packets (IQ packets)
     * from the server. Therefore, an unavailable session remains invisible to other clients.
     *
     * @param session the session that receieved an available presence.
     * @param presence the presence for the session.
     */
    public void sessionAvailable(LocalClientSession session, Presence presence) {
        if (session.getAuthToken().isAnonymous()) {
            // Anonymous session always have resources so we only need to add one route. That is
            // the route to the anonymous session
            routingTable.addClientRoute(session.getAddress(), session);
        }
        else {
            // A non-anonymous session is now available
            // Add route to the new session
            routingTable.addClientRoute(session.getAddress(), session);
            // Broadcast presence between the user's resources
            broadcastPresenceOfOtherResource(session);
        }
    }

    /**
     * Returns true if the session should broadcast presences to all other resources for the
     * current client. When disabled it is not possible to broadcast presence packets to another
     * resource of the connected user. This is desirable if you have a use case where you have
     * many resources attached to the same user account.
     *
     * @return true if presence should be broadcast to other resources of the same account
     */
    public static boolean isOtherResourcePresenceEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.client.other-resource.presence", true);
    }

    /**
     * Sends the presences of other connected resources to the resource that just connected.
     *
     * @param session the newly created session.
     */
    private void broadcastPresenceOfOtherResource(LocalClientSession session) {
        if (!SessionManager.isOtherResourcePresenceEnabled()) {
            return;
        }
        Presence presence;
        // Get list of sessions of the same user
        JID searchJID = new JID(session.getAddress().getNode(), session.getAddress().getDomain(), null);
        List<JID> addresses = routingTable.getRoutes(searchJID, null);
        for (JID address : addresses) {
            if (address.equals(session.getAddress())) {
                continue;
            }
            // Send the presence of an existing session to the session that has just changed
            // the presence
            ClientSession userSession = routingTable.getClientRoute(address);
            presence = userSession.getPresence().createCopy();
            presence.setTo(session.getAddress());
            session.process(presence);
        }
    }

    /**
     * @param originatingResource The JID broadcasting the presence
     * @param presence The presence to broadcast
     * @deprecated Use {@link #broadcastPresenceToResources(JID, Presence)} instead.
     */
    @Deprecated
    public void broadcastPresenceToOtherResources(JID originatingResource, Presence presence)
    {
        broadcastPresenceToResources(originatingResource, presence);
    }

    /**
     * Broadcasts presence updates from the originating user's resource to any of the user's
     * existing available resources (including the resource from where the update originates).
     *
     * @param originatingResource the full JID of the session that sent the presence update.
     * @param presence the presence.
     */
    public void broadcastPresenceToResources( JID originatingResource, Presence presence) {
        // RFC 6121 4.4.2 says we always send to the originating resource.
        // Also RFC 6121 4.2.2 for updates.
        presence.setTo(originatingResource);
        routingTable.routePacket(originatingResource, presence, false);
        if (!SessionManager.isOtherResourcePresenceEnabled()) {
            return;
        }
        // Get list of sessions of the same user
        JID searchJID = new JID(originatingResource.getNode(), originatingResource.getDomain(), null);
        List<JID> addresses = routingTable.getRoutes(searchJID, null);
        for (JID address : addresses) {
            if (!originatingResource.equals(address)) {
                // Send the presence of the session whose presence has changed to
                // this user's other session(s)
                presence.setTo(address);
                routingTable.routePacket(address, presence, false);
            }
        }
    }

    /**
     * Notification message sent when a client sent an unavailable presence for the session. Making
     * the session unavailable means that the session is not eligible for receiving messages from
     * other clients.
     *
     * @param session the session that received an unavailable presence.
     */
    public void sessionUnavailable(LocalClientSession session) {
        if (session.getAddress() != null && routingTable != null &&
                session.getAddress().toBareJID().trim().length() != 0) {
            // Update route to unavailable session (anonymous or not)
            routingTable.removeClientRoute(session.getAddress());
        }
    }

    /**
     * Change the priority of a session, that was already available, associated with the sender.
     *
     * @param session   The session whose presence priority has been modified
     * @param oldPriority The old priority for the session
     */
    public void changePriority(LocalClientSession session, int oldPriority) {
        if (session.getAuthToken().isAnonymous()) {
            // Do nothing if the session belongs to an anonymous user
            return;
        }
        int newPriority = session.getPresence().getPriority();
        if (newPriority < 0 || oldPriority >= 0) {
            // Do nothing if new presence priority is not positive and old presence negative
            return;
        }

        // Check presence's priority of other available resources
        JID searchJID = session.getAddress().asBareJID();
        for (JID address : routingTable.getRoutes(searchJID, null)) {
            if (address.equals(session.getAddress())) {
                continue;
            }
            ClientSession otherSession = routingTable.getClientRoute(address);
            if (otherSession.getPresence().getPriority() >= 0) {
                return;
            }
        }

        // User sessions had negative presence before this change so deliver messages
        if (!session.isAnonymousUser() && session.canFloodOfflineMessages()) {
            OfflineMessageStore messageStore = server.getOfflineMessageStore();
            Collection<OfflineMessage> messages = messageStore.getMessages(session.getAuthToken().getUsername(), true);
            for (Message message : messages) {
                session.process(message);
            }
        }
    }

    public boolean isAnonymousRoute(String username) {
        // JID's node and resource are the same for anonymous sessions
        return isAnonymousRoute(new JID(username, serverName, username, true));
    }

    public boolean isAnonymousRoute(JID address) {
        // JID's node and resource are the same for anonymous sessions
        return routingTable.isAnonymousRoute(address);
    }

    public boolean isActiveRoute(String username, String resource) {
        boolean hasRoute = false;
        Session session = routingTable.getClientRoute(new JID(username, serverName, resource));
        // Makes sure the session is still active
        if (session != null && !session.isClosed()) {
            hasRoute = session.validate();
        }

        return hasRoute;
    }

    /**
     * Returns the session responsible for this JID data. The returned Session may have never sent
     * an available presence (thus not have a route) or could be a Session that hasn't
     * authenticated yet (i.e. preAuthenticatedSessions).
     *
     * @param from the sender of the packet.
     * @return the <code>Session</code> associated with the JID.
     */
    public ClientSession getSession(JID from) {
        // Return null if the JID is null or belongs to a foreign server. If the server is
        // shutting down then serverName will be null so answer null too in this case.
        if (from == null || serverName == null || !serverName.equals(from.getDomain())) {
            return null;
        }

        // Initially Check preAuthenticated Sessions
        if (from.getResource() != null) {
            ClientSession session = localSessionManager.getPreAuthenticatedSessions().get(from.getResource());
            if (session != null) {
                return session;
            }
        }

        if (from.getResource() == null || from.getNode() == null) {
            return null;
        }

        return routingTable.getClientRoute(from);
    }

    /**
     * Returns a list that contains all authenticated client sessions connected to the server.
     * The list contains sessions of anonymous and non-anonymous users.
     *
     * @return a list that contains all client sessions connected to the server.
     */
    public Collection<ClientSession> getSessions() {
        return routingTable.getClientsRoutes(false);
    }


    public Collection<ClientSession> getSessions(SessionResultFilter filter) {
        List<ClientSession> results = new ArrayList<>();
        if (filter != null) {
            // Grab all the matching sessions
            results.addAll(getSessions());

            // Now we have a copy of the references so we can spend some time
            // doing the rest of the filtering without locking out session access
            // so let's iterate and filter each session one by one
            List<ClientSession> filteredResults = new ArrayList<>();
            for (ClientSession session : results) {
                // Now filter on creation date if needed
                filteredResults.add(session);
            }

            // Sort list.
            Collections.sort(filteredResults, filter.getSortComparator());

            int maxResults = filter.getNumResults();
            if (maxResults == SessionResultFilter.NO_RESULT_LIMIT) {
                maxResults = filteredResults.size();
            }

            // Now generate the final list. I believe it's faster to to build up a new
            // list than it is to remove items from head and tail of the sorted tree
            List<ClientSession> finalResults = new ArrayList<>();
            int startIndex = filter.getStartIndex();
            Iterator<ClientSession> sortedIter = filteredResults.iterator();
            for (int i = 0; sortedIter.hasNext() && finalResults.size() < maxResults; i++) {
                ClientSession result = sortedIter.next();
                if (i >= startIndex) {
                    finalResults.add(result);
                }
            }
            return finalResults;
        }
        return results;
    }

    /**
     * Returns the incoming server session hosted by this JVM that matches the specified stream ID.
     *
     * @param streamID the stream ID that identifies the incoming server session hosted by this JVM.
     * @return the incoming server session hosted by this JVM or null if none was found.
     */
    public LocalIncomingServerSession getIncomingServerSession(StreamID streamID) {
        return localSessionManager.getIncomingServerSession(streamID);
    }

    /**
     * Returns the list of sessions that were originated by a remote server. The list will be
     * ordered chronologically.  IncomingServerSession can only receive packets from the remote
     * server but are not capable of sending packets to the remote server.
     *
     * @param hostname the name of the remote server.
     * @return the sessions that were originated by a remote server.
     */
    public List<IncomingServerSession> getIncomingServerSessions(String hostname) {
        List<StreamID> streamIDs;
        // Get list of sockets/sessions coming from the remote hostname
        Lock lock = hostnameSessionsCache.getLock(hostname);
        lock.lock();
        try {
            streamIDs = hostnameSessionsCache.get(hostname);
        }
        finally {
            lock.unlock();
        }

        if (streamIDs == null) {
            return Collections.emptyList();
        }
        else {
            // Collect the sessions associated to the found stream IDs
            List<IncomingServerSession> sessions = new ArrayList<>();
            for (StreamID streamID : streamIDs) {
                // Search in local hosted sessions
                IncomingServerSession session = localSessionManager.getIncomingServerSession(streamID);
                RemoteSessionLocator locator = server.getRemoteSessionLocator();
                if (session == null && locator != null) {
                    // Get the node hosting this session
                    NodeID nodeID = incomingServerSessionsCache.get(streamID);
                    if (nodeID != null) {
                        session = locator.getIncomingServerSession(nodeID.toByteArray(), streamID);
                    }
                }
                if (session != null) {
                    sessions.add(session);
                }
            }
            return sessions;
        }
    }

    /**
     * Returns a session that was originated from this server to a remote server.
     * OutgoingServerSession an only send packets to the remote server but are not capable of
     * receiving packets from the remote server.
     *
     * @param pair DomainPair describing the local and remote servers.
     * @return a session that was originated from this server to a remote server.
     */
    public OutgoingServerSession getOutgoingServerSession(DomainPair pair) {
        return routingTable.getServerRoute(pair);
    }
    public List<OutgoingServerSession> getOutgoingServerSessions(String host) {
        List<OutgoingServerSession> sessions = new LinkedList<>();
        for (DomainPair pair : routingTable.getServerRoutes()) {
            if (pair.getRemote().equals(host)) {
                sessions.add(routingTable.getServerRoute(pair));
            }
        }
        return sessions;
    }

    public Collection<ClientSession> getSessions(String username) {
        List<ClientSession> sessionList = new ArrayList<>();
        if (username != null && serverName != null) {
            List<JID> addresses = routingTable.getRoutes(new JID(username, serverName, null, true), null);
            for (JID address : addresses) {
                sessionList.add(routingTable.getClientRoute(address));
            }
        }
        return sessionList;
    }

    /**
     * Returns number of client sessions that are connected to the server. Sessions that
     * are authenticated and not authenticated will be included
     *
     * @param onlyLocal true if only sessions connected to this JVM will be considered. Otherwise count cluster wise.
     * @return number of client sessions that are connected to the server.
     */
    public int getConnectionsCount(boolean onlyLocal) {
        int total = connectionsCounter.get();
        if (!onlyLocal) {
            Collection<Integer> results =
                    CacheFactory.doSynchronousClusterTask(new GetSessionsCountTask(false), false);
            for (Integer result : results) {
                if (result == null) {
                    continue;
                }
                total = total + result;
            }
        }
        return total;
    }

    /**
     * Returns number of client sessions that are authenticated with the server. This includes
     * anonymous and non-anoymous users.
     *
     * @param onlyLocal true if only sessions connected to this JVM will be considered. Otherwise count cluster wise.
     * @return number of client sessions that are authenticated with the server.
     */
    public int getUserSessionsCount(boolean onlyLocal) {
        int total = routingTable.getClientsRoutes(true).size();
        if (!onlyLocal) {
            Collection<Integer> results =
                    CacheFactory.doSynchronousClusterTask(new GetSessionsCountTask(true), false);
            for (Integer result : results) {
                if (result == null) {
                    continue;
                }
                total = total + result;
            }
        }
        return total;
    }

    /**
     * Returns number of sessions coming from remote servers. <i>Current implementation is only counting
     * sessions connected to this JVM and not adding up sessions connected to other cluster nodes.</i>
     *
     * @param onlyLocal true if only sessions connected to this JVM will be considered. Otherwise count cluster wise.
     * @return number of sessions coming from remote servers.
     */
    public int getIncomingServerSessionsCount(boolean onlyLocal) {
        int total = localSessionManager.getIncomingServerSessions().size();
        if (!onlyLocal) {
            // TODO Implement this when needed
        }
        return total;
    }

    /**
     * Returns the number of sessions for a user that are available. For the count
     * of all sessions for the user, including sessions that are just starting
     * or closed.
     *
     * @see #getConnectionsCount(boolean)
     * @param username the user.
     * @return number of available sessions for a user.
     */
    public int getActiveSessionCount(String username) {
        return routingTable.getRoutes(new JID(username, serverName, null, true), null).size();
    }

    public int getSessionCount(String username) {
        // TODO Count ALL sessions not only available
        return routingTable.getRoutes(new JID(username, serverName, null, true), null).size();
    }

    /**
     * Returns a collection with the established sessions from external components.
     *
     * @return a collection with the established sessions from external components.
     */
    public Collection<ComponentSession> getComponentSessions() {
        List<ComponentSession> sessions = new ArrayList<>();
        // Add sessions of external components connected to this JVM
        sessions.addAll(localSessionManager.getComponentsSessions());
        // Add sessions of external components connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            for (Map.Entry<String, HashSet<NodeID>> entry : componentSessionsCache.entrySet()) {
                for (NodeID nodeID : entry.getValue()) {
                    if (!server.getNodeID().equals(nodeID)) {
                        sessions.add(locator.getComponentSession(nodeID.toByteArray(), new JID(entry.getKey())));
                    }
                }
            }
        }
        return sessions;
    }

    /**
     * Returns the session of the component whose domain matches the specified domain.
     *
     * @param domain the domain of the component session to look for.
     * @return the session of the component whose domain matches the specified domain.
     */
    public ComponentSession getComponentSession(String domain) {
        // Search in the external components connected to this JVM
        for (ComponentSession session : localSessionManager.getComponentsSessions()) {
            if (domain.equals(session.getAddress().getDomain())) {
                return session;
            }
        }
        // Search in the external components connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            Set<NodeID> nodeIDs = componentSessionsCache.get(domain);
            if (nodeIDs != null) {
                for (NodeID nodeID : nodeIDs ) {
                    // TODO Think of a better way to pick a component.
                    return locator.getComponentSession( nodeID.toByteArray(), new JID(domain) );
                }
            }
        }
        return null;
    }

    /**
     * Returns a collection with the hostnames of the remote servers that currently have an
     * incoming server connection to this server.
     *
     * @return a collection with the hostnames of the remote servers that currently have an
     *         incoming server connection to this server.
     */
    public Collection<String> getIncomingServers() {
        return hostnameSessionsCache.keySet();
    }

    /**
     * Returns a collection with the hostnames of the remote servers that currently may receive
     * packets sent from this server.
     *
     * @return a collection with the hostnames of the remote servers that currently may receive
     *         packets sent from this server.
     */
    public Collection<String> getOutgoingServers() {
        return routingTable.getServerHostnames();
    }
    public Collection<DomainPair> getOutgoingDomainPairs() {
        return routingTable.getServerRoutes();
    }

    /**
     * Broadcasts the given data to all connected sessions. Excellent
     * for server administration messages.
     *
     * @param packet the packet to be broadcast.
     */
    public void broadcast(Message packet) {
        routingTable.broadcastPacket(packet, false);
    }

    /**
     * Broadcasts the given data to all connected sessions for a particular
     * user. Excellent for updating all connected resources for users such as
     * roster pushes.
     *
     * @param username the user to send the boradcast to.
     * @param packet the packet to be broadcast.
     * @throws PacketException if a packet exception occurs.
     */
    public void userBroadcast(String username, Packet packet) throws PacketException {
        // TODO broadcast to ALL sessions of the user and not only available
        for (JID address : routingTable.getRoutes(new JID(username, serverName, null), null)) {
            packet.setTo(address);
            routingTable.routePacket(address, packet, true);
        }
    }

    /**
     * Removes a session.
     *
     * @param session the session.
     * @return true if the requested session was successfully removed.
     */
    public boolean removeSession(LocalClientSession session) {
        // Do nothing if session is null or if the server is shutting down. Note: When the server
        // is shutting down the serverName will be null.
        if (session == null || serverName == null) {
            return false;
        }

        AuthToken authToken = session.getAuthToken();
        // Consider session anonymous (for this matter) if we are closing a session that never authenticated
        boolean anonymous = authToken == null || authToken.isAnonymous();
        return removeSession(session, session.getAddress(), anonymous, false);
    }

    /**
     * Removes a session.
     *
     * @param session the session or null when session is derived from fullJID.
     * @param fullJID the address of the session.
     * @param anonymous true if the authenticated user is anonymous.
     * @param forceUnavailable true if an unavailable presence must be created and routed.
     * @return true if the requested session was successfully removed.
     */
    public boolean removeSession(ClientSession session, JID fullJID, boolean anonymous, boolean forceUnavailable) {
        // Do nothing if server is shutting down. Note: When the server
        // is shutting down the serverName will be null.
        if (serverName == null) {
            return false;
        }

        if (session == null) {
            session = getSession(fullJID);
        }

        // Remove route to the removed session (anonymous or not)
        boolean removed = routingTable.removeClientRoute(fullJID);

        if (removed) {
            // Fire session event.
            if (anonymous) {
                SessionEventDispatcher
                        .dispatchEvent(session, SessionEventDispatcher.EventType.anonymous_session_destroyed);
            }
            else {
                SessionEventDispatcher.dispatchEvent(session, SessionEventDispatcher.EventType.session_destroyed);

            }
        }

        // Remove the session from the pre-Authenticated sessions list (if present)
        boolean preauth_removed =
                localSessionManager.getPreAuthenticatedSessions().remove(fullJID.getResource()) != null;
        // If the user is still available then send an unavailable presence
        if (forceUnavailable || session.getPresence().isAvailable()) {
            Presence offline = new Presence();
            offline.setFrom(fullJID);
            offline.setTo(new JID(null, serverName, null, true));
            offline.setType(Presence.Type.unavailable);
            router.route(offline);
        }

        // Stop tracking information about the session and share it with other cluster nodes.
        // Note that, unlike other caches, this cache is populated only when clustering is enabled.
        sessionInfoCache.remove(fullJID.toString());

        if (removed || preauth_removed) {
            // Decrement the counter of user sessions
            connectionsCounter.decrementAndGet();
            return true;
        }
        return false;
    }

    public int getConflictKickLimit() {
        return conflictLimit;
    }

    /**
     * Returns the temporary keys used by the sessions that has not been authenticated yet. This
     * is an utility method useful for debugging situations.
     *
     * @return the temporary keys used by the sessions that has not been authenticated yet.
     */
    public Collection<String> getPreAuthenticatedKeys() {
        return localSessionManager.getPreAuthenticatedSessions().keySet();
    }

    /**
     * Returns true if the specified address belongs to a preauthenticated session. Preauthenticated
     * sessions are only available to the local cluster node when running inside of a cluster.
     *
     * @param address the address of the session.
     * @return true if the specified address belongs to a preauthenticated session.
     */
    public boolean isPreAuthenticatedSession(JID address) {
        return serverName.equals(address.getDomain()) &&
                localSessionManager.getPreAuthenticatedSessions().containsKey(address.getResource());
    }

    public void setConflictKickLimit(int limit) {
        conflictLimit = limit;
        CONFLICT_LIMIT.setValue(limit);
    }

    private class ClientSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        @Override
        public void onConnectionClose(Object handback) {
            try {
                LocalClientSession session = (LocalClientSession) handback;
                if (session.isDetached()) {
                    Log.debug("Closing session with address {} and streamID {} is detached already.", session.getAddress(), session.getStreamID());
                    return;
                }
                if (session.getStreamManager().getResume()) {
                    Log.debug("Closing session with address {} and streamID {} has SM enabled; detaching.", session.getAddress(), session.getStreamID());
                    session.setDetached();
                    return;
                } else {
                    Log.debug("Closing session with address {} and streamID {} does not have SM enabled.", session.getAddress(), session.getStreamID());
                }
                try {
                    if ((session.getPresence().isAvailable() || !session.wasAvailable()) &&
                            routingTable.hasClientRoute(session.getAddress())) {
                        // Send an unavailable presence to the user's subscribers
                        // Note: This gives us a chance to send an unavailable presence to the
                        // entities that the user sent directed presences
                        Presence presence = new Presence();
                        presence.setType(Presence.Type.unavailable);
                        presence.setFrom(session.getAddress());
                        router.route(presence);
                    }

                    session.getStreamManager().onClose(router, serverAddress);
                }
                finally {
                    // Remove the session
                    removeSession(session);
                }
            }
            catch (Exception e) {
                // Can't do anything about this problem...
                Log.error(LocaleUtils.getLocalizedString("admin.error.close"), e);
            }
        }
    }

    private class IncomingServerSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        @Override
        public void onConnectionClose(Object handback) {
            IncomingServerSession session = (IncomingServerSession)handback;
            // Remove all the hostnames that were registered for this server session
            for (String hostname : session.getValidatedDomains()) {
                unregisterIncomingServerSession(hostname, session);
            }
        }
    }

    private class OutgoingServerSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        @Override
        public void onConnectionClose(Object handback) {
            OutgoingServerSession session = (OutgoingServerSession)handback;
            // Remove all the hostnames that were registered for this server session
            for (DomainPair domainPair : session.getOutgoingDomainPairs()) {
                // Remove the route to the session using the hostname
                server.getRoutingTable().removeServerRoute(domainPair);
            }
        }
    }

    private class ConnectionMultiplexerSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        @Override
        public void onConnectionClose(Object handback) {
            ConnectionMultiplexerSession session = (ConnectionMultiplexerSession)handback;
            // Remove all the hostnames that were registered for this server session
            String domain = session.getAddress().getDomain();
            localSessionManager.getConnnectionManagerSessions().remove(session.getAddress().toString());
            // Remove track of the cluster node hosting the CM connection
            multiplexerSessionsCache.remove(session.getAddress().toString());
            if (getConnectionMultiplexerSessions(domain).isEmpty()) {
                // Terminate ClientSessions originated from this connection manager
                // that are still active since the connection manager has gone down
                ConnectionMultiplexerManager.getInstance().multiplexerUnavailable(domain);
            }
        }
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        router = server.getPacketRouter();
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        serverName = server.getServerInfo().getXMPPDomain();
        serverAddress = new JID(serverName);

        if (JiveGlobals.getBooleanProperty("xmpp.audit.active")) {
            streamIDFactory = new AuditStreamIDFactory();
        }
        else {
            streamIDFactory = new BasicStreamIDFactory();
        }

        // Initialize caches.
        componentSessionsCache = CacheFactory.createCache(COMPONENT_SESSION_CACHE_NAME);
        multiplexerSessionsCache = CacheFactory.createCache(CM_CACHE_NAME);
        incomingServerSessionsCache = CacheFactory.createCache(ISS_CACHE_NAME);
        hostnameSessionsCache = CacheFactory.createCache(HOSTNAME_SESSIONS_CACHE_NAME);
        validatedDomainsCache = CacheFactory.createCache(VALIDATED_DOMAINS_CACHE_NAME);
        sessionInfoCache = CacheFactory.createCache(C2S_INFO_CACHE_NAME);

        // Listen to cluster events
        ClusterManager.addListener(this);
    }


    /**
     * Sends a message with a given subject and body to all the active user sessions in the server.
     *
     * @param subject the subject to broadcast.
     * @param body    the body to broadcast.
     */
    public void sendServerMessage(String subject, String body) {
        sendServerMessage(null, subject, body);
    }

    /**
     * Sends a message with a given subject and body to one or more user sessions related to the
     * specified address. If address is null or the address's node is null then the message will be
     * sent to all the user sessions. But if the address includes a node but no resource then
     * the message will be sent to all the user sessions of the requested user (defined by the node).
     * Finally, if the address is a full JID then the message will be sent to the session associated
     * to the full JID. If no session is found then the message is not sent.
     *
     * @param address the address that defines the sessions that will receive the message.
     * @param subject the subject to broadcast.
     * @param body    the body to broadcast.
     */
    public void sendServerMessage(JID address, String subject, String body) {
        Message packet = createServerMessage(subject, body);
        if (address == null || address.getNode() == null) {
            // No address, or no node: broadcast to all active user sessions on the server.
            broadcast(packet);
        }
        else if (address.getResource() == null || address.getResource().length() < 1) {
            // Node, but no resource: broadcast to all active sessions for the user.
            userBroadcast(address.getNode(), packet);
        }
        else {
            // Full JID: address to the session, if one exists.
            for (JID sessionAddress : routingTable.getRoutes(address, null)) {
                packet.setTo(sessionAddress); // expected to be equal to 'address'.
                routingTable.routePacket(sessionAddress, packet, true);
            }
        }
    }

    private Message createServerMessage(String subject, String body) {
        Message message = new Message();
        message.setFrom(serverAddress);
        if (subject != null) {
            message.setSubject(subject);
        }
        message.setBody(body);
        return message;
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        localSessionManager.start();

        // Run through the server sessions every 10% of the time of the maximum time that a session is allowed to be
        // detached, or every 3 minutes if the max time is outside the default boundaries.
        // TODO Reschedule task if getSessionDetachTime value changes.
        final int max = getSessionDetachTime();
        final long period;
        if ( max > Duration.ofMinutes(1).toMillis() && max < Duration.ofHours(1).toMillis() ) {
            period = max / 10;
        } else {
            period = Duration.ofMinutes(3).toMillis();
        }
        TaskEngine.getInstance().scheduleAtFixedRate(new DetachedCleanupTask(), period, period);
    }

    @Override
    public void stop() {
        Log.debug("SessionManager: Stopping server");
        // Stop threads that are sending packets to remote servers
        OutgoingSessionPromise.getInstance().shutdown();
        if (JiveGlobals.getBooleanProperty("shutdownMessage.enabled")) {
            sendServerMessage(null, LocaleUtils.getLocalizedString("admin.shutdown.now"));
        }
        localSessionManager.stop();
        serverName = null;

        try
        {
            // Purge our own components from the cache for the benefit of other cluster nodes.
            CacheUtil.removeValueFromMultiValuedCache( componentSessionsCache, XMPPServer.getInstance().getNodeID() );
        }
        catch ( Exception e )
        {
            Log.warn( "An exception occurred while trying to remove locally connected external components from the clustered cache. Other cluster nodes might continue to see our external components, even though we this instance is stopping.", e );
        }
    }

    /**
     * Returns true if remote servers are allowed to have more than one connection to this
     * server. Having more than one connection may improve number of packets that can be
     * transfered per second. This setting only used by the server dialback mehod.<p>
     *
     * It is highly recommended that {@link #getServerSessionTimeout()} is enabled so that
     * dead connections to this server can be easily discarded.
     *
     * @return true if remote servers are allowed to have more than one connection to this
     *         server.
     */
    public boolean isMultipleServerConnectionsAllowed() {
        return JiveGlobals.getBooleanProperty("xmpp.server.session.allowmultiple", true);
    }

    /**
     * Sets if remote servers are allowed to have more than one connection to this
     * server. Having more than one connection may improve number of packets that can be
     * transfered per second. This setting only used by the server dialback mehod.<p>
     *
     * It is highly recommended that {@link #getServerSessionTimeout()} is enabled so that
     * dead connections to this server can be easily discarded.
     *
     * @param allowed true if remote servers are allowed to have more than one connection to this
     *        server.
     */
    public void setMultipleServerConnectionsAllowed(boolean allowed) {
        JiveGlobals.setProperty("xmpp.server.session.allowmultiple", Boolean.toString(allowed));
        if (allowed && JiveGlobals.getIntProperty("xmpp.server.session.idle", 10 * 60 * 1000) <= 0)
        {
            Log.warn("Allowing multiple S2S connections for each domain, without setting a " +
                    "maximum idle timeout for these connections, is unrecommended! Either " +
                    "set xmpp.server.session.allowmultiple to 'false' or change " +
                    "xmpp.server.session.idle to a (large) positive value.");
        }
    }

    /******************************************************
     * Clean up code
     *****************************************************/
    /**
     * Sets the number of milliseconds to elapse between clearing of idle server sessions.
     *
     * @param timeout the number of milliseconds to elapse between clearings.
     */
    public void setServerSessionTimeout(int timeout) {
        if (getServerSessionTimeout() == timeout) {
            return;
        }
        // Set the new property value
        JiveGlobals.setProperty("xmpp.server.session.timeout", Integer.toString(timeout));
    }

    /**
     * Returns the number of milliseconds to elapse between clearing of idle server sessions.
     *
     * @return the number of milliseconds to elapse between clearing of idle server sessions.
     */
    public int getServerSessionTimeout() {
        return JiveGlobals.getIntProperty("xmpp.server.session.timeout", 5 * 60 * 1000);
    }

    public void setServerSessionIdleTime(int idleTime) {
        if (getServerSessionIdleTime() == idleTime) {
            return;
        }
        // Set the new property value
        JiveGlobals.setProperty("xmpp.server.session.idle", Integer.toString(idleTime));

        if (idleTime <= 0 && isMultipleServerConnectionsAllowed() )
        {
            Log.warn("Allowing multiple S2S connections for each domain, without setting a " +
                "maximum idle timeout for these connections, is unrecommended! Either " +
                "set xmpp.server.session.allowmultiple to 'false' or change " +
                "xmpp.server.session.idle to a (large) positive value.");
        }
    }

    public int getServerSessionIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.server.session.idle", 10 * 60 * 1000);
    }

    public void setSessionDetachTime(int idleTime) {
        if (getSessionDetachTime() == idleTime) {
            return;
        }
        // Set the new property value
        JiveGlobals.setProperty("xmpp.session.detach.timeout", Integer.toString(idleTime));
    }

    public int getSessionDetachTime() {
        return JiveGlobals.getIntProperty("xmpp.session.detach.timeout", 10 * 60 * 1000);
    }

    // Note that, unlike other caches, this cache is populated only when clustering is enabled.
    public Cache<String, ClientSessionInfo> getSessionInfoCache() {
        return sessionInfoCache;
    }

    @Override
    public void joinedCluster()
    {
        // The local node joined a cluster.
        //
        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // It does not appear to be needed to invoke any kind of event listeners for the data that was gained by joining
        // the cluster (eg: sessions connected to other cluster nodes, now suddenly available to the local cluster node):
        // There are six caches in play here, but only the content of one of them goes accompanied by firing off event
        // listeners (sessionInfoCache). However, when already running in a clustered environment, those events are
        // never broadcasted over the cluster, so there shouldn't be a need to do so for all sessions that were
        // gained/lost when joining or leaving a cluster either.
    }

    @Override
    public void joinedCluster(byte[] nodeID) {
        // Another node joined a cluster that we're already part of. It is expected that
        // the implementation of #joinedCluster() as executed on the cluster node that just
        // joined will synchronize all relevant data. This method need not do anything.
    }

    @Override
    public void leftCluster() {
        // The local cluster node left the cluster.
        if (XMPPServer.getInstance().isShuttingDown()) {
            // Do not put effort in restoring the correct state if we're shutting down anyway.
            return;
        }

        // Upon leaving a cluster, clustered caches are reset to their local equivalent (by the swap from the clustered
        // cache implementation to the default cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.leftCluster). This means that they now hold no data (as a new cache
        // has been created). Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // It does not appear to be needed to invoke any kind of event listeners for the data that was lost by leaving
        // the cluster (eg: sessions connected to other cluster nodes, now unavailable to the local cluster node):
        // There are six caches in play here, but only the content of one of them goes accompanied by firing off event
        // listeners (sessionInfoCache). However, when already running in a clustered environment, those events are
        // never broadcasted over the cluster, so there shouldn't be a need to do so for all sessions that were
        // gained/lost when joining or leaving a cluster either.
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        // Another node left the cluster.
        //
        // If the cluster node leaves in an orderly fashion, it might have broadcasted
        // the necessary events itself. This cannot be depended on, as the cluster node
        // might have disconnected unexpectedly (as a result of a crash or network issue).
        //
        // Determine what components were available only on that node, and remove them.
        // All remaining cluster nodes will be in a race to clean up the
        // same data. The implementation below accounts for that, by only having the
        // senior cluster node to perform the cleanup.
        if (!ClusterManager.isSeniorClusterMember())
        {
            return;
        }

        CacheUtil.removeValueFromMultiValuedCache(componentSessionsCache, NodeID.getInstance(nodeID));
        CacheUtil.removeValueFromCache(multiplexerSessionsCache, NodeID.getInstance(nodeID));
        final Set<StreamID> removedStreamIDs = CacheUtil.removeValueFromCache(incomingServerSessionsCache, NodeID.getInstance(nodeID));

        // Update the collection of 'sockets/sessions coming from the same remote hostname' as well as the collection of 'validated domains' to reflect the loss of incoming server sessions.
        if ( !removedStreamIDs.isEmpty() )
        {
            Log.debug("Cluster node {} just left the cluster, and was the node where incoming server sessions with IDs '{}' were living. They are no longer available.",
                      NodeID.getInstance(nodeID),
                      String.join(", ", removedStreamIDs.stream().map(StreamID::getID).collect(Collectors.toSet())));
            unregisterIncomingServerSession(removedStreamIDs);
        }

        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<String, ClientSessionInfo>> entries = Collections.unmodifiableSet(sessionInfoCache.entrySet() );
        for ( final Map.Entry<String, ClientSessionInfo> entry : entries )
        {
            if (entry.getValue().getNodeID().equals( NodeID.getInstance(nodeID) )) {
                sessionInfoCache.remove(entry.getKey());
            }
        }
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining ({@link #joinedCluster()} or leaving
     * ({@link #leftCluster()} a cluster.
     */
    private void restoreCacheContent()
    {
        if (ClusterManager.isClusteringStarted()) {
            Log.trace( "Restoring cache content for cache '{}' by adding all client sessions that are connected to the local cluster node.", sessionInfoCache.getName() );
            routingTable.getClientsRoutes(true).forEach( session -> sessionInfoCache.put(session.getAddress().toString(), new ClientSessionInfo( (LocalClientSession) session ) ) );
        }

        Log.trace( "Restoring cache content for cache '{}' by adding all component sessions that are connected to the local cluster node.", componentSessionsCache.getName() );
        localSessionManager.getComponentsSessions().forEach( session -> CacheUtil.addValueToMultiValuedCache( componentSessionsCache, session.getAddress().toString(), server.getNodeID(), HashSet::new ) );

        Log.trace( "Restoring cache content for cache '{}' by adding all connection manager sessions that are connected to the local cluster node.", multiplexerSessionsCache.getName() );
        localSessionManager.getConnnectionManagerSessions().forEach( (address, session) -> multiplexerSessionsCache.put( address, server.getNodeID() ) );

        Log.trace( "Restoring cache content for cache '{}', '{}' and '{}' by adding all incoming server sessions that are connected to the local cluster node.", incomingServerSessionsCache.getName(), hostnameSessionsCache.getName(), validatedDomainsCache.getName());
        localSessionManager.getIncomingServerSessions().forEach( session -> registerIncomingServerSession( session.getAddress().getDomain(), session ) );
    }

    /**
     * Task that closes detached client sessions.
     */
    private class DetachedCleanupTask extends TimerTask {
        /**
         * Close detached client sessions that haven't seen activity in more than
         * 30 minutes by default.
         */
        @Override
        public void run() {
            int idleTime = getSessionDetachTime();
            if (idleTime == -1) {
                return;
            }
            final long deadline = System.currentTimeMillis() - idleTime;
            for (LocalSession session : detachedSessions.values()) {
                try {
                    Log.trace("Iterating over detached session '{}' ({}) to determine if it needs to be cleaned up.", session.getAddress(), session.getStreamID());
                    if (session.getLastActiveDate().getTime() < deadline) {
                        Log.debug("Detached session '{}' ({}) has been detached for longer than {} and will be cleaned up.", session.getAddress(), session.getStreamID(), Duration.ofMillis(idleTime));
                        removeDetached(session);
                        LocalClientSession clientSession = (LocalClientSession)session;

                        // OF-1923: Only close the session if it has not been replaced by another session (if the session
                        // has been replaced, then the condition below will compare to distinct instances). This *should* not
                        // occur (but has been observed, prior to the fix of OF-1923). This check is left in as a safeguard.
                        if (session == routingTable.getClientRoute(session.getAddress())) {
                            try {
                                if ((clientSession.getPresence().isAvailable() || !clientSession.wasAvailable()) &&
                                    routingTable.hasClientRoute(session.getAddress())) {
                                    // Send an unavailable presence to the user's subscribers
                                    // Note: This gives us a chance to send an unavailable presence to the
                                    // entities that the user sent directed presences
                                    Presence presence = new Presence();
                                    presence.setType(Presence.Type.unavailable);
                                    presence.setFrom(session.getAddress());
                                    router.route(presence);
                                }

                                session.getStreamManager().onClose(router, serverAddress);
                            } finally {
                                // Remove the session
                                removeSession(clientSession);
                            }
                        } else {
                            Log.warn("Not removing detached session '{}' ({}) that appears to have been replaced by another session.", session.getAddress(), session.getStreamID());
                        }
                    } else {
                        Log.trace("Detached session '{}' ({}) has been detached for {}, which is not longer than the configured maximum of {}. It will not (yet) be cleaned up.", session.getAddress(), session.getStreamID(), Duration.ofMillis(System.currentTimeMillis()-session.getLastActiveDate().getTime()), Duration.ofMillis(idleTime));
                    }
                }
                catch (Throwable e) {
                    Log.error("An exception occurred while trying processing detached session '{}' ({}).", session.getAddress(), session.getStreamID(), e);
                }
            }
        }
    }

}
