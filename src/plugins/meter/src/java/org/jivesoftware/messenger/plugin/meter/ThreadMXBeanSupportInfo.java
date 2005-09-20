/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;

/**
 * @author Noah Campbell
 * @version 1.0
 */
@Rrd
public interface ThreadMXBeanSupportInfo {
        
    
    /**
     * @return time The current thread cpu time.
     */
    @Ds(name="cputime", expr="/CurrentThreadCpuTime" )
    long getCurrentThreadCpuTime();
    
    
    /**
     * @return count The current thread count.
     */
    @Ds(name="threadCount", expr="/ThreadCount")
    long getThreadCount();
    
    
}
