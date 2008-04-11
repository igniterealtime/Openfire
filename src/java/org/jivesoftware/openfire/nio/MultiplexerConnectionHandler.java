/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.openfire.net.MultiplexerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;

/**
 * ConnectionHandler that knows which subclass of {@link org.jivesoftware.openfire.net.StanzaHandler} should
 * be created and how to build and configure a {@link org.jivesoftware.openfire.nio.NIOConnection}.
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
