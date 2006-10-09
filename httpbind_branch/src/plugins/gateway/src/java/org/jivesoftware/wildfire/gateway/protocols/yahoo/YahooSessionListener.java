/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import org.jivesoftware.util.Log;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import ymsg.network.YahooUser;
import ymsg.network.StatusConstants;
import ymsg.network.event.SessionChatEvent;
import ymsg.network.event.SessionConferenceEvent;
import ymsg.network.event.SessionErrorEvent;
import ymsg.network.event.SessionEvent;
import ymsg.network.event.SessionExceptionEvent;
import ymsg.network.event.SessionFileTransferEvent;
import ymsg.network.event.SessionFriendEvent;
import ymsg.network.event.SessionListener;
import ymsg.network.event.SessionNewMailEvent;
import ymsg.network.event.SessionNotifyEvent;
import ymsg.support.MessageDecoder;

/**
 * Handles incoming packets from Yahoo.
 *
 * This takes care of events we don't do anything with yet by logging them.
 *
 * @author Daniel Henninger
 * Heavily inspired by Noah Campbell's work.
 */
public class YahooSessionListener implements SessionListener {

    /**
     * Handles converting messages between formats.
     */
    private MessageDecoder messageDecoder = new MessageDecoder();

    /**
     * Creates a Yahoo session listener affiliated with a session.
     *
     * @param session The YahooSession instance we are associatd with.
     */
    public YahooSessionListener(YahooSession session) {
        this.yahooSession = session;
    }

    /**
     * The transport session we are affiliated with.
     */
    YahooSession yahooSession;

    /**
     * @see ymsg.network.event.SessionListener#messageReceived(ymsg.network.event.SessionEvent)
     */
    public void messageReceived(SessionEvent event) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(yahooSession.getJIDWithHighestPriority());
        m.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        m.setBody(messageDecoder.decodeToText(event.getMessage()));
        yahooSession.getTransport().sendPacket(m);
    }

    /**
     * @see ymsg.network.event.SessionListener#offlineMessageReceived(ymsg.network.event.SessionEvent)
     */
    public void offlineMessageReceived(SessionEvent event) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(yahooSession.getJIDWithHighestPriority());
        m.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        m.setBody(messageDecoder.decodeToText(event.getMessage()));
        yahooSession.getTransport().sendPacket(m);
    }

    /**
     * @see ymsg.network.event.SessionListener#newMailReceived(ymsg.network.event.SessionNewMailEvent)
     */
    public void newMailReceived(SessionNewMailEvent event) {
        if (event.getMailCount() > 0) {
            Message m = new Message();
            m.setType(Message.Type.headline);
            m.setTo(yahooSession.getJIDWithHighestPriority());
            m.setFrom(yahooSession.getTransport().getJID());
            m.setBody("You have "+event.getMailCount()+" message(s) waiting in your Yahoo! mail.");
            yahooSession.getTransport().sendPacket(m);
        }
    }

    /**
     * @see ymsg.network.event.SessionListener#friendsUpdateReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendsUpdateReceived(SessionFriendEvent event) {
        for (YahooUser user : event.getFriends()) {
            Presence p = new Presence();
            p.setTo(yahooSession.getJID());
            p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            ((YahooTransport)yahooSession.getTransport()).setUpPresencePacket(p, user.getStatus());
            yahooSession.getTransport().sendPacket(p);
        }
    }

    /**
     * @see ymsg.network.event.SessionListener#friendAddedReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendAddedReceived(SessionFriendEvent event) {
        Presence p = new Presence(Presence.Type.subscribed);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see ymsg.network.event.SessionListener#friendRemovedReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendRemovedReceived(SessionFriendEvent event) {
        Presence p = new Presence(Presence.Type.unsubscribed);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see ymsg.network.event.SessionListener#connectionClosed(ymsg.network.event.SessionEvent)
     */
    public void connectionClosed(SessionEvent event) {
        Log.debug(event.toString());
        if (yahooSession.isLoggedIn()) {
            yahooSession.logOut();                            
        }
    }

    /**
     * @see ymsg.network.event.SessionListener#fileTransferReceived(ymsg.network.event.SessionFileTransferEvent)
     */
    public void fileTransferReceived(SessionFileTransferEvent event) {
        Log.debug(event.toString());
    }


    /**
     * @see ymsg.network.event.SessionListener#listReceived(ymsg.network.event.SessionEvent)
     */
    public void listReceived(SessionEvent event) {
        // We just got the entire contact list.  Lets sync up.
        yahooSession.syncUsers();
    }

    /**
     * @see ymsg.network.event.SessionListener#buzzReceived(ymsg.network.event.SessionEvent)
     */
    public void buzzReceived(SessionEvent event) {
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(yahooSession.getJIDWithHighestPriority());
        m.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        m.setBody(messageDecoder.decodeToText(event.getMessage()));
        yahooSession.getTransport().sendPacket(m);
    }

    /**
     * @see ymsg.network.event.SessionListener#errorPacketReceived(ymsg.network.event.SessionErrorEvent)
     */
    public void errorPacketReceived(SessionErrorEvent event) {
        //Log.error(event.toString());
        Message m = new Message();
        m.setType(Message.Type.error);
        m.setTo(yahooSession.getJIDWithHighestPriority());
        m.setFrom(yahooSession.getTransport().getJID());
        m.setBody("Error from yahoo: "+event.getMessage());
        yahooSession.getTransport().sendPacket(m);
    }

    /**
     * @see ymsg.network.event.SessionListener#inputExceptionThrown(ymsg.network.event.SessionExceptionEvent)
     */
    public void inputExceptionThrown(SessionExceptionEvent event) {
        //event.getException().printStackTrace();
        //Log.error(event.toString());
        Message m = new Message();
        m.setType(Message.Type.error);
        m.setTo(yahooSession.getJIDWithHighestPriority());
        m.setFrom(yahooSession.getTransport().getJID());
        m.setBody("Input error from yahoo: "+event.getMessage());
        yahooSession.getTransport().sendPacket(m);
    }

    /**
     * @see ymsg.network.event.SessionListener#notifyReceived(ymsg.network.event.SessionNotifyEvent)
     */
    public void notifyReceived(SessionNotifyEvent event) {
        Log.debug(event.toString());
        if (event.getType().equals(StatusConstants.NOTIFY_TYPING)) {
            // Ooh, a typing notification.  We'll use this in the future.
        }
    }

    /**
     * @see ymsg.network.event.SessionListener#contactRequestReceived(ymsg.network.event.SessionEvent)
     */
    public void contactRequestReceived(SessionEvent event) {
        Presence p = new Presence(Presence.Type.subscribe);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see ymsg.network.event.SessionListener#contactRejectionReceived(ymsg.network.event.SessionEvent)
     */
    public void contactRejectionReceived(SessionEvent event) {
        // TODO: Is this correct?  unsubscribed for a rejection?
        Log.debug(event.toString());
        Presence p = new Presence(Presence.Type.unsubscribed);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see ymsg.network.event.SessionListener#conferenceInviteReceived(ymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceInviteReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#conferenceInviteDeclinedReceived(ymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceInviteDeclinedReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#conferenceLogonReceived(ymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceLogonReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#conferenceLogoffReceived(ymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceLogoffReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#conferenceMessageReceived(ymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceMessageReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#chatLogonReceived(ymsg.network.event.SessionChatEvent)
     */
    public void chatLogonReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#chatLogoffReceived(ymsg.network.event.SessionChatEvent)
     */
    public void chatLogoffReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#chatMessageReceived(ymsg.network.event.SessionChatEvent)
     */
    public void chatMessageReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#chatUserUpdateReceived(ymsg.network.event.SessionChatEvent)
     */
    public void chatUserUpdateReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#chatConnectionClosed(ymsg.network.event.SessionEvent)
     */
    public void chatConnectionClosed(SessionEvent event) {
        Log.debug(event.toString());
    }

}
