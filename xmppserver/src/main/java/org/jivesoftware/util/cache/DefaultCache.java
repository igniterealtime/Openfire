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
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LinkedListNode;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default, non-distributed implementation of the Cache interface.
 * The algorithm for cache is as follows: a HashMap is maintained for fast
 * object lookup. Two linked lists are maintained: one keeps objects in the
 * order they are accessed from cache, the other keeps objects in the order
 * they were originally added to cache. When objects are added to cache, they
 * are first wrapped by a CacheObject which maintains the following pieces
 * of information:<ul>
 *
 * <li> The size of the object (in bytes).
 * <li> A pointer to the node in the linked list that maintains accessed
 * order for the object. Keeping a reference to the node lets us avoid
 * linear scans of the linked list.
 * <li> A pointer to the node in the linked list that maintains the age
 * of the object in cache. Keeping a reference to the node lets us avoid
 * linear scans of the linked list.</ul><p>
 *
 * To get an object from cache, a hash lookup is performed to get a reference
 * to the CacheObject that wraps the real object we are looking for.
 * The object is subsequently moved to the front of the accessed linked list
 * and any necessary cache cleanups are performed. Cache deletion and expiration
 * is performed as needed.
 *
 * @author Matt Tucker
 */
public class DefaultCache<K extends Serializable, V extends Serializable> implements Cache<K, V> {

    private static final String NULL_KEY_IS_NOT_ALLOWED = "Null key is not allowed!";
    private static final String NULL_VALUE_IS_NOT_ALLOWED = "Null value is not allowed!";
    private static final boolean allowNull = JiveGlobals.getBooleanProperty("cache.allow.null", false);
    private static final long MAX_CULL_COUNT_PERIOD = Duration.ofHours(12).toMillis();

    private static final Logger Log = LoggerFactory.getLogger(DefaultCache.class);
    // Contains the set of times when the Cache was last culled
    private final Set<Long> cullTimes;


    /**
     * The map the keys and values are stored in.
     */
    protected Map<K, DefaultCache.CacheObject<V>> map;

    /**
     * Linked list to maintain order that cache objects are accessed
     * in, most used to least used.
     */
    private org.jivesoftware.util.LinkedList<K> lastAccessedList;

    /**
     * Linked list to maintain time that cache objects were initially added
     * to the cache, most recently added to oldest added.
     */
    private org.jivesoftware.util.LinkedList<K> ageList;

    /**
     * Maximum size in bytes that the cache can grow to.
     */
    private long maxCacheSize;

    /**
     * Maintains the current size of the cache in bytes.
     */
    private long cacheSize = 0;

    /**
     * Maximum length of time objects can exist in cache before expiring.
     */
    private long maxLifetime;

    /**
     * Maintain the number of cache hits and misses. A cache hit occurs every
     * time the get method is called and the cache contains the requested
     * object. A cache miss represents the opposite occurrence.<p>
     *
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     */
    private long cacheHits, cacheMisses = 0L;

    /**
     * The name of the cache.
     */
    private String name;

    /**
     * Create a new default cache and specify the maximum size of for the cache in
     * bytes, and the maximum lifetime of objects.
     *
     * @param name a name for the cache.
     * @param maxSize the maximum size of the cache in bytes. -1 means the cache
     *      has no max size.
     * @param maxLifetime the maximum amount of time objects can exist in
     *      cache before being deleted. -1 means objects never expire.
     */
    DefaultCache(final String name, final long maxSize, final long maxLifetime) {
        this.name = name;
        this.maxCacheSize = maxSize;
        this.maxLifetime = maxLifetime;

        // Our primary data structure is a HashMap. The default capacity of 11
        // is too small in almost all cases, so we set it bigger.
        map = new HashMap<>(103);

        lastAccessedList = new org.jivesoftware.util.LinkedList<>();
        ageList = new org.jivesoftware.util.LinkedList<>();
        cullTimes = ConcurrentHashMap.newKeySet();
    }

    @Override
    public synchronized V put(final K key, final V value) {
        checkNotNull(key, NULL_KEY_IS_NOT_ALLOWED);
        checkNotNull(value, NULL_VALUE_IS_NOT_ALLOWED);
        // Delete an old entry if it exists.
        final V answer = remove(key);

        int objectSize = 1;
        try {
             objectSize = CacheSizes.sizeOfAnything(value);
        }
        catch (final CannotCalculateSizeException e) {
             Log.warn(e.getMessage(), e);
        }

        // If the object is bigger than the entire cache, simply don't add it.
        if (maxCacheSize > 0 && objectSize > maxCacheSize * .90) {
            Log.warn("Cache: " + name + " -- object with key " + key +
                    " is too large to fit in cache. Size is " + objectSize);
            return value;
        }
        cacheSize += objectSize;
        final DefaultCache.CacheObject<V> cacheObject = new DefaultCache.CacheObject<>(value, objectSize);
        map.put(key, cacheObject);
        // Make an entry into the cache order list.
        // Store the cache order list entry so that we can get back to it
        // during later lookups.
        cacheObject.lastAccessedListNode = lastAccessedList.addFirst(key);
        // Add the object to the age list
        final LinkedListNode<K> ageNode = ageList.addFirst(key);
        // We make an explicit call to currentTimeMillis() so that total accuracy
        // of lifetime calculations is better than one second.
        ageNode.timestamp = System.currentTimeMillis();
        cacheObject.ageListNode = ageNode;

        // If cache is too full, remove least used cache entries until it is
        // not too full.
        cullCache();

        return answer;
    }

    @Override
    public synchronized V get(final Object key) {
        checkNotNull(key, NULL_KEY_IS_NOT_ALLOWED);
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        final DefaultCache.CacheObject<V> cacheObject = map.get(key);
        if (cacheObject == null) {
            // The object didn't exist in cache, so increment cache misses.
            cacheMisses++;
            return null;
        }

        // The object exists in cache, so increment cache hits. Also, increment
        // the object's read count.
        cacheHits++;
        cacheObject.readCount++;

        // Remove the object from it's current place in the cache order list,
        // and re-insert it at the front of the list.
        cacheObject.lastAccessedListNode.remove();
        //noinspection unchecked
        lastAccessedList.addFirst((LinkedListNode<K>) cacheObject.lastAccessedListNode);

        return cacheObject.object;
    }

    @Override
    public synchronized V remove(final Object key) {
        checkNotNull(key, NULL_KEY_IS_NOT_ALLOWED);
        @SuppressWarnings("SuspiciousMethodCalls")
        final DefaultCache.CacheObject<V> cacheObject = map.get(key);
        // If the object is not in cache, stop trying to remove it.
        if (cacheObject == null) {
            return null;
        }
        // remove from the hash map
        map.remove(key);
        // remove from the cache order list
        cacheObject.lastAccessedListNode.remove();
        cacheObject.ageListNode.remove();
        // remove references to linked list nodes
        cacheObject.ageListNode = null;
        cacheObject.lastAccessedListNode = null;
        // removed the object, so subtract its size from the total.
        cacheSize -= cacheObject.size;
        return cacheObject.object;
    }

    @Override
    public synchronized void clear() {
        final Object[] keys = map.keySet().toArray();
        for (final Object key : keys) {
            remove(key);
        }

        // Now, reset all containers.
        map.clear();
        lastAccessedList.clear();
        lastAccessedList = new org.jivesoftware.util.LinkedList<>();
        ageList.clear();
        ageList = new org.jivesoftware.util.LinkedList<>();

        cacheSize = 0;
        cacheHits = 0;
        cacheMisses = 0;
    }

    @Override
    public int size() {
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        return map.size();
    }

    @Override
    public boolean isEmpty() {
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        return map.isEmpty();
    }

    @Override
    public Collection<V> values() {
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        synchronized (this) {
            return map.values().stream()
                .map(cachedObject -> cachedObject.object)
                .collect(Collectors.toList());
        }
    }

    @Override
    public boolean containsKey(final Object key) {
        checkNotNull(key, NULL_KEY_IS_NOT_ALLOWED);
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        return map.containsKey(key);
    }

    @Override
    public void putAll(final Map<? extends K, ? extends V> map) {
        for (final K key : map.keySet()) {
            final V value = map.get(key);
            put(key, value);
        }
    }

    @Override
    public boolean containsValue(final Object value) {
        checkNotNull(value, NULL_VALUE_IS_NOT_ALLOWED);
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        for (final V v : values()) {
            if (value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();

        synchronized (this) {
            return map.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().object))
                .entrySet();
        }
    }

    @Override
    public Set<K> keySet() {
        // First, clear all entries that have been in cache longer than the
        // maximum defined age.
        deleteExpiredEntries();
        synchronized (this) {
            return new HashSet<>(map.keySet());
        }
    }

    /**
     * Returns the name of this cache. The name is completely arbitrary
     * and used only for display to administrators.
     *
     * @return the name of this cache.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this cache.
     *
     * @param name the name of this cache.
     */
    @Override
    public void setName(final String name) {
        this.name = name;
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
    @Override
    public long getCacheHits() {
        return cacheHits;
    }

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
    @Override
    public long getCacheMisses() {
        return cacheMisses;
    }

    /**
     * Returns the size of the cache contents in bytes limited to integer size. This value is only a
     * rough approximation, so cache users should expect that actual VM
     * memory used by the cache could be significantly higher than the value
     * reported by this method.
     *
     * @return the size of the cache contents in bytes.
     */
    @Override
    public int getCacheSize() {
        return (int) Math.min(Integer.MAX_VALUE, cacheSize);
    }

    /**
     * Returns the size of the cache contents in bytes. This value is only a
     * rough approximation, so cache users should expect that actual VM
     * memory used by the cache could be significantly higher than the value
     * reported by this method.
     *
     * @return the size of the cache contents in bytes.
     */
    public long getLongCacheSize(){
        return cacheSize;
    }

    /**
     * Returns the maximum size of the cache (in bytes). If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * @return the maximum size of the cache (-1 indicates unlimited max size).
     */
    @Override
    public long getMaxCacheSize() {
        return maxCacheSize;
    }

    /**
     * Sets the maximum size of the cache in bytes limited to integer size. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * @param maxCacheSize the maximum size of this cache (-1 indicates unlimited max size).
     */
    @Override
    public void setMaxCacheSize(final int maxCacheSize) {
       setMaxCacheSize((long)maxCacheSize);
    }

    /**
     * Sets the maximum size of the cache in bytes. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     *<p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * @param maxSize the maximum size of the cache in bytes.
     */
    public void setMaxCacheSize(long maxSize){
        this.maxCacheSize = maxSize;
        CacheFactory.setMaxSizeProperty(name, maxSize);
        // It's possible that the new max size is smaller than our current cache
        // size. If so, we need to delete infrequently used items.
        cullCache();
    }

    /**
     * Returns the maximum number of milliseconds that any object can live
     * in cache. Once the specified number of milliseconds passes, the object
     * will be automatically expired from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * @return the maximum number of milliseconds before objects are expired.
     */
    @Override
    public long getMaxLifetime() {
        return maxLifetime;
    }

    /**
     * Sets the maximum number of milliseconds that any object can live
     * in cache. Once the specified number of milliseconds passes, the object
     * will be automatically expired from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * @param maxLifetime the maximum number of milliseconds before objects are expired.
     */
    @Override
    public void setMaxLifetime(final long maxLifetime) {
        this.maxLifetime = maxLifetime;
        CacheFactory.setMaxLifetimeProperty(name, maxLifetime);
    }

    /**
     * Clears all entries out of cache where the entries are older than the
     * maximum defined age.
     */
    private void deleteExpiredEntries() {
        // Check if expiration is turned on.
        if (maxLifetime <= 0) {
            return;
        }

        // Remove all old entries. To do this, we remove objects from the end
        // of the linked list until they are no longer too old. We get to avoid
        // any hash lookups or looking at any more objects than is strictly
        // necessary.
        LinkedListNode<K> node = ageList.getLast();
        // If there are no entries in the age list, return.
        if (node == null) {
            return;
        }

        // Determine the expireTime, which is the moment in time that elements
        // should expire from cache. Then, we can do an easy to check to see
        // if the expire time is greater than the expire time.
        final long expireTime = System.currentTimeMillis() - maxLifetime;

        while (expireTime > node.timestamp) {
            // Remove the object
            remove(node.object);

            // Get the next node.
            node = ageList.getLast();
            // If there are no more entries in the age list, return.
            if (node == null) {
                return;
            }
        }
    }

    /**
     * Removes objects from cache if the cache is too full. "Too full" is
     * defined as within 3% of the maximum cache size. Whenever the cache is
     * is too big, the least frequently used elements are deleted until the
     * cache is at least 10% empty.
     */
    private void cullCache() {
        // Check if a max cache size is defined.
        if (maxCacheSize < 0) {
            return;
        }

        // See if the cache size is within 3% of being too big. If so, clean out
        // cache until it's 10% free.
        long desiredSize = (long)(maxCacheSize * .97);
        if (cacheSize >= desiredSize) {
            // First, delete any old entries to see how much memory that frees.
            deleteExpiredEntries();
            desiredSize = (long) (maxCacheSize * .90);
            if (cacheSize > desiredSize) {
                long t = System.currentTimeMillis();
                cullTimes.add(t);
                do {
                    // Get the key and invoke the remove method on it.
                    remove(lastAccessedList.getLast().object);
                } while (cacheSize > desiredSize);
                t = System.currentTimeMillis() - t;
                Log.warn("Cache " + name + " was full, shrunk to 90% in " + t + "ms.");
            }
        }

        cullCacheTimes();
    }

    private void cullCacheTimes() {
        final long oldestCullToKeep = System.currentTimeMillis() - MAX_CULL_COUNT_PERIOD;
        final Set<Long> oldCulls = cullTimes.stream()
            .filter(cullTime -> cullTime < oldestCullToKeep)
            .collect(Collectors.toSet());
        cullTimes.removeAll(oldCulls);
    }

    public long getCacheCulls(final Duration duration) {
        final long millis = duration.toMillis();
        if(millis > MAX_CULL_COUNT_PERIOD) {
            throw new IllegalArgumentException("Request duration exceed maximum of " + StringUtils.getFullElapsedTime(duration));
        }
        cullCacheTimes();
        final long oldestCullToCount = System.currentTimeMillis() - millis;
        return cullTimes.stream()
            .filter(cullTime -> cullTime >= oldestCullToCount)
            .count();
    }

    /**
     * Wrapper for all objects put into cache. It's primary purpose is to maintain
     * references to the linked lists that maintain the creation time of the object
     * and the ordering of the most used objects.
     */
    private static class CacheObject<V> {

        /**
         * Underlying object wrapped by the CacheObject.
         */
        public V object;

        /**
         * The size of the Cacheable object. The size of the Cacheable
         * object is only computed once when it is added to the cache. This makes
         * the assumption that once objects are added to cache, they are mostly
         * read-only and that their size does not change significantly over time.
         */
        public int size;

        /**
         * A reference to the node in the cache order list. We keep the reference
         * here to avoid linear scans of the list. Every time the object is
         * accessed, the node is removed from its current spot in the list and
         * moved to the front.
         */
        LinkedListNode<?> lastAccessedListNode;

        /**
         * A reference to the node in the age order list. We keep the reference
         * here to avoid linear scans of the list. The reference is used if the
         * object has to be deleted from the list.
         */
        LinkedListNode<?> ageListNode;

        /**
         * A count of the number of times the object has been read from cache.
         */
        int readCount = 0;

        /**
         * Creates a new cache object wrapper. The size of the Cacheable object
         * must be passed in in order to prevent another possibly expensive
         * lookup by querying the object itself for its size.<p>
         *
         * @param object the underlying Object to wrap.
         * @param size   the size of the Cacheable object in bytes.
         */
        CacheObject(final V object, final int size) {
            this.object = object;
            this.size = size;
        }
    }

    private void checkNotNull(final Object argument, final String message) {
        try {
            if (argument == null) {
                throw new NullPointerException(message);
            }
        } catch (final NullPointerException e) {
            if (allowNull) {
                Log.debug("Allowing storage of null within Cache: ", e); // Gives us a trace for debugging.
            } else {
                throw e;
            }
        }
    }
}
