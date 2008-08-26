/**
 * $RCSfile: RoutingTableImpl.java,v $
 * $Revision: 3138 $
 * $Date: 2005-12-01 02:13:26 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * Routing table that stores routes to client sessions, outgoing server sessions
 * and components. As soon as a user authenticates with the server its client session
 * will be added to the routing table. Whenever the client session becomes available
 * or unavailable the routing table will be updated too.<p>
 *
 * When running inside of a cluster the routing table will also keep references to routes
 * hosted in other cluster nodes. A {@link RemotePacketRouter} will be use to route packets
 * to routes hosted in other cluster nodes.<p>
 *
 * Failure to route a packet will end up sending {@link IQRouter#routingFailed(JID, Packet)},
 * {@link MessageRouter#routingFailed(JID, Packet)} or {@link PresenceRouter#routingFailed(JID, Packet)}
 * depending on the packet type that tried to be sent.
 *
 * @author Gaston Dombiak
 */
public class RoutingTableImpl extends BasicModule implements RoutingTable, ClusterEventListener {

    public static final String C2S_CACHE_NAME = "Routing Users Cache";
    public static final String ANONYMOUS_C2S_CACHE_NAME = "Routing AnonymousUsers Cache";
    public static final String S2S_CACHE_NAME = "Routing Servers Cache";
    public static final String COMPONENT_CACHE_NAME = "Routing Components Cache";

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     * Key: server domain, Value: nodeID
     */
    private Cache<String, byte[]> serversCache;
    /**
     * Cache (unlimited, never expire) that holds components connected to the server.
     * Key: component domain, Value: list of nodeIDs hosting the component
     */
    private Cache<String, Set<NodeID>> componentsCache;
    /**
     * Cache (unlimited, never expire) that holds sessions of user that have authenticated with the server.
     * Key: full JID, Value: {nodeID, available/unavailable}
     */
    private Cache<String, ClientRoute> usersCache;
    /**
     * Cache (unlimited, never expire) that holds sessions of anonymous user that have authenticated with the server.
     * Key: full JID, Value: {nodeID, available/unavailable}
     */
    private Cache<String, ClientRoute> anonymousUsersCache;
    /**
     * Cache (unlimited, never expire) that holds list of connected resources of authenticated users
     * (includes anonymous).
     * Key: bare JID, Value: list of full JIDs of the user
     */
    private Cache<String, Collection<String>> usersSessions;

    private String serverName;
    private XMPPServer server;
    private LocalRoutingTable localRoutingTable;
    private RemotePacketRouter remotePacketRouter;
    private IQRouter iqRouter;
    private MessageRouter messageRouter;
    private PresenceRouter presenceRouter;
    private PresenceUpdateHandler presenceUpdateHandler;

    public RoutingTableImpl() {
        super("Routing table");
        serversCache = CacheFactory.createCache(S2S_CACHE_NAME);
        componentsCache = CacheFactory.createCache(COMPONENT_CACHE_NAME);
        usersCache = CacheFactory.createCache(C2S_CACHE_NAME);
        anonymousUsersCache = CacheFactory.createCache(ANONYMOUS_C2S_CACHE_NAME);
        usersSessions = CacheFactory.createCache("Routing User Sessions");
        localRoutingTable = new LocalRoutingTable();
    }

    public void addServerRoute(JID route, LocalOutgoingServerSession destination) {
        String address = route.getDomain();
        localRoutingTable.addRoute(address, destination);
        Lock lock = CacheFactory.getLock(address, serversCache);
        try {
            lock.lock();
            serversCache.put(address, server.getNodeID().toByteArray());
        }
        finally {
            lock.unlock();
        }
    }

    public void addComponentRoute(JID route, RoutableChannelHandler destination) {
        String address = route.getDomain();
        localRoutingTable.addRoute(address, destination);
        Lock lock = CacheFactory.getLock(address, componentsCache);
        try {
            lock.lock();
            Set<NodeID> nodes = componentsCache.get(address);
            if (nodes == null) {
                nodes = new HashSet<NodeID>();
            }
            nodes.add(server.getNodeID());
            componentsCache.put(address, nodes);
        } finally {
            lock.unlock();
        }
    }

    public boolean addClientRoute(JID route, LocalClientSession destination) {
        boolean added;
        boolean available = destination.getPresence().isAvailable();
        localRoutingTable.addRoute(route.toString(), destination);
        if (destination.getAuthToken().isAnonymous()) {
            Lock lockAn = CacheFactory.getLock(route.toString(), anonymousUsersCache);
            try {
                lockAn.lock();
                added = anonymousUsersCache.put(route.toString(), new ClientRoute(server.getNodeID(), available)) ==
                        null;
            }
            finally {
                lockAn.unlock();
            }
            // Add the session to the list of user sessions
            if (route.getResource() != null && (!available || added)) {
                Lock lock = CacheFactory.getLock(route.toBareJID(), usersSessions);
                try {
                    lock.lock();
                    usersSessions.put(route.toBareJID(), Arrays.asList(route.toString()));
                }
                finally {
                    lock.unlock();
                }
            }
        }
        else {
            Lock lockU = CacheFactory.getLock(route.toString(), usersCache);
            try {
                lockU.lock();
                added = usersCache.put(route.toString(), new ClientRoute(server.getNodeID(), available)) == null;
            }
            finally {
                lockU.unlock();
            }
            // Add the session to the list of user sessions
            if (route.getResource() != null && (!available || added)) {
                Lock lock = CacheFactory.getLock(route.toBareJID(), usersSessions);
                try {
                    lock.lock();
                    Collection<String> jids = usersSessions.get(route.toBareJID());
                    if (jids == null) {
                        // Optimization - use different class depending on current setup
                        if (ClusterManager.isClusteringStarted()) {
                            jids = new HashSet<String>();
                        }
                        else {
                            jids = new ConcurrentHashSet<String>();
                        }
                    }
                    jids.add(route.toString());
                    usersSessions.put(route.toBareJID(), jids);
                }
                finally {
                    lock.unlock();
                }
            }
        }
        return added;
    }

    public void broadcastPacket(Message packet, boolean onlyLocal) {
        // Send the message to client sessions connected to this JVM
        for(ClientSession session : localRoutingTable.getClientRoutes()) {
            session.process(packet);
        }

        // Check if we need to broadcast the message to client sessions connected to remote cluter nodes
        if (!onlyLocal && remotePacketRouter != null) {
            remotePacketRouter.broadcastPacket(packet);
        }
    }

    public void routePacket(JID jid, Packet packet, boolean fromServer) throws PacketException {
        boolean routed = false;
        if (serverName.equals(jid.getDomain())) {
            if (jid.getResource() == null) {
                // Packet sent to a bare JID of a user
                if (packet instanceof Message) {
                    // Find best route of local user
                    routed = routeToBareJID(jid, (Message) packet);
                }
                else {
                    throw new PacketException("Cannot route packet of type IQ or Presence to bare JID: " + packet);
                }
            }
            else {
                // Packet sent to local user (full JID)
                ClientRoute clientRoute = usersCache.get(jid.toString());
                if (clientRoute == null) {
                    clientRoute = anonymousUsersCache.get(jid.toString());
                }
                if (clientRoute != null) {
                    if (!clientRoute.isAvailable() && routeOnlyAvailable(packet, fromServer) &&
                            !presenceUpdateHandler.hasDirectPresence(packet.getTo(), packet.getFrom())) {
                        // Packet should only be sent to available sessions and the route is not available
                        routed = false;
                    }
                    else {
                        if (server.getNodeID().equals(clientRoute.getNodeID())) {
                            // This is a route to a local user hosted in this node
                            try {
                                localRoutingTable.getRoute(jid.toString()).process(packet);
                                routed = true;
                            } catch (UnauthorizedException e) {
                                Log.error(e);
                            }
                        }
                        else {
                            // This is a route to a local user hosted in other node
                            if (remotePacketRouter != null) {
                                routed = remotePacketRouter
                                        .routePacket(clientRoute.getNodeID().toByteArray(), jid, packet);
                            }
                        }
                    }
                }
            }
        }
        else if (jid.getDomain().contains(serverName) &&
                (hasComponentRoute(jid) || ExternalComponentManager.hasConfiguration(jid.getDomain()))) {
            // Packet sent to component hosted in this server
            // First check if the component is being hosted in this JVM
            RoutableChannelHandler route = localRoutingTable.getRoute(jid.getDomain());
            if (route != null) {
                try {
                    route.process(packet);
                    routed = true;
                } catch (UnauthorizedException e) {
                    Log.error(e);
                }
            }
            else {
                // Check if other cluster nodes are hosting this component
                Set<NodeID> nodes = componentsCache.get(jid.getDomain());
                if (nodes != null) {
                    for (NodeID nodeID : nodes) {
                        if (server.getNodeID().equals(nodeID)) {
                            // This is a route to a local component hosted in this node (route
                            // could have been added after our previous check)
                            try {
                                localRoutingTable.getRoute(jid.getDomain()).process(packet);
                                routed = true;
                                break;
                            } catch (UnauthorizedException e) {
                                Log.error(e);
                            }
                        }
                        else {
                            // This is a route to a local component hosted in other node
                            if (remotePacketRouter != null) {
                                routed = remotePacketRouter.routePacket(nodeID.toByteArray(), jid, packet);
                                if (routed) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            // Packet sent to remote server
            byte[] nodeID = serversCache.get(jid.getDomain());
            if (nodeID != null) {
                if (server.getNodeID().equals(nodeID)) {
                    // This is a route to a remote server connected from this node
                    try {
                        localRoutingTable.getRoute(jid.getDomain()).process(packet);
                        routed = true;
                    } catch (UnauthorizedException e) {
                        Log.error(e);
                    }
                }
                else {
                    // This is a route to a remote server connected from other node
                    if (remotePacketRouter != null) {
                        routed = remotePacketRouter.routePacket(nodeID, jid, packet);
                    }
                }
            }
            else {
                // Return a promise of a remote session. This object will queue packets pending
                // to be sent to remote servers
                OutgoingSessionPromise.getInstance().process(packet);
                routed = true;
            }
        }

        if (!routed) {
            if (Log.isDebugEnabled()) {
                Log.debug("RoutingTableImpl: Failed to route packet to JID: " + jid + " packet: " + packet);
            }
            if (packet instanceof IQ) {
                iqRouter.routingFailed(jid, packet);
            }
            else if (packet instanceof Message) {
                messageRouter.routingFailed(jid, packet);
            }
            else if (packet instanceof Presence) {
                presenceRouter.routingFailed(jid, packet);
            }
        }
    }

    /**
     * Returns true if the specified packet must only be route to available client sessions.
     *
     * @param packet the packet to route.
     * @param fromServer true if the packet was created by the server.
     * @return true if the specified packet must only be route to available client sessions.
     */
    private boolean routeOnlyAvailable(Packet packet, boolean fromServer) {
        if (fromServer) {
            // Packets created by the server (no matter their FROM value) must always be delivered no
            // matter the available presence of the user
            return false;
        }
        boolean onlyAvailable = true;
        JID from = packet.getFrom();
        boolean hasSender = from != null;
        if (packet instanceof IQ) {
            onlyAvailable = hasSender && !(serverName.equals(from.getDomain()) && from.getResource() == null) &&
                    !componentsCache.containsKey(from.getDomain());
        }
        else if (packet instanceof Message || packet instanceof Presence) {
            onlyAvailable = !hasSender ||
                    (!serverName.equals(from.toString()) && !componentsCache.containsKey(from.getDomain()));
        }
        return onlyAvailable;
    }

    /**
     * Deliver the message sent to the bare JID of a local user to the best connected resource. If the
     * target user is not online then messages will be stored offline according to the offline strategy.
     * However, if the user is connected from only one resource then the message will be delivered to
     * that resource. In the case that the user is connected from many resources the logic will be the
     * following:
     * <ol>
     *  <li>Select resources with highest priority</li>
     *  <li>Select resources with highest show value (chat, available, away, xa, dnd)</li>
     *  <li>Select resource with most recent activity</li>
     * </ol>
     *
     * Admins can override the above logic and just send the message to all connected resources
     * with highest priority by setting the system property <tt>route.all-resources</tt> to
     * <tt>true</tt>.
     *
     * @param recipientJID the bare JID of the target local user.
     * @param packet the message to send.
     * @return true if at least one target session was found
     */
    private boolean routeToBareJID(JID recipientJID, Message packet) {
        List<ClientSession> sessions = new ArrayList<ClientSession>();
        // Get existing AVAILABLE sessions of this user or AVAILABLE to the sender of the packet
        for (JID address : getRoutes(recipientJID, packet.getFrom())) {
            ClientSession session = getClientRoute(address);
            if (session != null) {
                sessions.add(session);
            }
        }
        sessions = getHighestPrioritySessions(sessions);
        if (sessions.isEmpty()) {
            // No session is available so store offline
            return false;
        }
        else if (sessions.size() == 1) {
            // Found only one session so deliver message
            sessions.get(0).process(packet);
        }
        else {
            // Many sessions have the highest priority (be smart now) :)
            if (!JiveGlobals.getBooleanProperty("route.all-resources", false)) {
                // Sort sessions by show value (e.g. away, xa)
                Collections.sort(sessions, new Comparator<ClientSession>() {

                    public int compare(ClientSession o1, ClientSession o2) {
                        int thisVal = getShowValue(o1);
                        int anotherVal = getShowValue(o2);
                        return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
                    }

                    /**
                     * Priorities are: chat, available, away, xa, dnd.
                     */
                    private int getShowValue(ClientSession session) {
                        Presence.Show show = session.getPresence().getShow();
                        if (show == Presence.Show.chat) {
                            return 1;
                        }
                        else if (show == null) {
                            return 2;
                        }
                        else if (show == Presence.Show.away) {
                            return 3;
                        }
                        else if (show == Presence.Show.xa) {
                            return 4;
                        }
                        else {
                            return 5;
                        }
                    }
                });

                // Get same sessions with same max show value
                List<ClientSession> targets = new ArrayList<ClientSession>();
                Presence.Show showFilter = sessions.get(0).getPresence().getShow();
                for (ClientSession session : sessions) {
                    if (session.getPresence().getShow() == showFilter) {
                        targets.add(session);
                    }
                    else {
                        break;
                    }
                }

                // Get session with most recent activity (and highest show value)
                Collections.sort(targets, new Comparator<ClientSession>() {
                    public int compare(ClientSession o1, ClientSession o2) {
                        return o2.getLastActiveDate().compareTo(o1.getLastActiveDate());
                    }
                });
                // Deliver stanza to session with highest priority, highest show value and most recent activity
                targets.get(0).process(packet);
            }
            else {
                // Deliver stanza to all connected resources with highest priority
                for (ClientSession session : sessions) {
                    session.process(packet);
                }
            }
        }
        return true;
    }

    /**
     * Returns the sessions that had the highest presence priority greater than zero.
     *
     * @param sessions the list of user sessions that filter and get the ones with highest priority.
     * @return the sessions that had the highest presence priority greater than zero or empty collection
     *         if all were negative.
     */
    private List<ClientSession> getHighestPrioritySessions(List<ClientSession> sessions) {
        int highest = Integer.MIN_VALUE;
        // Get the highest priority amongst the sessions
        for (ClientSession session : sessions) {
            int priority = session.getPresence().getPriority();
            if (priority >= 0 && priority > highest) {
                highest = priority;
            }
        }
        // Answer an empty collection if all have negative priority
        if (highest == Integer.MIN_VALUE) {
            return Collections.emptyList();
        }
        // Get sessions that have the highest priority
        List<ClientSession> answer = new ArrayList<ClientSession>(sessions.size());
        for (ClientSession session : sessions) {
            if (session.getPresence().getPriority() == highest) {
                answer.add(session);
            }
        }
        return answer;
    }

    public ClientSession getClientRoute(JID jid) {
        // Check if this session is hosted by this cluster node
        ClientSession session = (ClientSession) localRoutingTable.getRoute(jid.toString());
        if (session == null) {
            // The session is not in this JVM so assume remote
            RemoteSessionLocator locator = server.getRemoteSessionLocator();
            if (locator != null) {
                // Check if the session is hosted by other cluster node
                ClientRoute route = usersCache.get(jid.toString());
                if (route == null) {
                    route = anonymousUsersCache.get(jid.toString());
                }
                if (route != null) {
                    session = locator.getClientSession(route.getNodeID().toByteArray(), jid);
                }
            }
        }
        return session;
    }

    public Collection<ClientSession> getClientsRoutes(boolean onlyLocal) {
        // Add sessions hosted by this cluster node
        Collection<ClientSession> sessions = new ArrayList<ClientSession>(localRoutingTable.getClientRoutes());
        if (!onlyLocal) {
            // Add sessions not hosted by this JVM
            RemoteSessionLocator locator = server.getRemoteSessionLocator();
            if (locator != null) {
                // Add sessions of non-anonymous users hosted by other cluster nodes
                for (Map.Entry<String, ClientRoute> entry : usersCache.entrySet()) {
                    ClientRoute route = entry.getValue();
                    if (!server.getNodeID().equals(route.getNodeID())) {
                        sessions.add(locator.getClientSession(route.getNodeID().toByteArray(), new JID(entry.getKey())));
                    }
                }
                // Add sessions of anonymous users hosted by other cluster nodes
                for (Map.Entry<String, ClientRoute> entry : anonymousUsersCache.entrySet()) {
                    ClientRoute route = entry.getValue();
                    if (!server.getNodeID().equals(route.getNodeID())) {
                        sessions.add(locator.getClientSession(route.getNodeID().toByteArray(), new JID(entry.getKey())));
                    }
                }
            }
        }
        return sessions;
    }

    public OutgoingServerSession getServerRoute(JID jid) {
        // Check if this session is hosted by this cluster node
        OutgoingServerSession session = (OutgoingServerSession) localRoutingTable.getRoute(jid.getDomain());
        if (session == null) {
            // The session is not in this JVM so assume remote
            RemoteSessionLocator locator = server.getRemoteSessionLocator();
            if (locator != null) {
                // Check if the session is hosted by other cluster node
                byte[] nodeID = serversCache.get(jid.getDomain());
                if (nodeID != null) {
                    session = locator.getOutgoingServerSession(nodeID, jid);
                }
            }
        }
        return session;
    }

    public Collection<String> getServerHostnames() {
        return serversCache.keySet();
    }

    public int getServerSessionsCount() {
        return localRoutingTable.getServerRoutes().size();
    }

    public Collection<String> getComponentsDomains() {
        return componentsCache.keySet();
    }

    public boolean hasClientRoute(JID jid) {
        return usersCache.containsKey(jid.toString()) || isAnonymousRoute(jid);
    }

    public boolean isAnonymousRoute(JID jid) {
        return anonymousUsersCache.containsKey(jid.toString());
    }

    public boolean isLocalRoute(JID jid) {
        return localRoutingTable.isLocalRoute(jid);
    }

    public boolean hasServerRoute(JID jid) {
        return serversCache.containsKey(jid.getDomain());
    }

    public boolean hasComponentRoute(JID jid) {
        return componentsCache.containsKey(jid.getDomain());
    }

    public List<JID> getRoutes(JID route, JID requester) {
        List<JID> jids = new ArrayList<JID>();
        if (serverName.equals(route.getDomain())) {
            // Address belongs to local user
            if (route.getResource() != null) {
                // Address is a full JID of a user
                ClientRoute clientRoute = usersCache.get(route.toString());
                if (clientRoute == null) {
                    clientRoute = anonymousUsersCache.get(route.toString());
                }
                if (clientRoute != null &&
                        (clientRoute.isAvailable() || presenceUpdateHandler.hasDirectPresence(route, requester))) {
                    jids.add(route);
                }
            }
            else {
                // Address is a bare JID so return all AVAILABLE resources of user
                Collection<String> sessions = usersSessions.get(route.toBareJID());
                if (sessions != null) {
                    // Select only available sessions
                    for (String jid : sessions) {
                        ClientRoute clientRoute = usersCache.get(jid);
                        if (clientRoute == null) {
                            clientRoute = anonymousUsersCache.get(jid);
                        }
                        if (clientRoute != null && (clientRoute.isAvailable() ||
                                presenceUpdateHandler.hasDirectPresence(new JID(jid), requester))) {
                            jids.add(new JID(jid));
                        }
                    }
                }
            }
        }
        else if (route.getDomain().contains(serverName)) {
            // Packet sent to component hosted in this server
            if (componentsCache.containsKey(route.getDomain())) {
                jids.add(new JID(route.getDomain()));
            }
        }
        else {
            // Packet sent to remote server
            jids.add(route);
        }
        return jids;
    }

    public boolean removeClientRoute(JID route) {
        boolean anonymous = false;
        String address = route.toString();
        ClientRoute clientRoute = null;
        Lock lockU = CacheFactory.getLock(address, usersCache);
        try {
            lockU.lock();
            clientRoute = usersCache.remove(address);
        }
        finally {
            lockU.unlock();
        }
        if (clientRoute == null) {
            Lock lockA = CacheFactory.getLock(address, anonymousUsersCache);
            try {
                lockA.lock();
                clientRoute = anonymousUsersCache.remove(address);
                anonymous = true;
            }
            finally {
                lockA.unlock();
            }
        }
        if (clientRoute != null && route.getResource() != null) {
            Lock lock = CacheFactory.getLock(route.toBareJID(), usersSessions);
            try {
                lock.lock();
                if (anonymous) {
                    usersSessions.remove(route.toBareJID());
                }
                else {
                    Collection<String> jids = usersSessions.get(route.toBareJID());
                    if (jids != null) {
                        jids.remove(route.toString());
                        if (!jids.isEmpty()) {
                            usersSessions.put(route.toBareJID(), jids);
                        }
                        else {
                            usersSessions.remove(route.toBareJID());
                        }
                    }
                }
            }
            finally {
                lock.unlock();
            }
        }
        localRoutingTable.removeRoute(address);
        return clientRoute != null;
    }

    public boolean removeServerRoute(JID route) {
        String address = route.getDomain();
        boolean removed = false;
        Lock lock = CacheFactory.getLock(address, serversCache);
        try {
            lock.lock();
            removed = serversCache.remove(address) != null;
        }
        finally {
            lock.unlock();
        }
        localRoutingTable.removeRoute(address);
        return removed;
    }

    public boolean removeComponentRoute(JID route) {
        String address = route.getDomain();
        boolean removed = false;
        Lock lock = CacheFactory.getLock(address, componentsCache);
        try {
            lock.lock();
            Set<NodeID> nodes = componentsCache.get(address);
            if (nodes != null) {
                removed = nodes.remove(server.getNodeID());
                if (nodes.isEmpty()) {
                    componentsCache.remove(address);
                }
                else {
                    componentsCache.put(address, nodes);
                }
            }
        } finally {
            lock.unlock();
        }
        localRoutingTable.removeRoute(address);
        return removed;
    }

    public void setRemotePacketRouter(RemotePacketRouter remotePacketRouter) {
        this.remotePacketRouter = remotePacketRouter;
    }

    public RemotePacketRouter getRemotePacketRouter() {
        return remotePacketRouter;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        serverName = server.getServerInfo().getXMPPDomain();
        iqRouter = server.getIQRouter();
        messageRouter = server.getMessageRouter();
        presenceRouter = server.getPresenceRouter();
        presenceUpdateHandler = server.getPresenceUpdateHandler();
        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    public void start() throws IllegalStateException {
        super.start();
        localRoutingTable.start();
    }

    public void stop() {
        super.stop();
        localRoutingTable.stop();
    }

    public void joinedCluster() {
        restoreCacheContent();

        // Broadcast presence of local sessions to remote sessions when subscribed to presence
        // Probe presences of remote sessions when subscribed to presence of local session
        // Send pending subscription requests to local sessions from remote sessions
        // Deliver offline messages sent to local sessions that were unavailable in other nodes
        // Send available presences of local sessions to other resources of the same user
        PresenceUpdateHandler presenceUpdateHandler = XMPPServer.getInstance().getPresenceUpdateHandler();
        for (LocalClientSession session : localRoutingTable.getClientRoutes()) {
            // Simulate that the local session has just became available
            session.setInitialized(false);
            // Simulate that current session presence has just been received
            presenceUpdateHandler.process(session.getPresence());
        }
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
        // Add outgoing server sessions hosted locally to the cache (using new nodeID)
        for (LocalOutgoingServerSession session : localRoutingTable.getServerRoutes()) {
            addServerRoute(session.getAddress(), session);
        }

        // Add component sessions hosted locally to the cache (using new nodeID) and remove traces to old nodeID
        for (RoutableChannelHandler route : localRoutingTable.getComponentRoute()) {
            addComponentRoute(route.getAddress(), route);
        }

        // Add client sessions hosted locally to the cache (using new nodeID)
        for (LocalClientSession session : localRoutingTable.getClientRoutes()) {
            addClientRoute(session.getAddress(), session);
        }
    }

}
