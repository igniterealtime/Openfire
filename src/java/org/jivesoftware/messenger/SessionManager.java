/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.audit.AuditStreamIDFactory;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;
import org.jivesoftware.messenger.server.IncomingServerSession;
import org.jivesoftware.messenger.server.OutgoingServerSession;
import org.jivesoftware.messenger.server.OutgoingSessionPromise;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.component.ComponentSession;
import org.jivesoftware.messenger.component.InternalComponentManager;
import org.jivesoftware.messenger.event.SessionEventDispatcher;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the sessions associated with an account. The information
 * maintained by the Session manager is entirely transient and does
 * not need to be preserved between server restarts.
 *
 * @author Derek DeMoro
 */
public class SessionManager extends BasicModule {

    private int sessionCount = 0;
    public static final int NEVER_KICK = -1;

    private PresenceUpdateHandler presenceHandler;
    private PacketRouter router;
    private String serverName;
    private JID serverAddress;
    private UserManager userManager;
    private int conflictLimit;

    private ClientSessionListener clientSessionListener = new ClientSessionListener();
    private ComponentSessionListener componentSessionListener = new ComponentSessionListener();
    private IncomingServerSessionListener incomingServerListener = new IncomingServerSessionListener();
    private OutgoingServerSessionListener outgoingServerListener = new OutgoingServerSessionListener();

    /**
     * Map that holds sessions that has been created but haven't been authenticated yet. The Map
     * will hold client sessions.
     */
    private Map<String, ClientSession> preAuthenticatedSessions = new ConcurrentHashMap<String, ClientSession>();

    /**
     * Map of priority ordered SessionMap objects with username (toLowerCase) as key. The sessions
     * contained in this Map are client sessions. For each username a SessionMap is kept which
     * tracks the session for each user resource.
     */
    private Map<String, SessionMap> sessions = new ConcurrentHashMap<String, SessionMap>();

    /**
     * Map of anonymous server sessions. They need to be treated separately as they
     * have no associated user, and don't follow the normal routing rules for
     * priority based fall over. The sessions contained in this Map are client sessions.
     */
    private Map<String, ClientSession> anonymousSessions = new ConcurrentHashMap<String, ClientSession>();

    /**
     * The sessions contained in this List are component sessions. For each connected component
     * this Map will keep the component's session.
     */
    private List<ComponentSession> componentsSessions = new CopyOnWriteArrayList<ComponentSession>();

    /**
     * The sessions contained in this Map are server sessions originated by a remote server. These
     * sessions can only receive packets from the remote server but are not capable of sending
     * packets to the remote server. Sessions will be added to this collecion only after they were
     * authenticated. The key of the Map is the hostname of the remote server. The value is a
     * list of IncomingServerSession that will keep each session created by a remote server to
     * this server.
     */
    private Map<String, List<IncomingServerSession>> incomingServerSessions = new ConcurrentHashMap<String, List<IncomingServerSession>>();

    /**
     * The sessions contained in this Map are server sessions originated from this server to remote
     * servers. These sessions can only send packets to the remote server but are not capable of
     * receiving packets from the remote server. Sessions will be added to this collecion only
     * after they were authenticated. The key of the Map is the hostname of the remote server.
     */
    private Map<String, OutgoingServerSession> outgoingServerSessions = new ConcurrentHashMap<String, OutgoingServerSession>();

    /**
     * <p>Session manager must maintain the routing table as sessions are added and
     * removed.</p>
     */
    private RoutingTable routingTable;

    private StreamIDFactory streamIDFactory;

    /**
     * Timer that will clean up dead or inactive sessions. Currently only outgoing server sessions
     * will be analyzed.
     */
    private Timer timer = new Timer("Sessions cleanup");

    /**
     * Task that closes idle server sessions.
     */
    private ServerCleanupTask serverCleanupTask;

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
    }

    /**
     * Simple data structure to track sessions for a single user (tracked by resource
     * and priority).
     */
    private class SessionMap {
        private Map<String,ClientSession> resources = new ConcurrentHashMap<String,ClientSession>();
        private LinkedList<String> priorityList = new LinkedList<String>();

        /**
         * Add a session to the manager.
         *
         * @param session
         */
        void addSession(ClientSession session) {
            String resource = session.getAddress().getResource();
            Presence presence = session.getPresence();
            int priority = presence == null ? 0 : presence.getPriority();
            resources.put(resource, session);
            sortSession(resource, priority);
        }

        /**
         * Sorts the session into the list based on priority
         *
         * @param resource The resource corresponding to the session to sort
         * @param priority The priority to use for sorting
         */
        private void sortSession(String resource, int priority) {
            synchronized (priorityList) {
                if (priorityList.size() > 0) {
                    Iterator<String> iter = priorityList.iterator();
                    for (int i = 0; iter.hasNext(); i++) {
                        ClientSession sess = resources.get(iter.next());
                        if (sess != null && sess.getPresence().getPriority() <= priority) {
                            priorityList.add(i, resource);
                            break;
                        }
                    }
                }
                if (!priorityList.contains(resource)) {
                    priorityList.addLast(resource);
                }
            }
        }

        /**
         * Change the priority of a session associated with the sender.
         *
         * @param sender   The sender who's session just changed priority
         * @param priority The new priority for the session
         */
        public void changePriority(JID sender, int priority) {
            String resource = sender.getResource();
            if (resources.containsKey(resource)) {
                synchronized (priorityList) {
                    priorityList.remove(resource);
                    sortSession(resource, priority);
                }
            }
        }

        /**
         * Remove a session from the manager.
         *
         * @param session The session to remove
         */
        void removeSession(Session session) {
            String resource = session.getAddress().getResource();
            resources.remove(resource);
            synchronized (priorityList) {
                priorityList.remove(resource);
            }
        }

        /**
         * Gets the session for the given resource.
         *
         * @param resource The resource describing the particular session
         * @return The session for that resource or null if none found (use getDefaultSession() to obtain default)
         */
        ClientSession getSession(String resource) {
            return resources.get(resource);
        }

        /**
         * Checks to see if a session for the given resource exists.
         *
         * @param resource The resource of the session we're checking
         * @return True if we have a session corresponding to that resource
         */
        boolean hasSession(String resource) {
            return resources.containsKey(resource);
        }

        /**
         * Returns the default session for the user based on presence priority. It's possible to
         * indicate if only available sessions (i.e. with an available presence) should be
         * included in the search.
         *
         * @param filterAvailable flag that indicates if only available sessions should be
         *        considered.
         * @return The default session for the user.
         */
        ClientSession getDefaultSession(boolean filterAvailable) {
            if (priorityList.isEmpty()) {
                return null;
            }

            if (!filterAvailable) {
                return resources.get(priorityList.getFirst());
            }
            else {
                synchronized (priorityList) {
                    for (int i=0; i < priorityList.size(); i++) {
                        ClientSession s = resources.get(priorityList.get(i));
                        if (s != null && s.getPresence().isAvailable()) {
                            return s;
                        }
                    }
                }
                return null;
            }
        }

        /**
         * Determines if this map is empty or not.
         *
         * @return True if the map contains no entries
         */
        boolean isEmpty() {
            return resources.isEmpty();
        }

        /**
         * Broadcast to all resources for the given user
         *
         * @param packet
         */
        private void broadcast(Packet packet) throws UnauthorizedException, PacketException {
            for (Session session : resources.values()) {
                packet.setTo(session.getAddress());
                session.process(packet);
            }
        }

        /**
         * Create an iterator over all sessions for the user.
         * We create a new list to generate the iterator so other threads
         * may safely alter the session map without affecting the iterator.
         *
         * @return An iterator of all sessions
         */
        public Collection<ClientSession> getSessions() {
            return resources.values();
        }

        /**
         * Returns a collection of all the sessions whose presence is available.
         *
         * @return a collection of all the sessions whose presence is available.
         */
        public Collection<ClientSession> getAvailableSessions() {
            LinkedList<ClientSession> list = new LinkedList<ClientSession>();
            for (ClientSession session : resources.values()) {
                if (session.getPresence().isAvailable()) {
                    list.add(session);
                }
            }
            return list;
        }

        /**
         * This specified session has received an available presence so we need to recalculate the
         * order of the sessions so we can have update the default session.
         *
         * @param session the session that received an available presence.
         */
        public void sessionAvailable(ClientSession session) {
            changePriority(session.getAddress(), session.getPresence().getPriority());
        }

        /**
         * This specified session has received an unavailable presence so we need to recalculate the
         * order of the sessions so we can have update the default session.
         *
         * @param session the session that received an unavailable presence.
         */
        public void sessionUnavailable(ClientSession session) {
            changePriority(session.getAddress(), 0);
        }
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
     * Creates a new <tt>ClientSession</tt>.
     *
     * @param conn the connection to create the session from.
     * @return a newly created session.
     * @throws UnauthorizedException
     */
    public Session createClientSession(Connection conn) throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        StreamID id = nextStreamID();
        ClientSession session = new ClientSession(serverName, conn, id);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove  and also send an unavailable presence if it wasn't
        // sent before
        conn.registerCloseListener(clientSessionListener, session);

        // Add to pre-authenticated sessions.
        preAuthenticatedSessions.put(session.getAddress().toString(), session);
        return session;
    }

    public Session createComponentSession(Connection conn) throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        StreamID id = nextStreamID();
        ComponentSession session = new ComponentSession(serverName, conn, id);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove the external component from the list of components
        conn.registerCloseListener(componentSessionListener, session);

        // Add to component session.
        componentsSessions.add(session);
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
    public IncomingServerSession createIncomingServerSession(Connection conn, StreamID id)
            throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        IncomingServerSession session = new IncomingServerSession(serverName, conn, id);
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
    public void outgoingServerSessionCreated(OutgoingServerSession session) {
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
    public void registerIncomingServerSession(String hostname, IncomingServerSession session) {
        synchronized (incomingServerSessions) {
            List<IncomingServerSession> sessions = incomingServerSessions.get(hostname);
            if (sessions == null || sessions.isEmpty()) {
                // First session from the remote server to this server so create a
                // new association
                List<IncomingServerSession> value = new CopyOnWriteArrayList<IncomingServerSession>();
                value.add(session);
                incomingServerSessions.put(hostname, value);
            }
            else {
                // Add new session to the existing list of sessions originated by
                // the remote server
                sessions.add(session);
            }
        }
    }

    /**
     * Unregisters the server sessions originated by a remote server with the specified hostname.
     * Notice that the remote server may be hosting several subdomains as well as virtual hosts so
     * the same IncomingServerSession may be associated with many keys. The remote server may have
     * many sessions established with this server (eg. to the server itself and to subdomains
     * hosted by this server).
     *
     * @param hostname the hostname that is being served by the remote server.
     */
    public void unregisterIncomingServerSessions(String hostname) {
        synchronized (incomingServerSessions) {
            incomingServerSessions.remove(hostname);
        }
    }

    /**
     * Unregisters the specified remote server session originiated by the specified remote server.
     *
     * @param hostname the hostname that is being served by the remote server.
     * @param session the session to unregiser.
     */
    private void unregisterIncomingServerSession(String hostname, IncomingServerSession session) {
        synchronized (incomingServerSessions) {
            List<IncomingServerSession> sessions = incomingServerSessions.get(hostname);
            if (sessions != null) {
                sessions.remove(session);
            }
        }
    }

    /**
     * Registers that a server session originated by this server has been established to
     * a remote server named hostname. This session will only be used for sending packets
     * to the remote server and cannot receive packets. The {@link OutgoingServerSession}
     * may have one or more domains, subdomains or virtual hosts authenticated with the
     * remote server.
     *
     * @param hostname the hostname that is being served by the remote server.
     * @param session the outgoing server session to the remote server.
     */
    public void registerOutgoingServerSession(String hostname, OutgoingServerSession session) {
        outgoingServerSessions.put(hostname, session);
    }

    /**
     * Unregisters the server session that was originated by this server to a remote server
     * named hostname. This session was only being used for sending packets
     * to the remote server and not for receiving packets. The {@link OutgoingServerSession}
     * may have one or more domains, subdomains or virtual hosts authenticated with the
     * remote server.
     *
     * @param hostname the hostname that the session was connected with.
     */
    public void unregisterOutgoingServerSession(String hostname) {
        outgoingServerSessions.remove(hostname);
    }

    /**
     * Add a new session to be managed.
     */
    public boolean addSession(ClientSession session) {
        boolean success = false;
        String username = session.getAddress().getNode().toLowerCase();
        SessionMap resources = null;

        synchronized(username.intern()) {
            resources = sessions.get(username);
            if (resources == null) {
                resources = new SessionMap();
                sessions.put(username, resources);
            }
            resources.addSession(session);
            // Remove the pre-Authenticated session but remember to use the temporary JID as the key
            preAuthenticatedSessions.remove(new JID(null, session.getAddress().getDomain(),
                    session.getStreamID().toString()).toString());
            
            // Fire session created event.
            SessionEventDispatcher.dispatchEvent(session,
                    SessionEventDispatcher.EventType.session_created);
            
            success = true;
        }
        return success;
    }

    /**
     * Notification message sent when a client sent an available presence for the session. Making
     * the session available means that the session is now eligible for receiving messages from
     * other clients. Sessions whose presence is not available may only receive packets (IQ packets)
     * from the server. Therefore, an unavailable session remains invisible to other clients.
     *
     * @param session the session that receieved an available presence.
     */
    public void sessionAvailable(ClientSession session) {
        if (anonymousSessions.containsValue(session)) {
            // Anonymous session always have resources so we only need to add one route. That is
            // the route to the anonymous session
            routingTable.addRoute(session.getAddress(), session);
        }
        else {
            // A non-anonymous session is now available
            Session defaultSession = null;
            try {
                SessionMap sessionMap = sessions.get(session.getUsername());
                if (sessionMap == null) {
                    Log.warn("No SessionMap found for session" + "\n" + session);
                }
                // Update the order of the sessions based on the new presence of this session
                sessionMap.sessionAvailable(session);
                defaultSession = sessionMap.getDefaultSession(true);
                JID node = new JID(defaultSession.getAddress().getNode(),
                        defaultSession.getAddress().getDomain(), null);
                // Add route to default session (used when no resource is specified)
                routingTable.addRoute(node, defaultSession);
                // Add route to the new session
                routingTable.addRoute(session.getAddress(), session);
                // Broadcast presence between the user's resources
                broadcastPresenceToOtherResource(session);
            }
            catch (UserNotFoundException e) {
                // Do nothing since the session is anonymous (? - shouldn't happen)
            }
        }
    }

    /**
     * Broadcast initial presence from the user's new available resource to any of the user's 
     * existing available resources (if any).
     * 
     * @param session the session that received the new presence and therefore will not receive 
     *        the notification.
     */
    private void broadcastPresenceToOtherResource(ClientSession session)
            throws UserNotFoundException {
        Presence presence = null;
        Collection<ClientSession> availableSession;
        SessionMap sessionMap = sessions.get(session.getUsername());
        if (sessionMap != null) {
            availableSession = new ArrayList<ClientSession>(sessionMap.getAvailableSessions());
            for (ClientSession userSession : availableSession) {
                if (userSession != session) {
                    // Send the presence of an existing session to the session that has just changed
                    // the presence
                    if (session.getPresence().isAvailable()) {
                        presence = userSession.getPresence().createCopy();
                        presence.setTo(session.getAddress());
                        session.process(presence);
                    }
                    // Send the presence of the session whose presence has changed to this other
                    // user's session
                    presence = session.getPresence().createCopy();
                    presence.setTo(userSession.getAddress());
                    userSession.process(presence);
                }
            }
        }
    }

    /**
     * Notification message sent when a client sent an unavailable presence for the session. Making
     * the session unavailable means that the session is not eligible for receiving messages from
     * other clients.
     *
     * @param session the session that receieved an unavailable presence.
     */
    public void sessionUnavailable(ClientSession session) {
        if (session.getAddress() != null && routingTable != null &&
                session.getAddress().toBareJID().trim().length() != 0) {
            // Remove route to the removed session (anonymous or not)
            routingTable.removeRoute(session.getAddress());
            try {
                if (session.getUsername() == null) {
                    // Do nothing since this is an anonymous session
                    return;
                }
                SessionMap sessionMap = sessions.get(session.getUsername());
                // If sessionMap is null, which is an irregular case, try to clean up the routes to
                // the user from the routing table
                if (sessionMap == null) {
                    JID userJID = new JID(session.getUsername(), serverName, "");
                    if (routingTable.getRoute(userJID) != null) {
                        // Remove the route for the session's BARE address
                        routingTable.removeRoute(new JID(session.getAddress().getNode(),
                                session.getAddress().getDomain(), ""));
                    }
                }
                // If all the user sessions are gone then remove the route to the default session
                else if (sessionMap.getAvailableSessions().isEmpty()) {
                    // Remove the route for the session's BARE address
                    routingTable.removeRoute(new JID(session.getAddress().getNode(),
                            session.getAddress().getDomain(), ""));
                    // Broadcast presence between the user's resources
                    broadcastPresenceToOtherResource(session);
                }
                else {
                    // Update the order of the sessions based on the new presence of this session
                    sessionMap.sessionUnavailable(session);
                    // Update the route for the session's BARE address
                    Session defaultSession = sessionMap.getDefaultSession(true);
                    routingTable.addRoute(new JID(defaultSession.getAddress().getNode(),
                            defaultSession.getAddress().getDomain(), ""),
                            defaultSession);
                    // Broadcast presence between the user's resources
                    broadcastPresenceToOtherResource(session);
                }
            }
            catch (UserNotFoundException e) {
                // Do nothing since the session is anonymous
            }
        }
    }

    /**
     * Change the priority of a session, that was already available, associated with the sender.
     *
     * @param sender   The sender who's session just changed priority
     * @param priority The new priority for the session
     */
    public void changePriority(JID sender, int priority) {
        if (sender.getNode() == null) {
                // Do nothing if the session belongs to an anonymous user
            return;
        }
        String username = sender.getNode().toLowerCase();
        synchronized (username.intern()) {
            SessionMap resources = sessions.get(username);
            if (resources == null) {
                return;
            }
            resources.changePriority(sender, priority);

            // Get the session with highest priority
            Session defaultSession = resources.getDefaultSession(true);
            // Update the route to the bareJID with the session with highest priority
            routingTable.addRoute(new JID(defaultSession.getAddress().getNode(),
                    defaultSession.getAddress().getDomain(), ""),
                    defaultSession);
        }
    }


    /**
     * Retrieve the best route to deliver packets to this session given the recipient jid. If the
     * requested JID does not have a node (i.e. username) then the best route will be looked up
     * in the anonymous sessions list. Otherwise, try to find a root for the exact JID
     * (i.e. including the resource) and if none is found then answer the deafult session if any.
     *
     * @param recipient The recipient ID to deliver packets to
     * @return The XMPPAddress best suited to use for delivery to the recipient
     */
    public Session getBestRoute(JID recipient) {
        // Return null if the JID belongs to a foreign server
        if (serverName == null || !serverName.equals(recipient.getDomain())) {
             return null;
        }
        ClientSession session = null;
        String resource = recipient.getResource();
        String username = recipient.getNode();
        if (username == null || "".equals(username)) {
            if (resource != null) {
                session = anonymousSessions.get(resource);
                if (session == null){
                    session = getSession(recipient);
                }
            }
        }
        else {
            username = username.toLowerCase();
            synchronized (username.intern()) {
                SessionMap sessionMap = sessions.get(username);
                if (sessionMap != null) {
                    if (resource == null) {
                        session = sessionMap.getDefaultSession(false);
                    }
                    else {
                        session = sessionMap.getSession(resource);
                        if (session == null) {
                            session = sessionMap.getDefaultSession(false);
                        }
                    }
                }
            }
        }
        // Sanity check - check if the underlying session connection is closed. Remove the session
        // from the list of sessions if the session is closed and proceed to look for another route.
        if (session != null && session.getConnection().isClosed()) {
            removeSession(session);
            return getBestRoute(recipient);
        }
        return session;
    }

    public boolean isActiveRoute(String username, String resource) {
        boolean hasRoute = false;

        if (username == null || "".equals(username)) {
            if (resource != null) {
                hasRoute = anonymousSessions.containsKey(resource);
            }
        }
        else {
            username = username.toLowerCase();
            Session session = null;
            synchronized (username.intern()) {
                SessionMap sessionMap = sessions.get(username);
                if (sessionMap != null) {
                    if (resource == null) {
                        hasRoute = !sessionMap.isEmpty();
                    }
                    else {
                        if (sessionMap.hasSession(resource)) {
                            session = sessionMap.getSession(resource);
                        }
                    }
                }
            }
            // Makes sure the session is still active
            // Must occur outside of the lock since validation can cause
            // the socket to close - deadlocking on session removal
            if (session != null && !session.getConnection().isClosed()) {
                hasRoute = session.getConnection().validate();
            }

        }
        return hasRoute;
    }

    /**
     * Returns the session responsible for this JID.
     *
     * @param from the sender of the packet.
     * @return the <code>Session</code> associated with the JID.
     */
    public ClientSession getSession(JID from) {
        // Return null if the JID is null
        if (from == null) {
            return null;
        }
        return getSession(from.getNode(), from.getDomain(), from.getResource());
    }

    /**
     * Returns the session responsible for this JID data. The returned Session may have never sent
     * an available presence (thus not have a route) or could be a Session that hasn't
     * authenticated yet (i.e. preAuthenticatedSessions). 
     *
     * @param username the username of the JID.
     * @param domain the username of the JID.
     * @param resource the username of the JID.
     * @return the <code>Session</code> associated with the JID data.
     */
    public ClientSession getSession(String username, String domain, String resource) {
        // Return null if the JID's data belongs to a foreign server. If the server is
        // shutting down then serverName will be null so answer null too in this case.
        if (serverName == null || !serverName.equals(domain)) {
            return null;
        }

        ClientSession session = null;
        // Build a JID represention based on the given JID data
        StringBuilder buf = new StringBuilder(40);
        if (username != null) {
            buf.append(username).append("@");
        }
        buf.append(domain);
        if (resource != null) {
            buf.append("/").append(resource);
        }
        // Initially Check preAuthenticated Sessions
        session = preAuthenticatedSessions.get(buf.toString());
        if(session != null){
            return session;
        }

        if (resource == null) {
            return null;
        }
        if (username == null || "".equals(username)) {
            session = anonymousSessions.get(resource);
        }
        else {
            username = username.toLowerCase();
            synchronized (username.intern()) {
                SessionMap sessionMap = sessions.get(username);
                if (sessionMap != null) {
                    session = sessionMap.getSession(resource);
                }
            }
        }
        return session;
    }


    public Collection<ClientSession> getSessions() {
        List<ClientSession> allSessions = new ArrayList<ClientSession>();
        copyUserSessions(allSessions);
        copyAnonSessions(allSessions);
        return allSessions;
    }


    public Collection<ClientSession> getSessions(SessionResultFilter filter) {
        List<ClientSession> results = new ArrayList<ClientSession>();
        if (filter != null) {
            // Grab all the possible matching sessions by user
            if (filter.getUsername() == null) {
                // No user id filtering
                copyAnonSessions(results);
                copyUserSessions(results);
            }
            else {
                try {
                    copyUserSessions(userManager.getUser(filter.getUsername()).getUsername(),
                            results);
                }
                catch (UserNotFoundException e) {
                    // Ignore.
                }
            }

            Date createMin = filter.getCreationDateRangeMin();
            Date createMax = filter.getCreationDateRangeMax();
            Date activityMin = filter.getLastActivityDateRangeMin();
            Date activityMax = filter.getLastActivityDateRangeMax();

            // Now we have a copy of the references so we can spend some time
            // doing the rest of the filtering without locking out session access
            // so let's iterate and filter each session one by one
            List<ClientSession> filteredResults = new ArrayList<ClientSession>();
            for (ClientSession session : results) {
                // Now filter on creation date if needed
                if (createMin != null || createMax != null) {
                    if (!isBetweenDates(session.getCreationDate(), createMin, createMax)) {
                        session = null;
                    }
                }
                // Now filter on activity date if needed
                if ((activityMin != null || activityMax != null) && session != null) {
                    if (!isBetweenDates(session.getLastActiveDate(), activityMin, activityMax)) {
                        session = null;
                    }
                }
                if (session != null) {
                    if (!isBetweenPacketCount(session.getNumClientPackets(),
                            filter.getClientPacketRangeMin(),
                            filter.getClientPacketRangeMax())) {
                        session = null;
                    }
                }
                if (session != null) {
                    if (!isBetweenPacketCount(session.getNumServerPackets(),
                            filter.getServerPacketRangeMin(),
                            filter.getServerPacketRangeMax())) {
                        session = null;
                    }
                }
                if (session != null) {
                    filteredResults.add(session);
                }
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
     * Returns the list of sessions that were originated by a remote server. The list will be
     * ordered chronologically.  IncomingServerSession can only receive packets from the remote
     * server but are not capable of sending packets to the remote server.
     *
     * @param hostname the name of the remote server.
     * @return the sessions that were originated by a remote server.
     */
    public List<IncomingServerSession> getIncomingServerSessions(String hostname) {
        List<IncomingServerSession> sessions = incomingServerSessions.get(hostname);
        if (sessions == null) {
            return Collections.emptyList();
        }
        else {
            return Collections.unmodifiableList(sessions);
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
        return outgoingServerSessions.get(hostname);
    }

    /**
     * <p>Determines if the given date is before the min date, or after the max date.</p>
     * <p>The check is complicated somewhat by the fact that min can be null indicating
     * no earlier date, and max can be null indicating no upper limit.</p>
     *
     * @param date The date to check
     * @param min  The date must be after min, or any if min is null
     * @param max  The date must be before max, or any if max is null
     * @return True if the date is between min and max
     */
    private boolean isBetweenDates(Date date, Date min, Date max) {
        boolean between = true;
        if (min != null) {
            if (date.before(min)) {
                between = false;
            }
        }
        if (max != null && between) {
            if (date.after(max)) {
                between = false;
            }
        }
        return between;
    }

    /**
     * <p>Determines if the given count is before the min count, or after the max count.</p>
     * <p>The check is complicated somewhat by the fact that min or max
     * can be SessionResultFilter.NO_PACKET_LIMIT indicating no limit.</p>
     *
     * @param count The count to check
     * @param min   The count must be over min, or any if min is SessionResultFilter.NO_PACKET_LIMIT
     * @param max   The count must be under max, or any if max is SessionResultFilter.NO_PACKET_LIMIT
     * @return True if the count is between min and max
     */
    private boolean isBetweenPacketCount(long count, long min, long max) {
        boolean between = true;
        if (min != SessionResultFilter.NO_PACKET_LIMIT) {
            if (count < min) {
                between = false;
            }
        }
        if (max != SessionResultFilter.NO_PACKET_LIMIT && between) {
            if (count > max) {
                between = false;
            }
        }
        return between;
    }

    private void copyAnonSessions(List<ClientSession> sessions) {
        // Add anonymous sessions
        for (ClientSession session : anonymousSessions.values()) {
            sessions.add(session);
        }
    }

    private void copyUserSessions(List<ClientSession> sessions) {
        // Get a copy of the sessions from all users
        for (String username : getSessionUsers()) {
            for (ClientSession session : getSessions(username)) {
                sessions.add(session);
            }
        }
    }

    private void copyUserSessions(String username, List<ClientSession> sessionList) {
        // Get a copy of the sessions from all users
        SessionMap sessionMap = sessions.get(username);
        if (sessionMap != null) {
            for (ClientSession session : sessionMap.getSessions()) {
                sessionList.add(session);
            }
        }
    }

    public Iterator getAnonymousSessions() {
        return Arrays.asList(anonymousSessions.values().toArray()).iterator();
    }

    public Collection<ClientSession> getSessions(String username) {
        List<ClientSession> sessionList = new ArrayList<ClientSession>();
        if (username != null) {
            copyUserSessions(username, sessionList);
        }
        return sessionList;
    }

    public int getTotalSessionCount() {
        return sessionCount;
    }

    public int getSessionCount() {
        int sessionCount = 0;
        for (String username : getSessionUsers()) {
            sessionCount += getSessionCount(username);
        }
        sessionCount += anonymousSessions.size();
        return sessionCount;
    }

    public int getAnonymousSessionCount() {
        return anonymousSessions.size();
    }

    public int getSessionCount(String username) {
        if (username == null) {
            return 0;
        }
        int sessionCount = 0;
        SessionMap sessionMap = sessions.get(username);
        if (sessionMap != null) {
            sessionCount = sessionMap.resources.size();
        }
        return sessionCount;
    }

    public Collection<String> getSessionUsers() {
        return Collections.unmodifiableCollection(sessions.keySet());
    }

    /**
     * Returns a collection with the established sessions from external components.
     *
     * @return a collection with the established sessions from external components.
     */
    public Collection<ComponentSession> getComponentSessions() {
        return Collections.unmodifiableCollection(componentsSessions);
    }

    /**
     * Returns the session of the component whose domain matches the specified domain.
     *
     * @param domain the domain of the component session to look for.
     * @return the session of the component whose domain matches the specified domain.
     */
    public ComponentSession getComponentSession(String domain) {
        for (ComponentSession session : componentsSessions) {
            if (domain.equals(session.getAddress().getDomain())) {
                return session;
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
        return Collections.unmodifiableCollection(incomingServerSessions.keySet());
    }

    /**
     * Returns a collection with the hostnames of the remote servers that currently may receive
     * packets sent from this server.
     *
     * @return a collection with the hostnames of the remote servers that currently may receive
     *         packets sent from this server.
     */
    public Collection<String> getOutgoingServers() {
        return Collections.unmodifiableCollection(outgoingServerSessions.keySet());
    }

    /**
     * Broadcasts the given data to all connected sessions. Excellent
     * for server administration messages.
     *
     * @param packet The packet to be broadcast
     */
    public void broadcast(Packet packet) throws UnauthorizedException {
        for (SessionMap sessionMap : sessions.values()) {
            ((SessionMap) sessionMap).broadcast(packet);
        }

        for (Session session : anonymousSessions.values()) {
            session.process(packet);
        }
    }

    /**
     * Broadcasts the given data to all connected sessions for a particular
     * user. Excellent for updating all connected resources for users such as
     * roster pushes.
     *
     * @param packet The packet to be broadcast
     */
    public void userBroadcast(String username, Packet packet) throws UnauthorizedException, PacketException {
        SessionMap sessionMap = sessions.get(username);
        if (sessionMap != null) {
            sessionMap.broadcast(packet);
        }
    }

    /**
     * Removes a session.
     *
     * @param session the session.
     */
    public void removeSession(ClientSession session) {
        // TODO: Requires better error checking to ensure the session count is maintained
        // TODO: properly (removal actually does remove).
        // Do nothing if session is null or if the server is shutting down. Note: When the server
        // is shutting down the serverName will be null.
        if (session == null || serverName == null) {
            return;
        }
        SessionMap sessionMap = null;
        if (anonymousSessions.containsValue(session)) {
            anonymousSessions.remove(session.getAddress().getResource());
            sessionCount--;
            
            // Fire session event.
            SessionEventDispatcher.dispatchEvent(session,
                    SessionEventDispatcher.EventType.anonymous_session_destroyed);
        }
        else {
            // If this is a non-anonymous session then remove the session from the SessionMap
            if (session.getAddress() != null && session.getAddress().getNode() != null) {
                String username = session.getAddress().getNode().toLowerCase();
                synchronized (username.intern()) {
                    sessionMap = sessions.get(username);
                    if (sessionMap != null) {
                        sessionMap.removeSession(session);
                        sessionCount--;
                        if (sessionMap.isEmpty()) {
                            sessions.remove(username);
                        }
                        
                        // Fire session event.
                        SessionEventDispatcher.dispatchEvent(session,
                                SessionEventDispatcher.EventType.session_destroyed);
                    }
                }
            }
        }
        // If the user is still available then send an unavailable presence
        Presence presence = session.getPresence();
        if (presence == null || presence.isAvailable()) {
            Presence offline = new Presence();
            offline.setFrom(session.getAddress());
            offline.setTo(new JID(null, serverName, null));
            offline.setType(Presence.Type.unavailable);
            router.route(offline);
        }
        else if (preAuthenticatedSessions.containsValue(session)) {
            // Remove the session from the pre-Authenticated sessions list
            preAuthenticatedSessions.remove(session.getAddress().toString());
        }
    }

    public void addAnonymousSession(ClientSession session) {
        anonymousSessions.put(session.getAddress().getResource(), session);
        // Remove the session from the pre-Authenticated sessions list
        preAuthenticatedSessions.remove(session.getAddress().toString());
        
        // Fire session event.
        SessionEventDispatcher.dispatchEvent(session,
                SessionEventDispatcher.EventType.anonymous_session_created);
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
        return preAuthenticatedSessions.keySet();
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
                ClientSession session = (ClientSession)handback;
                try {
                    if (session.getPresence().isAvailable() || !session.wasAvailable()) {
                        // Send an unavailable presence to the user's subscribers
                        // Note: This gives us a chance to send an unavailable presence to the
                        // entities that the user sent directed presences
                        Presence presence = new Presence();
                        presence.setType(Presence.Type.unavailable);
                        presence.setFrom(session.getAddress());
                        presenceHandler.process(presence);
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
            ComponentSession session = (ComponentSession)handback;
            try {
                // Unbind the domain for this external component
                String domain = session.getAddress().getDomain();
                String subdomain = domain.substring(0, domain.indexOf(serverName) - 1);
                InternalComponentManager.getInstance().removeComponent(subdomain);
            }
            catch (Exception e) {
                // Can't do anything about this problem...
                Log.error(LocaleUtils.getLocalizedString("admin.error.close"), e);
            }
            finally {
                // Remove the session
                componentsSessions.remove(session);
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
                unregisterOutgoingServerSession(hostname);
                // Remove the route to the session using the hostname
                XMPPServer.getInstance().getRoutingTable().removeRoute(new JID(hostname));
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        presenceHandler = server.getPresenceUpdateHandler();
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
        // Run through the server sessions every 5 minutes after a 5 minutes server
        // startup delay (default values)
        serverCleanupTask = new ServerCleanupTask();
        timer.schedule(serverCleanupTask, getServerSessionTimeout(), getServerSessionTimeout());
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
        try {
            if (address == null || address.getNode() == null || address.getNode().length() < 1) {
                broadcast(packet);
            }
            else if (address.getResource() == null || address.getResource().length() < 1) {
                userBroadcast(address.getNode(), packet);
            }
            else {
                ClientSession session = getSession(address);
                if (session != null) {
                    session.process(packet);
                }
            }
        }
        catch (UnauthorizedException e) {
            // Ignore.
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

    public void stop() {
        Log.debug("Stopping server");
        // Stop threads that are sending packets to remote servers
        OutgoingSessionPromise.getInstance().shutdown();
        timer.cancel();
        serverName = null;
        if (JiveGlobals.getBooleanProperty("shutdownMessage.enabled")) {
            sendServerMessage(null, LocaleUtils.getLocalizedString("admin.shutdown.now"));
        }
        try {
            for (Session session : getSessions()) {
                try {
                    session.getConnection().close();
                }
                catch (Throwable t) {
                    // Ignore.
                }
            }
        }
        catch (Exception e) {
            // Ignore.
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
        // Cancel the existing task because the timeout has changed
        if (serverCleanupTask != null) {
            serverCleanupTask.cancel();
        }
        // Create a new task and schedule it with the new timeout
        serverCleanupTask = new ServerCleanupTask();
        timer.schedule(serverCleanupTask, getServerSessionTimeout(), getServerSessionTimeout());
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
    }

    public int getServerSessionIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.server.session.idle", 10 * 60 * 1000);
    }

    /**
     * Task that closes the idle server sessions.
     */
    private class ServerCleanupTask extends TimerTask {
        /**
         * Close outgoing server sessions that have been idle for a long time.
         */
        public void run() {
            // Do nothing if this feature is disabled
            if (getServerSessionIdleTime() == -1) {
                return;
            }
            final long deadline = System.currentTimeMillis() - getServerSessionIdleTime();
            // Check outgoing server sessions
            for (OutgoingServerSession session : outgoingServerSessions.values()) {
                try {
                    if (session.getLastActiveDate().getTime() < deadline) {
                        session.getConnection().close();
                    }
                }
                catch (Throwable e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
            // Check incoming server sessions
            for (List<IncomingServerSession> sessions : incomingServerSessions.values()) {
                for (IncomingServerSession session : sessions) {
                    try {
                        if (session.getLastActiveDate().getTime() < deadline) {
                            session.getConnection().close();
                        }
                    }
                    catch (Throwable e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
    }
}
