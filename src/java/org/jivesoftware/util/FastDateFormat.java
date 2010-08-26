/* ====================================================================
 * Trove - Copyright (c) 1997-2001 Walt Disney Internet Group
 * ====================================================================
 * The Tea Software License, Version 1.1
 *
 * Copyright (c) 2000 Walt Disney Internet Group. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Walt Disney Internet Group (http://opensource.go.com/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Tea", "TeaServlet", "Kettle", "Trove" and "BeanDoc" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact opensource@dig.com.
 *
 * 5. Products derived from this software may not be called "Tea",
 *    "TeaServlet", "Kettle" or "Trove", nor may "Tea", "TeaServlet",
 *    "Kettle", "Trove" or "BeanDoc" appear in their name, without prior
 *    written permission of the Walt Disney Internet Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE WALT DISNEY INTERNET GROUP OR ITS
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * For more information about Tea, please see http://opensource.go.com/.
 */

package org.jivesoftware.util;

import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.text.DateFormatSymbols;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * <p>Similar to {@link java.text.SimpleDateFormat}, but faster and thread-safe.
 * Only formatting is supported, but all patterns are compatible with
 * SimpleDateFormat.</p>
 *
 * <p>Note, this class is from the open source Tea project (http://sourceforge.net/projects/teatrove/).</p>
 *
 * @author Brian S O'Neill
 */
public class FastDateFormat {
    /** Style pattern */
    public static final Object
        FULL = new Integer(SimpleDateFormat.FULL),
        LONG = new Integer(SimpleDateFormat.LONG),
        MEDIUM = new Integer(SimpleDateFormat.MEDIUM),
        SHORT = new Integer(SimpleDateFormat.SHORT);

    private static final double LOG_10 = Math.log(10);

    private static String cDefaultPattern;
    private static TimeZone cDefaultTimeZone = TimeZone.getDefault();

    private static Map cTimeZoneDisplayCache = new HashMap();

    private static Map cInstanceCache = new HashMap(7);
    private static Map cDateInstanceCache = new HashMap(7);
    private static Map cTimeInstanceCache = new HashMap(7);
    private static Map cDateTimeInstanceCache = new HashMap(7);

    public static FastDateFormat getInstance() {
        return getInstance(getDefaultPattern(), null, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     */
    public static FastDateFormat getInstance(String pattern)
        throws IllegalArgumentException
    {
        return getInstance(pattern, null, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     */
    public static FastDateFormat getInstance
        (String pattern, TimeZone timeZone) throws IllegalArgumentException
    {
        return getInstance(pattern, timeZone, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param locale optional locale, overrides system locale
     */
    public static FastDateFormat getInstance
        (String pattern, Locale locale) throws IllegalArgumentException
    {
        return getInstance(pattern, null, locale, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param symbols optional date format symbols, overrides symbols for
     * system locale
     */
    public static FastDateFormat getInstance
        (String pattern, DateFormatSymbols symbols)
        throws IllegalArgumentException
    {
        return getInstance(pattern, null, null, symbols);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     */
    public static FastDateFormat getInstance
        (String pattern, TimeZone timeZone, Locale locale)
        throws IllegalArgumentException
    {
        return getInstance(pattern, timeZone, locale, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     * @param symbols optional date format symbols, overrides symbols for
     * provided locale
     */
    public static synchronized FastDateFormat getInstance
        (String pattern, TimeZone timeZone, Locale locale,
         DateFormatSymbols symbols)
        throws IllegalArgumentException
    {
        Object key = pattern;

        if (timeZone != null) {
            key = new Pair(key, timeZone);
        }
        if (locale != null) {
            key = new Pair(key, locale);
        }
        if (symbols != null) {
            key = new Pair(key, symbols);
        }

        FastDateFormat format = (FastDateFormat)cInstanceCache.get(key);
        if (format == null) {
            if (locale == null) {
                locale = Locale.getDefault();
            }
            if (symbols == null) {
                symbols = new DateFormatSymbols(locale);
            }
            format = new FastDateFormat(pattern, timeZone, locale, symbols);
            cInstanceCache.put(key, format);
        }
        return format;
    }

    /**
     * @param style date style: FULL, LONG, MEDIUM, or SHORT
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     */
    public static synchronized FastDateFormat getDateInstance
        (Object style, TimeZone timeZone, Locale locale)
        throws IllegalArgumentException
    {
        Object key = style;

        if (timeZone != null) {
            key = new Pair(key, timeZone);
        }
        if (locale == null) {
            key = new Pair(key, locale);
        }

        FastDateFormat format = (FastDateFormat)cDateInstanceCache.get(key);

        if (format == null) {
            int ds;
            try {
                ds = ((Integer)style).intValue();
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("Illegal date style: " + style);
            }

            if (locale == null) {
                locale = Locale.getDefault();
            }

            try {
                String pattern = ((SimpleDateFormat)DateFormat.getDateInstance(ds, locale)).toPattern();
                format = getInstance(pattern, timeZone, locale);
                cDateInstanceCache.put(key, format);
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("No date pattern for locale: " + locale);
            }
        }

        return format;
    }

    /**
     * @param style time style: FULL, LONG, MEDIUM, or SHORT
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     */
    public static synchronized FastDateFormat getTimeInstance
        (Object style, TimeZone timeZone, Locale locale)
        throws IllegalArgumentException
    {
        Object key = style;

        if (timeZone != null) {
            key = new Pair(key, timeZone);
        }
        if (locale != null) {
            key = new Pair(key, locale);
        }

        FastDateFormat format = (FastDateFormat)cTimeInstanceCache.get(key);

        if (format == null) {
            int ts;
            try {
                ts = ((Integer)style).intValue();
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("Illegal time style: " + style);
            }

            if (locale == null) {
                locale = Locale.getDefault();
            }

            try {
                String pattern = ((SimpleDateFormat)DateFormat.getTimeInstance(ts, locale)).toPattern();
                format = getInstance(pattern, timeZone, locale);
                cTimeInstanceCache.put(key, format);
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("No date pattern for locale: " + locale);
            }
        }

        return format;
    }

    /**
     * @param dateStyle date style: FULL, LONG, MEDIUM, or SHORT
     * @param timeStyle time style: FULL, LONG, MEDIUM, or SHORT
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     */
    public static synchronized FastDateFormat getDateTimeInstance
        (Object dateStyle, Object timeStyle, TimeZone timeZone, Locale locale)
        throws IllegalArgumentException
    {
        Object key = new Pair(dateStyle, timeStyle);

        if (timeZone != null) {
            key = new Pair(key, timeZone);
        }
        if (locale != null) {
            key = new Pair(key, locale);
        }

        FastDateFormat format =
            (FastDateFormat)cDateTimeInstanceCache.get(key);

        if (format == null) {
            int ds;
            try {
                ds = ((Integer)dateStyle).intValue();
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("Illegal date style: " + dateStyle);
            }

            int ts;
            try {
                ts = ((Integer)timeStyle).intValue();
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("Illegal time style: " + timeStyle);
            }

            if (locale == null) {
                locale = Locale.getDefault();
            }

            try {
                String pattern = ((SimpleDateFormat)DateFormat.getDateTimeInstance(ds, ts, locale)).toPattern();
                format = getInstance(pattern, timeZone, locale);
                cDateTimeInstanceCache.put(key, format);
            }
            catch (ClassCastException e) {
                throw new IllegalArgumentException
                    ("No date time pattern for locale: " + locale);
            }
        }

        return format;
    }

    static synchronized String getTimeZoneDisplay(TimeZone tz,
                                                  boolean daylight,
                                                  int style,
                                                  Locale locale) {
        Object key = new TimeZoneDisplayKey(tz, daylight, style, locale);
        String value = (String)cTimeZoneDisplayCache.get(key);
        if (value == null) {
            // This is a very slow call, so cache the results.
            value = tz.getDisplayName(daylight, style, locale);
            cTimeZoneDisplayCache.put(key, value);
        }
        return value;
    }

    private static synchronized String getDefaultPattern() {
        if (cDefaultPattern == null) {
            cDefaultPattern = new SimpleDateFormat().toPattern();
        }
        return cDefaultPattern;
    }

    /**
     * Returns a list of Rules.
     */
    private static List parse(String pattern, TimeZone timeZone, Locale locale,
                              DateFormatSymbols symbols) {
        List rules = new ArrayList();

        String[] ERAs = symbols.getEras();
        String[] months = symbols.getMonths();
        String[] shortMonths = symbols.getShortMonths();
        String[] weekdays = symbols.getWeekdays();
        String[] shortWeekdays = symbols.getShortWeekdays();
        String[] AmPmStrings = symbols.getAmPmStrings();

        int length = pattern.length();
        int[] indexRef = new int[1];

        for (int i=0; i<length; i++) {
            indexRef[0] = i;
            String token = parseToken(pattern, indexRef);
            i = indexRef[0];

            int tokenLen = token.length();
            if (tokenLen == 0) {
                break;
            }

            Rule rule;
            char c = token.charAt(0);

            switch (c) {
            case 'G': // era designator (text)
                rule = new TextField(Calendar.ERA, ERAs);
                break;
            case 'y': // year (number)
                if (tokenLen >= 4) {
                    rule = new UnpaddedNumberField(Calendar.YEAR);
                }
                else {
                    rule = new TwoDigitYearField();
                }
                break;
            case 'M': // month in year (text and number)
                if (tokenLen >= 4) {
                    rule = new TextField(Calendar.MONTH, months);
                }
                else if (tokenLen == 3) {
                    rule = new TextField(Calendar.MONTH, shortMonths);
                }
                else if (tokenLen == 2) {
                    rule = new TwoDigitMonthField();
                }
                else {
                    rule = new UnpaddedMonthField();
                }
                break;
            case 'd': // day in month (number)
                rule = selectNumberRule(Calendar.DAY_OF_MONTH, tokenLen);
                break;
            case 'h': // hour in am/pm (number, 1..12)
                rule = new TwelveHourField
                    (selectNumberRule(Calendar.HOUR, tokenLen));
                break;
            case 'H': // hour in day (number, 0..23)
                rule = selectNumberRule(Calendar.HOUR_OF_DAY, tokenLen);
                break;
            case 'm': // minute in hour (number)
                rule = selectNumberRule(Calendar.MINUTE, tokenLen);
                break;
            case 's': // second in minute (number)
                rule = selectNumberRule(Calendar.SECOND, tokenLen);
                break;
            case 'S': // millisecond (number)
                rule = selectNumberRule(Calendar.MILLISECOND, tokenLen);
                break;
            case 'E': // day in week (text)
                rule = new TextField
                    (Calendar.DAY_OF_WEEK,
                     tokenLen < 4 ? shortWeekdays : weekdays);
                break;
            case 'D': // day in year (number)
                rule = selectNumberRule(Calendar.DAY_OF_YEAR, tokenLen);
                break;
            case 'F': // day of week in month (number)
                rule = selectNumberRule
                    (Calendar.DAY_OF_WEEK_IN_MONTH, tokenLen);
                break;
            case 'w': // week in year (number)
                rule = selectNumberRule(Calendar.WEEK_OF_YEAR, tokenLen);
                break;
            case 'W': // week in month (number)
                rule = selectNumberRule(Calendar.WEEK_OF_MONTH, tokenLen);
                break;
            case 'a': // am/pm marker (text)
                rule = new TextField(Calendar.AM_PM, AmPmStrings);
                break;
            case 'k': // hour in day (1..24)
                rule = new TwentyFourHourField
                    (selectNumberRule(Calendar.HOUR_OF_DAY, tokenLen));
                break;
            case 'K': // hour in am/pm (0..11)
                rule = selectNumberRule(Calendar.HOUR, tokenLen);
                break;
            case 'z': // time zone (text)
                if (tokenLen >= 4) {
                    rule = new TimeZoneRule(timeZone, locale, TimeZone.LONG);
                }
                else {
                    rule = new TimeZoneRule(timeZone, locale, TimeZone.SHORT);
                }
                break;
            case '\'': // literal text
                String sub = token.substring(1);
                if (sub.length() == 1) {
                    rule = new CharacterLiteral(sub.charAt(0));
                }
                else {
                    rule = new StringLiteral(new String(sub));
                }
                break;
            default:
                throw new IllegalArgumentException
                    ("Illegal pattern component: " + token);
            }

            rules.add(rule);
        }

        return rules;
    }

    private static String parseToken(String pattern, int[] indexRef) {
        StringBuffer buf = new StringBuffer();

        int i = indexRef[0];
        int length = pattern.length();

        char c = pattern.charAt(i);
        if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
            // Scan a run of the same character, which indicates a time
            // pattern.
            buf.append(c);

            while (i + 1 < length) {
                char peek = pattern.charAt(i + 1);
                if (peek == c) {
                    buf.append(c);
                    i++;
                }
                else {
                    break;
                }
            }
        }
        else {
            // This will identify token as text.
            buf.append('\'');

            boolean inLiteral = false;

            for (; i < length; i++) {
                c = pattern.charAt(i);

                if (c == '\'') {
                    if (i + 1 < length && pattern.charAt(i + 1) == '\'') {
                        // '' is treated as escaped '
                        i++;
                        buf.append(c);
                    }
                    else {
                        inLiteral = !inLiteral;
                    }
                }
                else if (!inLiteral &&
                         (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) {
                    i--;
                    break;
                }
                else {
                    buf.append(c);
                }
            }
        }

        indexRef[0] = i;
        return buf.toString();
    }

    private static NumberRule selectNumberRule(int field, int padding) {
        switch (padding) {
        case 1:
            return new UnpaddedNumberField(field);
        case 2:
            return new TwoDigitNumberField(field);
        default:
            return new PaddedNumberField(field, padding);
        }
    }

    private final String mPattern;
    private final TimeZone mTimeZone;
    private final Locale mLocale;
    private final Rule[] mRules;
    private final int mMaxLengthEstimate;

    private FastDateFormat() {
        this(getDefaultPattern(), null, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     */
    private FastDateFormat(String pattern) throws IllegalArgumentException {
        this(pattern, null, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     */
    private FastDateFormat(String pattern, TimeZone timeZone)
        throws IllegalArgumentException
    {
        this(pattern, timeZone, null, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param locale optional locale, overrides system locale
     */
    private FastDateFormat(String pattern, Locale locale)
        throws IllegalArgumentException
    {
        this(pattern, null, locale, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param symbols optional date format symbols, overrides symbols for
     * system locale
     */
    private FastDateFormat(String pattern, DateFormatSymbols symbols)
        throws IllegalArgumentException
    {
        this(pattern, null, null, symbols);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     */
    private FastDateFormat(String pattern, TimeZone timeZone, Locale locale)
        throws IllegalArgumentException
    {
        this(pattern, timeZone, locale, null);
    }

    /**
     * @param pattern {@link java.text.SimpleDateFormat} compatible pattern
     * @param timeZone optional time zone, overrides time zone of formatted
     * date
     * @param locale optional locale, overrides system locale
     * @param symbols optional date format symbols, overrides symbols for
     * provided locale
     */
    private FastDateFormat(String pattern, TimeZone timeZone, Locale locale,
                           DateFormatSymbols symbols)
        throws IllegalArgumentException
    {
        if (locale == null) {
            locale = Locale.getDefault();
        }

        mPattern = pattern;
        mTimeZone = timeZone;
        mLocale = locale;

        if (symbols == null) {
            symbols = new DateFormatSymbols(locale);
        }

        List rulesList = parse(pattern, timeZone, locale, symbols);
        mRules = (Rule[])rulesList.toArray(new Rule[rulesList.size()]);

        int len = 0;
        for (int i=mRules.length; --i >= 0; ) {
            len += mRules[i].estimateLength();
        }

        mMaxLengthEstimate = len;
    }

    public String format(Date date) {
        Calendar c = new GregorianCalendar(cDefaultTimeZone);
        c.setTime(date);
        if (mTimeZone != null) {
            c.setTimeZone(mTimeZone);
        }
        return applyRules(c, new StringBuffer(mMaxLengthEstimate)).toString();
    }

    public String format(Calendar calendar) {
        return format(calendar, new StringBuffer(mMaxLengthEstimate))
            .toString();
    }

    public StringBuffer format(Date date, StringBuffer buf) {
        Calendar c = new GregorianCalendar(cDefaultTimeZone);
        c.setTime(date);
        if (mTimeZone != null) {
            c.setTimeZone(mTimeZone);
        }
        return applyRules(c, buf);
    }

    public StringBuffer format(Calendar calendar, StringBuffer buf) {
        if (mTimeZone != null) {
            calendar = (Calendar)calendar.clone();
            calendar.setTimeZone(mTimeZone);
        }
        return applyRules(calendar, buf);
    }

    private StringBuffer applyRules(Calendar calendar, StringBuffer buf) {
        Rule[] rules = mRules;
        int len = mRules.length;
        for (int i=0; i<len; i++) {
            rules[i].appendTo(buf, calendar);
        }
        return buf;
    }

    public String getPattern() {
        return mPattern;
    }

    /**
     * Returns the time zone used by this formatter, or null if time zone of
     * formatted dates is used instead.
     */
    public TimeZone getTimeZone() {
        return mTimeZone;
    }

    public Locale getLocale() {
        return mLocale;
    }

    /**
     * Returns an estimate for the maximum length date that this date
     * formatter will produce. The actual formatted length will almost always
     * be less than or equal to this amount.
     */
    public int getMaxLengthEstimate() {
        return mMaxLengthEstimate;
    }

    private interface Rule {
        int estimateLength();

        void appendTo(StringBuffer buffer, Calendar calendar);
    }

    private interface NumberRule extends Rule {
        void appendTo(StringBuffer buffer, int value);
    }

    private static class CharacterLiteral implements Rule {
        private final char mValue;

        CharacterLiteral(char value) {
            mValue = value;
        }

        public int estimateLength() {
            return 1;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            buffer.append(mValue);
        }
    }

    private static class StringLiteral implements Rule {
        private final String mValue;

        StringLiteral(String value) {
            mValue = value;
        }

        public int estimateLength() {
            return mValue.length();
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            buffer.append(mValue);
        }
    }

    private static class TextField implements Rule {
        private final int mField;
        private final String[] mValues;

        TextField(int field, String[] values) {
            mField = field;
            mValues = values;
        }

        public int estimateLength() {
            int max = 0;
            for (int i=mValues.length; --i >= 0; ) {
                int len = mValues[i].length();
                if (len > max) {
                    max = len;
                }
            }
            return max;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            buffer.append(mValues[calendar.get(mField)]);
        }
    }

    private static class UnpaddedNumberField implements NumberRule {
        private final int mField;

        UnpaddedNumberField(int field) {
            mField = field;
        }

        public int estimateLength() {
            return 4;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(mField));
        }

        public final void appendTo(StringBuffer buffer, int value) {
            if (value < 10) {
                buffer.append((char)(value + '0'));
            }
            else if (value < 100) {
                buffer.append((char)(value / 10 + '0'));
                buffer.append((char)(value % 10 + '0'));
            }
            else {
                buffer.append(Integer.toString(value));
            }
        }
    }

    private static class UnpaddedMonthField implements NumberRule {
        UnpaddedMonthField() {
        }

        public int estimateLength() {
            return 2;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(Calendar.MONTH) + 1);
        }

        public final void appendTo(StringBuffer buffer, int value) {
            if (value < 10) {
                buffer.append((char)(value + '0'));
            }
            else {
                buffer.append((char)(value / 10 + '0'));
                buffer.append((char)(value % 10 + '0'));
            }
        }
    }

    private static class PaddedNumberField implements NumberRule {
        private final int mField;
        private final int mSize;

        PaddedNumberField(int field, int size) {
            if (size < 3) {
                // Should use UnpaddedNumberField or TwoDigitNumberField.
                throw new IllegalArgumentException();
            }
            mField = field;
            mSize = size;
        }

        public int estimateLength() {
            return 4;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(mField));
        }

        public final void appendTo(StringBuffer buffer, int value) {
            if (value < 100) {
                for (int i = mSize; --i >= 2; ) {
                    buffer.append('0');
                }
                buffer.append((char)(value / 10 + '0'));
                buffer.append((char)(value % 10 + '0'));
            }
            else {
                int digits;
                if (value < 1000) {
                    digits = 3;
                }
                else {
                    digits = (int)(Math.log(value) / LOG_10) + 1;
                }
                for (int i = mSize; --i >= digits; ) {
                    buffer.append('0');
                }
                buffer.append(Integer.toString(value));
            }
        }
    }

    private static class TwoDigitNumberField implements NumberRule {
        private final int mField;

        TwoDigitNumberField(int field) {
            mField = field;
        }

        public int estimateLength() {
            return 2;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(mField));
        }

        public final void appendTo(StringBuffer buffer, int value) {
            if (value < 100) {
                buffer.append((char)(value / 10 + '0'));
                buffer.append((char)(value % 10 + '0'));
            }
            else {
                buffer.append(Integer.toString(value));
            }
        }
    }

    private static class TwoDigitYearField implements NumberRule {
        TwoDigitYearField() {
        }

        public int estimateLength() {
            return 2;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(Calendar.YEAR) % 100);
        }

        public final void appendTo(StringBuffer buffer, int value) {
            buffer.append((char)(value / 10 + '0'));
            buffer.append((char)(value % 10 + '0'));
        }
    }

    private static class TwoDigitMonthField implements NumberRule {
        TwoDigitMonthField() {
        }

        public int estimateLength() {
            return 2;
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            appendTo(buffer, calendar.get(Calendar.MONTH) + 1);
        }

        public final void appendTo(StringBuffer buffer, int value) {
            buffer.append((char)(value / 10 + '0'));
            buffer.append((char)(value % 10 + '0'));
        }
    }

    private static class TwelveHourField implements NumberRule {
        private final NumberRule mRule;

        TwelveHourField(NumberRule rule) {
            mRule = rule;
        }

        public int estimateLength() {
            return mRule.estimateLength();
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            int value = calendar.get(Calendar.HOUR);
            if (value == 0) {
                value = calendar.getLeastMaximum(Calendar.HOUR) + 1;
            }
            mRule.appendTo(buffer, value);
        }

        public void appendTo(StringBuffer buffer, int value) {
            mRule.appendTo(buffer, value);
        }
    }

    private static class TwentyFourHourField implements NumberRule {
        private final NumberRule mRule;

        TwentyFourHourField(NumberRule rule) {
            mRule = rule;
        }

        public int estimateLength() {
            return mRule.estimateLength();
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            int value = calendar.get(Calendar.HOUR_OF_DAY);
            if (value == 0) {
                value = calendar.getMaximum(Calendar.HOUR_OF_DAY) + 1;
            }
            mRule.appendTo(buffer, value);
        }

        public void appendTo(StringBuffer buffer, int value) {
            mRule.appendTo(buffer, value);
        }
    }

    private static class TimeZoneRule implements Rule {
        private final TimeZone mTimeZone;
        private final Locale mLocale;
        private final int mStyle;
        private final String mStandard;
        private final String mDaylight;

        TimeZoneRule(TimeZone timeZone, Locale locale, int style) {
            mTimeZone = timeZone;
            mLocale = locale;
            mStyle = style;

            if (timeZone != null) {
                mStandard = getTimeZoneDisplay(timeZone, false, style, locale);
                mDaylight = getTimeZoneDisplay(timeZone, true, style, locale);
            }
            else {
                mStandard = null;
                mDaylight = null;
            }
        }

        public int estimateLength() {
            if (mTimeZone != null) {
                return Math.max(mStandard.length(), mDaylight.length());
            }
            else if (mStyle == TimeZone.SHORT) {
                return 4;
            }
            else {
                return 40;
            }
        }

        public void appendTo(StringBuffer buffer, Calendar calendar) {
            TimeZone timeZone;
            if ((timeZone = mTimeZone) != null) {
                if (timeZone.useDaylightTime() &&
                    calendar.get(Calendar.DST_OFFSET) != 0) {

                    buffer.append(mDaylight);
                }
                else {
                    buffer.append(mStandard);
                }
            }
            else {
                timeZone = calendar.getTimeZone();
                if (timeZone.useDaylightTime() &&
                    calendar.get(Calendar.DST_OFFSET) != 0) {

                    buffer.append(getTimeZoneDisplay
                                  (timeZone, true, mStyle, mLocale));
                }
                else {
                    buffer.append(getTimeZoneDisplay
                                  (timeZone, false, mStyle, mLocale));
                }
            }
        }
    }

    private static class TimeZoneDisplayKey {
        private final TimeZone mTimeZone;
        private final int mStyle;
        private final Locale mLocale;

        TimeZoneDisplayKey(TimeZone timeZone,
                           boolean daylight, int style, Locale locale) {
            mTimeZone = timeZone;
            if (daylight) {
                style |= 0x80000000;
            }
            mStyle = style;
            mLocale = locale;
        }

        @Override
		public int hashCode() {
            return mStyle * 31 + mLocale.hashCode();
        }

        @Override
		public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof TimeZoneDisplayKey) {
                TimeZoneDisplayKey other = (TimeZoneDisplayKey)obj;
                return
                    mTimeZone.equals(other.mTimeZone) &&
                    mStyle == other.mStyle &&
                    mLocale.equals(other.mLocale);
            }
            return false;
        }
    }

    private static class Pair implements Comparable, java.io.Serializable {
        private final Object mObj1;
        private final Object mObj2;

        public Pair(Object obj1, Object obj2) {
            mObj1 = obj1;
            mObj2 = obj2;
        }

        public int compareTo(Object obj) {
            if (this == obj) {
                return 0;
            }

            Pair other = (Pair)obj;

            Object a = mObj1;
            Object b = other.mObj1;

            firstTest: {
                if (a == null) {
                    if (b != null) {
                        return 1;
                    }
                    // Both a and b are null.
                    break firstTest;
                }
                else {
                    if (b == null) {
                        return -1;
                    }
                }

                int result = ((Comparable)a).compareTo(b);

                if (result != 0) {
                    return result;
                }
            }

            a = mObj2;
            b = other.mObj2;

            if (a == null) {
                if (b != null) {
                    return 1;
                }
                // Both a and b are null.
                return 0;
            }
            else {
                if (b == null) {
                    return -1;
                }
            }

            return ((Comparable)a).compareTo(b);
        }

        @Override
		public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Pair)) {
                return false;
            }

            Pair key = (Pair)obj;

            return
                (mObj1 == null ?
                 key.mObj1 == null : mObj1.equals(key.mObj1)) &&
                (mObj2 == null ?
                 key.mObj2 == null : mObj2.equals(key.mObj2));
        }

        @Override
		public int hashCode() {
            return
                (mObj1 == null ? 0 : mObj1.hashCode()) +
                (mObj2 == null ? 0 : mObj2.hashCode());
        }

        @Override
		public String toString() {
            return "[" + mObj1 + ':' + mObj2 + ']';
        }
    }
}
