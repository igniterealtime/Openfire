/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * Class representing and holding constants for priority.
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 */
public final class Priority implements Serializable {

    /**
     * Developer orientated messages, usually used during development of product.
     */
    public final static Priority DEBUG = new Priority("DEBUG", 5);

    /**
     * Useful information messages such as state changes, client connection, user login etc.
     */
    public final static Priority INFO = new Priority("INFO", 10);

    /**
     * A problem or conflict has occurred but it may be recoverable, then
     * again it could be the start of the system failing.
     */
    public final static Priority WARN = new Priority("WARN", 15);

    /**
     * A problem has occurred but it is not fatal. The system will still function.
     */
    public final static Priority ERROR = new Priority("ERROR", 20);

    /**
     * Something caused whole system to fail. This indicates that an administrator
     * should restart the system and try to fix the problem that caused the failure.
     */
    public final static Priority FATAL_ERROR = new Priority("FATAL_ERROR", 25);

    private final String m_name;
    private final int m_priority;

    /**
     * Retrieve a Priority object for the name parameter.
     *
     * @param priority the priority name
     * @return the Priority for name
     */
    public static Priority getPriorityForName(final String priority) {
        if (Priority.DEBUG.getName().equals(priority))
            return Priority.DEBUG;
        else if (Priority.INFO.getName().equals(priority))
            return Priority.INFO;
        else if (Priority.WARN.getName().equals(priority))
            return Priority.WARN;
        else if (Priority.ERROR.getName().equals(priority))
            return Priority.ERROR;
        else if (Priority.FATAL_ERROR.getName().equals(priority))
            return Priority.FATAL_ERROR;
        else
            return Priority.DEBUG;
    }

    /**
     * Private Constructor to block instantiation outside class.
     *
     * @param name     the string name of priority
     * @param priority the numerical code of priority
     */
    private Priority(final String name, final int priority) {
        m_name = name;
        m_priority = priority;
    }

    /**
     * Overidden string to display Priority in human readable form.
     *
     * @return the string describing priority
     */
    public String toString() {
        return "Priority[" + getName() + "/" + getValue() + "]";
    }

    /**
     * Get numerical value associated with priority.
     *
     * @return the numerical value
     */
    public int getValue() {
        return m_priority;
    }

    /**
     * Get name of priority.
     *
     * @return the priorities name
     */
    public String getName() {
        return m_name;
    }

    /**
     * Test whether this priority is greater than other priority.
     *
     * @param other the other Priority
     */
    public boolean isGreater(final Priority other) {
        return m_priority > other.getValue();
    }

    /**
     * Test whether this priority is lower than other priority.
     *
     * @param other the other Priority
     */
    public boolean isLower(final Priority other) {
        return m_priority < other.getValue();
    }

    /**
     * Test whether this priority is lower or equal to other priority.
     *
     * @param other the other Priority
     */
    public boolean isLowerOrEqual(final Priority other) {
        return m_priority <= other.getValue();
    }

    /**
     * Helper method that replaces deserialized object with correct singleton.
     *
     * @return the singleton version of object
     * @throws ObjectStreamException if an error occurs
     */
    private Object readResolve()
            throws ObjectStreamException {
        return getPriorityForName(m_name);
    }
}
