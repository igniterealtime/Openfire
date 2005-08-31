/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output.io.rotate;

import java.io.File;

/**
 * rotation stragety based when log writting started.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class RotateStrategyByTime
        implements RotateStrategy {
    ///time interval when rotation is triggered.
    private long m_timeInterval;

    ///time when logging started.
    private long m_startingTime;

    ///rotation count.
    private long m_currentRotation;

    /**
     * Rotate logs by time.
     * By default do log rotation every 24 hours
     */
    public RotateStrategyByTime() {
        this(1000 * 60 * 60 * 24);
    }

    /**
     * Rotate logs by time.
     *
     * @param timeInterval rotate after time-interval [ms] has expired
     */
    public RotateStrategyByTime(final long timeInterval) {
        m_startingTime = System.currentTimeMillis();
        m_currentRotation = 0;
        m_timeInterval = timeInterval;
    }

    /**
     * reset interval history counters.
     */
    public void reset() {
        m_startingTime = System.currentTimeMillis();
        m_currentRotation = 0;
    }

    /**
     * Check if now a log rotation is neccessary.
     * If
     * <code>(current_time - m_startingTime) / m_timeInterval &gt; m_currentRotation </code>
     * rotation is needed.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        final long newRotation =
                (System.currentTimeMillis() - m_startingTime) / m_timeInterval;

        if (newRotation > m_currentRotation) {
            m_currentRotation = newRotation;
            return true;
        }
        else {
            return false;
        }
    }
}


