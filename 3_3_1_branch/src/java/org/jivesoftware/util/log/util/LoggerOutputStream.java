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
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Redirect an output stream to a logger.
 * This class is useful to redirect standard output or
 * standard error to a Logger. An example use is
 * <p/>
 * <pre>
 * final LoggerOutputStream outputStream =
 *     new LoggerOutputStream( logger, Priority.DEBUG );
 * final PrintStream output = new PrintStream( outputStream, true );
 * <p/>
 * System.setOut( output );
 * </pre>
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class LoggerOutputStream
        extends OutputStream {
    ///Logger that we log to
    private final Logger m_logger;

    ///Log level we log to
    private final Priority m_priority;

    ///The buffered output so far
    private final StringBuffer m_output = new StringBuffer();

    ///Flag set to true once stream closed
    private boolean m_closed;

    /**
     * Construct OutputStreamLogger to send to a particular logger at a particular priority.
     *
     * @param logger   the logger to send to
     * @param priority the priority at which to log
     */
    public LoggerOutputStream(final Logger logger,
                              final Priority priority) {
        m_logger = logger;
        m_priority = priority;
    }

    /**
     * Shutdown stream.
     */
    public void close()
            throws IOException {
        flush();
        super.close();
        m_closed = true;
    }

    /**
     * Write a single byte of data to output stream.
     *
     * @param data the byte of data
     * @throws IOException if an error occurs
     */
    public void write(final int data)
            throws IOException {
        checkValid();

        //Should we properly convert char using locales etc??
        m_output.append((char)data);

        if ('\n' == data) {
            flush();
        }
    }

    /**
     * Flush data to underlying logger.
     *
     * @throws IOException if an error occurs
     */
    public synchronized void flush()
            throws IOException {
        checkValid();

        m_logger.log(m_priority, m_output.toString());
        m_output.setLength(0);
    }

    /**
     * Make sure stream is valid.
     *
     * @throws IOException if an error occurs
     */
    private void checkValid()
            throws IOException {
        if (true == m_closed) {
            throw new EOFException("OutputStreamLogger closed");
        }
    }
}
