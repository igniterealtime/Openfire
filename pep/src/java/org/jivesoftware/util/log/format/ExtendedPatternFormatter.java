/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.format;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.log.ContextMap;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.util.StackIntrospector;

/**
 * Formatter especially designed for debugging applications.
 * <p/>
 * This formatter extends the standard PatternFormatter to add
 * two new possible expansions. These expansions are %{method}
 * and %{thread}. In both cases the context map is first checked
 * for values with specified key. This is to facilitate passing
 * information about caller/thread when threads change (as in
 * AsyncLogTarget). They then attempt to determine appropriate
 * information dynamically.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @version CVS $Revision$ $Date$
 */
public class ExtendedPatternFormatter extends PatternFormatter {
    private final static int TYPE_METHOD = MAX_TYPE + 1;
    private final static int TYPE_THREAD = MAX_TYPE + 2;

    private final static String TYPE_METHOD_STR = "method";
    private final static String TYPE_THREAD_STR = "thread";

    public ExtendedPatternFormatter(final String format) {
        super(format);
    }

    /**
     * Retrieve the type-id for a particular string.
     *
     * @param type the string
     * @return the type-id
     */
    protected int getTypeIdFor(final String type) {
        if (type.equalsIgnoreCase(TYPE_METHOD_STR))
            return TYPE_METHOD;
        else if (type.equalsIgnoreCase(TYPE_THREAD_STR))
            return TYPE_THREAD;
        else {
            return super.getTypeIdFor(type);
        }
    }

    /**
     * Formats a single pattern run (can be extended in subclasses).
     *
     * @param run the pattern run to format.
     * @return the formatted result.
     */
    protected String formatPatternRun(final LogEvent event, final PatternRun run) {
        switch (run.m_type) {
            case TYPE_METHOD:
                return getMethod(event, run.m_format);
            case TYPE_THREAD:
                return getThread(event, run.m_format);
            default:
                return super.formatPatternRun(event, run);
        }
    }

    /**
     * Utility method to format category.
     *
     * @param event
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    private String getMethod(final LogEvent event, final String format) {
        final ContextMap map = event.getContextMap();
        if (null != map) {
            final Object object = map.get("method");
            if (null != object) {
                return object.toString();
            }
        }

//        final String result = StackIntrospector.getCallerMethod(Logger.class);
        final String result = StackIntrospector.getCallerMethod(Log.class);
        if (null == result) {
            return "UnknownMethod";
        }
        return result;
    }

    /**
     * Utility thread to format category.
     *
     * @param event
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    private String getThread(final LogEvent event, final String format) {
        final ContextMap map = event.getContextMap();
        if (null != map) {
            final Object object = map.get("thread");
            if (null != object) {
                return object.toString();
            }
        }

        return Thread.currentThread().getName();
    }
}
