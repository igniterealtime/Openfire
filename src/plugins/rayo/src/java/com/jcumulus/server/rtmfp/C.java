package com.jcumulus.server.rtmfp;

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

import com.jcumulus.server.rtmfp.pipe.A;
import com.jcumulus.server.rtmfp.pipe.D;
import com.jcumulus.server.rtmfp.stream.B;
import java.io.IOException;
import org.apache.log4j.Logger;


public class C
{
    private static final Logger E = Logger.getLogger(C.class);
    public static final int A = 64;
    boolean F;
    byte M[];
    byte J[];
    String K;
    int B;
    byte C;
    byte H[];
    com.jcumulus.server.rtmfp.pipe.B G;
    D I;
    byte D[];
    B L;


    public C()
    {
        J = new byte[64];
        B = 0;
        C = 120;
        D = new byte[256];
        L = new B(D);
        F = true;
    }

    public C(byte abyte0[], String s)
    {
        J = new byte[64];
        B = 0;
        C = 120;
        D = new byte[256];
        L = new B(D);
        M = abyte0;
        F = false;
        K = s;
        H = new byte[N.F + 11];
        try
        {
            com.jcumulus.server.rtmfp.pipe.C.A().read(J);
            System.arraycopy(new byte[] {
                3, 26, 0, 0, 2, 30, 0, -127, 2, 13,
                2
            }, 0, H, 0, 11);
            I = com.jcumulus.server.rtmfp.N.B();
            System.arraycopy(I.A(), 0, H, 11, N.F);
        }
        catch(IOException ioexception)
        {
            E.error(ioexception.getMessage(), ioexception);
        }
    }

    public C(byte abyte0[])
    {
        J = new byte[64];
        B = 0;
        C = 120;
        D = new byte[256];
        L = new B(D);
        M = abyte0;
        F = false;
        H = new byte[73];
        try
        {
            com.jcumulus.server.rtmfp.pipe.C.A().read(J);
            System.arraycopy(new byte[] {
                3, 26, 0, 0, 2, 30, 0, 65, 14
            }, 0, H, 0, 9);
            com.jcumulus.server.rtmfp.pipe.C.A().read(H, 9, 64);
        }
        catch(IOException ioexception)
        {
            E.error(ioexception.getMessage(), ioexception);
        }
    }

    public D A(byte abyte0[], byte abyte1[], int i)
    {
        byte abyte2[] = com.jcumulus.server.rtmfp.N.A(I.B(), abyte0);
        return new D(com.jcumulus.server.rtmfp.N.A(abyte2, abyte1, i, H), com.jcumulus.server.rtmfp.N.B(abyte2, abyte1, i, H));
    }

    public void A()
    {
        if(L.A() == 0)
        {
            L.A(B);
            if(!F)
            {
                L.D(H.length);
                L.A(H, H.length);
                L.B((byte)88);
            }
        }
    }

    public byte A(B b)
    {
        b.B(L.B());
        return (byte)L.A();
    }

}
