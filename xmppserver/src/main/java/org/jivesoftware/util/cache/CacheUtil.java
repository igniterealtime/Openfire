package org.jivesoftware.util.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
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
        final C elements = cache.getOrDefault( key, supplier.get() );
        elements.add( element );
        cache.put( key, elements ); // Explicitly adding the value is required for the change to propagate through Hazelcast.
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
    }
}
