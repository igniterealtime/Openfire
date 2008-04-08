/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.tester;

/**
 * Title: SIP Register Tester
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

/**
 * Creates and writes out messages.
 */
public class Log {

    private static boolean debugger = false;

    static {
        if (System.getProperty("debugger") != null
                && System.getProperty("debugger").equals("true"))
            Log.debugger = true;
    }

    public static void debug(String message) {
        if (Log.debugger)
            System.out.println((message != null ? message : ""));
    }

    public static void debug(String method, String message) {
        if (Log.debugger)
            System.out.println((method != null ? method : "") + " - "
                    + (message != null ? message : ""));
    }

    public static void error(String method, Exception e) {
        System.out.println((method != null ? method : "") + " - "
                + (e != null ? e.toString() : ""));
    }

    public static void error(Exception e) {
        Log.error("", e);
    }

    public static void error(String method, Error e) {
        System.out.println((method != null ? method : "") + " - "
                + (e != null ? e.toString() : ""));
    }

    public static void error(String method, Throwable e) {
        System.out.println((method != null ? method : "") + " - "
                + (e != null ? e.toString() : ""));
    }

}
