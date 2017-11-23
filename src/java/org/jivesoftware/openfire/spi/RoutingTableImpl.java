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

package org.jivesoftware.openfire.spi;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.carbons.Received;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.forward.Forwarded;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final Logger Log = LoggerFactory.getLogger(RoutingTableImpl.class);
    
    public static final String C2S_CACHE_NAME = "Routing Users Cache";
    public static final String ANONYMOUS_C2S_CACHE_NAME = "Routing AnonymousUsers Cache";
    public static final String S2S_CACHE_NAME = "Routing Servers Cache";
    public static final String COMPONENT_CACHE_NAME = "Routing Components Cache";
    public static final String C2S_SESSION_NAME = "Routing User Sessions";

    /**
     * Cache (unlimited, never expire) that holds outgoing sessions to remote servers from this server.
     * Key: server domain pair, Value: nodeID
     */
    private Cache<DomainPair, byte[]> serversCache;
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
        usersSessions = CacheFactory.createCache(C2S_SESSION_NAME);
        localRoutingTable = new LocalRoutingTable();
    }

    @Override
    public void addServerRoute(DomainPair address, LocalOutgoingServerSession destination) {
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

    @Override
    public void addComponentRoute(JID route, RoutableChannelHandler destination) {
        DomainPair pair = new DomainPair("", route.getDomain());
        String address = route.getDomain();
        localRoutingTable.addRoute(pair, destination);
        Lock lock = CacheFactory.getLock(address, componentsCache);
        try {
            lock.lock();
            Set<NodeID> nodes = componentsCache.get(address);
            if (nodes == null) {
                nodes = new HashSet<>();
            }
            nodes.add(server.getNodeID());
            componentsCache.put(address, nodes);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean addClientRoute(JID route, LocalClientSession destination) {
        boolean added;
        boolean available = destination.getPresence().isAvailable();
        Log.debug("Adding client route {}", route);
        localRoutingTable.addRoute(new DomainPair("", route.toString()), destination);
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
                            jids = new HashSet<>();
                        }
                        else {
                            jids = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
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

    @Override
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

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.openfire.RoutingTable#routePacket(org.xmpp.packet.JID, org.xmpp.packet.Packet, boolean)
     * 
     * @param jid the recipient of the packet to route.
     * @param packet the packet to route.
     * @param fromServer true if the packet was created by the server. This packets should
     *        always be delivered
     * @throws PacketException thrown if the packet is malformed (results in the sender's
     *      session being shutdown).
     */
    @Override
    public void routePacket(JID jid, Packet packet, boolean fromServer) throws PacketException {
        boolean routed = false;
        try {
            if (serverName.equals(jid.getDomain())) {
                // Packet sent to our domain.
                routed = routeToLocalDomain(jid, packet, fromServer);
            }
            else if (jid.getDomain().endsWith(serverName) && hasComponentRoute(jid)) {
                // Packet sent to component hosted in this server
                routed = routeToComponent(jid, packet, routed);
            }
            else {
                // Packet sent to remote server
                routed = routeToRemoteDomain(jid, packet, routed);
            }
        } catch (Exception ex) {
            // Catch here to ensure that all packets get handled, despite various processing
            // exceptions, rather than letting any fall through the cracks. For example,
            // an IAE could be thrown when running in a cluster if a remote member becomes 
            // unavailable before the routing caches are updated to remove the defunct node.
            // We have also occasionally seen various flavors of NPE and other oddities, 
            // typically due to unexpected environment or logic breakdowns. 
            Log.error("Primary packet routing failed", ex); 
        }

        if (!routed) {
            if (Log.isDebugEnabled()) {
                Log.debug("Failed to route packet to JID: {} packet: {}", jid, packet.toXML());
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
     * Routes packets that are sent to the XMPP domain itself (excluding subdomains).
     * 
     * @param jid
     *            the recipient of the packet to route.
     * @param packet
     *            the packet to route.
     * @param fromServer
     *            true if the packet was created by the server. This packets
     *            should always be delivered
     * @throws PacketException
     *             thrown if the packet is malformed (results in the sender's
     *             session being shutdown).
     * @return <tt>true</tt> if the packet was routed successfully,
     *         <tt>false</tt> otherwise.
     */
    private boolean routeToLocalDomain(JID jid, Packet packet,
            boolean fromServer) {
        boolean routed = false;
        Element privateElement = packet.getElement().element(QName.get("private", "urn:xmpp:carbons:2"));
        boolean isPrivate = privateElement != null;
        // The receiving server and SHOULD remove the <private/> element before delivering to the recipient.
        packet.getElement().remove(privateElement);

        if (jid.getResource() == null) {
            // Packet sent to a bare JID of a user
            if (packet instanceof Message) {
                // Find best route of local user
                routed = routeToBareJID(jid, (Message) packet, isPrivate);
            }
            else {
                throw new PacketException("Cannot route packet of type IQ or Presence to bare JID: " + packet.toXML());
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
                    Log.debug("Unable to route packet. Packet should only be sent to available sessions and the route is not available. {} ", packet.toXML());
                    routed = false;
                } else {
                    if (localRoutingTable.isLocalRoute(jid)) {
                        if (packet instanceof Message) {
                            Message message = (Message) packet;
                            if (message.getType() == Message.Type.chat && !isPrivate) {
                                List<JID> routes = getRoutes(jid.asBareJID(), null);
                                for (JID route : routes) {
                                    // The receiving server MUST NOT send a forwarded copy to the full JID the original <message/> stanza was addressed to, as that recipient receives the original <message/> stanza.
                                    if (!route.equals(jid)) {
                                        ClientSession clientSession = getClientRoute(route);
                                        if (clientSession.isMessageCarbonsEnabled()) {
                                            Message carbon = new Message();
                                            // The wrapping message SHOULD maintain the same 'type' attribute value;
                                            carbon.setType(message.getType());
                                            // the 'from' attribute MUST be the Carbons-enabled user's bare JID
                                            carbon.setFrom(route.asBareJID());
                                            // and the 'to' attribute MUST be the full JID of the resource receiving the copy
                                            carbon.setTo(route);
                                            // The content of the wrapping message MUST contain a <received/> element qualified by the namespace "urn:xmpp:carbons:2", which itself contains a <forwarded/> element qualified by the namespace "urn:xmpp:forward:0" that contains the original <message/>.
                                            carbon.addExtension(new Received(new Forwarded(message)));

                                            try {
                                                localRoutingTable.getRoute(route).process(carbon);
                                            } catch (UnauthorizedException e) {
                                                Log.error("Unable to route packet " + packet.toXML(), e);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // This is a route to a local user hosted in this node
                        try {
                            localRoutingTable.getRoute(jid).process(packet);
                            routed = true;
                        } catch (UnauthorizedException e) {
                            Log.error("Unable to route packet " + packet.toXML(), e);
                        }
                    }
                    else {
                        // This is a route to a local user hosted in other node
                        if (remotePacketRouter != null) {
                            routed = remotePacketRouter
                                    .routePacket(clientRoute.getNodeID().toByteArray(), jid, packet);
                            if (!routed) {
                                removeClientRoute(jid); // drop invalid client route
                            }
                        }
                    }
                }
            }
        }
        return routed;
    }

    /**
     * Routes packets that are sent to components of the XMPP domain (which are
     * subdomains of the XMPP domain)
     * 
     * @param jid
     *            the recipient of the packet to route.
     * @param packet
     *            the packet to route.
     * @throws PacketException
     *             thrown if the packet is malformed (results in the sender's
     *             session being shutdown).
     * @return <tt>true</tt> if the packet was routed successfully,
     *         <tt>false</tt> otherwise.
     */
    private boolean routeToComponent(JID jid, Packet packet,
            boolean routed) {
        if (!hasComponentRoute(jid) 
                && !ExternalComponentManager.hasConfiguration(jid.getDomain())) {
            return false;
        }
        
        // First check if the component is being hosted in this JVM
        RoutableChannelHandler route = localRoutingTable.getRoute(new JID(null, jid.getDomain(), null, true));
        if (route != null) {
            try {
                route.process(packet);
                routed = true;
            } catch (UnauthorizedException e) {
                Log.error("Unable to route packet " + packet.toXML(), e);
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
                            RoutableChannelHandler localRoute = localRoutingTable.getRoute(new JID(null, jid.getDomain(), null, true));
                            if (localRoute != null) {
                                localRoute.process(packet);
                                routed = true;
                                break;
                            }
                        } catch (UnauthorizedException e) {
                            Log.error("Unable to route packet " + packet.toXML(), e);
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
        return routed;
    }

    /**
     * Routes packets that are sent to other XMPP domains than the local XMPP
     * domain.
     * 
     * @param jid
     *            the recipient of the packet to route.
     * @param packet
     *            the packet to route.
     * @throws PacketException
     *             thrown if the packet is malformed (results in the sender's
     *             session being shutdown).
     * @return <tt>true</tt> if the packet was routed successfully,
     *         <tt>false</tt> otherwise.
     */
    private boolean routeToRemoteDomain(JID jid, Packet packet, boolean routed)
    {
        if ( !JiveGlobals.getBooleanProperty( ConnectionSettings.Server.ALLOW_ANONYMOUS_OUTBOUND_DATA, false ) )
        {
            // Disallow anonymous local users to send data to other domains than the local domain.
            if ( isAnonymousRoute( packet.getFrom() ) )
            {
                Log.info( "The anonymous user '{}' attempted to send data to '{}', which is on a remote domain. Openfire is configured to not allow anonymous users to send data to remote domains.", packet.getFrom(), jid );
                routed = false;
                return routed;
            }
        }

        DomainPair pair = new DomainPair(packet.getFrom().getDomain(), jid.getDomain());
        byte[] nodeID = serversCache.get(pair);
        if (nodeID != null) {
            if (server.getNodeID().equals(nodeID)) {
                // This is a route to a remote server connected from this node
                try {
                    localRoutingTable.getRoute(pair).process(packet);
                    routed = true;
                } catch (UnauthorizedException e) {
                    Log.error("Unable to route packet " + packet.toXML(), e);
                }
            }
            else {
                // This is a route to a remote server connected from other node
                if (remotePacketRouter != null) {
                    routed = remotePacketRouter.routePacket(nodeID, jid, packet);
                }
            }
        }
        else if (!RemoteServerManager.canAccess(jid.getDomain())) { // Check if the remote domain is in the blacklist
            Log.info( "Will not route: Remote domain {} is not accessible according to our configuration (typical causes: server federation is disabled, or domain is blacklisted).", jid.getDomain() );
            routed = false;
        }
        else {
            // Return a promise of a remote session. This object will queue packets pending
            // to be sent to remote servers
            OutgoingSessionPromise.getInstance().process(packet);
            routed = true;
        }
        return routed;
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
    private boolean routeToBareJID(JID recipientJID, Message packet, boolean isPrivate) {
        List<ClientSession> sessions = new ArrayList<>();
        // Get existing AVAILABLE sessions of this user or AVAILABLE to the sender of the packet
        for (JID address : getRoutes(recipientJID, packet.getFrom())) {
            ClientSession session = getClientRoute(address);
            if (session != null && session.isInitialized()) {
                sessions.add(session);
            }
        }

        // Get the sessions with non-negative priority for message carbons processing.
        List<ClientSession> nonNegativePrioritySessions = getNonNegativeSessions(sessions, 0);

        if (packet.getType() == Message.Type.error) {
            // Errors should be dropped at this point.
            Log.debug("Error stanza to bare JID discarded: {}", packet.toXML());
            return true; // Not offline.
        }

        if (packet.getType() == Message.Type.groupchat) {
            // Surreal message type; cannot occur.
            Log.debug("Groupchat stanza to bare JID discarded: {}", packet.toXML());
            return false; // Maybe offline has an idea?
        }

        if (nonNegativePrioritySessions.isEmpty()) {
            // No session is available so store offline
            Log.debug("Unable to route packet. No session is available so store offline. {} ", packet.toXML());
            return false;
        }

        // Check for message carbons enabled sessions and send the message to them.
        for (ClientSession session : nonNegativePrioritySessions) {
            if (packet.getType() == Message.Type.headline) {
                // Headline messages are broadcast.
                session.process(packet);
            // Deliver to each session, if is message carbons enabled.
            } else if (shouldCarbonCopyToResource(session, packet, isPrivate)) {
                session.process(packet);
            // Deliver to each session if property route.really-all-resources is true
            // (in case client does not support carbons)
            } else if (JiveGlobals.getBooleanProperty("route.really-all-resources", false)) {
                session.process(packet);
            }
        }

        if (packet.getType() == Message.Type.headline) {
            return true;
        }

        if (JiveGlobals.getBooleanProperty("route.really-all-resources", false)) {
            return true;
        }

        // Get the highest priority sessions for normal processing.
        List<ClientSession> highestPrioritySessions = getHighestPrioritySessions(nonNegativePrioritySessions);

        if (highestPrioritySessions.size() == 1) {
            // Found only one session so deliver message (if it hasn't already been processed because it has message carbons enabled)
            if (!shouldCarbonCopyToResource(highestPrioritySessions.get(0), packet, isPrivate)) {
                highestPrioritySessions.get(0).process(packet);
            }
        }
        else {
            // Many sessions have the highest priority (be smart now) :)
            if (!JiveGlobals.getBooleanProperty("route.all-resources", false)) {
                // Sort sessions by show value (e.g. away, xa)
                Collections.sort(highestPrioritySessions, new Comparator<ClientSession>() {

                    @Override
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
                List<ClientSession> targets = new ArrayList<>();
                Presence.Show showFilter = highestPrioritySessions.get(0).getPresence().getShow();
                for (ClientSession session : highestPrioritySessions) {
                    if (session.getPresence().getShow() == showFilter) {
                        targets.add(session);
                    }
                    else {
                        break;
                    }
                }

                // Get session with most recent activity (and highest show value)
                Collections.sort(targets, new Comparator<ClientSession>() {
                    @Override
                    public int compare(ClientSession o1, ClientSession o2) {
                        return o2.getLastActiveDate().compareTo(o1.getLastActiveDate());
                    }
                });

                // Make sure, we don't send the packet again, if it has already been sent by message carbons.
                ClientSession session = targets.get(0);
                if (!shouldCarbonCopyToResource(session, packet, isPrivate)) {
                    // Deliver stanza to session with highest priority, highest show value and most recent activity
                    session.process(packet);
                }
            }
            else {
                for (ClientSession session : highestPrioritySessions) {
                    // Make sure, we don't send the packet again, if it has already been sent by message carbons.
                    if (!shouldCarbonCopyToResource(session, packet, isPrivate)) {
                        session.process(packet);
                    }
                }
            }
        }
        return true;
    }

    private boolean shouldCarbonCopyToResource(ClientSession session, Message message, boolean isPrivate) {
        return !isPrivate && session.isMessageCarbonsEnabled() && message.getType() == Message.Type.chat;
    }

    /**
     * Returns the sessions that had the highest presence priority that is non-negative.
     *
     * @param sessions the list of user sessions that filter and get the ones with highest priority.
     * @return the sessions that had the highest presence non-negative priority or empty collection
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
        // Get sessions that have the highest priority
        return getNonNegativeSessions(sessions, highest);
    }

    /**
     * Gets the non-negative session from a minimal priority.
     *
     * @param sessions The sessions.
     * @param min      The minimal priority.
     * @return The filtered sessions.
     */
    private List<ClientSession> getNonNegativeSessions(List<ClientSession> sessions, int min) {
        if (min < 0) {
            return Collections.emptyList();
        }
        // Get sessions with priority >= min
        List<ClientSession> answer = new ArrayList<>(sessions.size());
        for (ClientSession session : sessions) {
            if (session.getPresence().getPriority() >= min) {
                answer.add(session);
            }
        }
        return answer;
    }

    @Override
    public ClientSession getClientRoute(JID jid) {
        // Check if this session is hosted by this cluster node
        ClientSession session = (ClientSession) localRoutingTable.getRoute(jid);
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

    @Override
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

    @Override
    public OutgoingServerSession getServerRoute(DomainPair jids) {
        // Check if this session is hosted by this cluster node
        OutgoingServerSession session = (OutgoingServerSession) localRoutingTable.getRoute(jids);
        if (session == null) {
            // The session is not in this JVM so assume remote
            RemoteSessionLocator locator = server.getRemoteSessionLocator();
            if (locator != null) {
                // Check if the session is hosted by other cluster node
                byte[] nodeID = serversCache.get(jids);
                if (nodeID != null) {
                    session = locator.getOutgoingServerSession(nodeID, jids);
                }
            }
        }
        return session;
    }

    @Override
    public Collection<String> getServerHostnames() {
        Set<String> domains = new HashSet<>();
        for (DomainPair pair : serversCache.keySet()) {
            domains.add(pair.getRemote());
        }
        return domains;
    }

    @Override
    public Collection<DomainPair> getServerRoutes() {
        return serversCache.keySet();
    }

    @Override
    public int getServerSessionsCount() {
        return localRoutingTable.getServerRoutes().size();
    }

    @Override
    public Collection<String> getComponentsDomains() {
        return componentsCache.keySet();
    }

    @Override
    public boolean hasClientRoute(JID jid) {
        return usersCache.containsKey(jid.toString()) || isAnonymousRoute(jid);
    }

    @Override
    public boolean isAnonymousRoute(JID jid) {
        return anonymousUsersCache.containsKey(jid.toString());
    }

    @Override
    public boolean isLocalRoute(JID jid) {
        return localRoutingTable.isLocalRoute(jid);
    }

    @Override
    public boolean hasServerRoute(DomainPair pair) {
        return serversCache.containsKey(pair);
    }

    @Override
    public boolean hasComponentRoute(JID jid) {
        return componentsCache.containsKey(jid.getDomain());
    }

    @Override
    public List<JID> getRoutes(JID route, JID requester) {
        List<JID> jids = new ArrayList<>();
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
                Lock lock = CacheFactory.getLock(route.toBareJID(), usersSessions);
                try {
                    lock.lock(); // temporarily block new sessions for this JID
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
                finally {
                    lock.unlock();
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

    @Override
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
        Log.debug("Removing client route {}", route);
        localRoutingTable.removeRoute(new DomainPair("", route.toString()));
        return clientRoute != null;
    }

    @Override
    public boolean removeServerRoute(DomainPair route) {
        String address = route.toString();
        boolean removed = false;
        Lock lock = CacheFactory.getLock(route, serversCache);
        try {
            lock.lock();
            removed = serversCache.remove(route) != null;
        }
        finally {
            lock.unlock();
        }
        localRoutingTable.removeRoute(route);
        return removed;
    }

    @Override
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
        localRoutingTable.removeRoute(new DomainPair("", address));
        return removed;
    }

    @Override
    public void setRemotePacketRouter(RemotePacketRouter remotePacketRouter) {
        this.remotePacketRouter = remotePacketRouter;
    }

    @Override
    public RemotePacketRouter getRemotePacketRouter() {
        return remotePacketRouter;
    }

    @Override
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

    @Override
    public void start() throws IllegalStateException {
        super.start();
        localRoutingTable.start();
    }

    @Override
    public void stop() {
        super.stop();
        localRoutingTable.stop();
    }

    @Override
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

    @Override
    public void joinedCluster(byte[] nodeID) {
        // Do nothing
    }

    @Override
    public void leftCluster() {
        if (!XMPPServer.getInstance().isShuttingDown()) {
            // Add local sessions to caches
            restoreCacheContent();
        }
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        
        // When a peer server leaves the cluster, any remote routes that were
        // associated with the defunct node must be dropped from the routing 
        // caches that are shared by the remaining cluster member(s).
        
        // drop routes for all client sessions connected via the defunct cluster node
        Lock clientLock = CacheFactory.getLock(nodeID, usersCache);
        try {
            clientLock.lock();
            List<String> remoteClientRoutes = new ArrayList<>();
            for (Map.Entry<String, ClientRoute> entry : usersCache.entrySet()) {
                if (entry.getValue().getNodeID().equals(nodeID)) {
                    remoteClientRoutes.add(entry.getKey());
                }
            }
            for (Map.Entry<String, ClientRoute> entry : anonymousUsersCache.entrySet()) {
                if (entry.getValue().getNodeID().equals(nodeID)) {
                    remoteClientRoutes.add(entry.getKey());
                }
            }
            for (String route : remoteClientRoutes) {
                removeClientRoute(new JID(route));
            }
        }
        finally {
            clientLock.unlock();
        }
        
        // remove routes for server domains that were accessed through the defunct node
        Lock serverLock = CacheFactory.getLock(nodeID, serversCache);
        try {
            serverLock.lock();
            List<DomainPair> remoteServerDomains = new ArrayList<>();
            for (Map.Entry<DomainPair, byte[]> entry : serversCache.entrySet()) {
                if (Arrays.equals(entry.getValue(), nodeID)) {
                    remoteServerDomains.add(entry.getKey());
                }
            }
            for (DomainPair pair : remoteServerDomains) {
                removeServerRoute(pair);
            }
        }
        finally {
            serverLock.unlock();
        }
        
        // remove component routes for the defunct node
        Lock componentLock = CacheFactory.getLock(nodeID, componentsCache);
        try {
            componentLock.lock();
            List<String> remoteComponents = new ArrayList<>();
            NodeID nodeIDInstance = NodeID.getInstance( nodeID );
            for (Map.Entry<String, Set<NodeID>> entry : componentsCache.entrySet()) {
                if (entry.getValue().remove(nodeIDInstance) && entry.getValue().size() == 0) {
                    remoteComponents.add(entry.getKey());
                }
            }
            for (String jid : remoteComponents) {
                removeComponentRoute(new JID(jid));
            }
        }
        finally {
            componentLock.unlock();
        }
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    private void restoreCacheContent() {
        // Add outgoing server sessions hosted locally to the cache (using new nodeID)
        for (LocalOutgoingServerSession session : localRoutingTable.getServerRoutes()) {
            for (DomainPair pair : session.getOutgoingDomainPairs()) {
                addServerRoute(pair, session);
            }
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
