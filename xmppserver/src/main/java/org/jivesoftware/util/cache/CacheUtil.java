package org.jivesoftware.util.cache;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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
     * Removes all instances the specified element from every collection that is a value of the cache.
     * <p>
     * When the element removed from the set leaves that set empty, the cache entry is removed completely.
     * <p>
     * The implementation of this method is designed to be compatible with both clustered as well as non-clustered caches.
     *
     * @param cache   The cache from which to remove the element (cannot be null).
     * @param element The element to be removed (should not be null, as Cache does not support null values).
     */
    public static <K extends Serializable, E, V extends Collection<E> & Serializable> void removeValueFromMultiValuedCache( Cache<K, V> cache, E element )
    {
        // In some cache implementations, the entry-set is unmodifiable. To guard against potential
        // future changes of this implementation (that would make the implementation incompatible with
        // these cache implementations), the entry-set that's operated on in this implementation is
        // explicitly wrapped in an unmodifiable collection. That forces this implementation to be
        // compatible with the 'lowest common denominator'.
        final Set<Map.Entry<K, V>> entries = Collections.unmodifiableSet( cache.entrySet() );

        for ( final Map.Entry<K, V> entry : entries )
        {
            final V elements = entry.getValue();

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
