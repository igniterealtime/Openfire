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

import com.jcumulus.server.rtmfp.client.*;
import com.jcumulus.server.rtmfp.packet.*;

import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.publisher.E;
import com.jcumulus.server.rtmfp.publisher.F;
import com.jcumulus.server.rtmfp.stream.B;
import com.google.common.base.Strings;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.jboss.netty.channel.Channel;


public class ServerSession implements ISession
{
    private static final Logger J = Logger.getLogger(ServerSession.class);
    int O;
    int D;
    protected Peer M;
    boolean I;
    boolean F;
    byte H;
    K S;
    K K;
    protected final B N;
    Channel Q;
    com.jcumulus.server.rtmfp.pipe.B G;
    short A;
    boolean U;
    int B;
    int E;
    Map L;
    com.jcumulus.server.rtmfp.publisher.FlowWriter R;
    int P;
    Map C;
    Map T;


    public ServerSession(int i, int j, byte abyte0[], byte abyte1[])
    {
        this(i, j, abyte0, abyte1, new Peer());
    }

    public ServerSession(int i, int j, byte abyte0[], byte abyte1[], Peer p)
    {
        N = new B();
        G = new com.jcumulus.server.rtmfp.pipe.B();
        A = 0;
        U = false;
        L = new HashMap();
        P = 1;
        C = new HashMap();
        T = new HashMap();
        H = 0;
        F = false;
        I = false;
        O = i;
        D = j;
        M = p;

        M.A(new ClientHandler());

        S = new K(abyte0, com.jcumulus.server.rtmfp.K.Encryption.DECRYPT);
        K = new K(abyte1, com.jcumulus.server.rtmfp.K.Encryption.ENCRYPT);
        N.E(11);
        N.C(com.jcumulus.server.rtmfp.N.G);
    }

    public I A(byte abyte0[])
    {
        I i = (I)T.get(abyte0);
        if(i == null)
        {
            i = new I();
            T.put(abyte0, i);
        }
        return i;
    }

    public void A(Channel channel, SocketAddress socketaddress)
    {
        Q = channel;
        M.A(socketaddress);
    }

    public void A(AudioPacket a)
    {
        if(!com.jcumulus.server.rtmfp.N.A(S, a))
        {
            J.error((new StringBuilder()).append("Decrypt error on session ").append(O).toString());
            return;
        } else
        {
            B(a);
            return;
        }
    }

    protected void A(B b)
    {
        if(Q == null)
        {
            J.error((new StringBuilder()).append("Impossible to send on a null socket for session ").append(O).toString());
            return;
        }
        B b1 = com.jcumulus.server.rtmfp.N.A(K, b);
        if(b1 != null)
        {
            com.jcumulus.server.rtmfp.N.A(b1, D);
            Q.write(b1.E(), M.L());
        }
    }

    public boolean A(com.jcumulus.server.rtmfp.publisher.FlowWriter h)
    {
        return R == h;
    }

    public void B(com.jcumulus.server.rtmfp.publisher.FlowWriter h)
    {
        while(++P == 0 || C.get(Integer.valueOf(P)) != null) ;
        h.B(P);
        if(L.size() > 0)
            h.C(((E[])L.values().toArray(new E[L.size()]))[0].D());
        C.put(Integer.valueOf(P), h);
    }

  public com.jcumulus.server.rtmfp.stream.B A(byte paramByte, int paramInt, com.jcumulus.server.rtmfp.publisher.FlowWriter paramH)
  {
    if (this.U)
      synchronized (this.N)
      {
        this.N.B(11);
        this.N.C(this.N.C());
        return this.N;
      }
    this.R = paramH;
    int i = paramInt + 3;
    if (i > this.N.D())
    {
      A(false);
      if (i > this.N.D())
      {
        J.error("Message truncated because exceeds maximum UDP packet size on session " + this.O);
        i = this.N.D();
      }
      this.R = null;
    }
    synchronized (this.N)
    {
      this.N.C(this.N.C() + i);
      this.N.B(paramByte);
      this.N.A((short)paramInt);
      return this.N;
    }
  }

    private void D()
    {
        if(F)
            return;
        B++;
        synchronized(N)
        {
            N.B((byte)12);
            N.A((short)0);
        }
        A(false);
        if(B == 10 || G.A(0x15752a00L))
            C();
    }

    private void A(String s)
    {
        if(U)
            return;
        synchronized(N)
        {
            N.B(11);
        }
        Integer integer;
        for(Iterator iterator = C.keySet().iterator(); iterator.hasNext(); ((com.jcumulus.server.rtmfp.publisher.FlowWriter)C.get(integer)).I())
            integer = (Integer)iterator.next();

        this.M.A((com.jcumulus.server.rtmfp.publisher.FlowWriter)null);
        this.M.J();
        this.U = true;

        if(!Strings.isNullOrEmpty(s))
        {
            J.warn((new StringBuilder()).append("Session failed id : ").append(s).toString());
            M.E(s);
            D();
        }
    }

    public void C()
    {
        if(F)
            return;
        M.O();
        M.C();
        U = true;
        M.A((com.jcumulus.server.rtmfp.publisher.FlowWriter)null);
        M.J();
        E e;
        for(Iterator iterator = L.values().iterator(); iterator.hasNext(); e.C())
            e = (E)iterator.next();

        L.clear();
        com.jcumulus.server.rtmfp.publisher.FlowWriter h;
        for(Iterator iterator1 = C.values().iterator(); iterator1.hasNext(); h.F())
            h = (com.jcumulus.server.rtmfp.publisher.FlowWriter)iterator1.next();

        C.clear();
        F = true;
        J.info((new StringBuilder()).append("Session ").append(O).append(" died").toString());
    }

    protected void B(AudioPacket a)
    {
        if(F)
            return;
        if(!U && M.K())
            A("");
        if(M.N().size() == 0)
        {
            J.error((new StringBuilder()).append("Session ").append(O).append(" has no any addresses!").toString());
            M.N().add(M.L().toString());
        }
        G.A();
        byte byte0 = (byte)(a.L() | 0xfffffff0);
        A = a.E();
        if(byte0 == -3)
        {
            short word0 = com.jcumulus.server.rtmfp.N.A(G.getTime());
            short word1 = a.E();
            M.A((short)(word1 <= word0 ? word0 - word1 : 0));
        } else
        if(byte0 != -7)
            J.warn("Packet marker unknown : marker");
        byte byte1 = 0;
        E e = null;
        int i = 0;
        int j = 0;
        byte byte2 = a.I() <= 0 ? -1 : a.L();
        boolean flag = false;
        do
        {
            if(byte2 == -1)
                break;
            short word2 = a.E();
            AudioPacket a1 = new AudioPacket(a.G(), word2);
            switch(byte2)
            {
            case 112: // 'p'
                break;

            case 12: // '\f'
                A("failed on client side");
                break;

            case 76: // 'L'
                C();
                return;

            case 1: // '\001'
                if(!M.M())
                    A("Timeout connection client");
                else
                    A((byte)65, 0, null);
                // fall through

            case 65: // 'A'
                E = 0;
                break;

            case 94: // '^'
                int k = a1.J();
                com.jcumulus.server.rtmfp.publisher.FlowWriter h = (com.jcumulus.server.rtmfp.publisher.FlowWriter)C.get(Integer.valueOf(k));
                if(h != null)
                    h.D((new StringBuilder()).append("FlowWriter rejected on session ").append(O).toString());
                else
                    J.warn((new StringBuilder()).append("FlowWriter ").append(k).append(" unfound for failed signal on session ").append(O).toString());
                break;

            case 24: // '\030'
                A("ack negative from server");
                break;

            case 81: // 'Q'
                int l = a1.J();
                com.jcumulus.server.rtmfp.publisher.FlowWriter h1 = (com.jcumulus.server.rtmfp.publisher.FlowWriter)C.get(Integer.valueOf(l));
                if(h1 != null)
                    h1.A(a1);
                else
                    J.warn((new StringBuilder()).append("FlowWriter ").append(l).append(" unfound for acknowledgment on session").append(O).toString());
                break;

            case 16: // '\020'
                byte1 = a1.L();
                int i1 = a1.J();
                i = a1.J() - 1;
                j = a1.J() - 1;
                if(U)
                    break;
                e = (E)L.get(Integer.valueOf(i1));
                if((byte1 & com.jcumulus.server.rtmfp.publisher.E.N.intValue()) != 0)
                {
                    byte abyte0[] = a1.F(a1.L() & 0xff);
                    if(e == null)
                        e = A(i1, abyte0);
                    if(a1.L() > 0)
                    {
                        if(a1.L() != 10)
                            J.warn((new StringBuilder()).append("Unknown fullduplex header part for the flow '").append(i1).append("'").toString());
                        else
                            a1.J();
                        byte byte3;
                        for(byte3 = a1.L(); byte3 > 0 && a1.I() > 0; byte3 = a1.L())
                        {
                            J.warn((new StringBuilder()).append("Unknown message part on flow '").append(i1).append("'").toString());
                            a1.D(byte3);
                        }

                        if(byte3 > 0)
                            J.error("Bad header message part, finished before scheduled");
                    }
                }
                if(e == null)
                    J.warn((new StringBuilder()).append("Flow ").append(i1).append(" unfound").toString());
                // fall through

            case 17: // '\021'
                i++;
                j++;
                if(byte2 == 17)
                    byte1 = a1.L();
                if(e == null)
                    break;
                e.A(i, j, a1, byte1);
                if(!Strings.isNullOrEmpty(e.G()) || M.K())
                {
                    A(e.G());
                    e = null;
                }
                break;

            default:
                J.error((new StringBuilder()).append("Message type '").append(byte2).append("' unknown").toString());
                break;
            }
            a.D(word2);
            byte2 = a.I() <= 0 ? -1 : a.L();
            if(e != null && byte2 != 17)
            {
                e.F();
                if(e.B())
                    L.remove(Integer.valueOf(e.D()));
                e = null;
            }
        } while(true);
        A((byte)74, true);
    }

    private E A(int i, byte abyte0[])
    {
        if(F)
        {
            J.error((new StringBuilder()).append("Session ").append(i).append(" is died, no more Flow creation possible").toString());
            return null;
        }
        Object obj = (E)L.get(Integer.valueOf(i));
        if(obj != null)
        {
            J.warn((new StringBuilder()).append("Flow ").append(i).append(" has already been created").toString());
            return ((E) (obj));
        }
        if(com.jcumulus.server.rtmfp.pipe.C.B(abyte0, com.jcumulus.server.rtmfp.publisher.F.S))
            obj = new F(i, M, this);
        else
        if(com.jcumulus.server.rtmfp.pipe.C.B(abyte0, com.jcumulus.server.rtmfp.publisher.C.Q))
            obj = new com.jcumulus.server.rtmfp.publisher.C(i, M, this);
        else
        if(com.jcumulus.server.rtmfp.pipe.C.A(com.jcumulus.server.rtmfp.publisher.A.Z, abyte0))
            obj = new com.jcumulus.server.rtmfp.publisher.A(i, abyte0, M, this);
        else
            J.error((new StringBuilder()).append("New unknown flow '").append(Arrays.toString(abyte0)).append("' on session ").append(O).toString());
        if(obj != null)
        {
            J.debug((new StringBuilder()).append("New flow ").append(i).append(" on session ").append(O).toString());
            L.put(Integer.valueOf(i), obj);
        }
        return ((E) (obj));
    }

    public void A(boolean flag)
    {
        A((byte)74, flag);
    }

    protected void A(byte byte0, boolean flag)
    {
        R = null;
        if(F)
            return;
        if(N.A() >= com.jcumulus.server.rtmfp.N.C)
        {
            if(G.A(0x1c9c380L))
                flag = false;
            byte byte1 = 0;
            if(flag)
                byte0 += 4;
            else
                byte1 = 2;
            B b1;
            synchronized(N)
            {
                b1 = N.F();
                N.B(11);
                N.C(com.jcumulus.server.rtmfp.N.G);
            }
            if(b1 != null)
            {
                b1.C(0);
                b1.G(byte1);
                b1.F(6);
                b1.B(byte0);
                b1.A(com.jcumulus.server.rtmfp.N.A());
                if(flag)
                    b1.A((short)(A + com.jcumulus.server.rtmfp.N.A(G.B())));
                A(b1);
            }
        }
    }

    public boolean A()
    {
        return U;
    }

    public B B()
    {
        N.C(com.jcumulus.server.rtmfp.N.G);
        return N;
    }

}
