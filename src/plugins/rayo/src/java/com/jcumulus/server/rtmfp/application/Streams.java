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
import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import java.util.*;
import org.apache.log4j.Logger;


public class Streams
{

    private static Logger A = Logger.getLogger(Streams.class);
    private List D;
    private Map C;
    Integer B;


    public Streams(Map map)
    {
        D = new ArrayList();
        B = Integer.valueOf(0);
        C = map;
    }

    public Integer A()
    {
        Integer integer = B;
        Integer integer1 = B = Integer.valueOf(B.intValue() + 1);
        Integer _tmp = integer;
        A.info((new StringBuilder()).append("New stream ").append(B).toString());
        D.add(B);
        return B;
    }

    public Publication A(String s)
    {
        Publication b = (Publication)C.get(s);
        if(b != null)
        {
            return b;
        } else
        {
            Publication b1 = new Publication(s);
            C.put(s, b1);
            return b1;
        }
    }

    public Publication A(Peer p, int i, String s, FlowWriter h)
        throws Exception
    {
        Publication b = A(s);
        try
        {
            b.A(p, i, h);
        }
        catch(Exception exception)
        {
            if(b.B() == 0 && b.A().size() == 0)
                A(b);
            throw exception;
        }
        return b;
    }

    public void A(Peer p, int i, String s)
    {
        Publication b = (Publication)C.get(s);
        if(b == null)
        {
            A.debug((new StringBuilder()).append("The stream '").append(s).append("' with a ").append(i).append(" id doesn't exist, unpublish useless").toString());
            return;
        }
        b.B(p, i);
        if(b.B() == 0 && b.A().size() == 0)
            A(b);
    }

    public boolean A(Peer p, int i, String s, FlowWriter h, double d)
    {
        Publication b = (Publication)C.get(s);
        boolean flag = false;
        if(b == null)
            b = A(s);
        if(b != null)
        {
            flag = b.A(p, i, h, d == -3000D);
            if(!flag && b.B() == 0 && b.A().size() == 0)
                A(b);
        }
        return flag;
    }

    public void B(Peer p, int i, String s)
    {
        Publication b = (Publication)C.get(s);
        if(b == null)
        {
            A.debug((new StringBuilder()).append("The stream '").append(s).append("' doesn't exists, unsubscribe useless").toString());
            return;
        }
        b.A(p, i);
        if(b.B() == 0 && b.A().size() == 0)
            A(b);
    }

    void A(Publication b)
    {
        C.remove(b.C());
    }

    public void A(Integer integer)
    {
        A.debug((new StringBuilder()).append("Stream ").append(integer).append(" deleted").toString());
        D.remove(integer);
    }

}
