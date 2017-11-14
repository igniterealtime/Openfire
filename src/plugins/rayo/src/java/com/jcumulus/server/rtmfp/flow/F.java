package com.jcumulus.server.rtmfp.flow;

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

import com.jcumulus.server.rtmfp.pipe.B;
import com.jcumulus.server.rtmfp.stream.BinaryWriter;
import com.google.common.base.Strings;
import java.util.*;
import org.apache.log4j.Logger;


public class F
{

    public F(BinaryWriter a)
    {
        C = new HashMap();
        A = new ArrayList();
        G = new LinkedList();
        F = a;
    }

    public void A(String s)
    {
        H = Integer.valueOf(0);
        if(Strings.isNullOrEmpty(s))
        {
            F.B(((byte)(E ? 1 : 6)));
            return;
        }
        if(!E)
        {
            if(B)
            {
                if(s.length() > 65535)
                {
                    F.B((byte)12);
                    F.A(s.length());
                    F.A(s);
                } else
                {
                    F.B((byte)2);
                    F.D(s.getBytes());
                }
                return;
            }
            F.B((byte)17);
        }
        F.B((byte)6);
        E(s);
        H = Integer.valueOf(A.size());
    }

    private void B(String s)
    {
        H = Integer.valueOf(0);
        if(!E)
        {
            F.D(s.getBytes());
            return;
        } else
        {
            E(s);
            return;
        }
    }

    private void E(String s)
    {
        if(!Strings.isNullOrEmpty(s))
        {
            Integer integer = (Integer)C.get(s);
            if(integer != null)
            {
                F.D(integer.intValue() << 1);
                return;
            }
            C.put(s, Integer.valueOf(C.size()));
        }
        F.D(s.length() << 1 | 1);
        F.A(s);
    }

    public void A()
    {
        H = Integer.valueOf(0);
        F.B(((byte)(E ? 1 : 5)));
    }

    public void A(boolean flag)
    {
        H = Integer.valueOf(0);
        if(!E)
        {
            F.B((byte)1);
            F.B((byte)(flag ? 1 : 0));
        } else
        {
            F.B(((byte)(flag ? 3 : 2)));
        }
    }

    public void A(B b)
    {
        H = Integer.valueOf(0);
        if(!E)
        {
            if(B)
            {
                F.B((byte)11);
                F.A((double)b.getTime() / 1000D);
                F.A((short)0);
                return;
            }
            F.B((byte)17);
        }
        F.B((byte)8);
        F.B((byte)1);
        F.A((double)b.getTime() / 1000D);
        A.add(Integer.valueOf(8));
        H = Integer.valueOf(A.size());
    }

    public void A(double d)
    {
        H = Integer.valueOf(0);
        F.B(((byte)(E ? 5 : 0)));
        F.A(d);
    }

    public void A(int i)
    {
        H = Integer.valueOf(0);
        if(!E)
        {
            if(B)
            {
                A(i);
                return;
            }
            F.B((byte)17);
        }
        F.B((byte)4);
        if(i > 0xfffffff)
        {
            D.error("AMF Integer maximum value reached");
            i = 0xfffffff;
        } else
        if(i < 0)
            i += 0x20000000;
        F.D(i);
    }

    private BinaryWriter B(int i)
    {
        H = Integer.valueOf(0);
        if(!E)
            F.B((byte)17);
        F.B((byte)12);
        F.D(i << 1 | 1);
        A.add(Integer.valueOf(12));
        H = Integer.valueOf(A.size());
        return F;
    }

    public void A(byte abyte0[], int i)
    {
        BinaryWriter a = B(i);
        a.A(abyte0, i);
    }

    void A(String s, Integer integer)
    {
        B(s);
        if(integer != null)
            A(integer.intValue());
        else
            A();
    }

    void A(String s, Double double1)
    {
        B(s);
        if(double1 != null)
            A(double1.doubleValue());
        else
            A();
    }

    void A(String s, String s1)
    {
        B(s);
        if(s1 != null)
            A(s1);
        else
            A();
    }

    void A(String s, B b)
    {
        B(s);
        if(b != null)
            A(b);
        else
            A();
    }

    void A(String s, Boolean boolean1)
    {
        B(s);
        if(boolean1 != null)
            A(boolean1.booleanValue());
        else
            A();
    }

    void A(String s, byte abyte0[])
    {
        B(s);
        if(abyte0 != null)
            A(abyte0, abyte0.length);
        else
            A();
    }

    void A(String s, E e)
    {
        B(s);
        if(e != null)
            A(e);
        else
            A();
    }

    void C(String s)
    {
        B(s);
        A();
    }

    void C()
    {
        A("", false);
    }

    void D(String s)
    {
        A(s, false);
    }

    void A(String s, boolean flag)
    {
        H = Integer.valueOf(0);
        if(!E)
        {
            if(B && !flag)
            {
                G.push(Integer.valueOf(0));
                if(Strings.isNullOrEmpty(s))
                {
                    F.B((byte)3);
                } else
                {
                    F.B((byte)16);
                    A(s);
                }
                return;
            }
            F.B((byte)17);
            E = true;
        }
        F.B((byte)10);
        A.add(Integer.valueOf(10));
        H = Integer.valueOf(A.size());
        G.push(H);
        int i = 1;
        i |= 2;
        if(flag)
            i |= 4;
        else
            i |= 8;
        F.D(i);
        B(s);
    }

    void B()
    {
        if(G.size() == 0)
        {
            D.error("AMFWriter.endObject called without beginObject calling");
            return;
        }
        H = (Integer)G.pollLast();
        if(!E)
        {
            F.A((short)0);
            F.B((byte)9);
            return;
        }
        F.B((byte)1);
        if(G.size() == 0 || ((Integer)G.getLast()).intValue() == 0)
            E = false;
    }

    public void A(Object obj)
    {
        if(obj == null)
            A();
        else
        if(obj instanceof E)
            A((E)obj);
        else
        if(obj instanceof Integer)
            A(((Integer)obj).intValue());
        else
        if(obj instanceof String)
            A((String)obj);
        else
        if(obj instanceof Number)
            A(((Number)obj).doubleValue());
        else
        if(obj instanceof B)
            A((B)obj);
        else
            A(obj.toString());
    }

    public void A(E e)
    {
        C();
        Map map = e.A();
        for(Iterator iterator = map.keySet().iterator(); iterator.hasNext();)
        {
            String s = (String)iterator.next();
            H h = (H)map.get(s);
            if(com.jcumulus.server.rtmfp.flow.H.Boolean == h)
                A(s, e.A(s));
            else
            if(com.jcumulus.server.rtmfp.flow.H.String == h)
                A(s, e.H(s));
            else
            if(com.jcumulus.server.rtmfp.flow.H.Number == h)
                A(s, e.D(s));
            else
            if(com.jcumulus.server.rtmfp.flow.H.Integer == h)
                A(s, e.B(s));
            else
            if(com.jcumulus.server.rtmfp.flow.H.Date == h)
                A(s, e.C(s));
            else
            if(com.jcumulus.server.rtmfp.flow.H.Null == h)
                C(s);
            else
            if(com.jcumulus.server.rtmfp.flow.H.Object == h)
                A(s, e.E(s));
            else
                D.error((new StringBuilder()).append("Unknown AMFObject '").append(h).append("' type").toString());
        }

        B();
    }

    public boolean D()
    {
        return B;
    }

    public void B(boolean flag)
    {
        B = flag;
    }

    public BinaryWriter E()
    {
        return F;
    }

    private static final Logger D = Logger.getLogger(F.class);
    BinaryWriter F;
    Integer H;
    boolean B;
    Map C;
    List A;
    LinkedList G;
    boolean E;

}
