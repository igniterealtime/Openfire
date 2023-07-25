package org.jivesoftware.openfire.spi;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

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

    public static final String TRAFFIC_HANDLER_NAME = "trafficShapingHandler"
    ;
    private final NettyConnectionHandler businessLogicHandler;
    private final boolean directTLS;
    private final ChannelGroup allChannels;

    public NettyServerInitializer(NettyConnectionHandler businessLogicHandler, boolean directTLS, ChannelGroup allChannels) {
        this.businessLogicHandler = businessLogicHandler;
        this.directTLS = directTLS;
        this.allChannels = allChannels;
    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        boolean isClientConnection = businessLogicHandler instanceof NettyClientConnectionHandler;
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

        if (directTLS) {
            ch.attr(CONNECTION).get().startTLS(false, true);
        }

        allChannels.add(ch);
    }
}
