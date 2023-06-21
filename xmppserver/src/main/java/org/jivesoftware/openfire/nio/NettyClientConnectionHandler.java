package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

public class NettyClientConnectionHandler extends NettyConnectionHandler{

    /**
     * Enable / disable backup delivery of stanzas to the 'offline message store' of the corresponding user when a stanza
     * failed to be delivered on a client connection. When disabled, stanzas that can not be delivered on the connection
     * are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.client.netty-backup-packet-delivery.enabled") // TODO - rename once MINA-specific is removed and NettyClientConnectionHandler becomes ClientConnectionHandler
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public NettyClientConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? new OfflinePacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new ClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);

    }


    // TODO Do we need a sessionIdle function for the ClientConnectionHandler specifically? :
    /**
     * In addition to the functionality provided by the parent class, this
     * method will send XMPP ping requests to the remote entity on every first
     * invocation of this method (which will occur after a period of half the
     * allowed connection idle time has passed, without any IO).
     *
     * XMPP entities must respond with either an IQ result or an IQ error
     * (feature-unavailable) stanza upon receiving the XMPP ping stanza. Both
     * responses will be received by Openfire and will cause the connection idle
     * count to be reset.
     *
     * Entities that do not respond to the IQ Ping stanzas can be considered
     * dead, and their connection will be closed by the parent class
     * implementation on the second invocation of this method.
     *
     * Note that whitespace pings that are sent by XMPP entities will also cause
     * the connection idle count to be reset.
     *
     * @see ConnectionHandler#sessionIdle(IoSession, IdleStatus)
     */

    @Override
    int getMaxIdleTime()
    {
        return JiveGlobals.getIntProperty( "xmpp.server.idle", 6 * 60 * 1000 ) / 1000;
    }

}
