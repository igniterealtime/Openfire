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
import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import com.jcumulus.server.rtmfp.flow.F;
import com.jcumulus.server.rtmfp.stream.BinaryWriter;
import org.apache.log4j.Logger;


public class Listener
{
    private static final Logger E = Logger.getLogger(Listener.class);
    private Publication K;
    private int B;
    private boolean A;
    private int D;
    private boolean F;
    private int G;
    private int H;
    private int L;
    private FlowWriter J;
    private AudioWriter C;
    private com.jcumulus.server.rtmfp.application.VideoWriter I;


    public Listener(int i, Publication b, FlowWriter h, boolean flag)
    {
        B = i;
        K = b;
        J = h;
        A = flag;
    }

    public Publication getPublication()
    {
        return K;
    }


    public void C()
    {
        if(C == null)
            C = J.B();
        else
            E.warn((new StringBuilder()).append("Listener ").append(B).append(" audio track has already been initialized").toString());
        if(I == null)
            I = J.K();
        else
            E.warn((new StringBuilder()).append("Listener ").append(B).append(" video track has already been initialized").toString());
        A();
    }

    private void A()
    {
        if(I != null)
            A(((FlowWriter) (I)));
        if(C != null)
            A(((FlowWriter) (C)));
        A(J);
        D++;
    }

    private int A(int i)
    {
        if(i == 0)
            i = 1;
        if(G == 0 && H == 0)
        {
            G = i;
            E.debug((new StringBuilder()).append("Deltatime assignment : ").append(G).toString());
        }
        if(G > i)
        {
            E.warn((new StringBuilder()).append("Time infererior to deltaTime on listener ").append(B).append(", certainly a non increasing time").toString());
            G = i;
        }
        L = (i - G) + H;
        return L;
    }

    private void A(FlowWriter h)
    {
        E.debug((new StringBuilder()).append("Writing bound ").append(D).append(" on flow writer ").append(h.E()).toString());
        BinaryWriter a = h.A(false);
        a.A((short)34);
        a.A(D);
        a.A(3);
    }

    public void A(String s)
    {
        J.A("Play.PublishNotify", (new StringBuilder()).append(s).append(" is now published").toString());
        F = false;
    }

    public void B(String s)
    {
        J.A("Play.UnpublishNotify", (new StringBuilder()).append(s).append(" is now unpublished").toString());
        G = 0;
        H = L;
        C.M().E();
        I.M().E();
    }

    public void B()
    {
        if(C != null)
            C.A();
        if(I != null)
            I.A();
        J.B(true);
    }

    public void A(String s, Packet a)
    {
        if(A)
        {
            int i = s.length() + 9;
            if(a.H() >= i)
            {
                a.E(a.H() - i);
                J.A(a.G(), a.I());
                return;
            }
            E.warn((new StringBuilder()).append("Written unbuffered impossible, it requires ").append(i).append(" head bytes available on PacketReader given").toString());
        }
        J.B(s).E().B(a.G());
    }

    public void B(int i, Packet a)
    {
        if(C == null)
        {
            E.error((new StringBuilder()).append("Listener ").append(B).append(" must be initialized before to be used").toString());
            return;
        }
        if(C.L())
        {
            C.C(false);
            A();
        }
        C.A(A(i), a, A);
    }

    public void A(int i, Packet a)
    {
        if(I == null)
        {
            E.error((new StringBuilder()).append("Listener ").append(B).append(" must be initialized before to be used").toString());
            return;
        }
        if((a.K() & 0xf0) == 16)
            F = true;
        if(!F)
        {
            E.debug((new StringBuilder()).append("Video frame dropped for listener ").append(B).append(" to wait first key frame").toString());
            I.M().G();
            return;
        }
        if(I.L())
        {
            I.C(false);
            A();
        }
        I.A(A(i), a, A);
    }

}
