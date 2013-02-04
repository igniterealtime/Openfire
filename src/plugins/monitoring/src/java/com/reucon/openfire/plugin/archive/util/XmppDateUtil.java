package com.reucon.openfire.plugin.archive.util;

import org.jivesoftware.util.XMPPDateTimeFormat;

import java.text.ParseException;
import java.util.Date;

/**
 * Utility class to parse and format dates in UTC that adhere to the DateTime format specified
 * in Jabber Date and Time Profiles.
 */
public class XmppDateUtil
{
    private static final XMPPDateTimeFormat xmppDateTime = new XMPPDateTimeFormat();

    public static Date parseDate(String dateString)
    {
        try {
            return xmppDateTime.parseString(dateString);
        } catch (ParseException e) {
            return null;
        }
    }

    public static String formatDate(Date date)
    {
        if (date == null)
        {
            return null;
        }
        return XMPPDateTimeFormat.format(date);
    }
}
