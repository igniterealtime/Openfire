package org.jivesoftware.util.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
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
     * @param element  The element to be added (cannot be null, as Cache does not support null values).
     * @param key      The cache entry identifier (cannot be null)
     * @param supplier A provider of empty instances of the collection used as a value of the cache (in which elements are placed).
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void addValueToMultiValuedCache( Cache<K, C> cache, K key, V element, Supplier<C> supplier )
    {
        final Lock lock = CacheFactory.getLock( key, cache );
        try
        {
            lock.lock();

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
     * Removes all instances of the specified element from the collection that is mapped by the provided key.
     *
     * When the element removed from the set leaves that set empty, the cache entry is removed completely.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param key     The cache entry identifier (cannot be null)
     * @param element The element to be removed (should not be null, as Cache does not support null values).
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void removeValueFromMultiValuedCache( Cache<K, C> cache, K key, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, C>> entries = Collections.unmodifiableSet( cache.entrySet() );

        final Lock lock = CacheFactory.getLock( key, cache );
        try
        {
            lock.lock();

            final C elements = cache.get( key );

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
     * Removes all instances the specified element from every collection that is a value of the cache.
     *
     * When the element removed from the set leaves that set empty, the cache entry is removed completely.
     *
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param element The element to be removed (should not be null, as Cache does not support null values).
     */
    public static <K extends Serializable, V, C extends Collection<V> & Serializable> void removeValueFromMultiValuedCache( Cache<K, C> cache, V element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, C>> entries = Collections.unmodifiableSet( cache.entrySet() );

        for ( final Map.Entry<K, C> entry : entries )
        {
            final K key = entry.getKey();

            final Lock lock = CacheFactory.getLock( key, cache );
            try
            {
                lock.lock();

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
                    }
                    else
                    {
                        // The cluster-based cache needs an explicit 'put' to cause the change to propagate.
                        cache.put( entry.getKey(), elements );
                    }
                }
            }
            finally
            {
                lock.unlock();
            }
        }
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

            final Lock lock = CacheFactory.getLock( key, cache );
            try
            {
                lock.lock();

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
     * @param oldValue The element to be replaced (cannot be null, as Cache does not support null values).
     * @param newValue The replacement element (cannot be null, as Cache does not support null values).
     * @param key      The cache entry identifier (cannot be null)
     * @param supplier A provider of empty instances of the collection used as a value of the cache (in which elements are placed).
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

            final Lock lock = CacheFactory.getLock( key, cache );
            try
            {
                lock.lock();

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
