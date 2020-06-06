package org.apache.mina.management;

import static org.jivesoftware.openfire.spi.ConnectionManagerImpl.EXECUTOR_FILTER_NAME;

import org.apache.mina.core.service.IoService;
import org.apache.mina.core.service.IoServiceListener;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects statistics of an {@link IoService}. It's polling all the sessions of a given
 * IoService. It's attaching a {@link IoSessionStat} object to all the sessions polled
 * and filling the throughput values.
 *
 * Usage :
 * <pre>
 * IoService service = ...
 * MINAStatCollector collector = new MINAStatCollector( service );
 * collector.start();
 * </pre>
 *
 * By default the {@link org.apache.mina.management.MINAStatCollector} is polling the sessions every 5 seconds. You can
 * give a different polling time using a second constructor.<p>
 *
 * Note: This class is a spin-off from StatCollector present in
 * https://svn.apache.org/repos/asf/mina/branches/1.1/core/src/main/java/org/apache/mina/management.
 *
 * @author The Apache Directory Project (mina-dev@directory.apache.org)
 */
public class MINAStatCollector {

    private static final Logger Log = LoggerFactory.getLogger(MINAStatCollector.class);

    /**
     * The session attribute key for {@link IoSessionStat}.
     */
    public static final String KEY = MINAStatCollector.class.getName() + ".stat";


    /**
     * @noinspection StaticNonFinalField
     */
    private static volatile int nextId = 0;
    private final int id = nextId ++;

    private final IoService service;
    private Worker worker;
    private int pollingInterval = 5000;
    private Queue<IoSession> polledSessions;

    // resume of session stats, for simplifying acces to the statistics
    private AtomicLong totalProcessedSessions = new AtomicLong();
    private AtomicLong totalMsgWritten = new AtomicLong();
    private AtomicLong totalMsgRead = new AtomicLong();
    private AtomicLong totalBytesWritten = new AtomicLong();
    private AtomicLong totalBytesRead = new AtomicLong();
    private AtomicLong totalScheduledWrites = new AtomicLong();
    private AtomicLong totalQueuedEvents = new AtomicLong();

    private final IoServiceListener serviceListener = new IoServiceListener()
    {

        @Override
        public void sessionCreated(final IoSession session )
        {
            addSession( session );
        }

        @Override
        public void sessionDestroyed(final IoSession session )
        {
            removeSession( session );
        }

        @Override
        public void serviceActivated(final IoService service) {
        }

        @Override
        public void serviceIdle(final IoService service, final IdleStatus idleStatus) {
        }

        @Override
        public void serviceDeactivated(final IoService service) {
        }

        @Override
        public void sessionClosed(final IoSession ioSession) {
        }
    };

    /**
     * Create a stat collector for the given service with a default polling time of 5 seconds.
     * @param service the IoService to inspect
     */
    public MINAStatCollector( IoService service )
    {
        this( service,5000 );
    }

    /**
     * create a stat collector for the given given service
     * @param service the IoService to inspect
     * @param pollingInterval milliseconds
     */
    public MINAStatCollector( IoService service, int pollingInterval )
    {
        this.service = service;
        this.pollingInterval = pollingInterval;
    }

    /**
     * Start collecting stats for the {@link IoSession} of the service.
     * New sessions or destroyed will be automaticly added or removed.
     */
    public void start()
    {
        synchronized (this)
        {
            if ( worker != null && worker.isAlive() )
                throw new RuntimeException( "Stat collecting already started" );

            // add all current sessions

            polledSessions = new ConcurrentLinkedQueue<>();

            Map<Long, IoSession> sessions = service.getManagedSessions();
            if (sessions != null) {
                for (IoSession ioSession : sessions.values()) {
                    addSession(ioSession);
                }
            }

            // listen for new ones
            service.addListener( serviceListener );

            // start polling
            worker = new Worker();
            worker.start();

        }

    }

    /**
     * Stop collecting stats. all the {@link IoSessionStat} object will be removed of the
     * polled session attachements.
     */
    public void stop()
    {
        synchronized (this)
        {
            service.removeListener( serviceListener );

            // stop worker
            worker.stop = true;
            worker.interrupt();
            while( worker.isAlive() )
            {
                try
                {
                    worker.join();
                }
                catch( InterruptedException e )
                {
                    //ignore since this is shutdown time
                }
            }

            for (IoSession session : polledSessions) {
                session.removeAttribute(KEY);
            }
            polledSessions.clear();
        }
    }

    /**
     * is the stat collector started and polling the {@link IoSession} of the {@link IoService}
     * @return true if started
     */
    public boolean isRunning()
    {
        synchronized (this)
        {
            return worker != null && worker.stop != true;
        }
    }

    private void addSession( IoSession session )
    {
        IoSessionStat sessionStats = new IoSessionStat();
        sessionStats.lastPollingTime = System.currentTimeMillis();
        session.setAttribute( KEY, sessionStats );
        totalProcessedSessions.incrementAndGet();
        polledSessions.add( session );
    }

    private void removeSession( IoSession session )
    {
        // remove the session from the list of polled sessions
        polledSessions.remove( session );

        // add the bytes processed between last polling and session closing
        // prevent non seen byte with non-connected protocols like HTTP and datagrams
        IoSessionStat sessStat = ( IoSessionStat ) session.removeAttribute( KEY );

        if (sessStat != null) {
            totalMsgWritten.addAndGet(session.getWrittenMessages() - sessStat.lastMessageWrite);
            totalMsgRead.addAndGet(session.getReadMessages() - sessStat.lastMessageRead);
            totalBytesWritten.addAndGet(session.getWrittenBytes() - sessStat.lastByteWrite);
            totalBytesRead.addAndGet(session.getReadBytes() - sessStat.lastByteRead);
        }
    }


    /**
     * total number of sessions processed by the stat collector
     * @return number of sessions
     */
    public long getTotalProcessedSessions()
    {
        return totalProcessedSessions.longValue();
    }

    public long getBytesRead()
    {
        return totalBytesRead.get();
    }

    public long getBytesWritten()
    {
        return totalBytesWritten.get();
    }

    public long getMsgRead()
    {
        return totalMsgRead.get();
    }

    public long getMsgWritten()
    {
        return totalMsgWritten.get();
    }

    public long getScheduledWrites() {
        return totalScheduledWrites.get();
    }

    public long getQueuedEvents() {
        return totalQueuedEvents.get();
    }

    public long getSessionCount()
    {
        return polledSessions.size();
    }

    private class Worker extends Thread
    {

        boolean stop = false;

        private Worker()
        {
            super( "StatCollectorWorker-"+id );
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Thread#run()
         */
        @Override 
        public void run()
        {
            while ( !stop )
            {
                // wait polling time
                try
                {
                    Thread.sleep( pollingInterval );
                }
                catch ( InterruptedException e )
                {
                    Log.trace("Sleep interrupted");
                }

                long tmpMsgWritten = 0L;
                long tmpMsgRead = 0L;
                long tmpBytesWritten = 0L;
                long tmpBytesRead = 0L;
                long tmpScheduledWrites = 0L;
                long tmpQueuevedEvents = 0L;

                for (IoSession session : polledSessions)
                {
                    // upadating individual session statistics
                    IoSessionStat sessStat = ( IoSessionStat ) session.getAttribute( KEY );

                    long currentTimestamp = System.currentTimeMillis();
                    // Calculate delta
                    float pollDelta = (currentTimestamp - sessStat.lastPollingTime) / 1000f;
                    // Store last polling time of this session
                    sessStat.lastPollingTime = currentTimestamp;

                    long readBytes = session.getReadBytes();
                    long writtenBytes = session.getWrittenBytes();
                    long readMessages = session.getReadMessages();
                    long writtenMessages = session.getWrittenMessages();
                    sessStat.byteReadThroughput = (readBytes - sessStat.lastByteRead) / pollDelta;
                    sessStat.byteWrittenThroughput = (writtenBytes - sessStat.lastByteWrite) / pollDelta;
                    sessStat.messageReadThroughput = (readMessages - sessStat.lastMessageRead) / pollDelta;
                    sessStat.messageWrittenThroughput = (writtenMessages - sessStat.lastMessageWrite) / pollDelta;

                    tmpMsgWritten += (writtenMessages - sessStat.lastMessageWrite);
                    tmpMsgRead += (readMessages - sessStat.lastMessageRead);
                    tmpBytesWritten += (writtenBytes - sessStat.lastByteWrite);
                    tmpBytesRead += (readBytes - sessStat.lastByteRead);
                    tmpScheduledWrites += session.getScheduledWriteMessages();

                    ExecutorFilter executorFilter =
                            (ExecutorFilter) session.getFilterChain().get(EXECUTOR_FILTER_NAME);
                    if (executorFilter != null) {
                        Executor executor =  executorFilter.getExecutor();
                        if (executor instanceof OrderedThreadPoolExecutor) {
                            tmpQueuevedEvents += ((OrderedThreadPoolExecutor) executor).getActiveCount();
                        }
                    }

                    sessStat.lastByteRead = readBytes;
                    sessStat.lastByteWrite = writtenBytes;
                    sessStat.lastMessageRead = readMessages;
                    sessStat.lastMessageWrite = writtenMessages;

                }

                totalMsgWritten.addAndGet(tmpMsgWritten);
                totalMsgRead.addAndGet(tmpMsgRead);
                totalBytesWritten.addAndGet(tmpBytesWritten);
                totalBytesRead.addAndGet(tmpBytesRead);
                totalScheduledWrites.set(tmpScheduledWrites);
                totalQueuedEvents.set(tmpQueuevedEvents);
            }
        }
    }
    
    public class IoSessionStat {
        long lastByteRead = -1;

        long lastByteWrite = -1;

        long lastMessageRead = -1;

        long lastMessageWrite = -1;

        float byteWrittenThroughput = 0;

        float byteReadThroughput = 0;

        float messageWrittenThroughput = 0;

        float messageReadThroughput = 0;

        //  last time the session was polled
        long lastPollingTime = System.currentTimeMillis();

        /**
         * Bytes read per second  
         * @return bytes per second
         */
        public float getByteReadThroughput() {
            return byteReadThroughput;
        }

        /**
         * Bytes written per second  
         * @return bytes per second
         */
        public float getByteWrittenThroughput() {
            return byteWrittenThroughput;
        }

        /**
         * Messages read per second  
         * @return messages per second
         */
        public float getMessageReadThroughput() {
            return messageReadThroughput;
        }

        /**
         * Messages written per second  
         * @return messages per second
         */
        public float getMessageWrittenThroughput() {
            return messageWrittenThroughput;
        }

        /**
         * used for the StatCollector, last polling value 
         */
        long getLastByteRead() {
            return lastByteRead;
        }

        /**
         * used for the StatCollector, last polling value 
         */
        long getLastByteWrite() {
            return lastByteWrite;
        }

        /**
         * used for the StatCollector, last polling value 
         */
        long getLastMessageRead() {
            return lastMessageRead;
        }

        /**
         * used for the StatCollector, last polling value 
         */
        long getLastMessageWrite() {
            return lastMessageWrite;
        }
    }
}
