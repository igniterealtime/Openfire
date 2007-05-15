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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * LockFactory to be used when not running in cluster mode. The locks returned by this
 * factory are only visibile within this JVM.
 *
 * @author Gaston Dombiak
 */
public class LocalLockFactory implements LockFactory {

    private TimerTask maintenanceTask;
    private Map<Object, WeakReference<Lock>> locks = new ConcurrentHashMap<Object, WeakReference<Lock>>();

    public Lock getLock(Object key) {
        WeakReference<Lock> lockRef;
        Lock lock;
        Object lockKey = key;
        if (key instanceof String) {
            lockKey = ((String) key).intern();
        }
        synchronized (lockKey) {
            lockRef = locks.get(key);
            lock = lockRef != null ? lockRef.get() : null;
            if (lockRef == null || lock == null) {
                lock = new ReentrantLock(true);
                lockRef = new WeakReference<Lock>(lock);
                locks.put(key, lockRef);
            }
        }
        return lock;
    }

    public void start() {
        // Remove entries in the locks Map that are no longer used
        maintenanceTask = new TimerTask() {
            public void run() {
                for (Map.Entry<Object, WeakReference<Lock>> entry : locks.entrySet()) {
                    if (entry.getValue().get() == null) {
                        locks.remove(entry.getKey());
                    }
                }
            }
        };
        TaskEngine.getInstance().scheduleAtFixedRate(maintenanceTask, 30000, 60000);
    }

    public void shutdown() {
        TaskEngine.getInstance().cancelScheduledTask(maintenanceTask);
        // Clean up existing locks
        locks.clear();
    }
}
