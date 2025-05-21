/*
 * Copyright (C) 2021-2025 Ignite Realtime Foundation. All rights reserved.
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

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.SystemProperty;

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
 * This implementation can be used in scenarios where exactly one cluster node 'owns' data (eg: client connection), as
 * well as in scenarios where more than one cluster node 'owns' data (eg: component sessions). Instances can be
 * configured to expect a unique owner (through a boolean argument in the constructor). When operating in that mode, an
 * entry addition or update will cause entries for other nodes to be removed. This can be particularly helpful when
 * events arrive 'out of order' (which could lead to data inconsistencies).
 *
 * This implementation assumes that the cluster node on which the cache entry change originates is an owner of the
 * corresponding entry.
 */
public class ReverseLookupUpdatingCacheEntryListener<K, V> implements ClusteredCacheEntryListener<K, V>
{
    private final static SystemProperty<Boolean> DISABLE_OPTIMIZATION = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("cache.reverselookup.optimization.disabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    private final Interner<K> mutex = Interners.newStrongInterner();

    private final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation;
    private final boolean uniqueOwnerExpected;

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation) {
        this(reverseCacheRepresentation, false);
    }

    public ReverseLookupUpdatingCacheEntryListener(@Nonnull final ConcurrentMap<NodeID, Set<K>> reverseCacheRepresentation, final boolean uniqueOwnerExpected) {
        this.reverseCacheRepresentation = reverseCacheRepresentation;
        this.uniqueOwnerExpected = uniqueOwnerExpected;
    }

    @Override
    public void entryAdded(@Nonnull final K key, @Nullable final V value, @Nonnull final NodeID nodeID) {
        synchronized (mutex.intern(key)) {
            reverseCacheRepresentation.computeIfAbsent(nodeID, k -> new HashSet<>()).add(key);

            if (uniqueOwnerExpected && !DISABLE_OPTIMIZATION.getValue()) {
                // As the entry is now owned by the nodeID processed above, it can no longer be owned by any of the others.
                reverseCacheRepresentation.forEach((k, v) -> {
                    if (!k.equals(nodeID)) {
                        v.remove(key);
                    }
                });
            }
        }
    }

    @Override
    public void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        synchronized (mutex.intern(key)) {
            reverseCacheRepresentation.computeIfPresent(nodeID, (k, v) -> {
                v.remove(key);
                return v;
            });
        }
    }

    @Override
    public void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID) {
        if (DISABLE_OPTIMIZATION.getValue()) {
            return;
        }
        synchronized (mutex.intern(key)) {
            // Possibly out-of-order event. The update itself signals that it's expected that the entry exists, so let's add it.
            entryAdded(key, newValue, nodeID);
        }
    }

    @Override
    public void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID) {
        synchronized (mutex.intern(key)) {
            entryRemoved(key, oldValue, nodeID);
        }
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
