/**
 * $RCSfile: ComponentSocketReader.java,v $
 * $Revision: 3174 $
 * $Date: 2005-12-08 17:41:00 -0300 (Thu, 08 Dec 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.dom4j.Element;
import org.jivesoftware.wildfire.component.ComponentSession;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.Socket;

/**
 * A SocketReader specialized for component connections. This reader will be used when the open
 * stream contains a jabber:component:accept namespace.
 *
 * @author Gaston Dombiak
 */
public class ComponentSocketReader extends SocketReader {

    public ComponentSocketReader(PacketRouter router, String serverName, Socket socket,
            SocketConnection connection) {
        super(router, serverName, socket, connection);
    }

    /**
     * Only packets of type Message, Presence and IQ can be processed by this class. Any other
     * type of packet is unknown and thus rejected generating the connection to be closed.
     *
     * @param doc the unknown DOM element that was received
     * @return always false.
     */
    protected boolean processUnknowPacket(Element doc) {
        return false;
    }

    boolean createSession(String namespace) throws UnauthorizedException, XmlPullParserException,
            IOException {
        if ("jabber:component:accept".equals(namespace)) {
            // The connected client is a component so create a ComponentSession
            session = ComponentSession.createSession(serverName, reader, connection);
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:component:accept";
    }

    boolean validateHost() {
        return false;
    }
}
