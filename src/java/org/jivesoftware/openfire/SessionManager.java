/**
 * $RCSfile$
 * $Revision: 3170 $
 * $Date: 2005-12-07 14:00:58 -0300 (Wed, 07 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.audit.AuditStreamIDFactory;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.component.InternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.http.HttpSession;
import org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.lock.LockManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

/**
 * Manages the sessions associated with an account. The information
 * maintained by the Session manager is entirely transient and does
 * not need to be preserved between server restarts.
 *
 * @author Derek DeMoro
 */
public class SessionManager extends BasicModule implements ClusterEventListener {

    public static final String COMPONENT_SESSION_CACHE_NAME = "Components Sessions";
    public static final String CM_CACHE_NAME = "Connection Managers Sessions";
    public static final String ISS_CACHE_NAME = "Incoming Server Sessions";

    public static final int NEVER_KICK = -1;

    private XMPPServer server;
    private PacketRouter router;
    private String serverName;
    private JID serverAddress;
    private UserManager userManager;
    private int conflictLimit;

    /**
     * Counter of user connections. A connection is counted just after it was created and not
     * after the user became available.
     */
    private final AtomicInteger connectionsCounter = new AtomicInteger(0);

    /**
     * Cache (unlimited, never expire) that holds external component sessions.
     * Key: component address, Value: nodeID
     */
    private Cache<String, byte[]> componentSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds sessions of connection managers. For each
     * socket connection of the CM to the server there is going to be an entry in the cache.
     * Key: full address of the CM that identifies the socket, Value: nodeID
     */
    private Cache<String, byte[]> multiplexerSessionsCache;

    /**
     * Cache (unlimited, never expire) that holds incoming sessions of remote servers.
     * Key: stream ID that identifies the socket/session, Value: nodeID
     */
    private Cache<String, byte[]> incomingServerSessionsCache;
    /**
     * Cache (unlimited, never expire) that holds list of incoming sessions
     * originated from the same remote server (domain/subdomain). For instance, jabber.org
     * may have 2 connections to the server running in jivesoftware.com (one socket to
     * jivesoftware.com and the other socket to conference.jivesoftware.com).
     * Key: remote hostname (domain/subdomain), Value: list of stream IDs that identify each socket.
     */
    private Cache<String, List<String>> hostnameSessionsCache;

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
    private Cache<String, Set<String>> validatedDomainsCache;

    private ClientSessionListener clientSessionListener = new ClientSessionListener();
    private ComponentSessionListener componentSessionListener = new ComponentSessionListener();
    private IncomingServerSessionListener incomingServerListener = new IncomingServerSessionListener();
    private OutgoingServerSessionListener outgoingServerListener = new OutgoingServerSessionListener();
    private ConnectionMultiplexerSessionListener multiplexerSessionListener = new ConnectionMultiplexerSessionListener();

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
        conflictLimit = JiveGlobals.getIntProperty("xmpp.session.conflict-limit", 0);
    }

    /**
     * Returns the session originated from the specified address or <tt>null</tt> if none was
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
            byte[] nodeID = multiplexerSessionsCache.get(address.toString());
            if (nodeID != null) {
                return server.getRemoteSessionLocator().getConnectionMultiplexerSession(nodeID, address);
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
        List<ConnectionMultiplexerSession> sessions = new ArrayList<ConnectionMultiplexerSession>();
        // Add sessions of CMs connected to this JVM
        sessions.addAll(localSessionManager.getConnnectionManagerSessions().values());
        // Add sessions of CMs connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            for (Map.Entry<String, byte[]> entry : multiplexerSessionsCache.entrySet()) {
                if (!server.getNodeID().equals(entry.getValue())) {
                    sessions.add(locator.getConnectionMultiplexerSession(entry.getValue(), new JID(entry.getKey())));
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
        List<ConnectionMultiplexerSession> sessions = new ArrayList<ConnectionMultiplexerSession>();
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
            for (Map.Entry<String, byte[]> entry : multiplexerSessionsCache.entrySet()) {
                if (!server.getNodeID().equals(entry.getValue())) {
                    JID jid = new JID(entry.getKey());
                    if (domain.equals(jid.getDomain())) {
                        sessions.add(
                                locator.getConnectionMultiplexerSession(entry.getValue(), new JID(entry.getKey())));
                    }
                }
            }
        }
        return sessions;
    }

    /**
     * Creates a new <tt>ConnectionMultiplexerSession</tt>.
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
        multiplexerSessionsCache.put(address.toString(), server.getNodeID().toByteArray());
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
     * Creates a new <tt>ClientSession</tt>. The new Client session will have a newly created
     * stream ID.
     *
     * @param conn the connection to create the session from.
     * @return a newly created session.
     */
    public LocalClientSession createClientSession(Connection conn) {
        return createClientSession(conn, nextStreamID());
    }

    /**
     * Creates a new <tt>ClientSession</tt> with the specified streamID.
     *
     * @param conn the connection to create the session from.
     * @param id the streamID to use for the new session.
     * @return a newly created session.
     */
    public LocalClientSession createClientSession(Connection conn, StreamID id) {
        if (serverName == null) {
            throw new IllegalStateException("Server not initialized");
        }
        LocalClientSession session = new LocalClientSession(serverName, conn, id);
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

    public HttpSession createClientHttpSession(long rid, InetAddress address, StreamID id)
            throws UnauthorizedException
    {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        PacketDeliverer backupDeliverer = server.getPacketDeliverer();
        HttpSession session = new HttpSession(backupDeliverer, serverName, address, id, rid);
        Connection conn = session.getConnection();
        conn.init(session);
        conn.registerCloseListener(clientSessionListener, session);
        localSessionManager.getPreAuthenticatedSessions().put(session.getAddress().getResource(), session);
        connectionsCounter.incrementAndGet();
        return session;
    }

    public LocalComponentSession createComponentSession(JID address, Connection conn) throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        StreamID id = nextStreamID();
        LocalComponentSession session = new LocalComponentSession(serverName, conn, id);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove the external component from the list of components
        conn.registerCloseListener(componentSessionListener, session);
        // Set the bind address as the address of the session
        session.setAddress(address);

        // Add to component session.
        localSessionManager.getComponentsSessions().add(session);
        // Keep track of the cluster node hosting the new external component
        componentSessionsCache.put(address.toString(), server.getNodeID().toByteArray());
        return session;
    }

    /**
     * Creates a session for a remote server. The session should be created only after the
     * remote server has been authenticated either using "server dialback" or SASL.
     *
     * @param conn the connection to the remote server.
     * @param id the stream ID used in the stream element when authenticating the server.
     * @return the newly created {@link IncomingServerSession}.
     * @throws UnauthorizedException if the local server has not been initialized yet.
     */
    public LocalIncomingServerSession createIncomingServerSession(Connection conn, StreamID id)
            throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        LocalIncomingServerSession session = new LocalIncomingServerSession(serverName, conn, id);
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
        String streamID = session.getStreamID().getID();
        localSessionManager.addIncomingServerSessions(streamID, session);
        // Keep track of the nodeID hosting the incoming server session
        incomingServerSessionsCache.put(streamID, server.getNodeID().toByteArray());
        // Update list of sockets/sessions coming from the same remote hostname
        Lock lock = LockManager.getLock(hostname);
        try {
            lock.lock();
            List<String> streamIDs = hostnameSessionsCache.get(hostname);
            if (streamIDs == null) {
                streamIDs = new ArrayList<String>();
            }
            streamIDs.add(streamID);
            hostnameSessionsCache.put(hostname, streamIDs);
        }
        finally {
            lock.unlock();
        }
        // Add to clustered cache
        lock = LockManager.getLock(streamID);
        try {
            lock.lock();
            Set<String> validatedDomains = validatedDomainsCache.get(streamID);
            if (validatedDomains == null) {
                validatedDomains = new HashSet<String>();
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
        String streamID = session.getStreamID().getID();
        localSessionManager.removeIncomingServerSessions(streamID);
        // Remove track of the nodeID hosting the incoming server session
        incomingServerSessionsCache.remove(streamID);

        // Remove from list of sockets/sessions coming from the remote hostname
        Lock lock = LockManager.getLock(hostname);
        try {
            lock.lock();
            List<String> streamIDs = hostnameSessionsCache.get(hostname);
            if (streamIDs != null) {
                streamIDs.remove(streamID);
                if (streamIDs.isEmpty()) {
                    hostnameSessionsCache.remove(hostname);
                }
                else {
                    hostnameSessionsCache.put(hostname, streamIDs);
                }
            }
        }
        finally {
            lock.unlock();
        }
        // Remove from clustered cache
        lock = LockManager.getLock(streamID);
        try {
            lock.lock();
            Set<String> validatedDomains = validatedDomainsCache.get(streamID);
            if (validatedDomains == null) {
                validatedDomains = new HashSet<String>();
            }
            validatedDomains.remove(hostname);
            if (!validatedDomains.isEmpty()) {
                validatedDomainsCache.put(streamID, validatedDomains);
            }
            else {
                validatedDomainsCache.remove(streamID);
            }
        } finally {
            lock.unlock();
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
    public Collection<String> getValidatedDomains(String streamID) {
        Lock lock = LockManager.getLock(streamID);
        try {
            lock.lock();
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
        // Remove the pre-Authenticated session but remember to use the temporary ID as the key
        localSessionManager.getPreAuthenticatedSessions().remove(session.getStreamID().toString());
        // Add session to the routing table (routing table will know session is not available yet)
        routingTable.addClientRoute(session.getAddress(), session);
        SessionEventDispatcher.EventType event = session.getAuthToken().isAnonymous() ?
                SessionEventDispatcher.EventType.anonymous_session_created :
                SessionEventDispatcher.EventType.session_created;
        // Fire session created event.
        SessionEventDispatcher.dispatchEvent(session, event);
    }

    /**
     * Notification message sent when a client sent an available presence for the session. Making
     * the session available means that the session is now eligible for receiving messages from
     * other clients. Sessions whose presence is not available may only receive packets (IQ packets)
     * from the server. Therefore, an unavailable session remains invisible to other clients.
     *
     * @param session the session that receieved an available presence.
     */
    public void sessionAvailable(LocalClientSession session) {
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
     * Sends the presences of other connected resources to the resource that just connected.
     * 
     * @param session the newly created session.
     */
    private void broadcastPresenceOfOtherResource(LocalClientSession session) {
        Presence presence;
        // Get list of sessions of the same user
        JID searchJID = new JID(session.getAddress().getNode(), session.getAddress().getDomain(), null);
        List<JID> addresses = routingTable.getRoutes(searchJID);
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
     * Broadcasts presence updates from the originating user's resource to any of the user's
     * existing available resources (if any).
     *
     * @param originatingResource the full JID of the session that sent the presence update.
     * @param presence the presence.
     */
    public void broadcastPresenceToOtherResources(JID originatingResource, Presence presence) {
        // Get list of sessions of the same user
        JID searchJID = new JID(originatingResource.getNode(), originatingResource.getDomain(), null);
        List<JID> addresses = routingTable.getRoutes(searchJID);
        for (JID address : addresses) {
            if (address.equals(originatingResource)) {
                continue;
            }
            // Send the presence of the session whose presence has changed to
            // this other user's session
            presence.setTo(address);
            routingTable.routePacket(address, presence, false);
        }
    }

    /**
     * Notification message sent when a client sent an unavailable presence for the session. Making
     * the session unavailable means that the session is not eligible for receiving messages from
     * other clients.
     *
     * @param session the session that receieved an unavailable presence.
     */
    public void sessionUnavailable(LocalClientSession session) {
        if (session.getAddress() != null && routingTable != null &&
                session.getAddress().toBareJID().trim().length() != 0) {
            // Update route to unavailable session (anonymous or not)
            routingTable.addClientRoute(session.getAddress(), session);
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
        JID searchJID = new JID(session.getAddress().toBareJID());
        for (JID address : routingTable.getRoutes(searchJID)) {
            if (address.equals(session.getAddress())) {
                continue;
            }
            ClientSession otherSession = routingTable.getClientRoute(address);
            if (otherSession.getPresence().getPriority() >= 0) {
                return;
            }
        }

        // User sessions had negative presence before this change so deliver messages
        if (session.canFloodOfflineMessages()) {
            OfflineMessageStore messageStore = server.getOfflineMessageStore();
            Collection<OfflineMessage> messages = messageStore.getMessages(session.getAuthToken().getUsername(), true);
            for (Message message : messages) {
                session.process(message);
            }
        }
    }

    public boolean isAnonymousRoute(String username) {
        // JID's node and resource are the same for anonymous sessions
        return isAnonymousRoute(new JID(username, serverName, username));
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
        List<ClientSession> results = new ArrayList<ClientSession>();
        if (filter != null) {
            // Grab all the matching sessions
            results.addAll(getSessions());

            // Now we have a copy of the references so we can spend some time
            // doing the rest of the filtering without locking out session access
            // so let's iterate and filter each session one by one
            List<ClientSession> filteredResults = new ArrayList<ClientSession>();
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
            List<ClientSession> finalResults = new ArrayList<ClientSession>();
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
    public LocalIncomingServerSession getIncomingServerSession(String streamID) {
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
        List<String> streamIDs;
        // Get list of sockets/sessions coming from the remote hostname
        Lock lock = LockManager.getLock(hostname);
        try {
            lock.lock();
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
            List<IncomingServerSession> sessions = new ArrayList<IncomingServerSession>();
            for (String streamID : streamIDs) {
                // Search in local hosted sessions
                IncomingServerSession session = localSessionManager.getIncomingServerSession(streamID);
                RemoteSessionLocator locator = server.getRemoteSessionLocator();
                if (session == null && locator != null) {
                    // Get the node hosting this session
                    byte[] nodeID = incomingServerSessionsCache.get(streamID);
                    if (nodeID != null) {
                        session = locator.getIncomingServerSession(nodeID, streamID);
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
     * @param hostname the name of the remote server.
     * @return a session that was originated from this server to a remote server.
     */
    public OutgoingServerSession getOutgoingServerSession(String hostname) {
        return routingTable.getServerRoute(new JID(null, hostname, null));
    }

    public Collection<ClientSession> getSessions(String username) {
        List<ClientSession> sessionList = new ArrayList<ClientSession>();
        if (username != null) {
            List<JID> addresses = routingTable.getRoutes(new JID(username, serverName, null, true));
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
            Collection<Object> results =
                    CacheFactory.doSynchronousClusterTask(new GetSessionsCountTask(false), false);
            for (Object result : results) {
                if (result == null) {
                    continue;
                }
                total = total + (Integer) result;
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
            Collection<Object> results =
                    CacheFactory.doSynchronousClusterTask(new GetSessionsCountTask(true), false);
            for (Object result : results) {
                if (result == null) {
                    continue;
                }
                total = total + (Integer) result;
            }
        }
        return total;
    }

    /**
     * Returns the number of sessions for a user that are available. For the count
     * of all sessions for the user, including sessions that are just starting
     * or closed, see {@see #getConnectionsCount(String)}.
     *
     * @param username the user.
     * @return number of available sessions for a user.
     */
    public int getActiveSessionCount(String username) {
        return routingTable.getRoutes(new JID(username, serverName, null, true)).size();
    }

    public int getSessionCount(String username) {
        // TODO Count ALL sessions not only available
        return routingTable.getRoutes(new JID(username, serverName, null, true)).size();
    }

    /**
     * Returns a collection with the established sessions from external components.
     *
     * @return a collection with the established sessions from external components.
     */
    public Collection<ComponentSession> getComponentSessions() {
        List<ComponentSession> sessions = new ArrayList<ComponentSession>();
        // Add sessions of external components connected to this JVM
        sessions.addAll(localSessionManager.getComponentsSessions());
        // Add sessions of external components connected to other cluster nodes
        RemoteSessionLocator locator = server.getRemoteSessionLocator();
        if (locator != null) {
            for (Map.Entry<String, byte[]> entry : componentSessionsCache.entrySet()) {
                if (!server.getNodeID().equals(entry.getValue())) {
                    sessions.add(locator.getComponentSession(entry.getValue(), new JID(entry.getKey())));
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
            byte[] nodeID = componentSessionsCache.get(domain);
            if (nodeID != null) {
                return locator.getComponentSession(nodeID, new JID(domain));
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
        for (JID address : routingTable.getRoutes(new JID(username, serverName, null))) {
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
        JiveGlobals.setProperty("xmpp.session.conflict-limit", Integer.toString(conflictLimit));
    }

    private class ClientSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        public void onConnectionClose(Object handback) {
            try {
                LocalClientSession session = (LocalClientSession) handback;
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

    private class ComponentSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
        public void onConnectionClose(Object handback) {
            LocalComponentSession session = (LocalComponentSession)handback;
            try {
                // Unbind registered domains for this external component
                for (String domain : session.getExternalComponent().getSubdomains()) {
                    String subdomain = domain.substring(0, domain.indexOf(serverName) - 1);
                    InternalComponentManager.getInstance().removeComponent(subdomain);
                }
            }
            catch (Exception e) {
                // Can't do anything about this problem...
                Log.error(LocaleUtils.getLocalizedString("admin.error.close"), e);
            }
            finally {
                // Remove the session
                localSessionManager.getComponentsSessions().remove(session);
                // Remove track of the cluster node hosting the external component
                componentSessionsCache.remove(session.getAddress().toString());
            }
        }
    }

    private class IncomingServerSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
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
        public void onConnectionClose(Object handback) {
            OutgoingServerSession session = (OutgoingServerSession)handback;
            // Remove all the hostnames that were registered for this server session
            for (String hostname : session.getHostnames()) {
                // Remove the route to the session using the hostname
                server.getRoutingTable().removeServerRoute(new JID(hostname));
            }
        }
    }

    private class ConnectionMultiplexerSessionListener implements ConnectionCloseListener {
        /**
         * Handle a session that just closed.
         *
         * @param handback The session that just closed
         */
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

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        router = server.getPacketRouter();
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        serverName = server.getServerInfo().getName();
        serverAddress = new JID(serverName);

        if (JiveGlobals.getBooleanProperty("xmpp.audit.active")) {
            streamIDFactory = new AuditStreamIDFactory();
        }
        else {
            streamIDFactory = new BasicStreamIDFactory();
        }

        String conflictLimitProp = JiveGlobals.getProperty("xmpp.session.conflict-limit");
        if (conflictLimitProp == null) {
            conflictLimit = 0;
            JiveGlobals.setProperty("xmpp.session.conflict-limit", Integer.toString(conflictLimit));
        }
        else {
            try {
                conflictLimit = Integer.parseInt(conflictLimitProp);
            }
            catch (NumberFormatException e) {
                conflictLimit = 0;
                JiveGlobals.setProperty("xmpp.session.conflict-limit", Integer.toString(conflictLimit));
            }
        }

        // Initialize caches.
        componentSessionsCache = CacheFactory.createCache(COMPONENT_SESSION_CACHE_NAME);
        multiplexerSessionsCache = CacheFactory.createCache(CM_CACHE_NAME);
        incomingServerSessionsCache = CacheFactory.createCache(ISS_CACHE_NAME);
        hostnameSessionsCache = CacheFactory.createCache("Sessions by Hostname");
        validatedDomainsCache = CacheFactory.createCache("Validated Domains");
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
     * the message will be sent to all the user sessions of the requeted user (defined by the node).
     * Finally, if the address is a full JID then the message will be sent to the session associated
     * to the full JID. If no session is found then the message is not sent.
     *
     * @param address the address that defines the sessions that will receive the message.
     * @param subject the subject to broadcast.
     * @param body    the body to broadcast.
     */
    public void sendServerMessage(JID address, String subject, String body) {
        Message packet = createServerMessage(subject, body);
        if (address == null || address.getNode() == null || !userManager.isRegisteredUser(address)) {
            broadcast(packet);
        }
        else if (address.getResource() == null || address.getResource().length() < 1) {
            userBroadcast(address.getNode(), packet);
        }
        else {
            routingTable.routePacket(address, packet, true);
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

    public void start() throws IllegalStateException {
        super.start();
        localSessionManager.start();
    }

    public void stop() {
        Log.debug("Stopping server");
        // Stop threads that are sending packets to remote servers
        OutgoingSessionPromise.getInstance().shutdown();
        if (JiveGlobals.getBooleanProperty("shutdownMessage.enabled")) {
            sendServerMessage(null, LocaleUtils.getLocalizedString("admin.shutdown.now"));
        }
        localSessionManager.stop();
        serverName = null;
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

    public void joinedCluster() {
        restoreCacheContent();
    }

    public void joinedCluster(byte[] nodeID) {
        // Do nothing
    }

    public void leftCluster() {
        if (!XMPPServer.getInstance().isShuttingDown()) {
            // Add local sessions to caches
            restoreCacheContent();
        }
    }

    public void leftCluster(byte[] nodeID) {
        // Do nothing
    }

    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    private void restoreCacheContent() {
        // Add external component sessions hosted locally to the cache (using new nodeID)
        for (Session session : localSessionManager.getComponentsSessions()) {
            componentSessionsCache.put(session.getAddress().toString(), server.getNodeID().toByteArray());
        }

        // Add connection multiplexer sessions hosted locally to the cache (using new nodeID)
        for (String address : localSessionManager.getConnnectionManagerSessions().keySet()) {
            multiplexerSessionsCache.put(address, server.getNodeID().toByteArray());
        }

        // Add incoming server sessions hosted locally to the cache (using new nodeID)
        for (LocalIncomingServerSession session : localSessionManager.getIncomingServerSessions()) {
            String streamID = session.getStreamID().getID();
            incomingServerSessionsCache.put(streamID, server.getNodeID().toByteArray());
            for (String hostname : session.getValidatedDomains()) {
                // Update list of sockets/sessions coming from the same remote hostname
                Lock lock = LockManager.getLock(hostname);
                try {
                    lock.lock();
                    List<String> streamIDs = hostnameSessionsCache.get(hostname);
                    if (streamIDs == null) {
                        streamIDs = new ArrayList<String>();
                    }
                    streamIDs.add(streamID);
                    hostnameSessionsCache.put(hostname, streamIDs);
                }
                finally {
                    lock.unlock();
                }
                // Add to clustered cache
                lock = LockManager.getLock(streamID);
                try {
                    lock.lock();
                    Set<String> validatedDomains = validatedDomainsCache.get(streamID);
                    if (validatedDomains == null) {
                        validatedDomains = new HashSet<String>();
                    }
                    boolean added = validatedDomains.add(hostname);
                    if (added) {
                        validatedDomainsCache.put(streamID, validatedDomains);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }
}
