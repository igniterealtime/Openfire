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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.QuicConnectionAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.IDLE_FLAG;
import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * Tracks all QUIC streams associated with one QUIC connection and provides deterministic outbound stream selection.
 */
public class QuicSessionStreamRouter
{
    private static final Logger Log = LoggerFactory.getLogger(QuicSessionStreamRouter.class);
    private static final AttributeKey<QuicSessionStreamRouter> ROUTER_KEY = AttributeKey.valueOf("OF-QUIC-STREAM-ROUTER");

    private final QuicChannel quicChannel;
    private final ConnectionConfiguration configuration;
    private final EventExecutorGroup blockingHandlerExecutor;
    private final List<NettyConnection> inboundConnections = new CopyOnWriteArrayList<>();
    private final List<QuicStreamChannel> outboundChannels = new CopyOnWriteArrayList<>();
    private final Map<String, QuicStreamChannel> streamAssignmentsByFromBareJid = new HashMap<>();
    /** Stanzas queued while a new outbound stream is being opened asynchronously. */
    private final Deque<String> pendingStanzas = new ArrayDeque<>();

    private volatile LocalClientSession session;
    private volatile NettyConnection primaryInboundConnection;
    private int nextNonPrimaryStreamIndex = 0;
    /** True while an async stream-open is in flight; prevents opening multiple streams concurrently. */
    private boolean streamOpenInFlight = false;

    private QuicSessionStreamRouter(final QuicChannel quicChannel, final ConnectionConfiguration configuration, final EventExecutorGroup blockingHandlerExecutor)
    {
        this.quicChannel = quicChannel;
        this.configuration = configuration;
        this.blockingHandlerExecutor = blockingHandlerExecutor;
    }

    public static QuicSessionStreamRouter getOrCreate(final Channel channel, final ConnectionConfiguration configuration, final EventExecutorGroup blockingHandlerExecutor)
    {
        final QuicChannel quicChannel = findOwningQuicChannel(channel);
        if (quicChannel == null) {
            return null;
        }
        synchronized (quicChannel) {
            QuicSessionStreamRouter router = quicChannel.attr(ROUTER_KEY).get();
            if (router == null) {
                router = new QuicSessionStreamRouter(quicChannel, configuration, blockingHandlerExecutor);
                quicChannel.attr(ROUTER_KEY).set(router);
            }
            return router;
        }
    }

    public static QuicSessionStreamRouter find(final Channel channel)
    {
        final QuicChannel quicChannel = findOwningQuicChannel(channel);
        return quicChannel == null ? null : quicChannel.attr(ROUTER_KEY).get();
    }

    private static QuicChannel findOwningQuicChannel(final Channel channel)
    {
        for (Channel cursor = channel; cursor != null; cursor = cursor.parent()) {
            if (cursor instanceof QuicChannel quicChannel) {
                return quicChannel;
            }
        }
        return null;
    }

    public synchronized void registerInboundConnection(final NettyConnection connection)
    {
        inboundConnections.add(connection);
    }

    public synchronized LocalClientSession getSession()
    {
        return session;
    }

    public synchronized void bindPrimarySession(final LocalClientSession session, final NettyConnection primaryConnection)
    {
        if (this.session == null) {
            this.session = session;
            this.primaryInboundConnection = primaryConnection;
        }
    }

    public synchronized boolean isPrimaryConnection(final NettyConnection connection)
    {
        return primaryInboundConnection == connection;
    }

    public synchronized boolean shouldCloseSessionOnStreamClose(final NettyConnection connection)
    {
        inboundConnections.remove(connection);
        if (connection.getChannel() instanceof QuicStreamChannel streamChannel) {
            streamAssignmentsByFromBareJid.entrySet().removeIf(entry -> entry.getValue() == streamChannel);
        }
        return isPrimaryConnection(connection);
    }

    public synchronized boolean writeStanza(final Packet packet, final String serializedStanza)
    {
        final QuicStreamChannel stream = selectOutboundStream(packet, serializedStanza);
        if (stream == null) {
            // Stanza has been queued for delivery once the async stream open completes.
            return true;
        }
        if (!stream.isActive()) {
            return false;
        }

        stream.writeAndFlush(serializedStanza);
        return true;
    }

    /**
     * Selects the outbound stream for the given packet. Returns null if a new stream is being opened
     * asynchronously and the stanza has been queued for later delivery.
     */
    private QuicStreamChannel selectOutboundStream(final Packet packet, final String serializedStanza)
    {
        final QuicStreamChannel primaryStream = getPrimaryStream();
        if (primaryStream == null) {
            return null;
        }

        final String fromBareJid = determineBareFrom(packet);
        if (shouldUsePrimaryStream(fromBareJid)) {
            return primaryStream;
        }

        final QuicStreamChannel assigned = streamAssignmentsByFromBareJid.get(fromBareJid);
        if (assigned != null && assigned.isActive()) {
            return assigned;
        }

        return chooseNonPrimaryStream(primaryStream, fromBareJid, serializedStanza);
    }

    /**
     * Chooses or opens a non-primary stream. Returns null (and queues the stanza) when an async
     * stream open has been initiated.
     */
    private QuicStreamChannel chooseNonPrimaryStream(final QuicStreamChannel primaryStream, final String fromBareJid, final String serializedStanza)
    {
        // If a stream open is already in flight, queue and wait.
        if (streamOpenInFlight) {
            pendingStanzas.add(serializedStanza);
            return null;
        }

        final List<QuicStreamChannel> candidates = activeNonPrimaryStreams(primaryStream);
        if (!candidates.isEmpty()) {
            final QuicStreamChannel selected = chooseRoundRobin(candidates);
            streamAssignmentsByFromBareJid.put(fromBareJid, selected);
            return selected;
        }

        // Try to open a new stream asynchronously.
        final boolean opening = openOutboundStreamAsync(fromBareJid);
        if (opening) {
            pendingStanzas.add(serializedStanza);
            return null;
        }

        // Could not open a new stream; fall back to primary.
        return primaryStream;
    }

    private List<QuicStreamChannel> activeNonPrimaryStreams(final QuicStreamChannel primaryStream)
    {
        final List<QuicStreamChannel> streams = new ArrayList<>();

        final List<QuicStreamChannel> inbound = inboundConnections.stream()
            .map(NettyConnection::getChannel)
            .filter(QuicStreamChannel.class::isInstance)
            .map(QuicStreamChannel.class::cast)
            .filter(Channel::isActive)
            .filter(stream -> stream != primaryStream)
            .collect(Collectors.toList());
        streams.addAll(inbound);

        for (final QuicStreamChannel stream : outboundChannels) {
            if (stream.isActive()) {
                streams.add(stream);
            }
        }

        return streams;
    }

    private QuicStreamChannel chooseRoundRobin(final List<QuicStreamChannel> candidates)
    {
        final int index = Math.floorMod(nextNonPrimaryStreamIndex, candidates.size());
        nextNonPrimaryStreamIndex++;
        return candidates.get(index);
    }

    /**
     * Initiates an async open of a new server-initiated bidirectional QUIC stream with a full inbound+outbound
     * pipeline. Returns true if the open was initiated (stanza should be queued), false if it could not be started.
     * Must be called while holding {@code this} monitor.
     */
    private boolean openOutboundStreamAsync(final String fromBareJid)
    {
        final int maxOutboundStreams = ConnectionSettings.Client.QUIC_MAX_OUTBOUND_STREAMS.getValue();
        if (maxOutboundStreams <= 0 || outboundChannels.size() >= maxOutboundStreams) {
            return false;
        }
        if (quicChannel.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL) <= 0) {
            return false;
        }

        streamOpenInFlight = true;

        final QuicSessionStreamRouter self = this;
        quicChannel.newStreamBootstrap()
            .type(QuicStreamType.BIDIRECTIONAL)
            .handler(new ChannelInitializer<QuicStreamChannel>()
            {
                @Override
                protected void initChannel(final QuicStreamChannel channel)
                {
                    final QuicClientConnectionHandler businessLogicHandler = new QuicClientConnectionHandler(configuration, self);
                    final Duration maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime().isNegative()
                        ? Duration.ZERO
                        : businessLogicHandler.getMaxIdleTime();

                    channel.pipeline()
                        .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
                        .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS))
                        .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(true))
                        .addLast(new NettyXMPPDecoder())
                        .addLast(new StringEncoder(StandardCharsets.UTF_8))
                        .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(QuicConnectionAcceptor.WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
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
            })
            .create()
            .addListener((Future<QuicStreamChannel> future) -> {
                synchronized (self) {
                    streamOpenInFlight = false;
                    if (!future.isSuccess()) {
                        Log.debug("Unable to open an outbound QUIC stream for session multiplexing.", future.cause());
                        // Drain pending stanzas to primary stream as fallback.
                        final QuicStreamChannel primary = getPrimaryStream();
                        if (primary != null && primary.isActive()) {
                            String stanza;
                            while ((stanza = pendingStanzas.poll()) != null) {
                                primary.writeAndFlush(stanza);
                            }
                        } else {
                            pendingStanzas.clear();
                        }
                        return;
                    }

                    final QuicStreamChannel streamChannel = future.getNow();
                    if (streamChannel == null) {
                        pendingStanzas.clear();
                        return;
                    }

                    streamChannel.closeFuture().addListener(ignored -> removeClosedOutboundChannel(streamChannel));
                    outboundChannels.add(streamChannel);
                    streamAssignmentsByFromBareJid.put(fromBareJid, streamChannel);

                    // Flush queued stanzas onto the new stream.
                    String stanza;
                    while ((stanza = pendingStanzas.poll()) != null) {
                        streamChannel.writeAndFlush(stanza);
                    }
                }
            });

        return true;
    }

    private synchronized void removeClosedOutboundChannel(final QuicStreamChannel streamChannel)
    {
        outboundChannels.remove(streamChannel);
        streamAssignmentsByFromBareJid.entrySet().removeIf(entry -> entry.getValue() == streamChannel);
    }

    private QuicStreamChannel getPrimaryStream()
    {
        final NettyConnection primary = primaryInboundConnection;
        if (primary == null || !(primary.getChannel() instanceof QuicStreamChannel stream)) {
            return null;
        }
        return stream.isActive() ? stream : null;
    }

    private String determineBareFrom(final Packet packet)
    {
        final JID from = packet.getFrom();
        if (from == null) {
            return "";
        }
        return from.asBareJID().toString();
    }

    private boolean shouldUsePrimaryStream(final String fromBareJid)
    {
        if (fromBareJid == null || fromBareJid.isEmpty()) {
            return true;
        }

        final LocalClientSession localSession = session;
        if (localSession == null) {
            return true;
        }

        final JID accountJid = localSession.getAddress();
        if (accountJid != null && Objects.equals(fromBareJid, accountJid.asBareJID().toString())) {
            return true;
        }

        return Objects.equals(fromBareJid, new JID(null, localSession.getServerName(), null, true).toString());
    }
}
