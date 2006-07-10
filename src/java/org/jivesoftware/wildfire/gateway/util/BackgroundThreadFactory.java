/**
 * 
 */
package org.jivesoftware.wildfire.gateway.util;

import java.util.concurrent.ThreadFactory;

/**
 * Generates threads that are of low priority and daemon.
 * 
 * @author Noah Campbell
 * @version 1.0
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
