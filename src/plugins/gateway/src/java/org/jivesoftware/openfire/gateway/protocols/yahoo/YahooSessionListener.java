/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.yahoo;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.TransportLoginStatus;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;
import org.openymsg.network.YahooUser;
import org.openymsg.network.StatusConstants;
import org.openymsg.network.event.*;
import org.openymsg.support.MessageDecoder;

import java.util.Arrays;

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
     * @see org.openymsg.network.event.SessionListener#messageReceived(org.openymsg.network.event.SessionEvent)
     */
    public void messageReceived(SessionEvent event) {
        yahooSession.getTransport().sendMessage(
                yahooSession.getJIDWithHighestPriority(),
                yahooSession.getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );
    }

    /**
     * @see org.openymsg.network.event.SessionListener#offlineMessageReceived(org.openymsg.network.event.SessionEvent)
     */
    public void offlineMessageReceived(SessionEvent event) {
        yahooSession.getTransport().sendMessage(
                yahooSession.getJIDWithHighestPriority(),
                yahooSession.getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );
    }

    /**
     * @see org.openymsg.network.event.SessionListener#newMailReceived(org.openymsg.network.event.SessionNewMailEvent)
     */
    public void newMailReceived(SessionNewMailEvent event) {
        if (event.getMailCount() > 0) {
            yahooSession.getTransport().sendMessage(
                    yahooSession.getJIDWithHighestPriority(),
                    yahooSession.getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.yahoo.mail", "gateway", Arrays.asList(Integer.toString(event.getMailCount()))),
                    Message.Type.headline
            );
        }
    }

    /**
     * @see org.openymsg.network.event.SessionListener#friendsUpdateReceived(org.openymsg.network.event.SessionFriendEvent)
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
     * @see org.openymsg.network.event.SessionListener#friendAddedReceived(org.openymsg.network.event.SessionFriendEvent)
     */
    public void friendAddedReceived(SessionFriendEvent event) {
        Presence p = new Presence(Presence.Type.subscribe);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionListener#friendRemovedReceived(org.openymsg.network.event.SessionFriendEvent)
     */
    public void friendRemovedReceived(SessionFriendEvent event) {
        Presence p = new Presence(Presence.Type.unsubscribe);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionListener#groupRenameReceived(org.openymsg.network.event.SessionGroupEvent)
     */
    public void groupRenameReceived(SessionGroupEvent sessionGroupEvent) {
        // TODO: Handle this
    }

    /**
     * @see org.openymsg.network.event.SessionListener#chatJoinReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatJoinReceived(SessionChatEvent sessionChatEvent) {
    }

    /**
     * @see org.openymsg.network.event.SessionListener#chatExitReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatExitReceived(SessionChatEvent sessionChatEvent) {
    }

    /**
     * @see org.openymsg.network.event.SessionListener#connectionClosed(org.openymsg.network.event.SessionEvent)
     */
    public void connectionClosed(SessionEvent event) {
        Log.debug(event.toString());
        if (yahooSession.isLoggedIn()) {
            yahooSession.setLoginStatus(TransportLoginStatus.DISCONNECTED);
            yahooSession.sessionDisconnectedNoReconnect();
        }
    }

    /**
     * @see org.openymsg.network.event.SessionListener#fileTransferReceived(org.openymsg.network.event.SessionFileTransferEvent)
     */
    public void fileTransferReceived(SessionFileTransferEvent event) {
        Log.debug(event.toString());
    }


    /**
     * @see org.openymsg.network.event.SessionListener#listReceived(org.openymsg.network.event.SessionEvent)
     */
    public void listReceived(SessionEvent event) {
        // We just got the entire contact list.  Lets sync up.
        yahooSession.syncUsers();
    }

    /**
     * @see org.openymsg.network.event.SessionListener#buzzReceived(org.openymsg.network.event.SessionEvent)
     */
    public void buzzReceived(SessionEvent event) {
        yahooSession.getTransport().sendMessage(
                yahooSession.getJIDWithHighestPriority(),
                yahooSession.getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );
    }

    /**
     * @see org.openymsg.network.event.SessionListener#errorPacketReceived(org.openymsg.network.event.SessionErrorEvent)
     */
    public void errorPacketReceived(SessionErrorEvent event) {
        Log.error("Error from yahoo: "+event.getMessage()+", Code:"+event.getCode());
        yahooSession.getTransport().sendMessage(
                yahooSession.getJIDWithHighestPriority(),
                yahooSession.getTransport().getJID(),
                LocaleUtils.getLocalizedString("gateway.yahoo.error", "gateway")+" "+event.getMessage(),
                Message.Type.error
        );
    }

    /**
     * @see org.openymsg.network.event.SessionListener#inputExceptionThrown(org.openymsg.network.event.SessionExceptionEvent)
     */
    public void inputExceptionThrown(SessionExceptionEvent event) {
        Log.error("Input error from yahoo: "+event.getMessage(), event.getException());
        // Lets keep this silent for now.  Not bother the end user with it.
//        yahooSession.getTransport().sendMessage(
//                yahooSession.getJIDWithHighestPriority(),
//                yahooSession.getTransport().getJID(),
//                "Input error from yahoo: "+event.getMessage(),
//                Message.Type.error
//        );
    }

    /**
     * @see org.openymsg.network.event.SessionListener#notifyReceived(org.openymsg.network.event.SessionNotifyEvent)
     */
    public void notifyReceived(SessionNotifyEvent event) {
        Log.debug(event.toString());
        if (event.getType().equals(StatusConstants.NOTIFY_TYPING)) {
            yahooSession.getTransport().sendComposingNotification(
                    yahooSession.getJIDWithHighestPriority(),
                    yahooSession.getTransport().convertIDToJID(event.getFrom())
            );
        }
    }

    /**
     * @see org.openymsg.network.event.SessionListener#contactRequestReceived(org.openymsg.network.event.SessionEvent)
     */
    public void contactRequestReceived(SessionEvent event) {
        Presence p = new Presence(Presence.Type.subscribe);
        p.setTo(yahooSession.getJID());
        p.setFrom(yahooSession.getTransport().convertIDToJID(event.getFrom()));
        yahooSession.getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionListener#contactRejectionReceived(org.openymsg.network.event.SessionEvent)
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
     * @see org.openymsg.network.event.SessionListener#conferenceInviteReceived(org.openymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceInviteReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#conferenceInviteDeclinedReceived(org.openymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceInviteDeclinedReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#conferenceLogonReceived(org.openymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceLogonReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#conferenceLogoffReceived(org.openymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceLogoffReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#conferenceMessageReceived(org.openymsg.network.event.SessionConferenceEvent)
     */
    public void conferenceMessageReceived(SessionConferenceEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#chatMessageReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatMessageReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#chatUserUpdateReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatUserUpdateReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionListener#chatConnectionClosed(org.openymsg.network.event.SessionEvent)
     */
    public void chatConnectionClosed(SessionEvent event) {
        Log.debug(event.toString());
    }

}
