/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of jVoiceBridge.
 *
 * jVoiceBridge is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation and distributed hereunder
 * to you.
 *
 * jVoiceBridge is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the License file that accompanied this
 * code.
 */

package com.sun.voip;

import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Calendar;

/**
 * Logging utility.
 */
public class Logger {
    private static BufferedWriter bw = null;
    private static FileWriter fw = null;

    public static final int LOG_ERROR      = 0;
    public static final int LOG_WARNING    = 1;
    public static final int LOG_NOTICE     = 2;
    public static final int LOG_PRODUCTION = 3;
    public static final int LOG_INFO       = 4;
    public static final int LOG_MOREINFO   = 5;
    public static final int LOG_DETAILINFO = 6;
    public static final int LOG_SIP	   = 7;
    public static final int LOG_H323	   = 7;
    public static final int LOG_DETAIL     = 8;
    public static final int LOG_MOREDETAIL = 9;
    public static final int LOG_DEBUG      = 10;

    public static int logLevel             = LOG_PRODUCTION;
    public static boolean writeThru        = false;
    public static boolean suppressSystemOut= false;

    public final static String LOG_LEVEL = "com.sun.voip.server.LOGLEVEL";

    /*
     * Takes about 15ms to flush
     */
    private final static int BUFFER_SIZE = (16 * 1024);

    private static String logFileName;

    /**
     * Private Constructor
     */
    private Logger() {}

    /**
     * Initializes the logger
     */
    public static void init() {
        // Open log file
        logFileName = System.getProperty(
            "com.sun.voip.server.BRIDGE_LOG", "bridge.log");

        if (logFileName.charAt(0) != File.separatorChar) {
            String s = System.getProperty("com.sun.voip.server.Bridge.logDirectory", "." + File.separator + "log" + File.separator);
            logFileName = s + logFileName;
        }

    init(logFileName, false);
    }

    public static void init(String logFileName, boolean suppressSystemOut) {
    Logger.logFileName = logFileName;

        String s = System.getProperty(LOG_LEVEL, "3");

    try {
        logLevel = Integer.parseInt(s);
    } catch (Exception e) {
    }

    if (logLevel <= LOG_PRODUCTION) {
        Logger.suppressSystemOut = suppressSystemOut;
    }

        try {
            File logFile = new File(logFileName);

            if (!logFile.exists()) {
                logFile.createNewFile();
        }

            fw = new FileWriter(logFileName, false);
        bw = new BufferedWriter(fw, BUFFER_SIZE);

        forcePrintln("Log file is " + logFileName);
        } catch (IOException e) {
        fw = null;
        bw = null;

            println(getDate() + "could not open log file: "
        + logFileName);
        }
    }

    public static String getLogFileName() {
    return logFileName;
    }

    /**
     * Logs an error message.
     * @param error the message to log
     */
    public static synchronized void error(String msg) {
    println("ERROR:  " + msg);
    }

    /**
     * Logs an exception.
     * @param e the exception to log
     */
    public static synchronized void exception(String s, Exception e) {
    error(s);
    e.printStackTrace();
    System.out.flush();
    }

    /**
     * Logs a message.
     * @param msg the message to log.
     */
    public static synchronized void println(String msg) {
    if (bw != null) {
        writeFile(msg);

        if (suppressSystemOut == true) {
            return;
        }
    }

        System.out.println(getDate() + msg);
        System.out.flush();
    }

    /**
     * Logs a message.
     * @param msg the message to log.
     */
    public static synchronized void forcePrintln(String msg) {
        if (bw != null) {
            writeFile(msg);
        }

        System.out.println(getDate() + msg);
        System.out.flush();
    }

    /**
     * Logs a message to the log file only.  This write is buffered
     * so as to not disrupt timing.
     * @param msg the message to log.
     */
    public static synchronized void writeFile(String msg) {
    if (bw != null) {
        try {
        synchronized(bw) {
                    bw.write(getDate() + msg + "\n");
        }
        } catch (IOException e) {
        System.out.println(getDate() + "Unable to writeFile! "
            + e.getMessage());
        close();
        }

        if (writeThru) {
        flush();
        }
    } else {
            System.out.println(getDate() + msg);
            System.out.flush();
    }
    }

    /**
     * flushes the buffered writer.
     */
    public static void flush() {
    try {
        if (bw != null) {
        bw.flush();
        }
        if (fw != null) {
        fw.flush();
        }
    } catch (IOException e) {
            System.out.println(getDate() + "could not flush log file. "
        + e.getMessage());
    }
    }

    /**
     * Closes the log file.
     */
    public static synchronized void close() {
    try {
        if (bw != null) {
        bw.flush();
                bw.close();
        }
        } catch (IOException e) {
            //System.out.println(getDate() + "could not close buffered writer");
        }

    bw = null;

        try {
        if (fw != null) {
        fw.flush();
                fw.close();
        }
    } catch (IOException e) {
            //System.out.println(getDate() + "could not close log file");
    }

    fw = null;
    }

    private static String[] month = {
    "Jan",
    "Feb",
    "Mar",
    "Apr",
    "May",
    "Jun",
    "Jul",
    "Aug",
    "Sep",
    "Oct",
    "Nov",
    "Dec"
    };

    public static String getDate() {
        Calendar now = Calendar.getInstance();

    String m = month[now.get(Calendar.MONTH)];
    String ms = String.valueOf(now.get(Calendar.MILLISECOND));

    if (ms.length() == 1) {
        ms += "  ";
    } else if (ms.length() == 2) {
        ms += " ";
    }

    String s = m + " " + now.get(Calendar.DAY_OF_MONTH) + " "
        + now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE)
        + ":" + now.get(Calendar.SECOND) + "." + ms + " ";

    s += "            ";
    s = s.substring(0, 21);
    return (s);
    }

}
