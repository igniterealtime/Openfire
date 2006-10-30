/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.output.io.rotate;

import java.io.File;

/**
 * strategy for naming log files based on appending revolving suffix.
 * <p/>
 * Heavily odified by Bruce Ritchie (Jive Software) to rotate along
 * the following strategy:
 * <p/>
 * current log file will always be the base File name
 * the next oldest file will be the _1 file
 * the next oldest file will be the _2 file
 * etc.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public class ExpandingFileStrategy implements FileStrategy {

    ///the base file name.
    private String baseFileName;

    public ExpandingFileStrategy(final String baseFileName) {

        this.baseFileName = baseFileName;
    }

    public File currentFile() {
        return new File(baseFileName);
    }

    /**
     * Calculate the real file name from the base filename.
     *
     * @return File the calculated file name
     */
    public File nextFile() {
        // go through all the possible filenames and delete/rename as necessary
        for (int i = 0; true; i++) {
            File test = new File(baseFileName.substring(0, baseFileName.lastIndexOf('.')) +
                    "_" + i + baseFileName.substring(baseFileName.lastIndexOf('.')));

            if (test.exists()) {
                continue;
            }
            else {
                return test;
            }
        }
    }
}

