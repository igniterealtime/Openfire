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
 * Hierarchical Rotation stragety.
 * This object is initialised with several rotation strategy objects.
 * The <code>isRotationNeeded</code> method checks the first rotation
 * strategy object. If a rotation is needed, this result is returned.
 * If not the next rotation strategy object is asked and so on.
 *
 * @author <a href="mailto:cziegeler@apache.org">Carsten Ziegeler</a>
 */
public class OrRotateStrategy
        implements RotateStrategy {
    private RotateStrategy[] m_strategies;

    /**
     * The rotation strategy used. This marker is required for the reset()
     * method.
     */
    private int m_usedRotation = -1;

    /**
     * Constructor
     */
    public OrRotateStrategy(final RotateStrategy[] strategies) {
        this.m_strategies = strategies;
    }

    /**
     * reset.
     */
    public void reset() {
        if (-1 != m_usedRotation) {
            m_strategies[m_usedRotation].reset();
            m_usedRotation = -1;
        }
    }

    /**
     * check if now a log rotation is neccessary.
     * This object is initialised with several rotation strategy objects.
     * The <code>isRotationNeeded</code> method checks the first rotation
     * strategy object. If a rotation is needed, this result is returned.
     * If not the next rotation strategy object is asked and so on.
     *
     * @param data the last message written to the log system
     * @return boolean return true if log rotation is neccessary, else false
     */
    public boolean isRotationNeeded(final String data, final File file) {
        m_usedRotation = -1;

        if (null != m_strategies) {
            final int length = m_strategies.length;
            for (int i = 0; i < length; i++) {
                if (true == m_strategies[i].isRotationNeeded(data, file)) {
                    m_usedRotation = i;
                    return true;
                }
            }
        }

        return false;
    }
}

