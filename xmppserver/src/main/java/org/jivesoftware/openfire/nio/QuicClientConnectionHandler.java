/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.QuicClientStanzaHandler;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * C2S business-logic handler for QUIC channels.
 */
public class QuicClientConnectionHandler extends NettyClientConnectionHandler
{
    private static final Logger Log = LoggerFactory.getLogger(QuicClientConnectionHandler.class);

    private final QuicSessionStreamRouter streamRouter;

    public QuicClientConnectionHandler(final ConnectionConfiguration configuration)
    {
        this(configuration, null);
    }

    public QuicClientConnectionHandler(final ConnectionConfiguration configuration, final QuicSessionStreamRouter streamRouter)
    {
        super(configuration);
        this.streamRouter = streamRouter;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx)
    {
        super.handlerAdded(ctx); // creates NettyConnection + QuicClientStanzaHandler, stores in channel attrs

        if (streamRouter == null) {
            return;
        }
        final LocalClientSession existingSession = streamRouter.getSession();
        if (existingSession == null) {
            return; // primary stream — normal stream-open handshake required
        }

        // Aux stream: proactively initialise without waiting for a client stream-open.
        final QuicClientStanzaHandler handler = (QuicClientStanzaHandler) ctx.channel().attr(HANDLER).get();
        if (handler != null) {
            handler.initAsAuxStream(existingSession);
        } else {
            Log.warn("Could not find QuicClientStanzaHandler on aux stream channel {} — stream-open will be required from client", ctx.channel());
        }
    }

    @Override
    NettyConnection createNettyConnection(final ChannelHandlerContext ctx)
    {
        final NettyConnection connection = super.createNettyConnection(ctx);
        connection.setEncrypted(true); // QUIC always runs on top of TLS.
        if (streamRouter != null) {
            streamRouter.registerInboundConnection(connection);
        }
        return connection;
    }

    @Override
    ClientStanzaHandler createStanzaHandler(final NettyConnection connection)
    {
        if (streamRouter == null) {
            return super.createStanzaHandler(connection);
        }
        return new QuicClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection, streamRouter);
    }
}
