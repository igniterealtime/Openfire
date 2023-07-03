package org.jivesoftware.openfire.spi;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.jivesoftware.openfire.nio.NettyConnectionHandler;
import org.jivesoftware.openfire.nio.NettyXMPPDecoder;
import org.jivesoftware.util.SystemProperty;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

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

    private final NettyConnectionHandler businessLogicHandler;

    public NettyServerInitializer(NettyConnectionHandler businessLogicHandler) {
        this.businessLogicHandler = businessLogicHandler;
    }

    @Override
    public void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new NettyXMPPDecoder());
        ch.pipeline().addLast(new StringEncoder());
        ch.pipeline().addLast("stalledSessionHandler", new WriteTimeoutHandler(Math.toIntExact(WRITE_TIMEOUT_SECONDS.getValue().getSeconds())));
        ch.pipeline().addLast(businessLogicHandler);

    }
}
