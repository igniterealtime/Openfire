package org.ifsoft.rtp;

public class RTCPPLIPacket extends RTCPPSPacket
{
    private static Byte _feedbackMessageTypeByte = Byte.valueOf((byte)0);

    static
    {
        _feedbackMessageTypeByte = new Byte((byte)1);
    }
    protected void deserializeFCI()
    {
    }

    public RTCPPLIPacket()
    {
        super(_feedbackMessageTypeByte);
    }

    protected void serializeFCI()
    {
        super.setFCIPayload(new Byte[0]);
    }
}
