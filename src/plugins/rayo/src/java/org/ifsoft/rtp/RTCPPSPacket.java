package org.ifsoft.rtp;

import org.ifsoft.*;

public abstract class RTCPPSPacket extends RTCPFeedbackPacket
{

    public static RTCPPSPacket createPacket(Byte firstByte)
    {
        Byte _var0 = firstByte;
        if(_var0.byteValue() == 1)
            return new RTCPPLIPacket();
        if(_var0.byteValue() == 15)
            return new RTCPAFBPacket();
        else
            return null;
    }

    public RTCPPSPacket(Byte feedbackMessageType)
    {
        super(feedbackMessageType);
    }
}
