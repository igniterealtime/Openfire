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

package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.cluster.ClusterNodeInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * CacheFactoryStrategy for use in Openfire. It creates and manages local caches, and it's cluster
 * related method implementations do nothing.
 *
 * @see Cache
 * @see CacheFactory
 */
public class DefaultLocalCacheStrategy implements CacheFactoryStrategy {


    public DefaultLocalCacheStrategy() {
    }

    public boolean startCluster() {
        return false;
    }

    public void stopCluster() {
    }

    public Cache createCache(String name) {
        // Get cache configuration from system properties or default (hardcoded) values
        long maxSize = CacheFactory.getMaxCacheSize(name);
        long lifetime = CacheFactory.getMaxCacheLifetime(name);
        // Create cache with located properties
        return new DefaultCache(name, maxSize, lifetime);
    }

    public void destroyCache(Cache cache) {
        cache.clear();
    }

    public boolean isSeniorClusterMember() {
        return true;
    }

    public Collection<ClusterNodeInfo> getClusterNodesInfo() {
        return Collections.emptyList();
    }

    public int getMaxClusterNodes() {
        return 0;
    }

    public byte[] getSeniorClusterMemberID() {
        return null;
    }

    public byte[] getClusterMemberID() {
        return new byte[0];
    }

    public void doClusterTask(final ClusterTask task) {
    }

    public boolean doClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
    }

    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        return Collections.emptyList();
    }

    public Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
    }

    public void updateCacheStats(Map<String, Cache> caches) {
    }

    public void lockKey(Object key, long timeout) {
    }

    public void unlockKey(Object key) {
    }
}
