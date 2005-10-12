/**
 * 
 */
package org.jivesoftware.messenger.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public class SocketReaderObserver implements SocketReaderMBean, Runnable {

    
    /** The ds. */
    private DescriptiveStatistics ds = DescriptiveStatistics.newInstance();
    
    /** The des. */
    private ScheduledExecutorService des = Executors.newScheduledThreadPool(1);
    
    /**
     * Construct a new <code>SocketReaderObserver</code>.
     *
     */
    private SocketReaderObserver() {
        des.scheduleAtFixedRate(this, 0, 500, TimeUnit.MILLISECONDS);
    }
    
    /** The INSTANCE. */
    private static final SocketReaderObserver INSTANCE = new SocketReaderObserver();
    
    /**
     * @return observer
     */
    public static SocketReaderObserver getInstance() {
        return INSTANCE;
    }
    
    /**
     * @see org.jivesoftware.messenger.net.SocketReaderMBean#getConcurrent()
     */
    public int getConcurrent() {
        return SocketReader.concurrent;
    }

    /**
     * @see org.jivesoftware.messenger.net.SocketReaderMBean#getMeanConnectionTime()
     */
    public double getMeanConnectionTime() {
        return ds.getMean();
    }
    
    
    /**
     * @return
     */
    public double getMinConnectionTime() {
        return ds.getMin();
    }
    
    
    /**
     * @return
     */
    public double getMaxConnectionTime() {
        return ds.getMax();
    }
    
    
    /**
     * @return 
     */
    public double get95thPercentile() {
        return ds.getPercentile(95);
    }
    
    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {

        if(SocketReader.statsOutboundQueue.peek() != null) {
            Long l = SocketReader.statsOutboundQueue.poll();
            ds.addValue(l.doubleValue());
        }
       
        
    }

}
