/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output.io;

import org.jivesoftware.util.log.format.Formatter;
import org.jivesoftware.util.log.output.AbstractOutputTarget;
import java.io.IOException;
import java.io.Writer;

/**
 * This target outputs to a writer.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class WriterTarget extends AbstractOutputTarget {

    private Writer m_output;

    /**
     * Construct target with a specific writer and formatter.
     *
     * @param writer    the writer
     * @param formatter the formatter
     */
    public WriterTarget(final Writer writer, final Formatter formatter) {
        super(formatter);

        if (null != writer) {
            setWriter(writer);
            open();
        }
    }

    /**
     * Set the writer.
     * Close down writer and send tail if appropriate.
     *
     * @param writer the new writer
     */
    protected synchronized void setWriter(final Writer writer) {
        if (null == writer) {
            throw new NullPointerException("writer property must not be null");
        }

        m_output = writer;
    }

    /**
     * Concrete implementation of output that writes out to underlying writer.
     *
     * @param data the data to output
     */
    protected void write(final String data) {
        try {
            m_output.write(data);
            m_output.flush();
        }
        catch (final IOException ioe) {
            getErrorHandler().error("Caught an IOException", ioe, null);
        }
    }

    /**
     * Shutdown target.
     * Attempting to send to target after close() will cause errors to be logged.
     */
    public synchronized void close() {
        super.close();
        shutdownWriter();
    }

    /**
     * Shutdown Writer.
     */
    protected synchronized void shutdownWriter() {
        final Writer writer = m_output;
        m_output = null;

        try {
            if (null != writer) {
                writer.close();
            }
        }
        catch (final IOException ioe) {
            getErrorHandler().error("Error closing Writer", ioe, null);
        }
    }
}
