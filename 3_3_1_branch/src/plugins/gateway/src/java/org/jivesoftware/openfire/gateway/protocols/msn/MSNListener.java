/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.msn;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.TransportLoginStatus;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import net.sf.jml.event.MsnAdapter;
import net.sf.jml.*;
import net.sf.jml.message.MsnInstantMessage;
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.message.MsnDatacastMessage;
import net.sf.jml.message.MsnUnknownMessage;

import java.util.Date;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

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
        sessionReaper = new SessionReaper();
        timer.schedule(sessionReaper, reaperInterval, reaperInterval);
    }

    /**
     * The session this listener is associated with.
     */
    public MSNSession msnSession = null;

    /**
     * Timer to check for stale typing notifications.
     */
    private Timer timer = new Timer();

    /**
     * Interval at which typing notifications are reaped.
     */
    private int reaperInterval = 5000; // 5 seconds

    /**
     * The actual repear task.
     */
    private SessionReaper sessionReaper;

    /**
     * Record of active typing notifications.
     */
    private ConcurrentHashMap<String,Date> typingNotificationMap = new ConcurrentHashMap<String,Date>();

    /**
     * Handles incoming messages from MSN users.
     */
    public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact friend) {
        msnSession.getTransport().sendMessage(
                msnSession.getJIDWithHighestPriority(),
                msnSession.getTransport().convertIDToJID(friend.getEmail().toString()),
                message.getContent()
        );
    }

    /**
     * Handles incoming system messages from MSN.
     *
     * @param switchboard Switchboard session the message is associated with.
     * @param message MSN message.
     */
    public void systemMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message) {
        msnSession.getTransport().sendMessage(
                msnSession.getJIDWithHighestPriority(),
                msnSession.getTransport().getJID(),
                message.getContent()
        );
    }

    /**
     * Handles incoming control messages from MSN.
     */
    public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact friend) {
        if (message.getTypingUser() != null) {
            msnSession.getTransport().sendComposingNotification(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().convertIDToJID(friend.getEmail().toString())
            );
            typingNotificationMap.put(friend.getEmail().toString(), new Date());
        }
        else {
            Log.debug("MSN: Received unknown control msg to " + switchboard + " from " + friend + ": " + message);
        }
    }

    /**
     * Handles incoming datacast messages from MSN.
     */
    public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact friend) {
        if (message.getId() == 1) {
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().convertIDToJID(friend.getEmail().toString()),
                    LocaleUtils.getLocalizedString("gateway.msn.nudge", "gateway"),
                    Message.Type.headline
            );
        }
        else if (message.getId() == 2) {
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().convertIDToJID(friend.getEmail().toString()),
                    LocaleUtils.getLocalizedString("gateway.msn.wink", "gateway"),
                    Message.Type.headline
            );
        }
        else {
            Log.debug("MSN: Received unknown datacast message to " + switchboard + " from " + friend + ": " + message);
        }
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
     * Contact list has been synced.
     */
    public void contactListSyncCompleted(MsnMessenger messenger) {
        Log.debug("MSN: Contact list sync for "+messenger.getOwner().getEmail());
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
            Log.debug("MSN: Got group "+msnGroup);
            msnSession.storeGroup(msnGroup);
        }
        msnSession.syncUsers();        
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
     * Someone added us to their contact list.
     */
    public void contactAddedMe(MsnMessenger messenger, MsnContact friend) {
        Presence p = new Presence();
        p.setType(Presence.Type.subscribe);
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        msnSession.getTransport().sendPacket(p);
    }

    /**
     * Someone removed us from their contact list.
     */
    public void contactRemovedMe(MsnMessenger messenger, MsnContact friend) {
        Presence p = new Presence();
        p.setType(Presence.Type.unsubscribe);
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(friend.getEmail().toString()));
        msnSession.getTransport().sendPacket(p);
    }

    /**
     * A contact we added has been added to the server.
     */
    public void contactAddCompleted(MsnMessenger messenger, MsnContact contact) {
        Log.debug("MSN: Contact add completed: "+contact);
        Presence p = new Presence();
        p.setType(Presence.Type.subscribed);
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(contact.getEmail().toString()));
        msnSession.getTransport().sendPacket(p);
        msnSession.storeFriend(contact);
        msnSession.completedPendingContactAdd(contact);
    }

    /**
     * A contact we removed has been removed from the server.
     */
    public void contactRemoveCompleted(MsnMessenger messenger, MsnContact contact) {
        Log.debug("MSN: Contact remove completed: "+contact);
        Presence p = new Presence();
        p.setType(Presence.Type.unsubscribed);
        p.setTo(msnSession.getJID());
        p.setFrom(msnSession.getTransport().convertIDToJID(contact.getEmail().toString()));
        msnSession.getTransport().sendPacket(p);
        msnSession.unstoreFriend(contact);
    }

    /**
     * A group we added has been added to the server.
     */
    public void groupAddCompleted(MsnMessenger messenger, MsnGroup group) {
        Log.debug("MSN: Group add completed: "+group);
        msnSession.storeGroup(group);
        msnSession.completedPendingGroupAdd(group);
    }

    /**
     * A group we removed has been removed from the server.
     */
    public void groupRemoveCompleted(MsnMessenger messenger, MsnGroup group) {
        Log.debug("MSN: Group remove completed: "+group);
        msnSession.unstoreGroup(group);
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
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.passwordincorrect", "gateway"),
                    Message.Type.error
            );
            msnSession.logOut();
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.MsnProtocolException")) {
            Log.debug("MSN: Protocol exception: "+throwable.toString());
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.MsgNotSendException")) {
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.sendmsgfailed", "gateway")+" "+throwable.toString(),
                    Message.Type.error
            );
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.UnknownMessageException")) {
            Log.debug("MSN: Unknown message: "+throwable.toString());
        }
        else if (throwable.getClass().getName().equals("net.sf.jml.exception.UnsupportedProtocolException")) {
            Log.debug("MSN: Protocol error: "+throwable.toString());
        }
        else if (throwable.getClass().getName().equals("java.io.IOException")) {
            Log.debug("MSN: IO error: "+throwable.toString());
            msnSession.getTransport().sendMessage(
                    msnSession.getJIDWithHighestPriority(),
                    msnSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.disconnect", "gateway"),
                    Message.Type.error
            );
            msnSession.logOut();
        }
        else {
            Log.debug("MSN: Unknown error: "+throwable.toString());
        }
    }

    /**
     * Clean up any active typing notifications that are stale.
     */
    private class SessionReaper extends TimerTask {
        /**
         * Silence any typing notifications that are stale.
         */
        public void run() {
            cancelTypingNotifications();
        }
    }

    /**
     * Any typing notification that hasn't been heard in 10 seconds will be killed.
     */
    private void cancelTypingNotifications() {
        for (String source : typingNotificationMap.keySet()) {
            if (typingNotificationMap.get(source).getTime() < ((new Date().getTime()) - 10000)) {
                msnSession.getTransport().sendChatInactiveNotification(
                        msnSession.getJIDWithHighestPriority(),
                        msnSession.getTransport().convertIDToJID(source)
                );
                typingNotificationMap.remove(source);
            }
        }
    }

}
