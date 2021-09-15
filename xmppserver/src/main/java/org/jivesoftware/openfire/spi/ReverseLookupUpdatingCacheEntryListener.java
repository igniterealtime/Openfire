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
package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Cache entry listener implementation that maintains a reverse lookup map for the cache that is being observed.
 */
public class ReverseLookupUpdatingCacheEntryListener<K, V> implements ClusteredCacheEntryListener<K, V> {
    private final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation;

    private final Function<V, Set<NodeID>> nodeIDsFromValueDeducer;

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
        this.nodeIDsFromValueDeducer = null;
    }

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation, @Nonnull final Function<V, Set<NodeID>> nodeIDsFromValueDeducer) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
        this.nodeIDsFromValueDeducer = nodeIDsFromValueDeducer;
    }

    @Override
    public void entryAdded(@Nonnull final K key, @Nullable final V value, @Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.computeIfAbsent(nodeID, k -> new HashSet<>()).add(key);
    }

    @Override
    public void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.computeIfPresent(nodeID, (k, v) -> {
            v.remove(key);
            return v;
        });
    }

    @Override
    public void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID) {
        // Although we only care about keys, we do need to process this for caches in which values are not uniquely 'owned'
        // by a particular node, such as the #componentsCache. Such utility must define a function to identify the current
        // nodes for which the entry is valid.
        if (nodeIDsFromValueDeducer != null) {
            // FIXME this implementation is untested, and likely incorrect. Also, there's an argument to be made that the
            //       implementation of the other methods should need to use this Function.
            final Set<NodeID> newNodes = nodeIDsFromValueDeducer.apply(newValue);
            if (newNodes.contains(nodeID)) {
                reverseCacheRepresentation.computeIfAbsent(nodeID, k -> new HashSet<>()).add(key);
            } else {
                reverseCacheRepresentation.computeIfAbsent(nodeID, k -> new HashSet<>()).remove(key);
            }
        }
    }

    @Override
    public void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        entryRemoved(key, oldValue, nodeID);
    }

    @Override
    public void mapCleared(@Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.remove(nodeID);
    }

    @Override
    public void mapEvicted(@Nonnull final NodeID nodeID) {
        reverseCacheRepresentation.remove(nodeID);
    }
}
