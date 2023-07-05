package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.net.*;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class NettyOutboundConnectionHandler extends NettyConnectionHandler {
    private static final Logger log = LoggerFactory.getLogger(NettyOutboundConnectionHandler.class);
    private final DomainPair domainPair;

    public NettyOutboundConnectionHandler(ConnectionConfiguration configuration, DomainPair domainPair) {
        super(configuration);
        this.domainPair = domainPair;
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        return new NettyConnection(ctx, null, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new RespondingServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection, domainPair );
    }

    @Override
    int getMaxIdleTime() {
        return 0;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        System.out.println("Adding NettyOutboundConnectionHandler");
        super.handlerAdded(ctx);
    }
}
