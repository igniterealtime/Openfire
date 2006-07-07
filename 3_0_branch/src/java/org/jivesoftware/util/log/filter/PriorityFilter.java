/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.filter;

import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.Priority;

/**
 * Filters log events based on priority.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class PriorityFilter extends AbstractFilterTarget {

    ///Priority to filter against
    private Priority m_priority;

    /**
     * Constructor that sets the priority that is filtered against.
     *
     * @param priority the Priority
     */
    public PriorityFilter(final Priority priority) {
        m_priority = priority;
    }

    /**
     * Set priority used to filter.
     *
     * @param priority the priority to filter on
     */
    public void setPriority(final Priority priority) {
        m_priority = priority;
    }

    /**
     * Filter the log event based on priority.
     * <p/>
     * If LogEvent has a Lower priroity then discard it.
     *
     * @param event the event
     * @return return true to discard event, false otherwise
     */
    protected boolean filter(final LogEvent event) {
        return (!m_priority.isLower(event.getPriority()));
    }
}
