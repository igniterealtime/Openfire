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
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Utility methods for working with caches.
 *
 * Different implementations of {@link Cache} have idiosyncrasies, which this implementation takes into
 * account.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CacheUtil
{
    /**
     * Adds the specified element to a cache, in a collection that is mapped by the provided key.
     *
     * When the cache does not contain an entry for the provided key, a cache entry is added, which will have a new collection
     * created using the provided Supplier, to which the specified element is added.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache    The cache from which to remove the element (cannot be null).
     * @param element  The element to be added (can be null only if the value-Collection supports null values).
     * @param key      The cache entry identifier (cannot be null)
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @param <C> the type of collection contained by the cache
     * @param supplier A provider of empty instances of the collection used as a value of the cache (in which elements are placed).
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void addValueToMultiValuedCache( Cache<K, C> cache, K key, V element, Supplier<C> supplier )
    {
        final Lock lock = cache.getLock(key);
        lock.lock();
        try
        {
            final C elements = cache.getOrDefault( key, supplier.get() );
            elements.add( element );
            cache.put( key, elements ); // Explicitly adding the value is required for the change to propagate through Hazelcast.
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Removes all entries of a cache that map to the provided value.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param element The element to be removed (can not be null ).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @return a Set containing the keys of all affected cache entries (never null)
     */
    public static <K extends Serializable, V extends Serializable> Set<K> removeValueFromCache( Cache<K, V> cache, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, V>> entries = Collections.unmodifiableSet( cache.entrySet() );

        // contains all entries that were somehow changed.
        final Set<K> result = new HashSet<>();

        for ( final Map.Entry<K, V> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                if ( entry.getValue().equals( element ) )
                {
                    cache.remove( entry.getKey() );
                    result.add( entry.getKey() );
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Removes all instances of the specified element from the collection that is mapped by the provided key.
     *
     * When the element removed from the set leaves that set empty, the cache entry is removed completely.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param key     The cache entry identifier (cannot be null)
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @param <C> the type of collection contained by the cache
     * @param element The element to be removed (can be null only if the value-Collection supports null values).
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void removeValueFromMultiValuedCache( Cache<K, C> cache, K key, V element )
    {
        final Lock lock = cache.getLock( key );
        lock.lock();
        try
        {
            final C elements = cache.get( key );

            if ( elements == null ) {
                return;
            }

            // Remove all instances of the element from the entry value.
            boolean changed = false;
            while ( elements.remove( element ) )
            {
                changed = true;
            }

            if ( changed )
            {
                if ( elements.isEmpty() )
                {
                    // When after removal, the value is empty, remove the cache entry completely.
                    cache.remove( key );
                }
                else
                {
                    // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                    cache.put( key, elements );
                }
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Removes all instances of the specified element from every collection that is a value of the cache.
     *
     * When the element removed from the collection leaves that collection empty, the cache entry is removed completely.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * The return value is a Map that contains all entries that were affected by the call. The returned has exactly two
     * keys, that each have a Map for its value.
     * - the Map that is the value of the 'false' key contains all entries that have been removed from the cache.
     * - the Map that is the value of the 'true' key contains all entries that have been modified.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param element The element to be removed (can be null only if the value-Collection supports null values).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @param <C> the type of collection contained by the cache
     * @return a map containing all affected cache entries (never null)
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> Map<Boolean,Map<K, C>> removeValueFromMultiValuedCache( Cache<K, C> cache, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, C>> entries = Collections.unmodifiableSet( cache.entrySet() );

        // contains all entries that were somehow changed.
        final Map<Boolean, Map<K, C>> result = new HashMap<>();
        result.put( false, new HashMap<>());
        result.put( true, new HashMap<>());

        for ( final Map.Entry<K, C> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                final C elements = entry.getValue();

                // Remove all instances of the element from the entry value.
                boolean changed = false;
                while ( elements.remove( element ) )
                {
                    changed = true;
                }

                if ( changed )
                {
                    if ( elements.isEmpty() )
                    {
                        // When after removal, the value is empty, remove the cache entry completely.
                        cache.remove( entry.getKey() );
                        result.get(false).put( entry.getKey(), elements );
                    }
                    else
                    {
                        // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                        cache.put( entry.getKey(), elements );
                        result.get(true).put( entry.getKey(), elements );
                    }
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Remove elements from every collection that is a value of the cache, except for the specified element.
     *
     * When removal leaves a collection empty, the cache entry is removed completely.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * The return value is a Map that contains all entries that were affected by the call. The returned has exactly two
     * keys, that each have a Map for its value.
     * - the Map that is the value of the 'false' key contains all entries that have been removed from the cache.
     * - the Map that is the value of the 'true' key contains all entries that have been modified.
     *
     * @param cache   The cache in which to retain the element (cannot be null).
     * @param element The element to be retained (can be null only if the value-Collection supports null values).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @param <C> the type of collection contained by the cache
     * @return a map containing all affected cache entries (never null)
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> Map<Boolean,Map<K, C>> retainValueInMultiValuedCache( Cache<K, C> cache, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, C>> entries = Collections.unmodifiableSet( cache.entrySet() );

        // contains all entries that were somehow changed.
        final Map<Boolean, Map<K, C>> result = new HashMap<>();
        result.put( false, new HashMap<>());
        result.put( true, new HashMap<>());

        for ( final Map.Entry<K, C> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                final C elements = entry.getValue();

                // Remove all instances of the element from the entry value.
                boolean changed = false;
                while ( elements.retainAll( Collections.singleton( element ) ) )
                {
                    changed = true;
                }

                if ( changed )
                {
                    if ( elements.isEmpty() )
                    {
                        // When after removal, the value is empty, remove the cache entry completely.
                        cache.remove( entry.getKey() );
                        result.get(false).put( entry.getKey(), elements );
                    }
                    else
                    {
                        // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                        cache.put( entry.getKey(), elements );
                        result.get(true).put( entry.getKey(), elements );
                    }
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Remove elements from every collection that is a value of the cache, except for the specified element.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     **
     * @param cache   The cache in which to retain the element (cannot be null).
     * @param element The element to be retained (cannot be null).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @return a Set containing the keys of all affected cache entries (never null)
     */
    public static <K extends Serializable, V extends Serializable> Set<K> retainValueInCache( Cache<K, V> cache, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, V>> entries = Collections.unmodifiableSet( cache.entrySet() );

        // contains all entries that were somehow changed.
        final Set<K> result = new HashSet<>();

        for ( final Map.Entry<K, V> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                if ( !entry.getValue().equals( element ) )
                {
                    cache.remove( entry.getKey() );
                    result.add( entry.getKey() );
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        return result;
    }

    /**
     * Replaces all instances of a particular value in a cache.
     *
     * Every instance of the old value that is found in a cache value is replaced by the new value.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache    The cache from which to remove the element (cannot be null).
     * @param oldValue The element to be replaced (cannot be null).
     * @param newValue The replacement element (cannot be null).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     */
    public static <K extends Serializable, V extends Serializable> void replaceValueInCache( Cache<K, V> cache, V oldValue, V newValue )
    {
        if ( newValue.equals( oldValue ) )
        {
            return;
        }

        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, V>> entries = Collections.unmodifiableSet( cache.entrySet() );

        for ( final Map.Entry<K, V> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                if ( entry.getValue().equals( oldValue ) )
                {
                    // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                    cache.put( entry.getKey(), newValue );
                }
            }
            finally
            {
                lock.unlock();
            }
        }
    }

    /**
     * Applies a mapping function to all values in a cache.
     *
     * The provided mapping function is applied to all values in the cache. A cache modification is made only when the
     * mapping function returns a value that is not equal to the original value.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache  The cache from which to remove the element (cannot be null).
     * @param mapper The mapping function (cannot be null).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     */
    public static <K extends Serializable, V extends Serializable> void replaceValueInCacheByMapping( Cache<K, V> cache, Function<V, V> mapper )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, V>> entries = Collections.unmodifiableSet( cache.entrySet() );

        for ( final Map.Entry<K, V> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                final V modifiedValue = mapper.apply( entry.getValue() );
                if ( !modifiedValue.equals( entry.getValue() ) )
                {
                    // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                    cache.put( entry.getKey(), modifiedValue );
                }
            }
            finally
            {
                lock.unlock();
            }
        }
    }

    /**
     * Replaces an element in a cache that has collection-based values.
     *
     * Every instance of the old value that is found in a collection-based value is removed, and for each such removal,
     * the new value is added to the cache. Note that no guarantees regarding collection order are given.
     *
     * Cache entries for which the value-collection does contain the old value are left unchanged.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache    The cache from which to remove the element (cannot be null).
     * @param oldValue The element to be replaced (can be null only if the value-Collection supports null values).
     * @param newValue The replacement element (can be null only if the value-Collection supports null values).
     * @param <K> the type of key contained by the cache
     * @param <V> the type of value contained by the cache
     * @param <C> the type of collection contained by the cache
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void replaceValueInMultivaluedCache( Cache<K, C> cache, V oldValue, V newValue )
    {
        if ( newValue.equals( oldValue ) )
        {
            return;
        }

        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, C>> entries = Collections.unmodifiableSet( cache.entrySet() );

        for ( final Map.Entry<K, C> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = cache.getLock( key );
            lock.lock();
            try
            {
                final C elements = entry.getValue();

                // Replace all instances of the element from the entry value.
                boolean changed = false;
                while ( elements.remove( oldValue ) )
                {
                    elements.add( newValue );
                    changed = true;
                }

                if ( changed )
                {
                    // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                    cache.put( entry.getKey(), elements );
                }
            }
            finally
            {
                lock.unlock();
            }
        }
    }
}
