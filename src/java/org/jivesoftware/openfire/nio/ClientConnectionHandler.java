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
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;

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
        return JiveGlobals.getIntProperty("xmpp.client.idle", 6 * 60 * 1000) / 1000;
    }
}
