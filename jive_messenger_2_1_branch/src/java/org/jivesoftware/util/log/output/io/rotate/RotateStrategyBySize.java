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
 * Rotation stragety based on size written to log file.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class RotateStrategyBySize
        implements RotateStrategy {
    private long m_maxSize;
    private long m_currentSize;

    /**
     * Rotate logs by size.
     * By default do log rotation after writing approx. 1MB of messages
     */
    public RotateStrategyBySize() {
        this(1024 * 1024);
    }

    /**
     * Rotate logs by size.
     *
     * @param maxSize rotate after writing max_size [byte] of messages
     */
    public RotateStrategyBySize(final long maxSize) {
        m_currentSize = 0;
        m_maxSize = maxSize;
    }

    /**
     * reset log size written so far.
     */
    public void reset() {
        m_currentSize = 0;
    }

    /**
     * Check if now a log rotation is neccessary.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        m_currentSize += data.length();
        if (m_currentSize >= m_maxSize) {
            m_currentSize = 0;
            return true;
        }
        else {
            return false;
        }
    }
}

