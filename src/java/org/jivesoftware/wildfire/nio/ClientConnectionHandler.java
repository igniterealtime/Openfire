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
import org.jivesoftware.wildfire.net.ClientStanzaHandler;
import org.jivesoftware.wildfire.net.StanzaHandler;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ClientConnectionHandler extends ConnectionHandler {

    public ClientConnectionHandler(String serverName) {
        super(serverName);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, XMPPServer.getInstance().getPacketDeliverer());
    }

    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new ClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.client.idle", 30 * 60 * 1000) / 1000;
    }
}
