package com.reucon.openfire.plugin.archive.util;

import org.jivesoftware.util.JiveConstants;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Utility class to parse and format dates in UTC that adhere to the DateTime format specified
 * in Jabber Date and Time Profiles.
 */
public class XmppDateUtil
{
    private static final DateFormat dateFormat;
    private static final DateFormat dateFormatWithoutMillis;

    static
    {
        dateFormat =  new SimpleDateFormat(JiveConstants.XMPP_DATETIME_FORMAT);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormatWithoutMillis =  new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormatWithoutMillis.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private XmppDateUtil()
    {

    }

    public static Date parseDate(String dateString)
    {
        Date date = null;

        if (dateString == null)
        {
            return null;
        }

        synchronized(dateFormat)
        {
            try
            {
                date = dateFormat.parse(dateString);
            }
            catch (ParseException e)
            {
                // ignore
            }
        }

        if (date != null)
        {
            return date;
        }

        synchronized(dateFormatWithoutMillis)
        {
            try
            {
                date = dateFormatWithoutMillis.parse(dateString);
            }
            catch (ParseException e)
            {
                // ignore
            }
        }

        return date;
    }

    public static String formatDate(Date date)
    {
        if (date == null)
        {
            return null;
        }

        synchronized(dateFormat)
        {
            return dateFormat.format(date);
        }
    }
}
