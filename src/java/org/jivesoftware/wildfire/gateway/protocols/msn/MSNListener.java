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

import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import net.sf.jml.event.MsnAdapter;
import net.sf.jml.MsnSwitchboard;
import net.sf.jml.MsnContact;
import net.sf.jml.MsnMessenger;
import net.sf.jml.message.MsnInstantMessage;

/**
 * MSN Listener Interface.
 *
 * This handles real interaction with MSN, but mostly is a listener for
 * incoming events from MSN.
 *
 * @author Daniel Henninger
 */
public class MSNListener extends MsnAdapter {

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
    public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact friend) {
        Log.debug("MSN: Received im to " + switchboard + " from " + friend + ": " + message.toString());
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        m.setBody(message.toString());
        msnSession.getTransport().sendPacket(m);
    }


    /**
     * The user's login has completed and was accepted.
     */
    public void loginCompleted(MsnMessenger messenger) {
        Log.debug("MSN login completed");
        Presence p = new Presence();
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().getJID());
        msnSession.getTransport().sendPacket(p);
    }

}
