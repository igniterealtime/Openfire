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

import org.xmpp.packet.Message;

import java.util.Date;

import rath.msnm.event.MsnAdapter;
import rath.msnm.SwitchboardSession;
import rath.msnm.msg.MimeMessage;
import rath.msnm.entity.MsnFriend;

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
     * Handles incoming messages from MSN users.
     */
    public void instantMessageReceived(SwitchboardSession switchboard, MsnFriend friend, MimeMessage message) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().convertIDToJID(friend.getLoginName()));
        m.setBody(message.getMessage());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * The user's login has completed and was accepted.
     */
    public void loginComplete(MsnFriend me) {
        msnSession.getRegistration().setLastLogin(new Date());
        msnSession.setLoginStatus(true);
    }

}
