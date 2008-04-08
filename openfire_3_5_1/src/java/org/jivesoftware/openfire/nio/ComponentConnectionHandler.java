/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.nio;

import org.apache.mina.common.IoSession;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ComponentStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.util.JiveGlobals;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ComponentConnectionHandler extends ConnectionHandler {
    public ComponentConnectionHandler(String serverName) {
        super(serverName);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, XMPPServer.getInstance().getPacketDeliverer());
    }

    StanzaHandler createStanzaHandler(NIOConnection connection) {
        return new ComponentStanzaHandler(XMPPServer.getInstance().getPacketRouter(), serverName, connection);
    }

    int getMaxIdleTime() {
        return JiveGlobals.getIntProperty("xmpp.component.idle", 6 * 60 * 1000) / 1000;
    }
}
