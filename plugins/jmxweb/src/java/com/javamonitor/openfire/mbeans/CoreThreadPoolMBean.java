package com.javamonitor.openfire.mbeans;

/**
 * MBean definition for collectors that gather statistics of one of the core
 * thread pools in use by the server.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public interface CoreThreadPoolMBean {
    /**
     * @see java.util.concurrent.ThreadPoolExecutor#getCorePoolSize()
     * @return the core number of threads
     */
    int getCorePoolSize();

    int getMaximumPoolSize();

    int getActiveCount();

    int getQueueSize();

    long getCompletedTaskCount();

    int getLargestPoolSize();

    int getPoolSize();

    long getTaskCount();

    long getMinaBytesRead();

    long getMinaBytesWritten();

    long getMinaMsgRead();

    long getMinaMsgWritten();

    long getMinaQueuedEvents();

    long getMinaScheduledWrites();

    long getMinaSessionCount();

    long getMinaTotalProcessedSessions();
}
