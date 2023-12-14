/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.collect.Multimap;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PresenceRouter;
import org.jivesoftware.openfire.RemotePacketRouter;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.carbons.Received;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.forward.Forwarded;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.RemoteSessionLocator;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheUtil;
import org.jivesoftware.util.cache.ConsistencyChecks;
import org.jivesoftware.util.cache.ReverseLookupComputingCacheEntryListener;
import org.jivesoftware.util.cache.ReverseLookupUpdatingCacheEntryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     *
     * @see LocalRoutingTable#getServerRoutes() which holds content added by the local cluster node.
     * @see #s2sDomainPairsByClusterNode which holds content added by cluster nodes other than the local node.
     */
    // TODO OF-2301: having a DomainPair point to only a singular node implies that only one cluster node can have an outgoing server session.
    //               Allowing multiple cluster nodes to establish an outgoing session to the same domain would be better for performance.
    private final Cache<DomainPair, NodeID> serversCache;

    /**
     * A map that, for all nodes in the cluster except for the local one, tracks if a server to server connection has
     * been established.
     *
     * Whenever any cluster node adds or removes an entry to the #serversCache, this map, on <em>every</em> cluster
     * node, will receive a corresponding update. This ensures that every cluster node has a complete overview of all
     * cache entries (or at least the most important details of each entry - we should avoid duplicating the entire
     * cache, as that somewhat defaults the purpose of having the cache - however for this specific cache we need all
     * data, so this basically becomes a reverse lookup table).
     *
     * This map is to be used when a cluster node unexpectedly leaves the cluster. As the cache implementation uses a
     * distributed data structure that gives no guarantee that all data is visible to all cluster nodes at any given
     * time, the cache cannot be trusted to 'locally' contain all information that was added to it by the disappeared
     * node (nor can that node be contacted to retrieve the missing data, because it has already disappeared).
     *
     * @see #serversCache which is the cache for which this field is a supporting data structure.
     */
    private final ConcurrentMap<NodeID, Set<DomainPair>> s2sDomainPairsByClusterNode = new ConcurrentHashMap<>();

    /**
     * Cache (unlimited, never expire) that holds components connected to the server.
     * Key: component domain, Value: list of nodeIDs hosting the component
     *
     * @see LocalRoutingTable#getComponentRoute() which holds content added by the local cluster node.
     * @see #componentsByClusterNode which holds content added by cluster nodes other than the local node.
     */
    private final Cache<String, HashSet<NodeID>> componentsCache;

    /**
     * A map that, for all nodes in the cluster except for the local one, tracks if a component connection has
     * been established.
     *
     * Whenever any cluster node adds or removes an entry to the #componentsCache, this map, on <em>every</em> cluster
     * node, will receive a corresponding update. This ensures that every cluster node has a complete overview of all
     * cache entries (or at least the most important details of each entry - we should avoid duplicating the entire
     * cache, as that somewhat defaults the purpose of having the cache - however for this specific cache we need all
     * data, so this basically becomes a reverse lookup table).
     *
     * This map is to be used when a cluster node unexpectedly leaves the cluster. As the cache implementation uses a
     * distributed data structure that gives no guarantee that all data is visible to all cluster nodes at any given
     * time, the cache cannot be trusted to 'locally' contain all information that was added to it by the disappeared
     * node (nor can that node be contacted to retrieve the missing data, because it has already disappeared).
     *
     * @see #componentsCache which is the cache for which this field is a supporting data structure.
     */
    private final ConcurrentMap<NodeID, Set<String>> componentsByClusterNode = new ConcurrentHashMap<>();

    /**
     * Cache (unlimited, never expire) that holds sessions of user that have authenticated with the server.
     * Key: full JID, Value: {nodeID, available/unavailable}
     *
     * @see LocalRoutingTable#getClientRoutes() which holds content added by the local cluster node.
     * @see #routeOwnersByClusterNode which holds content added by cluster nodes other than the local node.
     */
    private final Cache<String, ClientRoute> usersCache;

    /**
     * Cache (unlimited, never expire) that holds sessions of anonymous user that have authenticated with the server.
     * Key: full JID, Value: {nodeID, available/unavailable}
     *
     * @see LocalRoutingTable#getClientRoutes() which holds content added by the local cluster node.
     * @see #routeOwnersByClusterNode which holds content added by cluster nodes other than the local node.
     */
    private final Cache<String, ClientRoute> anonymousUsersCache;

    /**
     * A map that, for all nodes in the cluster except for the local one, tracks if a particular entity (identified by
     * its full JID) has a ClientRoute in either #usersCache or #anonymousUsersCache. Every String in the collections
     * that are the value of this map corresponds to a key in one of those caches.
     *
     * Whenever any cluster node adds or removes an entry to either #usersCache or #anonymousUsersCache, this map, on
     * <em>every</em> cluster node, will receive a corresponding update. This ensures that every cluster node has a
     * complete overview of all cache entries (or at least the most important details of each entry - we should avoid
     * duplicating the entire cache, as that somewhat defaults the purpose of having the cache).
     *
     * This map is to be used when a cluster node unexpectedly leaves the cluster. As the cache implementation uses a
     * distributed data structure that gives no guarantee that all data is visible to all cluster nodes at any given
     * time, the cache cannot be trusted to 'locally' contain all information that was added to it by the disappeared
     * node (nor can that node be contacted to retrieve the missing data, because it has already disappeared).
     *
     * @see #usersCache which is one of the two caches for which this field is a supporting data structure.
     * @see #anonymousUsersCache which is one of the two for which this field is a supporting data structure.
     */
    private final ConcurrentMap<NodeID, Set<String>> routeOwnersByClusterNode = new ConcurrentHashMap<>();

    /**
     * Cache (unlimited, never expire) that holds set of connected resources of authenticated users
     * (includes anonymous).
     * Key: bare JID, Value: set of full JIDs of the user
     *
     * Note: unlike the other caches in this implementation, this cache does not explicitly have supporting data
     * structures. Instead, it implicitly uses the supporting data structures of {@link #usersCache} and {@link #anonymousUsersCache}.
     */
    private final Cache<String, HashSet<String>> usersSessionsCache;

    private String serverName;
    private XMPPServer server;
    private final LocalRoutingTable localRoutingTable;
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
        usersSessionsCache = CacheFactory.createCache(C2S_SESSION_NAME);
        localRoutingTable = new LocalRoutingTable();
    }

    @Override
    public void addServerRoute(DomainPair address, LocalOutgoingServerSession destination) {
        Lock lock = serversCache.getLock(address);
        lock.lock();
        try {
            final NodeID oldValue = serversCache.putIfAbsent(address, server.getNodeID());
            if (oldValue != null && !oldValue.equals(XMPPServer.getInstance().getNodeID())) {
                // Existing implementation assumes that only one node has an outgoing server connection for a domain. Fail if that's not the case. See: OF-2280
                throw new IllegalStateException("The local cluster node attempts to established a new S2S connection to '"+address+"', but such a connection already exists on cluster node '"+oldValue+"'.");
            }
        }
        finally {
            lock.unlock();
        }
        localRoutingTable.addRoute(address, destination);
    }

    @Override
    public void addComponentRoute(JID route, RoutableChannelHandler destination) {
        DomainPair pair = new DomainPair("", route.getDomain());
        String address = route.getDomain();
        localRoutingTable.addRoute(pair, destination);
        Lock lock = componentsCache.getLock(address);
        lock.lock();
        try {
            HashSet<NodeID> nodes = componentsCache.get(address);
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
        final ClientRoute newClientRoute = new ClientRoute(server.getNodeID(), available);
        if (destination.getAuthToken().isAnonymous()) {
            Lock lockAn = anonymousUsersCache.getLock(route.toString());
            lockAn.lock();
            try {
                added = anonymousUsersCache.put(route.toString(), newClientRoute) ==
                        null;
            }
            finally {
                lockAn.unlock();
            }
            // Add the session to the list of user sessions
            if (route.getResource() != null && (!available || added)) {
                Lock lock = usersSessionsCache.getLock(route.toBareJID());
                lock.lock();
                try {
                    usersSessionsCache.put(route.toBareJID(), new HashSet<>(Collections.singletonList(route.toString())));
                }
                finally {
                    lock.unlock();
                }
            }
        }
        else {
            Lock lockU = usersCache.getLock(route.toString());
            lockU.lock();
            try {
                Log.debug("Adding client route {} to users cache under key {}", newClientRoute, route);
                added = usersCache.put(route.toString(), newClientRoute) == null;
            }
            finally {
                lockU.unlock();
            }
            // Add the session to the list of user sessions
            if (route.getResource() != null && (!available || added)) {
                Lock lock = usersSessionsCache.getLock(route.toBareJID());
                lock.lock();
                try {
                    HashSet<String> jids = usersSessionsCache.get(route.toBareJID());
                    if (jids == null) {
                        jids = new HashSet<>();
                    }
                    jids.add(route.toString());
                    usersSessionsCache.put(route.toBareJID(), jids);
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
     * @throws PacketException thrown if the packet is malformed (results in the sender's
     *      session being shutdown).
     */
    @Override
    public void routePacket(JID jid, Packet packet) throws PacketException {
        boolean routed = false;
        try {
            if (serverName.equals(jid.getDomain())) {
                // Packet sent to our domain.
                routed = routeToLocalDomain(jid, packet);
            }
            else if (jid.getDomain().endsWith(serverName) && hasComponentRoute(jid)) {
                // Packet sent to component hosted in this server
                routed = routeToComponent(jid, packet);
            }
            else {
                // Packet sent to remote server
                routed = routeToRemoteDomain(jid, packet);
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
     * @throws PacketException
     *             thrown if the packet is malformed (results in the sender's
     *             session being shutdown).
     * @return {@code true} if the packet was routed successfully,
     *         {@code false} otherwise.
     */
    private boolean routeToLocalDomain(JID jid, Packet packet)
    {
        boolean routed = false;
        Element privateElement = packet.getElement().element(QName.get("private", Received.NAMESPACE));
        // The receiving server and SHOULD remove the <private/> element before delivering to the recipient.
        packet.getElement().remove(privateElement);

        if (jid.getResource() == null) {
            // RFC 6121: 8.5.2. localpart@domainpart (Packet sent to a bare JID of a user)
            if (packet instanceof Message) {
                // Find best route of local user
                routed = routeToBareJID(jid, (Message) packet);
            }
            else {
                throw new PacketException("Cannot route packet of type IQ or Presence to bare JID: " + packet.toXML());
            }
        }
        else {
            // RFC 6121 section 8.5.3. localpart@domainpart/resourcepart (Packet sent to a full JID of a user)
            ClientRoute clientRoute = getClientRouteForLocalUser(jid);
            if (clientRoute != null) {
                // RFC-6121 section 8.5.3.1. Resource Matches
                if (localRoutingTable.isLocalRoute(jid)) {
                    if (packet instanceof Message) {
                        ccMessage(jid, (Message) packet);
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
        return routed;
    }

    private void ccMessage(JID originalRecipient, Message message) {
        if (!Forwarded.isEligibleForCarbonsDelivery(message)) {
            return;
        }

        List<JID> routes = getRoutes(originalRecipient.asBareJID(), null);
        for (JID ccJid : routes) {
            // The receiving server MUST NOT send a forwarded copy to the full JID the original <message/> stanza was addressed to, as that recipient receives the original <message/> stanza.
            if (!ccJid.equals(originalRecipient)) {
                ClientSession clientSession = getClientRoute(ccJid);
                if (clientSession.isMessageCarbonsEnabled()) {
                    Message carbon = new Message();
                    // The wrapping message SHOULD maintain the same 'type' attribute value;
                    carbon.setType(message.getType());
                    // the 'from' attribute MUST be the Carbons-enabled user's bare JID
                    carbon.setFrom(ccJid.asBareJID());
                    // and the 'to' attribute MUST be the full JID of the resource receiving the copy
                    carbon.setTo(ccJid);
                    // The content of the wrapping message MUST contain a <received/> element qualified by the namespace "urn:xmpp:carbons:2", which itself contains a <forwarded/> element qualified by the namespace "urn:xmpp:forward:0" that contains the original <message/>.
                    carbon.addExtension(new Received(new Forwarded(message)));

                    try {
                        final RoutableChannelHandler localRoute = localRoutingTable.getRoute(ccJid);
                        if (localRoute != null) {
                            // This session is on a local cluster node
                            localRoute.process(carbon);
                        } else {
                            // The session is not on a local cluster node, so try a remote
                            final ClientRoute remoteRoute = getClientRouteForLocalUser(ccJid);
                            if (remotePacketRouter != null // If we're in a cluster
                                && remoteRoute != null // and we've found a route to the other node
                                && !remoteRoute.getNodeID().equals(XMPPServer.getInstance().getNodeID())) { // and it really is a remote node
                                // Try and route the packet to the remote session
                                remotePacketRouter.routePacket(remoteRoute.getNodeID().toByteArray(), ccJid, carbon);
                            } else {
                                Log.warn("Unable to find route to CC remote user {}", ccJid);
                            }
                        }
                    } catch (UnauthorizedException e) {
                        Log.error("Unable to route packet {}", message, e);
                    }
                }
            }
        }
    }

    private ClientRoute getClientRouteForLocalUser(JID jid) {
        ClientRoute clientRoute = usersCache.get(jid.toString());
        if (clientRoute == null) {
            clientRoute = anonymousUsersCache.get(jid.toString());
        }
        return clientRoute;
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
     * @return {@code true} if the packet was routed successfully,
     *         {@code false} otherwise.
     */
    private boolean routeToComponent(JID jid, Packet packet) {
        if (!hasComponentRoute(jid) 
                && !ExternalComponentManager.hasConfiguration(jid.getDomain())) {
            return false;
        }
        
        // First check if the component is being hosted in this JVM
        boolean routed = false;
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
     * @return {@code true} if the packet was routed successfully,
     *         {@code false} otherwise.
     */
    private boolean routeToRemoteDomain(JID jid, Packet packet) {
        if ( !JiveGlobals.getBooleanProperty( ConnectionSettings.Server.ALLOW_ANONYMOUS_OUTBOUND_DATA, false ) )
        {
            // Disallow anonymous local users to send data to other domains than the local domain.
            if ( isAnonymousRoute( packet.getFrom() ) )
            {
                Log.info( "The anonymous user '{}' attempted to send data to '{}', which is on a remote domain. Openfire is configured to not allow anonymous users to send data to remote domains.", packet.getFrom(), jid );
                return false;
            }
        }

        if (!RemoteServerManager.canAccess(jid.getDomain())) { // Check if the remote domain is in the blacklist
            Log.info( "Will not route: Remote domain {} is not accessible according to our configuration (typical causes: server federation is disabled, or domain is blacklisted).", jid.getDomain() );
            return false;
        }

        DomainPair domainPair = new DomainPair(packet.getFrom().getDomain(), jid.getDomain());

        Log.trace("Routing to remote domain: {}", packet);

        // It is possible that serversCache has an entry for this domain, while the OutgoingSessionPromise is
        // still processing its queue. Stanzas must be delivered in order, so delivery of this stanza needs to
        // be postponed until after the queue is empty (OF-2321).
        //
        // The code block below is guarded by the mutex that also guards:
        // - sending queued stanzas _after_ a connection has been established (but not the connection establishment itself),
        // - changes to the response of OutgoingServerSession#isPending() for the domainPair that is used to create the mutex
        // This will ensure that a new stanza:
        // - is supplied to OutgoingSessionPromise before queue processing is started / can start.
        // - is not supplied to OutgoingSessionPromise after queue processing has started. In that case, this code will
        //   block until the OutgoingSessionPromise is done processing, and will then route the stanza through the localRoutingTable.
        synchronized (OutgoingSessionPromise.getInstance().getMutex(domainPair))
        {
            if (OutgoingSessionPromise.getInstance().hasProcess(domainPair)) {
                Log.trace("An outgoing session for {} is in process of being established. Queuing stanza for delivery when that's done.", domainPair);
                OutgoingSessionPromise.getInstance().queue(domainPair, packet);
                return true;
            } else {
                NodeID nodeID = serversCache.get(domainPair);
                if (nodeID != null) {
                    if (server.getNodeID().equals(nodeID)) {
                        Log.trace("An outgoing session for {} is available on the local cluster node. Delivering stanza.", domainPair);
                        try {
                            localRoutingTable.getRoute(domainPair).process(packet);
                            return true;
                        } catch (UnauthorizedException e) {
                            Log.error("Unable to route packet " + packet.toXML(), e);
                            return false;
                        }
                    } else {
                        if (remotePacketRouter != null) {
                            Log.trace("An outgoing session for {} is available on a remote cluster node. Asking that node to deliver stanza.", domainPair);
                            return remotePacketRouter.routePacket(nodeID.toByteArray(), jid, packet);
                        } else {
                            Log.error("An outgoing session for {} is available on a remote cluster node, but no RemotePacketRouter exists!", domainPair);
                            return false;
                        }
                    }
                } else {
                    Log.trace("A new outgoing session for {} is needed. Instantiating a new queue stanza for delivery when that's done.", domainPair);
                    OutgoingSessionPromise.getInstance().createProcess(domainPair, packet);
                    return true;
                }
            }
        }
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
     * with highest priority by setting the system property {@code route.all-resources} to
     * {@code true}.
     *
     * @param recipientJID the bare JID of the target local user.
     * @param packet the message to send.
     * @return true if at least one target session was found
     */
    private boolean routeToBareJID(JID recipientJID, Message packet) {
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
            } else if (shouldCarbonCopyToResource(session, packet)) {
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
            if (!shouldCarbonCopyToResource(highestPrioritySessions.get(0), packet)) {
                highestPrioritySessions.get(0).process(packet);
            }
        }
        else {
            // Many sessions have the highest priority (be smart now) :)
            if (!JiveGlobals.getBooleanProperty("route.all-resources", false)) {
                // Sort sessions by show value (e.g. away, xa)
                highestPrioritySessions.sort(new Comparator<ClientSession>() {

                    @Override
                    public int compare(ClientSession o1, ClientSession o2) {
                        int thisVal = getShowValue(o1);
                        int anotherVal = getShowValue(o2);
                        return (Integer.compare(thisVal, anotherVal));
                    }

                    /**
                     * Priorities are: chat, available, away, xa, dnd.
                     */
                    private int getShowValue(ClientSession session) {
                        Presence.Show show = session.getPresence().getShow();
                        if (show == Presence.Show.chat) {
                            return 1;
                        } else if (show == null) {
                            return 2;
                        } else if (show == Presence.Show.away) {
                            return 3;
                        } else if (show == Presence.Show.xa) {
                            return 4;
                        } else {
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
                targets.sort((o1, o2) -> o2.getLastActiveDate().compareTo(o1.getLastActiveDate()));

                // Make sure, we don't send the packet again, if it has already been sent by message carbons.
                ClientSession session = targets.get(0);
                if (!shouldCarbonCopyToResource(session, packet)) {
                    // Deliver stanza to session with highest priority, highest show value and most recent activity
                    session.process(packet);
                }
            }
            else {
                for (ClientSession session : highestPrioritySessions) {
                    // Make sure, we don't send the packet again, if it has already been sent by message carbons.
                    if (!shouldCarbonCopyToResource(session, packet)) {
                        session.process(packet);
                    }
                }
            }
        }
        return true;
    }

    private boolean shouldCarbonCopyToResource(ClientSession session, Message message) {
        return session.isMessageCarbonsEnabled() && Forwarded.isEligibleForCarbonsDelivery(message );
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
                ClientRoute route = getClientRouteForLocalUser(jid);
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
        Collection<ClientSession> sessions = new ArrayList<>(localRoutingTable.getClientRoutes());
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
                NodeID nodeID = serversCache.get(jids);
                if (nodeID != null) {
                    session = locator.getOutgoingServerSession(nodeID.toByteArray(), jids);
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
        if ( jid.getResource() != null ) {
            // Check if there's a anonymous route for the JID.
            return anonymousUsersCache.containsKey( jid.toString() );
        } else {
            // Anonymous routes are mapped by full JID. if there's no full JID, check for any route for the node-part.
            return anonymousUsersCache.keySet().stream().anyMatch( key -> key.startsWith( jid.toString() ) );
        }
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
                ClientRoute clientRoute = getClientRouteForLocalUser(route);
                if (clientRoute != null &&
                        (clientRoute.isAvailable() || presenceUpdateHandler.hasDirectPresence(route, requester))) {
                    jids.add(route);
                }
            }
            else {
                // Address is a bare JID so return all AVAILABLE resources of user
                Lock lock = usersSessionsCache.getLock(route.toBareJID());
                lock.lock(); // temporarily block new sessions for this JID
                try {
                    Collection<String> sessions = usersSessionsCache.get(route.toBareJID());
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

        if (route.getResource() == null) {
            throw new IllegalArgumentException("For removing a client route, the argument 'route' must be a full JID, but was " + route);
        }

        boolean anonymous = false;
        boolean sessionRemoved = false;
        String address = route.toString();
        ClientRoute clientRoute;
        Lock lockU = usersCache.getLock(address);
        lockU.lock();
        int cacheSizeBefore = usersCache.size();
        try {
            clientRoute = usersCache.remove(address);
        }
        finally {
            lockU.unlock();
        }
        Log.debug("Removed users cache entry for {} / {}, changing entry count from {} to {}", route, clientRoute, cacheSizeBefore, usersCache.size());
        if (clientRoute == null) {
            Lock lockA = anonymousUsersCache.getLock(address);
            lockA.lock();
            try {
                clientRoute = anonymousUsersCache.remove(address);
                if (clientRoute != null) {
                    anonymous = true;
                }
            }
            finally {
                lockA.unlock();
            }
        }

        final String bareJID = route.toBareJID();

        if (usersSessionsCache.containsKey(bareJID)) {
            // The user session still needs to be removed
            if (clientRoute == null) {
                Log.warn("Client route not found for route {}, while user session still exists, Current content of users cache is {}", bareJID, usersCache);
            }

            Lock lock = usersSessionsCache.getLock(bareJID);
            lock.lock();
            try {
                if (anonymous) {
                    sessionRemoved = usersSessionsCache.remove(bareJID) != null;
                } else {
                    HashSet<String> jids = usersSessionsCache.get(bareJID);
                    if (jids != null) {
                        sessionRemoved = jids.remove(route.toString());
                        if (!jids.isEmpty()) {
                            usersSessionsCache.put(bareJID, jids);
                        } else {
                            usersSessionsCache.remove(bareJID);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        Log.debug("Removing client route {} from local routing table", route);
        localRoutingTable.removeRoute(new DomainPair("", route.toString()));
        return sessionRemoved;
    }

    @Override
    public boolean removeServerRoute(DomainPair route) {
        boolean removed;
        Lock lock = serversCache.getLock(route);
        lock.lock();
        try {
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
        return removeComponentRoute(route, server.getNodeID());
    }

    /**
     * Remove local or remote component route.
     *
     * @param route the route of the component to be removed.
     * @param nodeID The node to which the to-be-removed component was connected to.
     */
    private boolean removeComponentRoute(JID route, NodeID nodeID) {
        String address = route.getDomain();
        boolean removed = false;
        Lock lock = componentsCache.getLock(address);
        lock.lock();
        try {
            HashSet<NodeID> nodes = componentsCache.get(address);
            if (nodes != null) {
                nodes.remove(nodeID);
                if (nodes.isEmpty()) {
                    componentsCache.remove(address);
                    removed = true;
                }
                else {
                    componentsCache.put(address, nodes);
                }
            }
        } finally {
            lock.unlock();
        }

        if (removed || XMPPServer.getInstance().getNodeID().equals(nodeID)) {
            localRoutingTable.removeRoute(new DomainPair("", address));
        }

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
        // Listen to cluster events, and be one of the last listeners to handle events
        ClusterManager.addListener(this, 10);
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

        try
        {
            // Purge our own components from the cache for the benefit of other cluster nodes.
            CacheUtil.removeValueFromMultiValuedCache( componentsCache, XMPPServer.getInstance().getNodeID() );
        }
        catch ( Exception e )
        {
            Log.warn( "An exception occurred while trying to remove locally connected external components from the clustered cache. Other cluster nodes might continue to see our external components, even though we this instance is stopping.", e );
        }
    }

    /**
     * Verifies that {@link #serversCache}, {@link #localRoutingTable#getServerRoutes} and {@link #s2sDomainPairsByClusterNode}
     * are in a consistent state.
     *
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     *
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @return A consistency state report.
     * @see #serversCache which is the cache that is used tho share data with other cluster nodes.
     * @see LocalRoutingTable#getServerRoutes() which holds content added to the cache by the local cluster node.
     * @see #s2sDomainPairsByClusterNode which holds content added to the cache by cluster nodes other than the local node.
     */
    public Multimap<String, String> clusteringStateConsistencyReportForServerRoutes() {
        // Pass through defensive copies, that both prevent the diagnostics from affecting cache usage, as well as
        // give a better chance of representing a stable / snapshot-like representation of the state while diagnostics
        // are being performed.
        return ConsistencyChecks.generateReportForRoutingTableServerRoutes(serversCache, localRoutingTable.getServerRoutes(), new HashMap<>(s2sDomainPairsByClusterNode));
    }

    /**
     * Verifies that {@link #componentsCache}, {@link #localRoutingTable#getComponentRoute()} and {@link #componentsByClusterNode}
     * are in a consistent state.
     *
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     *
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @return A consistency state report.
     * @see #componentsCache which is the cache that is used tho share data with other cluster nodes.
     * @see LocalRoutingTable#getComponentRoute() which holds content added to the cache by the local cluster node.
     * @see #componentsByClusterNode which holds content added to the cache by cluster nodes other than the local node.
     */
    public Multimap<String, String> clusteringStateConsistencyReportForComponentRoutes() {
        // Pass through defensive copies, that both prevent the diagnostics from affecting cache usage, as well as
        // give a better chance of representing a stable / snapshot-like representation of the state while diagnostics
        // are being performed.
        return ConsistencyChecks.generateReportForRoutingTableComponentRoutes(componentsCache, localRoutingTable.getComponentRoute(), new HashMap<>(componentsByClusterNode));
    }

    /**
     * Verifies that {@link #usersCache}, {@link #anonymousUsersCache}, {@link #localRoutingTable#getClientsRoutes(boolean)}
     * and {@link #routeOwnersByClusterNode} are in a consistent state.
     *
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     *
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @return A consistency state report.
     * @see #usersCache which is one of the two caches that is used tho share data with other cluster nodes.
     * @see #anonymousUsersCache which is one of the two caches that is used tho share data with other cluster nodes.
     * @see LocalRoutingTable#getClientRoutes() which holds content added to the caches by the local cluster node.
     * @see #routeOwnersByClusterNode which holds content added to the caches by cluster nodes other than the local node.
     */
    public Multimap<String, String> clusteringStateConsistencyReportForClientRoutes() {
        // Pass through defensive copies, that both prevent the diagnostics from affecting cache usage, as well as
        // give a better chance of representing a stable / snapshot-like representation of the state while diagnostics
        // are being performed.
        return ConsistencyChecks.generateReportForRoutingTableClientRoutes(usersCache, anonymousUsersCache, localRoutingTable.getClientRoutes(), new HashMap<>(routeOwnersByClusterNode));
    }

    /**
     * Verifies that {@link #usersSessionsCache}, {@link #usersCache} and {@link #anonymousUsersCache} are in a
     * consistent state.
     *
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     *
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @return A consistency state report.
     * @see #usersSessionsCache which tracks user sessions.
     * @see #usersCache which is one of the two caches that is used tho share data with other cluster nodes.
     * @see #anonymousUsersCache which is one of the two caches that is used tho share data with other cluster nodes.
     */
    public Multimap<String, String> clusteringStateConsistencyReportForUsersSessions() {
        return ConsistencyChecks.generateReportForUserSessions(usersSessionsCache, usersCache, anonymousUsersCache);
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

        Log.debug("Add the entry listeners to the corresponding caches.");
        // Register a cache entry event listeners that will collect data for entries added by all other cluster nodes,
        // which is intended to be used (only) in the event of a cluster split.
        final ClusteredCacheEntryListener<String, ClientRoute> userCacheEntryListener = new ReverseLookupUpdatingCacheEntryListener<>(routeOwnersByClusterNode);
        final ClusteredCacheEntryListener<DomainPair, NodeID> serversCacheEntryListener = new ReverseLookupUpdatingCacheEntryListener<>(s2sDomainPairsByClusterNode);
        final ClusteredCacheEntryListener<String, HashSet<NodeID>> componentsCacheEntryListener = new ReverseLookupComputingCacheEntryListener<>(componentsByClusterNode,
            nodeIDS -> nodeIDS.stream().filter(n->!n.equals(XMPPServer.getInstance().getNodeID())).collect(Collectors.toSet())
        );

        // Note that, when #joinedCluster() fired, the cache will _always_ have been replaced, meaning that it won't
        // have old event listeners. When #leaveCluster() fires, the cache will be destroyed. This takes away the need
        // to explicitly deregister the listener in that case.
        // Ensure that event listeners have been registered with the caches, before starting to simulate 'entryAdded' events,
        // to prevent the possibility of having entries that are missed by the simulation because of bad timing.
        usersCache.addClusteredCacheEntryListener(userCacheEntryListener, false, false);
        anonymousUsersCache.addClusteredCacheEntryListener(userCacheEntryListener, false, false);
        serversCache.addClusteredCacheEntryListener(serversCacheEntryListener, false, false);
        componentsCache.addClusteredCacheEntryListener(componentsCacheEntryListener, true, true);
        // This is not necessary for the usersSessions cache, because its content is being managed while the content
        // of users cache and anonymous users cache is being managed.

        Log.debug("Simulate 'entryAdded' for all data that already exists elsewhere in the cluster.");
        Stream.concat(usersCache.entrySet().stream(), anonymousUsersCache.entrySet().stream())
            // this filter isn't needed if we do this before restoreCacheContent.
            .filter(entry -> !entry.getValue().getNodeID().equals(XMPPServer.getInstance().getNodeID()))
            .forEach(entry -> userCacheEntryListener.entryAdded(entry.getKey(), entry.getValue(), entry.getValue().getNodeID()));

        serversCache.entrySet().stream()
            // this filter isn't needed if we do this before restoreCacheContent.
            .filter(entry -> !entry.getValue().equals(XMPPServer.getInstance().getNodeID()))
            .forEach(entry -> serversCacheEntryListener.entryAdded(entry.getKey(), entry.getValue(), entry.getValue()));

        componentsCache.entrySet().forEach(entry -> {
            entry.getValue().forEach(nodeIdForComponent -> { // Iterate over all node ids on which the component is known
                    if (!nodeIdForComponent.equals(XMPPServer.getInstance().getNodeID())) {
                        // Here we pretend that the component has been added by the node id on which it is reported to
                        // be available. This might not have been the case, but it is probably accurate. An alternative
                        // approach is not easily available.
                        componentsCacheEntryListener.entryAdded(entry.getKey(), entry.getValue(), nodeIdForComponent);
                    }
                }
            );
        });

        // Broadcast presence of local sessions to remote sessions when subscribed to presence.
        // Probe presences of remote sessions when subscribed to presence of local session.
        // Send pending subscription requests to local sessions from remote sessions.
        // Deliver offline messages sent to local sessions that were unavailable in other nodes.
        // Send available presences of local sessions to other resources of the same user.
        PresenceUpdateHandler presenceUpdateHandler = XMPPServer.getInstance().getPresenceUpdateHandler();
        for (LocalClientSession session : localRoutingTable.getClientRoutes()) {
            // Simulate that the local session has just become available
            session.setInitialized(false);
            // Simulate that current session presence has just been received
            presenceUpdateHandler.process(session.getPresence());
        }
        // TODO OF-2067: the above also (re)generates events on the local node, where these events had already occurred. Ideally, that should not happen.

        // TODO OF-2066: shouldn't a similar action be done on the other nodes, so that the node that just joined gets informed about all sessions living on other cluster nodes?
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

        // All clients on all other nodes are now unavailable! Simulate an unavailable presence for sessions that were
        // being hosted in other cluster nodes.
        Set<JID> removedRoutes = new HashSet<>();
        routeOwnersByClusterNode.values().stream().flatMap(Collection::stream).forEach( fullJID -> {
            final JID offlineJID = new JID(fullJID);
            removeClientRoute(offlineJID);
            removedRoutes.add(offlineJID);
        });
        routeOwnersByClusterNode.clear();

        // Remove outgoing server sessions hosted in node that left the cluster
        s2sDomainPairsByClusterNode.values()
            .stream()
            .flatMap(Collection::stream)
            .forEach(domainPair -> {
                try {
                    removeServerRoute(domainPair);
                } catch (Exception e) {
                    Log.error("We have left the cluster. Federated connections on other nodes are no longer available. To reflect this, we're deleting these routes. While doing this for '{}', this caused an exception to occur.", domainPair, e);
                }
            });
        s2sDomainPairsByClusterNode.clear();

        // Remove component connections hosted in node that left the cluster
        for (Map.Entry<NodeID, Set<String>> entry : componentsByClusterNode.entrySet()) {
            NodeID nodeId = entry.getKey();
            for (String componentJid : entry.getValue()) {
                removeComponentRoute(new JID(componentJid), nodeId);
            }
        }

        restoreUsersSessionsCache();

        removedRoutes.forEach(offlineJID -> {
            try {
                final Presence presence = new Presence(Presence.Type.unavailable);
                presence.setFrom(offlineJID);
                XMPPServer.getInstance().getPresenceRouter().route(presence);
            }
            catch (final PacketException e) {
                Log.error("We have left the cluster. Users on other cluster nodes are no longer available. To reflect this, we're broadcasting presence unavailable on their behalf. While doing this for '{}', this caused an exception to occur.", offlineJID, e);
            }
        });
    }

    @Override
    public void leftCluster(byte[] nodeID)
    {
        // Another node left the cluster.
        final NodeID nodeIDOfLostNode = NodeID.getInstance(nodeID);
        Log.debug("Cluster node {} just left the cluster.", nodeIDOfLostNode);

        // When the local node drops out of the cluster (for example, due to a network failure), then from the perspective
        // of that node, all other nodes leave the cluster. This method is invoked for each of them. In certain
        // circumstances, this can mean that the local node no longer has access to all data (or its backups) that is
        // maintained in the clustered caches. From the perspective of the remaining node, this data is lost. (OF-2297/OF-2300).
        // To prevent this being an issue, most caches have supporting local data structures that maintain a copy of the most
        // critical bits of the data stored in the clustered cache, which is to be used to detect and/or correct such a
        // loss in data. This is done in the next few lines of this method.
        detectAndFixBrokenCaches(); // This excludes Users Sessions Cache, which is a bit of an odd duckling. This one is processed later in this method.

        // When a peer server leaves the cluster, any remote routes that were associated with the defunct node must be
        // dropped from the routing caches (and supporting data structures) that are shared by the remaining cluster member(s).

        // Note: All remaining cluster nodes will be in a race to clean up the same data. We can not depend on cluster
        // seniority to appoint a 'single' cleanup node, because for a small moment we may not have a senior cluster member.

        // Remove outgoing server routes accessed through the node that left the cluster.
        final Set<DomainPair> remoteServers = s2sDomainPairsByClusterNode.remove(nodeIDOfLostNode);
        CacheUtil.removeValueFromCache(serversCache, nodeIDOfLostNode); // Clean up remote data from the cache, but note that the return value can't be guaranteed to be correct/complete (do not use it)!
        if (remoteServers != null) {
            for (final DomainPair domainPair : remoteServers) {
                Log.debug("Removing server route for {} that is no longer available because cluster node {} left the cluster.", domainPair, nodeIDOfLostNode);
                removeServerRoute(domainPair);
            }
        }
        Log.info("Cluster node {} just left the cluster. A total of {} outgoing server sessions was living there, and are no longer available.", nodeIDOfLostNode, remoteServers == null ? 0 : remoteServers.size());

        // Remove component routes hosted in node that left the cluster.
        final Set<String> componentJids = componentsByClusterNode.remove(nodeIDOfLostNode);
        CacheUtil.removeValueFromMultiValuedCache(componentsCache, nodeIDOfLostNode); // Clean up remote data from the cache, but note that the return value can't be guaranteed to be correct/complete (do not use it)!
        int lostComponentsCount = 0;
        if (componentJids != null) {
            Log.debug("Removing node '{}' from componentsByClusteredNode: {}", nodeIDOfLostNode, componentJids);
            for (final String componentJid : componentJids) {
                if (removeComponentRoute(new JID(componentJid), nodeIDOfLostNode)) {
                    Log.debug("Removing component route for {} that is no longer available because cluster node {} left the cluster.", componentJid, nodeIDOfLostNode);
                    lostComponentsCount++;;
                }
            }
        }
        Log.info("Cluster node {} just left the cluster. A total of {} component sessions is now no longer available as a result.", nodeIDOfLostNode, lostComponentsCount);

        // Remove client routes hosted in node that left the cluster.
        final Set<String> removed = routeOwnersByClusterNode.remove(nodeIDOfLostNode);
        final AtomicLong removedSessionCount = new AtomicLong();
        if (removed != null) {
            removed.forEach(fullJID -> {
                Log.debug("Removing client route for {} that is no longer available because cluster node {} left the cluster.", fullJID, nodeIDOfLostNode);
                final JID offlineJID = new JID(fullJID);
                removeClientRoute(offlineJID);
                removedSessionCount.incrementAndGet();
            });
        }
        Log.debug("Cluster node {} just left the cluster. A total of {} client routes was living there, and are no longer available.", nodeIDOfLostNode, removedSessionCount.get());

        // With all of the other caches fixed and adjusted, process the Users Sessions Cache.
        restoreUsersSessionsCache();

        // Now that the users sessions cache is restored, we can proceed sending presence updates for all removed users.
        if (removed != null) {
            removed.forEach(fullJID -> {
                final JID offlineJID = new JID(fullJID);
                try {
                    final Presence presence = new Presence(Presence.Type.unavailable);
                    presence.setFrom(offlineJID);
                    XMPPServer.getInstance().getPresenceRouter().route(presence);
                    // TODO: OF-2302 This broadcasts the presence over the entire (remaining) cluster, which is too much because it is done by each remaining cluster node.
                } catch (final PacketException e) {
                    Log.error("Remote node {} left the cluster. Users on that node are no longer available. To reflect this, we're broadcasting presence unavailable on their behalf.  While doing this for '{}', this caused an exception to occur.", nodeIDOfLostNode, fullJID, e);
                }
            });
        }
    }

    /**
     * When the local node drops out of the cluster (for example, due to a network failure), then from the perspective
     * of that node, all other nodes leave the cluster. Under certain circumstances, this can mean that the local node
     * no longer has access to all data (or its backups) that is maintained in the clustered caches. From the
     * perspective of the remaining node, this data is lost. (OF-2297/OF-2300). To prevent this being an issue, most
     * caches have supporting local data structures that maintain a copy of the most critical bits of the data stored in
     * the clustered cache. This local copy can be used to detect and/or correct such a loss in data. This is performed
     * by this method.
     *
     * Note that this method is expected to be called as part of {@link #leftCluster(byte[])} only. It will therefor
     * mostly restore data that is considered local to the server node, and won't bother with data that's considered
     * to be pertinent to other cluster nodes only (as that data will be removed directly after invocation of this
     * method anyway).
     *
     * Note that this method does <em>not</em> process the users sessions cache, as that's a bit of an odd one out. This
     * cache is being processed in {@link #restoreUsersSessionsCache()}.
     */
    private void detectAndFixBrokenCaches()
    {
        // Ensure that 'serversCache' has content that reflects the locally available s2s connections (we do not need to
        // restore the s2s connections on other nodes, as those will be dropped right after invoking this method anyway).
        Log.info("Looking for local server routes that have 'dropped out' of the cache (likely as a result of a network failure).");
        final Collection<LocalOutgoingServerSession> localServerRoutes = localRoutingTable.getServerRoutes();
        final Set<DomainPair> cachesServerRoutes = serversCache.keySet();
        final Set<DomainPair> serverRoutesNotInCache = localServerRoutes.stream().map(LocalOutgoingServerSession::getOutgoingDomainPairs).flatMap(Collection::stream).collect(Collectors.toSet());
        serverRoutesNotInCache.removeAll(cachesServerRoutes);
        if (serverRoutesNotInCache.isEmpty()) {
            Log.info("Found no local server routes that are missing from the cache.");
        } else {
            Log.warn("Found {} server routes that we know locally, but are not (no longer) in the cache. This can occur when a cluster node fails, but should not occur otherwise. Missing server routes: {}", serverRoutesNotInCache.size(), serverRoutesNotInCache.stream().map(DomainPair::toString).collect(Collectors.joining(", ")));
            for (final DomainPair missing : serverRoutesNotInCache) {
                Log.info("Restoring server route: {}", missing);
                serversCache.put(missing, XMPPServer.getInstance().getNodeID());
            }
        }

        // Ensure that 'componentsCache' has content that reflects the locally available components. The component route
        // cache is special in the sense that an entry is not directly related to a single cluster node. Therefor we
        // need to ensure that all entries are in there, before surgically removing those that really need to be removed.
        // Restore cache from 'remote' data structure
        Log.info("Looking for and restoring component routes that have 'dropped out' of the cache (likely as a result of a network failure).");
        componentsByClusterNode.forEach((key, value) -> {
            for (final String componentDomain : value) {
                CacheUtil.addValueToMultiValuedCache(componentsCache, componentDomain, key, HashSet::new);
            }
        });
        // Restore cache from 'local' data structure
        localRoutingTable.getComponentRoute().forEach(route -> CacheUtil.addValueToMultiValuedCache(componentsCache, route.getAddress().getDomain(), server.getNodeID(), HashSet::new));

        // Ensure that 'usersCache' has content that reflects the locally available client connections (we do not need
        // to restore the client connections on other nodes, as those will be dropped right after invoking this method anyway).
        Log.info("Looking for local (non-anonymous) client routes that have 'dropped out' of the cache (likely as a result of a network failure).");
        final Collection<LocalClientSession> localClientRoutes = localRoutingTable.getClientRoutes();
        final Map<String, LocalClientSession> localUserRoutes = localClientRoutes.stream().filter(r -> !r.isAnonymousUser()).collect(Collectors.toMap((LocalClientSession localClientSession) -> localClientSession.getAddress().toString(), Function.identity()));
        final Set<String> cachedUsersRoutes = usersCache.keySet();
        final Set<String> userRoutesNotInCache = localUserRoutes.values().stream().map(LocalClientSession::getAddress).map(JID::toString).collect(Collectors.toSet());
        userRoutesNotInCache.removeAll(cachedUsersRoutes);
        if (userRoutesNotInCache.isEmpty()) {
            Log.info("Found no local (non-anonymous) user routes that are missing from the cache.");
        } else {
            Log.warn("Found {} (non-anonymous) user routes that we know locally, but are not (no longer) in the cache. This can occur when a cluster node fails, but should not occur otherwise.", userRoutesNotInCache.size());
            for (String missing : userRoutesNotInCache) {
                Log.info("Restoring (non-anonymous) user routes: {}", missing);
                final LocalClientSession localClientSession = localUserRoutes.get(missing);
                assert localClientSession != null; // We've established this with the filtering above.
                addClientRoute(localClientSession.getAddress(), localClientSession);
            }
        }

        // Ensure that 'anonymousUsersCache' has content that reflects the locally available client connections (we do not need
        // to restore the client connections on other nodes, as those will be dropped right after invoking this method anyway).
        Log.info("Looking for local (non-anonymous) client routes that have 'dropped out' of the cache (likely as a result of a network failure).");
        final Map<String, LocalClientSession> localAnonymousUserRoutes = localClientRoutes.stream().filter(LocalClientSession::isAnonymousUser).collect(Collectors.toMap((LocalClientSession localClientSession) -> localClientSession.getAddress().toString(), Function.identity()));
        final Set<String> cachedAnonymousUsersRoutes = anonymousUsersCache.keySet();
        final Set<String> anonymousUserRoutesNotInCache = new HashSet<>(localAnonymousUserRoutes.keySet()); // defensive copy - we should not modify localAnonymousUserRoutes!
        anonymousUserRoutesNotInCache.removeAll(cachedAnonymousUsersRoutes);
        if (anonymousUserRoutesNotInCache.isEmpty()) {
            Log.info("Found no local anonymous user routes that are missing from the cache.");
        } else {
            Log.warn("Found {} anonymous user routes that we know locally, but are not (no longer) in the cache. This can occur when a cluster node fails, but should not occur otherwise.", anonymousUserRoutesNotInCache.size());
            for (String missing : anonymousUserRoutesNotInCache) {
                Log.info("Restoring (non-anonymous) user route: {}", missing);
                final LocalClientSession localClientSession = localAnonymousUserRoutes.get(missing);
                assert localClientSession != null; // We've established this with the filtering above.
                addClientRoute(localClientSession.getAddress(), localClientSession);
            }
        }
    }

    /**
     * When the users sessions cache is (potentially) inconsistent, it can be rebuilt from routeOwnersByClusterNode and
     * routingTable.
     * This method relies on the routeOwnersByClusterNode and routingTable being stable and reflecting the actual
     * reality. So run this restore method <em>after or at the end of</em> cleanup on a cluster leave.
     */
    private void restoreUsersSessionsCache()
    {
        Log.info("Restoring Users Sessions Cache");

        // First remove all elements from users sessions cache that are not present in user caches
        final Set<String> existingUserRoutes = routeOwnersByClusterNode.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        existingUserRoutes.addAll(localRoutingTable.getClientRoutes().stream().map(LocalClientSession::getAddress).map(JID::toFullJID).collect(Collectors.toSet()));
        final Set<String> entriesToRemove = usersSessionsCache.values().stream()
            .flatMap(Collection::stream)
            .filter(fullJid -> !existingUserRoutes.contains(fullJid))
            .collect(Collectors.toSet());
        entriesToRemove.forEach(fullJid -> CacheUtil.removeValueFromMultiValuedCache(usersSessionsCache, new JID(fullJid).toBareJID(), fullJid));

        // Add elements from users caches that are not present in users sessions cache
        existingUserRoutes.forEach(fullJid -> {
            CacheUtil.addValueToMultiValuedCache(usersSessionsCache, new JID(fullJid).toBareJID(), fullJid, HashSet::new);
        });
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
        Log.debug( "Restoring cache content for cache '{}' by adding all outgoing server routes that are connected to the local cluster node.", serversCache.getName() );

        // Check if there are local s2s connections that are already in the cache for remote nodes
        Set<DomainPair> localServerRoutesToRemove = new HashSet<>();
        localRoutingTable.getServerRoutes().forEach(
            route -> route.getOutgoingDomainPairs().forEach(
                address -> {
                    final Lock lock = serversCache.getLock(address);
                    lock.lock();
                    try {
                        if (serversCache.containsKey(address)) {
                            Log.info("We have an s2s connection to {}, but this connection also exists on other nodes. They are not allowed to both exist, so this local s2s connection will be terminated.", address);
                            localServerRoutesToRemove.add(address);
                        } else {
                            serversCache.put(address, server.getNodeID());
                        }
                    } finally {
                        lock.unlock();
                    }
                })
        );
        for (DomainPair localServerRouteToRemove : localServerRoutesToRemove) {
            final RoutableChannelHandler route = localRoutingTable.getRoute(localServerRouteToRemove);
            if (route instanceof LocalOutgoingServerSession) {
                // Terminating the connection should also trigger the OutgoingServerSessionListener#onConnectionClose in SessionManagerImpl.
                // That will result in the s2s connection actually being removed from the LocalRoutingTable.
                try {
                    LocalOutgoingServerSession.class.cast(route).close();
                } catch (Exception e) {
                    Log.warn("Failed to terminate the local s2s connection for " + localServerRouteToRemove + ".", e);
                }
            } else {
                Log.warn("Failed to terminate the local s2s connection for {} because it is a {} instead of a LocalOutgoingServerSession.", localServerRouteToRemove, route.getClass());
            }
        }

        Log.debug( "Restoring cache content for cache '{}' by adding all component routes that are connected to the local cluster node.", componentsCache.getName() );
        localRoutingTable.getComponentRoute().forEach( route -> CacheUtil.addValueToMultiValuedCache( componentsCache, route.getAddress().getDomain(), server.getNodeID(), HashSet::new ));

        addLocalClientRoutesToCache();
    }

    public void addLocalClientRoutesToCache() {
        Log.debug( "Restoring cache content for cache '{}', '{}' and '{}' by adding all client routes that are connected to the local cluster node.", usersCache.getName(), anonymousUsersCache.getName(), usersSessionsCache.getName() );
        // Add client sessions hosted locally to the cache (using new nodeID)
        for (LocalClientSession session : localRoutingTable.getClientRoutes()) {
            addClientRoute(session.getAddress(), session);
        }
    }

}
