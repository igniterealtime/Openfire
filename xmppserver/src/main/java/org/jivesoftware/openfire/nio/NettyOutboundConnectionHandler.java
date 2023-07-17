/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbound (S2S) specific ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created
 * and how to build and configure a {@link NettyConnection}.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public class NettyOutboundConnectionHandler extends NettyConnectionHandler {
    private static final Logger Log = LoggerFactory.getLogger(NettyOutboundConnectionHandler.class);
    private final DomainPair domainPair;

    public NettyOutboundConnectionHandler(ConnectionConfiguration configuration, DomainPair domainPair) {
        super(configuration);
        this.domainPair = domainPair;
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        return new NettyConnection(ctx, null, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new RespondingServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection, domainPair );
    }

    @Override
    int getMaxIdleTime() {
        return 0;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Log.trace("Adding NettyOutboundConnectionHandler");
        super.handlerAdded(ctx);
    }
}
