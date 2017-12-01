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
import com.jcumulus.server.rtmfp.Peer;
import com.jcumulus.server.rtmfp.flow.A;
import com.jcumulus.server.rtmfp.flow.B;
import com.jcumulus.server.rtmfp.flow.E;
import com.jcumulus.server.rtmfp.application.C;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.util.*;



public class F extends com.jcumulus.server.rtmfp.publisher.E
{

    public F(int i, Peer p, ISession d)
    {
        super(i, S, "NetConnection", p, d);
        U = new ArrayList();
    }

    public void A(String s, B b)
    {
        if("connect".equals(s))
        {
            b.C();
            E e = b.L();
            b.K();
            K.A(e.A("swfUrl", ""));
            K.C(e.A("pageUrl", ""));
            K.D(e.A("flashVer", ""));
            if(e.A("objectEncoding", Double.valueOf(0.0D)).doubleValue() == 0.0D)
            {
                O.B("Connect.Error", "ObjectEncoding client must be in a AMF3 format (not AMF0)");
                return;
            }
            K.A(O);
            int i = O.D();
            A a1 = O.C("Connect.Success", "Connection succeeded");
            a1.A("objectEncoding", 3D);
            boolean flag = K.A(b, a1);
            a1.A();
            if(!flag)
            {
                O.A(i);
                K.C();
            }
        } else
        if("setPeerInfo".equals(s))
        {
            String s1;
            for(; b.B(); K.N().add(s1))
                s1 = b.E();

            com.jcumulus.server.rtmfp.stream.BinaryWriter a = O.A(false);
            a.A((short)41);
            a.A(JiveGlobals.getIntProperty("voicebridge.rtmfp.keep.alive.server", 5));
            a.A(JiveGlobals.getIntProperty("voicebridge.rtmfp.keep.alive.peer", 5));
        } else
        if(!"initStream".equals(s))
            if("createStream".equals(s))
            {
                com.jcumulus.server.rtmfp.flow.F f = O.J();
                Integer integer1 = I.getStreams().A();
                U.add(integer1);
                f.A(integer1.intValue());
            } else
            if("deleteStream".equals(s))
            {
                Integer integer = Integer.valueOf(b.N().intValue());
                U.remove(integer);
                I.getStreams().A(integer);
            } else
            if(!K.A(s, b))
                O.B("Call.Failed", (new StringBuilder()).append("Method '").append(s).append("' not found").toString());
    }

    public static final byte S[] = {
        0, 84, 67, 4, 0
    };
    public static final String T = "NetConnection";
    List U;

}
