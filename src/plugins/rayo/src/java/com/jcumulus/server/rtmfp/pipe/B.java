package com.jcumulus.server.rtmfp.pipe;

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

import java.util.Date;

public class B extends Date
{

    public B()
    {
        super((new Date()).getTime());
    }

    public void A()
    {
        setTime((new Date()).getTime());
    }

    public boolean A(long l)
    {
        long l1 = (new B()).getTime() - getTime();
        return l1 > l;
    }

    public long B()
    {
        Long long1 = Long.valueOf((new Date()).getTime());
        long l = long1.longValue() - getTime();
        if(l == 0L)
            l = 1L;
        return l;
    }

    public static final int A = 4;
}
