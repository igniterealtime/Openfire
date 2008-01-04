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
 * Strategy for naming log files.
 * For a given base file name an implementation calculates
 * the real file name.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public interface FileStrategy {

    /**
     * Get the current logfile
     */
    File currentFile();

    /**
     * Get the next log file to rotate to.
     *
     * @return the file to rotate to
     */
    File nextFile();
}


