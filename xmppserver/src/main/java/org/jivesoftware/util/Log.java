/*
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

package org.jivesoftware.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Openfire makes use of a logging facade (slf4j) to manage its log output. The
 * facade is backed up by Log4j by default. This class provides utility methods.
 * <p>
 * Additionally, this class provides methods that can be used to record logging
 * statements. These methods are exact duplicates of the previous Log
 * implementation of Openfire and are kept for backwards-compatibility (the are
 * deprecated). These methods will delegate logging functionality to slf4j.
 * Instead of these methods, slf4j logging functionality should be used
 * directly.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="http://www.slf4j.org/">http://www.slf4j.org/</a>
 */
public final class Log {

    private static final org.slf4j.Logger Logger = org.slf4j.LoggerFactory.getLogger(Log.class);
    public static final SystemProperty<Boolean> DEBUG_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("log.debug.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(Log::setDebugEnabled)
        .build();
    /**
     * @deprecated in favour of {@link #DEBUG_ENABLED}
     */
    @Deprecated
    public static final String LOG_DEBUG_ENABLED = DEBUG_ENABLED.getKey();
    public static final SystemProperty<Boolean> TRACE_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("log.trace.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(Log::setTraceEnabled)
        .build();
    /**
     * @deprecated in favour of {@link #TRACE_ENABLED}
     */
    @Deprecated
    public static final String LOG_TRACE_ENABLED = TRACE_ENABLED.getKey();
    private static boolean debugEnabled = false;
    private static boolean traceEnabled = false;

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#isErrorEnabled()}.
     *             Functionality of this method is delegated there.
     * @return {@code true} if logging is enabed, otherwise {@code false}
     */
    @Deprecated
    public static boolean isErrorEnabled() {
        return Logger.isErrorEnabled();
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#isDebugEnabled()}.
     *             Functionality of this method is delegated there.
     * @return {@code true} if logging is enabed, otherwise {@code false}
     */
    @Deprecated
    public static boolean isDebugEnabled() {
        return Logger.isDebugEnabled();
    }

    public static void setDebugEnabled(final boolean enabled) {
        debugEnabled = enabled;
        setLogLevel();
    }

    public static void setTraceEnabled(final boolean enabled) {
        traceEnabled = enabled;
        setLogLevel();
    }

    private static void setLogLevel() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        final org.apache.logging.log4j.Level newLevel;
        if (traceEnabled) {
            newLevel = org.apache.logging.log4j.Level.TRACE;
        } else if (debugEnabled) {
            newLevel = org.apache.logging.log4j.Level.DEBUG;
        } else {
            newLevel = org.apache.logging.log4j.Level.INFO;
        }
        final LoggerContext ctx = (LoggerContext) LogManager.getContext( false );
        final Configuration config = ctx.getConfiguration();
        final LoggerConfig loggerConfig = config.getLoggerConfig( LogManager.ROOT_LOGGER_NAME );
        loggerConfig.setLevel( newLevel );
        ctx.updateLoggers();  // This causes all Loggers to refetch information from their LoggerConfig.
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#isInfoEnabled()}.
     *             Functionality of this method is delegated there.
     * @return {@code true} if logging is enabed, otherwise {@code false}
     */
    @Deprecated
    public static boolean isInfoEnabled() {
        return Logger.isInfoEnabled();
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#isWarnEnabled()}.
     *             Functionality of this method is delegated there.
     * @return {@code true} if logging is enabed, otherwise {@code false}
     */
    @Deprecated
    public static boolean isWarnEnabled() {
        return Logger.isWarnEnabled();
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#debug(String)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     */
    @Deprecated
    public static void debug(String s) {
        if (isDebugEnabled()) {
            Logger.debug(s);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#debug(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void debug(Throwable throwable) {
        if (isDebugEnabled()) {
            Logger.debug("", throwable);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#debug(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void debug(String s, Throwable throwable) {
        if (isDebugEnabled()) {
            Logger.debug(s, throwable);
        }
    }

    public static void markDebugLogFile(String username) {
        String message = getMarkMessage(username);
        debug(message);
    }

    public static void rotateDebugLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "debug.log");
        emptyFile(logFile);
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#info(String)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     */
    @Deprecated
    public static void info(String s) {
        if (isInfoEnabled()) {
            Logger.info(s);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#info(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void info(Throwable throwable) {
        if (isInfoEnabled()) {
            Logger.info("", throwable);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#info(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void info(String s, Throwable throwable) {
        if (isInfoEnabled()) {
            Logger.info(s, throwable);
        }
    }

    public static void markInfoLogFile(String username) {
        String message = getMarkMessage(username);
        info(message);
    }

    public static void rotateInfoLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "info.log");
        emptyFile(logFile);
    }
    
    /**
     * @deprecated replaced by {@link org.slf4j.Logger#warn(String)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     */
    @Deprecated
    public static void warn(String s) {
        if (isWarnEnabled()) {
            Logger.warn(s);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#warn(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void warn(Throwable throwable) {
        if (isWarnEnabled()) {
            Logger.warn("", throwable);
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#debug(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void warn(String s, Throwable throwable) {
        if (isWarnEnabled()) {
            Logger.warn(s, throwable);
        }
    }

    public static void markWarnLogFile(String username) {
        String message = getMarkMessage(username);
        warn(message);
    }

    public static void rotateWarnLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "warn.log");
        emptyFile(logFile);
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#error(String)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     */
    @Deprecated
    public static void error(String s) {
        if (isErrorEnabled()) {
            Logger.error(s);
            if (isDebugEnabled()) {
                printToStdErr(s, null);
            }
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#error(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void error(Throwable throwable) {
        if (isErrorEnabled()) {
            Logger.error("", throwable);
            if (isDebugEnabled()) {
                printToStdErr(null, throwable);
            }
        }
    }

    /**
     * @deprecated replaced by {@link org.slf4j.Logger#error(String, Throwable)}.
     *             Functionality of this method is delegated there.
     * @param s the string to log
     * @param throwable the throwable to log
     */
    @Deprecated
    public static void error(String s, Throwable throwable) {
        if (isErrorEnabled()) {
            Logger.error(s, throwable);
            if (isDebugEnabled()) {
                printToStdErr(s, throwable);
            }
        }
    }

    public static void markErrorLogFile(String username) {
        String message = getMarkMessage(username);
        error(message);
    }

    public static void rotateErrorLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "error.log");
        emptyFile(logFile);
    }

    public static void rotateAllLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "all.log");
        emptyFile(logFile);
    }

    
    /**
     * Returns the directory that log files exist in. The directory name will
     * have a File.separator as the last character in the string.
     *
     * @return the directory that log files exist in.
     */
    public static String getLogDirectory() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        final StringBuilder sb = new StringBuilder();
        sb.append(JiveGlobals.getHomeDirectory());
        if (!sb.substring(sb.length()-1).startsWith(File.separator)) {
            sb.append(File.separator);
        }
        sb.append("logs");
        sb.append(File.separator);
        return sb.toString();
    }

    private static String getMarkMessage(String username) {
        final List<String> args = new ArrayList<>();
        args.add(username);
        args.add(JiveGlobals.formatDateTime(new java.util.Date()));
        return LocaleUtils.getLocalizedString("log.marker_inserted_by", args);
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

    private static void emptyFile(File logFile) {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(logFile));
            out.write("");
        } catch (IOException ex) {
            Log.warn("Could not empty file " + logFile.getName(), ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Log.warn("Could not close file.", ex);
                }
            }
        }
    }
}
