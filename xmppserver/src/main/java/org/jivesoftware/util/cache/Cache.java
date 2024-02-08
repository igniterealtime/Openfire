/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * General purpose cache. It stores objects associated with unique keys in
 * memory for fast access. All keys and values added to the cache must
 * implement the Serializable interface. Values may implement the Cacheable
 * interface, which allows the cache to determine object size much more quickly.
 * These restrictions allow a cache to never grow larger than a specified number
 * of bytes and to optionally be distributed over a cluster of servers.<p>
 *
 * If the cache does grow too large, objects will be removed such that those
 * that are accessed least frequently are removed first. Because expiration
 * happens automatically, the cache makes <b>no</b> guarantee as to how long
 * an object will remain in cache after it is put in.<p>
 *
 * Optionally, a maximum lifetime for all objects can be specified. In that
 * case, objects will be deleted from cache after that amount of time, even
 * if they are frequently accessed. This feature is useful if objects put in
 * cache represent data that should be periodically refreshed; for example,
 * information from a database.<p>
 *
 * All cache operations are thread safe.<p>
 *
 * Note that neither keys nor values can be null; A {@link NullPointerException}
 * will be thrown attempting to place or retrieve null values in to the cache.
 *
 * Caches can (but need not be) used as a mechanism that is used to share data
 * in an Openfire cluster. When a cache is used for this purpose, it is important
 * to realize that, when joining or leaving a cluster, the cache content will
 * be cleared of all data that was added on the local cluster node. Typically,
 * {@link org.jivesoftware.openfire.cluster.ClusterEventListener} is used to
 * detect these events and restore the content of the cache.
 *
 * @see Cacheable
 */
public interface Cache<K extends Serializable, V extends Serializable> extends java.util.Map<K, V> {

    /**
     * Defines the unit used to calculate the capacity of the cache.
     */
    enum CapacityUnit
    {
        /**
         * The capacity is measured in bytes (an aggregation of the byte-size of all elements).
         */
        BYTES,

        /**
         * The capacity is measured in number of entities (a natural count of the amount of elements).
         */
        ENTITIES,
    }

    /**
     * Defines the unit used to calculate the capacity of the cache.<p>
     *
     * When the unit is unknown, null is returned.
     *
     * @return the unit to be used to calculate the capacity of this cache.
     */
    default CapacityUnit getCapacityUnit() {
        return null;
    }

    /**
     * Returns the name of the cache.
     *
     * @return the name of the cache.
     */
    String getName();

    /**
     * Sets the name of the cache
     *
     * @param name the name of the cache
     */
    void setName(String name);

    /**
     * Returns the maximum size of the cache.<p>
     *
     * The value should be placed in context of the unit returned by {@link #getCapacityUnit()}. When, for example, the
     * capacity unit for this cache is {@link CapacityUnit#BYTES}, then a return value of 2048 should be interpreted as
     * this cache having a capacity of 2048 bytes (or 2KB). When the capacity unit is {@link CapacityUnit#ENTITIES} then
     * this cache can contain 2048 elements (irrespective of their byte size).<p>
     *
     * If the cache grows larger than the maximum size, the least frequently used items will be removed.<p>
     *
     * If the maximum cache size is set to -1, there is no size limit.
     *
     * @return the maximum size of the cache.
     */
    long getMaxCacheSize();

    /**
     * Sets the maximum size of the cache.<p>
     *
     * The value should be placed in context of the unit returned by {@link #getCapacityUnit()}. When, for example, the
     * capacity unit for this cache is {@link CapacityUnit#BYTES}, then a return value of 2048 should be interpreted as
     * this cache having a capacity of 2048 bytes (or 2KB). When the capacity unit is {@link CapacityUnit#ENTITIES} then
     * this cache can contain 2048 elements (irrespective of their byte size).<p>
     *
     * If the cache grows larger than the maximum size, the least frequently used items will be removed. If the maximum
     * cache size is set to -1, there is no size limit.<p>
     *
     * <strong>Note:</strong> If using the Hazelcast clustering plugin, this will only take effect if the cache is
     * dynamically configured (not defined in the hazelcast-cache-config.xml file), and will not take effect until the
     * next time the cache is created.
     *
     * @param maxSize the maximum size of the cache.
     */
    void setMaxCacheSize(long maxSize);

    /**
     * An optional, human-readable remark on the maximum cache capacity configuration.
     *
     * @return an optional human-readable text
     */
    default String getMaxCacheSizeRemark() {
        return null;
    }

    /**
     * Returns the maximum number of milliseconds that any object can live
     * in cache. Once the specified number of milliseconds passes, the object
     * will be automatically expired from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * @return the maximum number of milliseconds before objects are expired.
     */
    long getMaxLifetime();

    /**
     * Sets the maximum number of milliseconds that any object can live
     * in cache. Once the specified number of milliseconds passes, the object
     * will be automatically expired from cache. If the max lifetime is set
     * to -1, then objects never expire.<p>
     *
     * <strong>Note:</strong> If using the Hazelcast clustering plugin, this will only take effect if the cache is
     * dynamically configured (not defined in the hazelcast-cache-config.xml file), and will not take effect until the
     * next time the cache is created.<p>
     *
     * @param maxLifetime the maximum number of milliseconds before objects are expired.
     */
    void setMaxLifetime(long maxLifetime);

    /**
     * Returns the size of the cache.<p>
     *
     * The value should be placed in context of the unit returned by {@link #getCapacityUnit()}. When, for example, the
     * capacity unit for this cache is {@link CapacityUnit#BYTES}, then a return value of 2048 should be interpreted as
     * all entities of this cache having a combined size of 2024 bytes (or 2KB). When the capacity unit is
     * {@link CapacityUnit#ENTITIES} then this cache currently contains 2048 elements.<p>
     *
     * The returned value can be an approximation.
     *
     * @return the size of the cache contents.
     */
    long getLongCacheSize();

    /**
     * An optional, human-readable remark on the current size of the cache.
     *
     * @return an optional human-readable text
     */
    default String getCacheSizeRemark() {
        return null;
    }

    /**
     * Returns the number of cache hits. A cache hit occurs every
     * time the get method is called and the cache contains the requested
     * object.<p>
     *
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    long getCacheHits();

    /**
     * Returns the number of cache misses. A cache miss occurs every
     * time the get method is called and the cache does not contain the
     * requested object.<p>
     *
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    long getCacheMisses();

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#values()} implementation, the collection returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable collection view of the values contained in this map
     */
    Collection<V> values();

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#entrySet()} implementation, the set returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable set view of the mappings contained in this map
     */
    @Override
    Set<Entry<K, V>> entrySet();

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#keySet()} implementation, the set returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable set view of the keys contained in this map
     */
    @Override
    Set<K> keySet();

    /**
     * Returns an existing {@link Lock} on the specified key or creates a new one
     * if none was found. This operation is thread safe. Successive calls with the same key may or may not
     * return the same {@link Lock}. However, different threads asking for the
     * same Lock at the same time will get the same Lock object.
     *
     * <p>The supplied cache may or may not be used depending whether the server is running on cluster mode
     * or not. When not running as part of a cluster then the lock will be unrelated to the cache and will
     * only be visible in this JVM.
     *
     * @param key the object that defines the visibility or scope of the lock.
     * @return an existing lock on the specified key or creates a new one if none was found.
     */
    @SuppressWarnings("deprecation")
    default Lock getLock(final K key) {
        return CacheFactory.getLock(key, this);
    }

    AtomicBoolean secretKey = new AtomicBoolean(false);
    AtomicBoolean secretValue = new AtomicBoolean(false);
    default void setSecretKey() {
        this.secretKey.set(true);
    }

    default void setSecretValue() {
        this.secretValue.set(true);
    }

    default boolean isKeySecret() {
        return this.secretKey.get();
    }

    default boolean isValueSecret() {
        return this.secretValue.get();
    }

    /**
     * Registers a listener to receive entry events for this cache.
     *
     * To optimize the amount of data that is being processed, this method allows the listener to be registered in a way
     * that suppresses values from being sent to it. In that case, only keys will be included in the event listener
     * invocation.
     *
     * @param listener the listener to be registered.
     * @param includeValues defines if the listener should receive the values associated with the events.
     * @param includeEventsFromLocalNode defines if the listener should be invoked for events that originate on the local node.
     * @return a unique identifier for the listener which is used as a key to remove the listener
     */
    String addClusteredCacheEntryListener(@Nonnull final ClusteredCacheEntryListener<K, V> listener, final boolean includeValues, final boolean includeEventsFromLocalNode);

    /**
     * Removes a previously registered event listener.
     *
     * @param listenerId the identifier of the listener to be removed.
     */
    void removeClusteredCacheEntryListener(@Nonnull final String listenerId);
}
