package org.ifsoft.rtp;

import java.util.ArrayList;

public class Vp8Accumulator
{

    private ArrayList packets;

    public void add(Vp8Packet packet)
    {
        if(packet.getStartOfPartition().booleanValue() || packets.size() > 0)
            packets.add(packet);
    }

    public Vp8Packet[] getPackets()
    {
        return (Vp8Packet[])packets.toArray(new Vp8Packet[0]);
    }

    public void reset()
    {
        packets = new ArrayList();
    }

    public Vp8Accumulator()
    {
        packets = new ArrayList();
    }
}
