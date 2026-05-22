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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final List<NettyConnection> inboundConnections = new ArrayList<>();
    private final List<QuicStreamChannel> outboundChannels = new ArrayList<>();
    private final Map<String, QuicStreamChannel> streamAssignmentsByFromBareJid = new HashMap<>();
    /** Stanzas queued while a new outbound stream is being opened asynchronously. */
    private final Deque<String> pendingStanzas = new ArrayDeque<>();

    private volatile LocalClientSession session;
    private volatile NettyConnection primaryInboundConnection;
    private int nextNonPrimaryStreamIndex = 0;
    /** True while an async stream-open is in flight; prevents opening multiple streams concurrently. */
    private boolean streamOpenInFlight = false;
    /**
     * The stream ID of the WebTransport CONNECT stream, or -1 if this is not a WebTransport session.
     * Set by {@link #markWebTransportSessionEstablished(long)} when the CONNECT handshake completes.
     */
    private long webTransportSessionId = -1;

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

    /**
     * Called by {@link WebTransportConnectionHandler} when the HTTP/3 CONNECT handshake completes
     * successfully. Records the session ID (= CONNECT stream ID) so that data streams can be
     * associated with this WebTransport session.
     *
     * @param connectStreamId the QUIC stream ID of the CONNECT stream (the WebTransport session ID)
     */
    public synchronized void markWebTransportSessionEstablished(final long connectStreamId)
    {
        this.webTransportSessionId = connectStreamId;
        Log.debug("WebTransport session established on QUIC connection {}; session ID (CONNECT stream) = {}.",
            quicChannel.id().asShortText(), connectStreamId);
    }

    /**
     * Returns the WebTransport session ID (= CONNECT stream ID), or -1 if this is not a
     * WebTransport session or the CONNECT handshake has not yet completed.
     */
    public synchronized long getWebTransportSessionId()
    {
        return webTransportSessionId;
    }

    /**
     * Returns true if this router is associated with a WebTransport session (i.e. the CONNECT
     * handshake has completed).
     */
    public synchronized boolean isWebTransportSession()
    {
        return webTransportSessionId >= 0;
    }

    public synchronized void registerInboundConnection(final NettyConnection connection)
    {
        inboundConnections.add(connection);
    }

    public synchronized LocalClientSession getSession()
    {
        return session;
    }

    /**
     * Returns the underlying QUIC connection. Used by the admin UI to surface link-quality stats.
     */
    public QuicChannel getQuicChannel()
    {
        return quicChannel;
    }

    /**
     * Number of client-initiated (inbound) QUIC streams currently associated with this session.
     */
    public synchronized int getInboundStreamCount()
    {
        return inboundConnections.size();
    }

    /**
     * Number of server-initiated (outbound) QUIC streams currently associated with this session.
     */
    public synchronized int getOutboundStreamCount()
    {
        return outboundChannels.size();
    }

    /**
     * Number of distinct remote bare-JIDs currently mapped to a non-primary stream.
     */
    public synchronized int getActiveStreamAssignmentCount()
    {
        return streamAssignmentsByFromBareJid.size();
    }

    /**
     * Returns the QUIC stream id of the primary (control) stream, or -1 if none has been bound yet.
     */
    public synchronized long getPrimaryStreamId()
    {
        if (primaryInboundConnection == null) {
            return -1;
        }
        if (primaryInboundConnection.getChannel() instanceof QuicStreamChannel streamChannel) {
            return streamChannel.streamId();
        }
        return -1;
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
     *
     * <p>Selection policy (in order):</p>
     * <ol>
     *   <li>Prefer an <em>unallocated</em> client-initiated (inbound) stream — these were already
     *       opened by the client and cost nothing to reuse, so we should consume them before
     *       paying the cost (and stream-credit budget) of a server-initiated open.</li>
     *   <li>Otherwise prefer an unallocated server-initiated (outbound) stream we previously opened.</li>
     *   <li>Otherwise try to open a new server-initiated stream asynchronously.</li>
     *   <li>Otherwise reuse an already-allocated non-primary stream round-robin.</li>
     *   <li>As a last resort, fall back to the primary stream.</li>
     * </ol>
     */
    private QuicStreamChannel chooseNonPrimaryStream(final QuicStreamChannel primaryStream, final String fromBareJid, final String serializedStanza)
    {
        // If a stream open is already in flight, queue and wait.
        if (streamOpenInFlight) {
            pendingStanzas.add(serializedStanza);
            return null;
        }

        // 1 + 2: prefer any currently-unallocated non-primary stream (inbound first, then outbound).
        final QuicStreamChannel unallocated = pickUnallocatedNonPrimaryStream(primaryStream);
        if (unallocated != null) {
            streamAssignmentsByFromBareJid.put(fromBareJid, unallocated);
            return unallocated;
        }

        // 3: try to open a new server-initiated stream.
        final boolean opening = openOutboundStreamAsync(fromBareJid);
        if (opening) {
            pendingStanzas.add(serializedStanza);
            return null;
        }

        // 4: reuse an already-allocated non-primary stream round-robin.
        final List<QuicStreamChannel> candidates = activeNonPrimaryStreams(primaryStream);
        if (!candidates.isEmpty()) {
            final QuicStreamChannel selected = chooseRoundRobin(candidates);
            streamAssignmentsByFromBareJid.put(fromBareJid, selected);
            return selected;
        }

        // 5: fall back to primary.
        return primaryStream;
    }

    /**
     * Returns an active non-primary stream that has no current {@link #streamAssignmentsByFromBareJid}
     * mapping pointing at it, or {@code null} if every active non-primary stream is already in use.
     * Client-initiated (inbound) streams are preferred over server-initiated (outbound) streams.
     */
    private QuicStreamChannel pickUnallocatedNonPrimaryStream(final QuicStreamChannel primaryStream)
    {
        final Set<QuicStreamChannel> assigned = new HashSet<>(streamAssignmentsByFromBareJid.values());

        // Inbound (client-initiated) first.
        for (final NettyConnection connection : inboundConnections) {
            final Channel channel = connection.getChannel();
            if (!(channel instanceof QuicStreamChannel stream)) {
                continue;
            }
            if (stream == primaryStream || !stream.isActive()) {
                continue;
            }
            if (!assigned.contains(stream)) {
                return stream;
            }
        }

        // Then outbound (server-initiated).
        for (final QuicStreamChannel stream : outboundChannels) {
            if (!stream.isActive()) {
                continue;
            }
            if (!assigned.contains(stream)) {
                return stream;
            }
        }

        return null;
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
                        // No WriteTimeoutHandler on QUIC streams: QUIC liveness is the transport's
                        // max_idle_timeout; an app-level write timeout would disconnect slow but
                        // healthy clients. See QuicConnectionAcceptor for the matching rationale.
                        .addLast(new ChannelInboundHandlerAdapter()
                        {
                            @Override
                            public void channelActive(final ChannelHandlerContext ctx) throws Exception
                            {
                                // For WebTransport sessions, server-initiated bidirectional streams
                                // MUST begin with the WEBTRANSPORT_STREAM signal (0x41) followed by
                                // the session ID (varint) before any application data (draft-ietf-webtrans-http3 §4.2).
                                if (self.isWebTransportSession()) {
                                    final long sessionId = self.getWebTransportSessionId();
                                    final io.netty.buffer.ByteBuf prefix = ctx.alloc().buffer(10);
                                    prefix.writeByte(0x41); // WEBTRANSPORT_STREAM signal value
                                    writeVarInt(prefix, sessionId);
                                    ctx.channel().write(prefix);
                                    Log.debug("WebTransport outbound stream {}: wrote 0x41 + session_id={} prefix.",
                                        channel.streamId(), sessionId);
                                }
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

    /**
     * Writes a QUIC variable-length integer (RFC 9000 §16) into {@code buf}.
     */
    private static void writeVarInt(final ByteBuf buf, final long value)
    {
        if (value <= 0x3F) {
            buf.writeByte((int) value);
        } else if (value <= 0x3FFF) {
            buf.writeByte((int) (0x40 | (value >> 8)));
            buf.writeByte((int) (value & 0xFF));
        } else if (value <= 0x3FFFFFFF) {
            buf.writeByte((int) (0x80 | (value >> 24)));
            buf.writeByte((int) ((value >> 16) & 0xFF));
            buf.writeByte((int) ((value >> 8) & 0xFF));
            buf.writeByte((int) (value & 0xFF));
        } else {
            buf.writeByte((int) (0xC0 | (value >> 56)));
            buf.writeByte((int) ((value >> 48) & 0xFF));
            buf.writeByte((int) ((value >> 40) & 0xFF));
            buf.writeByte((int) ((value >> 32) & 0xFF));
            buf.writeByte((int) ((value >> 24) & 0xFF));
            buf.writeByte((int) ((value >> 16) & 0xFF));
            buf.writeByte((int) ((value >> 8) & 0xFF));
            buf.writeByte((int) (value & 0xFF));
        }
    }

    private synchronized void removeClosedOutboundChannel(final QuicStreamChannel streamChannel)
    {
        outboundChannels.remove(streamChannel);
        streamAssignmentsByFromBareJid.entrySet().removeIf(entry -> entry.getValue() == streamChannel);
    }

    /**
     * Returns the primary (stream id 0) QuicStreamChannel for this connection, or {@code null}
     * if no primary stream is currently active. Per XEP-0467 §3.2 top-level non-stanza elements
     * (stream errors, CSI toggles, &lt;proceed/&gt;-style framing, etc.) MUST be sent on stream id 0
     * only; callers that need to deliver such elements should route them through this stream.
     */
    public QuicStreamChannel getPrimaryStream()
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
