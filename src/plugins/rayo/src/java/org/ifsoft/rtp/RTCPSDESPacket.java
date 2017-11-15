package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;

public class RTCPSDESPacket extends RTCPPacket
{
    private RTCPSourceDescriptionChunk _sourceDescriptionChunks[];

    protected void deserialize()
    {
        Integer num = Integer.valueOf(0);
        Byte firstByte = super.getFirstByte();
        if(num.intValue() < ArrayExtensions.getLength(super.getPayload()).intValue())
        {
            ArrayList list = new ArrayList();
            for(Integer i = Integer.valueOf(0); i.intValue() < firstByte.byteValue();)
            {
                Integer integer = i;
                Integer integer1 = i = Integer.valueOf(i.intValue() + 1);
                Integer _tmp = integer;
            }

            setSourceDescriptionChunks((RTCPSourceDescriptionChunk[])list.toArray(new RTCPSourceDescriptionChunk[0]));
        }
    }

    public RTCPSDESPacket()
    {
    }

    public RTCPSourceDescriptionChunk[] getSourceDescriptionChunks()
    {
        return _sourceDescriptionChunks;
    }

    protected void serialize()
    {
        if(getSourceDescriptionChunks() != null)
            super.setFirstByte(new Byte((new Integer(ArrayExtensions.getLength(getSourceDescriptionChunks()).intValue())).byteValue()));
        ArrayList list = new ArrayList();
        if(getSourceDescriptionChunks() != null)
        {
            RTCPSourceDescriptionChunk arr$[] = getSourceDescriptionChunks();
            int len$ = arr$.length;
            for(int i$ = 0; i$ < len$; i$++)
            {
                RTCPSourceDescriptionChunk chunk = arr$[i$];
                ArrayListExtensions.addRange(list, chunk.getBytes());
            }

        }
        super.setPayload((Byte[])list.toArray(new Byte[0]));
    }

    public void setSourceDescriptionChunks(RTCPSourceDescriptionChunk value[])
    {
        _sourceDescriptionChunks = value;
    }
}
