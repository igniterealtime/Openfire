/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.XMLProperties;
import org.jivesoftware.util.XPPReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.dom4j.Document;

/**
 * This class controls Jive properties. Jive properties are only meant to be set and retrieved
 * by core Jive classes.<p>
 * <p/>
 * All properties are stored in the file <tt>jive_config.xml</tt> which is located in the
 * <tt>jiveHome/config</tt> directory. The location of that* directory should be specified one of
 * three ways:
 * <ol>
 * <li>Set a Java system property named <tt>jiveHome</tt> with the full path to your
 * jiveHome directory.
 * <li>Indicate its value in the <tt>jive_init.xml</tt> file. This
 * is a simple xml file that should look something like:<br>
 * <tt><jiveHome>c:\some\directory\jiveHome</jiveHome></tt> (Windows) <br>
 * or <br>
 * <tt><jiveHome>/home/some/directory/jiveHome</jiveHome></tt> (Unix) <p>
 * <p/>
 * The file must be in your classpath so that it can be loaded by Java's classloader.
 * <li>Use another class in your VM to set the <tt>JiveGlobals.jiveHome</tt> variable.
 * This must be done before the rest of Jive starts up, for example: in a servlet that
 * is set to run as soon as the appserver starts up.
 * </ol>
 * <p/>
 * All Jive property names must be in the form <code>prop.name</code> - parts of the name must
 * be seperated by ".". The value can be any valid String, including strings with line breaks.
 *
 * @author Iain Shigeoka
 */
public class JiveGlobals {

    private static final String JIVE_CONFIG_FILENAME = "jive-messenger.xml";

    /**
     * Location of the jiveHome directory. All configuration files should be
     * located here.
     */
    public static String jiveHome = null;

    public static boolean failedLoading = false;

    /**
     * XML properties to actually get and set the Jive properties.
     */
    private static XMLProperties properties = null;

    private static Locale locale = null;
    private static TimeZone timeZone = null;
    private static String characterEncoding = null;
    private static DateFormat dateFormat = null;
    private static DateFormat dateTimeFormat = null;

    /**
     * Returns the global Locale used by Jive. A locale specifies language
     * and country codes, and is used for internationalization. The default
     * locale is system dependant - Locale.getDefault().
     *
     * @return the global locale used by Jive.
     */
    public static Locale getLocale() {
        if (locale == null) {
            loadProperties();
        }

        if (locale != null) {
            return locale;
        }
        else {
            // we don't want the locale to be null ever so just return the system default locale
            return Locale.getDefault();
        }
    }

    /**
     * Sets the global locale used by Jive. A locale specifies language
     * and country codes, and is used for formatting dates and numbers.
     * The default locale is Locale.US.
     *
     * @param newLocale the global Locale for Jive.
     */
    public static void setLocale(Locale newLocale) {
        locale = newLocale;
        // Save values to Jive properties.
        setJiveProperty("locale.country", locale.getCountry());
        setJiveProperty("locale.language", locale.getLanguage());
        // Reset the date formatter objects
        dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
        dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                DateFormat.MEDIUM, locale);
        dateFormat.setTimeZone(timeZone);
        dateTimeFormat.setTimeZone(timeZone);
    }

    /**
     * Returns the character set that Jive uses for encoding. This
     * is used for displaying content in skins, sending email watch
     * updates, etc. The default encoding is ISO-8895-1, which is suitable for
     * most Latin languages. If you need to support double byte character sets
     * such as Chinese or Japanese, it's recommend that you use utf-8
     * as the charset (Unicode). Unicode offers simultaneous support for a
     * large number of languages and is easy to convert into native charsets
     * as necessary. You may also specifiy any other charset that is supported
     * by your JVM. A list of encodings supported by the Sun JVM can be found
     * <a href="http://java.sun.com/j2se/1.3/docs/guide/intl/encoding.doc.html">
     * here.</a><p>
     * <p/>
     * In order for a particular encoding to work (such as Unicode), your
     * application server and database may need to be specially configured.
     * Please consult your server documentation for more information. For
     * example, SQLServer has a special column type for Unicode text, and the
     * Resin application server can be configured to use a custom charset by
     * adding a &lt;character-encoding&gt; element to the web.xml/resin.conf
     * file. Any Servlet 2.3 compliant application servers also supports the
     * method HttpServletRequest.setCharacterEncoding(String). A Servlet 2.3
     * Filter called SetCharacterEncodingFilter is installed in the default
     * Jive Forums web.xml file, which  will set the incoming character encoding
     * to the one reported by this method.
     *
     * @return the global Jive character encoding.
     */
    public static String getCharacterEncoding() {
        if (locale == null) {
            loadProperties();
        }
        return characterEncoding;
    }

    /**
     * Sets the character set that Jive uses for encoding. This
     * is used for displaying content in skins, sending email watch
     * updates, etc. The default encoding is ISO-8859-1, which is suitable for
     * most Latin languages. If you need to support double byte character sets
     * such as Chinese or Japanese, it's recommend that you use utf-8
     * as the charset (Unicode). Unicode offers simultaneous support for a
     * large number of languages and is easy to convert into native charsets
     * as necessary. You may also specifiy any other charset that is supported
     * by your JVM. A list of encodings supported by the Sun JVM can be found
     * <a href="http://java.sun.com/j2se/1.3/docs/guide/intl/encoding.doc.html">
     * here.</a><p>
     * <p/>
     * In order for a particular encoding to work (such as Unicode), your
     * application server and database may need to be specially configured.
     * Please consult your server documentation for more information. For
     * example, SQLServer has a special column type for Unicode text, and the
     * Resin application server can be configured to use a custom charset by
     * adding a &lt;character-encoding&gt; element to the web.xml/resin.conf
     * file. Any Servlet 2.3 compliant application servers also supports the
     * method HttpServletRequest.setCharacterEncoding(String). A Servlet 2.3
     * Filter called SetCharacterEncodingFilter is installed in the default
     * Jive Forums web.xml file, which  will set the incoming character encoding
     * to the one reported by this method.
     *
     * @param characterEncoding the global Jive character encoding.
     */
    public static void setCharacterEncoding(String characterEncoding) {
        JiveGlobals.characterEncoding = characterEncoding;
        setJiveProperty("locale.characterEncoding", characterEncoding);
    }

    /**
     * Returns the global TimeZone used by Jive. The default is the VM's
     * default time zone.
     *
     * @return the global time zone used by Jive.
     */
    public static TimeZone getTimeZone() {
        if (locale == null) {
            loadProperties();
        }
        return timeZone;
    }

    /**
     * Sets the global time zone used by Jive. The default time zone is the VM's
     * time zone.
     */
    public static void setTimeZone(TimeZone newTimeZone) {
        timeZone = newTimeZone;
        dateFormat.setTimeZone(timeZone);
        dateTimeFormat.setTimeZone(timeZone);
        setJiveProperty("locale.timeZone", timeZone.getID());
    }

    /**
     * Formats a Date object to return a date using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the date.
     */
    public static String formatDate(Date date) {
        if (locale == null) {
            loadProperties();
        }
        return dateFormat.format(date);
    }

    /**
     * Formats a Date object to return a date and time using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the date and time.
     */
    public static String formatDateTime(Date date) {
        if (locale == null) {
            loadProperties();
        }
        return dateTimeFormat.format(date);
    }

    /**
     * Returns the location of the <code>jiveHome</code> directory.
     *
     * @return the location of the jiveHome dir.
     */
    public static String getJiveHome() {
        if (jiveHome == null) {
            loadProperties();
        }
        return jiveHome;
    }

    /**
     * Returns a Jive property. Jive properties are stored in the file
     * <tt>jive_config.xml</tt> that exists in the <tt>jiveHome</tt> directory.
     * Properties are always specified as "foo.bar.prop", which would map to
     * the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name the name of the property to return.
     * @return the property value specified by name.
     */
    public static String getJiveProperty(String name) {
        if (properties == null) {
            loadProperties();
        }

        // jiveHome not loaded?
        if (properties == null) {
            return null;
        }

        return properties.getProperty(name);
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created. Jive properties are stored in the file
     * <tt>jive_config.xml</tt> that exists in the <tt>jiveHome</tt> directory.
     * Properties are always specified as "foo.bar.prop", which would map to
     * the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * @param name  the name of the property being set.
     * @param value the value of the property being set.
     */
    public static void setJiveProperty(String name, String value) {
        if (properties == null) {
            loadProperties();
        }

        if (properties != null) {
            properties.setProperty(name, value);
        }
    }

    /**
     * Deletes a Jive property. If the property doesn't exist, the method
     * does nothing.
     *
     * @param name the name of the property to delete.
     */
    public static void deleteJiveProperty(String name) {
        if (properties == null) {
            loadProperties();
        }
        if (properties != null) {
            properties.deleteProperty(name);
        }
    }

    /**
     * Loads properties if necessary. Property loading must be done lazily so
     * that we give outside classes a chance to set <tt>jiveHome</tt>.
     */
    private synchronized static void loadProperties() {
        if (failedLoading) {
            return;
        }
        if (properties == null) {
            // First, try to load it jiveHome as a system property.
            if (jiveHome == null) {
                jiveHome = System.getProperty("jiveHome");
            }

            // If we still don't have jiveHome, let's assume this is standalone
            // and just look for jiveHome in a standard sub-dir location and verify
            // by looking for the config file
            if (jiveHome == null) {
                jiveHome = "..";
                // Create a manager with the full path to the xml config file.
                try {
                    properties = new XMLProperties(jiveHome + File.separator +
                            "config" + File.separator +
                            JIVE_CONFIG_FILENAME);
                }
                catch (IOException ioe) {
                    jiveHome = null;
                }
            }

            // If jiveHome is still null, no outside process has set it and
            // we have to attempt to load the value from messenger_init.xml,
            // which must be in the classpath.
            if (jiveHome == null) {
                jiveHome = new InitPropLoader().getJiveHome();
            }

            if (jiveHome == null) {
                Log.error("Jive Home was never set. In order to continue, Jive Home will need to be set");
                failedLoading = true;
                return;
            }
            else {
                // Create a manager with the full path to the xml config file.
                try {
                    properties = new XMLProperties(jiveHome + File.separator +
                            "config" + File.separator +
                            JIVE_CONFIG_FILENAME);
                }
                catch (IOException ioe) {
                    Log.error(ioe);
                    failedLoading = true;
                    return;
                }
            }
        }

        if (locale == null) {
            String language = properties.getProperty("locale.language");
            if (language == null) {
                language = "";
            }
            String country = properties.getProperty("locale.country");
            if (country == null) {
                country = "";
            }
            // If no locale info is specified, default to system default Locale
            if (language.equals("") && country.equals("")) {
                locale = Locale.getDefault();
            }
            else {
                locale = new Locale(language, country);
            }
            String charEncoding = properties.getProperty("locale.characterEncoding");
            if (charEncoding != null) {
                characterEncoding = charEncoding;
            }
            else {
                // The default encoding is ISO-8859-1. We use the version of
                // the encoding name that seems to be most widely compatible.
                characterEncoding = "ISO-8859-1";
            }
            String timeZoneID = properties.getProperty("locale.timeZone");
            if (timeZoneID == null) {
                timeZone = TimeZone.getDefault();
            }
            else {
                timeZone = TimeZone.getTimeZone(timeZoneID);
            }
            dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
            dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.MEDIUM, locale);
            dateFormat.setTimeZone(timeZone);
            dateTimeFormat.setTimeZone(timeZone);
        }
    }
}

/**
 * A very small class to load the messenger_init.properties file. The class is
 * needed since loading files from the classpath in a static context often
 * fails.
 */
class InitPropLoader {

    public String getJiveHome() {
        String jiveHome = null;
        InputStream in = null;
        try {
            in = getClass().getResourceAsStream("/messenger_init.xml");
            if (in != null) {
                Document doc = XPPReader.parseDocument(new InputStreamReader(in), this.getClass());
                jiveHome = doc.getRootElement().getText();
            }
        }
        catch (Exception e) {
            Log.error("Error loading messenger_init.xml to find jiveHome.", e);
        }
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            }
            catch (Exception e) {
            }
        }
        if (jiveHome != null) {
            jiveHome = jiveHome.trim();
            // Remove trailing slashes.
            while (jiveHome.endsWith("/") || jiveHome.endsWith("\\")) {
                jiveHome = jiveHome.substring(0, jiveHome.length() - 1);
            }
        }
        if ("".equals(jiveHome)) {
            jiveHome = null;
        }
        return jiveHome;
    }
}
