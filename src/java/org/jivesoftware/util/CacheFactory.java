/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.util;

import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.Cache;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates Cache objects. The returned caches will either be local or clustered
 * depending on the clustering enabled setting and a user's license.<p>
 * <p/>
 * When clustered caching is turned on, cache usage statistics for all caches
 * that have been created are periodically published to the clustered cache
 * named "$cacheStats".
 * <p/>
 * Clustering support has been removed until that feature is needed in XMPP.
 *
 * @author Matt Tucker
 */
public class CacheFactory {

    /**
     * One of the major potential bottlenecks of the cache is performing
     * System.currentTimeMillis() for every cache get operation. Instead,
     * we maintain a global timestamp that gets updated once a second. This
     * means that cache expirations can be no more than one second accurate.
     */
    public static long currentTime;

    /**
     * Determines if clustering is enabled.
     */
    private static boolean clusteringEnabled = false;

    /**
     * Storage for all caches that get created.
     */
    private static Map caches = new HashMap();

    /**
     * Returns the named cache, creating it as necessary.
     *
     * @param name         the name of the cache to create.
     * @param maxCacheSize the max size the cache can grow to, in bytes.
     * @param maxLifetime  the max number of milleseconds that objects can
     *                     remain in cache.
     * @return a cache with the specified properties.
     */
    public static Cache createCache(String name, int maxCacheSize, long maxLifetime) {
        Cache cache = (Cache)caches.get(name);
        if (cache != null) {
            return cache;
        }

        if (cache == null) {
            cache = new DefaultCache(name, maxCacheSize, maxLifetime);
        }

        // Add this cache name to the list if it's not already there.
        if (!caches.containsKey(name)) {
            caches.put(name, cache);
        }

        return cache;
    }

    /**
     * Creates a new cache. If clustering is enabled, a clustered cache will
     * wrap the specified cache. If clustering is not enabled, the specified
     * cache will be registered with the system and then returned. Using this
     * method to create a clustered/non-clustered cache is useful if you need
     * to use a different cache implementation than the default.
     *
     * @param cache the cache returned when clustering is disabled, or the
     *              underlying cache of a clustered cache when clustering is enabled.
     * @return a cache.
     */
    public static Cache createCache(Cache cache) {
        String name = cache.getName();
        int maxCacheSize = cache.getMaxCacheSize();
        long maxLifetime = cache.getMaxLifetime();
        Cache newCache = (Cache)caches.get(name);
        if (newCache != null) {
            return newCache;
        }
        newCache = cache;

        // Add this cache name to the list if it's not already there.
        if (!caches.containsKey(name)) {
            caches.put(name, newCache);
        }

        return newCache;
    }

    /**
     * Returns true if cache clustering is enabled.
     *
     * @return true if clustering is enabled.
     */
    public static boolean isClusteringEnabled() {
        return clusteringEnabled;
    }

    /**
     * Sets whether cache clustering should be enabled. Anytime this value is
     * changed, the application server must be restarted
     *
     * @param enabled true if cache clustering should be enabled.
     */
    public static void setClusteringEnabled(boolean enabled) {
        // We only set the Jive property but don't change the
        // clusteringEnabled variable. This enforces app-server restarts
        // being required.
        JiveGlobals.setJiveProperty("cache.clustering.enabled", String.valueOf(enabled));
    }
}
