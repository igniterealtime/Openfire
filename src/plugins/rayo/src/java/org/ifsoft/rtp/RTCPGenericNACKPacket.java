package org.ifsoft.rtp;

import org.ifsoft.*;

import java.util.ArrayList;
import java.util.Iterator;

public class RTCPGenericNACKPacket extends RTCPRTPPacket
{
    private ArrayList __genericNACKs;
    private static Byte _feedbackMessageTypeByte = Byte.valueOf((byte)0);

    static
    {
        _feedbackMessageTypeByte = new Byte((byte)1);
    }

    public void addGenericNACK(FBGenericNACK genericNACK)
    {
        __genericNACKs.add(genericNACK);
    }

    protected void deserializeFCI()
    {
        ArrayList list = new ArrayList();
        Integer num = Integer.valueOf(ArrayExtensions.getLength(super.getFCIPayload()).intValue() / 4);
        for(Integer i = Integer.valueOf(0); i.intValue() < num.intValue();)
        {
            FBGenericNACK.parseBytes(BitAssistant.subArray(super.getFCIPayload(), Integer.valueOf(i.intValue() * 4), Integer.valueOf(4)));
            Integer integer = i;
            Integer integer1 = i = Integer.valueOf(i.intValue() + 1);
            Integer _tmp = integer;
        }

        __genericNACKs = list;
    }

    public RTCPGenericNACKPacket()
    {
        super(_feedbackMessageTypeByte);
        __genericNACKs = new ArrayList();
    }

    public FBGenericNACK[] getGenericNACKs()
    {
        return (FBGenericNACK[])__genericNACKs.toArray(new FBGenericNACK[0]);
    }

    public Boolean removeGenericNACK(FBGenericNACK genericNACK)
    {
        return Boolean.valueOf(__genericNACKs.remove(genericNACK));
    }

    protected void serializeFCI()
    {
        ArrayList list = new ArrayList();
        FBGenericNACK cnack;
        for(Iterator i$ = __genericNACKs.iterator(); i$.hasNext(); ArrayListExtensions.addRange(list, cnack.getBytes()))
            cnack = (FBGenericNACK)i$.next();

        super.setFCIPayload((Byte[])list.toArray(new Byte[0]));
    }
}
