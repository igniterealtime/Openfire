package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.ArrayList;
import java.util.Date;

public class RTCPSRPacket extends RTCPPacket
{
    private Long _octetCount;
    private Long _packetCount;
    private RTCPReportBlock _reportBlocks[];
    private Long _rTPTimestamp;
    private Long _synchronizationSource;
    private Date _timestamp;

    protected void deserialize()
    {
        Integer startIndex = Integer.valueOf(0);
        setSynchronizationSource(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        setTimestamp(NetworkTimeProtocol.nTPToDateTime(BitAssistant.toLongNetwork(super.getPayload(), startIndex)));
        startIndex = Integer.valueOf(startIndex.intValue() + 8);
        setRTPTimestamp(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        setPacketCount(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        setOctetCount(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
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

    public RTCPSRPacket(Long synchronizationSource, Date timestamp, Long rtpTimestamp, Long packetCount, Long octetCount)
    {
        _octetCount = Long.valueOf(0L);
        _packetCount = Long.valueOf(0L);
        _rTPTimestamp = Long.valueOf(0L);
        _synchronizationSource = Long.valueOf(0L);
        _timestamp = new Date();
        setSynchronizationSource(synchronizationSource);
        setTimestamp(timestamp);
        setRTPTimestamp(rtpTimestamp);
        setPacketCount(packetCount);
        setOctetCount(octetCount);
    }

    RTCPSRPacket()
    {
        _octetCount = Long.valueOf(0L);
        _packetCount = Long.valueOf(0L);
        _rTPTimestamp = Long.valueOf(0L);
        _synchronizationSource = Long.valueOf(0L);
        _timestamp = new Date();
    }

    public Long getOctetCount()
    {
        return _octetCount;
    }

    public Long getPacketCount()
    {
        return _packetCount;
    }

    public RTCPReportBlock[] getReportBlocks()
    {
        return _reportBlocks;
    }

    public Long getRTPTimestamp()
    {
        return _rTPTimestamp;
    }

    public Long getSynchronizationSource()
    {
        return _synchronizationSource;
    }

    public Date getTimestamp()
    {
        return _timestamp;
    }

    protected void serialize()
    {
        if(getReportBlocks() != null)
            super.setFirstByte(new Byte((new Integer(ArrayExtensions.getLength(getReportBlocks()).intValue())).byteValue()));
        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getSynchronizationSource()));
        ArrayListExtensions.addRange(list, BitAssistant.getLongBytesNetwork(NetworkTimeProtocol.dateTimeToNTP(getTimestamp())));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getRTPTimestamp()));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getPacketCount()));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getOctetCount()));
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

    public void setOctetCount(Long value)
    {
        _octetCount = value;
    }

    public void setPacketCount(Long value)
    {
        _packetCount = value;
    }

    public void setReportBlocks(RTCPReportBlock value[])
    {
        _reportBlocks = value;
    }

    private void setRTPTimestamp(Long value)
    {
        _rTPTimestamp = value;
    }

    private void setSynchronizationSource(Long value)
    {
        _synchronizationSource = value;
    }

    private void setTimestamp(Date value)
    {
        _timestamp = value;
    }
}
