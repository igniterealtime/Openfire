/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2021 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.XMPPServer;
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

    @Override
    public boolean startCluster() {
        return false;
    }

    @Override
    public void stopCluster() {
    }

    @Override
    public Cache createCache(String name) {
        // Get cache configuration from system properties or default (hardcoded) values
        long maxSize = CacheFactory.getMaxCacheSize(name);
        long lifetime = CacheFactory.getMaxCacheLifetime(name);
        // Create cache with located properties
        return new DefaultCache(name, maxSize, lifetime);
    }

    @Override
    public void destroyCache(Cache cache) {
        cache.clear();
    }

    @Override
    public boolean isSeniorClusterMember() {
        return true;
    }

    @Override
    public Collection<ClusterNodeInfo> getClusterNodesInfo() {
        return Collections.emptyList();
    }

    @Override
    public int getMaxClusterNodes() {
        return 0;
    }

    @Override
    public byte[] getSeniorClusterMemberID() {
        return null;
    }

    @Override
    public byte[] getClusterMemberID() {
        return XMPPServer.getInstance().getNodeID().toByteArray();
    }

    @Override
    public long getClusterTime() {
        return System.currentTimeMillis();
    }

    @Override
    public void doClusterTask(final ClusterTask task) {
    }

    @Override
    public void doClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
    }

    @Override
    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        return Collections.emptyList();
    }

    @Override
    public Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
    }

    @Override
    public void updateCacheStats(Map<String, Cache> caches) {
    }

    @Override
    public String getPluginName() {
        return "local";
    }

    @Override
    public ClusterNodeInfo getClusterNodeInfo(byte[] nodeID) {
        // not clustered
        return null;
    }
}
