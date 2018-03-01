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

import com.jcumulus.server.rtmfp.application.Publication;
import com.jcumulus.server.rtmfp.application.Listener;

import com.jcumulus.server.rtmfp.client.ClientHandler;
import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import com.jcumulus.server.rtmfp.flow.B;
import com.jcumulus.server.rtmfp.flow.F;

import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;


public class Peer extends Client
{

    private static final Logger L = Logger.getLogger(Peer.class);
    private SocketAddress J;
    private List N;
    private ClientHandler K;
    private boolean M;

    public Peer()
    {
        N = new ArrayList();
    }

    public void A(ClientHandler a)
    {
        K = a;
    }

    public boolean A(B b, com.jcumulus.server.rtmfp.flow.A a)
    {
        if(!M)
            M = K == null || K.onConnection(this, b, a);
        else
            L.error((new StringBuilder()).append("Client ").append(B).append(" seems already connected!").toString());
        return M;
    }

    public void E(String s)
    {
        if(M && K != null)
            K.onFailed(this, s);
    }

    public void O()
    {
        if(M)
        {
            M = false;
            if(K != null)
                K.onDisconnection(this);
        }
    }

    public boolean A(String s, B b)
    {
        if(M && K != null)
        {
            try
            {
                Method method = K.getClass().getMethod(s, new Class[] {
                    com.jcumulus.server.rtmfp.Client.class, b.getClass()
                });
                Object obj = method.invoke(K, new Object[] {
                    this, b
                });
                F f = F.J();
                f.A(obj);
                return true;
            }
            catch(Throwable throwable)
            {
                L.error(throwable.getMessage(), throwable);
            }
            return false;
        } else
        {
            L.warn("RPC client before connection");
            F.B("Call.Failed", "Client must be connected before remote procedure calling");
            return true;
        }
    }

    public boolean A(Publication b)
    {
        if(M)
        {
            return K == null || K.onPublish(this, b);
        } else
        {
            L.warn("Publication client before connection");
            return false;
        }
    }

    public void B(Publication b)
    {
        if(M)
        {
            if(K != null)
                K.onUnpublish(this, b);
            return;
        } else
        {
            L.warn("Unpublication client before connection");
            return;
        }
    }

    public boolean B(Listener i)
    {
        if(M)
        {
            return K == null || K.onSubscribe(this, i);
        } else
        {
            L.warn("Subscription client before connection");
            return false;
        }
    }

    public void A(Listener i)
    {
        if(M)
        {
            if(K != null)
                K.onUnsubscribe(this, i);
            return;
        } else
        {
            L.warn("Unsubscription client before connection");
            return;
        }
    }

    public void A(Publication b, String s, com.jcumulus.server.rtmfp.packet.Packet a)
    {
        if(M)
        {
            if(K != null)
                K.onDataPacket(this, b, s, a);
            return;
        } else
        {
            L.warn("DataPacket client before connection");
            return;
        }
    }

    public void B(Publication b, int i, com.jcumulus.server.rtmfp.packet.Packet a)
    {
        if(M)
        {
            if(K != null)
                K.onPacket(this, b, i, a);
            return;
        } else
        {
            L.warn("Packet client before connection");
            return;
        }
    }

    public void A(Publication b, int i, com.jcumulus.server.rtmfp.packet.Packet a)
    {
        if(M)
        {
            if(K != null)
                K.onVideoPacket(this, b, i, a);
            return;
        } else
        {
            L.warn("VideoPacket client before connection");
            return;
        }
    }

    public void J()
    {
    }

    public List N()
    {
        return N;
    }

    public void A(FlowWriter h)
    {
        F = h;
    }

    public boolean K()
    {
        return A;
    }

    public SocketAddress L()
    {
        return J;
    }

    public void A(SocketAddress socketaddress)
    {
        J = socketaddress;
    }

    public boolean M()
    {
        return M;
    }


}
