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

package org.jivesoftware.messenger;

import org.jivesoftware.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.*;

import org.dom4j.Document;

import javax.naming.InitialContext;

/**
 * This class controls Jive properties. Jive properties are only meant to be set and retrieved
 * by core Jive classes.<p>
 * <p/>
 * All properties are stored in the file <tt>jive_config.xml</tt> which is located in the
 * <tt>messengerHome/conf</tt> directory. The location of that directory should be specified one of
 * three ways:
 * <ol>
 * <li>Set a Java system property named <tt>messengerHome</tt> with the full path to your
 * messengerHome directory.
 * <li>Indicate its value in the <tt>messenger_init.xml</tt> file. This
 * is a simple xml file that should look something like:<br>
 * <tt><messengerHome>c:\JiveMessenger</messengerHome></tt> (Windows) <br>
 * or <br>
 * <tt><messengerHome>/var/JiveMessenger</messengerHome></tt> (Unix) <p>
 * <p/>
 * The file must be in your classpath so that it can be loaded by Java's classloader.
 * <li>Use another class in your VM to set the <tt>JiveGlobals.messengerHome</tt> variable.
 * This must be done before the rest of Jive starts up, for example: in a servlet that
 * is set to run as soon as the appserver starts up.
 * </ol>
 * <p/>
 * All property names must be in the form <code>prop.name</code> - parts of the name must
 * be seperated by ".". The value can be any valid String, including strings with line breaks.
 */
public class JiveGlobals {

    private static String JIVE_CONFIG_FILENAME = "jive-messenger.xml";

    private static final String DEFAULT_CHAR_ENCODING = "UTF-8";

    /**
     * Location of the jiveHome directory. All configuration files should be
     * located here.
     */
    public static String messengerHome = null;

    public static boolean failedLoading = false;

    private static XMLProperties xmlProperties = null;
    private static JiveProperties properties = null;

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
            if (properties != null) {
                String language = (String)properties.get("locale.language");
                if (language == null) {
                    language = "";
                }
                String country = (String)properties.get("locale.country");
                if (country == null) {
                    country = "";
                }
                // If no locale info is specified, return the system default Locale.
                if (language.equals("") && country.equals("")) {
                    locale = Locale.getDefault();
                }
                else {
                    locale = new Locale(language, country);
                }
            }
            else {
                return Locale.getDefault();
            }
        }
        return locale;
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
        setProperty("locale.country", locale.getCountry());
        setProperty("locale.language", locale.getLanguage());
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
     *
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
        if (characterEncoding == null) {
            if (properties != null) {
                String charEncoding = (String)properties.get("locale.characterEncoding");
                if (charEncoding != null) {
                    characterEncoding = charEncoding;
                }
                else {
                    // The default encoding is ISO-8859-1. We use the version of
                    // the encoding name that seems to be most widely compatible.
                    characterEncoding = DEFAULT_CHAR_ENCODING;
                }
            }
            else {
                return DEFAULT_CHAR_ENCODING;
            }
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
     *
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
        setProperty("locale.characterEncoding", characterEncoding);
    }

    /**
     * Returns the global TimeZone used by Jive. The default is the VM's
     * default time zone.
     *
     * @return the global time zone used by Jive.
     */
    public static TimeZone getTimeZone() {
        if (timeZone == null) {
            if (properties != null) {
                String timeZoneID = (String)properties.get("locale.timeZone");
                if (timeZoneID == null) {
                    timeZone = TimeZone.getDefault();
                }
                else {
                    timeZone = TimeZone.getTimeZone(timeZoneID);
                }
            }
            else {
                return TimeZone.getDefault();
            }
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
        setProperty("locale.timeZone", timeZone.getID());
    }

    /**
     * Formats a Date object to return a date using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the date.
     */
    public static String formatDate(Date date) {
        if (dateFormat == null) {
            if (properties != null) {
                dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, getLocale());
                dateFormat.setTimeZone(getTimeZone());
            }
            else {
                DateFormat instance = DateFormat.getDateInstance(DateFormat.MEDIUM, getLocale());
                instance.setTimeZone(getTimeZone());
                return instance.format(date);
            }
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
        if (dateTimeFormat == null) {
            if (properties != null) {
                dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                        DateFormat.MEDIUM, getLocale());
                dateTimeFormat.setTimeZone(getTimeZone());
            }
            else {
                DateFormat instance = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                        DateFormat.MEDIUM, getLocale());
                instance.setTimeZone(getTimeZone());
                return instance.format(date);
            }
        }
        return dateTimeFormat.format(date);
    }

    /**
     * Returns the location of the <code>messengerHome</code> directory.
     *
     * @return the location of the messengerHome dir.
     */
    public static String getMessengerHome() {
        if (messengerHome == null) {
            loadSetupProperties();
        }
        return messengerHome;
    }

    /**
     * Sets the location of the <code>messengerHome</code> directory. This method
     * is only intended to be used during setup and should <b>not</b> set called
     * in normal Messenger operations.
     */
    public static void setMessengerHome(String mHome) {
        properties = null;
        xmlProperties = null;
        failedLoading = false;
        messengerHome = mHome;
        locale = null;
        timeZone = null;
        characterEncoding = null;
        dateFormat = null;
        dateTimeFormat = null;

        loadSetupProperties();
        System.err.println("Warning - messengerHome is being reset to " + mHome +
                "! Resetting the messengerHome is a normal part of the setup process, " +
                "however it should not occur during the normal operations of Jive Messenger.");
    }

    /**
     * Returns a local property. Local properties are stored in the file
     * <tt>jive-messenger.xml</tt> that exists in the <tt>jiveMessenger/conf</tt> directory.
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
    public static String getXMLProperty(String name) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }

        // jiveHome not loaded?
        if (xmlProperties == null) {
            return null;
        }

        return xmlProperties.getProperty(name);
    }

    /**
     * Returns an integer value local property. Local properties are stored in the file
     * <tt>jive_forums.xml</tt> that exists in the <tt>jiveHome</tt> directory.
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
     * If the specified property can't be found, or if the value is not a number, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property could not be loaded or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static int getXMLProperty(String name, int defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) { }
        }
        return defaultValue;
    }

    /**
     * Sets a local property. If the property doesn't already exists, a new
     * one will be created. Local properties are stored in the file
     * <tt>jive_forums.xml</tt> that exists in the <tt>jiveHome</tt> directory.
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
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     */
    public static void setXMLProperty(String name, String value) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }

        // jiveHome not loaded?
        if (xmlProperties != null) {
            xmlProperties.setProperty(name, value);
        }
    }

    /**
     * Sets multiple local properties at once. If a property doesn't already exists, a new
     * one will be created. Local properties are stored in the file
     * <tt>jive_forums.xml</tt> that exists in the <tt>jiveHome</tt> directory.
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
     * @param propertyMap a map of properties, keyed on property name.
     */
    public static void setXMLProperties(Map propertyMap) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }

        if (xmlProperties != null) {
            xmlProperties.setProperties(propertyMap);
        }
    }

    /**
     * Return all immediate children property values of a parent local property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt> (the value of <tt>C.D</tt> would not be returned using this method).<p>
     *
     * Local properties are stored in the file <tt>jive_forums.xml</tt> that exists
     * in the <tt>jiveHome</tt> directory. Properties are always specified as "foo.bar.prop",
     * which would map to the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     *
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public static List getXMLProperties(String parent) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }

        // jiveHome not loaded?
        if (xmlProperties == null) {
            return Collections.EMPTY_LIST;
        }

        String[] propNames = xmlProperties.getChildrenProperties(parent);
        List values = new ArrayList();
        for (int i = 0; i < propNames.length; i++) {
            String propName = propNames[i];
            String value = getProperty(parent + "." + propName);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * Deletes a locale property. If the property doesn't exist, the method
     * does nothing.
     *
     * @param name the name of the property to delete.
     */
    public static void deleteXMLProperty(String name) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }
        xmlProperties.deleteProperty(name);
    }
    /**
     * Returns a Jive property.
     *
     * @param name the name of the property to return.
     * @return the property value specified by name.
     */
    public static String getProperty(String name) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }

        return (String)properties.get(name);
    }

    /**
     * Returns a Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return the property value specified by name.
     */
    public static String getProperty(String name, String defaultValue) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }

        String value = (String)properties.get(name);

        if (value != null) {
            return value;
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Returns an integer value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static int getIntProperty(String name, int defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) { }
        }
        return defaultValue;
    }

    /**
     * Returns a boolean value Jive property.
     *
     * @param name the name of the property to return.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public static boolean getBooleanProperty(String name) {
        return Boolean.valueOf(getProperty(name)).booleanValue();
    }

    /**
     * Returns a boolean value Jive property. If the property doesn't exist, the <tt>defaultValue</tt>
     * will be returned.
     *
     * If the specified property can't be found, or if the value is not a number, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return true if the property value exists and is set to <tt>"true"</tt> (ignoring case).
     *      Otherwise <tt>false</tt> is returned.
     */
    public static boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            return Boolean.valueOf(getProperty(name)).booleanValue();
        }
        else {
            return defaultValue;
        }
    }

    /**
     * Return all immediate children property names of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt> (<tt>C.D</tt> would not be returned using this method).<p>
     *
     * @return a List of all immediate children property names (Strings).
     */
    public static List getPropertyNames(String parent) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }
        return new ArrayList(properties.getChildrenNames(parent));
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then
     * the immediate child properties of <tt>X.Y</tt> are <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and
     * <tt>X.Y.C</tt> (the value of <tt>X.Y.C.D</tt> would not be returned using this method).<p>
     *
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public static List<String> getProperties(String parent) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }

        Collection<String> propertyNames = properties.getChildrenNames(parent);
        List<String> values = new ArrayList<String>();
        for (Iterator i=propertyNames.iterator(); i.hasNext(); ) {
            String propName = (String)i.next();
            String value = getProperty(propName);
            if (value != null) {
                values.add(value);
            }
        }

        return values;
    }

    /**
     * Returns all Jive property names.
     *
     * @return a List of all property names (Strings).
     */
    public static List getPropertyNames() {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }
        return new ArrayList(properties.getPropertyNames());
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created.
     *
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     */
    public static void setProperty(String name, String value) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }
        properties.put(name, value);
    }

   /**
     * Sets multiple Jive properties at once. If a property doesn't already exists, a new
     * one will be created.
     *
     * @param propertyMap a map of properties, keyed on property name.
     */
    public static void setProperties(Map propertyMap) {
        if (properties == null) {
            properties = JiveProperties.getInstance();
        }

        properties.putAll(propertyMap);
    }

    /**
     * Deletes a Jive property. If the property doesn't exist, the method
     * does nothing. All children of the property will be deleted as well.
     *
     * @param name the name of the property to delete.
     */
    public static void deleteProperty(String name) {
        if (properties == null) {
            properties = JiveProperties.getInstance();;
        }
        properties.remove(name);
    }

   /**
    * Allows the name of the local config file name to be changed. The
    * default is "jive_startup.xml".
    *
    * @param configName the name of the config file.
    */
    public static void setConfigName(String configName) {
        JIVE_CONFIG_FILENAME = configName;
    }

    /**
     * Loads properties if necessary. Property loading must be done lazily so
     * that we give outside classes a chance to set <tt>messengerHome</tt>.
     */
    private synchronized static void loadSetupProperties() {
        if (failedLoading) {
            return;
        }
        if (xmlProperties == null) {
            // If jiveHome is still null, no outside process has set it and
            // we have to attempt to load the value from jive_init.xml,
            // which must be in the classpath.
            if (messengerHome == null) {
                messengerHome = new InitPropLoader().getMessengerHome();
            }
            // If that failed, try loading it from JNDI
            if (messengerHome == null) {
                try {
                    InitialContext context = new InitialContext();
                    messengerHome = (String)context.lookup("java:comp/env/messengerHome");
                }
                catch (Exception e) { }
            }
            // Finally, try to load it jiveHome as a system property.
            if (messengerHome == null) {
                messengerHome = System.getProperty("messengerHome");
            }

            if(messengerHome == null){
                try {
                    messengerHome = new File("..").getCanonicalPath();
                    if(!new File(messengerHome, "conf/jive-messenger.xml").exists()){
                        messengerHome = null;
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

             if(messengerHome == null){
                try {
                    messengerHome = new File("").getCanonicalPath();
                    if(!new File(messengerHome, "conf/jive-messenger.xml").exists()){
                        messengerHome = null;
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // If still null, finding messengerHome failed.
            if (messengerHome == null) {
                failedLoading = true;
                StringBuffer msg = new StringBuffer();
                msg.append("Critical Error! The messengerHome directory could not be loaded, \n");
                msg.append("which will prevent the application from working correctly.\n\n");
                msg.append("You must set messengerHome in one of four ways:\n");
                msg.append("    1) Set a servlet init parameter named messengerHome.\n");
                msg.append("    2) Add a messenger_init.xml file to your classpath, which points \n ");
                msg.append("       to messengerHome. Normally, this file will be in WEB-INF/classes.\n");
                msg.append("    3) Set the JNDI value \"java:comp/env/messengerHome\" with a String \n");
                msg.append("       that points to your messengerHome directory. \n");
                msg.append("    4) Set the Java system property \"messengerHome\".\n\n");
                msg.append("Further instructions for setting messengerHome can be found in the \n");
                msg.append("installation documentation.");
                System.err.println(msg.toString());
                return;
            }
            // Create a manager with the full path to the xml config file.
            try {
                // Do a permission check on the jiveHome directory:
                File mh = new File(messengerHome);
                if (!mh.exists()) {
                    Log.error("Error - the specified messengerHome directory does not exist (" + messengerHome + ")");
                }
                else {
                    if (!mh.canRead() || !mh.canWrite()) {
                        Log.error("Error - the user running this Jive application can not read and write to the "
                                + "specified jiveHome directory (" + messengerHome + "). Please grant the executing user "
                                + "read and write perms.");
                    }
                }
                xmlProperties = new XMLProperties(messengerHome + File.separator + "conf" +
                        File.separator + JIVE_CONFIG_FILENAME);
            }
            catch (IOException ioe) {
                Log.error(ioe);
                failedLoading = true;
                return;
            }
        }
    }
}

/**
 * A very small class to load the messenger_init.properties file. The class is
 * needed since loading files from the classpath in a static context often
 * fails.
 */
class InitPropLoader {

    public String getMessengerHome() {
        String messengerHome = null;
        InputStream in = null;
        try {
            in = getClass().getResourceAsStream("/messenger_init.xml");
            if (in != null) {
                Document doc = XPPReader.parseDocument(new InputStreamReader(in), this.getClass());
                messengerHome = doc.getRootElement().getText();
            }
        }
        catch (Exception e) {
            Log.error("Error loading messenger_init.xml to find messengerHome.", e);
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
        if (messengerHome != null) {
            messengerHome = messengerHome.trim();
            // Remove trailing slashes.
            while (messengerHome.endsWith("/") || messengerHome.endsWith("\\")) {
                messengerHome = messengerHome.substring(0, messengerHome.length() - 1);
            }
        }
        if ("".equals(messengerHome)) {
            messengerHome = null;
        }
        return messengerHome;
    }
}