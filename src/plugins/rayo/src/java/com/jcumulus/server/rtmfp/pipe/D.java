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

public class D
{

    public D(byte abyte0[], byte abyte1[])
    {
        B = abyte0;
        A = abyte1;
    }

    public byte[] B()
    {
        return B;
    }

    public void A(byte abyte0[])
    {
        B = abyte0;
    }

    public byte[] A()
    {
        return A;
    }

    public void B(byte abyte0[])
    {
        A = abyte0;
    }

    private byte B[];
    private byte A[];
}
