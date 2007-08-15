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

package org.jivesoftware.util;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

/**
 * A formatter for formatting byte sizes. For example, formatting 12345 byes results in
 * "12.1 K" and 1234567 results in "1.18 MB".
 *
 * @author Bill Lynch
 */
public class ByteFormat extends Format {

    /**
     * Formats a long which represent a number of bytes.
     */
    public String format(long bytes) {
        return format(new Long(bytes));
    }

    /**
     * Formats a long which represent a number of kilobytes.
     */
    public String formatKB(long kilobytes) {
        return format(new Long(kilobytes * 1024));
    }

    /**
     * Format the given object (must be a Long).
     *
     * @param obj assumed to be the number of bytes as a Long.
     * @param buf the StringBuffer to append to.
     * @param pos
     * @return A formatted string representing the given bytes in more human-readable form.
     */
    public StringBuffer format(Object obj, StringBuffer buf, FieldPosition pos) {
        if (obj instanceof Long) {
            long numBytes = ((Long)obj).longValue();
            if (numBytes < 1024 * 1024) {
                DecimalFormat formatter = new DecimalFormat("#,##0.0");
                buf.append(formatter.format((double)numBytes / 1024.0)).append(" K");
            }
            else {
                DecimalFormat formatter = new DecimalFormat("#,##0.0");
                buf.append(formatter.format((double)numBytes / (1024.0 * 1024.0))).append(" MB");
            }
        }
        return buf;
    }

    /**
     * In this implementation, returns null always.
     *
     * @param source
     * @param pos
     * @return returns null in this implementation.
     */
    public Object parseObject(String source, ParsePosition pos) {
        return null;
    }
}