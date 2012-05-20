/**
 * $RCSfile$
 * $Revision$
 * $Date$
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

package org.jivesoftware.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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
public class Log {

	private static final org.slf4j.Logger Logger = org.slf4j.LoggerFactory.getLogger(Log.class);

// TODO deprecate these properties
//	JiveGlobals.getXMLProperty("log.debug.format");
//	JiveGlobals.getXMLProperty("log.info.format");
//	JiveGlobals.getXMLProperty("log.warn.format");
//	JiveGlobals.getXMLProperty("log.error.format");
//	JiveGlobals.getXMLProperty("log.debug.size");
//	JiveGlobals.getXMLProperty("log.info.size");
//	JiveGlobals.getXMLProperty("log.warn.size");
//	JiveGlobals.getXMLProperty("log.error.size"); 
//	JiveGlobals.getXMLProperty("log.debug.enabled");

	/**
	 * @deprecated replaced by {@link org.slf4j.Logger#isErrorEnabled()}.
	 *             Functionality of this method is delegated there.
	 */
	@Deprecated
    public static boolean isErrorEnabled() {
        return Logger.isErrorEnabled();
    }

	/**
	 * @deprecated replaced by {@link org.slf4j.Logger#isDebugEnabled()}.
	 *             Functionality of this method is delegated there.
	 */
    @Deprecated
	public static boolean isDebugEnabled() {
        return Logger.isDebugEnabled();
    }

    public static void setDebugEnabled(boolean enabled) {
        // SLF4J doesn't provide a hook into the logging implementation. We'll have to do this 'direct', bypassing slf4j.
    	final org.apache.log4j.Level newLevel;
    	if (enabled) {
    		newLevel = org.apache.log4j.Level.ALL;
    	} else {
    		newLevel = org.apache.log4j.Level.INFO;
    	}
    		
    	org.apache.log4j.LogManager.getRootLogger().setLevel(newLevel);
    }

	/**
	 * @deprecated replaced by {@link org.slf4j.Logger#isInfoEnabled()}.
	 *             Functionality of this method is delegated there.
	 */
    @Deprecated
	public static boolean isInfoEnabled() {
        return Logger.isInfoEnabled();
    }

	/**
	 * @deprecated replaced by {@link org.slf4j.Logger#isWarnEnabled()}.
	 *             Functionality of this method is delegated there.
	 */
    @Deprecated
	public static boolean isWarnEnabled() {
        return Logger.isWarnEnabled();
    }

	/**
	 * @deprecated replaced by {@link org.slf4j.Logger#debug(String)}.
	 *             Functionality of this method is delegated there.
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
        final List<String> args = new ArrayList<String>();
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