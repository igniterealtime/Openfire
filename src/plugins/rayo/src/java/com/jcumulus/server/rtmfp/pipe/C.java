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

import com.jcumulus.server.rtmfp.Peer;


public class C
{

    public C()
    {
    }

    public static void A(String s, Peer p)
    {
    }

    public static A A()
    {
        return B;
    }

    public static boolean B(byte abyte0[], byte abyte1[])
    {
        if(abyte0.length != abyte1.length)
            return false;
        for(int i = 0; i < abyte0.length; i++)
            if(abyte0[i] != abyte1[i])
                return false;

        return true;
    }

    public static boolean A(byte abyte0[], byte abyte1[])
    {
        for(int i = 0; i < abyte0.length; i++)
            if(abyte0[i] != abyte1[i])
                return false;

        return true;
    }

    public static byte A(int i)
    {
        if(i >= 0x200000)
            return 4;
        if(i >= 16384)
            return 3;
        return ((byte)(i < 128 ? 1 : 2));
    }

    public static String A(byte abyte0[])
    {
        int i = 0;
        char ac[] = new char[abyte0.length * 2];
        for(int j = 0; j < abyte0.length; j++)
        {
            ac[i++] = A[abyte0[j] >> 4 & 0xf];
            ac[i++] = A[abyte0[j] & 0xf];
        }

        return new String(ac);
    }

    private static final A B = new A();
    private static final char A[] = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'b', 'c', 'd', 'e', 'f'
    };

}
