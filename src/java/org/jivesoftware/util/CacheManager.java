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

import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheFactory;

import java.util.Hashtable;

/**
 * <p>A centralized, JVM static manager of Jive caches.</p>
 * <p/>
 * <p>Caches are essential for scalability of Jive software. Only Jive objects should access the cache!
 * 3rd parties are discouraged from modifying or using the Jive caches without consulting with Jive
 * engineers on consequences.</p>
 *
 * @author Iain Shigeoka
 *         <p/>
 */
public class CacheManager {

    /**
     * <p>The map for accessing the caches.</p>
     */
    private static Hashtable caches = new Hashtable();
    /**
     * <p>The max lifetime of items in any cache.</p>
     */
    private static long maxLifetime = JiveConstants.HOUR * 6;

    /**
     * <p>Initialize a cache by name.</p>
     * <p/>
     * <p>Caches require initialization before use. Be careful to initialize your cache before using it.
     * Initializing a cache that has already been initialized once does nothing.</p>
     * <p/>
     * <p>The cache manager will check jive module context for overriding defaultMaxCacheSize values.
     * The property names should be "cache.name.size" where 'name' will be the same as the cache name.
     * If the property exists, that value will be used instead of the defaultMaxCacheSize.</p>
     *
     * @param name                the name of the cache to create.
     * @param defaultMaxCacheSize The default max size the cache can grow to, in bytes.
     */
    public static void initializeCache(String name, int defaultMaxCacheSize, ModuleContext context) {
        Cache cache = (Cache)caches.get(name);
        if (cache == null) {
            String cacheSize = context.getProperty("cache." + name + ".size");
            int maxCacheSize = defaultMaxCacheSize;
            if (cacheSize != null) {
                try {
                    maxCacheSize = Integer.parseInt(cacheSize);
                }
                catch (NumberFormatException e) { /* ignore */
                }
            }
            caches.put(name, CacheFactory.createCache(name, maxCacheSize, maxLifetime));
        }
    }

    /**
     * <p>Obtain a cache by name.</p>
     *
     * @param name The name of the cache to retrieve
     * @return The cache found, or null if no cache by that name has been initialized
     */
    public static Cache getCache(String name) {
        return (Cache)caches.get(name);
    }
}
