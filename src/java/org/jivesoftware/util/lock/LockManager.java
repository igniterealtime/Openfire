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

import java.util.concurrent.locks.Lock;

/**
 * Manager of {@link Lock Locks} that could be valid when running within a cluster or when in local mode.
 * By default the LockManager will use a {@link org.jivesoftware.util.lock.LocalLockFactory} but
 * you can set new factories by sending {@link #setLockFactory(LockFactory)}.
 *
 * @author Gaston Dombiak
 */
public class LockManager {

    private static LockFactory lockFactory;

    static {
        setLockFactory(new LocalLockFactory());
    }

    /**
     * Returns the existing lock factory being used for creating new Locks.
     *
     * @return the existing lock factory being used for creating new Locks.
     */
    public static LockFactory getLockFactory() {
        return lockFactory;
    }

    /**
     * Sets the lock factory to use for creating new Locks. If <tt>null</tt> then
     * use {@link LocalLockFactory}.
     *
     * @param lockFactory the new lock factory to use for creating new Locks.
     */
    public static void setLockFactory(LockFactory lockFactory) {
        // Shutdown old factory
        if (LockManager.lockFactory != null) {
            LockManager.lockFactory.shutdown();
        }
        LockManager.lockFactory = lockFactory;
        // Start new factory
        if (LockManager.lockFactory != null) {
            LockManager.lockFactory.start();
        }
    }

    /**
     * Returns an existing {@link Lock} on the specified key or creates a new one if none was found. This
     * operation should be thread safe. Successive calls with the same key may or may not return
     * the same {@link Lock}. However, different threads asking for the same Lock at the same time will
     * get the same Lock object.
     *
     * @param key the object that defines the visibility or scope of the lock.
     * @return an existing lock on the specified key or creates a new one if none was found.
     */
    public static Lock getLock(Object key) {
        return lockFactory.getLock(key);
    }
}
