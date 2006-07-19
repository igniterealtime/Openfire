/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.msn;

import java.util.ArrayList;
import java.util.HashMap;
import org.hn.sleek.jmml.IncomingMessageEvent;
import org.hn.sleek.jmml.MessengerClientAdapter;
import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;

/**
 * MSN Listener Interface.
 *
 * This handles real interaction with MSN, but mostly is a listener for
 * incoming events from MSN.
 *
 * @author Daniel Henninger
 */
public class MSNListener extends MessengerClientAdapter {

    /**
     * Creates the MSN Listener instance.
     *
     * @param session Session this listener is associated with.
     */
    public MSNListener(MSNSession session) {
        this.msnSession = session;
    }

    /**
     * The session this listener is associated with.
     */
    public MSNSession msnSession = null;

    /**
     * Handles incoming messages from MSN.
     */
    public void incomingMessage(IncomingMessageEvent event) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJID());
        m.setFrom(msnSession.getTransport().convertIDToJID(event.getUserName()));
        m.setBody(event.getMessage());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * Deals with a server disconnection.
     */
    public void serverDisconnected() {
        msnSession.logOut();
    }

}
