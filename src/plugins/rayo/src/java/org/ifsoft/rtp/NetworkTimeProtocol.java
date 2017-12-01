package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.Date;

public class NetworkTimeProtocol
{
    private static Date _baseTime = new Date();

    static
    {
        _baseTime = DateExtensions.createDate(1900, 1, 1, 0, 0, 0);
    }

    public static Long dateTimeToNTP(Date dateTime)
    {
        TimeSpan span = new TimeSpan(Long.valueOf(DateExtensions.getTicks(dateTime).longValue() - DateExtensions.getTicks(_baseTime).longValue()));
        Long totalMilliseconds = new Long((new Double(span.getTotalMilliseconds().doubleValue())).longValue());
        Long num2 = Long.valueOf(totalMilliseconds.longValue() / 1000L);
        Long num3 = Long.valueOf(totalMilliseconds.longValue() % 1000L);
        Long num4 = Long.valueOf((0x100000000L * num3.longValue()) / 1000L);
        Byte integerBytesFromLongNetwork[] = BitAssistant.getIntegerBytesFromLongNetwork(num2);
        Byte buffer2[] = BitAssistant.getIntegerBytesFromLongNetwork(num4);
        Byte buffer3[] = {
            integerBytesFromLongNetwork[0], integerBytesFromLongNetwork[1], integerBytesFromLongNetwork[2], integerBytesFromLongNetwork[3], buffer2[0], buffer2[1], buffer2[2], buffer2[3]
        };
        return BitAssistant.toLongNetwork(buffer3, Integer.valueOf(0));
    }

    public NetworkTimeProtocol()
    {
    }

    public static Long getUtcNow()
    {
        return dateTimeToNTP(DateExtensions.getUtcNow());
    }

    public static Date nTPToDateTime(Long ntp)
    {
        Byte longBytesNetwork[] = BitAssistant.getLongBytesNetwork(ntp);
        Long num = BitAssistant.toLongFromIntegerNetwork(longBytesNetwork, Integer.valueOf(0));
        Long num3 = Long.valueOf((BitAssistant.toLongFromIntegerNetwork(longBytesNetwork, Integer.valueOf(4)).longValue() * 1000L) / 0x100000000L);
        return DateExtensions.addMilliseconds(DateExtensions.addSeconds(_baseTime, new Double((new Long(num.longValue())).doubleValue())), new Double((new Long(num3.longValue())).doubleValue()));
    }
}
