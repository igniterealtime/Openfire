/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output.io.rotate;

import org.jivesoftware.util.FastDateFormat;

import java.io.File;
import java.util.Date;

/**
 * Strategy for naming log files based on appending time suffix.
 * A file name can be based on simply appending the number of miliseconds
 * since (not really sure) 1/1/1970.
 * Other constructors accept a pattern of a <code>SimpleDateFormat</code>
 * to form the appended string to the base file name as well as a suffix
 * which should be appended last.
 * <p/>
 * A <code>new UniqueFileStrategy( new File("foo.", "yyyy-MM-dd", ".log" )</code>
 * object will return <code>File</code> objects with file names like
 * <code>foo.2001-12-24.log</code>
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 * @author <a href="mailto:giacomo@apache.org">Giacomo Pati</a>
 */
public class UniqueFileStrategy
        implements FileStrategy {
    private File m_baseFile;
    private File m_currentFile;

    private FastDateFormat m_formatter;

    private String m_suffix;

    public UniqueFileStrategy(final File baseFile) {
        m_baseFile = baseFile;
    }

    public UniqueFileStrategy(final File baseFile, String pattern) {
        this(baseFile);
        m_formatter = FastDateFormat.getInstance(pattern);
    }

    public UniqueFileStrategy(final File baseFile, String pattern, String suffix) {
        this(baseFile, pattern);
        m_suffix = suffix;
    }

    public File currentFile() {
        return m_currentFile;
    }

    /**
     * Calculate the real file name from the base filename.
     *
     * @return File the calculated file name
     */
    public File nextFile() {
        final StringBuilder sb = new StringBuilder();
        sb.append(m_baseFile);
        if (m_formatter == null) {
            sb.append(System.currentTimeMillis());
        }
        else {
            final String dateString = m_formatter.format(new Date());
            sb.append(dateString);
        }

        if (m_suffix != null) {
            sb.append(m_suffix);
        }

        m_currentFile = new File(sb.toString());
        return m_currentFile;
    }
}

