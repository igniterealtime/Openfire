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
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Outbound S2S connection handler for QUIC streams.
 *
 * <p>QUIC provides TLS 1.3 as an integral part of its transport layer. By the time a
 * {@link io.netty.handler.codec.quic.QuicStreamChannel} is handed to this handler the TLS
 * handshake on the parent {@link io.netty.handler.codec.quic.QuicChannel} has already
 * completed successfully. This handler therefore:</p>
 * <ul>
 *   <li>Marks the {@link NettyConnection} as already-encrypted immediately on
 *       {@code channelActive}, so that {@link org.jivesoftware.openfire.net.SASLAuthentication}
 *       offers SASL EXTERNAL without waiting for a STARTTLS upgrade.</li>
 *   <li>Suppresses the {@code SslHandshakeCompletionEvent} path in
 *       {@link NettyOutboundConnectionHandler#userEventTriggered} — that event is never fired
 *       on a QUIC stream channel.</li>
 *   <li>Disables the application-level idle timeout; QUIC has its own transport-level idle
 *       timeout and keepalive mechanism.</li>
 * </ul>
 *
 * <p>Peer certificates are exposed to SASL via
 * {@link NettyConnection#getPeerCertificates()}, which walks up the channel hierarchy to the
 * parent {@code QuicChannel}'s {@code SSLEngine} when no {@code SslHandler} is present in the
 * stream pipeline.</p>
 */
public class QuicOutboundConnectionHandler extends NettyOutboundConnectionHandler
{
    private static final Logger Log = LoggerFactory.getLogger(QuicOutboundConnectionHandler.class);

    public QuicOutboundConnectionHandler(final ConnectionConfiguration configuration,
                                         final DomainPair domainPair,
                                         final int port)
    {
        super(configuration, domainPair, port);
    }

    /**
     * Disables the application-level idle timeout for QUIC connections.
     * QUIC has its own transport-level idle timeout; an additional application-level
     * ping/timeout cycle would generate unnecessary traffic.
     */
    @Override
    public Duration getMaxIdleTime()
    {
        return Duration.ofMillis(-1);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx)
    {
        Log.trace("Adding QuicOutboundConnectionHandler");
        super.handlerAdded(ctx);
    }

    /**
     * Marks the connection as encrypted immediately on channel activation.
     *
     * <p>For QUIC streams the TLS 1.3 handshake has already completed on the parent
     * {@link io.netty.handler.codec.quic.QuicChannel} before this method is called.
     * We therefore set {@code encrypted = true} and fire {@code channelActive} upstream
     * without waiting for an {@code SslHandshakeCompletionEvent} (which is never fired on
     * QUIC stream channels).</p>
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception
    {
        Log.debug("QUIC outbound stream channel active; marking connection as encrypted.");
        final NettyConnection connection = ctx.channel().attr(NettyConnectionHandler.CONNECTION).get();
        if (connection != null) {
            connection.setEncrypted(true);
        }
        // sslInitDone guards the super.channelActive call in NettyOutboundConnectionHandler;
        // set it so the parent's channelActive proceeds normally.
        sslInitDone = true;
        super.channelActive(ctx);
    }

    /**
     * Suppresses SSL-handshake event handling.
     *
     * <p>QUIC stream channels never fire {@code SslHandshakeCompletionEvent}; TLS lives on
     * the parent {@code QuicChannel}. All other user events are forwarded to the superclass.</p>
     */
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception
    {
        if (evt instanceof io.netty.handler.ssl.SslHandshakeCompletionEvent) {
            // Should not happen on a QUIC stream channel, but guard defensively.
            Log.warn("Unexpected SslHandshakeCompletionEvent on QUIC stream channel — ignoring.");
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    NettyConnection createNettyConnection(final ChannelHandlerContext ctx)
    {
        final NettyConnection connection = super.createNettyConnection(ctx);
        // Pre-mark as encrypted so that SASL EXTERNAL is offered immediately.
        connection.setEncrypted(true);
        return connection;
    }
}
