package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public abstract class RTCPFeedbackPacket extends RTCPPacket
{

    private Byte _fCIPayload[];
    private Byte _feedbackMessageType;
    private Long _mediaSourceSynchronizationSource;
    private Long _packetSenderSynchronizationSource;

    protected void deserialize()
    {
        setFeedbackMessageType(super.getFirstByte());
        Integer startIndex = Integer.valueOf(0);
        setPacketSenderSynchronizationSource(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        setMediaSourceSynchronizationSource(BitAssistant.toLongFromIntegerNetwork(super.getPayload(), startIndex));
        startIndex = Integer.valueOf(startIndex.intValue() + 4);
        setFCIPayload(BitAssistant.subArray(super.getPayload(), startIndex));
        deserializeFCI();
    }

    protected abstract void deserializeFCI();

    public RTCPFeedbackPacket(Byte feedbackMessageType)
    {
        _feedbackMessageType = Byte.valueOf((byte)0);
        _mediaSourceSynchronizationSource = Long.valueOf(0L);
        _packetSenderSynchronizationSource = Long.valueOf(0L);
        setFeedbackMessageType(feedbackMessageType);
    }

    public Byte[] getFCIPayload()
    {
        return _fCIPayload;
    }

    public Byte getFeedbackMessageType()
    {
        return _feedbackMessageType;
    }

    public Long getMediaSourceSynchronizationSource()
    {
        return _mediaSourceSynchronizationSource;
    }

    public Long getPacketSenderSynchronizationSource()
    {
        return _packetSenderSynchronizationSource;
    }

    protected void serialize()
    {
        super.setFirstByte(getFeedbackMessageType());
        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getPacketSenderSynchronizationSource()));
        ArrayListExtensions.addRange(list, BitAssistant.getIntegerBytesFromLongNetwork(getMediaSourceSynchronizationSource()));
        serializeFCI();
        ArrayListExtensions.addRange(list, getFCIPayload());
        super.setPayload((Byte[])list.toArray(new Byte[0]));
    }

    protected abstract void serializeFCI();

    public void setFCIPayload(Byte value[])
    {
        _fCIPayload = value;
    }

    public void setFeedbackMessageType(Byte value)
    {
        _feedbackMessageType = value;
    }

    public void setMediaSourceSynchronizationSource(Long value)
    {
        _mediaSourceSynchronizationSource = value;
    }

    public void setPacketSenderSynchronizationSource(Long value)
    {
        _packetSenderSynchronizationSource = value;
    }
}
