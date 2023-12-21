/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.cluster;

import org.jivesoftware.util.cache.Cache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An event listener for changes made to entries of a clustered cache.
 *
 * Generally speaking, event listener invocation will always include the key value. For performance optimizations,
 * the value can be omitted. The behavior is configured when registering the listener with a cache.
 *
 * An instance can be registered with a cache using {@link Cache#addClusteredCacheEntryListener(ClusteredCacheEntryListener, boolean, boolean)}
 *
 * Listeners are invoked in an asynchronous fashion. It is possible that invocations occur in a different order than in
 * which the cache was modified.
 *
 * @param <K> Type of the key of the cache
 * @param <V> Type of the value of the cache
 */
public interface ClusteredCacheEntryListener<K, V>
{
    /**
     * An entry was added to the cache.
     *
     * @param key The key of the entry that was added.
     * @param newValue The (optional) value of the entry that was added.
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void entryAdded(@Nonnull final K key, @Nullable final V newValue, @Nonnull final NodeID nodeID);

    /**
     * An entry was removed from the cache.
     *
     * @param key The key of the entry that was removed.
     * @param oldValue The (optional) value of the entry that was removed.
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void entryRemoved(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID);

    /**
     * An entry was updated in the cache.
     *
     * @param key The key of the entry that was changed.
     * @param oldValue The (optional) value of the entry prior to the update.
     * @param newValue The (optional) value of the entry after to the update.
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void entryUpdated(@Nonnull final K key, @Nullable final V oldValue, @Nullable final V newValue, @Nonnull final NodeID nodeID);

    /**
     * An entry was evicted from the cache.
     *
     * @param key The key of the entry that was evicted.
     * @param oldValue The (optional) value of the entry that was removed.
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void entryEvicted(@Nonnull final K key, @Nullable final V oldValue, @Nonnull final NodeID nodeID);

    /**
     * The cache was cleared.
     *
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void mapCleared(@Nonnull final NodeID nodeID);

    /**
     * The cache was evicted.
     *
     * @param nodeID identifier of the node on which the cache modification occurred.
     */
    void mapEvicted(@Nonnull final NodeID nodeID);
}
