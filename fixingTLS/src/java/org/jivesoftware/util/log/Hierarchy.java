/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log;

import org.jivesoftware.util.log.format.PatternFormatter;
import org.jivesoftware.util.log.output.io.StreamTarget;
import org.jivesoftware.util.log.util.DefaultErrorHandler;

/**
 * This class encapsulates a basic independent log hierarchy.
 * The hierarchy is essentially a safe wrapper around root logger.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class Hierarchy {
    ///Format of default formatter
    private static final String FORMAT =
            "%7.7{priority} %5.5{time}   [%8.8{category}] (%{context}): %{message}\\n%{throwable}";

    ///The instance of default hierarchy
    private static final Hierarchy c_hierarchy = new Hierarchy();

    ///Error Handler associated with hierarchy
    private ErrorHandler m_errorHandler;

    ///The root logger which contains all Loggers in this hierarchy
    private Logger m_rootLogger;

    /**
     * Retrieve the default hierarchy.
     * <p/>
     * <p>In most cases the default LogHierarchy is the only
     * one used in an application. However when security is
     * a concern or multiple independent applications will
     * be running in same JVM it is advantageous to create
     * new Hierarchies rather than reuse default.</p>
     *
     * @return the default Hierarchy
     */
    public static Hierarchy getDefaultHierarchy() {
        return c_hierarchy;
    }

    /**
     * Create a hierarchy object.
     * The default LogTarget writes to stdout.
     */
    public Hierarchy() {
        m_errorHandler = new DefaultErrorHandler();
        m_rootLogger = new Logger(new InnerErrorHandler(), "", null, null);

        //Setup default output target to print to console
        final PatternFormatter formatter = new PatternFormatter(FORMAT);
        final StreamTarget target = new StreamTarget(System.out, formatter);

        setDefaultLogTarget(target);
    }

    /**
     * Set the default log target for hierarchy.
     * This is the target inherited by loggers if no other target is specified.
     *
     * @param target the default target
     */
    public void setDefaultLogTarget(final LogTarget target) {
        if (null == target) {
            throw new IllegalArgumentException("Can not set DefaultLogTarget to null");
        }

        final LogTarget[] targets = new LogTarget[]{target};
        getRootLogger().setLogTargets(targets);
    }

    /**
     * Set the default log targets for this hierarchy.
     * These are the targets inherited by loggers if no other targets are specified
     *
     * @param targets the default targets
     */
    public void setDefaultLogTargets(final LogTarget[] targets) {
        if (null == targets || 0 == targets.length) {
            throw new IllegalArgumentException("Can not set DefaultLogTargets to null");
        }

        for (int i = 0; i < targets.length; i++) {
            if (null == targets[i]) {
                throw new IllegalArgumentException("Can not set DefaultLogTarget element to null");
            }
        }

        getRootLogger().setLogTargets(targets);
    }

    /**
     * Set the default priority for hierarchy.
     * This is the priority inherited by loggers if no other priority is specified.
     *
     * @param priority the default priority
     */
    public void setDefaultPriority(final Priority priority) {
        if (null == priority) {
            throw new IllegalArgumentException("Can not set default Hierarchy Priority to null");
        }

        getRootLogger().setPriority(priority);
    }

    /**
     * Set the ErrorHandler associated with hierarchy.
     *
     * @param errorHandler the ErrorHandler
     */
    public void setErrorHandler(final ErrorHandler errorHandler) {
        if (null == errorHandler) {
            throw new IllegalArgumentException("Can not set default Hierarchy ErrorHandler to null");
        }

        m_errorHandler = errorHandler;
    }

    /**
     * Retrieve a logger for named category.
     *
     * @param category the context
     * @return the Logger
     */
    public Logger getLoggerFor(final String category) {
        return getRootLogger().getChildLogger(category);
    }

//    /**
//     * Logs an error message to error handler.
//     * Default Error Handler is stderr.
//     *
//     * @param message a message to log
//     * @param throwable a Throwable to log
//     * @deprecated Logging components should use ErrorHandler rather than Hierarchy.log()
//     */
//    public void log(final String message, final Throwable throwable) {
//        m_errorHandler.error(message, throwable, null);
//    }
//
//    /**
//     * Logs an error message to error handler.
//     * Default Error Handler is stderr.
//     *
//     * @param message a message to log
//     * @deprecated Logging components should use ErrorHandler rather than Hierarchy.log()
//     */
//    public void log(final String message) {
//        log(message, null);
//    }

    private class InnerErrorHandler
            implements ErrorHandler {
        /**
         * Log an unrecoverable error.
         *
         * @param message   the error message
         * @param throwable the exception associated with error (may be null)
         * @param event     the LogEvent that caused error, if any (may be null)
         */
        public void error(final String message,
                          final Throwable throwable,
                          final LogEvent event) {
            m_errorHandler.error(message, throwable, event);
        }
    }

    /**
     * Utility method to retrieve logger for hierarchy.
     * This method is intended for use by sub-classes
     * which can take responsibility for manipulating
     * Logger directly.
     *
     * @return the Logger
     */
    protected final Logger getRootLogger() {
        return m_rootLogger;
    }
}
