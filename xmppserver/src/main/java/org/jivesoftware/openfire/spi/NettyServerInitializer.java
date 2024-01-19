/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.nio.*;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;

/**
 * Creates a newly configured {@link ChannelPipeline} for a new channel.
 */
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger Log = LoggerFactory.getLogger(NettyServerInitializer.class);

    /**
     * Controls the write timeout time in seconds to handle stalled sessions and prevent DoS
     */
    public static final SystemProperty<Duration> WRITE_TIMEOUT_SECONDS = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.socket.write-timeout-seconds")
        .setDefaultValue(Duration.ofSeconds(30))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();

    public static final String TRAFFIC_HANDLER_NAME = "trafficShapingHandler";
    private final ChannelGroup allChannels; // This is a collection that is managed by the invoking entity.
    private final ConnectionConfiguration configuration;
    private final Set<NettyChannelHandlerFactory> channelHandlerFactories; // This is a collection that is managed by the invoking entity.

    public NettyServerInitializer(ConnectionConfiguration configuration, ChannelGroup allChannels, Set<NettyChannelHandlerFactory> channelHandlerFactories) {
        this.allChannels = allChannels;
        this.configuration = configuration;
        this.channelHandlerFactories = channelHandlerFactories;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {

        boolean isClientConnection = configuration.getType() == ConnectionType.SOCKET_C2S;

        NettyConnectionHandler businessLogicHandler = NettyConnectionHandlerFactory.createConnectionHandler(configuration);
        Duration maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime().isNegative() ? Duration.ZERO : businessLogicHandler.getMaxIdleTime();

        ch.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing.dividedBy(2).toMillis(), 0, 0, TimeUnit.MILLISECONDS))
            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(isClientConnection))
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
            .addLast(businessLogicHandler);

        // Add ChannelHandler providers implemented by plugins, if any.
        channelHandlerFactories.forEach(factory -> {
            try {
                factory.addNewHandlerTo(ch.pipeline());
            } catch (Throwable t) {
                Log.warn("Unable to add ChannelHandler from '{}' to pipeline of new channel: {}", factory, ch, t);
            }
        });

        if (isDirectTLSConfigured()) {
            ch.attr(CONNECTION).get().startTLS(false, true);
        }

        allChannels.add(ch);
    }

    private boolean isDirectTLSConfigured() {
        return this.configuration.getTlsPolicy() == Connection.TLSPolicy.directTLS;
    }
}
