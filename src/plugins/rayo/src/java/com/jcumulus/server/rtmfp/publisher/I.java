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
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class I
{

    public static final byte F = 0;
    public static final byte E = 8;
    public static final byte D = 9;
    public static final byte C = 20;
    public static final byte H = 15;
    private Map A;
    private boolean B;
    protected byte G[];


    protected I(boolean flag)
    {
        A = new LinkedHashMap();
        G = new byte[20000];
        B = flag;
    }

    public abstract int C();

    public Packet D()
    {
        return new Packet(G, C());
    }

    public Packet A(int i)
    {
        Packet b = new Packet(G, C());
        b.E(i);
        return b;
    }

    public Map B()
    {
        return A;
    }

    public boolean A()
    {
        return B;
    }
}
