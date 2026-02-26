/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.dom4j.*;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.net.SocketUtil;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionAcceptor;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.NettyConnectionAcceptor;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;


/**
 * Initialises an outgoing netty channel for outbound S2S
 */
public class NettySessionInitializer {

    /**
     * The inactivity duration after which a Netty executor can be shutdown gracefully.
     */
    public static final SystemProperty<Duration> GRACEFUL_SHUTDOWN_QUIET_PERIOD = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.socket.netty.graceful-shutdown.quiet-period")
        .setDefaultValue(Duration.ofSeconds(2))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(false)
        .build();

    /**
     * The maximum amount of time to wait until a Netty executor is shutdown regardless if a task was submitted during the quiet period.
     */
    public static final SystemProperty<Duration> GRACEFUL_SHUTDOWN_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.socket.netty.graceful-shutdown.timeout")
        .setDefaultValue(Duration.ofSeconds(15))
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDynamic(false)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(NettySessionInitializer.class);
    private final DomainPair domainPair;
    private final int port;
    private boolean directTLS = false;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    /**
     * EventLoopGroup responsible for non-blocking I/O on established connections.
     *
     * This group (often referred to as the "worker" group) handles read/write operations and propagates I/O events
     * through the Netty pipeline for each accepted connection. Handlers running on this group must remain strictly
     * non-blocking to avoid starving the event loop.
     */
    private final EventLoopGroup ioWorkerGroup;

    /**
     * Executor group for pipeline handlers that may perform blocking or long-running operations.
     *
     * This executor group is shared across all channels created by this acceptor and is used to offload work such as
     * authentication, routing, persistence, or calls into legacy/blocking APIs. Using this group ensures that Netty
     * EventLoop threads remain responsive and dedicated to I/O.
     */
    private final EventExecutorGroup blockingHandlerExecutor;

    private Channel channel;

    public NettySessionInitializer(DomainPair domainPair, int port) {
        this.domainPair = domainPair;
        this.port = port;

        // TODO The event loop and executor should be re-used for all instances of this class, rather than each instance creating their own instance. OF-3182
        final String name = "socket_s2s_outbound" + (/*this.directTLS*/ port == 5223 ? "_ssl" : "");

        final ThreadFactory ioWorkerGroupThreadFactory = new NamedThreadFactory(name + "-worker-" + domainPair.getRemote() + "-", null, false, Thread.NORM_PRIORITY);
        this.ioWorkerGroup = new NioEventLoopGroup(ioWorkerGroupThreadFactory);

        final ThreadFactory blockingHandlerGroupThreadFactory = new NamedThreadFactory(name + "-handler-" + domainPair.getRemote() + "-", null, false, Thread.NORM_PRIORITY);
        this.blockingHandlerExecutor = new DefaultEventExecutorGroup(Math.max(1, Runtime.getRuntime().availableProcessors()) * 2, blockingHandlerGroupThreadFactory);
    }

    public Future<LocalSession> init(ConnectionListener listener) {
        // Connect to remote server using XMPP 1.0 (TLS + SASL EXTERNAL or TLS + server dialback or server dialback)
        Log.debug( "Creating plain socket connection to a host that belongs to the remote XMPP domain." );
        final Map.Entry<Socket, Boolean> socketToXmppDomain = SocketUtil.createSocketToXmppDomain(domainPair.getRemote(), port );

        if ( socketToXmppDomain == null ) {
            throw new NetworkEntityUnreachableException("Cannot establish connection to any host in the XMPP domain '" + domainPair.getRemote() + "'.");
        }
        Socket socket = socketToXmppDomain.getKey();
        this.directTLS = socketToXmppDomain.getValue();

        final SocketAddress socketAddress = socket.getRemoteSocketAddress();
        try {
            //TODO: Finish https://igniterealtime.atlassian.net/browse/OF-2721 by removing the need for an extraneous extra socket at all.
            socket.close();
        } catch (IOException e) {
            Log.warn("Unable to close socket to remote address: {}", socketAddress, e);
        }

        Log.debug( "Opening a new connection to {} {}.", socketAddress, directTLS ? "using directTLS" : "that is initially not encrypted" );

        try {
            Bootstrap b = new Bootstrap();
            b.group(ioWorkerGroup);
            b.channel(NioSocketChannel.class);
            b.option(ChannelOption.SO_KEEPALIVE, true);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    // Disable auto-read to prevent incoming data before pipeline is ready (which leads to race-conditions, sometimes preventing data from a new socket from being processed).
                    ch.config().setAutoRead(false);

                    NettyConnectionHandler businessLogicHandler = new NettyOutboundConnectionHandler(listener.generateConnectionConfiguration(), domainPair, port);
                    Duration maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime().isNegative() ? Duration.ZERO : businessLogicHandler.getMaxIdleTime();

                    ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS));
                    ch.pipeline().addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(false));
                    ch.pipeline().addLast(new NettyXMPPDecoder());
                    ch.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                    ch.pipeline().addLast(blockingHandlerExecutor, businessLogicHandler);

                    final ConnectionAcceptor connectionAcceptor = listener.getConnectionAcceptor();
                    if (connectionAcceptor instanceof NettyConnectionAcceptor) {
                        ((NettyConnectionAcceptor) connectionAcceptor).getChannelHandlerFactories().forEach(factory -> {
                            try {
                                factory.addNewHandlerTo(ch.pipeline(), blockingHandlerExecutor);
                            } catch (Throwable t) {
                                Log.warn("Unable to add ChannelHandler from '{}' to pipeline of new channel: {}", factory, ch, t);
                            }
                        });
                    }

                    // Re-enable autoRead after the channel is fully registered.
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRegistered(ChannelHandlerContext ctx) {
                            // Schedule enabling auto-read on the blocking executor to ensure pipeline is fully ready.
                            blockingHandlerExecutor.execute(() -> ctx.channel().config().setAutoRead(true));
                            ctx.fireChannelRegistered();
                        }
                    });

                    if (directTLS) {
                        ch.attr(CONNECTION).get().startTLS(true, true);
                    }
                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    super.exceptionCaught(ctx, cause);
                    if (exceptionOccurredForDirectTLS(cause)) {
                        if (directTLS &&
                            JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ON_PLAIN_DETECTION_ALLOW_NONDIRECTTLS_FALLBACK, true) &&
                            cause.getMessage().contains("plaintext connection?")
                        ) {
                            Log.warn("Plaintext detected on a new connection that is was started in DirectTLS mode (socket address: {}). Attempting to restart the connection in non-DirectTLS mode.", domainPair.getRemote());
                            directTLS = false;
                            Log.info("Re-establishing connection to {}. Proceeding without directTLS.", domainPair.getRemote());
                            init(listener);
                        }
                    }
                }

                public boolean exceptionOccurredForDirectTLS(Throwable cause) {
                    return cause instanceof SSLException;
                }
            });

            this.channel = b.connect(socketAddress).sync().channel();

            // Make sure we free up resources (worker group NioEventLoopGroup) when the channel is closed
            this.channel.closeFuture().addListener(future -> stop());

            // When using directTLS a Netty SSLHandler is added to the pipeline from instantiation. This initiates the TLS handshake, and as such we do not need to send an opening stream element.
            // The opening stream element will be sent by the StanzaHandler once TLS has been negotiated.
            if (!directTLS) {
                // Start the session negotiation for startTLS
                sendOpeningStreamHeader(channel);
            }

            return waitForSession(channel);
        } catch (InterruptedException e) {
            Log.error("Error establishing Netty client session", e);
            stop();
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        if (!isStopped.compareAndSet(false, true)) {
            return; // Guard against closing more than once (OF-2673).
        }
        if (channel != null) {
            // Close connection to allow its event handlers to clean up routing table (OF-2674).
            final NettyConnection connection = channel.attr(CONNECTION).get();
            if (connection != null) {
                connection.close();
            }
            channel.close();
        }
        ioWorkerGroup.shutdownGracefully(GRACEFUL_SHUTDOWN_QUIET_PERIOD.getValue().toMillis(), GRACEFUL_SHUTDOWN_TIMEOUT.getValue().toMillis(), TimeUnit.MILLISECONDS);
        blockingHandlerExecutor.shutdownGracefully(GRACEFUL_SHUTDOWN_QUIET_PERIOD.getValue().toMillis(), GRACEFUL_SHUTDOWN_TIMEOUT.getValue().toMillis(), TimeUnit.MILLISECONDS);
    }

    private Future<LocalSession> waitForSession(Channel channel) {
        RespondingServerStanzaHandler stanzaHandler = (RespondingServerStanzaHandler) channel.attr(NettyConnectionHandler.HANDLER).get();
        return CompletableFuture.anyOf(stanzaHandler.isSessionAuthenticated(), stanzaHandler.haveAttemptedAllAuthenticationMethods())
            .thenApply(o -> stanzaHandler.getSession());
    }

    private void sendOpeningStreamHeader(Channel channel) {
        Log.debug("Send the stream header and wait for response...");
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("", "jabber:server"));
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            stream.add(Namespace.get("db", "jabber:server:dialback"));
        }
        stream.addAttribute("from", domainPair.getLocal()); // OF-673
        stream.addAttribute("to", domainPair.getRemote());
        stream.addAttribute("version", "1.0");

        final String withoutClosing = StringUtils.asUnclosedStream(document);

        Log.trace("Sending: {}", withoutClosing);
        channel.writeAndFlush(withoutClosing);
    }

    @Override
    public String toString()
    {
        return "NettySessionInitializer{" +
            "domainPair=" + domainPair +
            ", port=" + port +
            ", directTLS=" + directTLS +
            ", workerGroup=" + ioWorkerGroup +
            ", channel=" + channel +
            '}';
    }
}
