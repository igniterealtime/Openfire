package org.jivesoftware.util.cache.lock;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.util.cache.Cache;

import java.util.Objects;

/**
 * A key of a cache, namespaced by the cache that it belongs to.
 */
class CacheKey
{
    private static final Interner<CacheKey> interner = Interners.newWeakInterner();

    final String cacheName;
    final Object key;

    public static CacheKey createCacheKey(Cache cache, Object key) {
        final CacheKey instance = new CacheKey(cache, key);
        return interner.intern(instance);
    }

    private CacheKey(Cache cache, Object key)
    {
        this.cacheName = cache.getName();
        this.key = key;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return cacheName.equals(cacheKey.cacheName) && key.equals(cacheKey.key);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(cacheName, key);
    }
}
