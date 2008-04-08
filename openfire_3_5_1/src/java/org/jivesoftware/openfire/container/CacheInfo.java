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
         * Data is fully replicated to every member in the cluster. Offers the fastest read performance. Clustered,
         * fault-tolerant cache with linear performance scalability for reads, but poor scalability for writes
         * (as writes must be processed by every member in the cluster). Because data is replicated to all machines,
         * adding servers does not increase aggregate cache capacity.
         */
        replicated("replicated"),
        /**
         * OptimisticCache is a clustered cache implementation similar to the ReplicatedCache implementation, but
         * without any concurrency control. This implementation has the highest possible throughput. It also allows
         * to use an alternative underlying store for the cached data (for example, a MRU/MFU-based cache). However,
         * if two cluster members are independently pruning or purging the underlying local stores, it is possible
         * that a cluster member may have a different store content than that held by another cluster member.
         * This cache is good for frequent reads and not frequent writes. However, this cache will not scale fine
         * if it has lot of content that will end up consuming all the JVM memory. For this case a
         * {@link #distributed} is a better option.
         */
        optimistic("optimistic"),
        /**
         * An distributed-scheme defines caches where the storage for entries is partitioned across cluster nodes.
         * A hybrid cache; fronts a fault-tolerant, scalable partitioned cache with a local cache. Near cache
         * invalidates front cache entries, using configurable invalidation strategy, and provides excellent
         * performance and synchronization. Near cache backed by a partitioned cache offers zero-millisecond local
         * access for repeat data access, while enabling concurrency and ensuring coherency and fail-over,
         * effectively combining the best attributes of replicated and partitioned caches.
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
