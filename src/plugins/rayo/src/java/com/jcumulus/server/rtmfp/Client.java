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

import com.jcumulus.server.rtmfp.pipe.C;
import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import com.jcumulus.server.rtmfp.flow.F;
import org.apache.log4j.Logger;

public class Client
{

    private static final Logger Log = Logger.getLogger(Client.class);
    protected byte B[];
    private String I;
    private String E;
    private String D;
    private String C;
    private short G;
    protected boolean A;
    protected FlowWriter F;


    public Client()
    {
    }

    public void C()
    {
        if(F != null)
        {
            F.A("Connect.Closed", "Server close connection");
            F.B(true);
            A = true;
        }
    }

    public void A(String s, Object aobj[])
    {
        if(F != null)
        {
            F f = F.A(s);
            Object aobj1[] = aobj;
            int i = aobj1.length;
            for(int j = 0; j < i; j++)
            {
                Object obj = aobj1[j];
                f.A(obj);
            }

            F.B(true);
        }
    }

    public byte[] A()
    {
        return B;
    }

    public String E()
    {
        return com.jcumulus.server.rtmfp.pipe.C.A(B);
    }

    public String H()
    {
        return I;
    }

    public void B(String s)
    {
        I = s;
    }

    public String G()
    {
        return E;
    }

    public void A(String s)
    {
        E = s;
    }

    public String I()
    {
        return D;
    }

    public void C(String s)
    {
        D = s;
    }

    public String B()
    {
        return C;
    }

    public void D(String s)
    {
        C = s;
    }

    public short D()
    {
        return G;
    }

    public void A(short word0)
    {
        G = word0;
    }

    public FlowWriter F()
    {
        return F;
    }

}
