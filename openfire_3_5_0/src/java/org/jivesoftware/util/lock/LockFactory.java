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
 * Factory that creates new Locks for specified keys and keeps them in memory until they
 * are no longer needed.
 *
 * @author Gaston Dombiak
 */
public interface LockFactory {

    /**
     * Returns an existing lock on the specified key or creates a new one if none was found. This
     * operation should be thread safe.
     *
     * @param key the object that defines the visibility or scope of the lock.
     * @return an existing lock on the specified key or creates a new one if none was found.
     */
    Lock getLock(Object key);
}
