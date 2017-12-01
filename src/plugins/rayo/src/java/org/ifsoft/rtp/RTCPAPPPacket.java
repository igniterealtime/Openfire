package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.*;
import java.util.ArrayList;


public class RTCPAPPPacket extends RTCPPacket
{
    private Byte __data[];
    private Byte __name[];
    private Byte _subType;
    private Long _synchronizationSource;

    protected void deserialize()
        throws Exception
    {
        setSubType(super.getFirstByte());
        setSynchronizationSource(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), Integer.valueOf(0)));
        setName(BitAssistant.subArray(super.getPayload(), Integer.valueOf(4), Integer.valueOf(4)));
        if(ArrayExtensions.getLength(super.getPayload()).intValue() > 8)
            setData(BitAssistant.subArray(super.getPayload(), Integer.valueOf(8), Integer.valueOf(ArrayExtensions.getLength(super.getPayload()).intValue() - 8)));
    }

    RTCPAPPPacket()
    {
        _subType = Byte.valueOf((byte)0);
        _synchronizationSource = Long.valueOf(0L);
        __name = new Byte[4];
        initializeName();
    }

    public RTCPAPPPacket(Long synchronizationSource)
    {
        _subType = Byte.valueOf((byte)0);
        _synchronizationSource = Long.valueOf(0L);
        __name = new Byte[4];
        initializeName();
        setSynchronizationSource(synchronizationSource);
    }

    public Byte[] getData()
    {
        return __data;
    }

    public Byte[] getName()
    {
        return __name;
    }

    public Byte getSubType()
    {
        return _subType;
    }

    public Long getSynchronizationSource()
    {
        return _synchronizationSource;
    }

    private void initializeName()
    {
        for(Integer i = Integer.valueOf(0); i.intValue() < ArrayExtensions.getLength(__name).intValue();)
        {
            __name[i.intValue()] = Byte.valueOf((byte)0);
            Integer integer = i;
            Integer integer1 = i = Integer.valueOf(i.intValue() + 1);
            Integer _tmp = integer;
        }

    }

    protected void serialize()
    {
        super.setFirstByte(getSubType());
        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getSynchronizationSource()));
        ArrayListExtensions.addRange(list, getName());
        if(getData() != null)
            ArrayListExtensions.addRange(list, getData());
        super.setPayload((Byte[])list.toArray(new Byte[0]));
    }

    public void setData(Byte value[])
        throws Exception
    {
        if(value != null && ArrayExtensions.getLength(value).intValue() % 4 != 0)
        {
            throw new Exception("Data must be a multiple of four bytes.");
        } else
        {
            __data = value;
            return;
        }
    }

    public void setName(Byte value[])
        throws Exception
    {
        if(value == null || ArrayExtensions.getLength(value).intValue() != 4)
        {
            throw new Exception("Name must be four bytes interpreted as a sequence of four ASCII characters.");
        } else
        {
            __name = value;
            return;
        }
    }

    public void setSubType(Byte value)
    {
        _subType = value;
    }

    private void setSynchronizationSource(Long value)
    {
        _synchronizationSource = value;
    }
}
