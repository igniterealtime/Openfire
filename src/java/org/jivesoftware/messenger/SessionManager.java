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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.messenger.audit.AuditStreamIDFactory;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
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
        private Map<String,ClientSession> resources = new HashMap<String,ClientSession>();
        private LinkedList priorityList = new LinkedList();

        /**
         * Add a session to the manager.
         *
         * @param session
         */
        void addSession(ClientSession session) {
            String resource = session.getAddress().getResource();
            resources.put(resource, session);
            Presence presence = session.getPresence();
            int priority = presence == null ? 0 : presence.getPriority();
            sortSession(resource, priority);
        }

        /**
         * Sorts the session into the list based on priority
         *
         * @param resource The resource corresponding to the session to sort
         * @param priority The priority to use for sorting
         */
        private void sortSession(String resource, int priority) {

            if (priorityList.size() > 0) {
                Iterator iter = priorityList.iterator();
                for (int i = 0; iter.hasNext(); i++) {
                    ClientSession sess = resources.get(iter.next());
                    if (sess.getPresence().getPriority() <= priority) {
                        priorityList.add(i, resource);
                        break;
                    }
                }
            }
            if (!priorityList.contains(resource)) {
                priorityList.addLast(resource);
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
                priorityList.remove(resource);
                sortSession(resource, priority);
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
            priorityList.remove(resource);
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
                for (int i=0; i < priorityList.size(); i++) {
                    ClientSession s = resources.get(priorityList.get(i));
                    if (s.getPresence().isAvailable()) {
                        return s;
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
                session.getConnection().deliver(packet);
            }
        }

        /**
         * Create an iterator over all sessions for the user.
         * We create a new list to generate the iterator so other threads
         * may safely alter the session map without affecting the iterator.
         *
         * @return An iterator of all sessions
         */
        public Iterator getSessions() {
            LinkedList<Session> list = new LinkedList<Session>();
            for (Session session : resources.values()) {
                list.add(session);
            }
            return list.iterator();
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
        StreamID id = streamIDFactory.createStreamID();
        ClientSession session = new ClientSession(serverName, conn, id);
        conn.init(session);
        // Register to receive close notification on this session so we can
        // remove its route from the sessions set and also send an unavailable presence if it wasn't
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
        StreamID id = streamIDFactory.createStreamID();
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
                defaultSession = sessions.get(session.getUsername()).getDefaultSession(true);
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
            catch (UnauthorizedException e) {
                // Do nothing
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
            throws UserNotFoundException, UnauthorizedException {
        Presence presence = null;
        SessionMap sessionMap = sessions.get(session.getUsername());
        if (sessionMap != null) {
            for (ClientSession userSession : sessionMap.getAvailableSessions()) {
                if (userSession != session) {
                    // Send the presence of an existing session to the session that has just changed
                    // the presence
                    if (session.getPresence().isAvailable()) {
                        presence = userSession.getPresence().createCopy();
                        presence.setTo(session.getAddress());
                        session.getConnection().deliver(presence);
                    }
                    // Send the presence of the session whose presence has changed to this other
                    // user's session
                    presence = session.getPresence().createCopy();
                    presence.setTo(userSession.getAddress());
                    userSession.getConnection().deliver(presence);
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
                    try {
                        routingTable.getRoute(userJID);
                        // Remove the route for the session's BARE address
                        routingTable.removeRoute(new JID(session.getAddress().getNode(),
                                session.getAddress().getDomain(), ""));
                    }
                    catch (NoSuchRouteException e) {
                        // Do nothing since the routingTable does not have routes to this user
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
            catch (UnauthorizedException e) {
                // Do nothing
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
        if (!serverName.equals(recipient.getDomain())) {
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

    public boolean isActiveRoute(JID route) {
        boolean hasRoute = false;
        String resource = route.getResource();
        String username = route.getNode();

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
        // Return null if the JID is null or belongs to a foreign server
        if (from == null || !serverName.equals(from.getDomain())) {
            return null;
        }

        ClientSession session = null;
        // Initially Check preAuthenticated Sessions
        session = preAuthenticatedSessions.get(from.toString());
        if(session != null){
            return (ClientSession)session;
        }

        String resource = from.getResource();
        if (resource == null) {
            return null;
        }
        String username = from.getNode();
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
        if (session == null) {
            return null;
        }
        return session;
    }


    public Collection<Session> getSessions() {
        List<Session> allSessions = new ArrayList<Session>();
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

    private void copyAnonSessions(List sessions) {
        // Add anonymous sessions
        for (Session session : anonymousSessions.values()) {
            sessions.add(session);
        }
    }

    private void copyUserSessions(List sessions) {
        // Get a copy of the sessions from all users
        for (String username : getSessionUsers()) {
            Collection<ClientSession> usrSessions = getSessions(username);
            for (Session session : usrSessions) {
                sessions.add(session);
            }
        }
    }

    private void copyUserSessions(String username, List sessionList) {
        // Get a copy of the sessions from all users
        SessionMap sessionMap = sessions.get(username);
        if (sessionMap != null) {
            Iterator sessionItr = sessionMap.getSessions();
            while (sessionItr.hasNext()) {
                sessionList.add(sessionItr.next());
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
     * Broadcasts the given data to all connected sessions. Excellent
     * for server administration messages.
     *
     * @param packet The packet to be broadcast
     */
    public void broadcast(Packet packet) throws UnauthorizedException {
        Iterator values = sessions.values().iterator();
        while (values.hasNext()) {
            ((SessionMap)values.next()).broadcast(packet);
        }

        for (Session session : anonymousSessions.values()) {
            session.getConnection().deliver(packet);
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
        if (session == null) {
            return;
        }
        SessionMap sessionMap = null;
        if (anonymousSessions.containsValue(session)) {
            anonymousSessions.remove(session.getAddress().getResource());
            sessionCount--;
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
    }

    public int getConflictKickLimit() {
        return conflictLimit;
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
                if (session.getPresence().isAvailable()) {
                    // Send an unavailable presence to the user's subscribers
                    // Note: This gives us a chance to send an unavailable presence to the
                    // entities that the user sent directed presences
                    Presence presence = new Presence();
                    presence.setType(Presence.Type.unavailable);
                    presence.setFrom(session.getAddress());
                    presenceHandler.process(presence);
                }
                // Remove the session
                removeSession(session);
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
            try {
                ComponentSession session = (ComponentSession)handback;
                // Unbind the domain for this external component
                InternalComponentManager.getInstance().removeComponent(session.getAddress().getDomain());
                // Remove the session
                componentsSessions.remove(session);
            }
            catch (Exception e) {
                // Can't do anything about this problem...
                Log.error(LocaleUtils.getLocalizedString("admin.error.close"), e);
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
    }


    public void sendServerMessage(String subject, String body) {
        try {
            sendServerMessage(null, subject, body);
        }
        catch (SessionNotFoundException e) {
        }
    }

    public void sendServerMessage(JID address, String subject, String body) throws SessionNotFoundException {
        Packet packet = createServerMessage(subject, body);
        try {
            if (address == null || address.getNode() == null || address.getNode().length() < 1) {
                broadcast(packet);
            }
            else if (address.getResource() == null || address.getResource().length() < 1) {
                userBroadcast(address.getNode(), packet);
            }
            else {
                getSession(address).getConnection().deliver(packet);
            }
        }
        catch (Exception e) {
        }
    }

    private Packet createServerMessage(String subject, String body) {
        Message message = new Message();
        message.setFrom(serverAddress);
        if (subject != null) {
            message.setSubject(subject);
        }
        message.setBody(body);
        return message;
    }

    public void stop() {
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
                }
            }
        }
        catch (Exception e) {
        }
    }
}
