package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public class FBGenericNACK
{
    private Boolean _lostPacketIdPlus1;
    private Boolean _lostPacketIdPlus10;
    private Boolean _lostPacketIdPlus11;
    private Boolean _lostPacketIdPlus12;
    private Boolean _lostPacketIdPlus13;
    private Boolean _lostPacketIdPlus14;
    private Boolean _lostPacketIdPlus15;
    private Boolean _lostPacketIdPlus16;
    private Boolean _lostPacketIdPlus2;
    private Boolean _lostPacketIdPlus3;
    private Boolean _lostPacketIdPlus4;
    private Boolean _lostPacketIdPlus5;
    private Boolean _lostPacketIdPlus6;
    private Boolean _lostPacketIdPlus7;
    private Boolean _lostPacketIdPlus8;
    private Boolean _lostPacketIdPlus9;
    private Integer _packetId;


    public FBGenericNACK()
    {
        _lostPacketIdPlus1 = Boolean.valueOf(false);
        _lostPacketIdPlus10 = Boolean.valueOf(false);
        _lostPacketIdPlus11 = Boolean.valueOf(false);
        _lostPacketIdPlus12 = Boolean.valueOf(false);
        _lostPacketIdPlus13 = Boolean.valueOf(false);
        _lostPacketIdPlus14 = Boolean.valueOf(false);
        _lostPacketIdPlus15 = Boolean.valueOf(false);
        _lostPacketIdPlus16 = Boolean.valueOf(false);
        _lostPacketIdPlus2 = Boolean.valueOf(false);
        _lostPacketIdPlus3 = Boolean.valueOf(false);
        _lostPacketIdPlus4 = Boolean.valueOf(false);
        _lostPacketIdPlus5 = Boolean.valueOf(false);
        _lostPacketIdPlus6 = Boolean.valueOf(false);
        _lostPacketIdPlus7 = Boolean.valueOf(false);
        _lostPacketIdPlus8 = Boolean.valueOf(false);
        _lostPacketIdPlus9 = Boolean.valueOf(false);
        _packetId = Integer.valueOf(0);
    }

    public Byte[] getBytes()
    {
        ArrayList list = new ArrayList();
        ArrayListExtensions.addRange(list, BitAssistant.getShortBytesFromIntegerNetwork(getPacketId()));
        Byte item = new Byte((new Integer((getLostPacketIdPlus16().booleanValue() ? 0x80 : 0) | (getLostPacketIdPlus15().booleanValue() ? 0x40 : 0) | (getLostPacketIdPlus14().booleanValue() ? 0x20 : 0) | (getLostPacketIdPlus13().booleanValue() ? 0x10 : 0) | (getLostPacketIdPlus12().booleanValue() ? 8 : 0) | (getLostPacketIdPlus11().booleanValue() ? 4 : 0) | (getLostPacketIdPlus10().booleanValue() ? 2 : 0) | (getLostPacketIdPlus9().booleanValue() ? 1 : 0))).byteValue());
        Byte num2 = new Byte((new Integer((getLostPacketIdPlus8().booleanValue() ? 0x80 : 0) | (getLostPacketIdPlus7().booleanValue() ? 0x40 : 0) | (getLostPacketIdPlus6().booleanValue() ? 0x20 : 0) | (getLostPacketIdPlus5().booleanValue() ? 0x10 : 0) | (getLostPacketIdPlus4().booleanValue() ? 8 : 0) | (getLostPacketIdPlus3().booleanValue() ? 4 : 0) | (getLostPacketIdPlus2().booleanValue() ? 2 : 0) | (getLostPacketIdPlus1().booleanValue() ? 1 : 0))).byteValue());
        list.add(new Byte(item.byteValue()));
        list.add(new Byte(num2.byteValue()));
        return (Byte[])list.toArray(new Byte[0]);
    }

    public Boolean getLostPacketIdPlus1()
    {
        return _lostPacketIdPlus1;
    }

    public Boolean getLostPacketIdPlus10()
    {
        return _lostPacketIdPlus10;
    }

    public Boolean getLostPacketIdPlus11()
    {
        return _lostPacketIdPlus11;
    }

    public Boolean getLostPacketIdPlus12()
    {
        return _lostPacketIdPlus12;
    }

    public Boolean getLostPacketIdPlus13()
    {
        return _lostPacketIdPlus13;
    }

    public Boolean getLostPacketIdPlus14()
    {
        return _lostPacketIdPlus14;
    }

    public Boolean getLostPacketIdPlus15()
    {
        return _lostPacketIdPlus15;
    }

    public Boolean getLostPacketIdPlus16()
    {
        return _lostPacketIdPlus16;
    }

    public Boolean getLostPacketIdPlus2()
    {
        return _lostPacketIdPlus2;
    }

    public Boolean getLostPacketIdPlus3()
    {
        return _lostPacketIdPlus3;
    }

    public Boolean getLostPacketIdPlus4()
    {
        return _lostPacketIdPlus4;
    }

    public Boolean getLostPacketIdPlus5()
    {
        return _lostPacketIdPlus5;
    }

    public Boolean getLostPacketIdPlus6()
    {
        return _lostPacketIdPlus6;
    }

    public Boolean getLostPacketIdPlus7()
    {
        return _lostPacketIdPlus7;
    }

    public Boolean getLostPacketIdPlus8()
    {
        return _lostPacketIdPlus8;
    }

    public Boolean getLostPacketIdPlus9()
    {
        return _lostPacketIdPlus9;
    }

    public Integer getPacketId()
    {
        return _packetId;
    }

    public static FBGenericNACK parseBytes(Byte genericNACKBytes[])
    {
        FBGenericNACK cnack = new FBGenericNACK();
        cnack.setPacketId(BitAssistant.toIntegerFromShortNetwork(genericNACKBytes, Integer.valueOf(0)));
        Byte num = genericNACKBytes[2];
        Byte num2 = genericNACKBytes[3];
        cnack.setLostPacketIdPlus16(Boolean.valueOf((num.byteValue() & 0x80) == 128));
        cnack.setLostPacketIdPlus15(Boolean.valueOf((num.byteValue() & 0x40) == 64));
        cnack.setLostPacketIdPlus14(Boolean.valueOf((num.byteValue() & 0x20) == 32));
        cnack.setLostPacketIdPlus13(Boolean.valueOf((num.byteValue() & 0x10) == 16));
        cnack.setLostPacketIdPlus12(Boolean.valueOf((num.byteValue() & 8) == 8));
        cnack.setLostPacketIdPlus11(Boolean.valueOf((num.byteValue() & 4) == 4));
        cnack.setLostPacketIdPlus10(Boolean.valueOf((num.byteValue() & 2) == 2));
        cnack.setLostPacketIdPlus9(Boolean.valueOf((num.byteValue() & 1) == 1));
        cnack.setLostPacketIdPlus8(Boolean.valueOf((num2.byteValue() & 0x80) == 128));
        cnack.setLostPacketIdPlus7(Boolean.valueOf((num2.byteValue() & 0x40) == 64));
        cnack.setLostPacketIdPlus6(Boolean.valueOf((num2.byteValue() & 0x20) == 32));
        cnack.setLostPacketIdPlus5(Boolean.valueOf((num2.byteValue() & 0x10) == 16));
        cnack.setLostPacketIdPlus4(Boolean.valueOf((num2.byteValue() & 8) == 8));
        cnack.setLostPacketIdPlus3(Boolean.valueOf((num2.byteValue() & 4) == 4));
        cnack.setLostPacketIdPlus2(Boolean.valueOf((num2.byteValue() & 2) == 2));
        cnack.setLostPacketIdPlus1(Boolean.valueOf((num2.byteValue() & 1) == 1));
        return cnack;
    }

    public void setLostPacketIdPlus1(Boolean value)
    {
        _lostPacketIdPlus1 = value;
    }

    public void setLostPacketIdPlus10(Boolean value)
    {
        _lostPacketIdPlus10 = value;
    }

    public void setLostPacketIdPlus11(Boolean value)
    {
        _lostPacketIdPlus11 = value;
    }

    public void setLostPacketIdPlus12(Boolean value)
    {
        _lostPacketIdPlus12 = value;
    }

    public void setLostPacketIdPlus13(Boolean value)
    {
        _lostPacketIdPlus13 = value;
    }

    public void setLostPacketIdPlus14(Boolean value)
    {
        _lostPacketIdPlus14 = value;
    }

    public void setLostPacketIdPlus15(Boolean value)
    {
        _lostPacketIdPlus15 = value;
    }

    public void setLostPacketIdPlus16(Boolean value)
    {
        _lostPacketIdPlus16 = value;
    }

    public void setLostPacketIdPlus2(Boolean value)
    {
        _lostPacketIdPlus2 = value;
    }

    public void setLostPacketIdPlus3(Boolean value)
    {
        _lostPacketIdPlus3 = value;
    }

    public void setLostPacketIdPlus4(Boolean value)
    {
        _lostPacketIdPlus4 = value;
    }

    public void setLostPacketIdPlus5(Boolean value)
    {
        _lostPacketIdPlus5 = value;
    }

    public void setLostPacketIdPlus6(Boolean value)
    {
        _lostPacketIdPlus6 = value;
    }

    public void setLostPacketIdPlus7(Boolean value)
    {
        _lostPacketIdPlus7 = value;
    }

    public void setLostPacketIdPlus8(Boolean value)
    {
        _lostPacketIdPlus8 = value;
    }

    public void setLostPacketIdPlus9(Boolean value)
    {
        _lostPacketIdPlus9 = value;
    }

    public void setPacketId(Integer value)
    {
        _packetId = value;
    }
}
