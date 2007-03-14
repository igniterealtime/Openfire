/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.nio;

import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.wildfire.net.MultiplexerStanzaHandler;
import org.jivesoftware.wildfire.net.StanzaHandler;

/**
 * ConnectionHandler that knows which subclass of {@link org.jivesoftware.wildfire.net.StanzaHandler} should
 * be created and how to build and configure a {@link org.jivesoftware.wildfire.nio.NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerConnectionHandler extends ConnectionHandler {

    public MultiplexerConnectionHandler(String serverName) {
        super(serverName);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, new MultiplexerPacketDeliverer());
    }

    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new MultiplexerStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.multiplex.idle", 5 * 60 * 1000) / 1000;
    }
}
