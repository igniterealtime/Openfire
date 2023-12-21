/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.apache.logging.log4j.Level;
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

    public static final SystemProperty<Boolean> TRACE_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("log.trace.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(Log::setTraceEnabled)
        .build();

    private static Level lastLogLevel = getRootLogLevel();

    public static void setDebugEnabled(final boolean enabled) {
        if (enabled && getRootLogLevel().isMoreSpecificThan(Level.DEBUG)) {
            lastLogLevel = getRootLogLevel();
            setLogLevel(Level.DEBUG);
        } else if (!enabled && getRootLogLevel().isLessSpecificThan(Level.DEBUG)) {
            setLogLevel(lastLogLevel != Level.DEBUG ? lastLogLevel : Level.INFO);
            lastLogLevel = Level.DEBUG;
        }
    }
    
    public static void setTraceEnabled(final boolean enabled) {
        if (enabled && getRootLogLevel().isMoreSpecificThan(Level.TRACE)) {
            lastLogLevel = getRootLogLevel();
            setLogLevel(Level.TRACE);
        } else if (!enabled && getRootLogLevel().isLessSpecificThan(Level.TRACE)) {
            setLogLevel(lastLogLevel != Level.TRACE ? lastLogLevel : Level.INFO);
            lastLogLevel = Level.TRACE;
        }
    }

    public static Level getRootLogLevel() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        final LoggerContext ctx = (LoggerContext) LogManager.getContext( false );
        final Configuration config = ctx.getConfiguration();
        final LoggerConfig loggerConfig = config.getRootLogger();
        return loggerConfig.getLevel();
    }

    private static void setLogLevel(Level newLevel) {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        final LoggerContext ctx = (LoggerContext) LogManager.getContext( false );
        final Configuration config = ctx.getConfiguration();
        final LoggerConfig loggerConfig = config.getRootLogger();
        loggerConfig.setLevel( newLevel );
        ctx.updateLoggers();  // This causes all Loggers to refetch information from their LoggerConfig.
    }

    public static void rotateOpenfireLogFile() {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
        File logFile = new File(Log.getLogDirectory(), "openfire.log");
        emptyFile(logFile);
    }

    public static void markOpenfireLogFile(String username) {
        String message = getMarkMessage(username);
        File logFile = new File(Log.getLogDirectory(), "openfire.log");

        try(FileWriter fw = new FileWriter(logFile, true);
            PrintWriter out = new PrintWriter(fw))
        {
            out.println(message);
        } catch (IOException e) {
            e.printStackTrace(); // Writing it to the logfile feels wrong, as we're processing the logfile here.
        }
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
        sb.append(JiveGlobals.getHomePath());
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
            Logger.warn("Could not empty file " + logFile.getName(), ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    Logger.warn("Could not close file.", ex);
                }
            }
        }
    }
}
