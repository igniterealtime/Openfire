package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public class RTCPBYEPacket extends RTCPPacket
{

    private String _reasonForLeaving;
    private Long _synchronizationAndContributingSources[];

    RTCPBYEPacket()
    {
    }

    protected void deserialize()
    {
        Integer startIndex = Integer.valueOf(0);
        ArrayList list = new ArrayList();
        Byte firstByte = super.getFirstByte();
        for(Integer i = Integer.valueOf(0); i.intValue() < firstByte.byteValue();)
        {
            list.add(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
            startIndex = Integer.valueOf(startIndex.intValue() + 4);
            Integer integer = i;
            Integer integer1 = i = Integer.valueOf(i.intValue() + 1);
            Integer _tmp = integer;
        }

        setSynchronizationAndContributingSources((Long[])list.toArray(new Long[0]));
        if(startIndex.intValue() < ArrayExtensions.getLength(super.getPayload()).intValue())
        {
            Short count = BitAssistant.toShortNetwork(new Byte[] {
                Byte.valueOf((byte)0), super.getPayload()[startIndex.intValue()]
            }, Integer.valueOf(0));
            setReasonForLeaving(Encoding.getUTF8().getString(super.getPayload(), startIndex.intValue() + 1, count.shortValue()));
        }
    }

    public RTCPBYEPacket(Long synchronizationAndContributingSources[])
        throws ArgumentNullException
    {
        if(synchronizationAndContributingSources == null)
        {
            throw new ArgumentNullException("synchronizationAndContributingSources");
        } else
        {
            setSynchronizationAndContributingSources(synchronizationAndContributingSources);
            return;
        }
    }

   public String getReasonForLeaving()
    {
        return _reasonForLeaving;
    }

    public Long[] getSynchronizationAndContributingSources()
    {
        return _synchronizationAndContributingSources;
    }

    protected void serialize()
    {
        super.setFirstByte(new Byte((new Integer(ArrayExtensions.getLength(getSynchronizationAndContributingSources()).intValue())).byteValue()));
        ArrayList list = new ArrayList();
        for(Integer i = Integer.valueOf(0); i.intValue() < ArrayExtensions.getLength(getSynchronizationAndContributingSources()).intValue();)
        {
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getSynchronizationAndContributingSources()[i.intValue()]));
            Integer integer = i;
            Integer integer1 = i = Integer.valueOf(i.intValue() + 1);
            Integer _tmp = integer;
        }

        if(!StringExtensions.isNullOrEmpty(getReasonForLeaving()).booleanValue())
        {
            list.add(new Byte((new Byte((new Integer(StringExtensions.getLength(getReasonForLeaving()).intValue())).byteValue())).byteValue()));
            Byte bytes[] = Encoding.getUTF8().getBytes(getReasonForLeaving());
            ArrayListExtensions.addRange(list, bytes);
            for(Integer j = Integer.valueOf(1 + ArrayExtensions.getLength(bytes).intValue()); j.intValue() % 4 > 0;)
            {
                list.add(new Byte((byte)0));
                Integer integer2 = j;
                Integer integer3 = j = Integer.valueOf(j.intValue() + 1);
                Integer _tmp1 = integer2;
            }

        }
        super.setPayload((Byte[])list.toArray(new Byte[0]));
    }

    public void setReasonForLeaving(String value)
    {
        _reasonForLeaving = value;
    }

    public void setSynchronizationAndContributingSources(Long value[])
    {
        _synchronizationAndContributingSources = value;
    }
}
