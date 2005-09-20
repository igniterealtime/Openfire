/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter;

import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;
import org.jrobin.annotations.SourceType;

/**
 * @author Noah Campbell
 * @version 1.0
 */
@Rrd
public interface MemoryMXBeanSupportInfo {

    
    
    
    /**
     * @return max The max heap memory usage.
     */
    @Ds(name="max", expr="/HeapMemoryUsage/max", type=SourceType.COUNTER)
    long getMaxHeapMemoryUsage();
    
    
    /**
     * @return initial The initial heap memory usage.
     */
    @Ds(name="init", expr="/HeapMemoryUsage/init")
    long getInitHeapMemoryUsage();
    
    
    /**
     * @return used The heap memory usage.
     */
    @Ds(name="used", expr="/HeapMemoryUsage/used")
    long getUsedHeapMemoryUsage();
    
    
    /**
     * @return commited The commited heap memory usage.
     */
    @Ds(name="commited", expr="/HeapMemoryUsage/committed")
    long getCommittedHeapMemoryUsage();
    
}
