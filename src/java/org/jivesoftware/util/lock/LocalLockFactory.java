/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util.lock;

import org.jivesoftware.util.TaskEngine;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

/**
 * LockFactory to be used when not running in cluster mode. The locks returned by this
 * factory are only visibile within this JVM.
 *
 * @author Gaston Dombiak
 */
public class LocalLockFactory implements LockFactory {

    private Map<Object, ReentrantLock> locks = new ConcurrentHashMap<Object, ReentrantLock>();
	private Map<Object, Integer> counts = new ConcurrentHashMap<Object, Integer>();

    public Lock getLock(Object key) {
        Object lockKey = key;
        if (key instanceof String) {
            lockKey = ((String) key).intern();
        }

		return new LocalLock(lockKey);
    }

	private void acquireLock(Object key) {
		ReentrantLock lock;
		synchronized (key) {
			lock = lookupLockForAcquire(key);
		}
		lock.lock();
	}

	private void releaseLock(Object key) {
		ReentrantLock lock;
		synchronized (key) {
			lock = lookupLockForRelease(key);
			if (lock.getHoldCount() <= 1 && !counts.containsKey(key)) {
				locks.remove(key);
			}
		}
		lock.unlock();
	}

	private ReentrantLock lookupLockForAcquire(Object key) {
		ReentrantLock lock = locks.get(key);
		if (lock == null) {
			lock = new ReentrantLock();
			locks.put(key, lock);
		}

		Integer count = counts.get(key);
		if (count == null) {
			counts.put(key, 1);
		}
		else {
			counts.put(key, ++count);
		}

		return lock;
	}

	private ReentrantLock lookupLockForRelease(Object key) {
		ReentrantLock lock = locks.get(key);
		if (lock == null) {
			throw new IllegalStateException("No lock found for object " + key);
		}

		Integer count = counts.get(key);
		if (count == null) {
			throw new IllegalStateException("No count found for object " + key);
		}

		if (count == 1) {
			counts.remove(key);
		}
		else {
			counts.put(key, --count);
		}

		return lock;
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
}
