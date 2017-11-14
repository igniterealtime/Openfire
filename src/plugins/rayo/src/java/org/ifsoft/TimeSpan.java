package org.ifsoft;

public class TimeSpan
{
    Integer _hours;
    Integer _minutes;
    Integer _seconds;
    Integer _milliseconds;

    public TimeSpan(Long ticks)
    {
        Long milliseconds = Long.valueOf(ticks.longValue() / 10000L);
        _milliseconds = new Integer((int)(milliseconds.longValue() % 1000L));
        milliseconds = Long.valueOf(milliseconds.longValue() - (long)_milliseconds.intValue());
        Long seconds = Long.valueOf(milliseconds.longValue() / 1000L);
        _seconds = new Integer((int)(seconds.longValue() % 60L));
        seconds = Long.valueOf(seconds.longValue() - (long)_seconds.intValue());
        Long minutes = Long.valueOf(seconds.longValue() / 60L);
        _minutes = new Integer((int)(minutes.longValue() % 60L));
        minutes = Long.valueOf(minutes.longValue() - (long)_minutes.intValue());
        Long hours = Long.valueOf(minutes.longValue() / 60L);
        _hours = Integer.valueOf(hours.intValue());
    }

    public TimeSpan(Integer hours, Integer minutes, Integer seconds)
    {
        _hours = hours;
        _minutes = minutes;
        _seconds = seconds;
    }

    public Double getTotalSeconds()
    {
        return new Double((long)_hours.intValue() * 3600L + (long)_minutes.intValue() * 60L + (long)_seconds.intValue());
    }

    public Double getTotalMilliseconds()
    {
        return new Double((long)_hours.intValue() * 0x36ee80L + (long)_minutes.intValue() * 60000L + (long)_seconds.intValue() * 1000L + (long)_milliseconds.intValue());
    }

}
