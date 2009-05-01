/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.filter;

import org.jivesoftware.util.log.FilterTarget;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.LogTarget;

/**
 * Abstract implementation of FilterTarget.
 * A concrete implementation has to implement filter method.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public abstract class AbstractFilterTarget
        implements FilterTarget, LogTarget {
    //Log targets in filter chain
    private LogTarget m_targets[];

    /**
     * Add a new target to output chain.
     *
     * @param target the target
     */
    public void addTarget(final LogTarget target) {
        if (null == m_targets) {
            m_targets = new LogTarget[]{target};
        }
        else {
            final LogTarget oldTargets[] = m_targets;
            m_targets = new LogTarget[oldTargets.length + 1];
            System.arraycopy(oldTargets, 0, m_targets, 0, oldTargets.length);
            m_targets[m_targets.length - 1] = target;
        }
    }

    /**
     * Filter the log event.
     *
     * @param event the event
     * @return return true to discard event, false otherwise
     */
    protected abstract boolean filter(LogEvent event);

    /**
     * Process a log event
     *
     * @param event the log event
     */
    public void processEvent(final LogEvent event) {
        if (null == m_targets || filter(event))
            return;
        else {
            for (int i = 0; i < m_targets.length; i++) {
                m_targets[i].processEvent(event);
            }
        }
    }
}
