/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import org.jivesoftware.util.JiveGlobals;

import org.eclipse.jetty.util.log.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Logger implementation to override the default Jetty logging behavior. All log statements
 * are written to the Openfire logs. Info level logging is sent to debug.
 */
public class JettyLog implements org.eclipse.jetty.util.log.Logger {

	private static final org.slf4j.Logger Log = LoggerFactory.getLogger(JettyLog.class);

    /**
     * Only enable Jetty debug logging if it's specifically enabled. Otherwise, Jetty debug logs
     * pollute the Openfire debug log with too much data.
     */
    private boolean debugEnabled = JiveGlobals.getBooleanProperty("jetty.debugEnabled");

    public boolean isDebugEnabled() {
        return debugEnabled && Log.isDebugEnabled();
    }

    public String getName() {
        return "JettyLog";
    }

    public void setDebugEnabled(boolean b) {
        // Do nothing.
    }




    public void info(String string, Object... object) {
        // Send info log messages to debug because they are generally not useful.
        Log.debug("JettyLog: " + object);
    }

    public void info(String string, Throwable throwable) {
        Log.debug("JettyLog: " + string);
    }

    public void info(Throwable throwable) {
        // do nothing
    }



    public void debug(String string, Object... object) {
        Log.debug("JettyLog: " + object);
    }

    public void debug(String string, Throwable throwable) {
        Log.debug(string);
    }

    public void debug(Throwable throwable) {
        // do nothing
    }




    public void warn(String string, Object... object) {
        Log.warn("JettyLog: "+ object);
    }

    public void warn(String string, Throwable throwable) {
        Log.warn(string);
    }

    public void warn(Throwable throwable) {
        // do nothing
    }



    public void ignore(Throwable throwable) {
        // do nothing
    }

    public Logger getLogger(String string) {
        return new JettyLog();
    }

    private String format(String msg, Object arg0, Object arg1) {
        int sub0 = msg.indexOf("{}");
        int sub1 = (sub0 > 0) ? ( 0 ) : ( msg.indexOf("{}") );

        if (arg0 != null && sub0 > 0)
            msg = msg.substring(0,sub0)+arg0+msg.substring(sub0+2);

        if (arg1 != null && sub1 > 0)
            msg = msg.substring(0,sub1)+arg1+msg.substring(sub1+2);

        return msg;
    }

}
