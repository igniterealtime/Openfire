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
import com.jcumulus.server.rtmfp.application.Publication;
import com.jcumulus.server.rtmfp.application.C;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import org.apache.log4j.Logger;


public class A extends com.jcumulus.server.rtmfp.publisher.E
{
    static enum _A
    {
        IDLE, PUBLISHING, PLAYING
    }

    private static final Logger _ = Logger.getLogger(E.class);
    public static final byte Z[] = {
        0, 84, 67, 4
    };
    public static final String X = "NetStream";
    int b;
    Publication c;
    _A W;
    boolean V;
    int Y;
    String a;


    public A(int i, byte abyte0[], Peer p, ISession d)
    {
        super(i, abyte0, "NetStream", p, d);
        Packet a1 = new AudioPacket(abyte0, abyte0.length);
        a1.D(4);
        b = a1.J();
        Iterator iterator = I.getPublications().values().iterator();
        do
        {
            if(!iterator.hasNext())
                break;
            Publication b1 = (Publication)iterator.next();
            if(b1.B() != b)
                continue;
            c = b1;
            break;
        } while(true);
    }

    public void C()
    {
        super.C();
        H();
    }

    void H()
    {
        if(W == _A.PUBLISHING)
        {
            I.getStreams().A(K, b, a);
            O.A("Unpublish.Success", (new StringBuilder()).append(a).append(" is now unpublished").toString());
        } else
        if(W == _A.PLAYING)
        {
            I.getStreams().B(K, b, a);
            O.A("Play.Stop", (new StringBuilder()).append("Stopped playing ").append(a).toString());
        }
        W = _A.IDLE;
    }

    protected void C(AudioPacket a1)
    {
        if(c != null && c.B() == b)
        {
            c.B(a1.C(), a1, Y);
            Y = 0;
        } else
        {
            _.warn("an audio packet has been received on a no publisher FlowStream, certainly a publication currently closing");
        }
    }

    protected void A(AudioPacket a1)
    {
        if(c != null && c.B() == b)
        {
            c.A(a1.C(), a1, Y);
            Y = 0;
        } else
        {
            _.warn("a video packet has been received on a no publisher FlowStream, certainly a publication currently closing");
        }
    }

    protected void A()
    {
        if(c != null && c.B() == b)
            c.D();
    }

    protected void A(byte byte0, Packet a1)
    {
        short word0 = a1.E();
        if(word0 == 34)
        {
            _.debug((new StringBuilder()).append("Bound ").append(A).append(" : ").append(a1.C()).append(" ").append(a1.C()).toString());
            return;
        } else
        {
            _.error((new StringBuilder()).append("Unknown raw flag ").append(word0).append(" on FlowStream ").append(A).toString());
            super.A(byte0, a1);
            return;
        }
    }

    protected void A(int i)
    {
        if(c != null)
            Y += i;
        super.A(i);
    }

    protected void A(String s, com.jcumulus.server.rtmfp.flow.B b1)
    {
        if("play".equals(s))
        {
            H();
            a = b1.E();
            double d = -2000D;
            if(b1.B())
                d = b1.N().doubleValue();
            if(I.getStreams().A(K, b, a, O, d))
                W = _A.PLAYING;
        } else
        if("closeStream".equals(s))
            H();
        else
        if("publish".equals(s))
        {
            H();
            a = b1.E();
            _.info((new StringBuilder()).append("Create new publication with name - ").append(a).toString());
            String s1;
            if(b1.B())
                s1 = b1.E();
            try
            {
                I.getStreams().A(K, b, a, O);
                W = _A.PUBLISHING;
            }
            catch(Exception exception)
            {
                _.error(exception.getMessage(), exception);
            }
        } else
        if(W == _A.PUBLISHING)
        {
            if(c == null)
            {
                c = (Publication)I.getPublications().get(a);
                if(c == null)
                    _.error((new StringBuilder()).append("Publication ").append(a).append(" unfound, related for the ").append(s).append(" message").toString());
            }
            if(c != null)
                c.A(s, b1.M());
        } else
        {
            super.A(s, b1);
        }
    }


}
