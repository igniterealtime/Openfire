/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Simple wrapper to the incorporated LogKit to log under a single logging name.
 *
 * @author Bruce Ritchie
 */
public class Log {

    private static final Logger debugLog = Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-DEBUG");
    private static final Logger infoLog = Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-INFO");
    private static final Logger warnLog = Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-WARN");
    private static final Logger errorLog = Hierarchy.getDefaultHierarchy().getLoggerFor("Jive-ERR");

    private static String logNameDebug = null;
    private static String logNameInfo = null;
    private static String logNameWarn = null;
    private static String logNameError = null;
    private static String debugPattern = null;
    private static String infoPattern = null;
    private static String warnPattern = null;
    private static String errorPattern = null;
    private static String logDirectory = null;

    private static long maxDebugSize = 1024;
    private static long maxInfoSize = 1024;
    private static long maxWarnSize = 1024;
    private static long maxErrorSize = 1024;

    private static boolean debugEnabled;

    static {
        initLog();
    }

    private Log() { }

    /**
     * This method is used to initialize the Log class. For normal operations this method
     * should <b>never</b> be called, rather it's only publically available so that the class
     * can be reset by the setup process once the home directory has been specified.
     */
    public static void initLog() {
        try {
            logDirectory = JiveGlobals.getXMLProperty("log.directory");
            if (logDirectory == null) {
                if (JiveGlobals.getHomeDirectory() != null) {
                    File openfireHome = new File(JiveGlobals.getHomeDirectory());
                    if (openfireHome.exists() && openfireHome.canWrite()) {
                        logDirectory = (new File(openfireHome, "logs")).toString();
                    }
                }
            }

            if (!logDirectory.endsWith(File.separator)) {
                logDirectory = logDirectory + File.separator;
            }

            // Make sure the logs directory exists. If not, make it:
            File logDir = new File(logDirectory);
            if (!logDir.exists()) {
                logDir.mkdir();
            }

            logNameDebug = logDirectory + "debug.log";
            logNameInfo = logDirectory + "info.log";
            logNameWarn = logDirectory + "warn.log";
            logNameError = logDirectory + "error.log";

            debugPattern = JiveGlobals.getXMLProperty("log.debug.format");
            infoPattern = JiveGlobals.getXMLProperty("log.info.format");
            warnPattern = JiveGlobals.getXMLProperty("log.warn.format");
            errorPattern = JiveGlobals.getXMLProperty("log.error.format");

            try { maxDebugSize = Long.parseLong(JiveGlobals.getXMLProperty("log.debug.size")); }
            catch (NumberFormatException e) { /* ignore */ }
            try { maxInfoSize = Long.parseLong(JiveGlobals.getXMLProperty("log.info.size")); }
            catch (NumberFormatException e) { /* ignore */ }
            try { maxWarnSize = Long.parseLong(JiveGlobals.getXMLProperty("log.warn.size")); }
            catch (NumberFormatException e) { /* ignore */ }
            try { maxErrorSize = Long.parseLong(JiveGlobals.getXMLProperty("log.error.size")); }
            catch (NumberFormatException e) { /* ignore */ }

            debugEnabled = "true".equals(JiveGlobals.getXMLProperty("log.debug.enabled"));
        }
        catch (Exception e) {
            // we'll get an exception if home isn't setup yet - we ignore that since
            // it's sure to be logged elsewhere :)
        }

        if (debugPattern == null) {
            debugPattern = "%{time:yyyy.MM.dd HH:mm:ss} %{message}\\n%{throwable}";
        }
        if (infoPattern == null) {
            infoPattern = "%{time:yyyy.MM.dd HH:mm:ss} %{message}\\n%{throwable}";
        }
        if (warnPattern == null) {
            warnPattern = "%{time:yyyy.MM.dd HH:mm:ss} %{message}\\n%{throwable}";
        }
        if (errorPattern == null) {
            errorPattern = "%{time:yyyy.MM.dd HH:mm:ss} [%{method}] %{message}\\n%{throwable}";
        }

        createLogger(debugPattern, logNameDebug, maxDebugSize, debugLog, Priority.DEBUG);
        createLogger(infoPattern, logNameInfo, maxInfoSize, infoLog, Priority.INFO);
        createLogger(warnPattern, logNameWarn, maxWarnSize, warnLog, Priority.WARN);
        createLogger(errorPattern, logNameError, maxErrorSize, errorLog, Priority.ERROR);

        // set up the ties into jdk logging
        Handler jdkLogHandler = new JiveLogHandler();
        jdkLogHandler.setLevel(Level.ALL);
        java.util.logging.Logger.getLogger("").addHandler(jdkLogHandler);
    }

    private static void createLogger(String pattern, String logName, long maxLogSize,
            Logger logger, Priority priority)
    {
        // debug log file
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter(pattern);
        StreamTarget target = null;
        Exception ioe = null;

        try {
            // home was not setup correctly
            if (logName == null) {
                throw new IOException("LogName was null - OpenfireHome not set?");
            }
            else {
                RevolvingFileStrategy fileStrategy = new RevolvingFileStrategy(logName, 5);
                RotateStrategyBySize rotateStrategy = new RotateStrategyBySize(maxLogSize * 1024);
                target = new RotatingFileTarget(formatter, rotateStrategy, fileStrategy);
            }
        }
        catch (IOException e) {
            ioe = e;
            // can't log to file, log to stderr
            target = new StreamTarget(System.err, formatter);
        }

        logger.setLogTargets(new LogTarget[] { target } );
        logger.setPriority(priority);

        if (ioe != null) {
            logger.debug("Error occurred opening log file: " + ioe.getMessage());
        }
    }

    public static void setProductName(String productName) {
        debugPattern = productName + " " + debugPattern;
        infoPattern = productName + " " + infoPattern;
        warnPattern = productName + " " + warnPattern;
        errorPattern = productName + " " + errorPattern;

        createLogger(debugPattern, logNameDebug, maxDebugSize, debugLog, Priority.DEBUG);
        createLogger(infoPattern, logNameInfo, maxInfoSize, infoLog, Priority.INFO);
        createLogger(warnPattern, logNameWarn, maxWarnSize, warnLog, Priority.WARN);
        createLogger(errorPattern, logNameError, maxErrorSize, errorLog, Priority.ERROR);
    }

    public static boolean isErrorEnabled() {
        return errorLog.isErrorEnabled();
    }

    public static boolean isFatalEnabled() {
        return errorLog.isFatalErrorEnabled();
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        JiveGlobals.setXMLProperty("log.debug.enabled", Boolean.toString(enabled));
        debugEnabled = enabled;
    }

    public static boolean isInfoEnabled() {
        return infoLog.isInfoEnabled();
    }

    public static boolean isWarnEnabled() {
        return warnLog.isWarnEnabled();
    }

    public static void debug(String s) {
        if (isDebugEnabled()) {
            debugLog.debug(s);
        }
    }

    public static void debug(Throwable throwable) {
        if (isDebugEnabled()) {
            debugLog.debug("", throwable);
        }
    }

    public static void debug(String s, Throwable throwable) {
        if (isDebugEnabled()) {
            debugLog.debug(s, throwable);
        }
    }

    public static void markDebugLogFile(String username) {
        RotatingFileTarget target = (RotatingFileTarget) debugLog.getLogTargets()[0];
        markLogFile(username, target);
    }

    public static void rotateDebugLogFile() {
        RotatingFileTarget target = (RotatingFileTarget) debugLog.getLogTargets()[0];
        try {
            target.rotate();
        }
        catch (IOException e) {
            System.err.println("Warning: There was an error rotating the Jive debug log file. " +
                    "Logging may not work correctly until a restart happens.");
        }
    }

    public static void info(String s) {
        if (isInfoEnabled()) {
            infoLog.info(s);
        }
    }

    public static void info(Throwable throwable) {
        if (isInfoEnabled()) {
            infoLog.info("", throwable);
        }
    }

    public static void info(String s, Throwable throwable) {
        if (isInfoEnabled()) {
            infoLog.info(s, throwable);
        }
    }

    public static void markInfoLogFile(String username) {
        RotatingFileTarget target = (RotatingFileTarget) infoLog.getLogTargets()[0];
        markLogFile(username, target);
    }

    public static void rotateInfoLogFile() {
        RotatingFileTarget target = (RotatingFileTarget) infoLog.getLogTargets()[0];
        try {
            target.rotate();
        }
        catch (IOException e) {
            System.err.println("Warning: There was an error rotating the Jive info log file. " +
                    "Logging may not work correctly until a restart happens.");
        }
    }

    public static void warn(String s) {
        if (isWarnEnabled()) {
            warnLog.warn(s);
        }
    }

    public static void warn(Throwable throwable) {
        if (isWarnEnabled()) {
            warnLog.warn("", throwable);
        }
    }

    public static void warn(String s, Throwable throwable) {
        if (isWarnEnabled()) {
            warnLog.warn(s, throwable);
        }
    }

    public static void markWarnLogFile(String username) {
        RotatingFileTarget target = (RotatingFileTarget) warnLog.getLogTargets()[0];
        markLogFile(username, target);
    }

    public static void rotateWarnLogFile() {
        RotatingFileTarget target = (RotatingFileTarget) warnLog.getLogTargets()[0];
        try {
            target.rotate();
        }
        catch (IOException e) {
            System.err.println("Warning: There was an error rotating the Jive warn log file. " +
                    "Logging may not work correctly until a restart happens.");
        }
    }

    public static void error(String s) {
        if (isErrorEnabled()) {
            errorLog.error(s);
            if (isDebugEnabled()) {
                printToStdErr(s, null);
            }
        }
    }

    public static void error(Throwable throwable) {
        if (isErrorEnabled()) {
            errorLog.error("", throwable);
            if (isDebugEnabled()) {
                printToStdErr(null, throwable);
            }
        }
    }

    public static void error(String s, Throwable throwable) {
        if (isErrorEnabled()) {
            errorLog.error(s, throwable);
            if (isDebugEnabled()) {
                printToStdErr(s, throwable);
            }
        }
    }

    public static void markErrorLogFile(String username) {
        RotatingFileTarget target = (RotatingFileTarget) errorLog.getLogTargets()[0];
        markLogFile(username, target);
    }

    public static void rotateErrorLogFile() {
        RotatingFileTarget target = (RotatingFileTarget) errorLog.getLogTargets()[0];
        try {
            target.rotate();
        }
        catch (IOException e) {
            System.err.println("Warning: There was an error rotating the Jive error log file. " +
                    "Logging may not work correctly until a restart happens.");
        }
    }

    public static void fatal(String s) {
        if (isFatalEnabled()) {
            errorLog.fatalError(s);
            if (isDebugEnabled()) {
                printToStdErr(s, null);
            }
        }
    }

    public static void fatal(Throwable throwable) {
        if (isFatalEnabled()) {
            errorLog.fatalError("", throwable);
            if (isDebugEnabled()) {
                printToStdErr(null, throwable);
            }
        }
    }

    public static void fatal(String s, Throwable throwable) {
        if (isFatalEnabled()) {
            errorLog.fatalError(s, throwable);
            if (isDebugEnabled()) {
                printToStdErr(s, throwable);
            }
        }
    }

    /**
     * Returns the directory that log files exist in. The directory name will
     * have a File.separator as the last character in the string.
     *
     * @return the directory that log files exist in.
     */
    public static String getLogDirectory() {
        return logDirectory;
    }

    private static void markLogFile(String username, RotatingFileTarget target) {
        List args = new ArrayList();
        args.add(username);
        args.add(JiveGlobals.formatDateTime(new java.util.Date()));
        target.write(LocaleUtils.getLocalizedString("log.marker_inserted_by", args) + "\n");
    }

    private static void printToStdErr(String s, Throwable throwable) {
        if (s != null) {
            System.err.println(s);
        }
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            System.err.print(sw.toString());
            System.err.print("\n");
        }
    }

    private static final class JiveLogHandler extends Handler {

        public void publish(LogRecord record) {

            Level level = record.getLevel();
            Throwable throwable = record.getThrown();


            if (Level.SEVERE.equals(level)) {

                if (throwable != null) {
                    Log.error(record.getMessage(), throwable);
                }
                else {
                    Log.error(record.getMessage());
                }

            }
            else if (Level.WARNING.equals(level)) {

                if (throwable != null) {
                    Log.warn(record.getMessage(), throwable);
                }
                else {
                    Log.warn(record.getMessage());
                }


            }
            else if (Level.INFO.equals(level)) {

                if (throwable != null) {
                    Log.info(record.getMessage(), throwable);
                }
                else {
                    Log.info(record.getMessage());
                }

            }
            else {
                // else FINE,FINER,FINEST

                if (throwable != null) {
                    Log.debug(record.getMessage(), throwable);
                }
                else {
                    Log.debug(record.getMessage());
                }

            }
        }

        public void flush() {
            // do nothing
        }

        public void close() throws SecurityException {
            // do nothing
        }
    }

}