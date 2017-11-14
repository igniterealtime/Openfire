package com.jcumulus.server.rtmfp.packet;

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

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.log4j.Logger;

public class Packet
{
    private static final Logger B = Logger.getLogger(Packet.class);
    public byte D[];
    private int A;
    private int C;

    public Packet(byte abyte0[], int i)
    {
        D = abyte0;
        C = i;
        A = 0;
    }

    public void A(byte abyte0[], int i)
    {
        D = abyte0;
        C = i;
        A = 0;
    }

    public int J()
    {
        byte byte0 = 0;
        byte byte1 = L();
        int i = 0;
        for(; (byte1 & 0x80) > 0 && byte0 < 3; byte0++)
        {
            i <<= 7;
            i |= byte1 & 0x7f;
            byte1 = L();
        }

        i <<= byte0 >= 3 ? 8 : 7;
        i |= byte1;
        return i;
    }

    public byte L()
    {
        byte byte0 = 0;
        for(int i = 0; i < 1; i++)
        {
            byte0 <<= 8;
            byte0 |= F();
        }

        return byte0;
    }

    public short E()
    {
        short word0 = 0;
        for(int i = 0; i < 2; i++)
        {
            word0 <<= 8;
            word0 |= F();
        }

        return word0;
    }

    public int C()
    {
        int i = 0;
        for(int j = 0; j < 4; j++)
        {
            i <<= 8;
            i |= F();
        }

        return i;
    }

    public double B()
    {
        return ByteBuffer.wrap(F(8)).getDouble();
    }

    public byte[] A()
    {
        return F(E() & 0xffff);
    }

    public byte[] F(int i)
    {
        if (A > D.length || (A + i) > D.length)
        {
            return new byte[0];

        } else {
            byte abyte0[] = Arrays.copyOfRange(D, A, A + i);
            A += i;
            return abyte0;
        }
    }

    private int F()
    {
        byte byte0 = D[A];
        A++;
        return byte0 & 0xff;
    }

    public byte K()
    {
        return D[A];
    }

    public int H()
    {
        return A;
    }

    public void E(int i)
    {
        A = i;
    }

    public void C(int i)
    {
        A -= i;
    }

    public void D(int i)
    {
        A += i;
    }

    public int D()
    {
        return C;
    }

    public int I()
    {
        return C - A;
    }

    public byte[] G()
    {
        return Arrays.copyOfRange(D, A, C);
    }

    public byte[] B(int i)
    {
        return Arrays.copyOfRange(D, A, A + i);
    }

    public void A(int i)
    {
        if(i > I())
        {
            B.warn((new StringBuilder()).append("rest ").append(i).append(" more upper than available ").append(I()).append(" bytes").toString());
            i = I();
        }
        D = Arrays.copyOfRange(D, 0, A + i);
        C = A + i;
    }


}
