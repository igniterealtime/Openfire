package org.apache.mina.management;

import org.apache.mina.common.*;
import org.apache.mina.filter.executor.ExecutorFilter;

import java.net.SocketAddress;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects statistics of an {@link org.apache.mina.common.IoService}. It's polling all the sessions of a given
 * IoService. It's attaching a {@link org.apache.mina.management.IoSessionStat} object to all the sessions polled
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
 * @version $Rev: 477648 $, $Date: 2006-11-21 04:33:38 -0800 (Tue, 21 Nov 2006) $
 */
public class MINAStatCollector {
    /**
     * The session attribute key for {@link org.apache.mina.management.IoSessionStat}.
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
        public void serviceActivated( IoService service, SocketAddress serviceAddress, IoHandler handler,
            IoServiceConfig config )
        {
        }

        public void serviceDeactivated( IoService service, SocketAddress serviceAddress, IoHandler handler,
            IoServiceConfig config )
        {
        }

        public void sessionCreated( IoSession session )
        {
            addSession( session );
        }

        public void sessionDestroyed( IoSession session )
        {
            removeSession( session );
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
     * Start collecting stats for the {@link org.apache.mina.common.IoSession} of the service.
     * New sessions or destroyed will be automaticly added or removed.
     */
    public void start()
    {
        synchronized (this)
        {
            if ( worker != null && worker.isAlive() )
                throw new RuntimeException( "Stat collecting already started" );

            // add all current sessions

            polledSessions = new ConcurrentLinkedQueue<IoSession>();

            Set<SocketAddress> addresses = service.getManagedServiceAddresses();
            if (addresses != null) {
                for (SocketAddress element : addresses) {
                    for (IoSession ioSession : service.getManagedSessions(element)) {
                        addSession(ioSession);
                    }
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
     * Stop collecting stats. all the {@link org.apache.mina.management.IoSessionStat} object will be removed of the
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
     * is the stat collector started and polling the {@link org.apache.mina.common.IoSession} of the {@link org.apache.mina.common.IoService}
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
        IoSessionStat sessStat = ( IoSessionStat ) session.getAttribute( KEY );
        session.removeAttribute( KEY );

        totalMsgWritten.addAndGet(session.getWrittenMessages() - sessStat.lastMessageWrite);
        totalMsgRead.addAndGet(session.getReadMessages() - sessStat.lastMessageRead);
        totalBytesWritten.addAndGet(session.getWrittenBytes() - sessStat.lastByteWrite);
        totalBytesRead.addAndGet(session.getReadBytes() - sessStat.lastByteRead);
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
                }

                long tmpMsgWritten = 0l;
                long tmpMsgRead = 0l;
                long tmpBytesWritten = 0l;
                long tmpBytesRead = 0l;
                long tmpScheduledWrites = 0l;
                long tmpQueuevedEvents = 0l;

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
                    tmpScheduledWrites += session.getScheduledWriteRequests();

                    ExecutorFilter executorFilter =
                            (ExecutorFilter) session.getFilterChain().get(ExecutorThreadModel.class.getName());
                    if (executorFilter != null) {
                        tmpQueuevedEvents += executorFilter.getEventQueueSize(session);
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
}
