/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.messenger.container.ModuleProperties;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XMLProperties;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;

/**
 * A way of passing context information between the container and
 * a module. This allows all modules to gather important information
 * regarding their environment.
 * <p/>
 * Properties can by any arbitrary key/value String pair. However,
 * we strongly suggest that you use dots to separate attribute groups
 * using the format: &quot;parent.child.propName&quot; although this is not required.
 * The default property save/load implementation will store these
 * values in an XML file with the format:
 * <pre>
 * <p/>
 * &lt;parent&gt;
 *   &lt;child&gt;
 *     &lt;propName&gt;
 *       propValue
 *     &lt;/propName&gt;
 *   &lt;/child&gt;
 * &lt;/parent&gt;
 * <p/>
 * </pre>
 * </p>
 *
 * @author Iain Shigeoka
 */
public class XMLModuleContext implements ModuleContext {

    /**
     * Should only be created by Jive internal classes.
     *
     * @param parent   the parent context.
     * @param filename the name of the file this context where it is loaded/saved.
     * @param home     the home directory for the server.
     * @param log      the log directory for the server.
     */
    public XMLModuleContext(ModuleContext parent,
                            File filename,
                            File home,
                            File log) {
        this.parentContext = parent;
        this.homeDir = home;
        this.logDir = log;
        loadProperties(filename);
    }

    /**
     * XML properties to actually get and set the Jive properties.
     */
    private XMLProperties properties = null;

    private ModuleContext parentContext;
    private Locale locale = null;
    private TimeZone timeZone = null;
    private String characterEncoding = null;
    private DateFormat dateFormat = null;
    private DateFormat dateTimeFormat = null;
    private File homeDir;
    private File logDir;

    public Locale getLocale() {
        Locale loc = locale;
        if (locale == null && parentContext != null) {
            loc = parentContext.getLocale();
        }
        else {
            loc = Locale.getDefault();
        }
        return loc;
    }

    public void setLocale(Locale newLocale) {
        if (newLocale == null) {
            locale = Locale.getDefault();
        }
        else {
            locale = newLocale;
        }
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

    public String getCharacterEncoding() {
        String encoding = characterEncoding;
        if (characterEncoding == null && parentContext != null) {
            encoding = parentContext.getCharacterEncoding();
        }
        return encoding;
    }

    public void setCharacterEncoding(String encoding) {
        this.characterEncoding = encoding;
        setProperty("locale.characterEncoding", encoding);
    }

    public TimeZone getTimeZone() {
        TimeZone zone = timeZone;
        if (timeZone == null && parentContext != null) {
            zone = parentContext.getTimeZone();
        }
        if (zone == null) {
            zone = TimeZone.getDefault();
        }
        return zone;
    }

    public void setTimeZone(TimeZone newTimeZone) {
        timeZone = newTimeZone;
        dateFormat.setTimeZone(timeZone);
        dateTimeFormat.setTimeZone(timeZone);
        setProperty("locale.timeZone", timeZone.getID());
    }

    public String formatDate(Date date) {
        String formattedDate;
        if (dateFormat == null && parentContext != null) {
            formattedDate = parentContext.formatDate(date);
        }
        else {
            formattedDate = dateFormat.format(date);
        }
        return formattedDate;
    }

    public String formatDateTime(Date date) {
        String formattedDate;
        if (dateTimeFormat == null && parentContext != null) {
            formattedDate = parentContext.formatDateTime(date);
        }
        else {
            formattedDate = dateTimeFormat.format(date);
        }
        return formattedDate;
    }

    public File getHomeDirectory() {
        return homeDir;
    }

    public File getLogDirectory() {
        return logDir;
    }

    public String getProperty(String name) {
        String prop = null;
        if (parentContext != null) {
            prop = parentContext.getProperty(name);
        }
        if (prop == null && properties != null) {
            prop = properties.getProperty(name);
        }
        return prop;
    }

    public String[] getProperties(String name) {
        String[] props = null;
        if (parentContext != null) {
            props = parentContext.getProperties(name);
        }
        if (props == null && properties != null) {
            props = properties.getProperties(name);
        }
        if (props == null) {
            props = new String[]{};
        }

        return props;
    }

    public ModuleProperties createChildProperty(String name) {
        ModuleProperties props = null;
        if (parentContext != null) {
            props = parentContext.createChildProperty(name);
        }
        if (props == null && properties != null) {
            props = properties.createChildProperty(name);
        }
        return props;
    }

    public Iterator getChildProperties(String name) {
        Iterator props = null;
        if (parentContext != null) {
            props = parentContext.getChildProperties(name);
        }
        if (props == null && properties != null) {
            props = properties.getChildProperties(name);
        }
        if (props == null) {
            props = Collections.EMPTY_LIST.iterator();
        }
        return props;
    }

    public void setProperties(String name, String[] values) {
        if (parentContext != null) {
            parentContext.setProperties(name, values);
        }
        else if (properties != null) {
            properties.setProperties(name, values);
        }
    }

    public void setProperty(String name, String value) {
        if (parentContext != null) {
            parentContext.setProperty(name, value);
        }
        else {
            if (properties != null) {
                properties.setProperty(name, value);
            }
        }
    }

    public void deleteProperty(String name) {
        if (properties != null) {
            properties.deleteProperty(name);
        }
    }

    /**
     * Loads properties if necessary. Property loading must be done lazily so
     * that we give outside classes a chance to set <tt>jiveHome</tt>.
     */
    private synchronized void loadProperties(File filename) {
        if (properties == null) {
            // Create a manager with the full path to the xml config file.
            try {
                properties = new XMLProperties(filename.toString());
            }
            catch (IOException ioe) {
                Log.error(ioe);
                return;
            }
        }

        if (locale == null) {
            String language = getDefaultProperty("locale.language", "");
            String country = getDefaultProperty("locale.country", "");
            // If no locale info is specified, default to system default Locale
            if ("".equals(language) && "".equals(country)) {
                locale = Locale.getDefault();
            }
            else {
                locale = new Locale(language, country);
            }
            // The default encoding is ISO-8859-1. We use the version of
            // the encoding name that seems to be most widely compatible.
            characterEncoding = getDefaultProperty("locale.characterEncoding",
                    "ISO-8859-1");
            String timeZoneID = getDefaultProperty("locale.timeZone",
                    TimeZone.getDefault().getID());
            timeZone = TimeZone.getTimeZone(timeZoneID);
            dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
            dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.MEDIUM, locale);
            dateFormat.setTimeZone(timeZone);
            dateTimeFormat.setTimeZone(timeZone);
        }

    }

    /**
     * Loads a property with the given name. Null properties will have the default value
     * returned instead.
     *
     * @param name  the name of the jive property to load.
     * @param value the default value of the property if it doesn't exist in the file
     * @return the value of the property
     */
    private String getDefaultProperty(String name, String value) {
        String prop = properties.getProperty(name);
        if (prop == null) {
            prop = value;
        }
        return prop;
    }
}
