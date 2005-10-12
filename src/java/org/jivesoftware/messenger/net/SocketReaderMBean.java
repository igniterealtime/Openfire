/**
 * 
 */
package org.jivesoftware.messenger.net;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public interface SocketReaderMBean {

    
    /**
     * The total SocketReaders in this jvm.
     * @return total
     */
    int getConcurrent();
    
    /**
     * The aver connection time for this socket reader
     * @return avgConnTime
     */
    double getMeanConnectionTime();
    
    /**
     * Return the min connection time.
     * 
     * @return min
     */
    public double getMinConnectionTime();
    
    /**
     * Return the max connection time.
     * 
     * @return max
     */
    public double getMaxConnectionTime();
    
    /**
     * Return the 95th percentile connection time (trims out spurrious data).
     * 
     * @return value
     */
    public double get95thPercentile();
}
