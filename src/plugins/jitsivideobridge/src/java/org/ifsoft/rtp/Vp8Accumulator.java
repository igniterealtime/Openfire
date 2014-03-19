package org.ifsoft.rtp;

import org.ifsoft.*;
import java.util.ArrayList;


public class Vp8Accumulator
{

    public void add(Vp8Packet packet)
    {
        //if(packet.getStartOfPartition().booleanValue() || ArrayListExtensions.getCount(packets).intValue() > 0)
            packets.add(packet);
    }

    public Vp8Accumulator()
    {
        packets = new ArrayList();
    }

    public Vp8Packet[] getPackets()
    {
        return (Vp8Packet[])packets.toArray(new Vp8Packet[0]);
    }

    public void reset()
    {
        packets = new ArrayList();
    }

    private ArrayList packets;
}
