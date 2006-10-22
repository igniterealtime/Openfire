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
 * Strategy that checks condition under which file rotation is needed.
 *
 * @author <a href="mailto:bh22351@i-one.at">Bernhard Huber</a>
 */
public interface RotateStrategy {
    /**
     * reset cumulative rotation history data.
     * Called after rotation.
     */
    void reset();

    /**
     * Check if a log rotation is neccessary at this time.
     *
     * @param data the serialized version of last message written to the log system
     * @param file the File that we are writing to
     * @return boolean return true if log rotation is neccessary, else false
     */
    boolean isRotationNeeded(String data, File file);
}

