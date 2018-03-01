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

import com.jcumulus.server.rtmfp.packet.*;


import com.jcumulus.server.rtmfp.pipe.A;
import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.pipe.D;
import com.jcumulus.server.rtmfp.stream.B;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;


public class Handshake extends ServerSession
{
    private static final Logger W = Logger.getLogger(Handshake.class);

    Map X;
    byte V[];
    private Sessions Y;


    Handshake(Sessions l)
    {
        super(0, 0, com.jcumulus.server.rtmfp.N.A, com.jcumulus.server.rtmfp.N.A);
        X = new HashMap();
        V = new byte[77];
        Y = l;
        I = true;
        System.arraycopy(new byte[] {
            1, 10, 65, 14
        }, 0, V, 0, 4);
        try
        {
            com.jcumulus.server.rtmfp.pipe.C.A().read(V, 4, 64);
        }
        catch(IOException ioexception)
        {
            W.error(ioexception.getMessage(), ioexception);
        }
        System.arraycopy(new byte[] {
            2, 21, 2, 2, 21, 5, 2, 21, 14
        }, 0, V, 68, 9);
    }

    void A(B b, I i, byte abyte0[], String s)
    {
        com.jcumulus.server.rtmfp.C c = i.C;
        if(c == null)
        {
            c = new com.jcumulus.server.rtmfp.C(abyte0, s);
            X.put(new String(c.J), c);
            i.C = c;
        }
        b.B((byte)64);
        b.A(c.J, 64);
    }

    protected void B(AudioPacket a)
    {
        byte byte0 = a.L();
        if(byte0 != 11)
        {
            W.error((new StringBuilder()).append("Marker handshake wrong : should be 0b and not ").append(byte0).toString());
            return;
        }
        a.E();
        byte byte1 = a.L();
        a.A(a.E());
        int i = N.C();
        N.E(3);
        byte byte2 = A(byte1, a, N);
        N.F(i);
        if(byte2 > 0)
        {
            N.B(byte2);
            N.A((short)(N.A() - N.C() - 2));
            E();
        }
        D = 0;
    }

    private byte A(byte byte0, AudioPacket a, B b)
    {
        switch(byte0)
        {
        case 48: // '0'
            a.L();
            int i = (a.L() & 0xff) - 1;
            byte byte1 = a.L();
            String s = new String(a.F(i));
            byte abyte0[] = a.F(16);
            b.A(abyte0);
            if(byte1 != 15)
            {
                if(byte1 == 10)
                {
                    I l = A(abyte0);
                    A(b, l, abyte0, s);
                    b.A(V, V.length);
                    return 112;
                }
                W.error("Unkown handshake first way with 'type' type");
            }
            break;

        case 56: // '8'
        case 57: // '9'
            D = a.C();
            if(a.J() != 64)
            {
                W.error("Bad handshake cookie its size should be 64 bytes");
                return 0;
            }
            com.jcumulus.server.rtmfp.C c = (com.jcumulus.server.rtmfp.C)X.get(new String(a.B(64)));
            if(c == null)
            {
                if(byte0 != 57)
                {
                    W.error("Handshake cookie unknown");
                    return 0;
                }
                c = new com.jcumulus.server.rtmfp.C();
                int j = a.H();
                c.J = a.F(64);
                c.K = new String(a.F(64));
                a.E(j);
                X.put(new String(c.J), c);
            }

            Peer p = new Peer();
            p.A(M.L());

            if(c.B == 0)
            {
                D d = null;
                if(byte0 == 56)
                {
                    a.D(64);
                    int k = a.J();
                    try
                    {
                        MessageDigest messagedigest = MessageDigest.getInstance("SHA-256");
                        messagedigest.update(a.B(k));
                        p.B = messagedigest.digest();
                    }
                    catch(NoSuchAlgorithmException nosuchalgorithmexception)
                    {
                        W.error(nosuchalgorithmexception.getMessage(), nosuchalgorithmexception);
                    }
                    byte abyte1[] = new byte[a.J() - 2];
                    a.D(2);
                    abyte1 = a.F(abyte1.length);
                    k = a.J();
                    d = c.A(abyte1, a.G(), k);
                }
                com.jcumulus.server.rtmfp.pipe.C.A(c.K, p);
                ServerSession h = Y.A(D, d.B(), d.A(), p);
                c.B = h.O;
                String s1 = null;
                if(byte0 != 57)
                    s1 = p.L().toString();
                p.N().clear();
                p.N().add(s1);
                c.A();
            }
            c.A(b);
            return c.C;

        case 65: // 'A'
        case 69: // 'E'
        default:
            W.error((new StringBuilder()).append("Unkown handshake packet id ").append(byte0).toString());
            break;
        }
        return 0;
    }

    public void E()
    {
        A((byte)11, false);
    }
}
