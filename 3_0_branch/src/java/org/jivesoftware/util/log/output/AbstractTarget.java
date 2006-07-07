/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output;

import org.jivesoftware.util.log.ErrorAware;
import org.jivesoftware.util.log.ErrorHandler;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.LogTarget;

/**
 * Abstract target.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public abstract class AbstractTarget implements LogTarget, ErrorAware {

    ///ErrorHandler used by target to delegate Error handling
    private ErrorHandler m_errorHandler;

    ///Flag indicating that log session is finished (aka target has been closed)
    private boolean m_isOpen;

    /**
     * Provide component with ErrorHandler.
     *
     * @param errorHandler the errorHandler
     */
    public synchronized void setErrorHandler(final ErrorHandler errorHandler) {
        m_errorHandler = errorHandler;
    }

    protected synchronized boolean isOpen() {
        return m_isOpen;
    }

    /**
     * Startup log session.
     */
    protected synchronized void open() {
        if (!isOpen()) {
            m_isOpen = true;
        }
    }

    /**
     * Process a log event, via formatting and outputting it.
     *
     * @param event the log event
     */
    public synchronized void processEvent(final LogEvent event) {
        if (!isOpen()) {
            getErrorHandler().error("Writing event to closed stream.", null, event);
            return;
        }

        try {
            doProcessEvent(event);
        }
        catch (final Throwable throwable) {
            getErrorHandler().error("Unknown error writing event.", throwable, event);
        }
    }

    /**
     * Process a log event, via formatting and outputting it.
     * This should be overidden by subclasses.
     *
     * @param event the log event
     */
    protected abstract void doProcessEvent(LogEvent event)
            throws Exception;

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        if (isOpen()) {
            m_isOpen = false;
        }
    }

    /**
     * Helper method to retrieve ErrorHandler for subclasses.
     *
     * @return the ErrorHandler
     */
    protected final ErrorHandler getErrorHandler() {
        return m_errorHandler;
    }

    /**
     * Helper method to send error messages to error handler.
     *
     * @param message   the error message
     * @param throwable the exception if any
     * @deprecated Use getErrorHandler().error(...) directly
     */
    protected final void error(final String message, final Throwable throwable) {
        getErrorHandler().error(message, throwable, null);
    }
}
