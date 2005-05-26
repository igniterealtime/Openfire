/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output.io;

import org.jivesoftware.util.log.format.Formatter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * A basic target that writes to a File.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public class FileTarget extends StreamTarget {

    ///File we are writing to
    private File m_file;

    ///Flag indicating whether or not file should be appended to
    private boolean m_append;

    /**
     * Construct file target to send to a file with a formatter.
     *
     * @param file      the file to send to
     * @param append    true if file is to be appended to, false otherwise
     * @param formatter the Formatter
     * @throws IOException if an error occurs
     */
    public FileTarget(final File file, final boolean append, final Formatter formatter)
            throws IOException {
        super(null, formatter);

        if (null != file) {
            setFile(file, append);
            openFile();
        }
    }

    /**
     * Set the file for this target.
     *
     * @param file   the file to send to
     * @param append true if file is to be appended to, false otherwise
     * @throws IOException if directories can not be created or file can not be opened
     */
    protected synchronized void setFile(final File file, final boolean append)
            throws IOException {
        if (null == file) {
            throw new NullPointerException("file property must not be null");
        }

        if (isOpen()) {
            throw new IOException("target must be closed before " +
                    "file property can be set");
        }

        m_append = append;
        m_file = file;
    }

    /**
     * Open underlying file and allocate resources.
     * This method will attempt to create directories below file and
     * append to it if specified.
     */
    protected synchronized void openFile()
            throws IOException {
        if (isOpen()) close();

        final File file = getFile().getCanonicalFile();

        final File parent = file.getParentFile();
        if (null != parent && !parent.exists()) {
            parent.mkdir();
        }

        final FileOutputStream outputStream =
                new FileOutputStream(file.getPath(), m_append);

        setOutputStream(outputStream);
        open();
    }

    /**
     * Retrieve file associated with target.
     * This allows subclasses to access file object.
     *
     * @return the output File
     */
    protected synchronized File getFile() {
        return m_file;
    }
}
