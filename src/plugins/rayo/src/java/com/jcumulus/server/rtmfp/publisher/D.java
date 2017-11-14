package com.jcumulus.server.rtmfp.publisher;

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
import org.apache.log4j.Logger;


public class D
{
    private static final Logger D = Logger.getLogger(E.class);
    private byte B[];
    private AudioPacket C;
    private int A;


    public D(AudioPacket a)
    {
        B = new byte[0];
        B = a.G();
        A = 1;
    }

    public void A(AudioPacket a)
    {
        byte abyte0[] = new byte[B.length + a.I()];
        System.arraycopy(B, 0, abyte0, 0, B.length);
        System.arraycopy(a.G(), 0, abyte0, B.length, a.I());
        B = abyte0;
        A++;
    }

    public AudioPacket A()
    {
        if(C != null)
        {
            D.error("Packet already released");
            return C;
        } else
        {
            C = new AudioPacket(B, B.length);
            C.G(A);
            return C;
        }
    }

    public int B()
    {
        return A;
    }

}
