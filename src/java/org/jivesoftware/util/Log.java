/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.jivesoftware.util.log.Hierarchy;
import org.jivesoftware.util.log.LogTarget;
import org.jivesoftware.util.log.Logger;
import org.jivesoftware.util.log.Priority;
import org.jivesoftware.util.log.format.ExtendedPatternFormatter;
import org.jivesoftware.util.log.output.io.StreamTarget;
import org.jivesoftware.util.log.output.io.rotate.RevolvingFileStrategy;
import org.jivesoftware.util.log.output.io.rotate.RotateStrategyBySize;
import org.jivesoftware.util.log.output.io.rotate.RotatingFileTarget;
import org.jivesoftware.messenger.JiveGlobals;
import java.io.File;

/**
 * Simple wrapper to the incorporated LogKit to log under a single logging name.
 *
 * @author Bruce Ritchie
 */
public class Log {

    private static final Logger infoLog =
            Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-INFO");
    private static final Logger warnLog =
            Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-WARN");
    private static final Logger errorLog =
            Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-ERR");

    static {
        Logger[] loggers = {infoLog, warnLog, errorLog};
        ExtendedPatternFormatter[] logFormatters = new ExtendedPatternFormatter[3];
        String[] logNames = {"info", "warn", "error"};
        Priority[] logPriority = {Priority.INFO, Priority.WARN, Priority.ERROR};
        String[] logPatterns = {
            "%{time:yyyy.MM.dd HH:mm} %{message}\\n%{throwable}",
            "%{time:yyyy.MM.dd HH:mm} %{message}\\n%{throwable}",
            "%{time:yyyy.MM.dd HH:mm} [%{method}] %{message}\\n%{throwable}"};

        for (int i = 0; i < 3; i++) {
            String pattern = JiveGlobals.getJiveProperty("log." + logNames[i] + ".format");
            if (pattern != null) {
                logPatterns[i] = pattern;
            }
            logFormatters[i] = new ExtendedPatternFormatter(logPatterns[i]);
        }

        try {
            // Make sure the logs directory exists. If not, make it:
            File logDir = new File(JiveGlobals.getJiveHome() + File.separator + "logs");
            if (!logDir.exists()) {
                try {
                    logDir.mkdir();
                }
                catch (SecurityException se) {
                    System.err.println("Could not create log directory: "
                            + logDir.toString() + " "
                            + se.getMessage());
                }
            }
            for (int i = 0; i < 3; i++) {
                StreamTarget infoTarget = null;
                if (logDir.exists()) {
                    String fileName = logDir.getPath()
                            + File.separator
                            + "jive." + logNames[i] + ".log";
                    String pattern = JiveGlobals.getJiveProperty("log."
                            + logNames[i]
                            + ".format");
                    if (pattern != null) {
                        logPatterns[i] = pattern;
                    }
                    RevolvingFileStrategy fileStrategy =
                            new RevolvingFileStrategy(fileName, 5);
                    RotateStrategyBySize rotateStrategy =
                            new RotateStrategyBySize(); // 1 meg default
                    try {
                        infoTarget = new RotatingFileTarget(logFormatters[i],
                                rotateStrategy,
                                fileStrategy);
                    }
                    catch (Exception e) {
                        System.err.print("Trouble opening log: " + e.getMessage());
                    }
                }
                if (infoTarget == null) {
                    infoTarget = new StreamTarget(System.err, logFormatters[i]);
                }
                loggers[i].setLogTargets(new LogTarget[]{infoTarget});
                loggers[i].setPriority(logPriority[i]);
            }
        }
        catch (SecurityException se) {
            System.err.println(se.getMessage());
        }
    }

    /**
     * <p>Check if the logger at the error level is enabled.</p>
     *
     * @return True if error logging is enabled
     */
    public static boolean isErrorEnabled() {
        return errorLog.isErrorEnabled();
    }

    /**
     * <p>Check if the logger at the info level is enabled.</p>
     *
     * @return true if info level is enabled
     */
    public static boolean isInfoEnabled() {
        return infoLog.isInfoEnabled();
    }

    /**
     * <p>Check if the logger at the warn level is enabled.</p>
     *
     * @return true if warn level is enabled
     */
    public static boolean isWarnEnabled() {
        return warnLog.isWarnEnabled();
    }

    /**
     * <p>Log an event at the info level.</p>
     *
     * @param s The string to log
     */
    public static void info(String s) {
        infoLog.info(s);
    }

    /**
     * <p>Log an event at the info level.</p>
     *
     * @param throwable The exception to log
     */
    public static void info(Throwable throwable) {
        infoLog.info("", throwable);
    }

    /**
     * <p>Log an event at the info level.</p>
     *
     * @param s         The string to log
     * @param throwable The exception to log
     */
    public static void info(String s, Throwable throwable) {
        infoLog.info(s, throwable);
    }

    /**
     * <p>Log an event at the warn level.</p>
     *
     * @param s The string to log
     */
    public static void warn(String s) {
        warnLog.warn(s);
    }

    /**
     * <p>Log an event at the warn level.</p>
     *
     * @param throwable The exception to log
     */
    public static void warn(Throwable throwable) {
        warnLog.warn("", throwable);
    }

    /**
     * <p>Log an event at the warn level.</p>
     *
     * @param s         The string to log
     * @param throwable The exception to log
     */
    public static void warn(String s, Throwable throwable) {
        warnLog.warn(s, throwable);
    }

    /**
     * <p>Log an event at the error level.</p>
     *
     * @param s The string to log
     */
    public static void error(String s) {
        errorLog.error(s);
    }

    /**
     * <p>Log an event at the error level.</p>
     *
     * @param throwable The exception to log
     */
    public static void error(Throwable throwable) {
        errorLog.error("", throwable);
    }

    /**
     * <p>Log an event at the error level.</p>
     *
     * @param s         The string to log
     * @param throwable The exception to log
     */
    public static void error(String s, Throwable throwable) {
        errorLog.error(s, throwable);
    }
}
