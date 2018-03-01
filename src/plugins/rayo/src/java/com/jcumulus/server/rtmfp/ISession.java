package com.jcumulus.server.rtmfp;

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


import com.jcumulus.server.rtmfp.publisher.FlowWriter;
import com.jcumulus.server.rtmfp.stream.B;

public interface ISession
{
    public abstract void A(boolean flag);

    public abstract boolean A(FlowWriter h);

    public abstract void B(FlowWriter h);

    public abstract B A(byte byte0, int i, FlowWriter h);

    public abstract boolean A();

    public abstract B B();
}
