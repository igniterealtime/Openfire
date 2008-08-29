/**
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

/**
 * A simple logging service for components. Four log levels are provided:<ul>
 *
 *      <li>Error -- an error occured in the component.
 *      <li>Warn -- a condition occured that an administrator should be warned about.
 *      <li>Info -- used to send information messages, such as a version or license notice.
 *      <li>Debug -- used to send debugging information. Most Log implementations will
 *              disable debug output by default.
 * </ul>
 *
 * Log implementations will attempt use the native logging service of the component host
 * server. However, this may not be possible in some cases -- for example, when using an
 * external component that is not currently connected to the server.
 *
 * @author Matt Tucker
 */
public interface Logger {

    /**
     * Logs an error.
     *
     * @param message the error message.
     */
    public void error(String message);

    /**
     * Logs an error.
     *
     * @param message the error message.
     * @param throwable the Throwable that caused the error.
     */
    public void error(String message, Throwable throwable);

    /**
     * Logs an error.
     *
     * @param throwable the Throwable that caused the error.
     */
    public void error(Throwable throwable);

    /**
     * Logs a warning.
     *
     * @param message the warning message.
     */
    public void warn(String message);

    /**
     * Logs a warning.
     *
     * @param message the warning message.
     * @param throwable the Throwable that caused the error.
     */
    public void warn(String message, Throwable throwable);

    /**
     * Logs a warning.
     *
     * @param throwable the Throwable that caused the error.
     */
    public void warn(Throwable throwable);

    /**
     * Logs an info message.
     *
     * @param message the info message.
     */
    public void info(String message);

    /**
     * Logs an info message.
     *
     * @param message the info message.
     * @param throwable the Throwable that caused the info message.
     */
    public void info(String message, Throwable throwable);

    /**
     * Logs an info message.
     *
     * @param throwable the Throwable that caused the info message.
     */
    public void info(Throwable throwable);

    /**
     * Logs a debug message.
     *
     * @param message the debug message.
     */
    public void debug(String message);

    /**
     * Logs a debug message.
     *
     * @param message the debug message.
     * @param throwable the Throwable that caused the debug message.
     */
    public void debug(String message, Throwable throwable);

    /**
     * Logs a debug message.
     *
     * @param throwable the Throwable the caused the debug message.
     */
    public void debug(Throwable throwable);

}