/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.util;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import java.text.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A set of methods for retrieving and converting locale specific strings and numbers.
 *
 * @author Jive Software
 */
public class LocaleUtils {

    private static final Map<Locale, String[][]> timeZoneLists =
            new ConcurrentHashMap<Locale, String[][]>();

    // The basename to use for looking up the appropriate resource bundles
    // TODO - extract this out into a test that grabs the resource name from JiveGlobals
    // TODO and defaults to openfire_i18n if nothing set.
    private static final String resourceBaseName = "openfire_i18n";

    private LocaleUtils() {
    }

    /**
     * Converts a locale string like "en", "en_US" or "en_US_win" to a Java
     * locale object. If the conversion fails, null is returned.
     *
     * @param localeCode the locale code for a Java locale. See the {@link java.util.Locale}
     *                   class for more details.
     */
    public static Locale localeCodeToLocale(String localeCode) {
        Locale locale = null;
        if (localeCode != null) {
            String language = null;
            String country = null;
            String variant = null;
            StringTokenizer tokenizer = new StringTokenizer(localeCode, "_");
            if (tokenizer.hasMoreTokens()) {
                language = tokenizer.nextToken();
                if (tokenizer.hasMoreTokens()) {
                    country = tokenizer.nextToken();
                    if (tokenizer.hasMoreTokens()) {
                        variant = tokenizer.nextToken();
                    }
                }
            }
            locale = new Locale(language,
                    ((country != null) ? country : ""),
                    ((variant != null) ? variant : ""));
        }
        return locale;
    }

    // The list of supported timezone ids. The list tries to include all of the relevant
    // time zones for the world without any extraneous zones.
    private static String[] timeZoneIds = new String[]{"GMT",
            "Pacific/Apia",
            "HST",
            "AST",
            "America/Los_Angeles",
            "America/Phoenix",
            "America/Mazatlan",
            "America/Denver",
            "America/Belize",
            "America/Chicago",
            "America/Mexico_City",
            "America/Regina",
            "America/Bogota",
            "America/New_York",
            "America/Indianapolis",
            "America/Halifax",
            "America/Caracas",
            "America/Santiago",
            "America/St_Johns",
            "America/Sao_Paulo",
            "America/Buenos_Aires",
            "America/Godthab",
            "Atlantic/South_Georgia",
            "Atlantic/Azores",
            "Atlantic/Cape_Verde",
            "Africa/Casablanca",
            "Europe/Dublin",
            "Europe/Berlin",
            "Europe/Belgrade",
            "Europe/Paris",
            "Europe/Warsaw",
            "ECT",
            "Europe/Athens",
            "Europe/Bucharest",
            "Africa/Cairo",
            "Africa/Harare",
            "Europe/Helsinki",
            "Asia/Jerusalem",
            "Asia/Baghdad",
            "Asia/Kuwait",
            "Europe/Moscow",
            "Africa/Nairobi",
            "Asia/Tehran",
            "Asia/Muscat",
            "Asia/Baku",
            "Asia/Kabul",
            "Asia/Yekaterinburg",
            "Asia/Karachi",
            "Asia/Calcutta",
            "Asia/Katmandu",
            "Asia/Almaty",
            "Asia/Dhaka",
            "Asia/Colombo",
            "Asia/Rangoon",
            "Asia/Bangkok",
            "Asia/Krasnoyarsk",
            "Asia/Hong_Kong",
            "Asia/Irkutsk",
            "Asia/Kuala_Lumpur",
            "Australia/Perth",
            "Asia/Taipei",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Asia/Yakutsk",
            "Australia/Adelaide",
            "Australia/Darwin",
            "Australia/Brisbane",
            "Australia/Sydney",
            "Pacific/Guam",
            "Australia/Hobart",
            "Asia/Vladivostok",
            "Pacific/Noumea",
            "Pacific/Auckland",
            "Pacific/Fiji",
            "Pacific/Tongatapu"
    };

    // A mapping from the supported timezone ids to friendly english names.
    private static final Map<String, String> nameMap = new HashMap<String, String>();

    static {
        nameMap.put(timeZoneIds[0], "International Date Line West");
        nameMap.put(timeZoneIds[1], "Midway Island, Samoa");
        nameMap.put(timeZoneIds[2], "Hawaii");
        nameMap.put(timeZoneIds[3], "Alaska");
        nameMap.put(timeZoneIds[4], "Pacific Time (US & Canada); Tijuana");
        nameMap.put(timeZoneIds[5], "Arizona");
        nameMap.put(timeZoneIds[6], "Chihuahua, La Pax, Mazatlan");
        nameMap.put(timeZoneIds[7], "Mountain Time (US & Canada)");
        nameMap.put(timeZoneIds[8], "Central America");
        nameMap.put(timeZoneIds[9], "Central Time (US & Canada)");
        nameMap.put(timeZoneIds[10], "Guadalajara, Mexico City, Monterrey");
        nameMap.put(timeZoneIds[11], "Saskatchewan");
        nameMap.put(timeZoneIds[12], "Bogota, Lima, Quito");
        nameMap.put(timeZoneIds[13], "Eastern Time (US & Canada)");
        nameMap.put(timeZoneIds[14], "Indiana (East)");
        nameMap.put(timeZoneIds[15], "Atlantic Time (Canada)");
        nameMap.put(timeZoneIds[16], "Caracas, La Paz");
        nameMap.put(timeZoneIds[17], "Santiago");
        nameMap.put(timeZoneIds[18], "Newfoundland");
        nameMap.put(timeZoneIds[19], "Brasilia");
        nameMap.put(timeZoneIds[20], "Buenos Aires, Georgetown");
        nameMap.put(timeZoneIds[21], "Greenland");
        nameMap.put(timeZoneIds[22], "Mid-Atlantic");
        nameMap.put(timeZoneIds[23], "Azores");
        nameMap.put(timeZoneIds[24], "Cape Verde Is.");
        nameMap.put(timeZoneIds[25], "Casablanca, Monrovia");
        nameMap.put(timeZoneIds[26], "Greenwich Mean Time : Dublin, Edinburgh, Lisbon, London");
        nameMap.put(timeZoneIds[27], "Amsterdam, Berlin, Bern, Rome, Stockholm, Vienna");
        nameMap.put(timeZoneIds[28], "Belgrade, Bratislava, Budapest, Ljubljana, Prague");
        nameMap.put(timeZoneIds[29], "Brussels, Copenhagen, Madrid, Paris");
        nameMap.put(timeZoneIds[30], "Sarajevo, Skopje, Warsaw, Zagreb");
        nameMap.put(timeZoneIds[31], "West Central Africa");
        nameMap.put(timeZoneIds[32], "Athens, Istanbul, Minsk");
        nameMap.put(timeZoneIds[33], "Bucharest");
        nameMap.put(timeZoneIds[34], "Cairo");
        nameMap.put(timeZoneIds[35], "Harare, Pretoria");
        nameMap.put(timeZoneIds[36], "Helsinki, Kyiv, Riga, Sofia, Tallinn, Vilnius");
        nameMap.put(timeZoneIds[37], "Jerusalem");
        nameMap.put(timeZoneIds[38], "Baghdad");
        nameMap.put(timeZoneIds[39], "Kuwait, Riyadh");
        nameMap.put(timeZoneIds[40], "Moscow, St. Petersburg, Volgograd");
        nameMap.put(timeZoneIds[41], "Nairobi");
        nameMap.put(timeZoneIds[42], "Tehran");
        nameMap.put(timeZoneIds[43], "Abu Dhabi, Muscat");
        nameMap.put(timeZoneIds[44], "Baku, Tbilisi, Muscat");
        nameMap.put(timeZoneIds[45], "Kabul");
        nameMap.put(timeZoneIds[46], "Ekaterinburg");
        nameMap.put(timeZoneIds[47], "Islamabad, Karachi, Tashkent");
        nameMap.put(timeZoneIds[48], "Chennai, Kolkata, Mumbai, New Dehli");
        nameMap.put(timeZoneIds[49], "Kathmandu");
        nameMap.put(timeZoneIds[50], "Almaty, Novosibirsk");
        nameMap.put(timeZoneIds[51], "Astana, Dhaka");
        nameMap.put(timeZoneIds[52], "Sri Jayawardenepura");
        nameMap.put(timeZoneIds[53], "Rangoon");
        nameMap.put(timeZoneIds[54], "Bangkok, Hanoi, Jakarta");
        nameMap.put(timeZoneIds[55], "Krasnoyarsk");
        nameMap.put(timeZoneIds[56], "Beijing, Chongqing, Hong Kong, Urumqi");
        nameMap.put(timeZoneIds[57], "Irkutsk, Ulaan Bataar");
        nameMap.put(timeZoneIds[58], "Kuala Lampur, Singapore");
        nameMap.put(timeZoneIds[59], "Perth");
        nameMap.put(timeZoneIds[60], "Taipei");
        nameMap.put(timeZoneIds[61], "Osaka, Sapporo, Tokyo");
        nameMap.put(timeZoneIds[62], "Seoul");
        nameMap.put(timeZoneIds[63], "Yakutsk");
        nameMap.put(timeZoneIds[64], "Adelaide");
        nameMap.put(timeZoneIds[65], "Darwin");
        nameMap.put(timeZoneIds[66], "Brisbane");
        nameMap.put(timeZoneIds[67], "Canberra, Melbourne, Sydney");
        nameMap.put(timeZoneIds[68], "Guam, Port Moresby");
        nameMap.put(timeZoneIds[69], "Hobart");
        nameMap.put(timeZoneIds[70], "Vladivostok");
        nameMap.put(timeZoneIds[71], "Magadan, Solomon Is., New Caledonia");
        nameMap.put(timeZoneIds[72], "Auckland, Wellington");
        nameMap.put(timeZoneIds[73], "Fiji, Kamchatka, Marshall Is.");
        nameMap.put(timeZoneIds[74], "Nuku'alofa");
    }

    /**
     * Returns a list of all available time zone's as a String [][]. The first
     * entry in each list item is the timeZoneID, and the second is the
     * display name.<p>
     * <p/>
     * The list of time zones attempts to be inclusive of all of the worlds
     * zones while being as concise as possible. For "en" language locales
     * the name is a friendly english name. For non-"en" language locales
     * the standard JDK name is used for the given Locale. The GMT+/- time
     * is also included for readability.
     *
     * @return a list of time zones, as a tuple of the zime zone ID, and its
     *         display name.
     */
    public static String[][] getTimeZoneList() {
        Locale jiveLocale = JiveGlobals.getLocale();

        String[][] timeZoneList = timeZoneLists.get(jiveLocale);
        if (timeZoneList == null) {
            String[] timeZoneIDs = timeZoneIds;
            // Now, create String[][] using the unique zones.
            timeZoneList = new String[timeZoneIDs.length][2];
            for (int i = 0; i < timeZoneList.length; i++) {
                String zoneID = timeZoneIDs[i];
                timeZoneList[i][0] = zoneID;
                timeZoneList[i][1] = getTimeZoneName(zoneID, jiveLocale);
            }

            // Add the new list to the map of locales to lists
            timeZoneLists.put(jiveLocale, timeZoneList);
        }

        return timeZoneList;
    }

    /**
     * Returns the display name for a time zone. The display name is the name
     * specified by the Java TimeZone class for non-"en" locales or a friendly english
     * name for "en", with the addition of the GMT offset
     * for human readability.
     *
     * @param zoneID the time zone to get the name for.
     * @param locale the locale to use.
     * @return the display name for the time zone.
     */
    public static String getTimeZoneName(String zoneID, Locale locale) {
        TimeZone zone = TimeZone.getTimeZone(zoneID);
        StringBuffer buf = new StringBuffer();
        // Add in the GMT part to the name. First, figure out the offset.
        int offset = zone.getRawOffset();
        if (zone.inDaylightTime(new Date()) && zone.useDaylightTime()) {
            offset += (int)JiveConstants.HOUR;
        }

        buf.append("(");
        if (offset < 0) {
            buf.append("GMT-");
        }
        else {
            buf.append("GMT+");
        }
        offset = Math.abs(offset);
        int hours = offset / (int)JiveConstants.HOUR;
        int minutes = (offset % (int)JiveConstants.HOUR) / (int)JiveConstants.MINUTE;
        buf.append(hours).append(":");
        if (minutes < 10) {
            buf.append("0").append(minutes);
        }
        else {
            buf.append(minutes);
        }
        buf.append(") ");

        // Use a friendly english timezone name if the locale is en, otherwise use the timezone id
        if ("en".equals(locale.getLanguage())) {
            String name = nameMap.get(zoneID);
            if (name == null) {
                name = zoneID;
            }

            buf.append(name);
        }
        else {
            buf.append(
                    zone.getDisplayName(true, TimeZone.LONG, locale).replace('_', ' ').replace('/',
                            ' '));
        }

        return buf.toString();
    }

    /**
     * Returns the specified resource bundle, which is a properties file
     * that aids in localization of skins. This method is handy since it
     * uses the class loader that other Jive classes are loaded from (hence,
     * it can load bundles that are stored in jive.jar).
     *
     * @param baseName the name of the resource bundle to load.
     * @param locale the desired Locale.
     * @return the specified resource bundle, if it exists.
     */
    public static ResourceBundle getResourceBundle(String baseName,
                                                   Locale locale) {
        return ResourceBundle.getBundle(baseName, locale);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle.
     * The locale used will be the locale specified by JiveGlobals.getLocale().
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @return the localized string.
     */
    public static String getLocalizedString(String key) {
        Locale locale = JiveGlobals.getLocale();

        ResourceBundle bundle = ResourceBundle.getBundle(resourceBaseName, locale);

        return getLocalizedString(key, locale, null, bundle);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the passed in Locale.
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @param locale the locale to use for retrieving the appropriate
     *      locale-specific string.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(resourceBaseName, locale);

        return getLocalizedString(key, locale, null, bundle);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the locale specified by JiveGlobals.getLocale() substituting the passed
     * in arguments. Substitution is handled using the
     * {@link java.text.MessageFormat} class.
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @param arguments a list of objects to use which are formatted, then
     *      inserted into the pattern at the appropriate places.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, List arguments) {
        Locale locale = JiveGlobals.getLocale();

        ResourceBundle bundle = ResourceBundle.getBundle(resourceBaseName, locale);
        return getLocalizedString(key, locale, arguments, bundle);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle from the passed
     * in plugin. If the plugin name is <tt>null</tt>, the key will be looked up using
     * the standard resource bundle.
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @param pluginName the name of the plugin to load the require resource bundle from.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, String pluginName) {
        return getLocalizedString(key, pluginName, null);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle from the passed
     * in plugin. If the plugin name is <tt>null</tt>, the key will be looked up using
     * the standard resource bundle.
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @param pluginName the name of the plugin to load the require resource bundle from.
     * @param arguments a list of objects to use which are formatted, then
     *      inserted into the pattern at the appropriate places.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, String pluginName, List arguments) {
        if (pluginName == null) {
            return getLocalizedString(key, arguments);
        }

        Locale locale = JiveGlobals.getLocale();
        String i18nFile = pluginName + "_i18n";

        // Retrieve classloader from pluginName.
        final XMPPServer xmppServer = XMPPServer.getInstance();
        PluginManager pluginManager = xmppServer.getPluginManager();
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null) {
            throw new NullPointerException("Plugin could not be located: " + pluginName);
        }

        ClassLoader pluginClassLoader = pluginManager.getPluginClassloader(plugin);
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(i18nFile, locale, pluginClassLoader);
            return getLocalizedString(key, locale, arguments, bundle);
        }
        catch (MissingResourceException mre) {
            Log.error(mre);
            return key;
        }
    }

    /**
     * Retrieve the <code>ResourceBundle</code> that is used with this plugin.
     *
     * @param pluginName the name of the plugin.
     * @return the ResourceBundle used with this plugin.
     * @throws Exception thrown if an exception occurs.
     */
    public static ResourceBundle getPluginResourceBundle(String pluginName) throws Exception {
        final Locale locale = JiveGlobals.getLocale();

        String i18nFile = pluginName + "_i18n";

        // Retrieve classloader from pluginName.
        final XMPPServer xmppServer = XMPPServer.getInstance();
        PluginManager pluginManager = xmppServer.getPluginManager();
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if (plugin == null) {
            throw new NullPointerException("Plugin could not be located.");
        }

        ClassLoader pluginClassLoader = pluginManager.getPluginClassloader(plugin);
        return ResourceBundle.getBundle(i18nFile, locale, pluginClassLoader);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the passed in Locale substituting the passed in arguments. Substitution
     * is handled using the {@link MessageFormat} class.
     *
     * @param key the key to use for retrieving the string from the
     *      appropriate resource bundle.
     * @param locale the locale to use for retrieving the appropriate
     *      locale-specific string.
     * @param arguments a list of objects to use which are formatted, then
     *      inserted into the pattern at the appropriate places.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, Locale locale, List arguments,
            ResourceBundle bundle)
    {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }
        if (locale == null) {
            locale = JiveGlobals.getLocale();
        }

        String value;

        // See if the bundle has a value
        try {
            // The jdk caches resource bundles on it's own, so we won't bother.
            value = bundle.getString(key);
            // perform argument substitutions
            if (arguments != null) {
                MessageFormat messageFormat = new MessageFormat("");
                messageFormat.setLocale(bundle.getLocale());
                messageFormat.applyPattern(value);
                try {
                    // This isn't fool-proof, but it's better than nothing
                    // The idea is to try and convert strings into the
                    // types of objects that the formatters expects
                    // i.e. Numbers and Dates
                    Format[] formats = messageFormat.getFormats();
                    for (int i = 0; i < formats.length; i++) {
                        Format format = formats[i];
                        if (format != null) {
                            if (format instanceof DateFormat) {
                                if (arguments.size() > i) {
                                    Object val = arguments.get(i);
                                    if (val instanceof String) {
                                        DateFormat dateFmt = (DateFormat)format;
                                        try {
                                            val = dateFmt.parse((String)val);
                                            arguments.set(i, val);
                                        }
                                        catch (ParseException e) {
                                            Log.error(e);
                                        }
                                    }
                                }
                            }
                            else if (format instanceof NumberFormat) {
                                if (arguments.size() > i) {
                                    Object val = arguments.get(i);
                                    if (val instanceof String) {
                                        NumberFormat nbrFmt = (NumberFormat)format;
                                        try {
                                            val = nbrFmt.parse((String)val);
                                            arguments.set(i, val);
                                        }
                                        catch (ParseException e) {
                                            Log.error(e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    value = messageFormat.format(arguments.toArray());
                }
                catch (IllegalArgumentException e) {
                    Log.error("Unable to format resource string for key: "
                            + key + ", argument type not supported");
                    value = "";
                }
            }
        }
        catch (java.util.MissingResourceException mre) {
            Log.warn("Missing resource for key: " + key
                    + " in locale " + locale.toString());
            value = "";
        }

        return value;
    }

    /**
     * Returns an internationalized String representation of the number using
     * the default locale.
     *
     * @param number the number to format.
     * @return an internationalized String representation of the number.
     */
    public static String getLocalizedNumber(long number) {
        return NumberFormat.getInstance().format(number);
    }

    /**
     * Returns an internationalized String representation of the number using
     * the specified locale.
     *
     * @param number the number to format.
     * @param locale the locale to use for formatting.
     * @return an internationalized String representation of the number.
     */
    public static String getLocalizedNumber(long number, Locale locale) {
        return NumberFormat.getInstance(locale).format(number);
    }

    /**
     * Returns an internationalized String representation of the number using
     * the default locale.
     *
     * @param number the number to format.
     * @return an internationalized String representation of the number.
     */
    public static String getLocalizedNumber(double number) {
        return NumberFormat.getInstance().format(number);
    }

    /**
     * Returns an internationalized String representation of the number using
     * the specified locale.
     *
     * @param number the number to format.
     * @param locale the locale to use for formatting.
     * @return an internationalized String representation of the number.
     */
    public static String getLocalizedNumber(double number, Locale locale) {
        return NumberFormat.getInstance(locale).format(number);
    }
}
