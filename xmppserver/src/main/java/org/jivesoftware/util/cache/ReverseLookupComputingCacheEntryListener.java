/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cache entry listener implementation that maintains a reverse lookup map for the cache that is being observed, which
 * is used to identify what cluster nodes are the logical owner of a particular cache entry. This information is typically
 * useful in scenarios where a cluster node drops out of the cluster requiring the remaining nodes to have to inform
 * their connected entities of what resources have become unavailable.
 *
 * This implementation uses a function (to be provided in the constructor) to calculate what cluster nodes 'own' a
 * corresponding entry. The function operates on the value of the cache. It is therefor required that listeners of this
 * type are registered using a configuration that includes entry values to be included in the event invocation. Null
 * values are not supported.
 */
public class ReverseLookupComputingCacheEntryListener<K, V> implements ClusteredCacheEntryListener<K, V>
{
    private final Map<NodeID, Set<K>> reverseCacheRepresentation;
    private final Function<V, Set<NodeID>> ownageDeducer;

    public ReverseLookupComputingCacheEntryListener(@Nonnull final Map<NodeID, Set<K>> reverseCacheRepresentation, @Nonnull final Function<V, Set<NodeID>> ownageDeducer) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
        this.ownageDeducer = ownageDeducer;
    }

    @Override
    public void entryAdded(@Nonnull final K key, @Nullable final V value, @Nonnull final NodeID initiator)
    {
        final Set<NodeID> newNodesWithThisKey = ownageDeducer.apply(value);
        add(key, newNodesWithThisKey);
    }

    private void add(K key, Set<NodeID> newNodesWithThisKey)
    {
        // Step 1 : update existing entries to the updated situation
        final Iterator<Map.Entry<NodeID, Set<K>>> iter = reverseCacheRepresentation.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<NodeID, Set<K>> existingEntry = iter.next();
            final NodeID existingEntryNodeID = existingEntry.getKey();
            if (newNodesWithThisKey.contains(existingEntryNodeID)) {
                existingEntry.getValue().add(key);
            }
        }
        // Step 2 : add entries for node ids that were not in the reverse lookup before
        for (final NodeID nodeIdForWhichTheValueExists : newNodesWithThisKey) {
            if (!reverseCacheRepresentation.containsKey(nodeIdForWhichTheValueExists)) {
                reverseCacheRepresentation.computeIfAbsent(nodeIdForWhichTheValueExists, k -> new HashSet<>()).add(key);
            }
        }
    }

    @Override
    public void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID initiator) {
        final Iterator<Map.Entry<NodeID, Set<K>>> iter = reverseCacheRepresentation.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<NodeID, Set<K>> existingEntry = iter.next();
            existingEntry.getValue().remove(key);
            if (existingEntry.getValue().isEmpty()) {
                iter.remove();
            }
        }
    }

    @Override
    public void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID) {
        final Set<NodeID> nodesToAdd = ownageDeducer.apply(newValue);

        // Remove all entries for the key.
        final Iterator<Map.Entry<NodeID, Set<K>>> iter = reverseCacheRepresentation.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<NodeID, Set<K>> existingEntry = iter.next();
            final NodeID existingEntryNodeID = existingEntry.getKey();
            if (!nodesToAdd.contains(existingEntryNodeID)) {
                existingEntry.getValue().remove(key);
                if (existingEntry.getValue().isEmpty()) {
                    iter.remove();
                }
            }
        }

        // Add entries for only the latest state of the value.
        add(key, nodesToAdd);
    }

    @Override
    public void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        entryRemoved(key, oldValue, nodeID);
    }

    @Override
    public void mapCleared(@Nonnull final NodeID nodeID) {
        // This is not necessarily correct, as the node owners aren't recalculated using the function. It probably is
        // sufficient though to work with cluster nodes dropping out of the cluster.
        reverseCacheRepresentation.remove(nodeID);
    }

    @Override
    public void mapEvicted(@Nonnull final NodeID nodeID) {
        mapCleared(nodeID);
    }
}
