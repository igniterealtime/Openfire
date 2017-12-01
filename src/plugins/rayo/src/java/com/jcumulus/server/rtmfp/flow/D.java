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

import java.util.LinkedList;

public class D
{

    public D(int i)
    {
        this(i, (byte)0);
    }

    public D(int i, byte byte0)
    {
        F = new LinkedList();
        D = i;
        B = 0;
        E = false;
        C = false;
        A = 0;
        G = byte0;
    }

    LinkedList F;
    int B;
    boolean E;
    boolean C;
    int A;
    byte G;
    int D;
}
