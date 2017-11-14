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

import com.jcumulus.server.rtmfp.ISession;
import com.jcumulus.server.rtmfp.Peer;
import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.flow.H;
import com.jcumulus.server.rtmfp.stream.BinaryWriter;
import com.jcumulus.server.rtmfp.stream.B;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;



public class E
{
    private static final Logger F = Logger.getLogger(E.class);
    public static final Integer N = Integer.valueOf(128);
    public static final Integer C = Integer.valueOf(16);
    public static final Integer M = Integer.valueOf(32);
    public static final Integer H = Integer.valueOf(2);
    public static final Integer E = Integer.valueOf(1);
    public static com.jcumulus.server.rtmfp.application.C I = new com.jcumulus.server.rtmfp.application.C();
    protected int A;
    private int J;
    protected Peer K;
    private boolean G;
    private ISession D;
    private com.jcumulus.server.rtmfp.publisher.D B;
    protected com.jcumulus.server.rtmfp.publisher.FlowWriter O;
    private Map P;
    private String L;



    public E(int i, byte abyte0[], String s, Peer p, ISession d)
    {
        P = new HashMap();
        A = i;
        J = 0;
        K = p;
        G = false;
        B = null;
        D = d;
        O = new com.jcumulus.server.rtmfp.publisher.FlowWriter(abyte0, d);
        O.C(i);
        O.C(s);
    }

    public void C()
    {
        E();
        O.C();
    }

    void E()
    {
        if(G)
            return;
        if(O.G() != null)
            F.debug((new StringBuilder()).append("Flow ").append(A).append(" consumed").toString());
        G = true;
    }

    protected void A(String s)
    {
        F.error((new StringBuilder()).append("Flow ").append(A).append(" failed : %s").append(s).toString());
        if(!G)
        {
            B b = D.A((byte)94, com.jcumulus.server.rtmfp.pipe.C.A(A) + 1, null);
            b.D(A);
            b.B((byte)0);
        }
    }

    private byte B(AudioPacket a)
    {
        if(a.I() == 0)
            return 0;
        byte byte0 = a.L();
        switch(byte0)
        {
        case 17: // '\021'
            a.D(1);
            // fall through

        case 20: // '\024'
            a.D(4);
            return 20;

        case 15: // '\017'
            a.D(5);
            break;

        case 4: // '\004'
            a.D(4);
            break;

        case 2: // '\002'
        case 3: // '\003'
        case 5: // '\005'
        case 6: // '\006'
        case 7: // '\007'
        case 10: // '\n'
        case 11: // '\013'
        case 12: // '\f'
        case 13: // '\r'
        case 14: // '\016'
        case 16: // '\020'
        case 18: // '\022'
        case 19: // '\023'
        default:
            F.error((new StringBuilder()).append("Unpacking type '").append(byte0).append("' unknown").toString());
            break;

        case 1: // '\001'
        case 8: // '\b'
        case 9: // '\t'
            break;
        }
        return byte0;
    }

    public void F()
    {
        int i = 0;
        ArrayList arraylist = new ArrayList();
        int j = J;
        boolean flag = false;
        Integer integer;
        for(Iterator iterator = P.keySet().iterator(); iterator.hasNext();)
            integer = (Integer)iterator.next();

        int k = B == null ? 127 : B.B() <= 16128 ? 16128 - B.B() : 0;
        if(O.G() == null)
            k = 0;
        B b = D.A((byte)81, com.jcumulus.server.rtmfp.pipe.C.A(A) + com.jcumulus.server.rtmfp.pipe.C.A(k) + com.jcumulus.server.rtmfp.pipe.C.A(J) + i, null);
        b.D(A);
        b.D(k);
        b.D(J);
        Integer integer1;
        for(Iterator iterator1 = arraylist.iterator(); iterator1.hasNext(); b.D(integer1.intValue()))
            integer1 = (Integer)iterator1.next();

        A();
        O.A();
    }

    public void A(int i, int j, AudioPacket a, byte byte0)
    {
        if(G)
            return;
        int k = J + 1;
        if(i < k)
        {
            F.debug((new StringBuilder()).append("Stage ").append(i).append(" on flow ").append(A).append(" has already been received").toString());
            return;
        }
        if(j > i)
        {
            F.warn((new StringBuilder()).append("DeltaNAck ").append(j).append(" superior to stage ").append(i).append(" on flow ").append(A).toString());
            j = i;
        }
        if(J < i - j)
        {
            Iterator iterator = P.keySet().iterator();
            do
            {
                if(!iterator.hasNext())
                    break;
                Integer integer = (Integer)iterator.next();
                if(integer.intValue() > i)
                    break;
                AudioPacket a1 = ((com.jcumulus.server.rtmfp.publisher.B)P.get(integer)).B();
                A(integer.intValue(), a1, ((com.jcumulus.server.rtmfp.publisher.B)P.get(integer)).A());
                if(G)
                    return;
                iterator.remove();
            } while(true);
            k = i;
        }
        if(i > k)
        {
            com.jcumulus.server.rtmfp.publisher.B b = (com.jcumulus.server.rtmfp.publisher.B)P.get(Integer.valueOf(i));
            if(b == null)
            {
                P.put(Integer.valueOf(i), new com.jcumulus.server.rtmfp.publisher.B(a, byte0));
                if(P.size() > 100)
                    F.debug((new StringBuilder()).append("fragmentMap.size()=").append(P.size()).toString());
            } else
            {
                F.debug((new StringBuilder()).append("Stage ").append(i).append(" on flow ").append(A).append(" has already been received").toString());
            }
        } else
        {
            A(k++, a, byte0);
            Iterator iterator1 = P.keySet().iterator();
            do
            {
                if(!iterator1.hasNext())
                    break;
                Integer integer1 = (Integer)iterator1.next();
                if(integer1.intValue() > k)
                    break;
                AudioPacket a2 = ((com.jcumulus.server.rtmfp.publisher.B)P.get(integer1)).B();
                A(k++, a2, ((com.jcumulus.server.rtmfp.publisher.B)P.get(integer1)).A());
                if(G)
                    break;
                iterator1.remove();
            } while(true);
        }
    }

    void A(int i, AudioPacket a, byte byte0)
    {
        if(i <= J)
        {
            F.error((new StringBuilder()).append("Stage ").append(i).append(" not sorted on flow").append(A).toString());
            return;
        }
        if(i > J + 1)
        {
            int j = i - J - 1;
            J = i;
            if(B != null)
                B = null;
            if((byte0 != 0) & (M != null))
            {
                A(j + 1);
                return;
            }
            A(j);
        } else
        {
            J = i;
        }
        if((byte0 & H.intValue()) != 0)
        {
            if(B != null)
                B = null;
            return;
        }
        if((byte0 & M.intValue()) != 0)
        {
            if(B == null)
            {
                F.warn("A received message tells to have a 'beforepart' and nevertheless partbuffer is empty, certainly some packets were lost");
                A(1);
                B = null;
                return;
            }
            B.A(a);
            if((byte0 & C.intValue()) != 0)
                return;
            a = B.A();
        } else
        if((byte0 & C.intValue()) != 0)
        {
            if(B != null)
            {
                F.error("A received message tells to have not 'beforepart' and nevertheless partbuffer exists");
                A(B.B());
            }
            B = new com.jcumulus.server.rtmfp.publisher.D(a);
            return;
        }
        byte byte1 = B(a);
        if(byte1 != 0)
        {
            O.A(Double.valueOf(0.0D));
            String s = null;
            com.jcumulus.server.rtmfp.flow.B b = new com.jcumulus.server.rtmfp.flow.B(a);
            if(byte1 == 20 || byte1 == 15)
            {
                s = b.E();
                if(byte1 == 20)
                {
                    O.A(b.N());
                    if(b.G() == com.jcumulus.server.rtmfp.flow.H.Null)
                        b.P();
                }
            }
            try
            {
                switch(byte1)
                {
                case 15: // '\017'
                case 20: // '\024'
                    A(s, b);
                    break;

                case 8: // '\b'
                    C(a);
                    break;

                case 9: // '\t'
                    A(a);
                    break;

                default:
                    A(byte1, a);
                    break;
                }
            }
            catch(Exception exception)
            {
                F.error(exception.getMessage(), exception);
                L = (new StringBuilder()).append("flow error, ").append(exception).toString();
            }
        }
        O.A(Double.valueOf(0.0D));
        if(B != null)
            B = null;
        if((byte0 & E.intValue()) != 0)
            E();
    }

    protected void A(String s, com.jcumulus.server.rtmfp.flow.B b)
    {
        F.error((new StringBuilder()).append("Message '").append(s).append("' unknown for flow ").append(A).toString());
    }

    protected void A(byte byte0, Packet a)
    {
        F.error((new StringBuilder()).append("Raw message unknown for flow ").append(A).toString());
    }

    protected void C(AudioPacket a)
    {
        F.error((new StringBuilder()).append("Audio packet untreated for flow ").append(A).toString());
    }

    protected void A(AudioPacket a)
    {
        F.error((new StringBuilder()).append("Video packet untreated for flow ").append(A).toString());
    }

    protected void A(int i)
    {
        F.info((new StringBuilder()).append(i).append(" fragments lost on flow").append(A).toString());
    }

    protected void A()
    {
    }

    public boolean B()
    {
        return G;
    }

    public int D()
    {
        return A;
    }

    public String G()
    {
        return L;
    }

}
