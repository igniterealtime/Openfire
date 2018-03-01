package org.ifsoft;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateExtensions
{
    private static Long origin;

    static
    {
        try
        {
            origin = Long.valueOf(createDate(1, 1, 1, 0, 0, 0).getTime());
        }
        catch(Exception e) { }
    }

    public DateExtensions()
    {
    }

    public static Date createDate(int year, int month, int date, int hour, int minute, int second)
    {
        return new Date(Date.UTC(year - 1900, month - 1, date, hour, minute, second));
    }

    public static Date getNow()
    {
        return new Date();
    }

    public static Date getUtcNow()
    {
        return new Date();
    }

    public static Long getTicks(Date date)
    {
        long milliseconds = date.getTime() - origin.longValue();
        return Long.valueOf((long)((double)milliseconds * 10000D));
    }

    public static Date toUniversalTime(Date date)
    {
        return new Date(date.getTime());
    }

    public static String toString(Date date, String format, IFormatProvider provider)
    {
        format = format.replace("T", "'T'").replace("f", "S");
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        return formatter.format(date).substring(0, 22).replace("'T'", "T");
    }

    public static Integer getHour(Date date)
    {
        return Integer.valueOf(date.getHours());
    }

    public static Integer getMinute(Date date)
    {
        return Integer.valueOf(date.getMinutes());
    }

    public static Integer getSecond(Date date)
    {
        return Integer.valueOf(date.getSeconds());
    }

    public static Date addSeconds(Date date, Double seconds)
    {
        return new Date(date.getTime() + (long)(seconds.doubleValue() * 1000D));
    }

    public static Date addMilliseconds(Date date, Double milliseconds)
    {
        return new Date(date.getTime() + (long)(milliseconds.doubleValue() * 1.0D));
    }
}
