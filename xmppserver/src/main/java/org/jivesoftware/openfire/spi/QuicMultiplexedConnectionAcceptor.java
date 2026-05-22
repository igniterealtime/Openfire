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
package org.jivesoftware.openfire.spi;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QLogConfiguration;
import io.netty.handler.codec.quic.QuicPathEvent;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamLimitChangedEvent;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.openfire.nio.NewConnectionRateLimitHandler;
import org.jivesoftware.openfire.nio.QuicClientConnectionHandler;
import org.jivesoftware.openfire.nio.QuicConnectionId;
import org.jivesoftware.openfire.nio.QuicServerConnectionHandler;
import org.jivesoftware.openfire.nio.QuicSessionRegistry;
import org.jivesoftware.openfire.nio.QuicSessionStreamRouter;
import org.jivesoftware.openfire.nio.WebTransportConnectionHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * A single UDP/QUIC listener that multiplexes C2S XMPP, S2S XMPP, and WebTransport (HTTP/3)
 * connections on the same port, distinguished by the ALPN value negotiated during the TLS 1.3
 * handshake.
 *
 * <h2>ALPN routing</h2>
 * <table border="1">
 *   <tr><th>ALPN value</th><th>Protocol</th><th>Handler</th></tr>
 *   <tr><td>{@code xmpp-client}</td><td>XMPP C2S over QUIC (XEP-0467)</td>
 *       <td>{@link QuicClientConnectionHandler}</td></tr>
 *   <tr><td>{@code xmpp-server}</td><td>XMPP S2S over QUIC (federation)</td>
 *       <td>{@link QuicServerConnectionHandler}</td></tr>
 *   <tr><td>{@code h3}</td><td>HTTP/3 WebTransport → XMPP C2S (XEP-0468)</td>
 *       <td>{@link WebTransportConnectionHandler} → {@link QuicClientConnectionHandler}</td></tr>
 * </table>
 *
 * <h2>TLS client authentication</h2>
 * <p>The TLS context is configured with {@code OPTIONAL} client authentication so that both
 * C2S connections (no client cert) and S2S connections (client cert expected) can share the
 * same listener. S2S SASL EXTERNAL enforcement happens at the application layer in
 * {@link org.jivesoftware.openfire.net.QuicServerStanzaHandler} after ALPN routing.</p>
 *
 * <h2>Separate-port operation</h2>
 * <p>When C2S and S2S are configured on different ports, separate
 * {@link QuicConnectionAcceptor} and {@link QuicS2SConnectionAcceptor} instances are used
 * instead of this class. This class is only instantiated when all three protocols share the
 * same port.</p>
 */
public class QuicMultiplexedConnectionAcceptor extends ConnectionAcceptor
{
    private static final Logger Log = LoggerFactory.getLogger(QuicMultiplexedConnectionAcceptor.class);

    /** ALPN value for XMPP client-to-server connections (XEP-0467). */
    public static final String ALPN_XMPP_CLIENT = "xmpp-client";
    /** ALPN value for XMPP server-to-server connections. */
    public static final String ALPN_XMPP_SERVER = "xmpp-server";
    /** ALPN value for HTTP/3 (WebTransport). */
    public static final String ALPN_H3 = "h3";

    /** The C2S connection configuration (used for C2S and WebTransport streams). */
    private final ConnectionConfiguration c2sConfiguration;
    /** The S2S connection configuration (used for S2S streams). */
    private final ConnectionConfiguration s2sConfiguration;
    /** Whether WebTransport (h3) streams are accepted. */
    private final boolean webTransportEnabled;

    private final EventLoopGroup ioEventLoopGroup;
    private final EventExecutorGroup blockingHandlerExecutor;
    private final QuicSessionRegistry sessionRegistry = new QuicSessionRegistry();
    private volatile Channel datagramChannel;

    /**
     * Creates a multiplexed QUIC acceptor.
     *
     * @param c2sConfiguration      connection configuration for C2S (and WebTransport) streams
     * @param s2sConfiguration      connection configuration for S2S streams
     * @param webTransportEnabled   whether to accept {@code h3} ALPN connections
     */
    public QuicMultiplexedConnectionAcceptor(final ConnectionConfiguration c2sConfiguration,
                                             final ConnectionConfiguration s2sConfiguration,
                                             final boolean webTransportEnabled)
    {
        // The parent ConnectionAcceptor stores one configuration; we use c2sConfiguration as
        // the primary (it supplies the port, bind address, and identity store).
        super(c2sConfiguration);
        this.c2sConfiguration = c2sConfiguration;
        this.s2sConfiguration = s2sConfiguration;
        this.webTransportEnabled = webTransportEnabled;

        ioEventLoopGroup = new MultiThreadIoEventLoopGroup(
            new NamedThreadFactory("quic-mux-io-", null, false, Thread.NORM_PRIORITY),
            NioIoHandler.newFactory()
        );
        blockingHandlerExecutor = new DefaultEventExecutorGroup(
            Math.max(c2sConfiguration.getMaxThreadPoolSize(), s2sConfiguration.getMaxThreadPoolSize()),
            new NamedThreadFactory("quic-mux-handler-", null, false, Thread.NORM_PRIORITY)
        );
    }

    @Override
    public synchronized void start()
    {
        if (datagramChannel != null && datagramChannel.isOpen()) {
            Log.debug("Ignoring start request for multiplexed QUIC listener on port {}, already started.",
                c2sConfiguration.getPort());
            return;
        }

        if (!Quic.isAvailable()) {
            Log.error("Unable to start multiplexed QUIC listener on port {}. " +
                "Netty QUIC native support is unavailable on this platform. " +
                "Supported platforms are: linux-x86_64, linux-aarch_64, osx-x86_64, osx-aarch_64, windows-x86_64. " +
                "Ensure the matching netty-codec-native-quic JAR is on the classpath.",
                c2sConfiguration.getPort());
            return;
        }

        try {
            final QuicSslContext sslContext = createSslContext();

            final long initialMaxData = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiLocal = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiRemote = 10 * 1024 * 1024;
            final long initialMaxStreamsBidi = ConnectionSettings.Client.QUIC_MAX_STREAMS.getValue();
            // Use the longer of the two idle timeouts so neither side times out prematurely.
            final long c2sIdleMs = ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis();
            final long s2sIdleMs = ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis();
            final long maxIdleTimeoutMs = Math.max(c2sIdleMs, s2sIdleMs);

            final List<String> alpnValues = buildAlpnList();
            Log.info("Multiplexed QUIC listener on port {} — ALPN: {}", c2sConfiguration.getPort(), alpnValues);

            final boolean migrationEnabled = QuicConnectionAcceptor.MIGRATION_ENABLED.getValue();

            final QuicServerCodecBuilder serverCodecBuilder = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .tokenHandler(new HmacQuicTokenHandler(migrationEnabled))
                .maxIdleTimeout(maxIdleTimeoutMs, TimeUnit.MILLISECONDS)
                .initialMaxData(initialMaxData)
                .initialMaxStreamDataBidirectionalLocal(initialMaxStreamDataBidiLocal)
                .initialMaxStreamDataBidirectionalRemote(initialMaxStreamDataBidiRemote)
                .initialMaxStreamsBidirectional(initialMaxStreamsBidi)
                // Allow unidirectional streams for HTTP/3 control streams (SETTINGS, QPACK encoder/decoder).
                .initialMaxStreamsUnidirectional(webTransportEnabled ? 1024 : 0)
                .handler(buildConnectionHandler())
                .streamHandler(buildStreamHandler());

            final String qlogDir = QuicConnectionAcceptor.QUIC_QLOG_DIR.getValue();
            if (qlogDir != null && !qlogDir.isBlank()) {
                Log.info("QUIC qlog enabled; writing per-connection files to: {}", qlogDir);
                serverCodecBuilder.option(QuicChannelOption.QLOG,
                    new QLogConfiguration(qlogDir, "openfire-quic-mux", "Multiplexed QUIC listener"));
            }

            datagramChannel = new Bootstrap()
                .group(ioEventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(serverCodecBuilder.build())
                .bind(new InetSocketAddress(c2sConfiguration.getBindAddress(), c2sConfiguration.getPort()))
                .sync()
                .channel();

            Log.info("Started multiplexed QUIC listener on UDP port {} (ALPN: {})",
                c2sConfiguration.getPort(), alpnValues);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("Startup interrupted while starting multiplexed QUIC listener on port {}",
                c2sConfiguration.getPort(), e);
            closeDatagramChannel();
        } catch (Exception e) {
            Log.error("Unable to start multiplexed QUIC listener on port {}",
                c2sConfiguration.getPort(), e);
            closeDatagramChannel();
        }
    }

    /**
     * Builds the list of ALPN values to advertise, based on which protocols are enabled.
     */
    private List<String> buildAlpnList()
    {
        final List<String> alpn = new ArrayList<>();
        alpn.add(ALPN_XMPP_CLIENT);
        alpn.add(ALPN_XMPP_SERVER);
        if (webTransportEnabled) {
            alpn.add(ALPN_H3);
        }
        return alpn;
    }

    /**
     * Builds the per-{@link QuicChannel} handler that handles connection-level events
     * (registration, migration, logging) and routes streams by ALPN.
     */
    private ChannelInitializer<QuicChannel> buildConnectionHandler()
    {
        return new ChannelInitializer<QuicChannel>()
        {
            @Override
            protected void initChannel(final QuicChannel ch)
            {
                // Note: TLS has not yet completed at initChannel time, so negotiatedAlpn() here
                // returns the fallback. We log it for tracing but do NOT use it for routing.
                Log.debug("QUIC connection established: remote={}, ALPN='{}'",
                    ch.remoteSocketAddress(), negotiatedAlpn(ch));

                ch.pipeline()
                    .addLast(new NewConnectionRateLimitHandler(ConnectionType.SOCKET_C2S))
                    .addLast(new ChannelInboundHandlerAdapter()
                    {
                        @Override
                        public void channelActive(final ChannelHandlerContext ctx) throws Exception
                        {
                            // TLS is complete by channelActive — read the real negotiated ALPN now.
                            final QuicChannel qc = (QuicChannel) ctx.channel();
                            final String alpn = negotiatedAlpn(qc);
                            Log.info("QUIC connection active: remote={}, ALPN='{}'",
                                qc.remoteSocketAddress(), alpn);
                            if (ALPN_XMPP_CLIENT.equals(alpn) || ALPN_H3.equals(alpn)) {
                                // Register in session registry for migration support.
                                final QuicConnectionId cid = new QuicConnectionId(qc.id());
                                final QuicSessionStreamRouter router =
                                    QuicSessionStreamRouter.getOrCreate(qc, c2sConfiguration, blockingHandlerExecutor);
                                sessionRegistry.register(cid, router);
                            }
                            if (ALPN_H3.equals(alpn)) {
                                // Per RFC 9114 §6.2 and the WebTransport-over-HTTP/3 spec, the
                                // server MUST open a unidirectional control stream and send an
                                // HTTP/3 SETTINGS frame before the client will send any requests.
                                // Until the browser receives SETTINGS it will not send the
                                // WebTransport CONNECT request.
                                sendH3Settings(qc);
                            }
                            super.channelActive(ctx);
                        }

                        @Override
                        public void channelInactive(final ChannelHandlerContext ctx) throws Exception
                        {
                            final QuicChannel qc = (QuicChannel) ctx.channel();
                            final String alpn = negotiatedAlpn(qc);
                            sessionRegistry.unregister(new QuicConnectionId(qc.id()));
                            Log.info("QUIC connection closed: remote={}, ALPN='{}'",
                                qc.remoteSocketAddress(), alpn);
                            super.channelInactive(ctx);
                        }

                        @Override
                        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt)
                            throws Exception
                        {
                            if (evt instanceof QuicStreamLimitChangedEvent) {
                                final QuicChannel qc = (QuicChannel) ctx.channel();
                                Log.debug("QUIC stream limit changed: remote={}, bidi={}, uni={}",
                                    qc.remoteSocketAddress(),
                                    qc.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL),
                                    qc.peerAllowedStreams(QuicStreamType.UNIDIRECTIONAL));
                            } else if (evt instanceof QuicPathEvent.PeerMigrated migrated) {
                                Log.info("QUIC path migration: conn={}, old={}, new={}",
                                    ctx.channel().id().asShortText(),
                                    migrated.remote(), ctx.channel().remoteAddress());
                            } else if (evt instanceof QuicPathEvent.New newPath) {
                                Log.debug("QUIC new path (not yet validated): local={}, remote={}",
                                    newPath.local(), newPath.remote());
                            } else if (evt instanceof QuicPathEvent.Validated validated) {
                                Log.debug("QUIC path validated: local={}, remote={}",
                                    validated.local(), validated.remote());
                            } else if (evt instanceof QuicPathEvent.FailedValidation failed) {
                                Log.warn("QUIC path validation failed: local={}, remote={}",
                                    failed.local(), failed.remote());
                            }
                            super.userEventTriggered(ctx, evt);
                        }
                    });
            }
        };
    }

    /**
     * Builds the per-{@link QuicStreamChannel} handler that routes each stream to the
     * appropriate XMPP or WebTransport pipeline based on the parent connection's ALPN.
     */
    private ChannelInitializer<QuicStreamChannel> buildStreamHandler()
    {
        return new ChannelInitializer<QuicStreamChannel>()
        {
            @Override
            protected void initChannel(final QuicStreamChannel channel)
            {
                final QuicChannel parent = (QuicChannel) channel.parent();
                final String alpn = negotiatedAlpn(parent);

                Log.debug("QUIC stream initialised: streamId={}, type={}, ALPN='{}'",
                    channel.streamId(), channel.type(), alpn);

                if (channel.type() != QuicStreamType.BIDIRECTIONAL) {
                    if (ALPN_H3.equals(alpn)) {
                        // Unidirectional streams for h3 are either HTTP/3 control streams
                        // (type 0x00, 0x02, 0x03) or WebTransport data streams (type 0x54).
                        // We install a handler that reads the stream-type varint and routes accordingly.
                        initH3UnidirectionalStream(channel, parent);
                    } else {
                        Log.info("Unexpected unidirectional QUIC stream {} for ALPN '{}'; closing.",
                            channel.streamId(), alpn);
                        channel.close();
                    }
                    return;
                }

                switch (alpn) {
                    case ALPN_XMPP_CLIENT -> initC2SStream(channel, parent);
                    case ALPN_XMPP_SERVER -> initS2SStream(channel);
                    case ALPN_H3         -> initWebTransportStream(channel, parent);
                    default -> {
                        Log.warn("Unknown ALPN '{}' on stream {}; closing.", alpn, channel.streamId());
                        channel.close();
                    }
                }
            }
        };
    }

    /** Initialises a C2S XMPP stream pipeline. */
    private void initC2SStream(final QuicStreamChannel channel, final QuicChannel parent)
    {
        final QuicSessionStreamRouter streamRouter =
            QuicSessionStreamRouter.getOrCreate(parent, c2sConfiguration, blockingHandlerExecutor);
        final QuicClientConnectionHandler businessLogicHandler =
            new QuicClientConnectionHandler(c2sConfiguration, streamRouter);

        channel.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast(new ChannelInboundHandlerAdapter()
            {
                @Override
                public void channelActive(final ChannelHandlerContext ctx) throws Exception
                {
                    businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
                        ctx.channel().eventLoop().execute(() -> {
                            ctx.channel().attr(NettyConnectionHandler.IDLE_FLAG).set(null);
                            ctx.channel().config().setAutoRead(true);
                        })
                    );
                    super.channelActive(ctx);
                }
            })
            .addLast(blockingHandlerExecutor, "businessLogicHandler", businessLogicHandler);

        channel.config().setAutoRead(false);
    }

    /** Initialises an S2S XMPP stream pipeline. */
    private void initS2SStream(final QuicStreamChannel channel)
    {
        final QuicServerConnectionHandler businessLogicHandler =
            new QuicServerConnectionHandler(s2sConfiguration);

        channel.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast(new ChannelInboundHandlerAdapter()
            {
                @Override
                public void channelActive(final ChannelHandlerContext ctx) throws Exception
                {
                    businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
                        ctx.channel().eventLoop().execute(() -> {
                            ctx.channel().attr(NettyConnectionHandler.IDLE_FLAG).set(null);
                            ctx.channel().config().setAutoRead(true);
                        })
                    );
                    super.channelActive(ctx);
                }
            })
            .addLast(blockingHandlerExecutor, "businessLogicHandler", businessLogicHandler);

        channel.config().setAutoRead(false);
    }

    /**
     * Initialises a bidirectional stream for an HTTP/3 (WebTransport) connection.
     *
     * <p>Per draft-ietf-webtrans-http3, stream 0 is the CONNECT stream: the client sends an
     * HTTP/3 CONNECT request and the server responds with 200. All subsequent bidirectional
     * streams are WebTransport data streams: they begin with the WEBTRANSPORT_STREAM signal
     * value ({@code 0x41}) followed by the session ID (varint), then application payload.</p>
     */
    private void initWebTransportStream(final QuicStreamChannel channel, final QuicChannel parent)
    {
        final QuicSessionStreamRouter streamRouter =
            QuicSessionStreamRouter.getOrCreate(parent, c2sConfiguration, blockingHandlerExecutor);

        if (channel.streamId() == 0) {
            // Stream 0: the HTTP/3 CONNECT stream — handle the WebTransport handshake.
            channel.pipeline()
                .addLast("webTransportUpgrade", new WebTransportConnectionHandler(streamRouter));
            channel.config().setAutoRead(true);
        } else {
            // Subsequent bidirectional streams: WebTransport data streams.
            // They start with 0x41 (WEBTRANSPORT_STREAM signal) + session_id (varint),
            // followed by the application (XMPP) payload.
            initWebTransportDataStream(channel, parent, streamRouter);
        }
    }

    /**
     * Installs a pipeline on a WebTransport data stream that strips the
     * {@code WEBTRANSPORT_STREAM} prefix ({@code 0x41} + session_id varint) and then
     * hands off to the standard XMPP C2S pipeline.
     */
    private void initWebTransportDataStream(final QuicStreamChannel channel,
                                             final QuicChannel parent,
                                             final QuicSessionStreamRouter streamRouter)
    {
        final QuicClientConnectionHandler businessLogicHandler =
            new QuicClientConnectionHandler(c2sConfiguration, streamRouter);

        // Install a one-shot prefix-stripping handler followed by the XMPP pipeline.
        channel.pipeline()
            .addLast("wtPrefixStripper", new io.netty.channel.ChannelInboundHandlerAdapter()
            {
                /** Accumulation buffer for the prefix bytes. */
                private ByteBuf prefixBuf;

                @Override
                public void handlerAdded(final io.netty.channel.ChannelHandlerContext ctx)
                {
                    prefixBuf = ctx.alloc().buffer(16);
                }

                @Override
                public void handlerRemoved(final io.netty.channel.ChannelHandlerContext ctx)
                {
                    if (prefixBuf != null) { prefixBuf.release(); prefixBuf = null; }
                }

                @Override
                public void channelRead(final io.netty.channel.ChannelHandlerContext ctx,
                                        final Object msg) throws Exception
                {
                    if (prefixBuf == null) {
                        // Already stripped — pass through.
                        ctx.fireChannelRead(msg);
                        return;
                    }
                    final ByteBuf in = (ByteBuf) msg;
                    prefixBuf.writeBytes(in);
                    in.release();
                    tryStripPrefix(ctx);
                }

                private void tryStripPrefix(final io.netty.channel.ChannelHandlerContext ctx)
                {
                    final ByteBuf buf = prefixBuf;
                    buf.markReaderIndex();

                    // Read signal value (must be 0x41).
                    if (!buf.isReadable()) { buf.resetReaderIndex(); return; }
                    final int first = buf.readUnsignedByte();
                    if (first != 0x41) {
                        Log.warn("WebTransport data stream {}: unexpected first byte 0x{} (expected 0x41); closing.",
                            channel.streamId(), Integer.toHexString(first));
                        ctx.close();
                        return;
                    }

                    // Read session_id varint.
                    final long sessionId = readVarInt(buf);
                    if (sessionId < 0) {
                        buf.resetReaderIndex();
                        return; // wait for more data
                    }

                    Log.debug("WebTransport data stream {}: prefix stripped, session_id={}.",
                        channel.streamId(), sessionId);

                    // Prefix consumed — install XMPP pipeline and remove self.
                    final io.netty.channel.ChannelPipeline pipeline = ctx.pipeline();
                    pipeline.addAfter(ctx.name(), TRAFFIC_HANDLER_NAME, new io.netty.handler.traffic.ChannelTrafficShapingHandler(0));
                    pipeline.addAfter(TRAFFIC_HANDLER_NAME, "xmppDecoder", new NettyXMPPDecoder());
                    pipeline.addAfter("xmppDecoder", "stringEncoder", new io.netty.handler.codec.string.StringEncoder(StandardCharsets.UTF_8));
                    pipeline.addAfter("stringEncoder", "autoReadEnabler", new io.netty.channel.ChannelInboundHandlerAdapter()
                    {
                        @Override
                        public void channelActive(final io.netty.channel.ChannelHandlerContext ctx2) throws Exception
                        {
                            businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
                                ctx2.channel().eventLoop().execute(() -> {
                                    ctx2.channel().attr(NettyConnectionHandler.IDLE_FLAG).set(null);
                                    ctx2.channel().config().setAutoRead(true);
                                })
                            );
                            super.channelActive(ctx2);
                        }
                    });
                    pipeline.addAfter("autoReadEnabler", "businessLogicHandler", businessLogicHandler);
                    pipeline.remove(this);

                    // Re-fire channelActive so the XMPP handler initialises its session.
                    ctx.fireChannelActive();

                    // Re-inject any remaining bytes (the actual XMPP payload).
                    if (buf.isReadable()) {
                        final ByteBuf leftover = buf.copy();
                        prefixBuf = null;
                        buf.release();
                        ctx.fireChannelRead(leftover);
                    } else {
                        prefixBuf = null;
                        buf.release();
                    }
                }

                /**
                 * Reads a QUIC variable-length integer from buf; returns -1 if incomplete.
                 */
                private long readVarInt(final ByteBuf buf)
                {
                    if (!buf.isReadable()) return -1;
                    final int b0 = buf.readUnsignedByte();
                    final int prefix = (b0 & 0xC0) >> 6;
                    switch (prefix) {
                        case 0: return b0 & 0x3F;
                        case 1:
                            if (!buf.isReadable()) return -1;
                            return ((long)(b0 & 0x3F) << 8) | buf.readUnsignedByte();
                        case 2:
                            if (buf.readableBytes() < 3) return -1;
                            return ((long)(b0 & 0x3F) << 24)
                                | ((long) buf.readUnsignedByte() << 16)
                                | ((long) buf.readUnsignedByte() << 8)
                                | buf.readUnsignedByte();
                        case 3:
                            if (buf.readableBytes() < 7) return -1;
                            return ((long)(b0 & 0x3F) << 56)
                                | ((long) buf.readUnsignedByte() << 48)
                                | ((long) buf.readUnsignedByte() << 40)
                                | ((long) buf.readUnsignedByte() << 32)
                                | ((long) buf.readUnsignedByte() << 24)
                                | ((long) buf.readUnsignedByte() << 16)
                                | ((long) buf.readUnsignedByte() << 8)
                                | buf.readUnsignedByte();
                        default: return -1;
                    }
                }
            })
            .addLast(blockingHandlerExecutor, "businessLogicHandler", businessLogicHandler);

        channel.config().setAutoRead(false);
    }

    /**
     * Handles an incoming unidirectional stream on an h3 connection.
     *
     * <p>Reads the stream-type varint:
     * <ul>
     *   <li>{@code 0x00} — HTTP/3 control stream (SETTINGS etc.): ignore.</li>
     *   <li>{@code 0x02}, {@code 0x03} — QPACK encoder/decoder streams: ignore.</li>
     *   <li>{@code 0x54} — WebTransport unidirectional data stream: strip session_id then
     *       route to XMPP (currently not expected for C2S; logged and closed).</li>
     *   <li>anything else — unknown; ignore.</li>
     * </ul>
     * </p>
     */
    private void initH3UnidirectionalStream(final QuicStreamChannel channel, final QuicChannel parent)
    {
        channel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter()
        {
            private ByteBuf typeBuf;

            @Override
            public void handlerAdded(final io.netty.channel.ChannelHandlerContext ctx)
            {
                typeBuf = ctx.alloc().buffer(8);
            }

            @Override
            public void handlerRemoved(final io.netty.channel.ChannelHandlerContext ctx)
            {
                if (typeBuf != null) { typeBuf.release(); typeBuf = null; }
            }

            @Override
            public void channelRead(final io.netty.channel.ChannelHandlerContext ctx,
                                    final Object msg)
            {
                if (typeBuf == null) {
                    // Already classified — discard (control/QPACK streams).
                    ((ByteBuf) msg).release();
                    return;
                }
                typeBuf.writeBytes((ByteBuf) msg);
                ((ByteBuf) msg).release();
                classifyStream(ctx);
            }

            private void classifyStream(final io.netty.channel.ChannelHandlerContext ctx)
            {
                typeBuf.markReaderIndex();
                if (!typeBuf.isReadable()) return;
                final int b0 = typeBuf.readUnsignedByte();
                final int prefix = (b0 & 0xC0) >> 6;
                // For stream types < 64 (single-byte varint) we have the type already.
                // For larger types we need more bytes — but 0x54 fits in one byte (0x54 < 64? No: 0x54=84 > 63).
                // 0x54 = 0101 0100 — prefix bits = 01 (2-byte varint), value = (0x14 << 8) | next byte.
                final long streamType;
                if (prefix == 0) {
                    streamType = b0 & 0x3F;
                } else if (prefix == 1) {
                    if (!typeBuf.isReadable()) { typeBuf.resetReaderIndex(); return; }
                    streamType = ((long)(b0 & 0x3F) << 8) | typeBuf.readUnsignedByte();
                } else {
                    // Longer varints not expected for stream types; just ignore.
                    streamType = -1;
                }

                if (streamType == 0x54) {
                    // WebTransport unidirectional data stream — not expected for C2S XMPP.
                    Log.debug("WebTransport unidirectional data stream {} (session_id follows); closing (not supported for C2S).",
                        channel.streamId());
                    ctx.close();
                } else {
                    // HTTP/3 control, QPACK, or unknown — silently drain.
                    Log.debug("HTTP/3 unidirectional stream {} type=0x{}: ignoring.",
                        channel.streamId(), Long.toHexString(streamType < 0 ? 0xFF : streamType));
                    typeBuf.release();
                    typeBuf = null;
                    // Remove self; remaining bytes are discarded.
                    ctx.pipeline().remove(this);
                }
            }
        });
        channel.config().setAutoRead(true);
    }

    /**
     * Returns the ALPN protocol negotiated on the given {@link QuicChannel}, or
     * {@link #ALPN_XMPP_CLIENT} as a safe default if the value cannot be determined.
     */
    private static String negotiatedAlpn(final QuicChannel ch)
    {
        try {
            final SSLEngine engine = ch.sslEngine();
            if (engine != null) {
                final String proto = engine.getApplicationProtocol();
                if (proto != null && !proto.isEmpty()) {
                    return proto;
                }
            }
        } catch (final Exception e) {
            Log.warn("Could not read ALPN from QuicChannel {}; defaulting to '{}'.",
                ch.id().asShortText(), ALPN_XMPP_CLIENT, e);
        }
        return ALPN_XMPP_CLIENT;
    }

    /**
     * Maps an ALPN value to the {@link ConnectionType} used for rate-limiting.
     */
    private static ConnectionType alpnToConnectionType(final String alpn)
    {
        return switch (alpn) {
            case ALPN_XMPP_SERVER -> ConnectionType.QUIC_S2S;
            default               -> ConnectionType.QUIC_C2S;
        };
    }

    /**
     * Builds a {@link QuicSslContext} that advertises all enabled ALPN values and uses
     * {@code OPTIONAL} client authentication so that both C2S (no cert) and S2S (cert
     * expected) connections can share the same TLS context.
     */
    private QuicSslContext createSslContext() throws Exception
    {
        // Use the C2S identity store for the server certificate.
        final EncryptionArtifactFactory encFactory = new EncryptionArtifactFactory(c2sConfiguration);
        final QuicSslContextBuilder builder = QuicSslContextBuilder
            .forServer(encFactory.getKeyManagerFactory(), null);

        builder.applicationProtocols(buildAlpnList().toArray(String[]::new));

        // Use the S2S trust store for peer certificate validation (S2S SASL EXTERNAL).
        // C2S connections that don't present a certificate are still accepted because
        // client auth is OPTIONAL.
        final EncryptionArtifactFactory s2sEncFactory = new EncryptionArtifactFactory(s2sConfiguration);
        builder.trustManager(s2sEncFactory.getTrustManagers()[0]);

        // OPTIONAL: accept connections with or without a client certificate.
        // S2S SASL EXTERNAL enforcement happens at the application layer.
        builder.clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL);

        return builder.build();
    }

    @Override
    public synchronized void stop()
    {
        closeDatagramChannel();
        if (!ioEventLoopGroup.isShuttingDown()) {
            ioEventLoopGroup.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS);
        }
        if (!blockingHandlerExecutor.isShuttingDown()) {
            blockingHandlerExecutor.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS);
        }
    }

    private void closeDatagramChannel()
    {
        if (datagramChannel != null) {
            datagramChannel.close();
            datagramChannel = null;
        }
    }

    @Override
    public boolean isIdle()
    {
        return datagramChannel == null || !datagramChannel.isOpen() || !datagramChannel.isActive();
    }

    @Override
    public synchronized void reconfigure(final ConnectionConfiguration configuration)
    {
        // The multiplexed acceptor manages two configurations; reconfigure is a no-op here
        // because changes require a full restart (port/TLS changes affect both protocols).
        Log.debug("reconfigure() called on QuicMultiplexedConnectionAcceptor; restart required for changes to take effect.");
    }

    /**
     * Opens a server-initiated unidirectional QUIC control stream and writes an HTTP/3
     * SETTINGS frame advertising WebTransport support.
     *
     * <p>Per RFC 9114 §6.2 and the WebTransport-over-HTTP/3 specification, the server MUST
     * send this frame before the client will transmit any HTTP/3 requests (including the
     * WebTransport CONNECT). Without it the browser waits indefinitely and the connection
     * idles out.</p>
     *
     * <p>The frame contains two settings:
     * <ul>
     *   <li>{@code H3_DATAGRAM} (id {@code 0x33}) = 1 — enables HTTP/3 datagrams</li>
     *   <li>{@code SETTINGS_ENABLE_WEBTRANSPORT} (id {@code 0x2b603742}) = 1</li>
     * </ul>
     * </p>
     */
    private void sendH3Settings(final QuicChannel qc)
    {
        qc.createStream(QuicStreamType.UNIDIRECTIONAL, null)
            .addListener(f -> {
                if (f.isSuccess()) {
                    final QuicStreamChannel ctrl = (QuicStreamChannel) f.getNow();
                    final ByteBuf buf = buildH3SettingsFrame(ctrl.alloc());
                    ctrl.writeAndFlush(buf).addListener(w -> {
                        if (w.isSuccess()) {
                            Log.debug("HTTP/3 SETTINGS sent on control stream {} for connection {}",
                                ctrl.streamId(), qc.id().asShortText());
                        } else {
                            Log.warn("Failed to send HTTP/3 SETTINGS on control stream for connection {}",
                                qc.id().asShortText(), w.cause());
                        }
                    });
                } else {
                    Log.warn("Failed to open HTTP/3 control stream for connection {}",
                        qc.id().asShortText(), f.cause());
                }
            });
    }

    /**
     * Builds the raw bytes for an HTTP/3 control stream header followed by a SETTINGS frame
     * that enables WebTransport.
     *
     * <pre>
     * Stream type:  0x00                          (1 byte  — HTTP/3 control stream)
     * SETTINGS frame:
     *   type:       0x04                          (1 byte)
     *   length:     varint (total payload bytes)
     *   payload:
     *     id=0x33 (H3_DATAGRAM),                       value=0x01   (2 bytes)
     *     id=0x2b603742 (ENABLE_WEBTRANSPORT),          value=0x01  (5 bytes, 4-byte varint id)
     *     id=0x2b603743 (WEBTRANSPORT_MAX_SESSIONS),    value=0x01  (5 bytes, 4-byte varint id)
     * </pre>
     *
     * <p>Chrome/Edge require {@code SETTINGS_WEBTRANSPORT_MAX_SESSIONS} (id {@code 0x2b603743})
     * to be present and non-zero in addition to {@code SETTINGS_ENABLE_WEBTRANSPORT}; without
     * it {@code wt.ready} never resolves.</p>
     */
    private static ByteBuf buildH3SettingsFrame(final io.netty.buffer.ByteBufAllocator alloc)
    {
        // Payload:
        //   H3_DATAGRAM (id=0x33, value=1)                      → 2 bytes
        //   ENABLE_WEBTRANSPORT (id=0x2b603742, value=1)         → 5 bytes (4-byte varint id + 1)
        //   WEBTRANSPORT_MAX_SESSIONS (id=0x2b603743, value=1)   → 5 bytes (4-byte varint id + 1)
        // Total payload = 12 bytes; length varint = 1 byte
        final ByteBuf buf = alloc.buffer(15);
        // Stream type byte: 0x00 = HTTP/3 control stream
        buf.writeByte(0x00);
        // SETTINGS frame type: 0x04
        buf.writeByte(0x04);
        // SETTINGS payload length: 12 bytes (1-byte varint)
        buf.writeByte(0x0C);
        // Setting: H3_DATAGRAM (0x33) = 1
        buf.writeByte(0x33);
        buf.writeByte(0x01);
        // Setting: SETTINGS_ENABLE_WEBTRANSPORT (0x2b603742) = 1
        // 4-byte QUIC variable-length integer: top 2 bits = 10 → 0x80 | rest
        buf.writeByte(0xAB);  // 0x80 | 0x2b
        buf.writeByte(0x60);
        buf.writeByte(0x37);
        buf.writeByte(0x42);
        buf.writeByte(0x01);
        // Setting: SETTINGS_WEBTRANSPORT_MAX_SESSIONS (0x2b603743) = 1
        // Required by Chrome/Edge: wt.ready will not resolve without this setting.
        buf.writeByte(0xAB);  // 0x80 | 0x2b
        buf.writeByte(0x60);
        buf.writeByte(0x37);
        buf.writeByte(0x43);
        buf.writeByte(0x01);
        return buf;
    }
}
