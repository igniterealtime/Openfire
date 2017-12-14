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

import com.jcumulus.server.rtmfp.packet.*;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;


public class RtmfpChannelUpstreamHandler extends SimpleChannelUpstreamHandler
{

    private static final Logger Log = Logger.getLogger(RtmfpChannelUpstreamHandler.class);
    private Sessions sessions;


    public RtmfpChannelUpstreamHandler(Sessions sessions)
    {
        this.sessions = sessions;
    }

    public void messageReceived(ChannelHandlerContext channelhandlercontext, MessageEvent messageevent)  throws Exception
    {
        ChannelBuffer channelbuffer = (ChannelBuffer)messageevent.getMessage();

        if(channelbuffer.readableBytes() < 16)
            Log.debug((new StringBuilder()).append("Incorrect packet received from ").append(messageevent.getRemoteAddress()).toString());

        byte abyte0[] = channelbuffer.array();
        AudioPacket packet = new AudioPacket(abyte0, channelbuffer.readableBytes());
        int i = com.jcumulus.server.rtmfp.N.A(packet);
        ServerSession h = sessions.A(i);

        if(h == null)
        {
            Log.warn((new StringBuilder()).append("Session with id=").append(i).append(" isn't found").toString());
            return;
        } else
        {
            h.A(messageevent.getChannel(), messageevent.getRemoteAddress());
            h.A(packet);
            return;
        }
    }

}
