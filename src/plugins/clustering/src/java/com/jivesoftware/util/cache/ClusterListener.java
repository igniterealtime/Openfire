/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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
package com.jivesoftware.util.cache;

import com.tangosol.net.MemberEvent;
import com.tangosol.net.MemberListener;
import com.tangosol.util.MapEvent;
import com.tangosol.util.MapListener;
import com.tangosol.util.UID;
import com.tangosol.util.filter.MapEventFilter;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.handler.DirectedPresence;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.session.RemoteSessionLocator;
import org.jivesoftware.openfire.spi.ClientRoute;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheWrapper;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * ClusterListener reacts to membership changes in the cluster. It takes care of cleaning up the state
 * of the routing table and the sessions within it when a node which manages those sessions goes down.
 */
public class ClusterListener implements MemberListener {

    private static final int C2S_CACHE_IDX = 0;
    private static final int ANONYMOUS_C2S_CACHE_IDX = 1;
    private static final int S2S_CACHE_NAME_IDX= 2;
    private static final int COMPONENT_CACHE_IDX= 3;

    private static final int COMPONENT_SESSION_CACHE_IDX = 4;
    private static final int CM_CACHE_IDX = 5;
    private static final int ISS_CACHE_IDX = 6;

    /**
     * Caches stored in RoutingTable
     */
    Cache C2SCache;
    Cache anonymousC2SCache;
    Cache S2SCache;
    Cache componentsCache;

    /**
     * Caches stored in SessionManager
     */
    Cache componentSessionsCache;
    Cache multiplexerSessionsCache;
    Cache incomingServerSessionsCache;

    /**
     * Caches stored in PresenceUpdateHandler
     */
    Cache directedPresencesCache;

    private Map<NodeID, Set<String>[]> nodeSessions = new ConcurrentHashMap<NodeID, Set<String>[]>();
    private Map<NodeID, Map<String, Collection<String>>> nodePresences = new ConcurrentHashMap<NodeID, Map<String, Collection<String>>>();
    private boolean seniorClusterMember = CacheFactory.isSeniorClusterMember();

    private Map<Cache, MapListener> mapListeners = new HashMap<Cache, MapListener>();
    /**
     * Flag that indicates if the listener has done all clean up work when noticed that the
     * cluster has been stopped. This will force the EnterprisePlugin to wait until all clean
     * up (e.g. changing caches implementations) is done before destroying the plugin.
     */
    private boolean done = false;

    public ClusterListener() {
        C2SCache = CacheFactory.createCache(RoutingTableImpl.C2S_CACHE_NAME);
        anonymousC2SCache = CacheFactory.createCache(RoutingTableImpl.ANONYMOUS_C2S_CACHE_NAME);
        S2SCache = CacheFactory.createCache(RoutingTableImpl.S2S_CACHE_NAME);
        componentsCache = CacheFactory.createCache(RoutingTableImpl.COMPONENT_CACHE_NAME);

        componentSessionsCache = CacheFactory.createCache(SessionManager.COMPONENT_SESSION_CACHE_NAME);
        multiplexerSessionsCache = CacheFactory.createCache(SessionManager.CM_CACHE_NAME);
        incomingServerSessionsCache = CacheFactory.createCache(SessionManager.ISS_CACHE_NAME);

        directedPresencesCache = CacheFactory.createCache(PresenceUpdateHandler.PRESENCE_CACHE_NAME);

        addMapListener(C2SCache, new ClientSessionListener(this, C2SCache.getName()));
        addMapListener(anonymousC2SCache, new ClientSessionListener(this, anonymousC2SCache.getName()));
        addMapListener(S2SCache, new DefaultCacheListener(this, S2SCache.getName()));
        addMapListener(componentsCache, new ComponentCacheListener());

        addMapListener(componentSessionsCache, new DefaultCacheListener(this, componentSessionsCache.getName()));
        addMapListener(multiplexerSessionsCache, new DefaultCacheListener(this, multiplexerSessionsCache.getName()));
        addMapListener(incomingServerSessionsCache, new DefaultCacheListener(this, incomingServerSessionsCache.getName()));

        addMapListener(directedPresencesCache, new DirectedPresenceListener());

        // Simulate insert events of existing content
        simuateCacheInserts(C2SCache);
        simuateCacheInserts(anonymousC2SCache);
        simuateCacheInserts(S2SCache);
        simuateCacheInserts(componentsCache);
        simuateCacheInserts(componentSessionsCache);
        simuateCacheInserts(multiplexerSessionsCache);
        simuateCacheInserts(incomingServerSessionsCache);
        simuateCacheInserts(directedPresencesCache);
    }

    private void addMapListener(Cache cache, MapListener listener) {
        if (cache instanceof CacheWrapper) {
            Cache wrapped = ((CacheWrapper)cache).getWrappedCache();
            if (wrapped instanceof ClusteredCache) {
                ((ClusteredCache)wrapped).addMapListener(listener, new MapEventFilter(MapEventFilter.E_KEYSET), false);
                // Keep track of the listener that we added to the cache
                mapListeners.put(cache, listener);
            }
        }
    }

    private void simuateCacheInserts(Cache<Object, Object> cache) {
        MapListener mapListener = mapListeners.get(cache);
        if (mapListener != null) {
            if (cache instanceof CacheWrapper) {
                Cache wrapped = ((CacheWrapper) cache).getWrappedCache();
                if (wrapped instanceof ClusteredCache) {
                    ClusteredCache clusteredCache = (ClusteredCache) wrapped;
                    for (Map.Entry entry : cache.entrySet()) {
                        MapEvent event = new MapEvent(clusteredCache.map, MapEvent.ENTRY_INSERTED, entry.getKey(), null,
                                entry.getValue());
                        mapListener.entryInserted(event);
                    }
                }
            }
        }
    }

    Set<String> lookupJIDList(NodeID nodeKey, String cacheName) {
        Set<String>[] allLists = nodeSessions.get(nodeKey);
        if (allLists == null) {
            allLists = insertJIDList(nodeKey);
        }

        if (cacheName.equals(C2SCache.getName())) {
            return allLists[C2S_CACHE_IDX];
        }
        else if (cacheName.equals(anonymousC2SCache.getName())) {
            return allLists[ANONYMOUS_C2S_CACHE_IDX];
        }
        else if (cacheName.equals(S2SCache.getName())) {
            return allLists[S2S_CACHE_NAME_IDX];
        }
        else if (cacheName.equals(componentsCache.getName())) {
            return allLists[COMPONENT_CACHE_IDX];
        }
        else if (cacheName.equals(componentSessionsCache.getName())) {
            return allLists[COMPONENT_SESSION_CACHE_IDX];
        }
        else if (cacheName.equals(multiplexerSessionsCache.getName())) {
            return allLists[CM_CACHE_IDX];
        }
        else if (cacheName.equals(incomingServerSessionsCache.getName())) {
            return allLists[ISS_CACHE_IDX];
        }
        else {
            throw new IllegalArgumentException("Unknown cache name: " + cacheName);
        }
    }

    private Set<String>[] insertJIDList(NodeID nodeKey) {
        Set<String>[] allLists = new Set[] {
            new HashSet<String>(),
            new HashSet<String>(),
            new HashSet<String>(),
            new HashSet<String>(),
            new HashSet<String>(),
            new HashSet<String>(),
            new HashSet<String>()
        };
        nodeSessions.put(nodeKey, allLists);
        return allLists;
    }

    public boolean isDone() {
        return done;
    }

    public void memberJoined(MemberEvent memberEvent) {
        if (memberEvent.isLocal()) {
            done = false;
            // We left and re-joined the cluster
            Log.info("Rejoining cluster as node: " + new UID(CacheFactory.getClusterMemberID()) + ". Senior Member: " +
                    (CacheFactory.isSeniorClusterMember() ? "YES" : "NO"));
            // Simulate insert events of existing cache content
            simuateCacheInserts(C2SCache);
            simuateCacheInserts(anonymousC2SCache);
            simuateCacheInserts(S2SCache);
            simuateCacheInserts(componentsCache);
            simuateCacheInserts(componentSessionsCache);
            simuateCacheInserts(multiplexerSessionsCache);
            simuateCacheInserts(incomingServerSessionsCache);
            simuateCacheInserts(directedPresencesCache);
            // Set the new ID of this cluster node
            XMPPServer.getInstance().setNodeID(NodeID.getInstance(CacheFactory.getClusterMemberID()));
            // Trigger events
            ClusterManager.fireJoinedCluster(true);
            if (CacheFactory.isSeniorClusterMember()) {
                seniorClusterMember = true;
                ClusterManager.fireMarkedAsSeniorClusterMember();
            }
        }
        else {
            nodePresences.put(NodeID.getInstance(memberEvent.getMember().getUid().toByteArray()),
                    new ConcurrentHashMap<String, Collection<String>>());
            // Trigger event that a new node has joined the cluster
            ClusterManager.fireJoinedCluster(memberEvent.getMember().getUid().toByteArray(), true);
        }
    }

    public void memberLeaving(MemberEvent memberEvent) {
        // Ignore
    }

    public void memberLeft(MemberEvent memberEvent) {
        byte[] nodeID = memberEvent.getMember().getUid().toByteArray();

        if (memberEvent.isLocal()) {
            Log.info("Leaving cluster");
            // This node may have realized that it got kicked out of the cluster
            seniorClusterMember = false;
            // Clean up all traces. This will set all remote sessions as unavailable
            List<NodeID> nodeIDs = new ArrayList<NodeID>(nodeSessions.keySet());

            // Trigger event. Wait until the listeners have processed the event. Caches will be populated
            // again with local content.
            ClusterManager.fireLeftCluster();

            if (!XMPPServer.getInstance().isShuttingDown()) {
                for (NodeID key : nodeIDs) {
                    // Clean up directed presences sent from entites hosted in the leaving node to local entities
                    // Clean up directed presences sent to entites hosted in the leaving node from local entities
                    cleanupDirectedPresences(key);
                    // Clean up no longer valid sessions
                    cleanupPresences(key);
                }
                // Remove traces of directed presences sent from local entities to handlers that no longer exist
                // At this point c2s sessions are gone from the routing table so we can identify expired sessions
                XMPPServer.getInstance().getPresenceUpdateHandler().removedExpiredPresences();
            }

            // Mark that we are done with the clean up
            done = true;
        }
        else {
            // Trigger event that a node left the cluster
            ClusterManager.fireLeftCluster(nodeID);

            // Clean up directed presences sent from entites hosted in the leaving node to local entities
            // Clean up directed presences sent to entites hosted in the leaving node from local entities
            cleanupDirectedPresences(NodeID.getInstance(nodeID));

            if (!seniorClusterMember && CacheFactory.isSeniorClusterMember()) {
                seniorClusterMember = true;
                ClusterManager.fireMarkedAsSeniorClusterMember();
            }

            if (CacheFactory.isSeniorClusterMember()) {
                cleanupNode(NodeID.getInstance(nodeID));
            }
            // Remove traces of directed presences sent from local entities to handlers that no longer exist.
            // At this point c2s sessions are gone from the routing table so we can identify expired sessions
            XMPPServer.getInstance().getPresenceUpdateHandler().removedExpiredPresences();
        }
        // Delete nodeID instance (release from memory)
        NodeID.deleteInstance(nodeID);
    }

    private void cleanupDirectedPresences(NodeID nodeID) {
        // Remove traces of directed presences sent from node that is gone to entities hosted in this JVM
        Map<String, Collection<String>> senders = nodePresences.remove(nodeID);
        if (senders != null) {
            for (Map.Entry<String, Collection<String>> entry : senders.entrySet()) {
                String sender = entry.getKey();
                Collection<String> receivers = entry.getValue();
                for (String receiver : receivers) {
                    try {
                        Presence presence = new Presence(Presence.Type.unavailable);
                        presence.setFrom(sender);
                        presence.setTo(receiver);
                        XMPPServer.getInstance().getPresenceRouter().route(presence);
                    }
                    catch (PacketException e) {
                        Log.error(e);
                    }
                }
            }
        }
    }

    /**
     * Executes close logic for each session hosted in the remote node that is
     * no longer available. This logic is similar to the close listeners used by
     * the {@link SessionManager}.<p>
     *
     * If the node that went down performed its own clean up logic then the other
     * cluster nodes will have the correct state. That means that this method
     * will not find any sessions to remove.<p>
     *
     * If this operation is too big and we are still in a cluster then we can
     * distribute the work in the cluster to go faster.
     *
     * @param key the key that identifies the node that is no longer available.
     */
    private void cleanupNode(NodeID key) {
        // TODO Fork in another process and even ask other nodes to process work
        RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
        RemoteSessionLocator sessionLocator = XMPPServer.getInstance().getRemoteSessionLocator();
        SessionManager manager = XMPPServer.getInstance().getSessionManager();

        // TODO Consider removing each cached entry once processed instead of all at the end. Could be more error-prove.

        Set<String> registeredUsers = lookupJIDList(key, C2SCache.getName());
        if (!registeredUsers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(registeredUsers)) {
                JID offlineJID = new JID(fullJID);
                manager.removeSession(null, offlineJID, false, true);
            }
        }

        Set<String> anonymousUsers = lookupJIDList(key, anonymousC2SCache.getName());
        if (!anonymousUsers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(anonymousUsers)) {
                JID offlineJID = new JID(fullJID);
                manager.removeSession(null, offlineJID, true, true);
            }
        }

        // Remove outgoing server sessions hosted in node that left the cluster
        Set<String> remoteServers = lookupJIDList(key, S2SCache.getName());
        if (!remoteServers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(remoteServers)) {
                JID serverJID = new JID(fullJID);
                routingTable.removeServerRoute(serverJID);
            }
        }

        Set<String> components = lookupJIDList(key, componentsCache.getName());
        if (!components.isEmpty()) {
            for (String address : new ArrayList<String>(components)) {
                Lock lock = CacheFactory.getLock(address, componentsCache);
                try {
                    lock.lock();
                    Set<NodeID> nodes = (Set<NodeID>) componentsCache.get(address);
                    if (nodes != null) {
                        nodes.remove(key);
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
            }
        }

        Set<String> componentSessions = lookupJIDList(key, componentSessionsCache.getName());
        if (!componentSessions.isEmpty()) {
            for (String domain : new ArrayList<String>(componentSessions)) {
                componentSessionsCache.remove(domain);
                // Registered subdomains of external component will be removed
                // by the clean up of the component cache
            }
        }

        Set<String> multiplexers = lookupJIDList(key, multiplexerSessionsCache.getName());
        if (!multiplexers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(multiplexers)) {
                multiplexerSessionsCache.remove(fullJID);
                // c2s connections connected to node that went down will be cleaned up
                // by the c2s logic above. If the CM went down and the node is up then
                // connections will be cleaned up as usual
            }
        }

        Set<String> incomingSessions = lookupJIDList(key, incomingServerSessionsCache.getName());
        if (!incomingSessions.isEmpty()) {
            for (String streamID : new ArrayList<String>(incomingSessions)) {
                IncomingServerSession session = sessionLocator.getIncomingServerSession(key.toByteArray(), streamID);
                // Remove all the hostnames that were registered for this server session
                for (String hostname : session.getValidatedDomains()) {
                    manager.unregisterIncomingServerSession(hostname, session);
                }
            }
        }
        nodeSessions.remove(key);
        // TODO Make sure that routing table has no entry referring to node that is gone
    }

    /**
     * Simulate an unavailable presence for sessions that were being hosted in other
     * cluster nodes. This method should be used ONLY when this JVM left the cluster.
     *
     * @param key the key that identifies the node that is no longer available.
     */
    private void cleanupPresences(NodeID key) {
        Set<String> registeredUsers = lookupJIDList(key, C2SCache.getName());
        if (!registeredUsers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(registeredUsers)) {
                JID offlineJID = new JID(fullJID);
                try {
                    Presence presence = new Presence(Presence.Type.unavailable);
                    presence.setFrom(offlineJID);
                    XMPPServer.getInstance().getPresenceRouter().route(presence);
                }
                catch (PacketException e) {
                    Log.error(e);
                }
            }
        }

        Set<String> anonymousUsers = lookupJIDList(key, anonymousC2SCache.getName());
        if (!anonymousUsers.isEmpty()) {
            for (String fullJID : new ArrayList<String>(anonymousUsers)) {
                JID offlineJID = new JID(fullJID);
                try {
                    Presence presence = new Presence(Presence.Type.unavailable);
                    presence.setFrom(offlineJID);
                    XMPPServer.getInstance().getPresenceRouter().route(presence);
                }
                catch (PacketException e) {
                    Log.error(e);
                }
            }
        }

        nodeSessions.remove(key);
    }

    /**
     * MapListener implementation tracks events for caches whose value is a nodeID.
     */
    private static class DefaultCacheListener extends CacheListener {

        public DefaultCacheListener(ClusterListener clusterListener, String cacheName) {
            super(clusterListener, cacheName);
        }

        NodeID getNodeID(MapEvent mapEvent, boolean removal) {
            Object value = removal ? mapEvent.getOldValue() : mapEvent.getNewValue();
            return NodeID.getInstance((byte[])value);
        }

    }

    /**
     * MapListener implementation tracks events for caches of c2s sessions.
     */
    private static class ClientSessionListener extends CacheListener {

        public ClientSessionListener(ClusterListener clusterListener, String cacheName) {
            super(clusterListener, cacheName);
        }

        NodeID getNodeID(MapEvent mapEvent, boolean removal) {
            Object value = removal ? mapEvent.getOldValue() : mapEvent.getNewValue();
            return ((ClientRoute)value).getNodeID();
        }
    }

    /**
     * MapListener implementation tracks events for caches of c2s sessions.
     */
    private class DirectedPresenceListener implements MapListener {

        public void entryInserted(MapEvent mapEvent) {
            byte[] nodeID = getNodeID(mapEvent, false);
            // Ignore events origintated from this JVM
            if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
                // Check if the directed presence was sent to an entity hosted by this JVM
                RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
                String sender = mapEvent.getKey().toString();
                Collection<String> handlers = new HashSet<String>();
                for (JID handler : getHandlers(mapEvent)) {
                    if (routingTable.isLocalRoute(handler)) {
                        // Keep track of the remote sender and local handler that got the directed presence
                        handlers.addAll(getReceivers(mapEvent, handler));
                    }
                }
                if (!handlers.isEmpty()) {
                    Map<String, Collection<String>> senders = nodePresences.get(NodeID.getInstance(nodeID));
                    if (senders == null) {
                        senders = new ConcurrentHashMap<String, Collection<String>>();
                        nodePresences.put(NodeID.getInstance(nodeID), senders);
                    }
                    senders.put(sender, handlers);
                }
            }
        }

        public void entryUpdated(MapEvent mapEvent) {
            byte[] nodeID = getNodeID(mapEvent, false);
            // Ignore events origintated from this JVM
            if (nodeID != null && !XMPPServer.getInstance().getNodeID().equals(nodeID)) {
                // Check if the directed presence was sent to an entity hosted by this JVM
                RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
                String sender = mapEvent.getKey().toString();
                Collection<String> handlers = new HashSet<String>();
                for (JID handler : getHandlers(mapEvent)) {
                    if (routingTable.isLocalRoute(handler)) {
                        // Keep track of the remote sender and local handler that got the directed presence
                        handlers.addAll(getReceivers(mapEvent, handler));
                    }
                }
                Map<String, Collection<String>> senders = nodePresences.get(NodeID.getInstance(nodeID));
                if (senders == null) {
                    senders = new ConcurrentHashMap<String, Collection<String>>();
                    nodePresences.put(NodeID.getInstance(nodeID), senders);
                }
                if (!handlers.isEmpty()) {
                    senders.put(sender, handlers);
                }
                else {
                    // Remove any traces of the sender since no directed presence was sent to this JVM
                    senders.remove(sender);
                }
            }
        }

        public void entryDeleted(MapEvent mapEvent) {
            if (mapEvent.getNewValue() == null && ((Collection)mapEvent.getOldValue()).isEmpty()) {
                // Nothing to remove
                return;
            }
            byte[] nodeID = getNodeID(mapEvent, true);
            if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
                String sender = mapEvent.getKey().toString();
                nodePresences.get(NodeID.getInstance(nodeID)).remove(sender);
            }
        }

        byte[] getNodeID(MapEvent mapEvent, boolean removal) {
            Object value = removal ? mapEvent.getOldValue() : mapEvent.getNewValue();
            Collection<DirectedPresence> directedPresences = (Collection<DirectedPresence>) value;
            if (directedPresences.isEmpty()) {
                Log.warn("ClusteringListener - Found empty directed presences for sender: " + mapEvent.getKey());
                return null;
            }
            return directedPresences.iterator().next().getNodeID();
        }

        Collection<JID> getHandlers(MapEvent mapEvent) {
            Object value = mapEvent.getNewValue();
            Collection<JID> answer = new ArrayList<JID>();
            for (DirectedPresence directedPresence : (Collection<DirectedPresence>)value) {
                answer.add(directedPresence.getHandler());
            }
            return answer;
        }

        Set<String> getReceivers(MapEvent mapEvent, JID handler) {
            Object value = mapEvent.getNewValue();
            for (DirectedPresence directedPresence : (Collection<DirectedPresence>)value) {
                if (directedPresence.getHandler().equals(handler)) {
                    return directedPresence.getReceivers();
                }
            }
            return Collections.emptySet();
        }
    }

    /**
     * MapListener implementation tracks events for caches of internal/external components.
     */
    private class ComponentCacheListener implements MapListener {

        public void entryInserted(MapEvent mapEvent) {
            Object newValue = mapEvent.getNewValue();
            if (newValue != null) {
                for (NodeID nodeID : (Set<NodeID>) newValue) {
                    //ignore items which this node has added
                    if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
                        Set<String> sessionJIDS = lookupJIDList(nodeID, componentsCache.getName());
                        sessionJIDS.add(mapEvent.getKey().toString());
                    }
                }
            }
        }

        public void entryUpdated(MapEvent mapEvent) {
            // Remove any trace to the component that was added/deleted to some node
            String domain = mapEvent.getKey().toString();
            for (Map.Entry<NodeID, Set<String>[]> entry : nodeSessions.entrySet()) {
                // Get components hosted in this node
                Set<String> nodeComponents = entry.getValue()[COMPONENT_CACHE_IDX];
                nodeComponents.remove(domain);
            }
            // Trace nodes hosting the component
            entryInserted(mapEvent);
        }

        public void entryDeleted(MapEvent mapEvent) {
            Object newValue = mapEvent.getNewValue();
            if (newValue != null) {
                for (NodeID nodeID : (Set<NodeID>) newValue) {
                    //ignore items which this node has added
                    if (!XMPPServer.getInstance().getNodeID().equals(nodeID)) {
                        Set<String> sessionJIDS = lookupJIDList(nodeID, componentsCache.getName());
                        sessionJIDS.remove(mapEvent.getKey().toString());
                    }
                }
            }
        }
    }
}
