/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util.cache;

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
    private Map<Object, LockAndCount> locks = new ConcurrentHashMap<Object, LockAndCount>();

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

    public Lock getLock(Object key, Cache cache) {
        Object lockKey = key;
        if (key instanceof String) {
            lockKey = ((String) key).intern();
        }

		return new LocalLock(lockKey);
    }

	private void acquireLock(Object key) {
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

		public void lock(){
			acquireLock(key);
		}

		public void	unlock() {
			releaseLock(key);
		}

        public void	lockInterruptibly(){
			throw new UnsupportedOperationException();
		}

		public Condition newCondition(){
			throw new UnsupportedOperationException();
		}

		public boolean 	tryLock() {
			throw new UnsupportedOperationException();
		}

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
}
