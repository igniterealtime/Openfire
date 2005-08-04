/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.jivesoftware.util.log.format;

import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.log.ContextMap;
import org.jivesoftware.util.log.LogEvent;
import org.jivesoftware.util.log.Priority;

import java.io.StringWriter;
import java.util.Date;
import java.util.Stack;

/**
 * This formater formats the LogEvents according to a input pattern
 * string.
 * <p/>
 * The format of each pattern element can be %[+|-][#[.#]]{field:subformat}.
 * </p>
 * <ul>
 * <li>The +|- indicates left or right justify.
 * </li>
 * <li>The #.# indicates the minimum and maximum size of output.<br>
 * You may omit the values and the field will be formatted without size
 * restriction.<br>
 * You may specify '#', or '#.' to define an minimum size, only.</br>
 * You may specify '.#' to define an maximum size only.
 * </li>
 * <li>
 * 'field' indicates which field is to be output and must be one of
 * properties of LogEvent.<br>
 * Currently following fields are supported:
 * <dl>
 * <dt>category</dt>
 * <dd>Category value of the logging event.</dd>
 * <dt>context</dt>
 * <dd>Context value of the logging event.</dd>
 * <dt>message</dt>
 * <dd>Message value of the logging event.</dd>
 * <dt>time</dt>
 * <dd>Time value of the logging event.</dd>
 * <dt>rtime</dt>
 * <dd>Relative time value of the logging event.</dd>
 * <dt>throwable</dt>
 * <dd>Throwable value of the logging event.</dd>
 * <dt>priority</dt>
 * <dd>Priority value of the logging event.</dd>
 * </dl>
 * </li>
 * <li>'subformat' indicates a particular subformat and is currently only used
 * for category context to specify the context map parameter name.
 * </li>
 * </ul>
 * <p>A simple example of a typical PatternFormatter format:
 * </p>
 * <pre><code>%{time} %5.5{priority}[%-10.10{category}]: %{message}
 * </pre></code>
 * <p/>
 * This format string will format a log event printing first time value of
 * of log event with out size restriction, next priority with minum and maximum size 5,
 * next category right justified having minmum and maximum size of 10,
 * at last the message of the log event without size restriction.
 * </p>
 * <p>A formatted sample message of the above pattern format:
 * </p>
 * <pre><code>1000928827905 DEBUG [     junit]: Sample message
 * </pre><code>
 *
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @author <a href="mailto:sylvain@apache.org">Sylvain Wallez</a>
 * @version CVS $Revision$ $Date$
 */
public class PatternFormatter implements Formatter {
    private final static int TYPE_TEXT = 1;
    private final static int TYPE_CATEGORY = 2;
    private final static int TYPE_CONTEXT = 3;
    private final static int TYPE_MESSAGE = 4;
    private final static int TYPE_TIME = 5;
    private final static int TYPE_RELATIVE_TIME = 6;
    private final static int TYPE_THROWABLE = 7;
    private final static int TYPE_PRIORITY = 8;

    /**
     * The maximum value used for TYPEs. Subclasses can define their own TYPEs
     * starting at <code>MAX_TYPE + 1</code>.
     */
    protected final static int MAX_TYPE = TYPE_PRIORITY;

    private final static String TYPE_CATEGORY_STR = "category";
    private final static String TYPE_CONTEXT_STR = "context";
    private final static String TYPE_MESSAGE_STR = "message";
    private final static String TYPE_TIME_STR = "time";
    private final static String TYPE_RELATIVE_TIME_STR = "rtime";
    private final static String TYPE_THROWABLE_STR = "throwable";
    private final static String TYPE_PRIORITY_STR = "priority";

    private final static String SPACE_16 = "                ";
    private final static String SPACE_8 = "        ";
    private final static String SPACE_4 = "    ";
    private final static String SPACE_2 = "  ";
    private final static String SPACE_1 = " ";

    private final static String EOL = System.getProperty("line.separator", "\n");

    protected static class PatternRun {
        public String m_data;
        public boolean m_rightJustify;
        public int m_minSize;
        public int m_maxSize;
        public int m_type;
        public String m_format;
    }

    private PatternRun m_formatSpecification[];

    private FastDateFormat m_simpleDateFormat;
    private final Date m_date = new Date();

    /**
     * @deprecated Use constructor PatternFormatter(String pattern) as this does not
     *             correctly initialize object
     */
    public PatternFormatter() {
    }

    public PatternFormatter(final String pattern) {
        parse(pattern);
    }

    /**
     * Extract and build a pattern from input string.
     *
     * @param stack   the stack on which to place patterns
     * @param pattern the input string
     * @param index   the start of pattern run
     * @return the number of characters in pattern run
     */
    private int addPatternRun(final Stack stack,
                              final char pattern[],
                              int index) {
        final PatternRun run = new PatternRun();
        final int start = index++;

        //first check for a +|- sign
        if ('+' == pattern[index])
            index++;
        else if ('-' == pattern[index]) {
            run.m_rightJustify = true;
            index++;
        }

        if (Character.isDigit(pattern[index])) {
            int total = 0;
            while (Character.isDigit(pattern[index])) {
                total = total * 10 + (pattern[index] - '0');
                index++;
            }
            run.m_minSize = total;
        }

        //check for . sign indicating a maximum is to follow
        if (index < pattern.length && '.' == pattern[index]) {
            index++;

            if (Character.isDigit(pattern[index])) {
                int total = 0;
                while (Character.isDigit(pattern[index])) {
                    total = total * 10 + (pattern[index] - '0');
                    index++;
                }
                run.m_maxSize = total;
            }
        }

        if (index >= pattern.length || '{' != pattern[index]) {
            throw
                    new IllegalArgumentException("Badly formed pattern at character " +
                    index);
        }

        int typeStart = index;

        while (index < pattern.length &&
                pattern[index] != ':' && pattern[index] != '}') {
            index++;
        }

        int typeEnd = index - 1;

        final String type =
                new String(pattern, typeStart + 1, typeEnd - typeStart);

        run.m_type = getTypeIdFor(type);

        if (index < pattern.length && pattern[index] == ':') {
            index++;
            while (index < pattern.length && pattern[index] != '}') index++;

            final int length = index - typeEnd - 2;

            if (0 != length) {
                run.m_format = new String(pattern, typeEnd + 2, length);
            }
        }

        if (index >= pattern.length || '}' != pattern[index]) {
            throw new
                    IllegalArgumentException("Unterminated type in pattern at character "
                    + index);
        }

        index++;

        stack.push(run);

        return index - start;
    }

    /**
     * Extract and build a text run  from input string.
     * It does special handling of '\n' and '\t' replaceing
     * them with newline and tab.
     *
     * @param stack   the stack on which to place runs
     * @param pattern the input string
     * @param index   the start of the text run
     * @return the number of characters in run
     */
    private int addTextRun(final Stack stack,
                           final char pattern[],
                           int index) {
        final PatternRun run = new PatternRun();
        final int start = index;
        boolean escapeMode = false;

        if ('%' == pattern[index]) index++;

        final StringBuffer sb = new StringBuffer();

        while (index < pattern.length && pattern[index] != '%') {
            if (escapeMode) {
                if ('n' == pattern[index])
                    sb.append(EOL);
                else if ('t' == pattern[index])
                    sb.append('\t');
                else
                    sb.append(pattern[index]);
                escapeMode = false;
            }
            else if ('\\' == pattern[index])
                escapeMode = true;
            else
                sb.append(pattern[index]);
            index++;
        }

        run.m_data = sb.toString();
        run.m_type = TYPE_TEXT;

        stack.push(run);

        return index - start;
    }

    /**
     * Utility to append a string to buffer given certain constraints.
     *
     * @param sb           the StringBuffer
     * @param minSize      the minimum size of output (0 to ignore)
     * @param maxSize      the maximum size of output (0 to ignore)
     * @param rightJustify true if the string is to be right justified in it's box.
     * @param output       the input string
     */
    private void append(final StringBuffer sb,
                        final int minSize,
                        final int maxSize,
                        final boolean rightJustify,
                        final String output) {
        final int size = output.length();

        if (size < minSize) {
            //assert( minSize > 0 );
            if (rightJustify) {
                appendWhiteSpace(sb, minSize - size);
                sb.append(output);
            }
            else {
                sb.append(output);
                appendWhiteSpace(sb, minSize - size);
            }
        }
        else if (maxSize > 0 && maxSize < size) {
            if (rightJustify) {
                sb.append(output.substring(size - maxSize));
            }
            else {
                sb.append(output.substring(0, maxSize));
            }
        }
        else {
            sb.append(output);
        }
    }

    /**
     * Append a certain number of whitespace characters to a StringBuffer.
     *
     * @param sb     the StringBuffer
     * @param length the number of spaces to append
     */
    private void appendWhiteSpace(final StringBuffer sb, int length) {
        while (length >= 16) {
            sb.append(SPACE_16);
            length -= 16;
        }

        if (length >= 8) {
            sb.append(SPACE_8);
            length -= 8;
        }

        if (length >= 4) {
            sb.append(SPACE_4);
            length -= 4;
        }

        if (length >= 2) {
            sb.append(SPACE_2);
            length -= 2;
        }

        if (length >= 1) {
            sb.append(SPACE_1);
            length -= 1;
        }
    }

    /**
     * Format the event according to the pattern.
     *
     * @param event the event
     * @return the formatted output
     */
    public String format(final LogEvent event) {
        final StringBuffer sb = new StringBuffer();

        for (int i = 0; i < m_formatSpecification.length; i++) {
            final PatternRun run = m_formatSpecification[i];

            //treat text differently as it doesn't need min/max padding
            if (run.m_type == TYPE_TEXT) {
                sb.append(run.m_data);
            }
            else {
                final String data = formatPatternRun(event, run);

                if (null != data) {
                    append(sb, run.m_minSize, run.m_maxSize, run.m_rightJustify, data);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Formats a single pattern run (can be extended in subclasses).
     *
     * @param run the pattern run to format.
     * @return the formatted result.
     */
    protected String formatPatternRun(final LogEvent event, final PatternRun run) {
        switch (run.m_type) {
            case TYPE_RELATIVE_TIME:
                return getRTime(event.getRelativeTime(), run.m_format);
            case TYPE_TIME:
                return getTime(event.getTime(), run.m_format);
            case TYPE_THROWABLE:
                return getStackTrace(event.getThrowable(), run.m_format);
            case TYPE_MESSAGE:
                return getMessage(event.getMessage(), run.m_format);
            case TYPE_CATEGORY:
                return getCategory(event.getCategory(), run.m_format);
            case TYPE_PRIORITY:
                return getPriority(event.getPriority(), run.m_format);

            case TYPE_CONTEXT:
//            if( null == run.m_format ||
//                run.m_format.startsWith( "stack" ) )
//            {
//                //Print a warning out to stderr here
//                //to indicate you are using a deprecated feature?
//                return getContext( event.getContextStack(), run.m_format );
//            }
//            else
//            {
                return getContextMap(event.getContextMap(), run.m_format);
//            }

            default:
                throw new IllegalStateException("Unknown Pattern specification." + run.m_type);
        }
    }

    /**
     * Utility method to format category.
     *
     * @param category the category string
     * @param format   ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getCategory(final String category, final String format) {
        return category;
    }

    /**
     * Get formatted priority string.
     */
    protected String getPriority(final Priority priority, final String format) {
        return priority.getName();
    }

//    /**
//     * Utility method to format context.
//     *
//     * @param context the context string
//     * @param format ancilliary format parameter - allowed to be null
//     * @return the formatted string
//     * @deprecated Use getContextStack rather than this method
//     */
//    protected String getContext( final ContextStack stack, final String format )
//    {
//        return getContextStack( stack, format );
//    }

//    /**
//     * Utility method to format context.
//     *
//     * @param context the context string
//     * @param format ancilliary format parameter - allowed to be null
//     * @return the formatted string
//     */
//    protected String getContextStack( final ContextStack stack, final String format )
//    {
//        if( null == stack ) return "";
//        return stack.toString( Integer.MAX_VALUE );
//    }

    /**
     * Utility method to format context map.
     *
     * @param map    the context map
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getContextMap(final ContextMap map, final String format) {
        if (null == map) return "";
        return map.get(format, "").toString();
    }

    /**
     * Utility method to format message.
     *
     * @param message the message string
     * @param format  ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getMessage(final String message, final String format) {
        return message;
    }

    /**
     * Utility method to format stack trace.
     *
     * @param throwable the throwable instance
     * @param format    ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getStackTrace(final Throwable throwable, final String format) {
        if (null == throwable) return "";
        final StringWriter sw = new StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Utility method to format relative time.
     *
     * @param time   the time
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getRTime(final long time, final String format) {
        return getTime(time, format);
    }

    /**
     * Utility method to format time.
     *
     * @param time   the time
     * @param format ancilliary format parameter - allowed to be null
     * @return the formatted string
     */
    protected String getTime(final long time, final String format) {
        if (null == format) {
            return Long.toString(time);
        }
        else {
            synchronized (m_date) {
                if (null == m_simpleDateFormat) {
                    m_simpleDateFormat = FastDateFormat.getInstance(format);
                }
                m_date.setTime(time);
                return m_simpleDateFormat.format(m_date);
            }
        }
    }

    /**
     * Retrieve the type-id for a particular string.
     *
     * @param type the string
     * @return the type-id
     */
    protected int getTypeIdFor(final String type) {
        if (type.equalsIgnoreCase(TYPE_CATEGORY_STR))
            return TYPE_CATEGORY;
        else if (type.equalsIgnoreCase(TYPE_CONTEXT_STR))
            return TYPE_CONTEXT;
        else if (type.equalsIgnoreCase(TYPE_MESSAGE_STR))
            return TYPE_MESSAGE;
        else if (type.equalsIgnoreCase(TYPE_PRIORITY_STR))
            return TYPE_PRIORITY;
        else if (type.equalsIgnoreCase(TYPE_TIME_STR))
            return TYPE_TIME;
        else if (type.equalsIgnoreCase(TYPE_RELATIVE_TIME_STR))
            return TYPE_RELATIVE_TIME;
        else if (type.equalsIgnoreCase(TYPE_THROWABLE_STR)) {
            return TYPE_THROWABLE;
        }
        else {
            throw new IllegalArgumentException("Unknown Type in pattern - " +
                    type);
        }
    }

    /**
     * Parse the input pattern and build internal data structures.
     *
     * @param patternString the pattern
     */
    protected final void parse(final String patternString) {
        final Stack stack = new Stack();
        final int size = patternString.length();
        final char pattern[] = new char[size];
        int index = 0;

        patternString.getChars(0, size, pattern, 0);

        while (index < size) {
            if (pattern[index] == '%' &&
                    !(index != size - 1 && pattern[index + 1] == '%')) {
                index += addPatternRun(stack, pattern, index);
            }
            else {
                index += addTextRun(stack, pattern, index);
            }
        }

        final int elementCount = stack.size();

        m_formatSpecification = new PatternRun[elementCount];

        for (int i = 0; i < elementCount; i++) {
            m_formatSpecification[i] = (PatternRun)stack.elementAt(i);
        }
    }

    /**
     * Set the string description that the format is extracted from.
     *
     * @param format the string format
     * @deprecated Parse format in via constructor rather than use this method
     */
    public void setFormat(final String format) {
        parse(format);
    }
}
