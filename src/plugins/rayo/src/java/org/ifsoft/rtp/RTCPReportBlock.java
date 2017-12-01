package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public class RTCPReportBlock
{
    private Integer _cumulativeNumberOfPacketsLost;
    private Long _delaySinceLastSR;
    private Long _extendedHighestSequenceNumberReceived;
    private Short _fractionLost;
    private Long _interarrivalJitter;
    private Long _lastSR;
    private Long _synchronizationSource;

    public RTCPReportBlock(Long synchronizationSource, Short fractionLost, Integer cumulativeNumberOfPacketsLost, Long extendedHighestSequenceNumberReceived, Long interarrivalJitter, Long lastSR, Long delaySinceLastSR)
    {
        _cumulativeNumberOfPacketsLost = Integer.valueOf(0);
        _delaySinceLastSR = Long.valueOf(0L);
        _extendedHighestSequenceNumberReceived = Long.valueOf(0L);
        _fractionLost = Short.valueOf((short)0);
        _interarrivalJitter = Long.valueOf(0L);
        _lastSR = Long.valueOf(0L);
        _synchronizationSource = Long.valueOf(0L);
        setSynchronizationSource(synchronizationSource);
        setFractionLost(fractionLost);
        setCumulativeNumberOfPacketsLost(cumulativeNumberOfPacketsLost);
        setExtendedHighestSequenceNumberReceived(extendedHighestSequenceNumberReceived);
        setInterarrivalJitter(interarrivalJitter);
        setLastSR(lastSR);
        setDelaySinceLastSR(delaySinceLastSR);
    }

    public Byte[] getBytes()
    {
        return getBytes(this);
    }

    public static Byte[] getBytes(RTCPReportBlock block)
    {
        if(block == null)
        {
            return new Byte[0];
        } else
        {
            ArrayList list = new ArrayList();
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(block.getSynchronizationSource()));
            list.add(new Byte((new Byte((new Short(block.getFractionLost().shortValue())).byteValue())).byteValue()));
            ArrayListExtensions.addRange(list, BitAssistant.subArray(BitAssistant.getIntegerBytesNetwork(block.getCumulativeNumberOfPacketsLost()), Integer.valueOf(1)));
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(block.getExtendedHighestSequenceNumberReceived()));
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(block.getInterarrivalJitter()));
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(block.getLastSR()));
            ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(block.getDelaySinceLastSR()));
            return (Byte[])list.toArray(new Byte[0]);
        }
    }

    public Integer getCumulativeNumberOfPacketsLost()
    {
        return _cumulativeNumberOfPacketsLost;
    }

    public Long getDelaySinceLastSR()
    {
        return _delaySinceLastSR;
    }

    public Long getExtendedHighestSequenceNumberReceived()
    {
        return _extendedHighestSequenceNumberReceived;
    }

    public Short getFractionLost()
    {
        return _fractionLost;
    }

    public Long getInterarrivalJitter()
    {
        return _interarrivalJitter;
    }

    public Long getLastSR()
    {
        return _lastSR;
    }

    public Long getSynchronizationSource()
    {
        return _synchronizationSource;
    }

    public static RTCPReportBlock parseBytes(Byte blockBytes[])
    {
        if(ArrayExtensions.getLength(blockBytes).intValue() < 24)
        {
            return null;
        } else
        {
            Integer index = Integer.valueOf(0);
            Long synchronizationSource = BitAssistant.toLongFromIntegerNetwork(blockBytes, Integer.valueOf(0));
            index = Integer.valueOf(index.intValue() + 4);
            Short fractionLost = new Short(blockBytes[index.intValue()].byteValue());
            Byte buffer[] = BitAssistant.subArray(blockBytes, index, Integer.valueOf(4));
            buffer[0] = Byte.valueOf((byte)0);
            Integer cumulativeNumberOfPacketsLost = BitAssistant.toIntegerNetwork(buffer, Integer.valueOf(0));
            index = Integer.valueOf(index.intValue() + 4);
            Long extendedHighestSequenceNumberReceived = BitAssistant.toLongFromIntegerNetwork(blockBytes, index);
            index = Integer.valueOf(index.intValue() + 4);
            Long interarrivalJitter = BitAssistant.toLongFromIntegerNetwork(blockBytes, index);
            index = Integer.valueOf(index.intValue() + 4);
            Long lastSR = BitAssistant.toLongFromIntegerNetwork(blockBytes, index);
            index = Integer.valueOf(index.intValue() + 4);
            Long delaySinceLastSR = BitAssistant.toLongFromIntegerNetwork(blockBytes, index);
            index = Integer.valueOf(index.intValue() + 4);
            return new RTCPReportBlock(synchronizationSource, fractionLost, cumulativeNumberOfPacketsLost, extendedHighestSequenceNumberReceived, interarrivalJitter, lastSR, delaySinceLastSR);
        }
    }

    private void setCumulativeNumberOfPacketsLost(Integer value)
    {
        _cumulativeNumberOfPacketsLost = value;
    }

    private void setDelaySinceLastSR(Long value)
    {
        _delaySinceLastSR = value;
    }

    private void setExtendedHighestSequenceNumberReceived(Long value)
    {
        _extendedHighestSequenceNumberReceived = value;
    }

    private void setFractionLost(Short value)
    {
        _fractionLost = value;
    }

    private void setInterarrivalJitter(Long value)
    {
        _interarrivalJitter = value;
    }

    private void setLastSR(Long value)
    {
        _lastSR = value;
    }

    private void setSynchronizationSource(Long value)
    {
        _synchronizationSource = value;
    }
}
