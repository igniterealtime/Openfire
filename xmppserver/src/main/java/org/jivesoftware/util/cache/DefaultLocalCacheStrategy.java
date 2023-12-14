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

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
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
    private Map<CacheKey, LockAndCount> locks = new ConcurrentHashMap<>();

    private Interner<CacheKey> interner = Interners.newWeakInterner();

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
        return new LocalLock(new CacheKey(cache, key));
    }

    @SuppressWarnings( "LockAcquiredButNotSafelyReleased" )
    private void acquireLock(CacheKey key) {
        ReentrantLock lock = lookupLockForAcquire(key);
        lock.lock();
    }

    private void releaseLock(CacheKey key) {
        ReentrantLock lock = lookupLockForRelease(key);
        lock.unlock();
    }

    private ReentrantLock lookupLockForAcquire(CacheKey cacheKey) {
        CacheKey mutex = interner.intern(cacheKey); // Ensure that the mutex used in the next line is the same for objects that are equal.
        synchronized(mutex) {
            LockAndCount lac = locks.get(mutex);
            if (lac == null) {
                lac = new LockAndCount(new ReentrantLock());
                lac.count = 1;
                locks.put(mutex, lac);
            }
            else {
                lac.count++;
            }

            return lac.lock;
        }
    }

    private ReentrantLock lookupLockForRelease(CacheKey cacheKey) {
        CacheKey mutex = interner.intern(cacheKey); // Ensure that the mutex used in the next line is the same for objects that are equal.
        synchronized(mutex) {
            LockAndCount lac = locks.get(mutex);
            if (lac == null) {
                throw new IllegalStateException("No lock found for object " + mutex);
            }

            if (lac.count <= 1) {
                locks.remove(mutex);
            }
            else {
                lac.count--;
            }

            return lac.lock;
        }
    }


    private class LocalLock implements Lock {
        private final CacheKey key;

        LocalLock(CacheKey key) {
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
        public void lockInterruptibly() throws InterruptedException {
            ReentrantLock lock = lookupLockForAcquire(key);
            lock.lockInterruptibly();
        }

        @Nonnull
        @Override
        public Condition newCondition(){
            ReentrantLock lock = lookupLockForAcquire(key);
            return lock.newCondition();
        }

        @Override
        public boolean tryLock() {
            ReentrantLock lock = lookupLockForAcquire(key);
            return lock.tryLock();
        }

        @Override
        public boolean tryLock(long time, @Nonnull TimeUnit unit) throws InterruptedException {
            ReentrantLock lock = lookupLockForAcquire(key);
            return lock.tryLock(time, unit);
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

    /**
     * A key of a cache, namespaced by the cache that it belongs to.
     */
    private static class CacheKey {
        final String cacheName;
        final Object key;

        private CacheKey(Cache cache, Object key) {
            this.cacheName = cache.getName();
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return cacheName.equals(cacheKey.cacheName) && key.equals(cacheKey.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cacheName, key);
        }
    }
}
