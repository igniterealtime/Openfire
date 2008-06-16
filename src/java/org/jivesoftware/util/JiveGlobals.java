/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import org.jivesoftware.database.DbConnectionManager;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Controls Jive properties. Jive properties are only meant to be set and retrieved
 * by core Jive classes. Some properties may be stored in XML format while others in the
 * database.<p>
 *
 * When starting up the application this class needs to be configured so that the initial
 * configuration of the application may be loaded from the configuration file. The configuration
 * file holds properties stored in XML format, database configuration and user authentication
 * configuration. Use {@link #setHomeDirectory(String)} and {@link #setConfigName(String)} for
 * setting the home directory and path to the configuration file.<p>
 *
 * XML property names must be in the form <code>prop.name</code> - parts of the name must
 * be seperated by ".". The value can be any valid String, including strings with line breaks.
 */
public class JiveGlobals {

    private static String JIVE_CONFIG_FILENAME = "conf" + File.separator + "openfire.xml";

    /**
     * Location of the jiveHome directory. All configuration files should be
     * located here.
     */
    private static String home = null;

    public static boolean failedLoading = false;

    private static XMLProperties xmlProperties = null;
    private static JiveProperties properties = null;

    private static Locale locale = null;
    private static TimeZone timeZone = null;
    private static DateFormat dateFormat = null;
    private static DateFormat dateTimeFormat = null;
    private static DateFormat timeFormat = null;

    /**
     * Returns the global Locale used by Jive. A locale specifies language
     * and country codes, and is used for internationalization. The default
     * locale is system dependant - Locale.getDefault().
     *
     * @return the global locale used by Jive.
     */
    public static Locale getLocale() {
        if (locale == null) {
            if (xmlProperties != null) {
                String [] localeArray;
                String localeProperty = xmlProperties.getProperty("locale");
                if (localeProperty != null) {
                    localeArray = localeProperty.split("_");
                }
                else {
                    localeArray = new String[] {"", ""};
                }

                String language = localeArray[0];
                if (language == null) {
                    language = "";
                }
                String country = "";
                if (localeArray.length == 2) {
                    country = localeArray[1];
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
        setXMLProperty("locale", locale.toString());

        // Reset the date formatter objects
        timeFormat = null;
        dateFormat = null;
        dateTimeFormat = null;
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
                String timeZoneID = properties.get("locale.timeZone");
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
     *
     * @param newTimeZone Time zone to set.
     */
    public static void setTimeZone(TimeZone newTimeZone) {
        timeZone = newTimeZone;
        if (timeFormat != null) {
            timeFormat.setTimeZone(timeZone);
        }
        if (dateFormat != null) {
            dateFormat.setTimeZone(timeZone);
        }
        if (dateTimeFormat != null) {
            dateTimeFormat.setTimeZone(timeZone);
        }
        setProperty("locale.timeZone", timeZone.getID());
    }

    /**
     * Formats a Date object to return a time using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the time.
     */
    public static String formatTime(Date date) {
        if (timeFormat == null) {
            if (properties != null) {
                timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, getLocale());
                timeFormat.setTimeZone(getTimeZone());
            }
            else {
                DateFormat instance = DateFormat.getTimeInstance(DateFormat.SHORT, getLocale());
                instance.setTimeZone(getTimeZone());
                return instance.format(date);
            }
        }
        return timeFormat.format(date);
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
     * Returns the location of the <code>home</code> directory.
     *
     * @return the location of the home dir.
     */
    public static String getHomeDirectory() {
        if (xmlProperties == null) {
            loadSetupProperties();
        }
        return home;
    }

    /**
     * Sets the location of the <code>home</code> directory. The directory must exist and the
     * user running the application must have read and write permissions over the specified
     * directory.
     *
     * @param pathname the location of the home dir.
     */
    public static void setHomeDirectory(String pathname) {
        File mh = new File(pathname);
        // Do a permission check on the new home directory
        if (!mh.exists()) {
            Log.error("Error - the specified home directory does not exist (" + pathname + ")");
        }
        else if (!mh.canRead() || !mh.canWrite()) {
                Log.error("Error - the user running this application can not read " +
                        "and write to the specified home directory (" + pathname + "). " +
                        "Please grant the executing user read and write permissions.");
        }
        else {
            home = pathname;
        }
    }

    /**
     * Returns a local property. Local properties are stored in the file defined in
     * <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt> directory.
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

        // home not loaded?
        if (xmlProperties == null) {
            return null;
        }

        return xmlProperties.getProperty(name);
    }

    /**
     * Returns a local property. Local properties are stored in the file defined in
     * <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt> directory.
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
     * If the specified property can't be found, the <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue the default value for the property.
     * @return the property value specified by name.
     */
    public static String getXMLProperty(String name, String defaultValue) {
        if (xmlProperties == null) {
            loadSetupProperties();
        }

        // home not loaded?
        if (xmlProperties == null) {
            return null;
        }

        String value = xmlProperties.getProperty(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Returns an integer value local property. Local properties are stored in the file defined in
     * <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt> directory.
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
        String value = getXMLProperty(name);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a boolean value local property. Local properties are stored in the
     * file defined in <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt>
     * directory. Properties are always specified as "foo.bar.prop", which would map to
     * the following entry in the XML file:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     *
     * If the specified property can't be found, the <tt>defaultValue</tt> will be returned.
     * If the property is found, it will be parsed using {@link Boolean#valueOf(String)}.  
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property could not be loaded or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static boolean getXMLProperty(String name, boolean defaultValue) {
        String value = getXMLProperty(name);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        return defaultValue;
    }

    /**
     * Sets a local property. If the property doesn't already exists, a new
     * one will be created. Local properties are stored in the file defined in
     * <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt> directory.
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
     * one will be created. Local properties are stored in the file defined in
     * <tt>JIVE_CONFIG_FILENAME</tt> that exists in the <tt>home</tt> directory.
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
    public static void setXMLProperties(Map<String, String> propertyMap) {
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
     * Local properties are stored in the file defined in <tt>JIVE_CONFIG_FILENAME</tt> that exists
     * in the <tt>home</tt> directory. Properties are always specified as "foo.bar.prop",
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
        List<String> values = new ArrayList<String>();
        for (String propName : propNames) {
            String value = getXMLProperty(parent + "." + propName);
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
            if (isSetupMode()) {
                return null;
            }
            properties = JiveProperties.getInstance();
        }
        return properties.get(name);
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
            if (isSetupMode()) {
                return defaultValue;
            }
            properties = JiveProperties.getInstance();
        }
        String value = properties.get(name);
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
            catch (NumberFormatException nfe) {
                // Ignore.
            }
        }
        return defaultValue;
    }

    /**
     * Returns a long value Jive property. If the specified property doesn't exist, the
     * <tt>defaultValue</tt> will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or <tt>defaultValue</tt>.
     */
    public static long getLongProperty(String name, long defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Long.parseLong(value);
            }
            catch (NumberFormatException nfe) {
                // Ignore.
            }
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
        return Boolean.valueOf(getProperty(name));
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
            return Boolean.valueOf(value);
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
     * @param parent Parent "node" to find the children of.
     * @return a List of all immediate children property names (Strings).
     */
    public static List<String> getPropertyNames(String parent) {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<String>(properties.getChildrenNames(parent));
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
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }

        Collection<String> propertyNames = properties.getChildrenNames(parent);
        List<String> values = new ArrayList<String>();
        for (String propertyName : propertyNames) {
            String value = getProperty(propertyName);
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
    public static List<String> getPropertyNames() {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<String>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<String>(properties.getPropertyNames());
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
            if (isSetupMode()) {
                return;
            }
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
    public static void setProperties(Map<String, String> propertyMap) {
        if (properties == null) {
            if (isSetupMode()) {
                return;
            }
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
            if (isSetupMode()) {
                return;
            }
            properties = JiveProperties.getInstance();
        }
        properties.remove(name);
    }

    /**
     * Convenience routine to migrate an XML property into the database
     * storage method.  Will check for the XML property being null before
     * migrating.
     *
     * @param name the name of the property to migrate.
     */
    public static void migrateProperty(String name) {
        if (isSetupMode()) {
            return;
        }
        if (getXMLProperty(name) != null) {
            if (getProperty(name) == null) {
                Log.debug("JiveGlobals: Migrating XML property '"+name+"' into database.");
                setProperty(name, getXMLProperty(name));
                deleteXMLProperty(name);
            }
            else if (getProperty(name).equals(getXMLProperty(name))) {
                Log.debug("JiveGlobals: Deleting duplicate XML property '"+name+"' that is already in database.");
                deleteXMLProperty(name);
            }
            else if (!getProperty(name).equals(getXMLProperty(name))) {
                Log.warn("Property '"+name+"' as specified in openfire.xml differs from what is stored in the database.  Please make property changes in the database instead of openfire.xml.");
            }
        }
    }

   /**
    * Allows the name of the local config file name to be changed. The
    * default is "openfire.xml".
    *
    * @param configName the name of the config file.
    */
    public static void setConfigName(String configName) {
        JIVE_CONFIG_FILENAME = configName;
    }

    /**
     * Returns the name of the local config file name.
     *
     * @return the name of the config file.
     */
    static String getConfigName() {
        return JIVE_CONFIG_FILENAME;
    }

    /**
     * Returns true if in setup mode. A false value means that setup has been completed
     * or that a connection to the database was possible to properies stored in the
     * datbase can be retrieved now. The latter means that once the database settings
     * during the setup was done a connection to the datbase should be available thus
     * properties stored from a previous setup will be available.
     *
     * @return true if in setup mode.
     */
    private static boolean isSetupMode() {
        return !Boolean.valueOf(JiveGlobals.getXMLProperty("setup"));
    }

    /**
     * Loads properties if necessary. Property loading must be done lazily so
     * that we give outside classes a chance to set <tt>home</tt>.
     */
    private synchronized static void loadSetupProperties() {
        if (xmlProperties == null) {
            // If home is null then log that the application will not work correctly
            if (home == null && !failedLoading) {
                failedLoading = true;
                StringBuilder msg = new StringBuilder();
                msg.append("Critical Error! The home directory has not been configured, \n");
                msg.append("which will prevent the application from working correctly.\n\n");
                System.err.println(msg.toString());
            }
            // Create a manager with the full path to the xml config file.
            else {
                try {
                    xmlProperties = new XMLProperties(home + File.separator + getConfigName());
                }
                catch (IOException ioe) {
                    Log.error(ioe);
                    failedLoading = true;
                }
            }
        }
    }
}