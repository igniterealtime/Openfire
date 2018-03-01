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

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class A extends InputStream
{
    public A()
    {
        B = new Random();
        A = false;
    }

    public int read() throws IOException
    {
        A();
        int i = B.nextInt() % 256;
        if(i < 0)
            i = -i;
        return i;
    }

    public int read(byte abyte0[], int i, int j) throws IOException
    {
        A();
        byte abyte1[] = new byte[j];
        B.nextBytes(abyte1);
        System.arraycopy(abyte1, 0, abyte0, i, j);
        return j;
    }

    public int read(byte abyte0[]) throws IOException
    {
        A();
        B.nextBytes(abyte0);
        return abyte0.length;
    }

    public long skip(long l) throws IOException
    {
        A();
        return l;
    }

    public void close()
    {
        A = true;
    }

    private void A() throws IOException
    {
        if(A)
            throw new IOException("Input stream closed");
        else
            return;
    }

    public int available()
    {
        return 0x7fffffff;
    }

    private Random B;
    private boolean A;
}
