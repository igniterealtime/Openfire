package com.jcumulus.server.rtmfp.client;

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
import com.jcumulus.server.rtmfp.Client;
import com.jcumulus.server.rtmfp.flow.B;
import com.jcumulus.server.rtmfp.application.Publication;
import com.jcumulus.server.rtmfp.application.Listener;

public interface IClientHandler
{

    public abstract boolean onConnection(Client client, B b, com.jcumulus.server.rtmfp.flow.A a);

    public abstract void onFailed(Client client, String s);

    public abstract void onDisconnection(Client client);

    public abstract boolean onPublish(Client client, Publication b);

    public abstract void onUnpublish(Client client, Publication b);

    public abstract boolean onSubscribe(Client client, Listener i);

    public abstract void onUnsubscribe(Client client, Listener i);

    public abstract void onDataPacket(Client client, Publication b, String s, Packet a);

    public abstract void onPacket(Client client, Publication b, int i, Packet a);

    public abstract void onVideoPacket(Client client, Publication b, int i, Packet a);
}
