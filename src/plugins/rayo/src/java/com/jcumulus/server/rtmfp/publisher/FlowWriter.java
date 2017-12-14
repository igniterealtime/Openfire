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

import com.jcumulus.server.rtmfp.ISession;
import com.jcumulus.server.rtmfp.packet.*;
import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.application.VideoWriter;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;



public class FlowWriter
{
    private static final Logger G = Logger.getLogger(FlowWriter.class);
    private static J S = new J();
    private int J;
    private boolean K;
    private int C;
    private byte I[];
    private ISession H;
    private boolean B;
    private L A;
    private final List P = new ArrayList();
    private int N;
    private final List O = new ArrayList();
    private int L;
    private int M;
    private int D;
    private int F;
    private Double E;
    private String R;
    private int Q;



    public FlowWriter(byte abyte0[], ISession d)
    {
        A = new L();
        K = false;
        J = 0;
        N = 0;
        L = 0;
        B = false;
        E = Double.valueOf(0.0D);
        Q = 0;
        C = 0;
        H = d;
        I = abyte0;
        F = 0;
        M = 0;
        D = 0;
        d.B(this);
    }

    public com.jcumulus.server.rtmfp.application.AudioWriter B()
    {
        return new com.jcumulus.server.rtmfp.application.AudioWriter(I, H);
    }

    public VideoWriter K()
    {
        return new VideoWriter(I, H);
    }

    public void F()
    {
        B = true;
        I();
    }

    public void D(String s)
    {
        G.warn((new StringBuilder()).append("FlowWriter ").append(J).append(" has failed : ").append(s).toString());
        N = L = M = D = 0;
        I();
        if(B)
        {
            return;
        } else
        {
            H.B(this);
            return;
        }
    }

    public void I()
    {
    }

    public void C()
    {
        if(B)
            return;
        if(N > 0)
            if(D() != 0);
        B = true;
        A();
    }

    protected com.jcumulus.server.rtmfp.flow.A C(String s, String s1)
    {
        return A("_result", s, s1);
    }

    public com.jcumulus.server.rtmfp.flow.A A(String s, String s1)
    {
        return A("onStatus", s, s1);
    }

    public com.jcumulus.server.rtmfp.flow.A B(String s, String s1)
    {
        return A("_error", s, s1);
    }

    public void A(AudioPacket a)
    {
        int i = a.J();
        if(i == 0)
        {
            G.error("Negative acknowledgment");
            D("Negative acknowledgment");
            return;
        }
        int j = L;
        int k = a.J();
        int l = L + 1;
        if(k > N)
        {
            G.error((new StringBuilder()).append("Acknowledgment received ").append(k).append(" superior than the current sending stage ").append(N).append(" on flowWriter ").append(J).toString());
            L = N;
        } else
        if(k <= L)
        {
            if(a.I() == 0)
                G.debug((new StringBuilder()).append("Acknowledgment ").append(k).append(" obsolete on flowWriter ").append(J).toString());
        } else
        {
            L = k;
        }
        int i1 = L;
        int j1 = a.H();
        while(a.I() > 0)
            i1 += a.J() + a.J() + 2;
        if(j1 != a.H())
            a.E(j1);
        int k1 = 0;
        int l1 = 0;
        boolean flag = false;
        boolean flag1 = true;
        boolean flag2 = false;
        synchronized(O)
        {
            ListIterator listiterator = O.listIterator();
            do
            {
                if(flag2 || !listiterator.hasNext())
                    break;
                I i2 = (I)listiterator.next();
                if(i2.B().size() == 0)
                {
                    G.error((new StringBuilder()).append("Message ").append(l + 1).append(" is bad formatted on flowWriter ").append(J).toString());
                } else
                {
                    Integer ainteger[] = (Integer[])i2.B().keySet().toArray(new Integer[0]);
                    Integer integer = ainteger[0];
                    int j2 = 0;
                    do
                    {
                        if(j2 >= ainteger.length)
                            break;
                        if(L >= l)
                        {
                            i2.B().remove(ainteger[0]);
                            ainteger = (Integer[])i2.B().keySet().toArray(new Integer[0]);
                            j2 = 0;
                            if(j2 < ainteger.length)
                                integer = ainteger[j2];
                            D++;
                            l++;
                            continue;
                        }
                        do
                        {
                            if(flag2 || k1 != 0)
                                break;
                            if(k1 == 0)
                                if(a.I() > 0)
                                {
                                    k1 = a.J() + 1;
                                    l1 = k + 1;
                                    k = l1 + k1 + a.J();
                                } else
                                {
                                    flag2 = true;
                                    break;
                                }
                            if(l1 > N)
                            {
                                G.error((new StringBuilder()).append("Lost information received ").append(l1).append(" have not been yet sent on flowWriter ").append(J).toString());
                                flag2 = true;
                                break;
                            }
                            if(l1 > L)
                                break;
                            k1--;
                            l1++;
                        } while(true);
                        if(flag2)
                            break;
                        if(l1 != l)
                        {
                            if(flag)
                            {
                                l++;
                                if(j2 < ainteger.length - 1)
                                {
                                    j2++;
                                    integer = ainteger[j2];
                                }
                                flag1 = true;
                            } else
                            {
                                L = l;
                            }
                        } else
                        if(!i2.A())
                        {
                            if(flag)
                            {
                                j2++;
                                integer = ainteger[j2];
                                l++;
                                flag1 = true;
                            } else
                            {
                                G.debug((new StringBuilder()).append("FlowWriter ").append(J).append(" : message ").append(l).append(" lost").toString());
                                D--;
                                M++;
                                L = l;
                            }
                            k1--;
                            l1++;
                        } else
                        {
                            flag = true;
                            if(((Integer)i2.B().get(integer)).intValue() >= i1 || k == l1 + 2)
                            {
                                l++;
                                flag1 = true;
                                k1--;
                                l1++;
                                if(j2 < ainteger.length - 1)
                                {
                                    j2++;
                                    integer = ainteger[j2];
                                }
                            } else
                            {
                                G.debug((new StringBuilder()).append("FlowWriter ").append(J).append(" : stage ").append(l).append(" repeated").toString());
                                int k2 = integer.intValue();
                                Packet b1 = i2.A(k2);
                                int i3 = b1.I();
                                i2.B().put(integer, Integer.valueOf(N));
                                int j3 = i3;
                                byte byte0 = 0;
                                if(k2 > 0)
                                    byte0 |= com.jcumulus.server.rtmfp.publisher.E.M.intValue();
                                if(j2 < ainteger.length - 1)
                                {
                                    j2++;
                                    integer = ainteger[j2];
                                    byte0 |= com.jcumulus.server.rtmfp.publisher.E.C.intValue();
                                    j3 = integer.intValue() - k2;
                                }
                                com.jcumulus.server.rtmfp.stream.B b2 = H.B();
                                int k3 = j3 + 4;
                                if(!flag1 && k3 > b2.D())
                                {
                                    H.A(false);
                                    flag1 = true;
                                }
                                if(flag1)
                                    k3 += D(l);
                                if(k3 > b2.D())
                                    H.A(false);
                                k3 -= 3;
                                A(H.A(((byte)(flag1 ? 16 : 17)), k3, null), l, byte0, flag1, b1, j3);
                                i3 -= j3;
                                flag1 = false;
                                k1--;
                                l1++;
                                l++;
                            }
                        }
                    } while(true);
                    if(i2.B().size() == 0)
                    {
                        if(i2.A())
                            F--;
                        if(D > 0)
                        {
                            Packet b = i2.D();
                            int l2 = b.I();
                            A(D, M, b, l2);
                            D = M = 0;
                        }
                        listiterator.remove();
                    }
                }
            } while(true);
        }
        if(k1 > 0 && a.I() > 0)
            G.error((new StringBuilder()).append("Some lost information received have not been yet sent on flowWriter ").append(J).toString());
        if(F == 0)
            A.B();
        else
        if(L > j || flag)
            A.A();
    }

    public void A(int i)
    {
        if(i >= D())
        {
            G.error((new StringBuilder()).append("Impossible to cancel ").append(i).append(" message on flowWriter ").append(J).toString());
            return;
        }
        synchronized(P)
        {
            for(int j = i; j < P.size(); j++)
                P.remove(i);

        }
    }

    public void A(byte abyte0[], int i)
    {
        A(abyte0, i, ((byte []) (null)), 0);
    }

    public void A(byte abyte0[], int i, byte abyte1[], int j)
    {
        if(B || I == null || H.A())
            return;
        G g = new G(abyte0, i, abyte1, j);
        synchronized(P)
        {
            P.add(g);
        }
        A();
    }

    private K H()
    {
        if(B || I == null || H.A())
            return S;
        K k = new K();
        synchronized(P)
        {
            P.add(k);
        }
        return k;
    }

    public com.jcumulus.server.rtmfp.stream.BinaryWriter A(boolean flag)
    {
        K k = H();
        if(!flag)
        {
            k.E().B((byte)4);
            k.E().A(0);
        }
        return k.E();
    }

    public com.jcumulus.server.rtmfp.flow.F B(String s)
    {
        K k = H();
        k.E().B((byte)15);
        k.E().B((byte)0);
        k.E().A(0);
        k.F().A(s);
        return k.F();
    }

    private void A(com.jcumulus.server.rtmfp.stream.BinaryWriter a, String s, double d)
    {
        a.B((byte)20);
        a.A(0);
        a.B((byte)2);
        a.D(s.getBytes());
        a.B((byte)0);
        a.A(d);
        a.B((byte)5);
    }

    public com.jcumulus.server.rtmfp.flow.F A(String s)
    {
        K k = H();
        A(k.E(), s, 0.0D);
        return k.F();
    }

    public com.jcumulus.server.rtmfp.flow.F J()
    {
        K k = H();
        A(k.E(), "_result", E.doubleValue());
        return k.F();
    }

    com.jcumulus.server.rtmfp.flow.A A(String s, String s1, String s2)
    {
        K k = H();
        A(k.E(), s, E.doubleValue());
        String s3 = R;
        if(!Strings.isNullOrEmpty(s1))
        {
            s3 = (new StringBuilder()).append(s3).append(".").toString();
            s3 = (new StringBuilder()).append(s3).append(s1).toString();
        }
        boolean flag = k.F().D();
        k.F().B(true);
        com.jcumulus.server.rtmfp.flow.A a = new com.jcumulus.server.rtmfp.flow.A(k.F());
        if(s.equals("_error"))
            a.A("level", "error");
        else
            a.A("level", "status");
        a.A("code", s3);
        if(!Strings.isNullOrEmpty(s2))
            a.A("description", s2);
        k.F().B(flag);
        return a;
    }

    public void A()
    {
        B(false);
    }

    public void B(boolean flag)
    {
        boolean flag1 = !H.A(this);
        synchronized(P)
        {
            for(Iterator iterator = P.iterator(); iterator.hasNext();)
            {
                I i = (I)iterator.next();
                if(i.A())
                {
                    F++;
                    A.C();
                }
                int j = 0;
                Packet b = i.D();
                int k = b.I();
                do
                {
                    com.jcumulus.server.rtmfp.stream.B b1 = H.B();
                    if(b1.D() < 12)
                    {
                        H.A(false);
                        flag1 = true;
                    }
                    boolean flag2 = flag1;
                    int l = k;
                    int i1 = l + 4;
                    N++;
                    if(flag2)
                        i1 += D(N);
                    byte byte0 = 0;
                    if(j > 0)
                        byte0 |= com.jcumulus.server.rtmfp.publisher.E.M.intValue();
                    if(i1 > b1.D())
                    {
                        byte0 |= com.jcumulus.server.rtmfp.publisher.E.C.intValue();
                        l = b1.D() - (i1 - l);
                        i1 = b1.D();
                        flag1 = true;
                    } else
                    {
                        flag1 = false;
                    }
                    i1 -= 3;
                    A(H.A(((byte)(flag2 ? 16 : 17)), (short)i1, this), N, byte0, flag2, b, l);
                    i.B().put(Integer.valueOf(j), Integer.valueOf(N));
                    k -= l;
                    j += l;
                } while(k > 0);
                synchronized(O)
                {
                    O.add(i);
                }
            }

            P.clear();
        }
        if(flag)
            H.A(true);
    }

    int D(int i)
    {
        int j = com.jcumulus.server.rtmfp.pipe.C.A(J);
        j += com.jcumulus.server.rtmfp.pipe.C.A(i);
        if(L > i)
            G.error((new StringBuilder()).append("stageAck ").append(L).append(" superior to stage ").append(i).append(" on flowWriter ").append(J).toString());
        j += com.jcumulus.server.rtmfp.pipe.C.A(i - L);
        j += L <= 0 ? I.length + (C != 0 ? 4 + com.jcumulus.server.rtmfp.pipe.C.A(C) : 2) : 0;
        return j;
    }

    public void A(com.jcumulus.server.rtmfp.stream.B b, int i, byte byte0, boolean flag, Packet b1, int j)
    {
        if(L == 0 && flag)
            byte0 |= com.jcumulus.server.rtmfp.publisher.E.N.intValue();
        if(j == 0)
            byte0 |= com.jcumulus.server.rtmfp.publisher.E.H.intValue();
        if(B)
            byte0 |= com.jcumulus.server.rtmfp.publisher.E.E.intValue();
        G.debug((new StringBuilder()).append("FlowWriter ").append(J).append(" stage ").append(i).toString());
        b.B(byte0);
        if(flag)
        {
            b.D(J);
            b.D(i);
            b.D((byte0 & com.jcumulus.server.rtmfp.publisher.E.H.intValue()) == 0 ? i - L : 0);
            if(L == 0)
            {
                b.C(I);
                if(C > 0)
                {
                    b.B((byte)(1 + com.jcumulus.server.rtmfp.pipe.C.A(C)));
                    b.B((byte)10);
                    b.D(C);
                }
                b.B((byte)0);
            }
        }
        if(j > 0)
            b.B(b1.F(j));
    }

    protected void A(int i, int j, Packet b, int k)
    {
    }

    public int D()
    {
        synchronized (this.P)
        {
          return this.P.size();
        }
    }

    public int E()
    {
        return J;
    }

    public void B(int i)
    {
        J = i;
    }

    public void C(int i)
    {
        C = i;
    }

    public void C(String s)
    {
        R = s;
    }

    public void A(Double double1)
    {
        E = double1;
    }

    public byte[] G()
    {
        return I;
    }

}
