/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.net;

import java.util.Date;

/**
 * Monitors a network usage. Monitors measure sample totals, and sample rate
 * (samples per second). Monitor information is used at runtime to adjust
 * server behavior as well as to generate reports on server history.<p>
 *
 * Based in part on the DataMonitor from Java Distributed Computing, Jim Farley,
 * O'Reilly.
 *
 * @author Iain Shigeoka
 */
public interface Monitor {

    /**
     * Add the number of samples that occured between the given start and
     * end times.<p>
     *
     * The monitor does not check for overlapping sample times. It is
     * the caller's responsibility to ensure samples don't overlap. Failure
     * to prevent overlapping sample times may result in elapsed time
     * being falsely counted and reported.
     *
     * @param quantity The number of samples that occurred during the sample period
     * @param startTime The beginning of the sample period
     * @param endTime The end of the sample period
     */
    void addSample(long quantity, Date startTime, Date endTime);

    /**
     * <p>Add the number of samples that occured between the last sample date,
     * and the current time.</p>
     *
     * <p>A convenience method when samples occur in sequential periods.
     * Equivalent to:</p>
     * <pre><code>
     * monitor.addSample(quantity, monitor.getLastSampleDate(), new Date());
     * </code></pre>
     *
     * @param quantity The number of samples that occurred between the
     * last sample date and now
     */
    void addSample(long quantity);

    /**
     * <p>Obtain the total number of samples reported during the monitor's lifetime.</p>
     *
     * @return The total number of samples reported to the monitor
     */
    long getTotal();

    /**
     * <p>Obtain the total amount of time (in milliseconds) that the monitor
     * has samples for.</p>
     *
     * @return The total time (in milliseconds) samples have been recorded for
     */
    long getTotalTime();

    /**
     * <p>Obtain the rate of samples reported during the monitor's lifetime.</p>
     *
     * @return The average rate of samples reported to the monitor
     */
    float getRate();

    /**
     * <p>The date-time of the first sample reported to the monitor.</p>
     *
     * @return The date-time of the first sample reported to the monitor
     */
    Date getFirstSampleDate();

    /**
     * <p>The date-time of the last sample reported to the monitor.</p>
     *
     * @return The date-time of the last sample reported to the monitor
     */
    Date getLastSampleDate();

    /**
     * <p>The size of the moving frame (in sample events)
     * that provides a recent view of the data.</p>
     *
     * <p>Samples can be monitored and managed based on
     * the usage within the most recent number of samples reported
     * during the frame. Larger frame sizes 'smooths' the results
     * creating frame statistics that are less affected by outlying samples but
     * requiring larger amounts of memory to store and more resources to calculate
     * frame statistics.</p>
     *
     * @return The sample frame size in seconds
     */
    int getFrameSize();

    /**
     * <p>Sets the size of the moving frame (in sample events).</p>
     *
     * <p>Changing the frame size to a larger value will not automatically
     * include past samples that were previously outside the frame. Instead,
     * the monitor will not return accurate frame related data until the
     * new frame is filled with new data.</p>
     *
     * <p>Warning: Larger frame sizes consume larger amounts of memory
     * per monitor and increases the amount of work required to generate
     * frame statistics. Set the framesize to the smallest useful size or zero
     * to not record any frame related data.</p>
     *
     * @param frameSize The new size of the sample frame in seconds
     */
    void setFrameSize(int frameSize);

    /**
     * <p>Obtain the sample total during the frame.</p>
     *
     * @return The sample total during the frame.
     */
    long getFrameTotal();

    /**
     * <p>Obtain the total sample time during the frame.</p>
     *
     * @return The total sample time during the frame.
     */
    long getFrameTotalTime();

    /**
     * <p>Obtain the number of bytes read during the frame.</p>
     *
     * @return The number of bytes read during the frame.
     */
    float getFrameRate();
}
