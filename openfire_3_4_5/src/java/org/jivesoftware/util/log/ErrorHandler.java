/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log;

/**
 * Handle unrecoverable errors that occur during logging.
 * Based on Log4js notion of ErrorHandlers.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public interface ErrorHandler {
    /**
     * Log an unrecoverable error.
     *
     * @param message   the error message
     * @param throwable the exception associated with error (may be null)
     * @param event     the LogEvent that caused error, if any (may be null)
     */
    void error(String message, Throwable throwable, LogEvent event);
}
