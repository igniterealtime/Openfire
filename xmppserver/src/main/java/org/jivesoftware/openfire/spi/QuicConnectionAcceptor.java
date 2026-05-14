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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.Quic;
import io.netty.handler.codec.quic.QLogConfiguration;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicChannelOption;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicPathEvent;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamLimitChangedEvent;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.NettyIdleStateKeepAliveHandler;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.openfire.nio.NewConnectionRateLimitHandler;
import org.jivesoftware.openfire.nio.QuicClientConnectionHandler;
import org.jivesoftware.openfire.nio.QuicConnectionId;
import org.jivesoftware.openfire.nio.QuicSessionRegistry;
import org.jivesoftware.openfire.nio.QuicSessionStreamRouter;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.IDLE_FLAG;
import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * Accepts QUIC-based client-to-server connections.
 */
public class QuicConnectionAcceptor extends ConnectionAcceptor
{
    private static final Logger Log = LoggerFactory.getLogger(QuicConnectionAcceptor.class);

    public static final SystemProperty<Duration> WRITE_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.quic.client.write-timeout-seconds")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    /**
     * Directory in which per-connection qlog files are written (one file per QUIC connection).
     * qlog captures transport parameters, stream events, and flow-control frames and is invaluable
     * for diagnosing stream-credit and idle-timeout issues.
     * Set to an empty string (the default) to disable qlog.
     * The directory must exist and be writable by the Openfire process.
     * Each connection produces a file named &lt;uuid&gt;.qlog in that directory.
     */
    public static final SystemProperty<String> QUIC_QLOG_DIR = SystemProperty.Builder.ofType(String.class)
        .setKey("xmpp.quic.client.qlog-dir")
        .setDefaultValue("")
        .setDynamic(false)
        .build();

    /**
     * When {@code true}, the server issues v2 (DCID-bound) address-validation tokens that allow
     * a QUIC client to migrate to a new UDP 4-tuple (e.g. after a NAT rebinding or Wi-Fi ↔
     * cellular handover) without losing its XMPP session (RFC 9000 §9).
     *
     * <p>When {@code false} (the default), the server issues v1 (IP+port-bound) tokens. A
     * migrated client will receive a Retry and must complete a fresh handshake from its new
     * address, which is safe but causes a session interruption.</p>
     *
     * <p><strong>Security note:</strong> v2 tokens are not bound to the client's source address,
     * which slightly weakens UDP amplification protection for the Retry exchange. Enable this
     * only when clients are expected to migrate (e.g. mobile deployments).</p>
     */
    public static final SystemProperty<Boolean> MIGRATION_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.quic.client.migration-enabled")
        .setDefaultValue(false)
        .setDynamic(false)
        .build();

    private final EventLoopGroup ioEventLoopGroup;
    private final EventExecutorGroup blockingHandlerExecutor;
    private final QuicSessionRegistry sessionRegistry = new QuicSessionRegistry();
    private volatile Channel datagramChannel;

    public QuicConnectionAcceptor(final ConnectionConfiguration configuration)
    {
        super(configuration);

        final String name = configuration.getType().toString().toLowerCase();
        ioEventLoopGroup = new MultiThreadIoEventLoopGroup(
            new NamedThreadFactory(name + "-io-", null, false, Thread.NORM_PRIORITY),
            NioIoHandler.newFactory()
        );
        blockingHandlerExecutor = new DefaultEventExecutorGroup(
            configuration.getMaxThreadPoolSize(),
            new NamedThreadFactory(name + "-handler-", null, false, Thread.NORM_PRIORITY)
        );
    }

    @Override
    public synchronized void start()
    {
        if (datagramChannel != null && datagramChannel.isOpen()) {
            Log.debug("Ignoring start request for QUIC listener on port {}, as it is already started.", configuration.getPort());
            return;
        }

        if (!Quic.isAvailable()) {
            Log.error("Unable to start QUIC listener on port {}. Netty QUIC support is unavailable.", configuration.getPort(), Quic.unavailabilityCause());
            return;
        }

        try {
            final QuicSslContext sslContext = createSslContext();

            final long initialMaxData = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiLocal = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiRemote = 10 * 1024 * 1024;
            final long initialMaxStreamsBidi = ConnectionSettings.Client.QUIC_MAX_STREAMS.getValue();
            final long maxIdleTimeoutMs = ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis();
            final List<String> alpn = ConnectionSettings.Client.QUIC_ALPN.getValue();

            // Log all transport parameters at startup so they can be verified without a qlog capture.
            // These are the values advertised to the client in the TLS handshake.
            Log.info("QUIC transport parameters for listener on port {}:", configuration.getPort());
            Log.info("  QUIC stack          : Netty/quiche (netty-codec-quic 4.2.x)");
            Log.info("  ALPN                : {}", alpn);
            Log.info("  max_idle_timeout    : {} ms", maxIdleTimeoutMs);
            Log.info("  initial_max_data    : {} bytes", initialMaxData);
            Log.info("  initial_max_stream_data_bidi_local  : {} bytes", initialMaxStreamDataBidiLocal);
            Log.info("  initial_max_stream_data_bidi_remote : {} bytes", initialMaxStreamDataBidiRemote);
            Log.info("  initial_max_streams_bidi : {} (client may open this many bidi streams toward server)", initialMaxStreamsBidi);
            Log.info("  initial_max_streams_uni  : 0 (unidirectional streams not used)");

            final QuicServerCodecBuilder serverCodecBuilder = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                // HmacQuicTokenHandler issues HMAC-SHA256 address-validation tokens bound to the
                // client's IP, port, and a timestamp. This prevents UDP amplification attacks by
                // ensuring the server only processes packets from addresses that have proven they
                // can receive traffic (RFC 9000 §8.1). The secret is generated fresh at startup;
                // tokens from before a restart are automatically invalidated.
                //
                // Connection migration (RFC 9000 §9) is supported. When a client migrates to
                // a new UDP 4-tuple, quiche fires QuicPathEvent.PeerMigrated on the same
                // QuicChannel object (the channel is reused, not replaced). The
                // QuicSessionStreamRouter is stored as a channel attribute and therefore survives
                // the migration transparently. The QuicSessionRegistry provides a secondary
                // lookup path keyed on the stable ChannelId for robustness.
                .tokenHandler(new HmacQuicTokenHandler(MIGRATION_ENABLED.getValue()))
                .maxIdleTimeout(maxIdleTimeoutMs, TimeUnit.MILLISECONDS)
                .initialMaxData(initialMaxData)
                .initialMaxStreamDataBidirectionalLocal(initialMaxStreamDataBidiLocal)
                .initialMaxStreamDataBidirectionalRemote(initialMaxStreamDataBidiRemote)
                .initialMaxStreamsBidirectional(initialMaxStreamsBidi)
                .initialMaxStreamsUnidirectional(1024)
                .handler(new ChannelInitializer<QuicChannel>()
                {
                    @Override
                    protected void initChannel(final QuicChannel ch)
                    {
                        ch.pipeline()
                            .addLast(new NewConnectionRateLimitHandler(ConnectionType.QUIC_C2S))
                            .addLast(new ChannelInboundHandlerAdapter()
                            {
                                @Override
                                public void channelActive(final ChannelHandlerContext ctx) throws Exception
                                {
                                    final QuicChannel qc = (QuicChannel) ctx.channel();
                                    Log.info("QUIC connection established: remote={}, peerAllowedBidiStreams={}, peerAllowedUniStreams={}",
                                        qc.remoteSocketAddress(),
                                        qc.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL),
                                        qc.peerAllowedStreams(QuicStreamType.UNIDIRECTIONAL));
                                    // Register the router in the session registry so it can be
                                    // looked up by connection ID during path migration (RFC 9000 §9).
                                    final QuicConnectionId cid = new QuicConnectionId(qc.id());
                                    final QuicSessionStreamRouter router =
                                        QuicSessionStreamRouter.getOrCreate(qc, configuration, blockingHandlerExecutor);
                                    sessionRegistry.register(cid, router);
                                    super.channelActive(ctx);
                                }

                                @Override
                                public void channelInactive(final ChannelHandlerContext ctx) throws Exception
                                {
                                    final QuicChannel qc = (QuicChannel) ctx.channel();
                                    sessionRegistry.unregister(new QuicConnectionId(qc.id()));
                                    Log.info("QUIC connection closed: remote={}", qc.remoteSocketAddress());
                                    super.channelInactive(ctx);
                                }

                                @Override
                                public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception
                                {
                                    if (evt instanceof QuicStreamLimitChangedEvent) {
                                        final QuicChannel qc = (QuicChannel) ctx.channel();
                                        Log.info("QUIC stream limit changed: remote={}, peerAllowedBidiStreams={}, peerAllowedUniStreams={}",
                                            qc.remoteSocketAddress(),
                                            qc.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL),
                                            qc.peerAllowedStreams(QuicStreamType.UNIDIRECTIONAL));
                                    } else if (evt instanceof QuicPathEvent.PeerMigrated migrated) {
                                        // The client has migrated to a new UDP 4-tuple. quiche has
                                        // already validated the new path via PATH_CHALLENGE/RESPONSE
                                        // (RFC 9000 §9.3) before firing this event. The QuicChannel
                                        // object is reused, so the QuicSessionStreamRouter channel
                                        // attribute is still valid — no re-attachment is needed.
                                        // The registry entry also remains correct because the
                                        // QuicConnectionId is keyed on the stable ChannelId.
                                        Log.info("QUIC path migration completed: connection={}, old remote={}, new remote={}",
                                            ctx.channel().id().asShortText(),
                                            migrated.remote(), ctx.channel().remoteAddress());
                                    } else if (evt instanceof QuicPathEvent.New newPath) {
                                        Log.debug("QUIC new path seen (not yet validated): local={}, remote={}",
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
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>()
                {
                    @Override
                    protected void initChannel(final QuicStreamChannel channel)
                    {
                        // Log every inbound stream initialisation so we can verify client open_bi() requests
                        // are actually arriving at the server and being accepted by the codec.
                        Log.info("QUIC inbound stream initialised: streamId={}, type={}, parentConn={}, remote={}",
                            channel.streamId(), channel.type(), channel.parent(),
                            channel.parent() == null ? "?" : channel.parent().remoteSocketAddress());

                        if (channel.type() != QuicStreamType.BIDIRECTIONAL) {
                            Log.info("QUIC inbound stream {} is unidirectional; closing (only bidirectional streams are supported).", channel.streamId());
                            channel.close();
                            return;
                        }

                        final QuicSessionStreamRouter streamRouter = QuicSessionStreamRouter.getOrCreate(channel.parent(), configuration, blockingHandlerExecutor);
                        final QuicClientConnectionHandler businessLogicHandler = new QuicClientConnectionHandler(configuration, streamRouter);
                        // QUIC connections rely on the transport-level max_idle_timeout for liveness;
                        // application-level idle checks (XEP-0199 ping / connection-timeout) are disabled
                        // by QuicClientConnectionHandler.getMaxIdleTime() returning a negative value.
                        final Duration maxIdleTime = businessLogicHandler.getMaxIdleTime();
                        final boolean useAppIdleHandler = !maxIdleTime.isNegative();

                        final io.netty.channel.ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0));
                        if (useAppIdleHandler) {
                            pipeline.addLast("idleStateHandler", new IdleStateHandler(maxIdleTime.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS));
                            pipeline.addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(true));
                        }
                        pipeline
                            .addLast(new NettyXMPPDecoder())
                            .addLast(new StringEncoder(StandardCharsets.UTF_8))
                            // Note: WriteTimeoutHandler intentionally NOT added on QUIC streams. Unlike a TCP
                            // socket, a QUIC stream may legitimately hold a write for tens of seconds on a
                            // congested or high-latency/lossy link while the QUIC layer paces, retransmits and
                            // honours flow / congestion control. Liveness is handled by the QUIC transport's
                            // max_idle_timeout (see QUIC_IDLE_TIMEOUT_PROPERTY) and quiche's loss-detection /
                            // PTO machinery. An app-level write timeout here would just disconnect slow but
                            // healthy clients (per XEP-0467 §2 #8).
                            .addLast(new ChannelInboundHandlerAdapter()
                            {
                                @Override
                                public void channelActive(final ChannelHandlerContext ctx) throws Exception
                                {
                                    businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
                                        ctx.channel().eventLoop().execute(() -> {
                                            ctx.channel().attr(IDLE_FLAG).set(null);
                                            ctx.channel().config().setAutoRead(true);
                                        })
                                    );
                                    super.channelActive(ctx);
                                }
                            })
                            .addLast(blockingHandlerExecutor, "businessLogicHandler", businessLogicHandler);

                        channel.config().setAutoRead(false);
                    }
                });

            // Enable qlog if a directory is configured. Each connection writes one file named <uuid>.qlog.
            // qlog captures transport_parameters_set, stream events, and max_streams frames — use qvis
            // (https://qvis.quictools.info/) or similar to inspect the files.
            final String qlogDir = QUIC_QLOG_DIR.getValue();
            if (qlogDir != null && !qlogDir.isBlank()) {
                Log.info("QUIC qlog enabled; writing per-connection files to: {}", qlogDir);
                serverCodecBuilder.option(QuicChannelOption.QLOG,
                    new QLogConfiguration(qlogDir, "openfire-quic", "XMPP-over-QUIC C2S connection"));
            }

            datagramChannel = new Bootstrap()
                .group(ioEventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(serverCodecBuilder.build())
                .bind(new InetSocketAddress(configuration.getBindAddress(), configuration.getPort()))
                .sync()
                .channel();
            Log.info("Started QUIC listener on UDP port {}", configuration.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("Startup interrupted while trying to start QUIC listener on port {}", configuration.getPort(), e);
            closeDatagramChannel();
        } catch (Exception e) {
            Log.error("Unable to start QUIC listener on port {}", configuration.getPort(), e);
            closeDatagramChannel();
        }
    }

    private QuicSslContext createSslContext() throws Exception
    {
        final EncryptionArtifactFactory encryptionArtifactFactory = new EncryptionArtifactFactory(configuration);
        final QuicSslContextBuilder builder = QuicSslContextBuilder.forServer(encryptionArtifactFactory.getKeyManagerFactory(), null);
        final List<String> alpnValues = ConnectionSettings.Client.QUIC_ALPN.getValue();
        if (alpnValues.isEmpty()) {
            throw new IllegalStateException("No ALPN values are configured for QUIC. Configure at least one value in xmpp.quic.client.alpn.");
        }
        builder.applicationProtocols(alpnValues.toArray(String[]::new));
        builder.trustManager(encryptionArtifactFactory.getTrustManagers()[0]);

        final Connection.ClientAuth clientAuth = configuration.getClientAuth();
        if (clientAuth == Connection.ClientAuth.needed) {
            builder.clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE);
        } else if (clientAuth == Connection.ClientAuth.wanted) {
            builder.clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL);
        } else {
            builder.clientAuth(io.netty.handler.ssl.ClientAuth.NONE);
        }

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
        this.configuration = configuration;
    }
}
