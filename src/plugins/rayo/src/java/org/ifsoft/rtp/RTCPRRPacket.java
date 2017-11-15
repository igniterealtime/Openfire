package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.ArrayList;

public class RTCPRRPacket extends RTCPPacket
{
    private RTCPReportBlock _reportBlocks[];
    private Long _synchronizationSource;

    protected void deserialize()
    {
        Integer startIndex = Integer.valueOf(0);
        setSynchronizationSource(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        Byte firstByte = super.getFirstByte();
        if(startIndex.intValue() < ArrayExtensions.getLength(super.getPayload()).intValue())
        {
            ArrayList list = new ArrayList();
            for(Integer i = Integer.valueOf(0); i.intValue() < firstByte.byteValue();)
            {
                RTCPReportBlock item = RTCPReportBlock.parseBytes(BitAssistant.subArray(super.getPayload(), startIndex, Integer.valueOf(24)));
                if(item != null)
                    list.add(item);
                startIndex = Integer.valueOf(startIndex.intValue() + 24);
                //item = i;
                Integer integer = i = Integer.valueOf(i.intValue() + 1);
                RTCPReportBlock _tmp = item;
            }

            setReportBlocks((RTCPReportBlock[])list.toArray(new RTCPReportBlock[0]));
        }
    }

    public RTCPRRPacket(Long synchronizationSource)
    {
        _synchronizationSource = Long.valueOf(0L);
        setSynchronizationSource(synchronizationSource);
    }

    RTCPRRPacket()
    {
        _synchronizationSource = Long.valueOf(0L);
    }

    public RTCPReportBlock[] getReportBlocks()
    {
        return _reportBlocks;
    }

    public Long getSynchronizationSource()
    {
        return _synchronizationSource;
    }

    protected void serialize()
    {
        if(getReportBlocks() != null)
            super.setFirstByte(new Byte((new Integer(ArrayExtensions.getLength(getReportBlocks()).intValue())).byteValue()));
        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getSynchronizationSource()));
        if(getReportBlocks() != null)
        {
            RTCPReportBlock arr$[] = getReportBlocks();
            int len$ = arr$.length;
            for(int i$ = 0; i$ < len$; i$++)
            {
                RTCPReportBlock block = arr$[i$];
                ArrayListExtensions.addRange(list, block.getBytes());
            }

        }
        super.setPayload((Byte[])list.toArray(new Byte[0]));
    }

    public void setReportBlocks(RTCPReportBlock value[])
    {
        _reportBlocks = value;
    }

    private void setSynchronizationSource(Long value)
    {
        _synchronizationSource = value;
    }
}
