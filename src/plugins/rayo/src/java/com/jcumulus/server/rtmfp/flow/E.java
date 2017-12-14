package com.jcumulus.server.rtmfp.flow;

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

import com.jcumulus.server.rtmfp.pipe.B;
import java.util.*;

public class E
{
    public E()
    {
        C = new HashMap();
        E = new HashMap();
        B = new HashMap();
        G = new HashMap();
        D = new HashMap();
        F = new HashMap();
        A = new ArrayList();
        H = new HashMap();
    }

    public void B(String s, String s1)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.String);
        C.put(s, s1);
    }

    public void A(String s, E e)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Object);
        F.put(s, e);
    }

    public void A(String s, int i)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Integer);
        E.put(s, Integer.valueOf(i));
    }

    public void A(String s, double d)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Number);
        B.put(s, Double.valueOf(d));
    }

    public void A(String s, boolean flag)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Boolean);
        G.put(s, Boolean.valueOf(flag));
    }

    public void A(String s, B b)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Date);
        D.put(s, b);
    }

    public void G(String s)
    {
        H.put(s, com.jcumulus.server.rtmfp.flow.H.Null);
        A.add(s);
    }

    public boolean F(String s)
    {
        return H.containsKey(s);
    }

    public Map A()
    {
        return H;
    }

    public String H(String s)
    {
        return A(s, ((String) (null)));
    }

    public String A(String s, String s1)
    {
        String s2 = (String)C.get(s);
        return s2 == null ? s1 : s2;
    }

    public Integer B(String s)
    {
        return A(s, ((Integer) (null)));
    }

    public Integer A(String s, Integer integer)
    {
        Integer integer1 = (Integer)E.get(s);
        return integer1 == null ? integer : integer1;
    }

    public Double D(String s)
    {
        return A(s, ((Double) (null)));
    }

    public Double A(String s, Double double1)
    {
        Double double2 = (Double)B.get(s);
        return double2 == null ? double1 : double2;
    }

    public B C(String s)
    {
        return B(s, ((B) (null)));
    }

    public B B(String s, B b)
    {
        B b1 = (B)D.get(s);
        return b1 == null ? b : b1;
    }

    public Boolean A(String s)
    {
        return A(s, ((Boolean) (null)));
    }

    public Boolean A(String s, Boolean boolean1)
    {
        Boolean boolean2 = (Boolean)G.get(s);
        return boolean2 == null ? boolean1 : boolean2;
    }

    public E E(String s)
    {
        return B(s, ((E) (null)));
    }

    public E B(String s, E e)
    {
        E e1 = (E)F.get(s);
        return e1 == null ? e : e1;
    }

    private Map C;
    private Map E;
    private Map B;
    private Map G;
    private Map D;
    private Map F;
    private List A;
    private Map H;
}
