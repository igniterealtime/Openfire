/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.container;

import java.util.Map;

/**
 * Configuration to use when creating caches. Caches can be used when running stand alone or when
 * running in a cluster. When running in a cluster a few extra parameters might be needed. Read
 * {@link #getParams()} for more information.
 *
 * @author Gaston Dombiak
 */
public class CacheInfo {
    /**
     * Name of the cache
     */
    private String cacheName;
    /**
     * Type of cache to use when running in a cluster. When not running in a cluster this value is not used.
     */
    private Type type;
    /**
     * Map with the configuration of the cache. Openfire expects the following properties to exist:
     * <ul>
     *  <li><b>back-size-high</b> - Maximum size of the cache. Size is in bytes. Zero means that there is no limit.</li>
     *  <li><b>back-size-low</b> - Size in byte of the cache after a clean up. Use zero to place no limit.</li>
     *  <li><b>back-expiry</b> - minutes, hours or days before content is expired. 10m, 12h or 2d. Zero means never.</li>
     * </ul>
     */
    private Map<String, String> params;

    /**
     * Creates the configuration to use for the specified cache. Caches can be used when running
     * as a standalone application or when running in a cluster. When running in a cluster a few
     * extra configuration are going to be needed. Read {@link #getParams()} for more information.
     *
     * @param cacheName name of the cache.
     * @param type type of cache to use when running in a cluster. Ignored when running as standalone.
     * @param params extra parameters that define cache properties like max size or expiration.
     */
    public CacheInfo(String cacheName, Type type, Map<String, String> params) {
        this.cacheName = cacheName;
        this.type = type;
        this.params = params;
    }

    public String getCacheName() {
        return cacheName;
    }

    public Type getType() {
        return type;
    }

    /**
     * Returns a map with the configuration to use for the cache. When running standalone the following
     * properties are required.
     * <ul>
     *  <li><b>back-size-high</b> - Maximum size of the cache. Size is in bytes. Zero means that there is no limit.</li>
     *  <li><b>back-expiry</b> - minutes, hours or days before content is expired. 10m, 12h or 2d. Zero means never.</li>
     * </ul>
     * When running in a cluster this extra property is required. More properties can be defined depending on the
     * clustering solution being used.
     * <ul>
     *  <li><b>back-size-low</b> - Size in byte of the cache after a clean up. Use zero to place no limit.</li>
     * </ul>
     *
     * @return map with the configuration to use for the cache.
     */
    public Map<String, String> getParams() {
        return params;
    }

    public static enum Type {
        /**
         * An optimistic scheme defines a cache which fully replicates all of its data to all cluster nodes that
         * are running the service. This cache is good for frequent reads and not frequent writes. However, this
         * cache will not scale fine if it has lot of content that will end up consuming all the JVM memory. For
         * this case a {@link #distributed} is a better option.
         */
        optimistic("optimistic"),
        /**
         * An distributed-scheme defines caches where the storage for entries is partitioned across cluster nodes.
         */
        distributed("near-distributed");

        private String name;
        Type(String name) {
            this.name = name;
        }

        public static Type valueof(String name) {
            if ("optimistic".equals(name)) {
                return optimistic;
            }
            return distributed;
        }

        public String getName() {
            return name;
        }
    }
}
