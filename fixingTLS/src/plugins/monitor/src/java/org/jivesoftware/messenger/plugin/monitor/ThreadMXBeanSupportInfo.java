/**
 * 
 */
package org.jivesoftware.messenger.plugin.monitor;

import java.lang.management.ThreadMXBean;

import org.jrobin.annotations.Arc;
import org.jrobin.annotations.Archives;
import org.jrobin.annotations.ConsolidationFunction;
import org.jrobin.annotations.Ds;
import org.jrobin.annotations.Rrd;

/**
 * @author Noah Campbell
 * @version 1.0
 */
@Rrd
@Archives({@Arc(consolidationFunction=ConsolidationFunction.AVERAGE, xff=0.5, steps=6, rows=700)})
public interface ThreadMXBeanSupportInfo {
    @Ds(name="cputime", expr="CurrentThreadCpuTime" )
    long getCurrentThreadCpuTime();
}
