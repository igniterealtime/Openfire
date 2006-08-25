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
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.message.MsnDatacastMessage;
import net.sf.jml.message.MsnUnknownMessage;

import java.util.Date;

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
    public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact friend) {
        Log.debug("MSN: Received im to " + switchboard + " from " + friend + ": " + message.getContent());
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        m.setBody(message.getContent());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * Handles incoming system messages from MSN.
     */
    public void systemMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message) {
        Log.debug("MSN: Received system msg to " + switchboard + " from MSN: " + message.getContent());
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().getJID());
        m.setBody(message.getContent());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * Handles incoming control messages from MSN.
     */
    public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact friend) {
        Log.debug("MSN: Received control msg to " + switchboard + " from " + friend + ": " + message);
    }

    /**
     * Handles incoming datacast messages from MSN.
     */
    public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact friend) {
        Log.debug("MSN: Received database msn to " + switchboard + " from " + friend + ": " + message);
    }

    /**
     * Handles incoming unknown messages from MSN.
     */
    public void unknownMessageReceived(MsnSwitchboard switchboard, MsnUnknownMessage message, MsnContact friend) {
        Log.debug("MSN: Received database msn to " + switchboard + " from " + friend + ": " + message);
    }

    /**
     * The user's login has completed and was accepted.
     */
    public void loginCompleted(MsnMessenger messenger) {
        Log.debug("MSN login completed");

        msnSession.getRegistration().setLastLogin(new Date());
        msnSession.setLoginStatus(true);
    }

    /**
     * Contact list initialization has completed.
     */
    public void contactListInitCompleted(MsnMessenger messenger) {
        Log.debug("Contact list init completed.");
    }

    /**
     * Contact list has been synced.
     */
    public void contactListSyncCompleted(MsnMessenger messenger) {
        Log.debug("Contact list sync completed.");
        for (MsnContact msnContact : messenger.getContactList().getContacts()) {
            Log.debug("Got contact "+msnContact);
            msnSession.storeFriend(msnContact);
        }
        msnSession.syncUsers();
    }

    /**
     * A friend for this user has changed status.
     */
    public void contactStatusChanged(MsnMessenger messenger, MsnContact friend) {
        Log.debug("Got MSN status "+friend);
        Presence p = new Presence();
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        ((MSNTransport)msnSession.getTransport()).setUpPresencePacket(p, friend.getStatus());
        msnSession.getTransport().sendPacket(p);
        msnSession.storeFriend(friend);
    }

    /**
     * Owner status has changed.
     */
    public void ownerStatusChanged(MsnMessenger messenger) {
        Log.debug("Owner status has changed: " + messenger);
        Presence p = new Presence();
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().getJID());
        ((MSNTransport)msnSession.getTransport()).setUpPresencePacket(p, messenger.getOwner().getStatus());
        msnSession.getTransport().sendPacket(p);
    }

    /**
     * Catches MSN exceptions.
     */
    public void exceptionCaught(MsnMessenger messenger, Throwable throwable) {
        Log.debug("Caught MSN exception: "+messenger+":"+throwable.toString());
    }

}
