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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.stream.XMLStreamException;
import org.jivesoftware.messenger.audit.AuditStreamIDFactory;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.spi.BasicStreamIDFactory;
import org.jivesoftware.messenger.spi.PacketTransporterImpl;
import org.jivesoftware.messenger.spi.SessionImpl;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
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
public class SessionManager implements ConnectionCloseListener {
    private int sessionCount = 0;
    public static final int NEVER_KICK = -1;

    public XMPPServer server;
    public PacketRouter router;
    public PacketTransporterImpl transporter;
    private String serverName;
    private JID serverAddress;
    public UserManager userManager;
    private int conflictLimit;
    private Random randomResource = new Random();

    private static SessionManager singleton;
    private static final Object LOCK = new Object();

    /**
     * Returns the singleton instance of <CODE>SessionManagerImpl</CODE>,
     * <p/>
     * creating it if necessary.
     * <p/>
     * <p/>
     *
     * @return the singleton instance of <Code>SessionManagerImpl</CODE>
     */
    public static SessionManager getInstance() {
        // Synchronize on LOCK to ensure that we don't end up creating
        // two singletons.
        synchronized (LOCK) {
            if (null == singleton) {
                SessionManager manager = new SessionManager();
                singleton = manager;
                return manager;
            }
        }
        return singleton;
    }

    private SessionManager() {
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
     * The standard Jive reader/writer lock to synchronize access to
     * the session set.
     */
    private ReadWriteLock sessionLock = new ReentrantReadWriteLock();

    /**
     * Map of priority ordered SessionMap objects with username (toLowerCase) as key.
     * The map and its contents should NOT be persisted to disk.
     */
    private Map<String, SessionMap> sessions = new ConcurrentHashMap<String, SessionMap>();

    /**
     * <p>Session manager must maintain the routing table as sessions are added and
     * removed.</p>
     */
    public RoutingTable routingTable;

    /**
     * The standard Jive reader/writer lock to synchronize access to
     * the anonymous session set.
     */
    private ReadWriteLock anonymousSessionLock = new ReentrantReadWriteLock();

    /**
     * Map of anonymous server sessions. They need to be treated separately as they
     * have no associated user, and don't follow the normal routing rules for
     * priority based fall over.
     */
    private Map anonymousSessions = new HashMap();

    /**
     * Simple data structure to track sessions for a single user (tracked by resource
     * and priority).
     */
    private class SessionMap {
        private Map resources = new HashMap();
        private LinkedList priorityList = new LinkedList();

        /**
         * Add a session to the manager.
         *
         * @param session
         */
        void addSession(Session session) {
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
                    Session sess = (Session)resources.get(iter.next());
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
        Session getSession(String resource) {
            return (Session)resources.get(resource);
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
         * Returns the default session for the user based on presence
         * priority.
         *
         * @return The default session for the user.
         */
        Session getDefaultSession() {
            if (priorityList.isEmpty()) {
                return null;
            }

//            return (Session) resources.get(priorityList.getFirst());
            Session s = (Session)resources.get(priorityList.getFirst());
            return s;
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
        private void broadcast(Packet packet) throws UnauthorizedException, PacketException, XMLStreamException {
            Iterator entries = resources.values().iterator();
            while (entries.hasNext()) {
                Session session = (Session)entries.next();
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
            LinkedList list = new LinkedList();
            Iterator entries = resources.values().iterator();
            while (entries.hasNext()) {
                list.add(entries.next());
            }
            return list.iterator();
        }
    }

    private StreamIDFactory streamIDFactory;

    public Session createSession(Connection conn) throws UnauthorizedException {
        if (serverName == null) {
            throw new UnauthorizedException("Server not initialized");
        }
        StreamID id = streamIDFactory.createStreamID();
        Session session = new SessionImpl(serverName, conn, id);
        conn.init(session);
        conn.registerCloseListener(this, session);
        return session;
    }

    /**
     * Add a new session to be managed.
     */
    public boolean addSession(Session session) {
        boolean success = false;
        sessionLock.writeLock().lock();
        String username = session.getAddress().getNode().toLowerCase();
        SessionMap resources = null;
        try {
            resources = (SessionMap)sessions.get(username);
            if (resources == null) {
                resources = new SessionMap();
                sessions.put(username, resources);
            }
            resources.addSession(session);
            // Register to recieve close notification on this session so we can
            // remove its route from the sessions set. We hand the session back
            // to ourselves in the message.
            session.getConnection().registerCloseListener(this, session);
            success = true;
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            sessionLock.writeLock().unlock();
        }
        if (success) {
            Session defaultSession = resources.getDefaultSession();
            routingTable.addRoute(new JID(defaultSession.getAddress().getNode(), defaultSession.getAddress().getDomain(), ""),
                    defaultSession);
            routingTable.addRoute(session.getAddress(), session);
        }
        return success;
    }

    /**
     * Change the priority of a session associated with the sender.
     *
     * @param sender   The sender who's session just changed priority
     * @param priority The new priority for the session
     */
    public void changePriority(JID sender, int priority) {
        String username = sender.getNode().toLowerCase();
        sessionLock.writeLock().lock();
        try {
            SessionMap resources = (SessionMap)sessions.get(username);
            if (resources == null) {
                return;
            }
            resources.changePriority(sender, priority);

            // Get the session with highest priority
            Session defaultSession = resources.getDefaultSession();
            // Update the route to the bareJID with the session with highest priority
            routingTable.addRoute(new JID(defaultSession.getAddress().getNode(), defaultSession.getAddress().getDomain(), ""),
                    defaultSession);
        }
        finally {
            sessionLock.writeLock().unlock();
        }
    }


    /**
     * Retrieve the best route to deliver packets to this session
     * given the recipient jid. If no active routes exist, this method
     * returns a reference to itself (the account can store the packet).
     * A null recipient chooses the default active route for this account
     * if one exists. If the recipient can't be reached by this account
     * (wrong account) an exception is thrown.
     *
     * @param recipient The recipient ID to send to or null to select the default route
     * @return The XMPPAddress best suited to use for delivery to the recipient
     */
    public Session getBestRoute(JID recipient) {
        Session session = null;
        String resource = recipient.getResource();
        String username = recipient.getNode();
        if (username == null || "".equals(username)) {
            if (resource != null) {
                anonymousSessionLock.readLock().lock();
                try {
                    session = (Session)anonymousSessions.get(resource);
                }
                finally {
                    anonymousSessionLock.readLock().unlock();
                }
            }
        }
        else {
            username = username.toLowerCase();
            sessionLock.readLock().lock();
            try {
                SessionMap sessionMap = (SessionMap)sessions.get(username);
                if (sessionMap != null) {
                    if (resource == null) {
                        session = sessionMap.getDefaultSession();
                    }
                    else {
                        session = sessionMap.getSession(resource);
                        if (session == null) {
                            session = sessionMap.getDefaultSession();
                        }
                    }
                }
            }
            finally {
                sessionLock.readLock().unlock();
            }
        }
        return session;
    }

    public boolean isActiveRoute(JID route) {
        boolean hasRoute = false;
        String resource = route.getResource();
        String username = route.getNode();

        if (username == null || "".equals(username)) {
            if (resource != null) {
                anonymousSessionLock.readLock().lock();
                try {
                    hasRoute = anonymousSessions.containsKey(resource);
                }
                finally {
                    anonymousSessionLock.readLock().unlock();
                }
            }
        }
        else {
            username = username.toLowerCase();
            Session session = null;
            sessionLock.readLock().lock();
            try {
                SessionMap sessionMap = (SessionMap)sessions.get(username);
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
            finally {
                sessionLock.readLock().unlock();
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
     * @param from the sender of the packet.
     * @return the <code>Session</code> associated with the JID.
     */
    public Session getSession(JID from) {
        Session session = null;
        String resource = from.getResource();
        if (resource == null) {
            return null;
        }
        String username = from.getNode();
        if (username == null || "".equals(username)) {
            anonymousSessionLock.readLock().lock();
            try {
                session = (Session)anonymousSessions.get(resource);
            }
            finally {
                anonymousSessionLock.readLock().unlock();
            }
        }
        else {
            username = username.toLowerCase();
            sessionLock.readLock().lock();
            try {
                SessionMap sessionMap = (SessionMap)sessions.get(username);
                if (sessionMap != null) {
                    session = sessionMap.getSession(resource);
                }
            }
            finally {
                sessionLock.readLock().unlock();
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


    public Collection<Session> getSessions(SessionResultFilter filter) {
        List<Session> results = new ArrayList<Session>();
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
            List<Session> filteredResults = new ArrayList<Session>();
            for (Session session : results) {
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
            List<Session> finalResults = new ArrayList<Session>();
            int startIndex = filter.getStartIndex();
            Iterator<Session> sortedIter = filteredResults.iterator();
            for (int i = 0; sortedIter.hasNext() && finalResults.size() < maxResults; i++) {
                Session result = sortedIter.next();
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
        anonymousSessionLock.readLock().lock();
        try {
            Iterator sessionItr = anonymousSessions.values().iterator();
            while (sessionItr.hasNext()) {
                sessions.add(sessionItr.next());
            }
        }
        finally {
            anonymousSessionLock.readLock().unlock();
        }
    }

    private void copyUserSessions(List sessions) {
        // Get a copy of the sessions from all users
        sessionLock.readLock().lock();
        try {
            Iterator users = getSessionUsers();
            while (users.hasNext()) {
                Collection<Session> usrSessions = getSessions((String)users.next());
                for (Session session : usrSessions) {
                    sessions.add(session);
                }
            }
        }
        finally {
            sessionLock.readLock().unlock();
        }
    }

    private void copyUserSessions(String username, List sessionList) {
        // Get a copy of the sessions from all users
        sessionLock.readLock().lock();
        try {
            SessionMap sessionMap = (SessionMap)sessions.get(username);
            if (sessionMap != null) {
                Iterator sessionItr = sessionMap.getSessions();
                while (sessionItr.hasNext()) {
                    sessionList.add(sessionItr.next());
                }
            }
        }
        finally {
            sessionLock.readLock().unlock();
        }
    }

    public Iterator getAnonymousSessions() {
        return Arrays.asList(anonymousSessions.values().toArray()).iterator();
    }

    public Collection<Session> getSessions(String username) {
        List<Session> sessionList = new ArrayList<Session>();
        if (username != null) {
            copyUserSessions(username, sessionList);
        }
        return sessionList;
    }

    public int getTotalSessionCount() throws UnauthorizedException {
        return sessionCount;
    }

    public int getSessionCount() throws UnauthorizedException {
        int sessionCount = 0;
        Iterator users = getSessionUsers();
        while (users.hasNext()) {
            sessionCount += getSessionCount((String)users.next());
        }
        sessionCount += anonymousSessions.size();
        return sessionCount;
    }

    public int getAnonymousSessionCount() throws UnauthorizedException {
        return anonymousSessions.size();
    }

    public int getSessionCount(String username) throws UnauthorizedException {
        int sessionCount = 0;
        sessionLock.readLock().lock();
        try {
            SessionMap sessionMap = (SessionMap)sessions.get(username);
            if (sessionMap != null) {
                sessionCount = sessionMap.resources.size();
            }
        }
        finally {
            sessionLock.readLock().unlock();
        }
        return sessionCount;
    }

    public Iterator getSessionUsers() {
        return Arrays.asList(sessions.keySet().toArray()).iterator();
    }

    /**
     * Broadcasts the given data to all connected sessions. Excellent
     * for server administration messages.
     *
     * @param packet The packet to be broadcast
     */
    public void broadcast(Packet packet) throws UnauthorizedException, PacketException, XMLStreamException {
        sessionLock.readLock().lock();
        try {
            Iterator values = sessions.values().iterator();
            while (values.hasNext()) {
                ((SessionMap)values.next()).broadcast(packet);
            }
        }
        finally {
            sessionLock.readLock().unlock();
        }
        anonymousSessionLock.readLock().lock();
        try {
            Iterator values = anonymousSessions.values().iterator();
            while (values.hasNext()) {
                ((Session)values.next()).getConnection().deliver(packet);
            }
        }
        finally {
            anonymousSessionLock.readLock().unlock();
        }
    }

    /**
     * Broadcasts the given data to all connected sessions for a particular
     * user. Excellent for updating all connected resources for users such as
     * roster pushes.
     *
     * @param packet The packet to be broadcast
     */
    public void userBroadcast(String username, Packet packet) throws UnauthorizedException, PacketException, XMLStreamException {
        sessionLock.readLock().lock();
        try {
            SessionMap sessionMap = (SessionMap)sessions.get(username);
            if (sessionMap != null) {
                sessionMap.broadcast(packet);
            }
        }
        finally {
            sessionLock.readLock().unlock();
        }
    }

    /**
     * TODO Requires better error checking to ensure the session count is maintained properly (removal actually does remove)
     *
     * @param session
     * @throws UnauthorizedException
     */
    public void removeSession(Session session) throws UnauthorizedException {
        if (session == null) {
            return;
        }
        SessionMap sessionMap = null;
        if (anonymousSessions.containsValue(session)) {
            anonymousSessionLock.writeLock().lock();
            try {
                anonymousSessions.remove(session.getAddress().getResource());
                sessionCount--;
            }
            finally {
                anonymousSessionLock.writeLock().unlock();
            }
        }
        else {
            if (session.getAddress() != null && session.getAddress().getNode() != null) {
                String username = session.getAddress().getNode().toLowerCase();
                sessionLock.writeLock().lock();
                try {
                    sessionMap = (SessionMap)sessions.get(username);
                    if (sessionMap != null) {
                        sessionMap.removeSession(session);
                        sessionCount--;
                        if (sessionMap.isEmpty()) {
                            sessions.remove(username);
                        }
                    }
                }
                finally {
                    sessionLock.writeLock().unlock();
                }
            }
        }

        Presence presence = session.getPresence();
        if (presence == null || presence.isAvailable()) {
            Presence offline = packetFactory.getPresence();
            offline.setFrom(session.getAddress());
            offline.setTo(new JID(null, serverName, null));
            offline.setType(Presence.Type.unavailable);
            router.route(offline);
        }
        if (session.getAddress() != null && routingTable != null && session.getAddress().toBareJID().trim().length() != 0) {
            routingTable.removeRoute(session.getAddress());
            if (sessionMap != null) {
                if (sessionMap.isEmpty()) {
                    // Remove the route for the session's BARE address
                    routingTable.removeRoute(new JID(session.getAddress().getNode(), session.getAddress().getDomain(), ""));
                }
                else {
                    // Update the route for the session's BARE address
                    Session defaultSession = sessionMap.getDefaultSession();
                    routingTable.addRoute(new JID(defaultSession.getAddress().getNode(), defaultSession.getAddress().getDomain(), ""),
                            defaultSession);
                }
            }
        }
    }

    public void addAnonymousSession(Session session) {
        try {
            JID newAddress = new JID(session.getAddress().getNode(), session.getAddress().getDomain(),
                    Integer.toHexString(randomResource.nextInt()));
            session.setAddress(newAddress);
            anonymousSessionLock.writeLock().lock();
            try {
                anonymousSessions.put(session.getAddress().getResource(), session);
                session.getConnection().registerCloseListener(this, session);
            }
            finally {
                anonymousSessionLock.writeLock().unlock();
            }
            routingTable.addRoute(session.getAddress(), session);
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public int getConflictKickLimit() {
        return conflictLimit;
    }

    public void setConflictKickLimit(int limit) {
        conflictLimit = limit;
        JiveGlobals.setProperty("xmpp.session.conflict-limit", Integer.toString(conflictLimit));
    }

    /**
     * Handle a session that just closed.
     *
     * @param handback The session that just closed
     */
    public void onConnectionClose(Object handback) {
        try {
            Session session = (Session)handback;
            removeSession(session);
        }
        catch (UnauthorizedException e) {
            // Do nothing
        }
        catch (Exception e) {
            // Can't do anything about this problem...
            Log.error(LocaleUtils.getLocalizedString("admin.error.close"), e);
        }
    }

    public PacketFactory packetFactory;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(XMPPServer.class, "server");
        trackInfo.getTrackerClasses().put(PacketRouter.class, "router");
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof XMPPServer && server != null) {
            serverName = server.getServerInfo().getName();
            serverAddress = new JID(serverName);
        }
    }

    public void serviceRemoved(Object service) {
        if (server == null) {
            serverName = null;
        }
    }

    public void initialize(Container container) {
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
        sendServerMessage(null, LocaleUtils.getLocalizedString("admin.shutdown.now"));
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
