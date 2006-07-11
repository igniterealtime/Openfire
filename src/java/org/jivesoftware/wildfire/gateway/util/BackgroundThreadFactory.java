/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.util;

import java.util.concurrent.ThreadFactory;

/**
 * Generates threads that are of low priority and daemon.
 * 
 * @author Noah Campbell
 */
public class BackgroundThreadFactory implements ThreadFactory {

    
    /** The background thread group. */
    private static ThreadGroup group = new ThreadGroup("background");
    
    /**
     * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
     */
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(group, r);
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    }


}
