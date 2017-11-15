package org.ifsoft.rtp;

import java.util.*;

public class RTCPAFBPacket extends RTCPPSPacket
{
    private Byte _aFBPayload[];
    private static Byte _feedbackMessageTypeByte = Byte.valueOf((byte)0);

    static
    {
        _feedbackMessageTypeByte = new Byte((byte)15);
    }
    protected void deserializeFCI()
    {
        setAFBPayload(super.getFCIPayload());
    }

    public RTCPAFBPacket()
    {
        super(_feedbackMessageTypeByte);
    }

    public Byte[] getAFBPayload()
    {
        return _aFBPayload;
    }

    protected void serializeFCI()
    {
        super.setFCIPayload(getAFBPayload());
    }

    public void setAFBPayload(Byte value[])
    {
        _aFBPayload = value;
    }
}
