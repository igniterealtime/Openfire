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
package org.jivesoftware.messenger.container;

import java.io.File;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * <p>A way of passing context information between the container and
 * a module. This allows all modules to gather important information
 * regarding their environment.</p>
 * <p/>
 * <p>Context information is stored in an XML configuration file to allow
 * both programmatic and manual editing of configuration information.</p>
 * <p/>
 * <p>There are some special Jive properties that will always be set:</p>
 * <p/>
 * <ul>
 * <li><strong>messengerHome</strong> - The directory where the XMPP server is
 * installed</li>
 * <li><strong>locale</strong> - The locale settings used by Jive including
 * timezone, character encoding, date formats, etc.</li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
public interface ModuleContext extends ModuleProperties {
    /**
     * Returns the global Locale used by Jive. A locale specifies language
     * and country codes, and is used for internationalization. The default
     * locale is system dependant - Locale.getDefault().
     *
     * @return the global locale used by Jive.
     */
    Locale getLocale();

    /**
     * Sets the global locale used by Jive. A locale specifies language
     * and country codes, and is used for formatting dates and numbers.
     * The default locale is Locale.US.
     *
     * @param newLocale the global Locale for Jive.
     */
    void setLocale(Locale newLocale);

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
    String getCharacterEncoding();

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
    void setCharacterEncoding(String characterEncoding);

    /**
     * Returns the global TimeZone used by Jive. The default is the VM's
     * default time zone.
     *
     * @return the global time zone used by Jive.
     */
    TimeZone getTimeZone();

    /**
     * Sets the global time zone used by Jive. The default time zone is the VM's
     * time zone.
     *
     * @param newTimeZone The new timezone for the context to use
     */
    void setTimeZone(TimeZone newTimeZone);

    /**
     * Formats a Date object to return a date using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the date.
     */
    String formatDate(Date date);

    /**
     * Formats a Date object to return a date and time using the global locale.
     *
     * @param date the Date to format.
     * @return a String representing the date and time.
     */
    String formatDateTime(Date date);

    /**
     * Returns the location of the home directory for the module.
     *
     * @return the location of the home dir for the module
     */
    File getHomeDirectory();

    /**
     * Returns the location of the log directory for the module.
     *
     * @return the location of the log dir for the module
     */
    File getLogDirectory();
}
