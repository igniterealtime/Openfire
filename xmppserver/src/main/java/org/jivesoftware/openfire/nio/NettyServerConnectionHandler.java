package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created and how to build and configure
 * a {@link NettyConnection}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class NettyServerConnectionHandler extends NettyConnectionHandler
{
    /**
     * Enable / disable backup delivery of stanzas to the XMPP server itself when a stanza failed to be delivered on a
     * server-to-server connection. When disabled, stanzas that can not be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.server.netty-backup-packet-delivery.enabled") // TODO - rename once MINA-specific is removed and NettyServerConnectionHandler becomes ServerConnectionHandler
            .setDefaultValue(true)
            .setDynamic(true)
            .build();

    public NettyServerConnectionHandler(ConnectionConfiguration configuration)
    {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? XMPPServer.getInstance().getPacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new ServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection );
    }



    // TBD V



    int getMaxIdleTime()
    {
        return JiveGlobals.getIntProperty( "xmpp.server.idle", 6 * 60 * 1000 ) / 1000;
    }
}
