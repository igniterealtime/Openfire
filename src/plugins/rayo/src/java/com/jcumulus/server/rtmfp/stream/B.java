package com.jcumulus.server.rtmfp.stream;

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
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;


public class B extends BinaryWriter implements Cloneable
{
    public static final int F = 1215;

    public B(byte abyte0[])
    {
        super(abyte0);
    }

    public B(byte abyte0[], int i)
    {
        super(abyte0);
        D = i;
    }

    public B()
    {
        super(new byte[1215]);
    }

    public B F()
    {
        B b = new B((byte[])E.clone(), D);
        b.C(B);
        return b;
    }

    public ChannelBuffer E()
    {
        ChannelBuffer channelbuffer = ChannelBuffers.buffer(A());
        channelbuffer.writeBytes(E, 0, A());
        return channelbuffer;
    }

    public AudioPacket G()
    {
        return new AudioPacket(E, A());
    }

    public Object clone()
        throws CloneNotSupportedException
    {
        return F();
    }

}
