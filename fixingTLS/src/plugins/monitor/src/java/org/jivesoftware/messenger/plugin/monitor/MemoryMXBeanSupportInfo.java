/**
 * 
 */
package org.jivesoftware.messenger.plugin.monitor;

import org.jrobin.annotations.Arc;
import org.jrobin.annotations.Archives;
import org.jrobin.annotations.ConsolidationFunction;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;
import org.jrobin.annotations.SourceType;

/**
 * @author Noah Campbell
 * @version 1.0
 */
@Rrd
@Archives({@Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=6, rows=700)})
public interface MemoryMXBeanSupportInfo {

    @Ds(name="max", expr="HeapMemoryUsage.max", type=SourceType.COUNTER)
    long getMaxHeapMemoryUsage();
    
    @Ds(name="init", expr="HeapMemoryUsage.init")
    long getInitHeapMemoryUsage();
    
    @Ds(name="used", expr="HeapMemoryUsage.used")
    long getUsedHeapMemoryUsage();
    
    @Ds(name="commited", expr="HeapMemoryUsage.committed")
    long getCommittedHeapMemoryUsage();
    
}
