package com.jcumulus.server.rtmfp.application;

/**
 * jCumulus is a Java port of Cumulus OpenRTMP
 *
 * Copyright 2011 OpenRTMFP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License received along this program for more
 * details (or else see http://www.gnu.org/licenses/).
 *
 *
 * This file is a part of jCumulus.
 */

import com.jcumulus.server.rtmfp.packet.*;

import com.jcumulus.server.rtmfp.ISession;
import com.jcumulus.server.rtmfp.stream.B;
import org.apache.log4j.Logger;


public class StreamWriter extends com.jcumulus.server.rtmfp.publisher.FlowWriter
{
    private static final Logger W = Logger.getLogger(StreamWriter.class);
    private byte V;
    private G T;
    private boolean U;


    public StreamWriter(byte byte0, byte abyte0[], ISession d)
    {
        super(abyte0, d);
        T = new G();
        V = byte0;
    }

    void A(int i, Packet a, boolean flag)
    {
        if(flag)
        {
            if(a.H() >= 5)
            {
                a.E(a.H() - 5);
                B b = new B(a.G(), 5);
                b.B(V);
                b.A(i);
                A(a.G(), a.I(), a.G(), 5);
                return;
            }
            W.warn("Written unbuffered impossible, it requires 5 head bytes available on PacketReader given");
        }
        com.jcumulus.server.rtmfp.stream.BinaryWriter a1 = A(true);
        a1.B(V);
        a1.A(i);
        a1.B(a.G());
    }

    public G M()
    {
        return T;
    }

    public boolean L()
    {
        return U;
    }

    public void C(boolean flag)
    {
        U = flag;
    }


}
