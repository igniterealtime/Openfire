/**
 * $RCSfile$
 * $Revision: 2580 $
 * $Date: 2005-02-10 12:48:23 -0800 (Thu, 10 Feb 2005) $
 *
 * Copyright 2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.xmpp.component;

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
public interface Log {

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