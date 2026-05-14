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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicClientCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.net.DNSUtil;
import org.jivesoftware.openfire.net.SrvRecord;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionListener;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.jivesoftware.util.NamedThreadFactory;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;

/**
 * Establishes an outbound QUIC server-to-server (S2S) connection.
 *
 * <h2>DNS discovery</h2>
 * <p>The remote XMPP domain is resolved via a {@code _xmpp-server._quic} SRV DNS lookup
 * (analogous to {@code _xmpp-server._tcp} for plain TCP and {@code _xmpps-server._tcp} for
 * Direct-TLS). If no SRV records are found the connection attempt fails; there is no A/AAAA
 * fallback because the port for QUIC S2S is not standardised and must be advertised via DNS.</p>
 *
 * <h2>TLS</h2>
 * <p>QUIC mandates TLS 1.3. The TLS handshake completes on the {@link QuicChannel} before any
 * XMPP bytes are exchanged. The local server presents its X.509 certificate during the handshake;
 * the remote server's certificate is validated against Openfire's configured trust store.</p>
 *
 * <h2>Stream setup</h2>
 * <p>After the QUIC connection is established a single bidirectional stream is opened. The XMPP
 * opening stream header is sent on that stream and session negotiation proceeds via
 * {@link QuicOutboundConnectionHandler} / {@link org.jivesoftware.openfire.net.RespondingServerStanzaHandler}.</p>
 */
public class QuicSessionInitializer
{
    private static final Logger Log = LoggerFactory.getLogger(QuicSessionInitializer.class);

    private final DomainPair domainPair;
    private final int defaultPort;
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    private EventLoopGroup ioWorkerGroup;
    private EventExecutorGroup blockingHandlerExecutor;
    private volatile Channel datagramChannel;
    private volatile QuicChannel quicChannel;

    public QuicSessionInitializer(final DomainPair domainPair, final int defaultPort)
    {
        this.domainPair = domainPair;
        this.defaultPort = defaultPort;
    }

    /**
     * Resolves {@code _xmpp-server._quic} SRV records for the remote domain and attempts to
     * establish a QUIC connection to each host in priority order.
     *
     * @param listener the connection listener supplying TLS configuration
     * @return a future that completes with the authenticated {@link LocalSession}, or {@code null}
     *         if session establishment failed
     * @throws NetworkEntityUnreachableException if no SRV records were found or all connection
     *         attempts failed
     */
    public Future<LocalSession> init(final ConnectionListener listener)
    {
        final String remoteDomain = domainPair.getRemote();
        Log.debug("Resolving _xmpp-server._quic SRV records for '{}'.", remoteDomain);

        final List<SrvRecord> srvRecords = DNSUtil.srvLookup("xmpp-server", "quic", remoteDomain);
        if (srvRecords.isEmpty()) {
            throw new NetworkEntityUnreachableException(
                "No _xmpp-server._quic SRV records found for '" + remoteDomain + "'.");
        }

        // Prioritise records (lowest priority value first, weight-randomised within a group).
        final List<Set<SrvRecord>> prioritised = SrvRecord.prioritize(srvRecords);

        Exception lastException = null;
        for (final Set<SrvRecord> group : prioritised) {
            for (final SrvRecord record : group) {
                try {
                    Log.debug("Attempting QUIC S2S connection to {}:{} for domain '{}'.",
                        record.getHostname(), record.getPort(), remoteDomain);
                    return connectTo(record.getHostname(), record.getPort(), listener);
                } catch (Exception e) {
                    Log.warn("QUIC S2S connection to {}:{} failed: {}", record.getHostname(), record.getPort(), e.getMessage());
                    lastException = e;
                    stop();
                }
            }
        }

        throw new NetworkEntityUnreachableException(
            "All _xmpp-server._quic hosts for '" + remoteDomain + "' are unreachable.", lastException);
    }

    private Future<LocalSession> connectTo(final String host, final int port,
                                           final ConnectionListener listener) throws Exception
    {
        final ConnectionConfiguration config = listener.generateConnectionConfiguration();
        final EncryptionArtifactFactory encFactory = new EncryptionArtifactFactory(config);

        // Build a QUIC client SSL context with our certificate and the configured trust store.
        final List<String> alpnValues = ConnectionSettings.Server.QUIC_ALPN.getValue();
        final QuicSslContext sslContext = QuicSslContextBuilder
            .forClient()
            .keyManager(encFactory.getKeyManagerFactory(), null)
            .trustManager(encFactory.getTrustManagers()[0])
            .applicationProtocols(alpnValues.toArray(String[]::new))
            .build();

        final long maxIdleTimeoutMs = ConnectionSettings.Server.QUIC_IDLE_TIMEOUT_PROPERTY.getValue().toMillis();

        ioWorkerGroup = new MultiThreadIoEventLoopGroup(
            new NamedThreadFactory("quic_s2s_outbound-io-", null, false, Thread.NORM_PRIORITY),
            NioIoHandler.newFactory()
        );
        final int handlerThreads = Math.max(1, Runtime.getRuntime().availableProcessors()) * 2;
        blockingHandlerExecutor = new DefaultEventExecutorGroup(
            handlerThreads,
            new NamedThreadFactory("quic_s2s_outbound-handler-", null, false, Thread.NORM_PRIORITY)
        );

        // Build the QUIC client codec and bind a local UDP socket.
        final io.netty.channel.ChannelHandler quicClientCodec = new QuicClientCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(maxIdleTimeoutMs, TimeUnit.MILLISECONDS)
            .initialMaxData(10 * 1024 * 1024)
            .initialMaxStreamDataBidirectionalLocal(10 * 1024 * 1024)
            .initialMaxStreamDataBidirectionalRemote(10 * 1024 * 1024)
            .initialMaxStreamsBidirectional(16)
            .initialMaxStreamsUnidirectional(0)
            .build();

        datagramChannel = new Bootstrap()
            .group(ioWorkerGroup)
            .channel(NioDatagramChannel.class)
            .handler(quicClientCodec)
            .bind(0)   // bind to any local port
            .sync()
            .channel();

        // Connect to the remote server and perform the QUIC/TLS handshake.
        quicChannel = QuicChannel.newBootstrap(datagramChannel)
            .streamHandler(new ChannelInitializer<QuicStreamChannel>()
            {
                @Override
                protected void initChannel(final QuicStreamChannel ch)
                {
                    // Server-initiated streams are not expected for S2S; log and close.
                    Log.warn("Unexpected server-initiated QUIC stream from '{}'; closing.", host);
                    ch.close();
                }
            })
            .remoteAddress(new InetSocketAddress(host, port))
            .connect()
            .get();

        Log.debug("QUIC connection to {}:{} established. Opening bidirectional stream.", host, port);

        // Open a client-initiated bidirectional stream for the XMPP session.
        final QuicOutboundConnectionHandler businessLogicHandler =
            new QuicOutboundConnectionHandler(config, domainPair, port);

        final QuicStreamChannel streamChannel = quicChannel.newStreamBootstrap()
            .type(QuicStreamType.BIDIRECTIONAL)
            .handler(new ChannelInitializer<QuicStreamChannel>()
            {
                @Override
                protected void initChannel(final QuicStreamChannel ch)
                {
                    ch.pipeline()
                        .addLast(new NettyXMPPDecoder())
                        .addLast(new StringEncoder(StandardCharsets.UTF_8))
                        .addLast(blockingHandlerExecutor, "businessLogicHandler", businessLogicHandler);
                    ch.config().setAutoRead(false);
                }
            })
            .create()
            .get();

        // Wait for the stanza handler to be ready, then enable reads and send the stream header.
        businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
            streamChannel.eventLoop().execute(() -> {
                streamChannel.config().setAutoRead(true);
                sendOpeningStreamHeader(streamChannel);
            })
        );

        // Free resources when the QUIC connection closes.
        quicChannel.closeFuture().addListener(f -> stop());

        return waitForSession(streamChannel, businessLogicHandler);
    }

    /**
     * Returns a future that completes when the outbound session has been authenticated or all
     * authentication methods have been exhausted.
     */
    private Future<LocalSession> waitForSession(final QuicStreamChannel streamChannel,
                                                final QuicOutboundConnectionHandler handler)
    {
        return handler.getStanzaHandlerFuture()
            .thenCompose(stanzaHandler -> {
                final CompletableFuture<Void> authenticated = stanzaHandler.isSessionAuthenticated();
                final CompletableFuture<Void> exhausted = stanzaHandler.haveAttemptedAllAuthenticationMethods();
                return authenticated.applyToEither(exhausted, ignored -> stanzaHandler.getSession());
            });
    }

    /**
     * Sends the XMPP opening stream header on the given channel.
     */
    private void sendOpeningStreamHeader(final Channel channel)
    {
        Log.debug("Sending XMPP opening stream header to '{}'.", domainPair.getRemote());
        final Element stream = DocumentHelper.createElement(
            QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("", "jabber:server"));
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            stream.add(Namespace.get("db", "jabber:server:dialback"));
        }
        stream.addAttribute("from", domainPair.getLocal());
        stream.addAttribute("to", domainPair.getRemote());
        stream.addAttribute("version", "1.0");

        final String withoutClosing = StringUtils.asUnclosedStream(document);
        Log.trace("Sending: {}", withoutClosing);
        channel.writeAndFlush(withoutClosing);
    }

    /**
     * Closes the QUIC connection and releases all associated resources.
     */
    public void stop()
    {
        if (!isStopped.compareAndSet(false, true)) {
            return;
        }
        if (quicChannel != null) {
            final NettyConnection connection = quicChannel.attr(CONNECTION).get();
            if (connection != null) {
                connection.close();
            }
            quicChannel.close();
        }
        if (datagramChannel != null) {
            datagramChannel.close();
        }
        if (ioWorkerGroup != null && !ioWorkerGroup.isShuttingDown()) {
            ioWorkerGroup.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS);
        }
        if (blockingHandlerExecutor != null && !blockingHandlerExecutor.isShuttingDown()) {
            blockingHandlerExecutor.shutdownGracefully(250, 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public String toString()
    {
        return "QuicSessionInitializer{domainPair=" + domainPair + ", defaultPort=" + defaultPort + '}';
    }
}
