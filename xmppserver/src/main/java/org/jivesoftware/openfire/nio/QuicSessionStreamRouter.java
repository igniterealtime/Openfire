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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.QuicConnectionAcceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tracks all QUIC streams associated with one QUIC connection and provides deterministic outbound stream selection.
 */
public class QuicSessionStreamRouter
{
    private static final Logger Log = LoggerFactory.getLogger(QuicSessionStreamRouter.class);
    private static final AttributeKey<QuicSessionStreamRouter> ROUTER_KEY = AttributeKey.valueOf("OF-QUIC-STREAM-ROUTER");

    private final QuicChannel quicChannel;
    private final List<NettyConnection> inboundConnections = new CopyOnWriteArrayList<>();
    private final List<QuicStreamChannel> outboundChannels = new CopyOnWriteArrayList<>();
    private final Map<String, QuicStreamChannel> streamAssignmentsByFromBareJid = new HashMap<>();

    private volatile LocalClientSession session;
    private volatile NettyConnection primaryInboundConnection;
    private int nextNonPrimaryStreamIndex = 0;

    private QuicSessionStreamRouter(final QuicChannel quicChannel)
    {
        this.quicChannel = quicChannel;
    }

    public static QuicSessionStreamRouter getOrCreate(final Channel channel)
    {
        final QuicChannel quicChannel = findOwningQuicChannel(channel);
        if (quicChannel == null) {
            return null;
        }
        synchronized (quicChannel) {
            QuicSessionStreamRouter router = quicChannel.attr(ROUTER_KEY).get();
            if (router == null) {
                router = new QuicSessionStreamRouter(quicChannel);
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
        final QuicStreamChannel stream = selectOutboundStream(packet);
        if (stream == null || !stream.isActive()) {
            return false;
        }

        stream.writeAndFlush(serializedStanza);
        return true;
    }

    private QuicStreamChannel selectOutboundStream(final Packet packet)
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

        final QuicStreamChannel selected = chooseNonPrimaryStream(primaryStream);
        streamAssignmentsByFromBareJid.put(fromBareJid, selected);
        return selected;
    }

    private QuicStreamChannel chooseNonPrimaryStream(final QuicStreamChannel primaryStream)
    {
        final QuicStreamChannel newlyOpened = openOutboundStream();
        if (newlyOpened != null) {
            return newlyOpened;
        }

        final List<QuicStreamChannel> candidates = activeNonPrimaryStreams(primaryStream);
        if (!candidates.isEmpty()) {
            return chooseRoundRobin(candidates);
        }

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

    private QuicStreamChannel openOutboundStream()
    {
        final int maxOutboundStreams = ConnectionSettings.Client.QUIC_MAX_OUTBOUND_STREAMS.getValue();
        if (maxOutboundStreams <= 0 || outboundChannels.size() >= maxOutboundStreams) {
            return null;
        }
        if (quicChannel.peerAllowedStreams(QuicStreamType.BIDIRECTIONAL) <= 0) {
            return null;
        }

        final Future<QuicStreamChannel> openResult = quicChannel.newStreamBootstrap()
            .type(QuicStreamType.BIDIRECTIONAL)
            .handler(new ChannelInitializer<QuicStreamChannel>()
            {
                @Override
                protected void initChannel(final QuicStreamChannel channel)
                {
                    final ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                    pipeline.addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(QuicConnectionAcceptor.WRITE_TIMEOUT_SECONDS.getValue().getSeconds())));
                }
            })
            .create();
        openResult.awaitUninterruptibly(250, TimeUnit.MILLISECONDS);

        if (!openResult.isSuccess()) {
            Log.debug("Unable to open an outbound QUIC stream for session multiplexing.", openResult.cause());
            return null;
        }

        final QuicStreamChannel streamChannel = openResult.getNow();
        if (streamChannel == null) {
            return null;
        }

        streamChannel.closeFuture().addListener(ignored -> removeClosedOutboundChannel(streamChannel));
        outboundChannels.add(streamChannel);
        return streamChannel;
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
