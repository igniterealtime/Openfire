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

import com.jcumulus.server.rtmfp.Peer;
import com.jcumulus.server.rtmfp.packet.*;
import com.jcumulus.server.rtmfp.publisher.E;
import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import com.google.common.base.Strings;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.apache.log4j.Logger;

// Referenced classes of package com.jcumulus.server.rtmfp.application:
//            G, I

public class Publication
{
    private static final Logger F = Logger.getLogger(Publication.class);
    private Peer E;
    private FlowWriter A;
    private boolean G;
    private String J;
    private int I;
    private Map H;
    private G D;
    private G C;
    private _A B;


    private class _A extends Thread
    {

        public void A()
        {
            D = true;
            interrupt();
        }

        public void run()
        {
            long l = System.nanoTime();
            do
            {
                if(D)
                    break;
                l += G * 0xf4240L;
                A.D();
                long l1 = l - System.nanoTime() - E * 0xf4240L;
                if(l1 > 0L)
                    LockSupport.parkNanos(l1);
            } while(true);
        }

        protected static final long C = 0L;
        protected static final long B = 0xf4240L;
        protected long E;
        protected Object F;
        protected long G;
        protected volatile boolean D;
        final Publication A;

        private _A()
        {
            super();
            A = Publication.this;

            F = new Object();
            G = 20L;
            D = false;
            setPriority(10);

            E = 0L; // timing_shift
        }

    }


    public Publication(String s)
    {
        G = false;
        H = new HashMap();
        D = new G();
        C = new G();
        B = new _A();
        B.start();
        F.info((new StringBuilder()).append("New publication ").append(s).toString());
        J = s;
    }

    public boolean A(Peer p, int i, FlowWriter h, boolean flag)
    {
        Listener j = (Listener)H.get(Integer.valueOf(i));
        if(j != null)
        {
            F.warn((new StringBuilder()).append("Listener ").append(i).append(" is already subscribed for publication ").append(I).toString());
            return true;
        }
        Listener k = new Listener(i, this, h, flag);
        if(p.B(k))
        {
            H.put(Integer.valueOf(i), k);
            h.A("Play.Reset", (new StringBuilder()).append("Playing and resetting ").append(J).toString());
            h.A("Play.Start", (new StringBuilder()).append("Started playing ").append(J).toString());
            k.C();
            return true;
        } else
        {
            h.A("Play.Failed", (new StringBuilder()).append("Not authorized to play ").append(J).toString());
            return false;
        }
    }

    public void A(Peer p, int i)
    {
        Listener j = (Listener)H.get(Integer.valueOf(i));
        if(j == null)
        {
            F.warn((new StringBuilder()).append("Listener ").append(i).append(" is already unsubscribed of publication ").append(I).toString());
            return;
        } else
        {
            p.A(j);
            H.remove(Integer.valueOf(i));
            return;
        }
    }

    public void A(String s, String s1)
    {
        if(I == 0)
        {
            F.error((new StringBuilder()).append("Publication ").append(J).append(" is not published").toString());
            return;
        }
        if(A != null)
        {
            if(!Strings.isNullOrEmpty(s))
                A.A(s, s1);
            A.A("close");
        } else
        {
            F.warn((new StringBuilder()).append("Publisher ").append(I).append(" has no controller to close it").toString());
        }
    }

    public void A(Peer p, int i, FlowWriter h)
        throws Exception
    {
        if(I != 0)
        {
            if(h != null)
                h.A("Publish.BadName", (new StringBuilder()).append(J).append(" is already published").toString());
            throw new Exception((new StringBuilder()).append(J).append(" is already published").toString());
        }
        I = i;
        if(!p.A(this))
        {
            String s = (new StringBuilder()).append("Not allowed to publish ").append(J).toString();
            I = 0;
            if(h != null)
                h.A("Publish.BadName", s);
            throw new Exception(s);
        }
        E = p;
        A = h;
        G = false;
        Listener j;
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); j.A(J))
            j = (Listener)iterator.next();

        D();
        if(h != null)
            h.A("Publish.Start", (new StringBuilder()).append(J).append(" is now published").toString());
    }

    public void B(Peer p, int i)
    {
        B.A();
        if(I != i)
        {
            F.warn((new StringBuilder()).append("Unpublish '").append(J).append("' operation with a ").append(i).append(" id different than its publisher ").append(I).append(" id").toString());
            return;
        }
        Listener j;
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); j.B(J))
            j = (Listener)iterator.next();

        D();
        p.B(this);
        D.E();
        C.E();
        I = 0;
        E = null;
        A = null;
    }

    public void D()
    {
        Listener i;
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); i.B())
            i = (Listener)iterator.next();

    }

    public void A(String s, Packet a)
    {
        if(I == 0)
        {
            F.error((new StringBuilder()).append("Data packet pushed on a publication ").append(I).append(" who is idle").toString());
            return;
        }
        int i = a.H();
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); a.E(i))
        {
            Listener j = (Listener)iterator.next();
            j.A(s, a);
        }

        E.A(this, s, a);
    }

    public void B(int i, AudioPacket a, int j)
    {
        int k = a.H();
        if(j > 0)
            F.info((new StringBuilder()).append(j).append(" audio fragments lost on publication ").append(I).toString());
        C.A(i, a.M(), j);
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); a.E(k))
        {
            Listener l = (Listener)iterator.next();
            l.B(i, a);
        }

        if(E != null)
            E.B(this, i, a);
    }

    public void A(int i, AudioPacket a, int j)
    {
        if(j > 0)
            G = false;
        if((a.K() & 0xf0) == 16)
            G = true;
        D.A(i, a.M(), j);
        if(j > 0)
            F.info((new StringBuilder()).append(j).append(" video fragments lost on publication ").append(I).toString());
        if(!G)
        {
            F.debug((new StringBuilder()).append("No key frame available on publication ").append(I).append(", frame dropped to wait first key frame").toString());
            D.G();
            return;
        }
        int k = a.H();
        for(Iterator iterator = H.values().iterator(); iterator.hasNext(); a.E(k))
        {
            Listener l = (Listener)iterator.next();
            l.A(i, a);
        }

        if(E != null)
            E.A(this, i, a);
    }

    public int B()
    {
        return I;
    }

    public Map A()
    {
        return H;
    }

    public String C()
    {
        return J;
    }


}
