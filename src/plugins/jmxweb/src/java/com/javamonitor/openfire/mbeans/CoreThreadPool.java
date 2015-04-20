package com.javamonitor.openfire.mbeans;

import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.EXECUTOR_FILTER_NAME;

import java.util.concurrent.ThreadPoolExecutor;

//import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.management.MINAStatCollector;
import org.apache.mina.transport.socket.SocketAcceptor;
//import org.apache.mina.transport.socket.nio.SocketAcceptor;

/**
 * A core thread pool mbean implementation.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CoreThreadPool implements CoreThreadPoolMBean {
    private final ThreadPoolExecutor executor;

    // TODO: replace with IoServiceMBean after updating MINA.
    private MINAStatCollector mina;

    /**
     * Create a new thread pool monitor mbean, giving it the pool to attach to.
     *
     * @param acceptor
     *            The pool to attach to.
     */
    public CoreThreadPool(final SocketAcceptor acceptor) {
        if (acceptor == null) {
            throw new NullPointerException("acceptor is null");
        }

        //ExecutorThreadModel threadModel = (ExecutorThreadModel) acceptor.getDefaultConfig().getThreadModel();
        //this.executor = (ThreadPoolExecutor) threadModel.getExecutor();

        final ExecutorFilter executorFilter = (ExecutorFilter) acceptor.getFilterChain().get(EXECUTOR_FILTER_NAME);
        this.executor = (ThreadPoolExecutor) executorFilter.getExecutor();
        this.mina = new MINAStatCollector(acceptor);
    }

    /**
     * Start collecting statistics from this pool.
     */
    public void start() {
        mina.start();
    }

    /**
     * Stop collecting statistics from this pool.
     */
    public void stop() {
        mina.stop();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getActiveCount()
     */
    public int getActiveCount() {
        return executor.getActiveCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getCompletedTaskCount()
     */
    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getCorePoolSize()
     */
    public int getCorePoolSize() {
        return executor.getCorePoolSize();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getLargestPoolSize()
     */
    public int getLargestPoolSize() {
        return executor.getLargestPoolSize();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMaximumPoolSize()
     */
    public int getMaximumPoolSize() {
        return executor.getMaximumPoolSize();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getPoolSize()
     */
    public int getPoolSize() {
        return executor.getPoolSize();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getQueueSize()
     */
    public int getQueueSize() {
        return executor.getQueue().size();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getTaskCount()
     */
    public long getTaskCount() {
        return executor.getTaskCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaBytesRead()
     */
    public long getMinaBytesRead() {
        return mina.getBytesRead();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaBytesWritten()
     */
    public long getMinaBytesWritten() {
        return mina.getBytesWritten();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaMsgRead()
     */
    public long getMinaMsgRead() {
        return mina.getMsgRead();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaMsgWritten()
     */
    public long getMinaMsgWritten() {
        return mina.getMsgWritten();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaQueuedEvents()
     */
    public long getMinaQueuedEvents() {
        return mina.getQueuedEvents();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaScheduledWrites()
     */
    public long getMinaScheduledWrites() {
        return mina.getScheduledWrites();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaSessionCount()
     */
    public long getMinaSessionCount() {
        return mina.getSessionCount();
    }

    /**
     * @see com.javamonitor.openfire.mbeans.CoreThreadPoolMBean#getMinaTotalProcessedSessions()
     */
    public long getMinaTotalProcessedSessions() {
        return mina.getTotalProcessedSessions();
    }
}
