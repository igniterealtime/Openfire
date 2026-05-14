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
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.openfire.nio.NewConnectionRateLimitHandler;
import org.jivesoftware.openfire.nio.QuicServerConnectionHandler;
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

import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * Accepts QUIC-based server-to-server (S2S / federation) connections.
 *
 * <h2>TLS and authentication</h2>
 * <p>QUIC mandates TLS 1.3 for every connection. There is no STARTTLS upgrade: the TLS
 * handshake completes before any XMPP bytes are exchanged. The remote server is expected to
 * present an X.509 certificate during the TLS handshake; Openfire will offer SASL EXTERNAL
 * (XEP-0178) once the stream is open and the certificate has been verified against the
 * configured trust store.</p>
 *
 * <h2>Certificate access</h2>
 * <p>Unlike TCP+TLS connections, QUIC streams do not have a {@code SslHandler} in their Netty
 * pipeline. The TLS session lives on the parent {@link QuicChannel}. The updated
 * {@link org.jivesoftware.openfire.nio.NettyConnection#getPeerCertificates()} and
 * {@link org.jivesoftware.openfire.nio.NettyConnection#getLocalCertificates()} methods walk up
 * the channel hierarchy to retrieve certificates from the {@code QuicChannel}'s
 * {@code SSLEngine}, making them transparently available to
 * {@link org.jivesoftware.openfire.net.SASLAuthentication}.</p>
 *
 * <h2>Client authentication</h2>
 * <p>The TLS client-auth policy is taken from the connection configuration (defaulting to
 * {@code wanted} for S2S). When set to {@code needed} the TLS handshake will fail if the
 * remote server does not present a certificate; when set to {@code wanted} the handshake
 * succeeds but SASL EXTERNAL will not be offered (Server Dialback may be used instead).</p>
 */
public class QuicS2SConnectionAcceptor extends ConnectionAcceptor
{
    private static final Logger Log = LoggerFactory.getLogger(QuicS2SConnectionAcceptor.class);

    public static final SystemProperty<Duration> WRITE_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.quic.server.write-timeout-seconds")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    private final EventLoopGroup ioEventLoopGroup;
    private final EventExecutorGroup blockingHandlerExecutor;
    private volatile Channel datagramChannel;

    public QuicS2SConnectionAcceptor(final ConnectionConfiguration configuration)
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
            Log.debug("Ignoring start request for QUIC S2S listener on port {}, as it is already started.", configuration.getPort());
            return;
        }

        if (!Quic.isAvailable()) {
            Log.error("Unable to start QUIC S2S listener on port {}. Netty QUIC native support is unavailable on this platform. " +
                "Supported platforms are: linux-x86_64, linux-aarch_64, osx-x86_64, osx-aarch_64, windows-x86_64. " +
                "Ensure the matching netty-codec-native-quic JAR is on the classpath.",
                configuration.getPort(), Quic.unavailabilityCause());
            return;
        }

        try {
            final QuicSslContext sslContext = createSslContext();

            final long initialMaxData = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiLocal = 10 * 1024 * 1024;
            final long initialMaxStreamDataBidiRemote = 10 * 1024 * 1024;
            // S2S connections typically carry one stream per federation session; allow a small
            // number of concurrent streams to support future multiplexing.
            final long initialMaxStreamsBidi = 16;
            final long maxIdleTimeoutMs = ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis();
            final List<String> alpn = ConnectionSettings.Server.QUIC_ALPN.getValue();

            Log.info("QUIC S2S transport parameters for listener on port {}:", configuration.getPort());
            Log.info("  QUIC stack          : Netty/quiche (netty-codec-quic 4.2.x)");
            Log.info("  ALPN                : {}", alpn);
            Log.info("  max_idle_timeout    : {} ms", maxIdleTimeoutMs);
            Log.info("  initial_max_data    : {} bytes", initialMaxData);
            Log.info("  initial_max_streams_bidi : {}", initialMaxStreamsBidi);

            final QuicServerCodecBuilder serverCodecBuilder = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                // HmacQuicTokenHandler issues HMAC-SHA256 address-validation tokens bound to the
                // remote server's IP, port, and a timestamp, preventing UDP amplification attacks
                // (RFC 9000 §8.1). Connection migration is not expected for S2S connections so
                // v1 (address-bound) tokens are always used.
                .tokenHandler(new HmacQuicTokenHandler(false))
                .maxIdleTimeout(maxIdleTimeoutMs, TimeUnit.MILLISECONDS)
                .initialMaxData(initialMaxData)
                .initialMaxStreamDataBidirectionalLocal(initialMaxStreamDataBidiLocal)
                .initialMaxStreamDataBidirectionalRemote(initialMaxStreamDataBidiRemote)
                .initialMaxStreamsBidirectional(initialMaxStreamsBidi)
                .initialMaxStreamsUnidirectional(0)
                .handler(new ChannelInitializer<QuicChannel>()
                {
                    @Override
                    protected void initChannel(final QuicChannel ch)
                    {
                        ch.pipeline()
                            .addLast(new NewConnectionRateLimitHandler(ConnectionType.QUIC_S2S))
                            .addLast(new ChannelInboundHandlerAdapter()
                            {
                                @Override
                                public void channelActive(final ChannelHandlerContext ctx) throws Exception
                                {
                                    final QuicChannel qc = (QuicChannel) ctx.channel();
                                    Log.info("QUIC S2S connection established: remote={}",
                                        qc.remoteSocketAddress());
                                    super.channelActive(ctx);
                                }

                                @Override
                                public void channelInactive(final ChannelHandlerContext ctx)
                                {
                                    Log.debug("QUIC S2S connection closed: remote={}",
                                        ctx.channel().remoteAddress());
                                }
                            });
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>()
                {
                    @Override
                    protected void initChannel(final QuicStreamChannel channel)
                    {
                        final QuicServerConnectionHandler businessLogicHandler =
                            new QuicServerConnectionHandler(configuration);

                        final io.netty.channel.ChannelPipeline pipeline = channel.pipeline();
                        pipeline.addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0));
                        pipeline
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
                });

            datagramChannel = new Bootstrap()
                .group(ioEventLoopGroup)
                .channel(NioDatagramChannel.class)
                .handler(serverCodecBuilder.build())
                .bind(new InetSocketAddress(configuration.getBindAddress(), configuration.getPort()))
                .sync()
                .channel();
            Log.info("Started QUIC S2S listener on UDP port {}", configuration.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("Startup interrupted while trying to start QUIC S2S listener on port {}", configuration.getPort(), e);
            closeDatagramChannel();
        } catch (Exception e) {
            Log.error("Unable to start QUIC S2S listener on port {}", configuration.getPort(), e);
            closeDatagramChannel();
        }
    }

    private QuicSslContext createSslContext() throws Exception
    {
        final EncryptionArtifactFactory encryptionArtifactFactory = new EncryptionArtifactFactory(configuration);
        final QuicSslContextBuilder builder = QuicSslContextBuilder.forServer(encryptionArtifactFactory.getKeyManagerFactory(), null);
        final List<String> alpnValues = ConnectionSettings.Server.QUIC_ALPN.getValue();
        if (alpnValues.isEmpty()) {
            throw new IllegalStateException("No ALPN values are configured for QUIC S2S. Configure at least one value in xmpp.quic.server.alpn.");
        }
        builder.applicationProtocols(alpnValues.toArray(String[]::new));
        builder.trustManager(encryptionArtifactFactory.getTrustManagers()[0]);

        // For S2S, client authentication is expected (SASL EXTERNAL uses the peer certificate).
        // The default is 'wanted' so that the TLS handshake succeeds even when the remote server
        // does not present a certificate (Server Dialback can be used as a fallback in that case).
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
