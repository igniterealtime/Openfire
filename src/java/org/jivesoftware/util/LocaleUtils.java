/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.util;

import org.jivesoftware.messenger.JiveGlobals;
import java.text.*;
import java.util.*;

/**
 * A set of methods for retrieving and converting locale specific strings and numbers.
 *
 * @author Jive Software
 */
public class LocaleUtils {

    private static String[][] timeZoneList = null;

    private static Object timeZoneLock = new Object();

    // The basename to use for looking up the appropriate resource bundles
    // TODO - extract this out into a test that grabs the resource name from JiveGlobals
    // and defaults to jive_i18n if nothing set.
    private static final String resourceBaseName = "jive_i18n";

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
            if (localeCode != null) {
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
            }
            locale = new Locale(language,
                    ((country != null) ? country : ""),
                    ((variant != null) ? variant : ""));
        }
        return locale;
    }

    /**
     * Returns a list of all available time zone's as a String [][]. The first
     * entry in each list item is the timeZoneID, and the second is the
     * display name.<p>
     * <p/>
     * Normally, there are many ID's that correspond to a single display name.
     * However, the list has been paired down so that a display name only
     * appears once. Normally, the time zones will be returned in order:
     * -12 GMT,..., +0GMT,... +12GMT..., etc.
     *
     * @return a list of time zones, as a tuple of the zime zone ID, and its
     *         display name.
     */
    public static String[][] getTimeZoneList() {
        if (timeZoneList == null) {
            synchronized (timeZoneLock) {
                if (timeZoneList == null) {
                    Date now = new Date();

                    String[] timeZoneIDs = TimeZone.getAvailableIDs();
                    Locale jiveLocale = JiveGlobals.getLocale();
                    // Now, create String[][] using the unique zones.
                    timeZoneList = new String[timeZoneIDs.length][2];
                    for (int i = 0; i < timeZoneList.length; i++) {
                        String zoneID = timeZoneIDs[i];
                        timeZoneList[i][0] = zoneID;
                        timeZoneList[i][1] = getTimeZoneName(zoneID, now, jiveLocale);
                    }
                }
            }
        }
        return timeZoneList;
    }

    /**
     * Returns the display name for a time zone. The display name is the name
     * specified by the Java TimeZone class, with the addition of the GMT offset
     * for human readability.
     *
     * @param zoneID the time zone to get the name for.
     * @param now    the current date.
     * @param locale the locale to use.
     * @return the display name for the time zone.
     */
    private static String getTimeZoneName(String zoneID, Date now, Locale locale) {
        TimeZone zone = TimeZone.getTimeZone(zoneID);
        StringBuffer buf = new StringBuffer();
        // Add in the GMT part to the name. First, figure out the offset.
        int offset = zone.getRawOffset();
        if (zone.inDaylightTime(now) && zone.useDaylightTime()) {
            offset += (int)JiveConstants.HOUR;
        }

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
        buf.append(" - ").append(zoneID.replace('_', ' ').replace('/', ' ')).append(" ");
        buf.append(zone.getDisplayName(true, TimeZone.SHORT, locale).replace('_', ' ').replace('/', ' '));
        return buf.toString();
    }

    /**
     * Returns the specified resource bundle, which is a properties file
     * that aids in localization of skins. This method is handy since it
     * uses the class loader that other Jive classes are loaded from (hence,
     * it can load bundles that are stored in jive.jar).
     *
     * @param baseName the name of the resource bundle to load.
     * @param locale   the desired Locale.
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
     *            appropriate resource bundle.
     * @return the localized string.
     */
    public static String getLocalizedString(String key) {
        return getLocalizedString(key, JiveGlobals.getLocale(), null);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the passed in Locale.
     *
     * @param key    the key to use for retrieving the string from the
     *               appropriate resource bundle.
     * @param locale the locale to use for retrieving the appropriate
     *               locale-specific string.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, Locale locale) {
        return getLocalizedString(key, locale, null);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the locale specified by JiveGlobals.getLocale() substituting the passed
     * in arguments. Substitution is handled using the
     * {@link java.text.MessageFormat} class.
     *
     * @param key       the key to use for retrieving the string from the
     *                  appropriate resource bundle.
     * @param arguments a list of objects to use which are formatted, then
     *                  inserted into the pattern at the appropriate places.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, List arguments) {
        return getLocalizedString(key, JiveGlobals.getLocale(), arguments);
    }

    /**
     * Returns an internationalized string loaded from a resource bundle using
     * the passed in Locale substituting the passed in arguments. Substitution
     * is handled using the {@link java.text.MessageFormat} class.
     *
     * @param key       the key to use for retrieving the string from the
     *                  appropriate resource bundle.
     * @param locale    the locale to use for retrieving the appropriate
     *                  locale-specific string.
     * @param arguments a list of objects to use which are formatted, then
     *                  inserted into the pattern at the appropriate places.
     * @return the localized string.
     */
    public static String getLocalizedString(String key, Locale locale, List arguments) {
        if (key == null) {
            throw new NullPointerException("Key cannot be null");
        }
        if (locale == null) {
            locale = JiveGlobals.getLocale();
        }

        String value = "";

        // See if the bundle has a value
        try {
            // The jdk caches resource bundles on it's own, so we won't bother.
            ResourceBundle bundle = ResourceBundle.getBundle(resourceBaseName, locale);
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
            Log.error("Missing resource for key: " + key
                    + " in locale " + locale.toString());
            value = "";
        }

        return value;
    }

    /**
     *
     */
    public static String getLocalizedNumber(long number) {
        return NumberFormat.getInstance().format(number);
    }

    /**
     *
     */
    public static String getLocalizedNumber(long number, Locale locale) {
        return NumberFormat.getInstance(locale).format(number);
    }

    /**
     *
     */
    public static String getLocalizedNumber(double number) {
        return NumberFormat.getInstance().format(number);
    }

    /**
     *
     */
    public static String getLocalizedNumber(double number, Locale locale) {
        return NumberFormat.getInstance(locale).format(number);
    }
}
