package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public class RTPPacket
{
    private Byte __payload[];
    private Byte __payloadType;
    private Long _contributingSources[];
    private Long _extension[];
    private Integer _extensionHeader;
    private Boolean _marker;
    private Boolean _padding;
    private Integer _sequenceNumber;
    private Long _synchronizationSource;
    private Long _timestamp;

    public RTPPacket(Byte payload[])
    {
        __payloadType = Byte.valueOf((byte)0);
        _extensionHeader = Integer.valueOf(0);
        _marker = Boolean.valueOf(false);
        _padding = Boolean.valueOf(false);
        _sequenceNumber = Integer.valueOf(0);
        _synchronizationSource = Long.valueOf(0L);
        _timestamp = Long.valueOf(0L);
        setPayload(payload);
    }

    public Byte[] getBytes()
    {
        return getBytes(this);
    }

    public static Byte[] getBytes(RTPPacket packet)
    {
        Boolean flag = Boolean.valueOf(packet.getExtensionHeader().intValue() != 0 || packet.getExtension() != null);
        ArrayList list = new ArrayList();
        Byte num = packet.getContributingSources() != null ? new Byte((byte)(BitAssistant.getShortBytesFromIntegerNetwork(ArrayExtensions.getLength(packet.getContributingSources()))[1].byteValue() & 0xf)) : new Byte((byte)0);
        list.add(new Byte((new Byte((new Integer(0x80 | (packet.getPadding().booleanValue() ? 0x20 : 0) | (flag.booleanValue() ? 0x10 : 0) | num.byteValue())).byteValue())).byteValue()));
        list.add(new Byte((new Byte((new Integer((packet.getMarker().booleanValue() ? 0x80 : 0) | packet.getPayloadType().byteValue() & 0x7f)).byteValue())).byteValue()));
        ArrayListExtensions.addRange(list, BitAssistant.getShortBytesFromIntegerNetwork(packet.getSequenceNumber()));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(packet.getTimestamp()));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(packet.getSynchronizationSource()));
        for(Integer num2 = Integer.valueOf(0); num2.intValue() < num.byteValue();)
        {
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(packet.getContributingSources()[num2.intValue()]));
            Integer integer = num2;
            Integer integer1 = num2 = Integer.valueOf(num2.intValue() + 1);
            Integer _tmp = integer;
        }

        if(flag.booleanValue())
        {
            Integer num3 = Integer.valueOf(packet.getExtension() != null ? ArrayExtensions.getLength(packet.getExtension()).intValue() : 0);
            ArrayListExtensions.addRange(list, BitAssistant.getShortBytesFromIntegerNetwork(packet.getExtensionHeader()));
            ArrayListExtensions.addRange(list, BitAssistant.getShortBytesFromIntegerNetwork(num3));
            for(Integer num2 = Integer.valueOf(0); num2.intValue() < num3.intValue();)
            {
                ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(packet.getExtension()[num2.intValue()]));
                Integer integer2 = num2;
                Integer integer3 = num2 = Integer.valueOf(num2.intValue() + 1);
                Integer _tmp1 = integer2;
            }

        }
        ArrayListExtensions.addRange(list, packet.getPayload());
        return (Byte[])list.toArray(new Byte[0]);
    }

    public Long[] getContributingSources()
    {
        return _contributingSources;
    }

    public Long[] getExtension()
    {
        return _extension;
    }

    public Integer getExtensionHeader()
    {
        return _extensionHeader;
    }

    public Boolean getMarker()
    {
        return _marker;
    }

    public Boolean getPadding()
    {
        return _padding;
    }

    public Byte[] getPayload()
    {
        return __payload;
    }

    public Byte getPayloadType()
    {
        return __payloadType;
    }

    public Integer getSequenceNumber()
    {
        return _sequenceNumber;
    }

    public Long getSynchronizationSource()
    {
        return _synchronizationSource;
    }

    public Long getTimestamp()
    {
        return _timestamp;
    }

    public static RTPPacket parseBytes(Byte bytes[])
        throws Exception
    {
        if(ArrayExtensions.getLength(bytes).intValue() < 12 || (bytes[0].byteValue() & 0xc0) != 128)
            return null;
        Boolean flag = Boolean.valueOf((bytes[0].byteValue() & 0x20) == 32);
        Boolean flag2 = Boolean.valueOf((bytes[0].byteValue() & 0x10) == 16);
        Byte num = new Byte((byte)(bytes[0].byteValue() & 0xf));
        Boolean flag3 = Boolean.valueOf((bytes[1].byteValue() & 0x80) == 128);
        Byte num2 = new Byte((byte)(bytes[1].byteValue() & 0x7f));
        Integer num3 = BitAssistant.toIntegerFromShortNetwork(bytes, Integer.valueOf(2));
        Long num4 = BitAssistant.toLongFromIntegerNetwork(bytes, Integer.valueOf(4));
        Long num5 = BitAssistant.toLongFromIntegerNetwork(bytes, Integer.valueOf(8));
        Integer startIndex = Integer.valueOf(12);
        Long numArray[] = null;
        if(num.byteValue() > 0)
        {
            numArray = new Long[num.byteValue()];
            for(Integer num7 = Integer.valueOf(0); num7.intValue() < num.byteValue();)
            {
                numArray[num7.intValue()] = BitAssistant.toLongFromIntegerNetwork(bytes, startIndex);
                startIndex = Integer.valueOf(startIndex.intValue() + 4);
                Integer integer = num7;
                Integer integer1 = num7 = Integer.valueOf(num7.intValue() + 1);
                Integer _tmp = integer;
            }

        }
        Integer num8 = Integer.valueOf(0);
        Long numArray2[] = null;
        if(flag2.booleanValue())
        {
            num8 = BitAssistant.toIntegerFromShortNetwork(bytes, startIndex);
            startIndex = Integer.valueOf(startIndex.intValue() + 2);
            Integer num9 = BitAssistant.toIntegerFromShortNetwork(bytes, startIndex);
            startIndex = Integer.valueOf(startIndex.intValue() + 2);
            if(num9.intValue() > 0)
            {
                numArray2 = new Long[num9.intValue()];
                for(Integer num7 = Integer.valueOf(0); num7.intValue() < num9.intValue();)
                {
                    numArray2[num7.intValue()] = BitAssistant.toLongFromIntegerNetwork(bytes, startIndex);
                    startIndex = Integer.valueOf(startIndex.intValue() + 4);
                    Integer integer2 = num7;
                    Integer integer3 = num7 = Integer.valueOf(num7.intValue() + 1);
                    Integer _tmp1 = integer2;
                }

            }
        }
        RTPPacket packet = new RTPPacket(BitAssistant.subArray(bytes, startIndex));
        packet.setPayloadType(num2);
        packet.setPadding(flag);
        packet.setMarker(flag3);
        packet.setSequenceNumber(num3);
        packet.setTimestamp(num4);
        packet.setSynchronizationSource(num5);
        packet.setContributingSources(numArray);
        packet.setExtensionHeader(num8);
        packet.setExtension(numArray2);
        return packet;
    }

    public void setContributingSources(Long value[])
    {
        _contributingSources = value;
    }

    public void setExtension(Long value[])
    {
        _extension = value;
    }

    public void setExtensionHeader(Integer value)
    {
        _extensionHeader = value;
    }

    public void setMarker(Boolean value)
    {
        _marker = value;
    }

    public void setPadding(Boolean value)
    {
        _padding = value;
    }

    public void setPayload(Byte value[])
    {
        if(value == null)
            __payload = new Byte[0];
        else
            __payload = value;
    }

    public void setPayloadType(Byte value)
        throws Exception
    {
        if(value.byteValue() < 0)
        {
            throw new Exception("Payload type is invalid.");
        } else
        {
            __payloadType = value;
            return;
        }
    }

    public void setSequenceNumber(Integer value)
    {
        _sequenceNumber = value;
    }

    public void setSynchronizationSource(Long value)
    {
        _synchronizationSource = value;
    }

    public void setTimestamp(Long value)
    {
        _timestamp = value;
    }
}
