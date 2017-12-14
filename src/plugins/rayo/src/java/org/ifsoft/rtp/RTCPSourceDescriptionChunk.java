package org.ifsoft.rtp;

import java.util.ArrayList;

public class RTCPSourceDescriptionChunk
{

    public RTCPSourceDescriptionChunk()
    {
    }

    public Byte[] getBytes()
    {
        return getBytes(this);
    }

    public static Byte[] getBytes(RTCPSourceDescriptionChunk chunk)
    {
        if(chunk == null)
        {
            return new Byte[0];
        } else
        {
            ArrayList list = new ArrayList();
            return (Byte[])list.toArray(new Byte[0]);
        }
    }

    public static RTCPSourceDescriptionChunk parseBytes(Byte chunkBytes[])
    {
        return null;
    }
}
