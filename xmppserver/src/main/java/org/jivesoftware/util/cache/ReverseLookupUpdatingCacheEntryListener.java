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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache entry listener implementation that maintains a reverse lookup map for the cache that is being observed, which
 * is used to identify what cluster node is the logical owner of a particular cache entry. This information is typically
 * useful in scenarios where a cluster node drops out of the cluster requiring the remaining nodes to have to inform
 * their connected entities of what resources have become unavailable.
 *
 * This implementation assumes that the cluster node on which the cache entry change originates is the owner of the
 * corresponding entry (cache entry updates are ignored).
 */
public class ReverseLookupUpdatingCacheEntryListener<K, V> implements ClusteredCacheEntryListener<K, V> {
    private final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation;

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
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
        // Possibly out-of-order event. The update itself signals that it's expected that the entry exists, so lets add it.
        entryAdded(key, newValue, nodeID);
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
