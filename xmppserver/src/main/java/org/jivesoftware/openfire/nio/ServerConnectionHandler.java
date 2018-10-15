package org.jivesoftware.openfire.nio;

import org.apache.mina.core.session.IoSession;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created and how to build and configure
 * a {@link NIOConnection}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class ServerConnectionHandler extends ConnectionHandler
{
    public ServerConnectionHandler( ConnectionConfiguration configuration )
    {
        super( configuration );
    }

    @Override
    NIOConnection createNIOConnection( IoSession session )
    {
        return new NIOConnection( session, XMPPServer.getInstance().getPacketDeliverer(), configuration );
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
