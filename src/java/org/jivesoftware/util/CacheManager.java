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

import java.util.*;

/**
 * A centralized, JVM static manager of Jive caches. Caches are essential for
 * scalability.
 *
 * @author Iain Shigeoka
 */
public class CacheManager {

    private static Map caches = new HashMap();
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
     * @param name the name of the cache to create.
     * @param defaultMaxCacheSize the default max size the cache can grow to, in bytes.
     */
    public static void initializeCache(String name, int defaultMaxCacheSize) {
        Cache cache = (Cache)caches.get(name);
        if (cache == null) {
            int maxCacheSize = JiveGlobals.getIntProperty("cache." + name + ".size", defaultMaxCacheSize);
            caches.put(name, new Cache(name, maxCacheSize, maxLifetime));
        }
    }

    /**
     * Returns the cache specified by name.
     *
     * @param name the name of the cache to return.
     * @return the cache found, or null if no cache by that name has been initialized.
     */
    public static Cache getCache(String name) {
        return (Cache)caches.get(name);
    }
}