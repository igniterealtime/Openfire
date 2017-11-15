package org.ifsoft.rtp;

public abstract class RTCPRTPPacket extends RTCPFeedbackPacket
{
    public static RTCPRTPPacket createPacket(Byte firstByte)
    {
        RTCPRTPPacket packet = null;
        if(firstByte.byteValue() == 1)
            packet = new RTCPGenericNACKPacket();
        return packet;
    }

    public RTCPRTPPacket(Byte feedbackMessageType)
    {
        super(feedbackMessageType);
    }
}
