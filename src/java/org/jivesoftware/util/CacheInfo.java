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

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Interface for querying and setting cache configuration information.
 * Since there will be several active caches within a single server, caches can be
 * given names (hopefully with some relationship to the the client using the
 * cache) for display to administrators.<p>
 *
 * This interface is separated from the cache so that objects can update
 * cache configuration separate from the cache itself. (Allowing it to delay
 * changes to the cache to times best suited to its own performance.) Alternatively,
 * objects can update the cache directly since it extends the CacheInfo interface.
 *
 * @author Iain Shigeoka
 */
public interface CacheInfo {
    /**
     * <p>Obtains the name of this cache.</p>
     * <p>The name is completely arbitrary and used only for
     * display to administrators. However, it should have some
     * relationship to the primary user of the cache.</p>
     *
     * @return the name of this cache.
     */
    public String getName();

    /**
     * <p>Obtain the maximum size of the cache.</p>
     * <p>If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.</p>
     *
     * @return The maximum size of the cache (-1 indicates unlimited max size)
     *         <p/>
     *
     */
    public int getMaxCacheSize();

    /**
     * Sets the maximum size of the cache. If the cache grows larger
     * than the max size, the least frequently used items will be removed. If
     * the max cache size is set to -1, there is no size limit.
     *
     * @param size The maximum size of this cache (-1 indicates unlimited max size)
     * @throws UnauthorizedException If there is insufficient permissions to adjust this setting
     */
    public void setMaxCacheSize(int size) throws UnauthorizedException;

    /**
     * Returns the maximum number of milleseconds that any object can live
     * in cache. Once the specified number of milleseconds passes, the object
     * will be automatically expried from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * @return the maximum number of milleseconds before objects are expired.
     */
    public long getMaxLifetime();

    /**
     * Sets the maximum number of milleseconds that any object can live
     * in cache. Once the specified number of milleseconds passes, the object
     * will be automatically expried from cache. If the max lifetime is set
     * to -1, then objects never expire.
     *
     * @param maxLifetime the maximum number of milleseconds before objects are expired.
     * @throws UnauthorizedException If there is insufficient permissions to adjust this setting
     */
    public void setMaxLifetime(long maxLifetime) throws UnauthorizedException;

    /**
     * Returns the size of the cache contents in bytes. This value is only a
     * rough approximation, so cache users should expect that actual VM
     * memory used by the cache could be significantly higher than the value
     * reported by this method.
     *
     * @return the size of the cache contents in bytes.
     */
    public int getCacheSize();

    /**
     * Returns the number of cache hits. A cache hit occurs every
     * time the get method is called and the cache contains the requested
     * object.<p>
     *
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    public long getCacheHits();

    /**
     * Returns the number of cache misses. A cache miss occurs every
     * time the get method is called and the cache does not contain the
     * requested object.<p>
     *
     * Keeping track of cache hits and misses lets one measure how efficient
     * the cache is; the higher the percentage of hits, the more efficient.
     *
     * @return the number of cache hits.
     */
    public long getCacheMisses();
}
