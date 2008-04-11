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

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.JiveGlobals;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool to be used for processing incoming packets when using non-blocking
 * connections.
 *
 * // TODO Change thead pool configuration. Would be nice to have something that can be
 * // TODO dynamically adjusted to demand and circumstances.
 *
 * @author Daniele Piras
 */
class IOExecutor {

    // SingleTon ...
    protected static IOExecutor instance = new IOExecutor();

    // Pool obj
    protected ThreadPoolExecutor executeMsgPool;

    // Internal queue for the pool
    protected LinkedBlockingQueue<Runnable> executeQueue;


    /*
    * Simple constructor that initialize the main executor structure.
    *
    */
    protected IOExecutor() {
        // Read poolsize parameter...
        int poolSize = JiveGlobals.getIntProperty("tiscali.pool.size", 15);
        // Create queue for executor
        executeQueue = new LinkedBlockingQueue<Runnable>();
        // Create executor
        executeMsgPool =
                new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, executeQueue);
    }

    public static void execute(Runnable task) {
        instance.executeMsgPool.execute(task);
    }

}
