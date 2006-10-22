/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 */

package org.jivesoftware.util;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * A collection of utilities for test writers. <p>
 *
 * File methods:
 *
 *  <ul><li>{@link #createTempFile()}</li>
 *      <li>{@link #createTempFile(String, String)}</li>
 *      <li>{@link #getAsString(java.io.File)}</li></ul>
 */
public class TestUtils {

    /**
     * Creates a temp file.
     * @see java.io.File#createTempFile(String, String)
     */
    public static File createTempFile() throws Exception {
        return createTempFile("test", ".test");
    }

    /**
     * Creates a temp file with the given filename suffix and prefix.
     * @see java.io.File#createTempFile(String, String)
     */
    public static File createTempFile(String prefix, String suffix) throws Exception {
        return File.createTempFile(prefix, suffix);
    }

    /**
     * Returns the contents of the given file as a String.
     */
    public static String getAsString(File file) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(file));
        StringBuffer xml = new StringBuffer();
        String lineSeparator = System.getProperty("line.separator");
        if (lineSeparator == null) {
            lineSeparator = "\n";
        }
        String line = null;
        while ((line=in.readLine()) != null) {
            xml.append(line).append(lineSeparator);
        }
        in.close();
        return xml.toString();
    }

    public static String prepareFilename(String filename) {
        return filename.replace('/', File.separatorChar);
    }
}

