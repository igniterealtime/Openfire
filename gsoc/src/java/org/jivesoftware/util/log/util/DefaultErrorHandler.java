/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.util;

import org.jivesoftware.util.log.ErrorHandler;
import org.jivesoftware.util.log.LogEvent;

/**
 * Handle unrecoverable errors that occur during logging by
 * writing to standard error.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class DefaultErrorHandler
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
        System.err.println("Logging Error: " + message);
        if (null != throwable) {
            throwable.printStackTrace();
        }
    }
}
