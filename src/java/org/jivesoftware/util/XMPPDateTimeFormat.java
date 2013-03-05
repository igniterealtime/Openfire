/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2013 Florian Schmaus
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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import net.jcip.annotations.ThreadSafe;

/**
 * 
 * Utility class for date/time format conversions as specified in
 * <a href="http://www.xmpp.org/extensions/xep-0082.html">XEP-0082</a> and
 * <a href="http://www.xmpp.org/extensions/xep-0090.html">XEP-0090</a> and
 * For Date -> String converstion FastDateFormat is used
 * 
 */
//@ThreadSafe
public class XMPPDateTimeFormat {
    /**
     * Date/time format for use by SimpleDateFormat. The format conforms to
     * <a href="http://www.xmpp.org/extensions/xep-0082.html">XEP-0082</a>, which defines
     * a unified date/time format for XMPP.
     */
    public static final String XMPP_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String XMPP_DATETIME_FORMAT_WO_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String XMPP_DATETIME_FORMAT_WO_MILLIS_WO_TIMEZONE = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String XMPP_DATE_FORMAT = "yyyy-MM-dd";
    public static final String XMPP_TIME_FORMAT = "HH:mm:ss.SSS";
    public static final String XMPP_TIME_FORMAT_WO_MILLIS = "HH:mm:ss";

    /**
     * Date/time format for use by SimpleDateFormat. The format conforms to the format
     * defined in <a href="http://www.xmpp.org/extensions/xep-0091.html">XEP-0091</a>,
     * a specialized date format for historical XMPP usage.
     */
    public static final String XMPP_DELAY_DATETIME_FORMAT = "yyyyMMdd'T'HH:mm:ss";

    // matches CCYY-MM-DDThh:mm:ss.SSS(Z|(+|-)hh:mm))
    private static final Pattern xep80DateTimePattern = Pattern.compile("^\\d+(-\\d+){2}+T(\\d+:){2}\\d+.\\d+(Z|([+-](\\d+:\\d+)))?$");
    // matches CCYY-MM-DDThh:mm:ss(Z|(+|-)hh:mm))
    private static final Pattern xep80DateTimeWoMillisPattern = Pattern.compile("^\\d+(-\\d+){2}+T(\\d+:){2}\\d+(Z|([+-](\\d+:\\d+)))?$");
    // matches CCYYMMDDThh:mm:ss
    @SuppressWarnings("unused")
    private static final Pattern xep91Pattern = Pattern.compile("^\\d+T\\d+:\\d+:\\d+$");

    private static final FastDateFormat FAST_FORMAT = FastDateFormat.getInstance(
            XMPP_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    private static final FastDateFormat FAST_FORMAT_OLD = FastDateFormat.getInstance(
            XMPP_DELAY_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));

    private final DateFormat dateTimeFormat = new SimpleDateFormat(XMPP_DATETIME_FORMAT_WO_TIMEZONE + 'Z');
    private final DateFormat dateTimeFormatWoMillies = new SimpleDateFormat(XMPP_DATETIME_FORMAT_WO_MILLIS_WO_TIMEZONE + 'Z');
    private final DateFormat dateTimeFormatOld = new SimpleDateFormat(XMPP_DELAY_DATETIME_FORMAT);

    /**
     * Create a new thread-safe instance of this utility class
     */
    public XMPPDateTimeFormat() {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateTimeFormat.setTimeZone(utc);
        dateTimeFormatWoMillies.setTimeZone(utc);
        dateTimeFormatOld.setTimeZone(utc);
    }

    /**
     * Tries to convert a given string to a Date object.
     * This method supports the format types defined by XEP-0082 and the format defined in legacy protocols
     * XEP-0082: CCYY-MM-DDThh:mm:ss[.sss]TZD
     * legacy: CCYYMMDDThh:mm:ss
     * 
     * @param dateStr
     * @return
     * @throws ParseException
     */
    public Date parseString(String dateString) throws ParseException {
        Matcher xep82WoMillisMatcher = xep80DateTimeWoMillisPattern.matcher(dateString);
        Matcher xep82Matcher = xep80DateTimePattern.matcher(dateString);

        if (xep82WoMillisMatcher.matches() || xep82Matcher.matches()) {
            String rfc822Date;
            // Convert the ISO 8601 time zone string to a RFC822 compatible format
            // since SimpleDateFormat supports ISO8601 only with Java7 or higher
            if (dateString.charAt(dateString.length() - 1) == 'Z') {
                rfc822Date = dateString.replace("Z", "+0000");
            } else {
                // If the time zone wasn't specified with 'Z', then it's in
                // ISO8601 format (i.e. '(+|-)HH:mm')
                // RFC822 needs a similar format just without the colon (i.e.
                // '(+|-)HHmm)'), so remove it
                int lastColon = dateString.lastIndexOf(':');
                rfc822Date = dateString.substring(0, lastColon) + dateString.substring(lastColon + 1);
            }

            if (xep82WoMillisMatcher.matches()) {
                synchronized (dateTimeFormatWoMillies) {
                    return dateTimeFormatWoMillies.parse(rfc822Date);
                }
            } else {
                synchronized (dateTimeFormat) {
                    return dateTimeFormat.parse(rfc822Date);
                }
            }
        } else {
            // at last try with the legacy format
            synchronized (dateTimeFormatOld) {
                return dateTimeFormatOld.parse(dateString);
            }
        }
    }

    /**
     * Tries to convert a given string to a Date object.
     * This method only supports the legacy XMPP time format: CCYYMMDDThh:mm:ss
     * 
     * @param dateStr
     * @return
     * @throws ParseException
     */
    public Date parseOldDate(String dateStr) throws ParseException {
        synchronized (dateTimeFormatOld) {
            return dateTimeFormatOld.parse(dateStr);
        }
    }

    /**
     * Formats a Date object to String as defined in XEP-0082.
     * 
     * The resulting String will have the timezone set to UTC ('Z') and includes milliseconds: 
     * CCYY-MM-DDThh:mm:ss.sssZ
     * 
     * @param date
     * @return
     */
    public static String format(Date date) {
        return FAST_FORMAT.format(date);
    }
    
    /**
     * Formats a Date object to String as defined in legacy XMPP protocols (e.g. XEP-0090)
     * 
     * CCYYMMDDThh:mm:ss
     * 
     * @param date
     * @return
     */
    public static String formatOld(Date date) {
        return FAST_FORMAT_OLD.format(date);
    }
}
