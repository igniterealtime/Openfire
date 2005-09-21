/**
 * 
 */
package org.jivesoftware.messenger.plugin.meter.accumulator;

import javax.management.ObjectName;

/**
 * @author Noah Campbell
 * @version 1.0
 */
public interface AccumulatorMBean {
    String getPath();
    ObjectName getSourceMBean();
    long getTotalReads();
}
