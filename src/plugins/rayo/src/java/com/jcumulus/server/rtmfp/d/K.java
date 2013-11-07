package com.jcumulus.server.rtmfp.d;

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

import com.jcumulus.server.rtmfp.e.F;
import com.jcumulus.server.rtmfp.g.A;

// Referenced classes of package com.jcumulus.server.rtmfp.d:
//            I

public class K extends I
{

    public K()
    {
        super(true);
        J = new A(G);
        I = new F(J);
    }

    public F F()
    {
        return I;
    }

    public A E()
    {
        return J;
    }

    public int C()
    {
        return J.A();
    }

    private F I;
    private A J;
}
