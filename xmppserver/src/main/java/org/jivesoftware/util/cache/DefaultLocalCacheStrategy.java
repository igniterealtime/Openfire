/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CacheFactoryStrategy for use in Openfire. It creates and manages local caches, and it's cluster
 * related method implementations do nothing.
 *
 * @see Cache
 * @see CacheFactory
 */
public class DefaultLocalCacheStrategy implements CacheFactoryStrategy {

    /**
     * Keep track of the locks that are currently being used.
     */
    private Map<Object, LockAndCount> locks = new ConcurrentHashMap<>();

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
    public Lock getLock(Object key, Cache cache) {
        Object lockKey = key;
        if (key instanceof String) {
            lockKey = ((String) key).intern();
        }

        return new LocalLock(lockKey);
    }

    @SuppressWarnings( "LockAcquiredButNotSafelyReleased" )
    private void acquireLock( Object key) {
        ReentrantLock lock = lookupLockForAcquire(key);
        lock.lock();
    }

    private void releaseLock(Object key) {
        ReentrantLock lock = lookupLockForRelease(key);
        lock.unlock();
    }

    private ReentrantLock lookupLockForAcquire(Object key) {
        synchronized(key) {
            LockAndCount lac = locks.get(key);
            if (lac == null) {
                lac = new LockAndCount(new ReentrantLock());
                lac.count = 1;
                locks.put(key, lac);
            }
            else {
                lac.count++;
            }

            return lac.lock;
        }
    }

    private ReentrantLock lookupLockForRelease(Object key) {
        synchronized(key) {
            LockAndCount lac = locks.get(key);
            if (lac == null) {
                throw new IllegalStateException("No lock found for object " + key);
            }

            if (lac.count <= 1) {
                locks.remove(key);
            }
            else {
                lac.count--;
            }

            return lac.lock;
        }
    }


    private class LocalLock implements Lock {
        private final Object key;

        LocalLock(Object key) {
            this.key = key;
        }

        @Override
        public void lock(){
            acquireLock(key);
        }

        @Override
        public void	unlock() {
            releaseLock(key);
        }

        @Override
        public void	lockInterruptibly(){
            throw new UnsupportedOperationException();
        }

        @Override
        public Condition newCondition(){
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean 	tryLock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean 	tryLock(long time, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

    }

    private static class LockAndCount {
        final ReentrantLock lock;
        int count;

        LockAndCount(ReentrantLock lock) {
            this.lock = lock;
        }
    }

    @Override
    public ClusterNodeInfo getClusterNodeInfo(byte[] nodeID) {
        // not clustered
        return null;
    }
}
