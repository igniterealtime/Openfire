/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log;

/**
 * LogTarget is a class to encapsulate outputting LogEvent's.
 * This provides the base for all output and filter targets.
 * <p/>
 * Warning: If performance becomes a problem then this
 * interface will be rewritten as a abstract class.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public interface LogTarget {
    /**
     * Process a log event.
     * In NO case should this method ever throw an exception/error.
     * The reason is that logging is usually added for debugging/auditing
     * purposes and it would be unnaceptable to have your debugging
     * code cause more errors.
     *
     * @param event the event
     */
    void processEvent(LogEvent event);
}
