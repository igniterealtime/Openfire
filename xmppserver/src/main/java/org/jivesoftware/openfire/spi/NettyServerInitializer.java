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
        int maxIdleTimeBeforeClosing = businessLogicHandler.getMaxIdleTime() > -1 ? businessLogicHandler.getMaxIdleTime() : 0;
        int maxIdleTimeBeforePinging = maxIdleTimeBeforeClosing / 2;

        ch.pipeline()
            .addLast(TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0))
            .addLast(new NettyXMPPDecoder())
            .addLast(new StringEncoder(StandardCharsets.UTF_8))
            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing, maxIdleTimeBeforePinging, 0))
            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(isClientConnection))
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
