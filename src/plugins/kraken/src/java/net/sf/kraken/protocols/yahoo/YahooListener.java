/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.yahoo;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;
import net.sf.kraken.util.chatstate.ChatStateEventSource;

import org.apache.log4j.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.openymsg.network.*;
import org.openymsg.network.event.*;
import org.openymsg.support.MessageDecoder;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * Handles incoming packets from Yahoo.
 *
 * This takes care of events we don't do anything with yet by logging them.
 *
 * @author Daniel Henninger
 * Heavily inspired by Noah Campbell's work.
 */
@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
public class YahooListener implements SessionListener {

    static Logger Log = Logger.getLogger(YahooListener.class);

    /**
     * Handles converting messages between formats.
     */
    private MessageDecoder messageDecoder = new MessageDecoder();

    /**
     * Indicator for whether we've gotten through the initial email notification.
     */
    private Boolean emailInitialized = false;

    /**
     * Creates a Yahoo session listener affiliated with a session.
     *
     * @param session The YahooSession instance we are associatd with.
     */
    public YahooListener(YahooSession session) {
        this.yahooSessionRef = new WeakReference<YahooSession>(session);
    }

    /**
     * The transport session we are affiliated with.
     */
    WeakReference<YahooSession> yahooSessionRef;

    /**
     * Returns the Yahoo session this listener is attached to.
     *
     * @return Yahoo session we are attached to.
     */
    public YahooSession getSession() {
        return yahooSessionRef.get();
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#messageReceived(org.openymsg.network.event.SessionEvent)
     */
    public void messageReceived(SessionEvent event) {
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );

    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#offlineMessageReceived(org.openymsg.network.event.SessionEvent)
     */
    public void offlineMessageReceived(SessionEvent event) {
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#newMailReceived(org.openymsg.network.event.SessionNewMailEvent)
     */
    public void newMailReceived(SessionNewMailEvent event) {
        if (JiveGlobals.getBooleanProperty("plugin.gateway.yahoo.mailnotifications", true) && (emailInitialized || event.getMailCount() > 0)) {
            if (!emailInitialized) {
                getSession().getTransport().sendMessage(
                        getSession().getJID(),
                        getSession().getTransport().getJID(),
                        LocaleUtils.getLocalizedString("gateway.yahoo.mail", "kraken", Arrays.asList(Integer.toString(event.getMailCount()))),
                        Message.Type.headline
                );
            }
            else {
                getSession().getTransport().sendMessage(
                        getSession().getJID(),
                        getSession().getTransport().getJID(),
                        LocaleUtils.getLocalizedString("gateway.yahoo.newmail", "kraken"),
                        Message.Type.headline
                );
            }
        }
        emailInitialized = true;
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#friendsUpdateReceived(org.openymsg.network.event.SessionFriendEvent)
     */
    public void friendsUpdateReceived(SessionFriendEvent event) {
        YahooUser user = event.getUser();
        Log.debug("Yahoo: Got status update: "+user);
        if (getSession().getBuddyManager().isActivated()) {
            try {
                YahooBuddy yahooBuddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(user.getId()));
                yahooBuddy.yahooUser = user;
                yahooBuddy.setPresenceAndStatus(((YahooTransport)getSession().getTransport()).convertYahooStatusToXMPP(user.getStatus(), user.getCustomStatus()), user.getCustomStatusMessage());

            }
            catch (NotFoundException e) {
                // Not in our list, lets change that.
                PseudoRosterItem rosterItem = getSession().getPseudoRoster().getItem(user.getId());
                String nickname = null;
                if (rosterItem != null) {
                    nickname = rosterItem.getNickname();
                }
                if (nickname == null) {
                    nickname = user.getId();
                }
                YahooBuddy yahooBuddy = new YahooBuddy(getSession().getBuddyManager(), user, nickname, user.getGroupIds(), rosterItem);
                getSession().getBuddyManager().storeBuddy(yahooBuddy);
                yahooBuddy.setPresenceAndStatus(((YahooTransport)getSession().getTransport()).convertYahooStatusToXMPP(user.getStatus(), user.getCustomStatus()), user.getCustomStatusMessage());
                //TODO: Something is amiss with openymsg-- telling us we have our full buddy list too early
                //Log.debug("Yahoo: Received presense notification for contact we don't care about: "+event.getFrom());
            }
        }
        else {
            getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(user.getId()), ((YahooTransport)getSession().getTransport()).convertYahooStatusToXMPP(user.getStatus(), user.getCustomStatus()), user.getCustomStatusMessage());

        }
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#friendAddedReceived(org.openymsg.network.event.SessionFriendEvent)
     */
    public void friendAddedReceived(SessionFriendEvent event) {
        // TODO: This means a friend -we- added is now added, do we want to use this
//        Presence p = new Presence(Presence.Type.subscribe);
//        p.setTo(getSession().getJID());
//        p.setFrom(getSession().getTransport().convertIDToJID(event.getFrom()));
//        getSession().getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#friendRemovedReceived(org.openymsg.network.event.SessionFriendEvent)
     */
    public void friendRemovedReceived(SessionFriendEvent event) {
        // TODO: This means a friend -we- removed is now gone, do we want to use this
//        Presence p = new Presence(Presence.Type.unsubscribe);
//        p.setTo(getSession().getJID());
//        p.setFrom(getSession().getTransport().convertIDToJID(event.getFrom()));
//        getSession().getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#chatJoinReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatJoinReceived(SessionChatEvent sessionChatEvent) {
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#chatExitReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatExitReceived(SessionChatEvent sessionChatEvent) {
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#connectionClosed(org.openymsg.network.event.SessionEvent)
     */
    public void connectionClosed(SessionEvent event) {
        Log.debug(event == null ? "closed event is null":event.toString());
        if (getSession().isLoggedIn()) {
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().sessionDisconnectedNoReconnect(null);
        }
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#fileTransferReceived(org.openymsg.network.event.SessionFileTransferEvent)
     */
    public void fileTransferReceived(SessionFileTransferEvent event) {
        Log.debug(event.toString());
    }


    /**
     * @see org.openymsg.network.event.SessionAdapter#listReceived(org.openymsg.network.event.SessionListEvent)
     */
    public void listReceived(SessionListEvent event) {
        // We just got the entire contact list.  Lets sync up.
        getSession().syncUsers();
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#buzzReceived(org.openymsg.network.event.SessionEvent)
     */
    public void buzzReceived(SessionEvent event) {
        getSession().getTransport().sendAttentionNotification(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(event.getFrom()),
                messageDecoder.decodeToText(event.getMessage())
        );
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#errorPacketReceived(org.openymsg.network.event.SessionErrorEvent)
     */
    public void errorPacketReceived(SessionErrorEvent event) {
        Log.debug("Error from yahoo: "+event.getMessage()+", Code:"+event.getCode());
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().getJID(),
                LocaleUtils.getLocalizedString("gateway.yahoo.error", "kraken")+" "+event.getMessage(),
                Message.Type.error
        );
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#inputExceptionThrown(org.openymsg.network.event.SessionExceptionEvent)
     */
    public void inputExceptionThrown(SessionExceptionEvent event) {
        Log.debug("Input error from yahoo: "+event.getMessage(), event.getException());
        if (event.getException().getClass().equals(LoginRefusedException.class)) {
            String reason = LocaleUtils.getLocalizedString("gateway.yahoo.loginrefused", "kraken");
            LoginRefusedException e = (LoginRefusedException)event.getException();
            AuthenticationState state = e.getStatus();
            if (state == AuthenticationState.BADUSERNAME) {
                reason = LocaleUtils.getLocalizedString("gateway.yahoo.unknownuser", "kraken");
                getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                getSession().sessionDisconnectedNoReconnect(reason);
            }
            else if (state == AuthenticationState.BAD) {
                reason = LocaleUtils.getLocalizedString("gateway.yahoo.badpassword", "kraken");
                getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
                getSession().sessionDisconnectedNoReconnect(reason);
            }
            else if (state == AuthenticationState.LOCKED) {
                reason = LocaleUtils.getLocalizedString("gateway.yahoo.accountlocked", "kraken");
                getSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
                getSession().sessionDisconnectedNoReconnect(reason);
            }
            else {
                getSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);
                getSession().sessionDisconnectedNoReconnect(reason);
            }
        }
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#notifyReceived(org.openymsg.network.event.SessionNotifyEvent)
     */
    public void notifyReceived(SessionNotifyEvent event) {
        Log.debug(event.toString());
        if (event.isTyping()) {
            final BaseTransport<YahooBuddy> transport = getSession().getTransport();
            final ChatStateEventSource chatStateEventSource = transport.getChatStateEventSource();
            final JID localJid = getSession().getJID();
            final JID legacyJid = transport.convertIDToJID(event.getFrom());

            if (event.isOn()) {
                chatStateEventSource.isComposing(legacyJid, localJid);
            }
            else {
                chatStateEventSource.isPaused(legacyJid, localJid);
            }
        }
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#contactRequestReceived(org.openymsg.network.event.SessionEvent)
     */
    /** is gone??
    @Override
    public void contactRequestReceived(SessionEvent event) {
        final Presence p = new Presence(Presence.Type.subscribe);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().convertIDToJID(event.getFrom()));
        getSession().getTransport().sendPacket(p);
    }
    */

    /**
     * @see org.openymsg.network.event.SessionAdapter#contactRejectionReceived(org.openymsg.network.event.SessionFriendRejectedEvent)
     */
    public void contactRejectionReceived(SessionFriendRejectedEvent event) {
        // TODO: Is this correct?  unsubscribed for a rejection?
        Log.debug(event.toString());
        Presence p = new Presence(Presence.Type.unsubscribed);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().convertIDToJID(event.getFrom()));
        getSession().getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#chatMessageReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatMessageReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#chatUserUpdateReceived(org.openymsg.network.event.SessionChatEvent)
     */
    public void chatUserUpdateReceived(SessionChatEvent event) {
        Log.debug(event.toString());
    }

    /**
     * A contact has accepted our subscription request
     */
    public void contactAcceptedReceived(SessionFriendAcceptedEvent event) {
        final Set<String> groups = new HashSet<String>();
        groups.add(event.getGroupName());
        final YahooUser user = new YahooUser(event.getFrom());
        // TODO clean up the next line. This implementation of constructor for YahooBuddy seems to be inappropriate here.
        final YahooBuddy buddy = new YahooBuddy(getSession().getBuddyManager(), user, null, groups, null);
        getSession().getBuddyManager().storeBuddy(buddy);
       
        final Presence p = new Presence(Presence.Type.subscribed);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().convertIDToJID(event.getFrom()));
        getSession().getTransport().sendPacket(p);
    }

    /**
     * @see org.openymsg.network.event.SessionAdapter#chatConnectionClosed(org.openymsg.network.event.SessionEvent)
     */
    public void chatConnectionClosed(SessionEvent event) {
        Log.debug(event.toString());
    }

        /**
     * Dispatches an event immediately to all listeners, instead of queuing. it.
     *
     * @param event
     *            The event to be dispatched.
     */
    public void dispatch(FireEvent event) {
        final SessionEvent ev = event.getEvent();

        switch (event.getType()) {
        case LOGOFF:
            connectionClosed(ev);
            break;
        case Y6_STATUS_UPDATE:
            friendsUpdateReceived((SessionFriendEvent) ev);
            break;
        case MESSAGE:
            messageReceived(ev);
            break;
        case X_OFFLINE:
            offlineMessageReceived(ev);
            break;
        case NEWMAIL:
            newMailReceived((SessionNewMailEvent) ev);
            break;
        case CONTACTNEW:
            //TODO: contactRequestReceived((SessionAuthorizationEvent) ev);
            break;
        case CONFDECLINE:
            //TODO: conferenceInviteDeclinedReceived((SessionConferenceDeclineInviteEvent) ev);
            break;
        case CONFINVITE:
            //TODO: conferenceInviteReceived((SessionConferenceInviteEvent) ev);
            break;
        case CONFLOGON:
            //TODO: conferenceLogonReceived((SessionConferenceLogonEvent) ev);
            break;
        case CONFLOGOFF:
            //TODO: conferenceLogoffReceived((SessionConferenceLogoffEvent) ev);
            break;
        case CONFMSG:
            //TODO: conferenceMessageReceived((SessionConferenceMessageEvent) ev);
            break;
        case FILETRANSFER:
            fileTransferReceived((SessionFileTransferEvent) ev);
            break;
        case NOTIFY:
            if (ev instanceof SessionNotifyEvent) {
                notifyReceived((SessionNotifyEvent) ev);
            }
            else {
                // probably a SessionPictureEvent, not handled
            }
            break;
        case LIST:
            listReceived((SessionListEvent) ev);
            break;
        case FRIENDADD:
            SessionFriendEvent friendAddEvent = (SessionFriendEvent) ev;
            if (friendAddEvent.isFailure()) {
                //TODO: friendsUpdateFailureReceived((SessionFriendFailureEvent) ev);
            }
            else {
                friendAddedReceived((SessionFriendEvent) ev);
            }
            break;
        case FRIENDREMOVE:
            friendRemovedReceived((SessionFriendEvent) ev);
            break;
        case GOTGROUPRENAME:
            //TODO: groupRenameReceived((SessionGroupEvent) ev);
            break;
        case CONTACTREJECT:
            contactRejectionReceived((SessionFriendRejectedEvent) ev);
            break;
        case CHATJOIN:
            chatJoinReceived((SessionChatEvent) ev);
            break;
        case CHATEXIT:
            chatExitReceived((SessionChatEvent) ev);
            break;
        case CHATDISCONNECT:
            chatConnectionClosed(ev);
            break;
        case CHATMSG:
            chatMessageReceived((SessionChatEvent) ev);
            break;
        case X_CHATUPDATE:
            chatUserUpdateReceived((SessionChatEvent) ev);
            break;
        case X_ERROR:
            errorPacketReceived((SessionErrorEvent) ev);
            break;
        case X_EXCEPTION:
            inputExceptionThrown((SessionExceptionEvent) ev);
            break;
        case X_BUZZ:
            buzzReceived(ev);
            break;
        case LOGON:
            logonReceived(ev);
            break;
        case X_CHATCAPTCHA:
            //TODO: chatCaptchaReceived((SessionChatEvent) ev);
            break;
        case PICTURE:
            //TODO: pictureReceived((SessionPictureEvent) ev);
            break;
        case Y7_AUTHORIZATION:
            if (ev instanceof SessionAuthorizationEvent) {
                //TODO: contactRequestReceived((SessionAuthorizationEvent) ev);
            }
            else if (ev instanceof SessionFriendRejectedEvent) {
                contactRejectionReceived((SessionFriendRejectedEvent) ev);
            }
            else if (ev instanceof SessionFriendAcceptedEvent) {
                contactAcceptedReceived((SessionFriendAcceptedEvent) ev);
            }
            else {
                throw new IllegalArgumentException("Don't know how to handle '" + event.getType() + "' event: " + event);
            }
            break;
        default:
            throw new IllegalArgumentException("Don't know how to handle service type '" + event.getType() + "'");
        }
    }

    /**
     * Listens for logon event (successful logon)
     * @param ev Sessino event
     */
    private void logonReceived(SessionEvent ev) {
        try {
            getSession().getYahooSession().setStatus(((YahooTransport)getSession().getTransport()).convertXMPPStatusToYahoo(getSession().getPresence()));
        }
        catch (IOException e) {
            Log.debug("Yahoo login caused IO exception:", e);

            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.yahoo.unknownerror", "kraken"),
                    Message.Type.error
            );
            getSession().setLoginStatus(TransportLoginStatus.LOGGED_OUT);
            getSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
            getSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.yahoo.unknownerror", "kraken"));
        }
        getSession().syncUsers();
    }

}
