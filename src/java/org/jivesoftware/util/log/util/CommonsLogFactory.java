/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.util.log.util;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.LogConfigurationException;
import org.jivesoftware.util.Log;

/**
 * A LogFactory implementation to override the default commons-logging behavior. All log
 * statements are written to the Openfire logs. Info level logging is sent to debug.
 */
public class CommonsLogFactory extends LogFactory {

    private org.apache.commons.logging.Log log;

    public CommonsLogFactory() {
        log = new org.apache.commons.logging.Log() {

            public boolean isDebugEnabled() {
                return Log.isDebugEnabled();
            }

            public boolean isErrorEnabled() {
                return Log.isErrorEnabled();
            }

            public boolean isFatalEnabled() {
                return Log.isErrorEnabled();
            }

            public boolean isInfoEnabled() {
                return Log.isInfoEnabled();
            }

            public boolean isTraceEnabled() {
                return Log.isDebugEnabled();
            }

            public boolean isWarnEnabled() {
                return Log.isWarnEnabled();
            }

            public void trace(Object object) {
                // Ignore.
            }

            public void trace(Object object, Throwable throwable) {
                // Ignore.
            }

            public void debug(Object object) {
                Log.debug(object.toString());
            }

            public void debug(Object object, Throwable throwable) {
                Log.debug(object.toString(), throwable);
            }

            public void info(Object object) {
                // Send info log messages to debug because they are generally not useful.
                Log.debug(object.toString());
            }

            public void info(Object object, Throwable throwable) {
                // Send info log messages to debug because they are generally not useful.
                Log.debug(object.toString(), throwable);
            }

            public void warn(Object object) {
                Log.warn(object.toString());
            }

            public void warn(Object object, Throwable throwable) {
                Log.warn(object.toString(), throwable);
            }

            public void error(Object object) {
                Log.error(object.toString());
            }

            public void error(Object object, Throwable throwable) {
                Log.error(object.toString(), throwable);
            }

            public void fatal(Object object) {
                Log.error(object.toString());
            }

            public void fatal(Object object, Throwable throwable) {
                Log.error(object.toString(), throwable);
            }
        };
    }

    public Object getAttribute(String string) {
        return null;
    }

    public String[] getAttributeNames() {
        return new String[0];
    }

    public org.apache.commons.logging.Log getInstance(Class aClass)
            throws LogConfigurationException {
        return log;
    }

    public org.apache.commons.logging.Log getInstance(String string)
            throws LogConfigurationException
    {
        return log;
    }

    public void release() {

    }

    public void removeAttribute(String string) {

    }

    public void setAttribute(String string, Object object) {

    }
}
