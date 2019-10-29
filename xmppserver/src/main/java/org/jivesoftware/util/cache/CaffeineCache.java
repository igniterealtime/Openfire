/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Wraps an instance of Ben Manes' Caffeine cache in a class that inherits from
 * {@link Cache}. This implementation intends to make Caffeine caches compatible
 * with utility functionality that is based on {@link Cache}.
 *
 * Caffeine is a highly configurable implementation. To be able to achieve
 * compatibility with {@link Cache}, a specific configuration is required:
 * <ul>
 *     <li>The cache must have an eviction policy, which must be based on 'weight'.
 *     Caffeine does not define a unit for weight. This class assumes that the
 *     weight is based on byte size of the cache entry.</li>
 *     <li>The cache must be configured to expire entries after write.</li>
 * </ul>
 * @param <K> Cache key type.
 * @param <V> Cache value type.
 * @see <a href="https://github.com/ben-manes/caffeine">Caffeine</a>
 */
public class CaffeineCache<K extends Serializable, V extends Serializable> implements Cache<K,V>
{
    private final com.github.benmanes.caffeine.cache.Cache<K,V> cache;
    private String name;

    /**
     * Wraps a Caffeine cache in an implementation that's Compatible with {@link Cache}.
     *
     * @param cache The cache to be wrapped (cannot be null).
     * @param name A human-readable name for the cache that's wrapped.
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @return A wrapped cache (never null).
     * @throws IllegalArgumentException when the cache configuration does not conform to the contract defined in the javadoc of this class.
     */
    public static <K extends Serializable, V extends Serializable> CaffeineCache of( com.github.benmanes.caffeine.cache.Cache<K,V> cache, String name )
    {
        return new CaffeineCache<>( cache, name );
    }

    /**
     * Instantiates a new wrapper for a Caffeine cache.
     *
     * @param cache The cache to be wrapped (cannot be null).
     * @param name A human-readable name for the cache that's wrapped.
     * @throws IllegalArgumentException when the cache configuration does not conform to the contract defined in the javadoc of this class.
     */
    private CaffeineCache( com.github.benmanes.caffeine.cache.Cache<K,V> cache, String name )
    {
        // Asserts that the configuration of the class is compatible with Jive's Cache interface contract.
        if (!cache.policy().eviction().isPresent() )
        {
            throw new IllegalArgumentException( "This class can only be used with cache implementations that have an eviction policy (that is weight-based)." );
        }

        if (!cache.policy().eviction().get().weightedSize().isPresent())
        {
            throw new IllegalArgumentException( "This class can only be used with cache implementations that have an eviction policy that is weight-based." );
        }

        if (!cache.policy().expireAfterWrite().isPresent())
        {
            throw new IllegalArgumentException( "This class can only be used with cache implementations that have an expire-after-write policy." );
        }
        this.cache = cache;
        this.name = name;
    }

    /**
     * Returns the name of the cache.
     *
     * @return the name of the cache.
     */
    @Override
    public String getName()
    {
        return name;
    }

    /**
     * Sets the name of the cache
     *
     * @param name the name of the cache
     */
    @Override
    public void setName( final String name )
    {
        this.name = name;
    }

    /**
     * Returns the maximum size of the cache in bytes. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * @return the maximum size of the cache in bytes.
     */
    @Override
    public long getMaxCacheSize()
    {
        // The constructor asserts that the eviction policy is present.
        return cache.policy().eviction().get().getMaximum();
    }

    /**
     * Sets the maximum size of the cache in bytes limited to integer size. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * <p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * @param maxSize the maximum size of the cache in bytes.
     */
    @Override
    public void setMaxCacheSize( final int maxSize )
    {
        setMaxCacheSize((long) maxSize);
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
    @Override
    public void setMaxCacheSize( final long maxSize )
    {
        cache.policy().eviction().ifPresent( eviction -> eviction.setMaximum(maxSize) );
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
    public long getMaxLifetime()
    {
        // The constructor asserts that the expireAfterWrite policy is present.
        return cache.policy().expireAfterWrite().get().getExpiresAfter( TimeUnit.MILLISECONDS );
    }

    /**
     * Sets the maximum number of milliseconds that any object can live
     * in cache. Once the specified number of milliseconds passes, the object
     * will be automatically expired from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * <p><strong>Note:</strong> If using the Hazelcast clustering plugin, this will not take
     * effect until the next time the cache is created</p>
     *
     * @param maxLifetime the maximum number of milliseconds before objects are expired.
     */
    @Override
    public void setMaxLifetime( final long maxLifetime )
    {
        cache.policy().expireAfterWrite().ifPresent( expiration -> expiration.setExpiresAfter( Duration.ofMillis( maxLifetime ) ) );;
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
    public int getCacheSize()
    {
        return (int) Math.min( Integer.MAX_VALUE, getLongCacheSize() );
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
        // The constructor asserts that the eviction policy and weightedSize are present.
        return cache.policy().eviction().get().weightedSize().getAsLong();
    }

    /**
     * Returns the number of cache hits. A cache hit occurs every
     * time the get method is called and the cache contains the requested
     * object.<p>
     * <p>
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    @Override
    public long getCacheHits()
    {
        return cache.stats().hitCount();
    }

    /**
     * Returns the number of cache misses. A cache miss occurs every
     * time the get method is called and the cache does not contain the
     * requested object.<p>
     * <p>
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    @Override
    public long getCacheMisses()
    {
        return cache.stats().missCount();
    }

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#values()} implementation, the collection returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable collection view of the values contained in this map
     */
    @Override
    public Collection<V> values()
    {
        return Collections.unmodifiableCollection( cache.asMap().values() );
    }

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#entrySet()} implementation, the set returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable set view of the mappings contained in this map
     */
    @Override
    public Set<Entry<K, V>> entrySet()
    {
        return Collections.unmodifiableSet( cache.asMap().entrySet() );
    }

    /**
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    @Override
    public int size()
    {
        final long result = cache.estimatedSize();
        return (int) Math.min( Integer.MAX_VALUE, result );
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings
     */
    @Override
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the specified
     * key.  More formally, returns <tt>true</tt> if and only if
     * this map contains a mapping for a key <tt>k</tt> such that
     * <tt>(key==null ? k==null : key.equals(k))</tt>.  (There can be
     * at most one such mapping.)
     *
     * @param key key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key
     * @throws ClassCastException   if the key is of an inappropriate type for
     *                              this map
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified key is null and this map
     *                              does not permit null keys
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    @Override
    public boolean containsKey( final Object key )
    {
        return cache.asMap().containsKey( key );
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.  More formally, returns <tt>true</tt> if and only if
     * this map contains at least one mapping to a value <tt>v</tt> such that
     * <tt>(value==null ? v==null : value.equals(v))</tt>.  This operation
     * will probably require time linear in the map size for most
     * implementations of the <tt>Map</tt> interface.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     * specified value
     * @throws ClassCastException   if the value is of an inappropriate type for
     *                              this map
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified value is null and this
     *                              map does not permit null values
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    @Override
    public boolean containsValue( final Object value )
    {
        return cache.asMap().containsValue( value );
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>If this map permits null values, then a return value of
     * {@code null} does not <i>necessarily</i> indicate that the map
     * contains no mapping for the key; it's also possible that the map
     * explicitly maps the key to {@code null}.  The {@link #containsKey
     * containsKey} operation may be used to distinguish these two cases.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or
     * {@code null} if this map contains no mapping for the key
     * @throws ClassCastException   if the key is of an inappropriate type for
     *                              this map
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified key is null and this map
     *                              does not permit null keys
     *                              (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    @Override
    public V get( final Object key )
    {
        return cache.getIfPresent( key );
    }

    /**
     * Associates the specified value with the specified key in this map
     * (optional operation).  If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.  (A map
     * <tt>m</tt> is said to contain a mapping for a key <tt>k</tt> if and only
     * if {@link #containsKey(Object) m.containsKey(k)} would return
     * <tt>true</tt>.)
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>,
     * if the implementation supports <tt>null</tt> values.)
     * @throws UnsupportedOperationException if the <tt>put</tt> operation
     *                                       is not supported by this map
     * @throws ClassCastException            if the class of the specified key or value
     *                                       prevents it from being stored in this map
     * @throws NullPointerException          if the specified key or value is null
     *                                       and this map does not permit null keys or values
     * @throws IllegalArgumentException      if some property of the specified key
     *                                       or value prevents it from being stored in this map
     */
    @Override
    public V put( final K key, final V value )
    {
        final V old = cache.getIfPresent( key );
        cache.put( key, value );
        return old;
    }

    /**
     * Removes the mapping for a key from this map if it is present
     * (optional operation).   More formally, if this map contains a mapping
     * from key <tt>k</tt> to value <tt>v</tt> such that
     * <code>(key==null ?  k==null : key.equals(k))</code>, that mapping
     * is removed.  (The map can contain at most one such mapping.)
     *
     * <p>Returns the value to which this map previously associated the key,
     * or <tt>null</tt> if the map contained no mapping for the key.
     *
     * <p>If this map permits null values, then a return value of
     * <tt>null</tt> does not <i>necessarily</i> indicate that the map
     * contained no mapping for the key; it's also possible that the map
     * explicitly mapped the key to <tt>null</tt>.
     *
     * <p>The map will not contain a mapping for the specified key once the
     * call returns.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * @throws UnsupportedOperationException if the <tt>remove</tt> operation
     *                                       is not supported by this map
     * @throws ClassCastException            if the key is of an inappropriate type for
     *                                       this map
     *                                       (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException          if the specified key is null and this
     *                                       map does not permit null keys
     *                                       (<a href="{@docRoot}/java/util/Collection.html#optional-restrictions">optional</a>)
     */
    @Override
    public V remove( final Object key )
    {
        final V old = cache.getIfPresent( key );
        cache.invalidate( key );
        return old;
    }

    /**
     * Copies all of the mappings from the specified map to this map
     * (optional operation).  The effect of this call is equivalent to that
     * of calling {@link #put(Object, Object) put(k, v)} on this map once
     * for each mapping from key <tt>k</tt> to value <tt>v</tt> in the
     * specified map.  The behavior of this operation is undefined if the
     * specified map is modified while the operation is in progress.
     *
     * @param m mappings to be stored in this map
     * @throws UnsupportedOperationException if the <tt>putAll</tt> operation
     *                                       is not supported by this map
     * @throws ClassCastException            if the class of a key or value in the
     *                                       specified map prevents it from being stored in this map
     * @throws NullPointerException          if the specified map is null, or if
     *                                       this map does not permit null keys or values, and the
     *                                       specified map contains null keys or values
     * @throws IllegalArgumentException      if some property of a key or value in
     *                                       the specified map prevents it from being stored in this map
     */
    @Override
    public void putAll( final Map<? extends K, ? extends V> m )
    {
        cache.putAll( m );
    }

    /**
     * Removes all of the mappings from this map (optional operation).
     * The map will be empty after this call returns.
     *
     * @throws UnsupportedOperationException if the <tt>clear</tt> operation
     *                                       is not supported by this map
     */
    @Override
    public void clear()
    {
        cache.invalidateAll();
    }

    /**
     * <strong>IMPORTANT:</strong> Unlike the standard {@link Map#keySet()} implementation, the set returned from
     * this method cannot be modified.
     *
     * @return an unmodifiable set view of the keys contained in this map
     */
    @Override
    public Set<K> keySet()
    {
        return Collections.unmodifiableSet( cache.asMap().keySet() );
    }
}
