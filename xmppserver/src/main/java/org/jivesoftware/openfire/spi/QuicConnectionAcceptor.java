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
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.NettyIdleStateKeepAliveHandler;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.openfire.nio.NewConnectionRateLimitHandler;
import org.jivesoftware.openfire.nio.QuicClientConnectionHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
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

    private final EventLoopGroup ioEventLoopGroup;
    private final EventExecutorGroup blockingHandlerExecutor;
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

            final QuicServerCodecBuilder serverCodecBuilder = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .maxIdleTimeout(ConnectionSettings.Client.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis(), TimeUnit.MILLISECONDS)
                .initialMaxData(10 * 1024 * 1024)
                .initialMaxStreamDataBidirectionalLocal(10 * 1024 * 1024)
                .initialMaxStreamDataBidirectionalRemote(10 * 1024 * 1024)
                .initialMaxStreamsBidirectional(ConnectionSettings.Client.QUIC_MAX_STREAMS.getValue())
                .handler(new NewConnectionRateLimitHandler(ConnectionType.QUIC_C2S))
                .streamHandler(new ChannelInitializer<QuicStreamChannel>()
                {
                    @Override
                    protected void initChannel(final QuicStreamChannel channel)
                    {
                        if (channel.type() != QuicStreamType.BIDIRECTIONAL) {
                            channel.close();
                            return;
                        }

                        final QuicClientConnectionHandler businessLogicHandler = new QuicClientConnectionHandler(configuration);
                        final Duration maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime().isNegative()
                            ? Duration.ZERO
                            : businessLogicHandler.getMaxIdleTime();

                        channel.pipeline()
                            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
                            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS))
                            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(true))
                            .addLast(new NettyXMPPDecoder())
                            .addLast(new StringEncoder(StandardCharsets.UTF_8))
                            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
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
        builder.applicationProtocols("xmpp");
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
