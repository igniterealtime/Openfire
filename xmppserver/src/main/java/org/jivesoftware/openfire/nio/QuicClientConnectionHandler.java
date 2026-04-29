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
            Log.info("QUIC stream {} has no stream router; treating as standalone (no aux-stream support on this connection).", ctx.channel());
            return;
        }
        final LocalClientSession existingSession = streamRouter.getSession();
        if (existingSession == null) {
            Log.info("QUIC stream {} classified as PRIMARY (no session yet on this QUIC connection); awaiting <stream:stream> from client.", ctx.channel());
            return; // primary stream — normal stream-open handshake required
        }

        // Aux stream: proactively initialise without waiting for a client stream-open.
        Log.info("QUIC stream {} classified as AUX (session {} already bound on this QUIC connection); initialising server-side without requiring client <stream:stream>.",
            ctx.channel(), existingSession.getAddress());

        // Aux streams must not have their own idle/keepalive handlers: the QUIC connection-level
        // idle timeout handles liveness, and per-stream idle handlers would fire independently
        // (even while the QUIC connection is active) and send stream:error on the wrong stream.
        // Per XEP-0467 §3.2, stream errors MUST go on stream id=0 only; removing these handlers
        // here is simpler and more correct than trying to re-route their output.
        if (ctx.pipeline().get("idleStateHandler") != null) {
            ctx.pipeline().remove("idleStateHandler");
        }
        if (ctx.pipeline().get("keepAliveHandler") != null) {
            ctx.pipeline().remove("keepAliveHandler");
        }

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
