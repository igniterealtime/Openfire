/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
 * Note that neither keys or values can be null; A {@link NullPointerException}
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
     * Returns the maximum size of the cache in bytes. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * @return the maximum size of the cache in bytes.
     */
    long getMaxCacheSize();

    /**
     * Sets the maximum size of the cache in bytes limited to integer size. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     *<p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * @param maxSize the maximum size of the cache in bytes.
     */
    @Deprecated
    void setMaxCacheSize(int maxSize);

    /**
     * Sets the maximum size of the cache in bytes. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     *<p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * Attention: The default implementation of this method sets the cache value limited to integer size.
     *
     * @param maxSize the maximum size of the cache in bytes.
     */
    default void setMaxCacheSize(long maxSize){
        setMaxCacheSize((int)Math.min(Integer.MAX_VALUE,maxSize));
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
     * to -1, then objects never expire.
     *
     *<p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * @param maxLifetime the maximum number of milliseconds before objects are expired.
     */
    void setMaxLifetime(long maxLifetime);

    /**
     * Returns the size of the cache contents in bytes limited to integer size. This value is only a
     * rough approximation, so cache users should expect that actual VM
     * memory used by the cache could be significantly higher than the value
     * reported by this method.
     *
     * @return the size of the cache contents in bytes.
     */
    @Deprecated
    int getCacheSize();

    /**
     * Returns the size of the cache contents in bytes. This value is only a
     * rough approximation, so cache users should expect that actual VM
     * memory used by the cache could be significantly higher than the value
     * reported by this method.
     *
     * Attention: The default implementation of this method returns the cache value limited to integer size.
     *
     * @return the size of the cache contents in bytes.
     */
    default long getLongCacheSize(){
        return getCacheSize();
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
}
