/*
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * Use is subject to license terms.
 */

package org.jivesoftware.util;

import java.util.*;

/**
 * Centralized management of caches. Caches are essential for performance and scalability.
 *
 * @see Cache
 * @author Matt Tucker
 */
public class CacheManager {

    private static Map<String, Cache> caches = new HashMap<String, Cache>();
    private static final long DEFAULT_EXPIRATION_TIME = JiveConstants.HOUR * 6;

    /**
     * Initializes a cache given it's name and max size. The default expiration time
     * of six hours will be used. If a cache with the same name has already been initialized,
     * this method returns the existing cache.<p>
     *
     * The size and expiration time for the cache can be overridden by setting Jive properties
     * in the format:<ul>
     *
     *  <li>Size: "cache.CACHE_NAME.size", in bytes.
     *  <li>Expiration: "cache.CACHE_NAME.expirationTime", in milleseconds.
     * </ul>
     * where CACHE_NAME is the name of the cache.
     *
     * @param name the name of the cache to initialize.
     * @param size the size the cache can grow to, in bytes.
     */
    public static Cache initializeCache(String name, int size) {
        return initializeCache(name, size, DEFAULT_EXPIRATION_TIME);
    }

    /**
     * Initializes a cache given it's name, max size, and expiration time. If a cache with
     * the same name has already been initialized, this method returns the existing cache.<p>
     *
     * The size and expiration time for the cache can be overridden by setting Jive properties
     * in the format:<ul>
     *
     *  <li>Size: "cache.CACHE_NAME.size", in bytes.
     *  <li>Expiration: "cache.CACHE_NAME.expirationTime", in milleseconds.
     * </ul>
     * where CACHE_NAME is the name of the cache.
     *
     * @param name the name of the cache to initialize.
     * @param size the size the cache can grow to, in bytes.
     */
    public static Cache initializeCache(String name, int size, long expirationTime) {
        Cache cache = caches.get(name);
        if (cache == null) {
            size = JiveGlobals.getIntProperty("cache." + name + ".size", size);
            expirationTime = (long)JiveGlobals.getIntProperty("cache." + name + ".expirationTime",
                    (int)expirationTime);
            caches.put(name, new Cache(name, size, expirationTime));
        }
        return cache;
    }

    /**
     * Returns the cache specified by name. The cache must be initialized before this
     * method can be called.
     *
     * @param name the name of the cache to return.
     * @return the cache found, or <tt>null</tt> if no cache by that name
     *      has been initialized.
     */
    public static Cache getCache(String name) {
        return caches.get(name);
    }
}