package com.jcumulus.server.rtmfp.stream;

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

import com.jcumulus.server.rtmfp.pipe.C;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.log4j.Logger;

public class BinaryWriter
{

    private static final Logger C = Logger.getLogger(BinaryWriter.class);

    protected byte E[];
    private int A;
    protected int D;
    protected int B;


    public BinaryWriter(byte abyte0[])
    {
        E = abyte0;
        A = 0;
        D = 0;
        B = abyte0.length;
    }

    private void A(byte byte0)
    {
        if(A < B)
        {
            E[A] = byte0;
            A++;
            if(A > D)
                D = A;
        } else
        {
            C.warn("Limit is exceeded");
            try
            {
                throw new Exception();
            }
            catch(Exception exception)
            {
                C.error("Trace - ", exception);
            }
        }
    }

    public void B(int i)
    {
        A = i;
        D = i;
    }

    public int D()
    {
        return B - A;
    }

    public void E(int i)
    {
        A += i;
        if(A > B)
        {
            A = B;
            D = B;
        } else
        if(A > D)
            D = A;
    }

    public void F(int i)
    {
        if(i <= D)
            A = i;
    }

    public int C()
    {
        return A;
    }

    public void C(int i)
    {
        if(i == 0)
            B = E.length;
        else
        if(i > E.length)
        {
            C.warn((new StringBuilder()).append("Limit '").append(i).append("' more upper than buffer size '").append(E.length).append("' bytes").toString());
            B = E.length;
        } else
        {
            B = i;
        }
    }

    public void G(int i)
    {
        D -= i;
        byte abyte0[] = Arrays.copyOfRange(E, i, E.length);
        E = new byte[E.length];
        System.arraycopy(abyte0, 0, E, 0, abyte0.length);
    }

    private void A(int i, int j)
    {
        byte abyte0[] = BigInteger.valueOf(i).toByteArray();
        for(int k = 0; k < j - abyte0.length; k++)
            A((byte)0);

        byte abyte1[] = abyte0;
        int l = abyte1.length;
        for(int i1 = 0; i1 < l; i1++)
        {
            byte byte0 = abyte1[i1];
            A(byte0);
        }

    }

    public void A(double d)
    {
        byte abyte0[] = new byte[8];
        ByteBuffer.wrap(abyte0).putDouble(d);
        B(abyte0);
    }

    public void B(byte byte0)
    {
        A(byte0);
    }

    public void A(byte abyte0[])
    {
        A((byte)abyte0.length);
        B(abyte0);
    }

    public void A(short word0)
    {
        A(word0, 2);
    }

    public void A(int i)
    {
        A(i, 4);
    }

    public void C(byte abyte0[])
    {
        B((byte)abyte0.length);
        byte abyte1[] = abyte0;
        int i = abyte1.length;
        for(int j = 0; j < i; j++)
        {
            byte byte0 = abyte1[j];
            A(byte0);
        }

    }

    public void D(byte abyte0[])
    {
        A((short)abyte0.length);
        byte abyte1[] = abyte0;
        int i = abyte1.length;
        for(int j = 0; j < i; j++)
        {
            byte byte0 = abyte1[j];
            A(byte0);
        }

    }

    public void A(byte byte0, int i)
    {
        for(int j = 0; j < i; j++)
            A(byte0);

    }

    public void A(byte abyte0[], int i)
    {
        for(int j = 0; j < i; j++)
            A(abyte0[j]);

    }

    public void A(String s)
    {
        byte abyte0[] = s.getBytes();
        int i = abyte0.length;
        for(int j = 0; j < i; j++)
        {
            Byte byte1 = Byte.valueOf(abyte0[j]);
            A(byte1.byteValue());
        }

    }

    public void B(byte abyte0[])
    {
        byte abyte1[] = abyte0;
        int i = abyte1.length;
        for(int j = 0; j < i; j++)
        {
            byte byte0 = abyte1[j];
            A(byte0);
        }

    }

    public void D(int i)
    {
        byte byte0 = com.jcumulus.server.rtmfp.pipe.C.A(i);
        switch(byte0)
        {
        case 4: // '\004'
            B((byte)(0x80 | i >> 22 & 0x7f));
            B((byte)(0x80 | i >> 15 & 0x7f));
            B((byte)(0x80 | i >> 8 & 0x7f));
            B((byte)(i & 0xff));
            break;

        case 3: // '\003'
            B((byte)(0x80 | i >> 14 & 0x7f));
            B((byte)(0x80 | i >> 7 & 0x7f));
            B((byte)(i & 0x7f));
            break;

        case 2: // '\002'
            B((byte)(0x80 | i >> 7 & 0x7f));
            B((byte)(i & 0x7f));
            break;

        default:
            B((byte)(i & 0x7f));
            break;
        }
    }

    public int A()
    {
        return D;
    }

    public byte[] B()
    {
        return Arrays.copyOfRange(E, 0, D);
    }

}
