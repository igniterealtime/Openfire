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
import org.jivesoftware.openfire.net.QuicServerStanzaHandler;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * S2S business-logic handler for QUIC channels.
 *
 * <p>QUIC provides TLS 1.3 as an integral part of its transport layer. This handler therefore
 * marks every new {@link NettyConnection} as already-encrypted and delegates to
 * {@link QuicServerStanzaHandler}, which suppresses the STARTTLS upgrade path and drives
 * SASL EXTERNAL authentication using the X.509 certificate presented during the QUIC/TLS
 * handshake.</p>
 *
 * <p>The peer certificate chain is exposed to
 * {@link org.jivesoftware.openfire.net.SASLAuthentication} via
 * {@link NettyConnection#getPeerCertificates()}, which falls back to the parent
 * {@code QuicChannel}'s {@code SSLEngine} when no {@code SslHandler} is present in the
 * Netty pipeline.</p>
 */
public class QuicServerConnectionHandler extends NettyServerConnectionHandler
{
    private static final Logger Log = LoggerFactory.getLogger(QuicServerConnectionHandler.class);

    public QuicServerConnectionHandler(final ConnectionConfiguration configuration)
    {
        super(configuration);
    }

    /**
     * Disables the application-level idle timeout for QUIC connections.
     * QUIC has its own transport-level idle timeout and keepalive mechanism; an additional
     * application-level ping/timeout cycle would generate unnecessary traffic.
     * Returning a negative value causes the acceptor to omit the {@code IdleStateHandler}
     * and {@code NettyIdleStateKeepAliveHandler} from the pipeline.
     */
    @Override
    public Duration getMaxIdleTime()
    {
        return Duration.ofMillis(-1);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx)
    {
        Log.trace("Adding QuicServerConnectionHandler");
        super.handlerAdded(ctx);
    }

    @Override
    NettyConnection createNettyConnection(final ChannelHandlerContext ctx)
    {
        final NettyConnection connection = super.createNettyConnection(ctx);
        // QUIC always runs on top of TLS 1.3; mark the connection as encrypted so that
        // SASLAuthentication.getSASLMechanismsElement(LocalIncomingServerSession) offers
        // SASL EXTERNAL without waiting for a STARTTLS upgrade.
        connection.setEncrypted(true);
        return connection;
    }

    @Override
    ServerStanzaHandler createStanzaHandler(final NettyConnection connection)
    {
        return new QuicServerStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);
    }
}
