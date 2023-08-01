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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;

/**
 * Creates a newly configured {@link ChannelPipeline} for a new channel.
 */
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {

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
    private final ChannelGroup allChannels;
    private final ConnectionConfiguration configuration;

    public NettyServerInitializer(ConnectionConfiguration configuration, ChannelGroup allChannels) {
        this.allChannels = allChannels;
        this.configuration = configuration;
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
            .addLast(new StringEncoder())
            .addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())))
            .addLast("idleStateHandler", new IdleStateHandler(maxIdleTimeBeforeClosing, maxIdleTimeBeforePinging, 0))
            .addLast("keepAliveHandler", new NettyIdleStateKeepAliveHandler(isClientConnection))
            .addLast(businessLogicHandler);

        if (isDirectTLSConfigured()) {
            ch.attr(CONNECTION).get().startTLS(false, true);
        }

        allChannels.add(ch);
    }

    private boolean isDirectTLSConfigured() {
        return this.configuration.getTlsPolicy() == Connection.TLSPolicy.legacyMode;
    }
}
