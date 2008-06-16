/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util.log.util;

import org.mortbay.log.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

/**
 * A Logger implementation to override the default Jetty logging behavior. All log statements
 * are written to the Openfire logs. Info level logging is sent to debug.
 */
public class JettyLog implements Logger {

    /**
     * Only enable Jetty debug logging if it's specifically enabled. Otherwise, Jetty debug logs
     * pollute the Openfire debug log with too much data.
     */
    private boolean debugEnabled = JiveGlobals.getBooleanProperty("jetty.debugEnabled");

    public boolean isDebugEnabled() {
        return debugEnabled && Log.isDebugEnabled();
    }

    public void setDebugEnabled(boolean b) {
        // Do nothing.
    }

    public void info(String string, Object object, Object object1) {
        // Send info log messages to debug because they are generally not useful.
        Log.debug("JettyLog: "+format(string,object,object1));
    }

    public void debug(String string, Throwable throwable) {
        Log.debug("JettyLog: "+string, throwable);
    }

    public void debug(String string, Object object, Object object1) {
        Log.debug("JettyLog: "+format(string,object,object1));
    }

    public void warn(String string, Object object, Object object1) {
        Log.warn(format(string,object,object1));
    }

    public void warn(String string, Throwable throwable) {
        Log.warn(string, throwable);
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
