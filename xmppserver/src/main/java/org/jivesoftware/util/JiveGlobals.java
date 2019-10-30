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

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TimerTask;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * be separated by ".". The value can be any valid String, including strings with line breaks.
 */
public class JiveGlobals {

    private static final Logger Log = LoggerFactory.getLogger(JiveGlobals.class);

    private static String JIVE_CONFIG_FILENAME = "conf" + File.separator + "openfire.xml";
    
    private static final String JIVE_SECURITY_FILENAME = "conf" + File.separator + "security.xml";
    private static final String ENCRYPTED_PROPERTY_NAME_PREFIX = "encrypt.";
    private static final String ENCRYPTED_PROPERTY_NAMES = ENCRYPTED_PROPERTY_NAME_PREFIX + "property.name";
    private static final String ENCRYPTION_ALGORITHM = ENCRYPTED_PROPERTY_NAME_PREFIX + "algorithm";
    private static final String ENCRYPTION_KEY_CURRENT = ENCRYPTED_PROPERTY_NAME_PREFIX + "key.current";
    private static final String ENCRYPTION_KEY_NEW = ENCRYPTED_PROPERTY_NAME_PREFIX + "key.new";
    private static final String ENCRYPTION_KEY_OLD = ENCRYPTED_PROPERTY_NAME_PREFIX + "key.old";
    private static final String ENCRYPTION_ALGORITHM_AES = "AES";
    private static final String ENCRYPTION_ALGORITHM_BLOWFISH = "Blowfish";

    /**
     * Location of the jiveHome directory. All configuration files should be
     * located here.
     */
    private static String home = null;

    private static boolean failedLoading = false;

    private static XMLProperties openfireProperties = null;
    private static XMLProperties securityProperties = null;
    private static JiveProperties properties = null;

    private static Locale locale = null;
    private static TimeZone timeZone = null;
    private static DateFormat dateFormat = null;
    private static DateFormat dateTimeFormat = null;
    private static DateFormat timeFormat = null;
    
    private static Encryptor propertyEncryptor = null;
    private static Encryptor propertyEncryptorNew = null;
    private static String currentKey = null;

    /**
     * Returns the global Locale used by Jive. A locale specifies language
     * and country codes, and is used for internationalization. The default
     * locale is system dependent - Locale.getDefault().
     *
     * @return the global locale used by Jive.
     */
    public static Locale getLocale() {
        if (locale == null) {
            if (openfireProperties != null) {
                String [] localeArray;
                String localeProperty = openfireProperties.getProperty("locale");
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
        if (openfireProperties == null) {
            loadOpenfireProperties();
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
     * {@code JIVE_CONFIG_FILENAME} that exists in the {@code home} directory.
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
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        return openfireProperties.getProperty(name);
    }

    /**
     * Returns a local property. Local properties are stored in the file defined in
     * {@code JIVE_CONFIG_FILENAME} that exists in the {@code home} directory.
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
     * If the specified property can't be found, the {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue the default value for the property.
     * @return the property value specified by name.
     */
    public static String getXMLProperty(String name, String defaultValue) {
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }

        String value = openfireProperties.getProperty(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Returns an integer value local property. Local properties are stored in the file defined in
     * {@code JIVE_CONFIG_FILENAME} that exists in the {@code home} directory.
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
     * {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property could not be loaded or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * file defined in {@code JIVE_CONFIG_FILENAME} that exists in the {@code home}
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
     * If the specified property can't be found, the {@code defaultValue} will be returned.
     * If the property is found, it will be parsed using {@link Boolean#valueOf(String)}.  
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property could not be loaded or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * {@code JIVE_CONFIG_FILENAME} that exists in the {@code home} directory.
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
     * @return {@code true} if the property was correctly saved to file, otherwise {@code false}
     */
    public static boolean setXMLProperty(String name, String value) {
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        return openfireProperties.setProperty(name, value);
    }

    /**
     * Sets multiple local properties at once. If a property doesn't already exists, a new
     * one will be created. Local properties are stored in the file defined in
     * {@code JIVE_CONFIG_FILENAME} that exists in the {@code home} directory.
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
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        openfireProperties.setProperties(propertyMap);
    }

    /**
     * Return all immediate children property values of a parent local property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties {@code X.Y.A}, {@code X.Y.B}, {@code X.Y.C} and {@code X.Y.C.D}, then
     * the immediate child properties of {@code X.Y} are {@code A}, {@code B}, and
     * {@code C} (the value of {@code C.D} would not be returned using this method).<p>
     *
     * Local properties are stored in the file defined in {@code JIVE_CONFIG_FILENAME} that exists
     * in the {@code home} directory. Properties are always specified as "foo.bar.prop",
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
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }

        String[] propNames = openfireProperties.getChildrenProperties(parent);
        List<String> values = new ArrayList<>();
        for (String propName : propNames) {
            String value = getXMLProperty(parent + "." + propName);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Return all property names as a list of strings, or an empty list if jiveHome has not been loaded.
     *
     * @return all child property for the given parent.
     */
    public static List<String> getXMLPropertyNames() {
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        return openfireProperties.getAllPropertyNames();
    }

    /**
     * Deletes a locale property. If the property doesn't exist, the method
     * does nothing.
     *
     * @param name the name of the property to delete.
     */
    public static void deleteXMLProperty(String name) {
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        openfireProperties.deleteProperty(name);
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
     * {@code defaultValue} will be returned.
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
     * Returns an enum constant Jive property. If the specified property doesn't exist, or if it's value cannot be parsed
     * as an enum constant, the {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param enumType the {@code Class} object of the enum type from which to return a constant.
     * @param defaultValue value returned if the property doesn't exist or it's value could not be parsed.
     * @param <E> The enum type whose constant is to be returned.
     * @return the property value (as an enum constant) or {@code defaultValue}.
     */
    public static <E extends Enum<E>> E getEnumProperty( String name, Class<E> enumType, E defaultValue )
    {
        String value = getProperty( name );
        if ( value != null )
        {
            try
            {
                return E.valueOf( enumType, value );
            }
            catch ( IllegalArgumentException e )
            {
                // Ignore
            }
        }
        return defaultValue;
    }

    /**
     * Returns an integer value Jive property. If the specified property doesn't exist, the
     * {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
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
     * Returns a double value Jive property. If the specified property doesn't exist, the
     *  {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist or was not
     *      a number.
     * @return the property value specified by name or {@code defaultValue}.
     */
    public static double getDoubleProperty(String name, double defaultValue) {
        String value = getProperty(name);
        if (value != null) {
            try {
                return Double.parseDouble(value);
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
     * @return true if the property value exists and is set to {@code "true"} (ignoring case).
     *      Otherwise {@code false} is returned.
     */
    public static boolean getBooleanProperty(String name) {
        return Boolean.valueOf(getProperty(name));
    }

    /**
     * Returns a boolean value Jive property. If the property doesn't exist, the {@code defaultValue}
     * will be returned.
     *
     * If the specified property can't be found, or if the value is not a number, the
     * {@code defaultValue} will be returned.
     *
     * @param name the name of the property to return.
     * @param defaultValue value returned if the property doesn't exist.
     * @return true if the property value exists and is set to {@code "true"} (ignoring case).
     *      Otherwise {@code false} is returned.
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
     * the properties {@code X.Y.A}, {@code X.Y.B}, {@code X.Y.C} and {@code X.Y.C.D}, then
     * the immediate child properties of {@code X.Y} are {@code A}, {@code B}, and
     * {@code C} ({@code C.D} would not be returned using this method).<p>
     *
     * @param parent Parent "node" to find the children of.
     * @return a List of all immediate children property names (Strings).
     */
    public static List<String> getPropertyNames(String parent) {
        if (properties == null) {
            if (isSetupMode()) {
                return new ArrayList<>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<>(properties.getChildrenNames(parent));
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings,
     * or an empty list if there are no children. For example, given
     * the properties {@code X.Y.A}, {@code X.Y.B}, {@code X.Y.C} and {@code X.Y.C.D}, then
     * the immediate child properties of {@code X.Y} are {@code X.Y.A}, {@code X.Y.B}, and
     * {@code X.Y.C} (the value of {@code X.Y.C.D} would not be returned using this method).<p>
     *
     * @param parent the name of the parent property to return the children for.
     * @return all child property values for the given parent.
     */
    public static List<String> getProperties( String parent )
    {
        return getListProperty( parent, new ArrayList<>() );
    }

    /**
     * Return all immediate children property values of a parent Jive property as a list of strings, or an default list
     * if the property does not exist.
     *
     * This implementation ignores child property values that are empty (these are excluded from the result). When all
     * child properties are empty, an empty collection (and explicitly not the default values) is returned. This allows
     * a property to override a default non-empty collection with an empty one.
     *
     * The child properties that are evaluated in this method are the same child properties as those used by
     * {@link #getProperties(String)}.
     *
     * @param parent        the name of the parent property to return the children for.
     * @param defaultValues values returned if the property doesn't exist.
     * @return all child property values for the given parent.
     */
    public static List<String> getListProperty( String parent, List<String> defaultValues )
    {
        if ( properties == null )
        {
            if ( isSetupMode() )
            {
                return defaultValues;
            }
            properties = JiveProperties.getInstance();
        }

        // Check for a legacy, comma separated value.
        final String legacyValue = JiveGlobals.getProperty( parent );

        // Ensure that properties are ordered.
        final SortedSet<String> propertyNames = new TreeSet<>( properties.getChildrenNames( parent ) );

        if ( propertyNames.isEmpty() )
        {
            if ( legacyValue != null )
            {
                Log.info( "Retrieving a list from property '{}' which is stored in a comma-separated format. Consider using child properties instead, via JiveGlobals.setProperty( String value, List<String> values )", parent );
                return Arrays.asList( legacyValue.split( "\\s*,\\s*" ) );
            }

            // When there are no child properties, return the default values.
            return defaultValues;
        }
        else if ( legacyValue != null )
        {
            // Raise a warning if two competing sets of data are detected.
            Log.warn( "Retrieving a list from property '{}' which is stored using child properties, but also in a legacy format! The data that is in the legacy format (the text value of property '{}') is not returned by this call! Its child property values are used instead. Consider removing the text value of the parent property.", parent, parent );
        }

        // When there are child properties, return its non-null, non-empty values (which might be an empty collection).
        final List<String> values = new ArrayList<>();
        for ( String propertyName : propertyNames )
        {
            final String value = getProperty( propertyName );
            if ( value != null && !value.isEmpty())
            {
                values.add( value );
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
                return new ArrayList<>();
            }
            properties = JiveProperties.getInstance();
        }
        return new ArrayList<>(properties.getPropertyNames());
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created.
     *
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     */
    public static void setProperty(String name, String value) {
        setProperty(name, value, false);
    }

    /**
     * Sets a Jive property. If the property doesn't already exists, a new
     * one will be created.
     *
     * @param name the name of the property being set.
     * @param value the value of the property being set.
     * @param encrypt {@code true} to encrypt the property in the database, other {@code false}
     */
    public static void setProperty(String name, String value, boolean encrypt) {
        if (properties == null) {
            if (isSetupMode()) {
                return;
            }
            properties = JiveProperties.getInstance();
        }
        properties.put(name, value, encrypt);
    }

    /**
     * Sets a Jive property with a list of values. If the property doesn't already exists, a new one will be created.
     * Empty or null values in the collection are ignored.
     *
     * Each value is stored as a direct child property of the property name provided as an argument to this method. When
     * this method is used, all previous children of the property will be deleted.
     *
     * When the provided value is null, any previously stored collection will be removed. If it is an empty collection
     * (or a collection that consists of null and empty values onlu), it is stored as an empty collection
     * (represented by a child property that has an empty value).
     *
     * The naming convention used by this method to define child properties is subject to change, and should not be
     * depended on.
     *
     * This method differs from {@link #setProperties(Map)}, which is used to set multiple properties. This method sets
     * one property with multiple values.
     *
     * @param name   the name of the property being set.
     * @param values the values of the property.
     */
    public static void setProperty( String name, List<String> values )
    {
        if ( properties == null )
        {
            if ( isSetupMode() )
            {
                return;
            }
            properties = JiveProperties.getInstance();
        }

        final List<String> existing = getProperties( name );
        if ( existing != null && existing.equals( values ) )
        {
            // no change.
            return;
        }

        properties.remove( name );
        if ( values != null )
        {
            int i = 1;
            for ( final String value : values )
            {
                if ( value != null && !value.isEmpty() )
                {
                    final String childName = name + "." + String.format("%05d", i++ );
                    properties.put( childName, value );
                }
            }

            // When no non-null, non-empty values are stored, store one to denote an empty collection.
            if ( i == 1 )
            {
                properties.put( name + ".00001", "" );
            }

            // The put's above will have generated events for each child property. Now, generate an event for the parent.
            final Map<String, Object> params = new HashMap<>();
            params.put("value", values);
            PropertyEventDispatcher.dispatchEvent(name, PropertyEventDispatcher.EventType.property_set, params);
        }
    }

    /**
     * Sets multiple Jive properties at once. If a property doesn't already exists, a new one will be created.
     *
     * This method differs from {@link #setProperty(String, List)}, which is used to one property with multiple
     * values. This method sets multiple properties, each with one value.
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
        clearXMLPropertyEncryptionEntry(name);
    }

    static void clearXMLPropertyEncryptionEntry(String name) {
        if (isSetupMode()) {
            return;
        }
        if (securityProperties == null) {
            loadSecurityProperties();
        }
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        // Note; only remove the encryption indicator from XML file if the (encrypted) property is not also defined in the XML file
        if (JiveGlobals.isXMLPropertyEncrypted(name) && openfireProperties.getProperty(name) == null) {
            securityProperties.removeFromList(ENCRYPTED_PROPERTY_NAMES, name);
        }
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
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }
        openfireProperties.migrateProperty(name);
    }

    /**
     * Convenience routine to migrate a tree of XML propertis into the database
     * storage method.
     *
     * @param name the name of the base property to migrate.
     */
    public static void migratePropertyTree(String name) {
        if (isSetupMode()) {
            return;
        }
        if (openfireProperties == null) {
            loadOpenfireProperties();
        }

        final String[] children = openfireProperties.getChildrenProperties( name );
        if ( children != null )
        {
            for ( final String child : children )
            {
                migratePropertyTree( name + "." + child );
            }
        }
        openfireProperties.migrateProperty(name);
    }
    
    /**
     * Flags certain properties as being sensitive, based on
     * property naming conventions. Values for matching property
     * names are hidden from the Openfire console.
     * 
     * @param name The name of the property
     * @return True if the property is considered sensitive, otherwise false
     */
    public static boolean isPropertySensitive(String name) {
        
        return name != null && (
            name.toLowerCase().contains("passwd") ||
                name.toLowerCase().contains("password") ||
                name.toLowerCase().contains("cookiekey"));
    }


    /**
     * Determines whether an XML property is configured for encryption.
     *
     * @param name
     *            The name of the property
     * @return {@code true} if the property is stored using encryption, otherwise {@code false}
     */
    public static boolean isXMLPropertyEncrypted(final String name) {
        if (securityProperties == null) {
            loadSecurityProperties();
        }
        return name != null &&
                !name.startsWith(JiveGlobals.ENCRYPTED_PROPERTY_NAME_PREFIX) &&
                securityProperties.getProperties(JiveGlobals.ENCRYPTED_PROPERTY_NAMES, true).contains(name);

    }

    /**
     * Determines whether a property is configured for encryption.
     * 
     * @param name
     *            The name of the property
     * @return {@code true} if the property is stored using encryption, otherwise {@code false}
     */
    public static boolean isPropertyEncrypted(String name) {
        if (properties == null) {
            if (isSetupMode()) {
                return false;
            }
            properties = JiveProperties.getInstance();
        }
        return properties.isEncrypted(name);
    }

    /**
     * Set the encryption status for the given property.
     * 
     * @param name The name of the property
     * @param encrypt True to encrypt the property, false to decrypt
     * @return True if the property's encryption status changed, otherwise false
     */
    public static boolean setPropertyEncrypted(String name, boolean encrypt) {
        if (properties == null) {
            if (isSetupMode()) {
                return false;
            }
            properties = JiveProperties.getInstance();
        }
        return properties.setPropertyEncrypted(name, encrypt);
    }

    /**
     * Fetches the property encryptor.
     * 
     * @param useNewEncryptor Should use the new encryptor
     * @return The property encryptor
     */
    public static Encryptor getPropertyEncryptor(boolean useNewEncryptor) {
        if (securityProperties == null) {
            loadSecurityProperties();
        }
        if (propertyEncryptor == null) {
            String algorithm = securityProperties.getProperty(ENCRYPTION_ALGORITHM);
            propertyEncryptor = getEncryptor(algorithm, currentKey);
            propertyEncryptorNew = propertyEncryptor;
        }
        return useNewEncryptor ? propertyEncryptorNew : propertyEncryptor;
    }

    /**
     * Fetches the current property encryptor.
     *
     * @return The current property encryptor
     */
    public static Encryptor getPropertyEncryptor() {
        return getPropertyEncryptor(false);
    }
    
    /**
     * This method is called early during the setup process to
     * set the algorithm for encrypting property values 
     * @param alg the algorithm used to encrypt properties
     */
    public static void setupPropertyEncryptionAlgorithm(String alg) {
        // Get the old secret key and encryption type
        String oldAlg = securityProperties.getProperty(ENCRYPTION_ALGORITHM);
        String oldKey = getCurrentKey();
        if (StringUtils.isNotEmpty(alg) && !oldAlg.equals(alg) && (StringUtils.isNotEmpty(oldKey) || propertyEncryptor != null)) {
            // update encrypted properties
            updateEncryptionProperties(alg, oldKey);
        }
        // Set the new algorithm
        if (ENCRYPTION_ALGORITHM_AES.equalsIgnoreCase(alg)) {
            securityProperties.setProperty(ENCRYPTION_ALGORITHM, ENCRYPTION_ALGORITHM_AES);
        } else {
            securityProperties.setProperty(ENCRYPTION_ALGORITHM, ENCRYPTION_ALGORITHM_BLOWFISH);
        }
    }
    
    /**
     * This method is called early during the setup process to
     * set a custom key for encrypting property values
     * @param key the key used to encrypt properties
     */
    public static void setupPropertyEncryptionKey(String key) {
        // Get the old secret key and encryption type
        String oldAlg = securityProperties.getProperty(ENCRYPTION_ALGORITHM);
        String oldKey = getCurrentKey();
        if ((StringUtils.isNotEmpty(oldKey) || propertyEncryptor != null) && StringUtils.isNotEmpty(key) && !key.equals(oldKey) && StringUtils.isNotEmpty(oldAlg)) {
            // update encrypted properties
            updateEncryptionProperties(oldAlg, key);
        }
        // Set the new key
        securityProperties.setProperty(ENCRYPTION_KEY_CURRENT, new AesEncryptor().encrypt(key));
        currentKey = key == "" ? null : key;
        propertyEncryptorNew = getEncryptor(oldAlg, key);
        propertyEncryptor = propertyEncryptorNew;
    }

    /**
     * Get current encryptor key.
     *
     */
    private static String getCurrentKey() {
        String encryptedKey = securityProperties.getProperty(ENCRYPTION_KEY_CURRENT);
        String key = null;
        if (StringUtils.isNotEmpty(encryptedKey)) {
            key = new AesEncryptor().decrypt(encryptedKey);
        }
        return key;
    }

    /**
     * Get current encryptor according to alg and key.
     *
     * @param alg algorithm type
     * @param key encryptor key
     */
    private static Encryptor getEncryptor(String alg, String key) {
        Encryptor encryptor;
        if (ENCRYPTION_ALGORITHM_AES.equalsIgnoreCase(alg)) {
            encryptor = new AesEncryptor(key);
        } else {
            encryptor = new Blowfish(key);
        }
        return encryptor;
    }

    /**
     * Re-encrypted with a new key and new algorithm configuration
     * 
     * @param newAlg new algorithm type
     * @param newKey new encryptor key
     */
    private static void updateEncryptionProperties(String newAlg, String newKey) {
        // load DB properties using the current key
        if(properties == null) {
            properties = JiveProperties.getInstance();
        }
        //create the new encryptor
        currentKey = newKey.isEmpty() ? null : newKey;
        propertyEncryptorNew = getEncryptor(newAlg, newKey);
        
        // Use new key to update configuration properties
        Iterator<Entry<String, String>> iterator = properties.entrySet().iterator();
        Entry<String, String> entry;
        String name;
        while(iterator.hasNext()){
            entry = iterator.next();
            name = entry.getKey();
            // only need to update the encrypted ones
            if (isPropertyEncrypted(name)) {
                properties.put(name, entry.getValue());
            }
        }
        // Update encryption properties to XML, using new encryption key
        for (String propertyName : securityProperties.getProperties(ENCRYPTED_PROPERTY_NAMES, true)) {
            String xmlProperty = getXMLProperty(propertyName);
            // update xml prop
            if(StringUtils.isNotEmpty(xmlProperty)){
                Log.info("Updating encrypted value for " + propertyName);
                setXMLProperty(propertyName, xmlProperty);
            }
        }
        // Two encryptors are now the same
        propertyEncryptor = propertyEncryptorNew;
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
     * Returns the name of the local config file.
     *
     * @return the name of the config file.
     */
    static String getConfigName() {
        return JIVE_CONFIG_FILENAME;
    }

    /**
     * Returns true if in setup mode. A false value means that setup has been completed
     * or that a connection to the database was possible to properties stored in the
     * database can be retrieved now. The latter means that once the database settings
     * during the setup was done a connection to the database should be available thus
     * properties stored from a previous setup will be available.
     *
     * @return true if in setup mode.
     */
    private static boolean isSetupMode() {
        if (Boolean.valueOf(JiveGlobals.getXMLProperty("setup"))) {
            return false;
        }
        // Check if the DB configuration is done
        if (DbConnectionManager.getConnectionProvider() == null) {
            // DB setup is still not completed so setup is needed
            return true;
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Properties can now be loaded from DB so consider setup done
        }
        catch (SQLException e) {
            // Properties cannot be loaded from DB so do not consider setup done
            return true;
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return false;
    }

    /**
     * Loads Openfire properties if necessary. Property loading must be done lazily so
     * that we give outside classes a chance to set {@code home}.
     */
    private synchronized static void loadOpenfireProperties() {
        if (openfireProperties == null) {
            // If home is null then log that the application will not work correctly
            if (home == null && !failedLoading) {
                failedLoading = true;
                System.err.println("Critical Error! The home directory has not been configured, \n" +
                    "which will prevent the application from working correctly.\n\n");
            }
            // Create a manager with the full path to the Openfire config file.
            else {
                try {
                    openfireProperties = new XMLProperties(home + File.separator + getConfigName());
                }
                catch (IOException ioe) {
                    Log.error(ioe.getMessage());
                    failedLoading = true;
                }
            }
            // create a default/empty XML properties set (helpful for unit testing)
            if (openfireProperties == null) {
                try { 
                    openfireProperties = new XMLProperties();
                } catch (IOException e) {
                    Log.error("Failed to setup default openfire properties", e);
                }            	
            }
        }
    }

    /**
     * Lazy-loads the security configuration properties.
     */
    private synchronized static void loadSecurityProperties() {
        
        if (securityProperties == null) {
            // If home is null then log that the application will not work correctly
            if (home == null && !failedLoading) {
                failedLoading = true;
                System.err.println("Critical Error! The home directory has not been configured, \n" +
                    "which will prevent the application from working correctly.\n\n");
            }
            // Create a manager with the full path to the security XML file.
            else {
                try {
                    securityProperties = new XMLProperties(home + File.separator + JIVE_SECURITY_FILENAME);
                    setupPropertyEncryption();
                    TaskEngine.getInstance().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // Migrate all secure XML properties into the database automatically
                            for (String propertyName : securityProperties.getAllPropertyNames()) {
                                if (!propertyName.startsWith(ENCRYPTED_PROPERTY_NAME_PREFIX)) {
                                    setPropertyEncrypted(propertyName, true);
                                    securityProperties.migrateProperty(propertyName);
                                }
                            }
                        }
                    }, 1000);
                }
                catch (IOException ioe) {
                    Log.error(ioe.getMessage());
                    failedLoading = true;
                }
            }
            // create a default/empty XML properties set (helpful for unit testing)
            if (securityProperties == null) {
                try { 
                    securityProperties = new XMLProperties();
                } catch (IOException e) {
                    Log.error("Failed to setup default security properties", e);
                }            	
            }
        }
    }
    
    /**
     * Setup the property encryption key, rewriting encrypted values as appropriate
     */
    private static void setupPropertyEncryption() {
        
        // get/set the current encryption key
        currentKey = getCurrentKey();
        
        // check to see if a new key has been defined
        String newKey = securityProperties.getProperty(ENCRYPTION_KEY_NEW, false);
        if (newKey != null) {
            
            Log.info("Detected new encryption key; updating encrypted properties");

            // if a new key has been provided, check to see if the old key matches 
            // the current key, otherwise log an error and ignore the new key
            String oldKey = securityProperties.getProperty(ENCRYPTION_KEY_OLD);
            if (oldKey == null) {
                if (currentKey != null) {
                    Log.warn("Old encryption key was not provided; ignoring new encryption key");
                    return;
                }
            } else {
                if (!oldKey.equals(currentKey)) {
                    Log.warn("Old encryption key does not match current encryption key; ignoring new encryption key");
                    return;
                }
            }

            String oldAlg = securityProperties.getProperty(ENCRYPTION_ALGORITHM);
            updateEncryptionProperties(oldAlg, newKey);
            
            securityProperties.deleteProperty(ENCRYPTION_KEY_NEW);
            securityProperties.deleteProperty(ENCRYPTION_KEY_OLD);
        }
    
        // (re)write the encryption key to the security XML file
        securityProperties.setProperty(ENCRYPTION_KEY_CURRENT, new AesEncryptor().encrypt(currentKey));
    }
}
