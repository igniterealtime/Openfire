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

import java.util.*;
import org.apache.log4j.Logger;


public class Sessions implements ISessions
{
    private static Logger A = Logger.getLogger(Sessions.class);

    private Handshake D;
    private Map C;
    int B;

    public Sessions()
    {
        C = new HashMap();
        B = 1;
        D = new Handshake(this);
    }

    public ServerSession A(int i, byte abyte0[], byte abyte1[], Peer p)
    {
        int j = A();
        ServerSession h = new ServerSession(j, i, abyte0, abyte1, p);
        C.put(Integer.valueOf(j), h);
        A.info((new StringBuilder()).append("Session ").append(j).append(" created").toString());
        return h;
    }

    public ServerSession A(int i)
    {
        if(i == 0)
            return D;
        ServerSession h = (ServerSession)C.get(Integer.valueOf(i));
        if(h != null && !h.F)
            return h;
        else
            return null;
    }

    public Collection B()
    {
        return C.values();
    }

    private int A()
    {
        int i = B;
        B++;
        return i;
    }
}
