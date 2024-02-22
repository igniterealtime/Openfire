/*
 * Copyright (C) 2021-2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.util.cache;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jivesoftware.openfire.RoutableChannelHandler;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.spi.OccupantManager;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.spi.ClientRoute;
import org.jivesoftware.util.CollectionUtils;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * This class defines methods that verify that the state of a cache and its various supporting data structures (in which
 * some data duplication is expected) is consistent.
 *
 * This code has been taken from the classes that are responsible for maintaining the cache to reduce the code complexity
 * of those classes.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ConsistencyChecks {
    /**
     * Verifies that #serversCache, #localRoutingTable#getServerRoutes and #s2sDomainPairsByClusterNode of
     * {@link org.jivesoftware.openfire.spi.RoutingTableImpl} are in a consistent state.
     * <p>
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     * <p>
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @param serversCache                The cache that is used to share data across cluster nodes
     * @param localServerRoutes           The data structure that keeps track of what data was added to the cache by the local cluster node.
     * @param s2sDomainPairsByClusterNode The data structure that keeps track of what data was added to the cache by the remote cluster nodes.
     * @return A consistency state report.
     */
    public static Multimap<String, String> generateReportForRoutingTableServerRoutes(
        @Nonnull final Cache<DomainPair, NodeID> serversCache,
        @Nonnull final Collection<LocalOutgoingServerSession> localServerRoutes,
        @Nonnull final HashMap<NodeID, Set<DomainPair>> s2sDomainPairsByClusterNode) {
        final Set<NodeID> clusterNodeIDs = ClusterManager.getNodesInfo().stream().map(ClusterNodeInfo::getNodeID).collect(Collectors.toSet());

        // Take a snapshots to reduce the chance of data changing while diagnostics are being performed
        final ConcurrentMap<DomainPair, NodeID> cache = new ConcurrentHashMap<>(serversCache);

        final List<DomainPair> localServerRoutesAddressing = localServerRoutes.stream().map(LocalOutgoingServerSession::getOutgoingDomainPairs).flatMap(Collection::stream).collect(Collectors.toList());
        final Set<DomainPair> localServerRoutesAddressingDuplicates = CollectionUtils.findDuplicates(localServerRoutesAddressing);

        final List<DomainPair> remoteServerRoutesAddressing = s2sDomainPairsByClusterNode.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        final List<String> remoteServerRoutesAddressingWithNodeId = new ArrayList<>();
        for (Map.Entry<NodeID, Set<DomainPair>> entry : s2sDomainPairsByClusterNode.entrySet()) {
            for (DomainPair item : entry.getValue()) {
                remoteServerRoutesAddressingWithNodeId.add(item + " (" + entry.getKey() + ")");
            }
        }
        final Set<DomainPair> remoteServerRoutesAddressingDuplicates = CollectionUtils.findDuplicates(remoteServerRoutesAddressing);

        final Set<DomainPair> serverRoutesAddressingBothLocalAndRemote = CollectionUtils.findDuplicates(localServerRoutesAddressing, remoteServerRoutesAddressing);

        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d S2S routes.", serversCache.getName(), cache.size()));
        result.put("info", String.format("LocalRoutingTable's getServerRoutes() response is used to track 'local' data to be restored after a cache switch-over. It tracks %d routes, for a combined %d pairs.", localServerRoutes.size(), localServerRoutesAddressing.size()));
        result.put("info", String.format("The field s2sDomainPairsByClusterNode is used to track data in the cache from every other cluster node. It contains %d routes for %d cluster nodes.", s2sDomainPairsByClusterNode.values().stream().reduce(0, (subtotal, values) -> subtotal + values.size(), Integer::sum), s2sDomainPairsByClusterNode.keySet().size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", serversCache.getName(), cache.keySet().stream().map(DomainPair::toString).collect(Collectors.joining("\n"))));
        result.put("data", String.format("LocalRoutingTable's getServerRoutes() response contains these entries (these represent 'local' data):\n%s", localServerRoutes.stream().map(LocalOutgoingServerSession::getOutgoingDomainPairs)
            .map(pairsForOneEntry -> pairsForOneEntry.stream().map(DomainPair::toString).collect(Collectors.joining("")))
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("s2sDomainPairsByClusterNode contains these entries (these represent 'remote' data):\n%s", String.join("\n", remoteServerRoutesAddressingWithNodeId)));

        if (localServerRoutesAddressingDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in addressing of LocalRoutingTable's getServerRoutes() response (They are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in addressing of LocalRoutingTable's getServerRoutes() response (They are not all unique values). These %d values are duplicated: %s", localServerRoutesAddressingDuplicates.size(), localServerRoutesAddressingDuplicates.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        if (remoteServerRoutesAddressingDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in s2sDomainPairsByClusterNode (They are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in s2sDomainPairsByClusterNode (They are not all unique values). These %d values are duplicated: %s", remoteServerRoutesAddressingDuplicates.size(), remoteServerRoutesAddressingDuplicates.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        if (!s2sDomainPairsByClusterNode.containsKey(XMPPServer.getInstance().getNodeID())) {
            result.put("pass", "s2sDomainPairsByClusterNode does not track data for the local cluster node.");
        } else {
            result.put("fail", "s2sDomainPairsByClusterNode tracks data for the local cluster node.");
        }

        if (clusterNodeIDs.containsAll(s2sDomainPairsByClusterNode.keySet())) {
            result.put("pass", "s2sDomainPairsByClusterNode tracks data for cluster nodes that are recognized in the cluster.");
        } else {
            result.put("fail", String.format("s2sDomainPairsByClusterNode tracks data for cluster nodes that are not recognized. All cluster nodeIDs as recognized: %s All cluster nodeIDs for which data is tracked: %s.", clusterNodeIDs.stream().map(NodeID::toString).collect(Collectors.joining(", ")), s2sDomainPairsByClusterNode.keySet().stream().map(NodeID::toString).collect(Collectors.joining(", "))));
        }

        if (serverRoutesAddressingBothLocalAndRemote.isEmpty()) {
            result.put("pass", "There are no elements that are both 'remote' (in s2sDomainPairsByClusterNode) as well as 'local' (in LocalRoutingTable's getServerRoutes()).");
        } else {
            result.put("fail", String.format("There are %d elements that are both 'remote' (in s2sDomainPairsByClusterNode) as well as 'local' (in LocalRoutingTable's getServerRoutes()): %s", serverRoutesAddressingBothLocalAndRemote.size(), serverRoutesAddressingBothLocalAndRemote.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        final Set<DomainPair> nonCachedLocalServerRouteAddressing = localServerRoutesAddressing.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());
        if (nonCachedLocalServerRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All elements in LocalRoutingTable's getServerRoutes() response exist in %s.", serversCache.getName()));
        } else {
            result.put("fail", String.format("Not all elements in of LocalRoutingTable's getServerRoutes() response exist in %s. These %d entries do not: %s", serversCache.getName(), nonCachedLocalServerRouteAddressing.size(), nonCachedLocalServerRouteAddressing.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        final Set<DomainPair> nonCachedRemoteServerRouteAddressing = remoteServerRoutesAddressing.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());
        if (nonCachedRemoteServerRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All elements in s2sDomainPairsByClusterNode exist in %s.", serversCache.getName()));
        } else {
            result.put("fail", String.format("Not all route owners in s2sDomainPairsByClusterNode exist in %s. These %d entries do not: %s", serversCache.getName(), nonCachedRemoteServerRouteAddressing.size(), nonCachedRemoteServerRouteAddressing.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        final Set<DomainPair> nonLocallyStoredCachedServerRouteAddressing = cache.keySet().stream().filter(v -> !localServerRoutesAddressing.contains(v)).filter(v -> !remoteServerRoutesAddressing.contains(v)).collect(Collectors.toSet());
        if (nonLocallyStoredCachedServerRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All cache entries of %s exist in s2sDomainPairsByClusterNode and/or LocalRoutingTable's getServerRoutes() response.", serversCache.getName()));
        } else {
            result.put("fail", String.format("Not all cache entries of %s exist in s2sDomainPairsByClusterNode and/or LocalRoutingTable's getServerRoutes() response. These %d entries do not: %s", serversCache.getName(), nonLocallyStoredCachedServerRouteAddressing.size(), nonLocallyStoredCachedServerRouteAddressing.stream().map(DomainPair::toString).collect(Collectors.joining(", "))));
        }

        return result;
    }

    /**
     * Verifies that #componentsCache, #localRoutingTable#getComponentRoute and #componentsByClusterNode of
     * {@link org.jivesoftware.openfire.spi.RoutingTableImpl} are in a consistent state.
     * <p>
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     * <p>
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @param componentsCache         The cache that is used to share data across cluster nodes
     * @param localComponentRoutes    The data structure that keeps track of what data was added to the cache by the local cluster node.
     * @param componentsByClusterNode The data structure that keeps track of what data was added to the cache by the remote cluster nodes.
     * @return A consistency state report.
     */
    public static Multimap<String, String> generateReportForRoutingTableComponentRoutes(
        @Nonnull final Cache<String, HashSet<NodeID>> componentsCache,
        @Nonnull final Collection<RoutableChannelHandler> localComponentRoutes,
        @Nonnull final HashMap<NodeID, Set<String>> componentsByClusterNode) {
        final Set<NodeID> clusterNodeIDs = ClusterManager.getNodesInfo().stream().map(ClusterNodeInfo::getNodeID).collect(Collectors.toSet());

        // Take a snapshots to reduce the chance of data changing while diagnostics are being performed
        final ConcurrentMap<String, HashSet<NodeID>> cache = new ConcurrentHashMap<>(componentsCache);

        final List<String> localComponentRoutesAddressing = localComponentRoutes.stream().map(r -> r.getAddress().toString()).collect(Collectors.toList());
        final Set<String> localComponentRoutesAddressingDuplicates = CollectionUtils.findDuplicates(localComponentRoutesAddressing);

        final List<String> remoteComponentRoutesAddressingWithNodeId = new ArrayList<>();
        for (Map.Entry<NodeID, Set<String>> entry : componentsByClusterNode.entrySet()) {
            for (String item : entry.getValue()) {
                remoteComponentRoutesAddressingWithNodeId.add(item + " (" + entry.getKey() + ")");
            }
        }
        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d component routes.", componentsCache.getName(), cache.size()));
        result.put("info", String.format("LocalRoutingTable's getComponentRoute() response is used to track 'local' data to be restored after a cache switch-over. It tracks %d routes.", localComponentRoutesAddressing.size()));
        result.put("info", String.format("The field componentsByClusterNode is used to track data in the cache from every other cluster node. It contains %d routes for %d cluster nodes.", componentsByClusterNode.values().stream().reduce(0, (subtotal, values) -> subtotal + values.size(), Integer::sum), componentsByClusterNode.keySet().size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", componentsCache.getName(), cache.entrySet().stream().map(e -> e.getKey() + "on nodes: " + e.getValue().stream().map(NodeID::toString).collect(Collectors.joining(", "))).collect(Collectors.joining("\n"))));
        result.put("data", String.format("LocalRoutingTable's getComponentRoute() response contains these entries (these represent 'local' data):\n%s", localComponentRoutes.stream().map(RoutableChannelHandler::getAddress).map(JID::toString).collect(Collectors.joining("\n"))));
        result.put("data", String.format("componentsByClusterNode contains these entries (these represent 'remote' data):\n%s", String.join("\n", remoteComponentRoutesAddressingWithNodeId)));

        if (localComponentRoutesAddressingDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in addressing of LocalRoutingTable's getComponentRoute() response (They are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in addressing of LocalRoutingTable's getComponentRoute() response (They are not all unique values). These %d values are duplicated: %s", localComponentRoutesAddressingDuplicates.size(), String.join(", ", localComponentRoutesAddressingDuplicates)));
        }

        if (!componentsByClusterNode.containsKey(XMPPServer.getInstance().getNodeID())) {
            result.put("pass", "componentsByClusterNode does not track data for the local cluster node.");
        } else {
            result.put("fail", "componentsByClusterNode tracks data for the local cluster node.");
        }

        if (clusterNodeIDs.containsAll(componentsByClusterNode.keySet())) {
            result.put("pass", "componentsByClusterNode tracks data for cluster nodes that are recognized in the cluster.");
        } else {
            result.put("fail", String.format("componentsByClusterNode tracks data for cluster nodes that are not recognized. All cluster nodeIDs as recognized: %s All cluster nodeIDs for which data is tracked: %s.", clusterNodeIDs.stream().map(NodeID::toString).collect(Collectors.joining(", ")), componentsByClusterNode.keySet().stream().map(NodeID::toString).collect(Collectors.joining(", "))));
        }

        final Set<String> nonCachedLocalComponentRouteAddressing = localComponentRoutesAddressing
            .stream()
            .filter(v -> !cache.containsKey(v) || !cache.get(v).contains(XMPPServer.getInstance().getNodeID()))
            .collect(Collectors.toSet());
        if (nonCachedLocalComponentRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All elements in LocalRoutingTable's getComponentRoute() response exist in %s.", componentsCache.getName()));
        } else {
            result.put("fail", String.format("Not all elements in of LocalRoutingTable's getComponentRoute() response exist in %s. These %d entries do not: %s", componentsCache.getName(), nonCachedLocalComponentRouteAddressing.size(), nonCachedLocalComponentRouteAddressing.stream().map(v -> v + " on " + XMPPServer.getInstance().getNodeID()).collect(Collectors.joining(", "))));
        }

        final Set<String> nonCachedRemoteComponentRouteAddressing = new HashSet<>();
        for (final Map.Entry<NodeID, Set<String>> entry : componentsByClusterNode.entrySet()) {
            final NodeID remoteNodeID = entry.getKey();
            final Set<String> remoteComponentAddresses = entry.getValue();
            for (final String remoteComponentAddress : remoteComponentAddresses) {
                if (!cache.containsKey(remoteComponentAddress) || !cache.get(remoteComponentAddress).contains(remoteNodeID)) {
                    nonCachedRemoteComponentRouteAddressing.add(remoteComponentAddress + " on " + remoteNodeID);
                }
            }
        }
        if (nonCachedRemoteComponentRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All elements in componentsByClusterNode exist in %s.", componentsCache.getName()));
        } else {
            result.put("fail", String.format("Not all component routes in componentsByClusterNode exist in %s. These %d entries do not: %s", componentsCache.getName(), nonCachedRemoteComponentRouteAddressing.size(), String.join(", ", nonCachedLocalComponentRouteAddressing)));
        }

        final Set<String> nonLocallyStoredCachedComponentRouteAddressing = new HashSet<>();
        for (final Map.Entry<String, HashSet<NodeID>> entry : cache.entrySet()) {
            final String componentAddress = entry.getKey();
            final Set<NodeID> nodeIDs = entry.getValue();
            for (final NodeID nodeID : nodeIDs) {
                if (nodeID.equals(XMPPServer.getInstance().getNodeID())) {
                    if (localComponentRoutes.stream().noneMatch(v -> v.getAddress().toString().equals(componentAddress))) {
                        nonLocallyStoredCachedComponentRouteAddressing.add(componentAddress + " on " + nodeID + " (the local cluster node)");
                    }
                } else {
                    if (!componentsByClusterNode.containsKey(nodeID) || !componentsByClusterNode.get(nodeID).contains(componentAddress)) {
                        nonLocallyStoredCachedComponentRouteAddressing.add(componentAddress + " on " + nodeID);
                    }
                }
            }
        }
        if (nonLocallyStoredCachedComponentRouteAddressing.isEmpty()) {
            result.put("pass", String.format("All cache entries of %s exist in componentsByClusterNode and/or LocalRoutingTable's getComponentRoute() response.", componentsCache.getName()));
        } else {
            result.put("fail", String.format("Not all cache entries of %s exist in componentsByClusterNode and/or LocalRoutingTable's getComponentRoute() response. These %d entries do not: %s", componentsCache.getName(), nonLocallyStoredCachedComponentRouteAddressing.size(), String.join(", ", nonLocallyStoredCachedComponentRouteAddressing)));
        }

        return result;
    }

    /**
     * Verifies that usersCache, anonymousUsersCache, localRoutingTable.getClientRoutes and routeOwnersByClusterNode
     * of {@link org.jivesoftware.openfire.spi.RoutingTableImpl} are in a consistent state.
     * <p>
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     * <p>
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @param usersCache               The cache that is used to share data across cluster nodes
     * @param anonymousUsersCache      The cache that is used to share data across cluster nodes
     * @param localClientRoutes        The data structure that keeps track of what data was added to the cache by the local cluster node.
     * @param routeOwnersByClusterNode The data structure that keeps track of what data was added to the cache by the remote cluster nodes.
     * @return A consistency state report.
     */
    public static Multimap<String, String> generateReportForRoutingTableClientRoutes(
        @Nonnull final Cache<String, ClientRoute> usersCache,
        @Nonnull final Cache<String, ClientRoute> anonymousUsersCache,
        @Nonnull final Collection<LocalClientSession> localClientRoutes,
        @Nonnull final Map<NodeID, Set<String>> routeOwnersByClusterNode) {
        final Set<NodeID> clusterNodeIDs = ClusterManager.getNodesInfo().stream().map(ClusterNodeInfo::getNodeID).collect(Collectors.toSet());

        // Take snapshots of all data structures at as much the same time as possible.
        final Set<String> usersCacheKeySet = usersCache.keySet();
        final Set<String> anonymousUsersCacheKeySet = anonymousUsersCache.keySet();

        final Set<String> userRouteCachesDuplicates = CollectionUtils.findDuplicates(usersCacheKeySet, anonymousUsersCacheKeySet);

        final List<String> localClientRoutesOwners = localClientRoutes.stream().map(r -> r.getAddress().toString()).collect(Collectors.toList());
        final Set<String> localClientRoutesOwnersDuplicates = CollectionUtils.findDuplicates(localClientRoutesOwners);

        final List<String> remoteClientRoutesOwners = routeOwnersByClusterNode.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        final List<String> remoteClientRoutesOwnersWithNodeId = new ArrayList<>();
        for (Map.Entry<NodeID, Set<String>> entry : routeOwnersByClusterNode.entrySet()) {
            for (String item : entry.getValue()) {
                remoteClientRoutesOwnersWithNodeId.add(item + " (" + entry.getKey() + ")");
            }
        }

        final Set<String> remoteClientRoutesOwnersDuplicates = CollectionUtils.findDuplicates(remoteClientRoutesOwners);

        final Set<String> clientRoutesBothLocalAndRemote = CollectionUtils.findDuplicates(localClientRoutesOwners, remoteClientRoutesOwners);

        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("Two caches are used to share data in the cluster: %s and %s, which contain %d and %d user routes respectively (%d combined).", usersCache.getName(), anonymousUsersCache.getName(), usersCacheKeySet.size(), anonymousUsersCacheKeySet.size(), usersCacheKeySet.size() + anonymousUsersCacheKeySet.size()));
        result.put("info", String.format("LocalRoutingTable's getClientRoutes() response is used to track 'local' data to be restored after a cache switch-over (for both caches). It tracks %d routes.", localClientRoutes.size()));
        result.put("info", String.format("The field routeOwnersByClusterNode is used to track data in the cache from every other cluster node. It contains %d routes for %d cluster nodes.", routeOwnersByClusterNode.values().stream().reduce(0, (subtotal, values) -> subtotal + values.size(), Integer::sum), routeOwnersByClusterNode.keySet().size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", usersCache.getName(), String.join("\n", usersCacheKeySet)));
        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", anonymousUsersCache.getName(), String.join("\n", anonymousUsersCacheKeySet)));
        result.put("data", String.format("LocalRoutingTable's getClientRoutes() response contains these entries (these represent 'local' data):\n%s", String.join("\n", localClientRoutesOwners)));
        result.put("data", String.format("routeOwnersByClusterNode contains these entries (these represent 'remote' data):\n%s", String.join("\n", remoteClientRoutesOwnersWithNodeId)));

        if (userRouteCachesDuplicates.isEmpty()) {
            result.put("pass", String.format("There is no overlap in keys of the %s and %s (They are all unique values).", usersCache.getName(), anonymousUsersCache.getName()));
        } else {
            result.put("fail", String.format("There is overlap in keys of the %s and %s caches (They are not all unique values). These %d values exist in both caches: %s", usersCache.getName(), anonymousUsersCache.getName(), userRouteCachesDuplicates.size(), String.join(", ", userRouteCachesDuplicates)));
        }

        if (localClientRoutesOwnersDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in route owners of LocalRoutingTable's getClientRoutes() response (They are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in route owners of LocalRoutingTable's getClientRoutes() response (They are not all unique values). These %d values are duplicated: %s", localClientRoutesOwnersDuplicates.size(), String.join(", ", localClientRoutesOwnersDuplicates)));
        }

        if (remoteClientRoutesOwnersDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in routeOwnersByClusterNode (They are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in routeOwnersByClusterNode (They are not all unique values). These %d values are duplicated: %s", remoteClientRoutesOwnersDuplicates.size(), String.join(", ", remoteClientRoutesOwnersDuplicates)));
        }

        if (!routeOwnersByClusterNode.containsKey(XMPPServer.getInstance().getNodeID())) {
            result.put("pass", "routeOwnersByClusterNode does not track data for the local cluster node.");
        } else {
            result.put("fail", "routeOwnersByClusterNode tracks data for the local cluster node.");
        }

        if (clusterNodeIDs.containsAll(routeOwnersByClusterNode.keySet())) {
            result.put("pass", "routeOwnersByClusterNode tracks data for cluster nodes that are recognized in the cluster.");
        } else {
            result.put("fail", String.format("routeOwnersByClusterNode tracks data for cluster nodes that are not recognized. All cluster nodeIDs as recognized: %s All cluster nodeIDs for which data is tracked: %s.", clusterNodeIDs.stream().map(NodeID::toString).collect(Collectors.joining(", ")), routeOwnersByClusterNode.keySet().stream().map(NodeID::toString).collect(Collectors.joining(", "))));
        }

        if (clientRoutesBothLocalAndRemote.isEmpty()) {
            result.put("pass", "There are no elements that are both 'remote' (in routeOwnersByClusterNode) as well as 'local' (in LocalRoutingTable's getClientRoutes()).");
        } else {
            result.put("fail", String.format("There are %d elements that are both 'remote' (in routeOwnersByClusterNode) as well as 'local' (in LocalRoutingTable's getClientRoutes()): %s", clientRoutesBothLocalAndRemote.size(), String.join(", ", clientRoutesBothLocalAndRemote)));
        }

        final Set<String> nonCachedLocalClientRoutesOwners = localClientRoutesOwners.stream().filter(v -> !usersCacheKeySet.contains(v)).filter(v -> !anonymousUsersCacheKeySet.contains(v)).collect(Collectors.toSet());
        if (nonCachedLocalClientRoutesOwners.isEmpty()) {
            result.put("pass", String.format("All route owners of LocalRoutingTable's getClientRoutes() response exist in %s and/or %s.", usersCache.getName(), anonymousUsersCache.getName()));
        } else {
            result.put("fail", String.format("Not all route owners of LocalRoutingTable's getClientRoutes() response exist in %s and/or %s. These %d entries do not: %s", usersCache.getName(), anonymousUsersCache.getName(), nonCachedLocalClientRoutesOwners.size(), String.join(", ", nonCachedLocalClientRoutesOwners)));
        }

        final Set<String> nonCachedRemoteClientRouteOwners = remoteClientRoutesOwners.stream().filter(v -> !usersCacheKeySet.contains(v)).filter(v -> !anonymousUsersCacheKeySet.contains(v)).collect(Collectors.toSet());
        if (nonCachedRemoteClientRouteOwners.isEmpty()) {
            result.put("pass", String.format("All route owners in routeOwnersByClusterNode exist in %s and/or %s.", usersCache.getName(), anonymousUsersCache.getName()));
        } else {
            result.put("fail", String.format("Not all route owners in routeOwnersByClusterNode exist in %s and/or %s. These %d entries do not: %s", usersCache.getName(), anonymousUsersCache.getName(), nonCachedRemoteClientRouteOwners.size(), String.join(", ", nonCachedRemoteClientRouteOwners)));
        }

        final Set<String> nonLocallyStoredCachedRouteOwners = usersCacheKeySet.stream().filter(v -> !localClientRoutesOwners.contains(v)).filter(v -> !remoteClientRoutesOwners.contains(v)).collect(Collectors.toSet());
        if (nonLocallyStoredCachedRouteOwners.isEmpty()) {
            result.put("pass", String.format("All cache entries of %s exist in routeOwnersByClusterNode and/or LocalRoutingTable's getClientRoutes() response.", usersCache.getName()));
        } else {
            result.put("fail", String.format("Not all cache entries of %s exist in routeOwnersByClusterNode and/or LocalRoutingTable's getClientRoutes() response. These %d entries do not: %s", usersCache.getName(), nonLocallyStoredCachedRouteOwners.size(), String.join(", ", nonLocallyStoredCachedRouteOwners)));
        }

        final Set<String> nonLocallyStoredCachedAnonRouteOwners = anonymousUsersCacheKeySet.stream().filter(v -> !localClientRoutesOwners.contains(v)).filter(v -> !remoteClientRoutesOwners.contains(v)).collect(Collectors.toSet());
        if (nonLocallyStoredCachedAnonRouteOwners.isEmpty()) {
            result.put("pass", String.format("All cache entries of %s exist in routeOwnersByClusterNode and/or LocalRoutingTable's getClientRoutes() response.", anonymousUsersCache.getName()));
        } else {
            result.put("fail", String.format("Not all cache entries of %s exist in routeOwnersByClusterNode and/or LocalRoutingTable's getClientRoutes() response. These %d entries do not: %s", anonymousUsersCache.getName(), nonLocallyStoredCachedAnonRouteOwners.size(), String.join(", ", nonLocallyStoredCachedAnonRouteOwners)));
        }

        return result;
    }

    /**
     * Verifies that #incomingServerSessionsCache, #localIncomingServerSessions and #incomingServerSessionsByClusterNode
     * of {@link org.jivesoftware.openfire.SessionManager} are in a consistent state.
     * <p>
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     * <p>
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @param incomingServerSessionsCache         The cache that is used to share data across cluster nodes
     * @param localIncomingServerSessions         The data structure that keeps track of what data was added to the cache by the local cluster node.
     * @param incomingServerSessionsByClusterNode The data structure that keeps track of what data was added to the cache by the remote cluster nodes.
     * @return A consistency state report.
     */
    public static Multimap<String, String> generateReportForSessionManagerIncomingServerSessions(
        @Nonnull final Cache<StreamID, IncomingServerSessionInfo> incomingServerSessionsCache,
        @Nonnull final Collection<LocalIncomingServerSession> localIncomingServerSessions,
        @Nonnull final Map<NodeID, Set<StreamID>> incomingServerSessionsByClusterNode
    ) {
        final Set<NodeID> clusterNodeIDs = ClusterManager.getNodesInfo().stream().map(ClusterNodeInfo::getNodeID).collect(Collectors.toSet());

        // Take snapshots of all data structures at as much the same time as possible.
        final ConcurrentMap<StreamID, IncomingServerSessionInfo> cache = new ConcurrentHashMap<>(incomingServerSessionsCache);
        final List<StreamID> localIncomingServerSessionsStreamIDs = localIncomingServerSessions.stream().map(LocalIncomingServerSession::getStreamID).collect(Collectors.toList());
        final List<StreamID> remoteIncomingServerSessions = incomingServerSessionsByClusterNode.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        final List<String> remoteIncomingServerSessionsWithNodeId = new ArrayList<>();
        for (Map.Entry<NodeID, Set<StreamID>> entry : incomingServerSessionsByClusterNode.entrySet()) {
            for (StreamID item : entry.getValue()) {
                remoteIncomingServerSessionsWithNodeId.add(item + " (" + entry.getKey() + ")");
            }
        }

        // Duplicates detection
        final Set<StreamID> localIncomingServerSessionsDuplicates = CollectionUtils.findDuplicates(localIncomingServerSessionsStreamIDs);
        final Set<StreamID> remoteIncomingServerSessionsDuplicates = CollectionUtils.findDuplicates(remoteIncomingServerSessions);
        final Set<StreamID> incomingServerSessionsBothLocalAndRemote = CollectionUtils.findDuplicates(localIncomingServerSessionsStreamIDs, remoteIncomingServerSessions);

        // Detection of other inconsistencies
        final Set<StreamID> nonLocallyStoredCachedIncomingServerSessions = cache.keySet()
            .stream()
            .filter(v -> !localIncomingServerSessionsStreamIDs.contains(v))
            .filter(v -> !remoteIncomingServerSessions.contains(v))
            .collect(Collectors.toSet());
        final Set<StreamID> nonCachedLocalIncomingServerSessions = localIncomingServerSessionsStreamIDs.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());
        final Set<StreamID> nonCachedRemoteIncomingServerSessions = remoteIncomingServerSessions.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());

        // Generate report
        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d incoming server sessions.", incomingServerSessionsCache.getName(), cache.size()));
        result.put("info", String.format("SessionManager's TODO response is used to track 'local' data to be restored after a cache switch-over. It tracks %d incoming server sessions.", localIncomingServerSessionsStreamIDs.size()));
        result.put("info", String.format("The field incomingServerSessionsByClusterNode is used to track data in the cache from every other cluster node. It contains %d routes for %d cluster nodes.", incomingServerSessionsByClusterNode.values().stream().reduce(0, (subtotal, values) -> subtotal + values.size(), Integer::sum), incomingServerSessionsByClusterNode.size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", incomingServerSessionsCache.getName(), cache.keySet()
            .stream()
            .map(StreamID::getID)
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("SessionManager's localSessionManager contains these entries (these represent 'local' data):\n%s", localIncomingServerSessionsStreamIDs
            .stream()
            .map(StreamID::getID)
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("incomingServerSessionsByClusterNode contains these entries (these represent 'remote' data):\n%s", String.join("\n", remoteIncomingServerSessionsWithNodeId)));

        if (!incomingServerSessionsByClusterNode.containsKey(XMPPServer.getInstance().getNodeID())) {
            result.put("pass", "incomingServerSessionsByClusterNode does not track data for the local cluster node.");
        } else {
            result.put("fail", "incomingServerSessionsByClusterNode tracks data for the local cluster node.");
        }

        if (clusterNodeIDs.containsAll(incomingServerSessionsByClusterNode.keySet())) {
            result.put("pass", "incomingServerSessionsByClusterNode tracks data for cluster nodes that are recognized in the cluster.");
        } else {
            result.put("fail", String.format("incomingServerSessionsByClusterNode tracks data for cluster nodes that are not recognized. All cluster nodeIDs as recognized: %s All cluster nodeIDs for which data is tracked: %s.", clusterNodeIDs.stream().map(NodeID::toString).collect(Collectors.joining(", ")), incomingServerSessionsByClusterNode.keySet().stream().map(NodeID::toString).collect(Collectors.joining(", "))));
        }

        if (localIncomingServerSessionsDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in local incoming server sessions (they are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in local incoming server sessions (they are not all unique values). These %d values are duplicated: %s", localIncomingServerSessionsDuplicates.size(), localIncomingServerSessionsDuplicates.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        if (remoteIncomingServerSessionsDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in incomingServerSessionsByClusterNode (they are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in incomingServerSessionsByClusterNode (they are not all unique values). These %d values are duplicated: %s", remoteIncomingServerSessionsDuplicates.size(), remoteIncomingServerSessionsDuplicates.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        if (incomingServerSessionsBothLocalAndRemote.isEmpty()) {
            result.put("pass", "There are no elements that are both 'remote' (in incomingServerSessionsByClusterNode) as well as 'local' (in SessionManager's localSessionManager).");
        } else {
            result.put("fail", String.format("There are %d elements that are both 'remote' (in incomingServerSessionsByClusterNode) as well as 'local' (in SessionManager's localSessionManager): %s", incomingServerSessionsBothLocalAndRemote.size(), incomingServerSessionsBothLocalAndRemote.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        if (nonCachedLocalIncomingServerSessions.isEmpty()) {
            result.put("pass", String.format("All elements in SessionManager's localSessionManager exist in %s.", incomingServerSessionsCache.getName()));
        } else {
            result.put("fail", String.format("Not all elements in SessionManager's localSessionManager exist in %s. These %d entries do not: %s", incomingServerSessionsCache.getName(), nonCachedLocalIncomingServerSessions.size(), nonCachedLocalIncomingServerSessions.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        if (nonCachedRemoteIncomingServerSessions.isEmpty()) {
            result.put("pass", String.format("All elements inincomingServerSessionsByClusterNode exist in %s.", incomingServerSessionsCache.getName()));
        } else {
            result.put("fail", String.format("Not all elements in incomingServerSessionsByClusterNode exist in %s. These %d entries do not: %s", incomingServerSessionsCache.getName(), nonCachedRemoteIncomingServerSessions.size(), nonCachedRemoteIncomingServerSessions.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        if (nonLocallyStoredCachedIncomingServerSessions.isEmpty()) {
            result.put("pass", String.format("All cache entries of %s exist in incomingServerSessionsByClusterNode and/or SessionManager's localSessionManager.", incomingServerSessionsCache.getName()));
        } else {
            result.put("fail", String.format("Not all cache entries of %s exist in incomingServerSessionsByClusterNode and/or SessionManager's localSessionManager. These %d entries do not: %s", incomingServerSessionsCache.getName(), nonLocallyStoredCachedIncomingServerSessions.size(), nonLocallyStoredCachedIncomingServerSessions.stream().map(StreamID::getID).collect(Collectors.joining(", "))));
        }

        return result;
    }

    /**
     * Verifies that #sessionInfoCache, #localSessionInfos and #sessionInfoKeysByClusterNode
     * of {@link org.jivesoftware.openfire.SessionManager} are in a consistent state.
     * <p>
     * Note that this operation can be costly in terms of resource usage. Use with caution in large / busy systems.
     * <p>
     * The returned multi-map can contain up to four keys: info, fail, pass, data. All entry values are a human readable
     * description of a checked characteristic. When the state is consistent, no 'fail' entries will be returned.
     *
     * @param sessionInfoCache             The cache that is used to share data across cluster nodes. (note that unlike its siblings, this is empty when not clustering!)
     * @param localSessions                The data structure that keeps track of what data was added to the cache by the local cluster node.
     * @param sessionInfoKeysByClusterNode The data structure that keeps track of what data was added to the cache by the remote cluster nodes.
     * @return A consistency state report.
     */
    public static Multimap<String, String> generateReportForSessionManagerSessionInfos(
        @Nonnull final Cache<String, ClientSessionInfo> sessionInfoCache,
        @Nonnull final Collection<ClientSession> localSessions,
        @Nonnull final Map<NodeID, Set<String>> sessionInfoKeysByClusterNode
    ) {
        final Set<NodeID> clusterNodeIDs = ClusterManager.getNodesInfo().stream().map(ClusterNodeInfo::getNodeID).collect(Collectors.toSet());

        // Take snapshots of all data structures at as much the same time as possible.
        final ConcurrentMap<String, ClientSessionInfo> cache = new ConcurrentHashMap<>(sessionInfoCache);
        final List<String> localSessionInfosJids = localSessions.stream().map(s -> s.getAddress().toString()).collect(Collectors.toList());
        final List<String> remoteSessionInfosJids = sessionInfoKeysByClusterNode.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        final List<String> remoteSessionInfosJidsWithNodeId = new ArrayList<>();
        for (Map.Entry<NodeID, Set<String>> entry : sessionInfoKeysByClusterNode.entrySet()) {
            for (String item : entry.getValue()) {
                remoteSessionInfosJidsWithNodeId.add(item + " (" + entry.getKey() + ")");
            }
        }

        // Duplicates detection
        final Set<String> localSessionInfosDuplicates = CollectionUtils.findDuplicates(localSessionInfosJids);
        final Set<String> remoteSessionInfosDuplicates = CollectionUtils.findDuplicates(remoteSessionInfosJids);
        final Set<String> sessionInfosBothLocalAndRemote = CollectionUtils.findDuplicates(localSessionInfosJids, remoteSessionInfosJids);

        // Detection of other inconsistencies
        final Set<String> nonLocallyStoredCachedSessionInfos = cache.keySet()
            .stream()
            .filter(v -> !localSessionInfosJids.contains(v))
            .filter(v -> !remoteSessionInfosJids.contains(v))
            .collect(Collectors.toSet());
        final Set<String> nonCachedLocalSessionInfos = localSessionInfosJids.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());
        final Set<String> nonCachedRemoteSessionInfos = remoteSessionInfosJids.stream().filter(v -> !cache.containsKey(v)).collect(Collectors.toSet());

        // Generate report
        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d session infos.", sessionInfoCache.getName(), cache.size()));
        result.put("info", String.format("RoutingTable's getClientsRoutes response is used to track 'local' data to be restored after a cache switch-over. It tracks %d session infos.", localSessionInfosJids.size()));
        result.put("info", String.format("The field sessionInfosByClusterNode is used to track data in the cache from every other cluster node. It contains %d routes for %d cluster nodes.", sessionInfoKeysByClusterNode.values().stream().reduce(0, (subtotal, values) -> subtotal + values.size(), Integer::sum), sessionInfoKeysByClusterNode.size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", sessionInfoCache.getName(), String.join("\n", cache.keySet())));
        result.put("data", String.format("RoutingTable's getClientsRoutes contains these entries (these represent 'local' data):\n%s", String.join("\n", localSessionInfosJids)));
        result.put("data", String.format("sessionInfosByClusterNode contains these entries (these represent 'remote' data):\n%s", String.join("\n", remoteSessionInfosJidsWithNodeId)));

        if (!sessionInfoKeysByClusterNode.containsKey(XMPPServer.getInstance().getNodeID())) {
            result.put("pass", "sessionInfoKeysByClusterNode does not track data for the local cluster node.");
        } else {
            result.put("fail", "sessionInfoKeysByClusterNode tracks data for the local cluster node.");
        }

        if (clusterNodeIDs.containsAll(sessionInfoKeysByClusterNode.keySet())) {
            result.put("pass", "sessionInfoKeysByClusterNode tracks data for cluster nodes that are recognized in the cluster.");
        } else {
            result.put("fail", String.format("sessionInfoKeysByClusterNode tracks data for cluster nodes that are not recognized. All cluster nodeIDs as recognized: %s All cluster nodeIDs for which data is tracked: %s.", clusterNodeIDs.stream().map(NodeID::toString).collect(Collectors.joining(", ")), sessionInfoKeysByClusterNode.keySet().stream().map(NodeID::toString).collect(Collectors.joining(", "))));
        }

        if (localSessionInfosDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in local session infos (they are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in local session infos (they are not all unique values). These %d values are duplicated: %s", localSessionInfosDuplicates.size(), String.join(", ", localSessionInfosDuplicates)));
        }

        if (remoteSessionInfosDuplicates.isEmpty()) {
            result.put("pass", "There is no overlap in sessionInfosByClusterNode (they are all unique values).");
        } else {
            result.put("fail", String.format("There is overlap in sessionInfosByClusterNode (they are not all unique values). These %d values are duplicated: %s", remoteSessionInfosDuplicates.size(), String.join(", ", remoteSessionInfosDuplicates)));
        }

        if (sessionInfosBothLocalAndRemote.isEmpty()) {
            result.put("pass", "There are no elements that are both 'remote' (in sessionInfosByClusterNode) as well as 'local' (in SessionManager's localSessionManager).");
        } else {
            result.put("fail", String.format("There are %d elements that are both 'remote' (in sessionInfosByClusterNode) as well as 'local' (in SessionManager's localSessionManager): %s", sessionInfosBothLocalAndRemote.size(), String.join(", ", sessionInfosBothLocalAndRemote)));
        }

        if (!ClusterManager.isClusteringStarted()) {
            if (!cache.isEmpty()) {
                result.put("fail", String.format("Clustering is not started. The Cache named %s is expected to be unused when clustering is not used, but is not empty!",  sessionInfoCache.getName()));
            }
        } else {
            if (nonCachedLocalSessionInfos.isEmpty()) {
                result.put("pass", String.format("All elements in SessionManager's localSessionManager exist in %s.", sessionInfoCache.getName()));
            } else {
                result.put("fail", String.format("Not all elements in SessionManager's localSessionManager exist in %s. These %d entries do not: %s", sessionInfoCache.getName(), nonCachedLocalSessionInfos.size(), String.join(", ", nonCachedLocalSessionInfos)));
            }

            if (nonCachedRemoteSessionInfos.isEmpty()) {
                result.put("pass", String.format("All elements in sessionInfosByClusterNode exist in %s.", sessionInfoCache.getName()));
            } else {
                result.put("fail", String.format("Not all elements in sessionInfosByClusterNode exist in %s. These %d entries do not: %s", sessionInfoCache.getName(), nonCachedRemoteSessionInfos.size(), String.join(", ", nonCachedRemoteSessionInfos)));
            }

            if (nonLocallyStoredCachedSessionInfos.isEmpty()) {
                result.put("pass", String.format("All cache entries of %s exist in sessionInfosByClusterNode and/or RoutingTable's getClientsRoutes.", sessionInfoCache.getName()));
            } else {
                result.put("fail", String.format("Not all cache entries of %s exist in sessionInfosByClusterNode and/or RoutingTable's getClientsRoutes. These %d entries do not: %s", sessionInfoCache.getName(), nonLocallyStoredCachedSessionInfos.size(), String.join(", ", nonLocallyStoredCachedSessionInfos)));
            }
        }

        return result;
    }

    public static Multimap<String, String> generateReportForUserSessions(
        @Nonnull final Cache<String, HashSet<String>> usersSessionsCache,
        @Nonnull final Cache<String, ClientRoute> usersCache,
        @Nonnull final Cache<String, ClientRoute> anonymousUsersCache
    ) {
        // Take snapshots of all data structures at as much the same time as possible.
        final ConcurrentMap<String, HashSet<String>> cache = new ConcurrentHashMap<>(usersSessionsCache);
        final Set<String> usersCacheKeys = usersCache.keySet();
        final Set<String> anonymousUsersCacheKeys = anonymousUsersCache.keySet();

        final Set<String> userCacheKeysNotInSessionsCache = usersCacheKeys.stream()
            .filter(fullJid -> {
                HashSet<String> fullJids = cache.get(new JID(fullJid).toBareJID());
                return fullJids == null || !fullJids.contains(fullJid);
            })
            .collect(Collectors.toSet());

        final Set<String> anonymousUserCacheKeysNotInSessionsCache = anonymousUsersCacheKeys.stream()
            .filter(fullJid -> {
                HashSet<String> fullJids = cache.get(new JID(fullJid).toBareJID());
                return fullJids == null || !fullJids.contains(fullJid);
            })
            .collect(Collectors.toSet());

        final Set<String> sessionCacheItemsNotInUserCaches = cache.values().stream()
            .flatMap(HashSet::stream)
            .filter(fullJid -> !usersCacheKeys.contains(fullJid) && !anonymousUsersCacheKeys.contains(fullJid))
            .collect(Collectors.toSet());

        final Set<String> duplicatesBetweenAnonAndNonAnonUsers = CollectionUtils.findDuplicates(usersCacheKeys, anonymousUsersCacheKeys);

        // Generate report
        final Multimap<String, String> result = HashMultimap.create();

        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d session infos.", usersSessionsCache.getName(), cache.size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", usersSessionsCache.getName(), cache.entrySet()
            .stream()
            .map(e -> e.getKey() + " -> " + e.getValue())
            .sorted()
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", usersCache.getName(), usersCacheKeys.stream().sorted()
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", anonymousUsersCache.getName(), anonymousUsersCacheKeys.stream().sorted()
            .collect(Collectors.joining("\n"))));

        if (userCacheKeysNotInSessionsCache.isEmpty()) {
            result.put("pass", "All user cache entries exist in the user sessions cache.");
        } else {
            result.put("fail", String.format("User sessions cache is missing entries that are present in the user cache. These %d entries are missing: %s", userCacheKeysNotInSessionsCache.size(), String.join(", ", userCacheKeysNotInSessionsCache)));
        }

        if (anonymousUserCacheKeysNotInSessionsCache.isEmpty()) {
            result.put("pass", "All anonymous user cache entries exist in the user sessions cache.");
        } else {
            result.put("fail", String.format("User sessions cache is missing entries that are present in the anonymous user cache. These %d entries are missing: %s", anonymousUserCacheKeysNotInSessionsCache.size(), String.join(", ", anonymousUserCacheKeysNotInSessionsCache)));
        }

        if (sessionCacheItemsNotInUserCaches.isEmpty()) {
            result.put("pass", "All user sessions cache entries exist in either the user cache or the anonymous user cache.");
        } else {
            result.put("fail", String.format("User cache and/or anonymous user cache is missing entries that are present in the user sessions cache. These %d entries are missing: %s", sessionCacheItemsNotInUserCaches.size(), String.join(", ", sessionCacheItemsNotInUserCaches)));
        }

        if (duplicatesBetweenAnonAndNonAnonUsers.isEmpty()) {
            result.put("pass", "There are no duplicates between non-anonymous users cache and anonymous users cache.");
        } else {
            result.put("fail", String.format("There are users both present in non-anonymous users cache and anonymous users cache. These %d entries are duplicates: %s", duplicatesBetweenAnonAndNonAnonUsers.size(), String.join(", ", duplicatesBetweenAnonAndNonAnonUsers)));
        }

        return result;
    }

    public static Multimap<String, String> generateReportForMucRooms(
        @Nonnull final Cache<String, MUCRoom> clusteredRoomCacheInput,
        @Nonnull final Map<String, MUCRoom> localRoomsInput,
        @Nonnull final Map<NodeID, Set<OccupantManager.Occupant>> occupantsByNodeInput,
        @Nonnull final Map<OccupantManager.Occupant, NodeID> nodeByOccupantInput,
        @Nonnull final Set<OccupantManager.Occupant> federatedOccupantsInput,
        @Nonnull final String mucServiceName
    ) {

        // Take snapshots of all data structures at as much the same time as possible.
        final ConcurrentMap<String, MUCRoom> cache = new ConcurrentHashMap<>(clusteredRoomCacheInput);
        final ConcurrentMap<String, MUCRoom> localRoomsCache = new ConcurrentHashMap<>(localRoomsInput);
        final ConcurrentMap<NodeID, Set<OccupantManager.Occupant>> occupantsByNode = new ConcurrentHashMap<>(occupantsByNodeInput);
        final ConcurrentMap<OccupantManager.Occupant, NodeID> nodeByOccupant = new ConcurrentHashMap<>(nodeByOccupantInput);
        final Set<OccupantManager.Occupant> federatedOccupants = new HashSet<>(federatedOccupantsInput);

        final Set<String> roomsOnlyInLocalCache = localRoomsCache.keySet().stream().filter(jid -> !cache.containsKey(jid)).collect(Collectors.toSet());

        final Set<OccupantManager.Occupant> allOccupantsFromOccupantsByNode = occupantsByNode.values().stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
        final Set<OccupantManager.Occupant> allOccupantsFromNodesByOccupant = nodeByOccupant.keySet();

        final Set<OccupantManager.Occupant> occupantsByNodeNotPresentInNodesByOccupant = allOccupantsFromOccupantsByNode.stream()
            .filter(o -> !allOccupantsFromNodesByOccupant.contains(o))
            .collect(Collectors.toSet());
        final Set<OccupantManager.Occupant> occupantsNotPresentInOccupantsByNode = allOccupantsFromNodesByOccupant.stream()
            .filter(o -> !allOccupantsFromOccupantsByNode.contains(o))
            .collect(Collectors.toSet());

        final List<OccupantManager.Occupant> allNonFederatedOccupants = allOccupantsFromOccupantsByNode.stream()
            .sorted(Comparator.comparing(OccupantManager.Occupant::toString))
            .collect(Collectors.toList());
        final List<String> allNonFederatedOccupantsJids = allNonFederatedOccupants
            .stream()
            .map(occupant -> occupant.getRealJID().toFullJID() + " (in room '" + occupant.getRoomName() + "' with nickname '" + occupant.getNickname() + "')")
            .sorted()
            .collect(Collectors.toList());

        final List<OccupantManager.Occupant> allFederatedOccupants = federatedOccupants.stream()
            .sorted(Comparator.comparing(OccupantManager.Occupant::toString))
            .collect(Collectors.toList());
        final List<String> allFederatedOccupantsJids = allFederatedOccupants
            .stream()
            .map(occupant -> occupant.getRealJID().toFullJID() + " (in room '" + occupant.getRoomName() + "' with nickname '" + occupant.getNickname() + "')")
            .sorted()
            .collect(Collectors.toList());

        final List<String> allOccupantJids = new LinkedList<>();
        allOccupantJids.addAll(allFederatedOccupantsJids);
        allOccupantJids.addAll(allNonFederatedOccupantsJids);

        final List<MUCRole> allMucRoles = cache.values().stream()
            .flatMap(room -> room.getOccupants().stream())
            .sorted(Comparator.comparing(MUCRole::toString))
            .collect(Collectors.toList());
        final List<String> allMucRolesOccupantsJids = allMucRoles
            .stream()
            .map(mucRole -> mucRole.getUserAddress().toFullJID() + " (in room '" + mucRole.getRoleAddress().getNode() + "' with nickname '" + mucRole.getNickname() + "')")
            .sorted()
            .collect(Collectors.toList());

        // Generate report
        final Multimap<String, String> result = HashMultimap.create();

        result.put("intro", String.format("This section concerns the '%s' muc service.", mucServiceName));
        result.put("info", String.format("The cache named %s is used to share data in the cluster, which contains %d muc rooms.", clusteredRoomCacheInput.getName(), cache.size()));

        result.put("data", String.format("%s contains these entries (these are shared in the cluster):\n%s", clusteredRoomCacheInput.getName(), cache.keySet()
            .stream()
            .sorted()
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("Local rooms cache contains these entries :\n%s", localRoomsCache.keySet()
            .stream()
            .sorted()
            .collect(Collectors.joining("\n"))));
        result.put("data", String.format("All non-federated occupants from occupant registration :\n%s", String.join("\n", allNonFederatedOccupantsJids)));
        result.put("data", String.format("All federated occupants from occupant registration :\n%s", String.join("\n", allFederatedOccupantsJids)));
        result.put("data", String.format("All occupants from rooms in cache :\n%s", String.join("\n", allMucRolesOccupantsJids)));

        if (roomsOnlyInLocalCache.isEmpty()) {
            result.put("pass", "All locally known rooms exist in clustered room cache.");
        } else {
            result.put("fail", String.format("Clustered room cache is missing entries that are present in the local room cache. These %d entries are missing: %s", roomsOnlyInLocalCache.size(), String.join(", ", roomsOnlyInLocalCache)));
        }

        if (occupantsByNodeNotPresentInNodesByOccupant.isEmpty()) {
            result.put("pass", "All occupants registered by node exist in the nodes registered by occupant.");
        } else {
            result.put("fail", String.format("The registration of nodes by occupant is missing entries that are present in the registration of occupants by node. These %d entries are missing: %s", occupantsByNodeNotPresentInNodesByOccupant.size(), occupantsByNodeNotPresentInNodesByOccupant.stream().map(OccupantManager.Occupant::getNickname).collect(Collectors.joining(", "))));
        }

        if (occupantsNotPresentInOccupantsByNode.isEmpty()) {
            result.put("pass", "All occupants in the nodes registered by occupant exist in the occupants registered by node.");
        } else {
            result.put("fail", String.format("The registration of occupants by node is missing entries that are present in the registration of nodes by occupant. These %d entries are missing: %s", occupantsNotPresentInOccupantsByNode.size(), occupantsNotPresentInOccupantsByNode.stream().map(OccupantManager.Occupant::getNickname).collect(Collectors.joining(", "))));
        }

        if (new HashSet<>(allOccupantJids).equals(new HashSet<>(allMucRolesOccupantsJids))) {
            result.put("pass", "The list of occupants registered by node equals the list of occupants seen in rooms.");
        } else {
            result.put("fail", String.format("The sum of the collection of non-federated (%d) and federated (%d) occupants is %d, which does not equal the list of occupants seen in rooms, which has %d elements", allNonFederatedOccupantsJids.size(), allFederatedOccupantsJids.size(), allOccupantJids.size(), allMucRolesOccupantsJids.size()));
        }

        return result;
    }
}
