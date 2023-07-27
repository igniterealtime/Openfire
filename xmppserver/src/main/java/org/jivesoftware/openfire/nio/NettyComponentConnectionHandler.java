package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ComponentStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NettyConnection}.
 *
 * @author Alex Gidman
 */
public class NettyComponentConnectionHandler extends NettyConnectionHandler {

    /**
     * Enable / disable backup delivery of stanzas to the XMPP server itself when a stanza failed to be delivered on a
     * component connection. When disabled, stanzas that can not be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.component.backup-packet-delivery.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public NettyComponentConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? XMPPServer.getInstance().getPacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new ComponentStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);
    }

    @Override
    public int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.component.idle", 6 * 60 * 1000) / 1000;
    }
}
