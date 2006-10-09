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
import org.jivesoftware.wildfire.gateway.TransportLoginStatus;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import net.sf.jml.event.MsnAdapter;
import net.sf.jml.*;
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
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(msnSession.getJIDWithHighestPriority());
        m.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        m.setBody(message.getContent());
        msnSession.getTransport().sendPacket(m);
    }

    /**
     * Handles incoming system messages from MSN.
     *
     * @param switchboard Switchboard session the message is associated with.
     * @param message MSN message.
     */
    public void systemMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message) {
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
        Log.debug("MSN: Received datacast message to " + switchboard + " from " + friend + ": " + message);
    }

    /**
     * Handles incoming unknown messages from MSN.
     */
    public void unknownMessageReceived(MsnSwitchboard switchboard, MsnUnknownMessage message, MsnContact friend) {
        Log.debug("MSN: Received unknown message to " + switchboard + " from " + friend + ": " + message);
    }

    /**
     * The user's login has completed and was accepted.
     */
    public void loginCompleted(MsnMessenger messenger) {
        Log.debug("MSN: Login completed for "+messenger.getOwner().getEmail());
        msnSession.getRegistration().setLastLogin(new Date());
        msnSession.setLoginStatus(TransportLoginStatus.LOGGED_IN);
    }

    /**
     * Contact list initialization has completed.
     */
    public void contactListInitCompleted(MsnMessenger messenger) {
        for (MsnContact msnContact : messenger.getContactList().getContacts()) {
            Log.debug("MSN: Got contact "+msnContact);
            if (msnContact.isInList(MsnList.FL) && msnContact.getEmail() != null) {
                msnSession.storeFriend(msnContact);
            }
        }
        for (MsnGroup msnGroup : messenger.getContactList().getGroups()) {
            msnSession.storeGroup(msnGroup);
        }
        msnSession.syncUsers();        
    }

    /**
     * Contact list has been synced.
     */
    public void contactListSyncCompleted(MsnMessenger messenger) {
        Log.debug("MSN: Contact list sync for "+messenger.getOwner().getEmail());        
    }

    /**
     * A friend for this user has changed status.
     */
    public void contactStatusChanged(MsnMessenger messenger, MsnContact friend) {
        if (!friend.isInList(MsnList.FL) || friend.getEmail() == null) {
            // Not in our buddy list, don't care, or null email address.  We need that.
            return;
        }
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
        Log.debug("MSN: Exception occurred for "+messenger.getOwner().getEmail()+" : "+throwable);        
        if (throwable.getClass().getName().equals("net.sf.jml.exception.IncorrectPasswordException")) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("The password you registered with is incorrect.  Please re-register with the correct password.");
            msnSession.getTransport().sendPacket(m);
            msnSession.logOut();
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.MsnProtocolException")) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("MSN error: "+throwable.toString());
            msnSession.getTransport().sendPacket(m);
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.MsgNotSendException")) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("Unable to send MSN message.  Reason: "+throwable.toString());
            msnSession.getTransport().sendPacket(m);
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.UnknownMessageException")) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("Unknown message from MSN: "+throwable.toString());
            msnSession.getTransport().sendPacket(m);
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.UnsupportedProtocolException")) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("MSN protocol error: "+throwable.toString());
            msnSession.getTransport().sendPacket(m);
        }
        else {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(msnSession.getJIDWithHighestPriority());
            m.setFrom(msnSession.getTransport().getJID());
            m.setBody("Unknown error from MSN: "+throwable.toString());
            throwable.printStackTrace();
            msnSession.getTransport().sendPacket(m);
        }
    }

}
