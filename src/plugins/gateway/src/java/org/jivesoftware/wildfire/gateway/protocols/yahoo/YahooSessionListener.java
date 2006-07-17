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
        Log.debug(event.toString());
        Message m = new Message();
        m.setType(Message.Type.chat);
        m.setTo(yahooSession.getJID());
        m.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        m.setBody(event.getMessage());
        yahooSession.getTransport().sendPacket(m);
    }


    /**
     * @see ymsg.network.event.SessionListener#fileTransferReceived(ymsg.network.event.SessionFileTransferEvent)
     */
    public void fileTransferReceived(SessionFileTransferEvent event) {
        Log.info(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#connectionClosed(ymsg.network.event.SessionEvent)
     */
    public void connectionClosed(SessionEvent event) {
        Log.info(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#listReceived(ymsg.network.event.SessionEvent)
     */
    public void listReceived(SessionEvent event) {
        Log.info(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#buzzReceived(ymsg.network.event.SessionEvent)
     */
    public void buzzReceived(SessionEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#offlineMessageReceived(ymsg.network.event.SessionEvent)
     */
    public void offlineMessageReceived(SessionEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#errorPacketReceived(ymsg.network.event.SessionErrorEvent)
     */
    public void errorPacketReceived(SessionErrorEvent event) {
        Log.error(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#inputExceptionThrown(ymsg.network.event.SessionExceptionEvent)
     */
    public void inputExceptionThrown(SessionExceptionEvent event) {
        event.getException().printStackTrace();
        Log.error(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#newMailReceived(ymsg.network.event.SessionNewMailEvent)
     */
    public void newMailReceived(SessionNewMailEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#notifyReceived(ymsg.network.event.SessionNotifyEvent)
     */
    public void notifyReceived(SessionNotifyEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#contactRequestReceived(ymsg.network.event.SessionEvent)
     */
    public void contactRequestReceived(SessionEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#contactRejectionReceived(ymsg.network.event.SessionEvent)
     */
    public void contactRejectionReceived(SessionEvent event) {
        Log.debug(event.toString());
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
     * @see ymsg.network.event.SessionListener#friendsUpdateReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendsUpdateReceived(SessionFriendEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#friendAddedReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendAddedReceived(SessionFriendEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see ymsg.network.event.SessionListener#friendRemovedReceived(ymsg.network.event.SessionFriendEvent)
     */
    public void friendRemovedReceived(SessionFriendEvent event) {
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
