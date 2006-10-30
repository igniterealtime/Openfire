/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.util;

import org.jivesoftware.util.log.Logger;
import org.jivesoftware.util.log.Priority;

/**
 * Redirect an output stream to a logger.
 * This class is useful to redirect standard output or
 * standard error to a Logger. An example use is
 * <p/>
 * <pre>
 * final OutputStreamLogger outputStream =
 *     new OutputStreamLogger( logger, Priority.DEBUG );
 * final PrintStream output = new PrintStream( outputStream, true );
 * <p/>
 * System.setOut( output );
 * </pre>
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @deprecated Use LoggerOutputStream as this class was misnamed.
 */
public class OutputStreamLogger
        extends LoggerOutputStream {

    /**
     * Construct logger to send to a particular logger at a particular priority.
     *
     * @param logger   the logger to send to
     * @param priority the priority at which to log
     * @deprecated Use LoggerOutputStream as this class was misnamed.
     */
    public OutputStreamLogger(final Logger logger,
                              final Priority priority) {
        super(logger, priority);
    }
}
