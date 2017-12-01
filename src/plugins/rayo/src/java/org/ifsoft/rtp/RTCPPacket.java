package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public abstract class RTCPPacket
{
    private static Short _aPPPayloadType = Short.valueOf((short)0);
    private static Short _bYEPayloadType = Short.valueOf((short)0);
    private Byte _firstByte;
    private Boolean _padding;
    private Byte _payload[];
    private Short _payloadType;
    private static Short _pSPayloadType = Short.valueOf((short)0);
    private static Short _rRPayloadType = Short.valueOf((short)0);
    private static Short _rTPPayloadType = Short.valueOf((short)0);
    private static Short _sDESPayloadType = Short.valueOf((short)0);
    private static Short _sRPayloadType = Short.valueOf((short)0);

    static
    {
        _sRPayloadType = new Short((short)200);
        _rRPayloadType = new Short((short)201);
        _sDESPayloadType = new Short((short)202);
        _bYEPayloadType = new Short((short)203);
        _aPPPayloadType = new Short((short)204);
        _rTPPayloadType = new Short((short)205);
        _pSPayloadType = new Short((short)206);
    }

    protected abstract void deserialize()
        throws Exception;

    protected RTCPPacket()
    {
        _firstByte = Byte.valueOf((byte)0);
        _padding = Boolean.valueOf(false);
        _payloadType = Short.valueOf((short)0);
    }

    public Byte[] getBytes()
    {
        return getBytes(this);
    }

    public static Byte[] getBytes(RTCPPacket packet)
    {
        ArrayList list = new ArrayList();
        try
        {
            packet.serialize();
        }
        catch(Exception exception1)
        {
            return new Byte[0];
        }
        if(packet instanceof RTCPSRPacket)
            packet.setPayloadType(_sRPayloadType);
        else
        if(packet instanceof RTCPRRPacket)
            packet.setPayloadType(_rRPayloadType);
        else
        if(packet instanceof RTCPSDESPacket)
            packet.setPayloadType(_sDESPayloadType);
        else
        if(packet instanceof RTCPBYEPacket)
            packet.setPayloadType(_bYEPayloadType);
        else
        if(packet instanceof RTCPAPPPacket)
            packet.setPayloadType(_aPPPayloadType);
        else
        if(packet instanceof RTCPRTPPacket)
            packet.setPayloadType(_rTPPayloadType);
        else
        if(packet instanceof RTCPPSPacket)
            packet.setPayloadType(_pSPayloadType);
        list.add(new Byte((new Byte((new Integer(0x80 | (packet.getPadding().booleanValue() ? 0x20 : 0) | packet.getFirstByte().byteValue() & 0x1f)).byteValue())).byteValue()));
        list.add(new Byte(BitAssistant.getShortBytesNetwork(packet.getPayloadType())[1].byteValue()));
        ArrayListExtensions.addRange(list, BitAssistant.getShortBytesNetwork(new Short((new Integer(ArrayExtensions.getLength(packet.getPayload()).intValue() / 4)).shortValue())));
        ArrayListExtensions.addRange(list, packet.getPayload());
        return (Byte[])list.toArray(new Byte[0]);
    }

    public static Byte[] getBytes(RTCPPacket packets[])
    {
        ArrayList list = new ArrayList();
        RTCPPacket arr$[] = packets;
        int len$ = arr$.length;
        for(int i$ = 0; i$ < len$; i$++)
        {
            RTCPPacket packet = arr$[i$];
            ArrayListExtensions.addRange(list, packet.getBytes());
        }

        return (Byte[])list.toArray(new Byte[0]);
    }

    protected Byte getFirstByte()
    {
        return _firstByte;
    }

    public Boolean getPadding()
    {
        return _padding;
    }

    protected Byte[] getPayload()
    {
        return _payload;
    }

    private Short getPayloadType()
    {
        return _payloadType;
    }

    public static RTCPPacket[] parseBytes(Byte bytes[])
    {
        ArrayList list = new ArrayList();
        do
        {
            RTCPPacket item = parseNext(bytes);
            if(item == null)
                return (RTCPPacket[])list.toArray(new RTCPPacket[0]);
            list.add(item);
            Integer offset = Integer.valueOf(4 + ArrayExtensions.getLength(item.getPayload()).intValue());
            Integer _var0 = offset;
            if(_var0 != null ? _var0.equals(ArrayExtensions.getLength(bytes)) : _var0 == ArrayExtensions.getLength(bytes))
                return (RTCPPacket[])list.toArray(new RTCPPacket[0]);
            bytes = BitAssistant.subArray(bytes, offset);
        } while(true);
    }

    private static RTCPPacket parseNext(Byte bytes[])
    {
        if(ArrayExtensions.getLength(bytes).intValue() >= 4 && (bytes[0].byteValue() & 0xc0) == 128)
        {
            Boolean flag = Boolean.valueOf((bytes[0].byteValue() & 0x20) == 32);
            Byte firstByte = new Byte((byte)(bytes[0].byteValue() & 0x1f));
            Short num2 = BitAssistant.toShortNetwork(new Byte[] {
                Byte.valueOf((byte)0), bytes[1]
            }, Integer.valueOf(0));
            Short num3 = BitAssistant.toShortNetwork(bytes, Integer.valueOf(2));
            Byte buffer2[] = BitAssistant.subArray(bytes, Integer.valueOf(4), Integer.valueOf(num3.shortValue() * 4));
            RTCPPacket packet = null;
            Short _var0 = num2;
            if(_var0 != null ? _var0.equals(_sRPayloadType) : _var0 == _sRPayloadType)
                packet = new RTCPSRPacket();
            Short _var1 = num2;
            if(_var1 != null ? _var1.equals(_rRPayloadType) : _var1 == _rRPayloadType)
                packet = new RTCPRRPacket();
            Short _var2 = num2;
            if(_var2 != null ? _var2.equals(_sDESPayloadType) : _var2 == _sDESPayloadType)
                packet = new RTCPSDESPacket();
            Short _var3 = num2;
            if(_var3 != null ? _var3.equals(_bYEPayloadType) : _var3 == _bYEPayloadType)
                packet = new RTCPBYEPacket();
            Short _var4 = num2;
            if(_var4 != null ? _var4.equals(_aPPPayloadType) : _var4 == _aPPPayloadType)
                packet = new RTCPAPPPacket();
            Short _var5 = num2;
            if(_var5 != null ? _var5.equals(_rTPPayloadType) : _var5 == _rTPPayloadType)
                packet = RTCPRTPPacket.createPacket(firstByte);
            Short _var6 = num2;
            if(_var6 != null ? _var6.equals(_pSPayloadType) : _var6 == _pSPayloadType)
                packet = RTCPPSPacket.createPacket(firstByte);
            if(packet != null)
            {
                packet.setFirstByte(firstByte);
                packet.setPayload(buffer2);
                packet.setPayloadType(num2);
                packet.setPadding(flag);
                try
                {
                    packet.deserialize();
                }
                catch(Exception exception1)
                {
                    return null;
                }
                return packet;
            }
        }
        return null;
    }

    protected abstract void serialize();

    protected void setFirstByte(Byte value)
    {
        _firstByte = value;
    }

    public void setPadding(Boolean value)
    {
        _padding = value;
    }

    protected void setPayload(Byte value[])
    {
        _payload = value;
    }

    private void setPayloadType(Short value)
    {
        _payloadType = value;
    }
}
