package org.jivesoftware.openfire.nio;

import org.apache.mina.core.session.IoSession;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created and how to build and configure
 * a {@link NIOConnection}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ServerConnectionHandler extends ConnectionHandler
{
    /**
     * Enable / disable backup delivery of stanzas to the XMPP server itself when a stanza failed to be delivered on a
     * server-to-server connection. When disabled, stanzas that can not be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.server.backup-packet-delivery.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public ServerConnectionHandler( ConnectionConfiguration configuration )
    {
        super( configuration );
    }

    @Override
    NIOConnection createNIOConnection( IoSession session )
    {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? XMPPServer.getInstance().getPacketDeliverer() : null;
        return new NIOConnection(session, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler( NIOConnection connection )
    {
        return new ServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection );
    }

    @Override
    int getMaxIdleTime()
    {
        return JiveGlobals.getIntProperty( "xmpp.server.idle", 6 * 60 * 1000 ) / 1000;
    }
}
