/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.jivesoftware.util.log.util;

import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A LogFactory implementation to override the default commons-logging behavior. All log
 * statements are written to the Openfire logs. Info level logging is sent to debug.
 *
 * @deprecated Openfire uses SLF4J's 'jcl-over-slf4j' instead. See http://www.slf4j.org/legacy.html
 */
@Deprecated
public class CommonsLogFactory extends LogFactory {

    private static final Logger Log = LoggerFactory.getLogger(CommonsLogFactory.class);

    private org.apache.commons.logging.Log log;

    public CommonsLogFactory() {
        log = new org.apache.commons.logging.Log() {

            @Override
            public boolean isDebugEnabled() {
                return Log.isDebugEnabled();
            }

            @Override
            public boolean isErrorEnabled() {
                return Log.isErrorEnabled();
            }

            @Override
            public boolean isFatalEnabled() {
                return Log.isErrorEnabled();
            }

            @Override
            public boolean isInfoEnabled() {
                return Log.isInfoEnabled();
            }

            @Override
            public boolean isTraceEnabled() {
                return Log.isDebugEnabled();
            }

            @Override
            public boolean isWarnEnabled() {
                return Log.isWarnEnabled();
            }

            @Override
            public void trace(Object object) {
                // Ignore.
            }

            @Override
            public void trace(Object object, Throwable throwable) {
                // Ignore.
            }

            @Override
            public void debug(Object object) {
                Log.debug(object.toString());
            }

            @Override
            public void debug(Object object, Throwable throwable) {
                Log.debug(object.toString(), throwable);
            }

            @Override
            public void info(Object object) {
                // Send info log messages to debug because they are generally not useful.
                Log.debug(object.toString());
            }

            @Override
            public void info(Object object, Throwable throwable) {
                // Send info log messages to debug because they are generally not useful.
                Log.debug(object.toString(), throwable);
            }

            @Override
            public void warn(Object object) {
                Log.warn(object.toString());
            }

            @Override
            public void warn(Object object, Throwable throwable) {
                Log.warn(object.toString(), throwable);
            }

            @Override
            public void error(Object object) {
                Log.error(object.toString());
            }

            @Override
            public void error(Object object, Throwable throwable) {
                Log.error(object.toString(), throwable);
            }

            @Override
            public void fatal(Object object) {
                Log.error(object.toString());
            }

            @Override
            public void fatal(Object object, Throwable throwable) {
                Log.error(object.toString(), throwable);
            }
        };
    }

    @Override
    public Object getAttribute(String string) {
        return null;
    }

    @Override
    public String[] getAttributeNames() {
        return new String[0];
    }

    @Override
    public org.apache.commons.logging.Log getInstance(Class aClass)
            throws LogConfigurationException {
        return log;
    }

    @Override
    public org.apache.commons.logging.Log getInstance(String string)
            throws LogConfigurationException
    {
        return log;
    }

    @Override
    public void release() {

    }

    @Override
    public void removeAttribute(String string) {

    }

    @Override
    public void setAttribute(String string, Object object) {

    }
}
