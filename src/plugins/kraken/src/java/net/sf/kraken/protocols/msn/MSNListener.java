/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.msn;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import net.sf.jml.DisplayPictureListener;
import net.sf.jml.MsnContact;
import net.sf.jml.MsnContactPending;
import net.sf.jml.MsnGroup;
import net.sf.jml.MsnList;
import net.sf.jml.MsnMessenger;
import net.sf.jml.MsnObject;
import net.sf.jml.MsnSwitchboard;
import net.sf.jml.event.MsnContactListListener;
import net.sf.jml.event.MsnEmailListener;
import net.sf.jml.event.MsnMessageListener;
import net.sf.jml.event.MsnMessengerListener;
import net.sf.jml.event.MsnSwitchboardListener;
import net.sf.jml.exception.IncorrectPasswordException;
import net.sf.jml.exception.LoginException;
import net.sf.jml.exception.MsgNotSendException;
import net.sf.jml.exception.MsnProtocolException;
import net.sf.jml.exception.UnknownMessageException;
import net.sf.jml.exception.UnsupportedProtocolException;
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.message.MsnDatacastMessage;
import net.sf.jml.message.MsnEmailActivityMessage;
import net.sf.jml.message.MsnEmailInitEmailData;
import net.sf.jml.message.MsnEmailInitMessage;
import net.sf.jml.message.MsnEmailNotifyMessage;
import net.sf.jml.message.MsnInstantMessage;
import net.sf.jml.message.MsnSystemMessage;
import net.sf.jml.message.MsnUnknownMessage;
import net.sf.jml.message.p2p.DisplayPictureRetrieveWorker;
import net.sf.jml.message.p2p.MsnP2PMessage;
import net.sf.jml.net.Session;
import net.sf.jml.net.SessionListener;
import net.sf.kraken.avatars.Avatar;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;
import net.sf.kraken.util.chatstate.ChatStateEventSource;

import org.apache.log4j.Logger;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * MSN Listener Interface.
 *
 * This handles real interaction with MSN, but mostly is a listener for
 * incoming events from MSN.
 *
 * @author Daniel Henninger
 */
public class MSNListener implements MsnContactListListener, MsnMessageListener, MsnMessengerListener,
									MsnSwitchboardListener, MsnEmailListener, SessionListener {

    static Logger Log = Logger.getLogger(MSNListener.class);

    /**
     * Creates the MSN Listener instance.
     *
     * @param session Session this listener is associated with.
     */
    public MSNListener(MSNSession session) {
        //this.msnSessionRef = new WeakReference<MSNSession>(session);
        this.msnSession = session;
    }

    /**
     * The session this listener is associated with.
     */
    //public WeakReference<MSNSession> msnSessionRef = null;
    public MSNSession msnSession = null;

    /**
     * Returns the MSN session this listener is attached to.
     *
     * @return MSN session we are attached to.
     */
    public MSNSession getSession() {
        //return msnSessionRef.get();
        return msnSession;
    }
    
    /**
     * Handles incoming messages from MSN users.
     */
    public void instantMessageReceived(MsnSwitchboard switchboard, MsnInstantMessage message, MsnContact friend) {
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(friend.getEmail().toString()),
                message.getContent()
        );
        
        // TODO: this will cause a duplicate chat state message to be sent. Prevent this!
        final JID to = getSession().getJID();
        final JID from = getSession().getTransport().convertIDToJID(friend.getEmail().toString());
        getSession().getTransport().getChatStateEventSource().isActive(from, to);
    }

    /**
     * Handles incoming system messages from MSN.
     *
     * @param messenger Messenger session the message is associated with.
     * @param message MSN message.
     */
    public void systemMessageReceived(MsnMessenger messenger, MsnSystemMessage message) {    
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().getJID(),
                message.getContent()
        );
    }

    /**
     * Handles incoming control messages from MSN.
     */
    public void controlMessageReceived(MsnSwitchboard switchboard, MsnControlMessage message, MsnContact friend) {
        if (message.getTypingUser() != null) {
            final JID to = getSession().getJID();
            final JID from = getSession().getTransport().convertIDToJID(friend.getEmail().toString());
            getSession().getTransport().getChatStateEventSource().isComposing(from, to);
        }
        else {
            Log.debug("MSN: Received unknown control msg to " + switchboard + " from " + friend + ": " + message);
        }
    }

    /**
     * Handles incoming datacast messages from MSN.
     */
    public void datacastMessageReceived(MsnSwitchboard switchboard, MsnDatacastMessage message, MsnContact friend) {
        final JID to = getSession().getJID();
        final JID from = getSession().getTransport().convertIDToJID(friend.getEmail().toString());
        if (message.getId() == 1) {
            final String msg = LocaleUtils.getLocalizedString("gateway.msn.nudge", "kraken");
            getSession().getTransport().sendAttentionNotification(to, from, msg);
        }
        else if (message.getId() == 2) {
            final String msg = LocaleUtils.getLocalizedString("gateway.msn.wink", "kraken");
            getSession().getTransport().sendAttentionNotification(to, from, msg);
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

    public void initialEmailNotificationReceived(MsnSwitchboard switchboard, MsnEmailInitMessage message, MsnContact contact) {
        Log.debug("MSN: Got init email notify "+message.getInboxUnread()+" unread message(s)");
        if (JiveGlobals.getBooleanProperty("plugin.gateway.msn.mailnotifications", true) && message.getInboxUnread() > 0) {
            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.initialmail", "kraken", Arrays.asList(message.getInboxUnread())),
                    Message.Type.headline
            );
        }
    }

    public void initialEmailDataReceived(MsnSwitchboard switchboard, MsnEmailInitEmailData message, MsnContact contact) {
        Log.debug("MSN: Got init email data "+message.getInboxUnread()+" unread message(s)");
        if (JiveGlobals.getBooleanProperty("plugin.gateway.msn.mailnotifications", true) && message.getInboxUnread() > 0) {
            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.initialmail", "kraken", Arrays.asList(message.getInboxUnread())),
                    Message.Type.headline
            );
        }
    }

    public void newEmailNotificationReceived(MsnSwitchboard switchboard, MsnEmailNotifyMessage message, MsnContact contact) {
        Log.debug("MSN: Got new email notification from "+message.getFrom()+" <"+message.getFromAddr()+">");
        if (JiveGlobals.getBooleanProperty("plugin.gateway.msn.mailnotifications", true)) {
            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.mail", "kraken", Arrays.asList(message.getFrom(), message.getFromAddr(), message.getSubject())),
                    Message.Type.headline
            );
        }
    }

    public void activityEmailNotificationReceived(MsnSwitchboard switchboard, MsnEmailActivityMessage message, MsnContact contact) {
        Log.debug("MSN: Got email activity notification "+message.getSrcFolder()+" to "+message.getDestFolder());
    }

    /**
     * The user's login has completed and was accepted.
     */
    public void loginCompleted(MsnMessenger messenger) {
        Log.debug("MSN: Login completed for "+messenger.getOwner().getEmail());
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
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
        for (MsnGroup msnGroup : messenger.getContactList().getGroups()) {
            Log.debug("MSN: Got group "+msnGroup);
            getSession().storeGroup(msnGroup);
        }
        for (MsnContact msnContact : messenger.getContactList().getContacts()) {
            Log.debug("MSN: Got contact "+msnContact);
            if (msnContact.isInList(MsnList.FL) && msnContact.getEmail() != null) {
                final MSNBuddy buddy = new MSNBuddy(getSession().getBuddyManager(), msnContact);
                getSession().getBuddyManager().storeBuddy(buddy);
                if (JiveGlobals.getBooleanProperty("plugin.gateway.msn.avatars", true)) {
                    final MsnObject msnAvatar = msnContact.getAvatar();
                    if (msnAvatar != null && (buddy.getAvatar() == null || !buddy.getAvatar().getLegacyIdentifier().equals(msnAvatar.getSha1c()))) {
                        try {
                            messenger.retrieveDisplayPicture(msnAvatar,
                                    new DisplayPictureListener() {

                                        public void notifyMsnObjectRetrieval(
                                                MsnMessenger messenger,
                                                DisplayPictureRetrieveWorker worker,
                                                MsnObject msnObject,
                                                ResultStatus result,
                                                byte[] resultBytes,
                                                Object context) {

                                            Log.debug("MSN: Got avatar retrieval result: "+result);

                                            // Check for the value
                                            if (result == ResultStatus.GOOD) {

                                                try {
                                                    Log.debug("MSN: Found avatar of length "+resultBytes.length);
                                                    Avatar avatar = new Avatar(buddy.getJID(), msnAvatar.getSha1c(), resultBytes);
                                                    buddy.setAvatar(avatar);
                                                }
                                                catch (IllegalArgumentException e) {
                                                    Log.debug("MSN: Got null avatar, ignoring.");
                                                }
                                            }
                                        }
                                    });
                        }
                        catch (Exception e) {
                            Log.debug("MSN: Unable to retrieve MSN avatar: ", e);
                        }

                    }
                    else if (buddy.getAvatar() != null && msnAvatar == null) {
                        buddy.setAvatar(null);
                    }
                }
            }
        }
        getSession().syncUsers();
    }

    /**
     * A friend for this user has changed status.
     */
    public void contactStatusChanged(MsnMessenger messenger, MsnContact friend) {
        if (!friend.isInList(MsnList.FL) || friend.getEmail() == null) {
            // Not in our buddy list, don't care, or null email address.  We need that.
            return;
        }
        if (getSession().getBuddyManager().isActivated()) {
            try {
                final MSNBuddy buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(friend.getEmail().toString()));
                buddy.setPresenceAndStatus(((MSNTransport)getSession().getTransport()).convertMSNStatusToXMPP(friend.getStatus()), friend.getPersonalMessage());
                buddy.setMsnContact(friend);
                if (JiveGlobals.getBooleanProperty("plugin.gateway.msn.avatars", true)) {
                    final MsnObject msnAvatar = friend.getAvatar();
                    if (msnAvatar != null && (buddy.getAvatar() == null || !buddy.getAvatar().getLegacyIdentifier().equals(msnAvatar.getSha1c()))) {
                        try {
                            messenger.retrieveDisplayPicture(msnAvatar,
                                    new DisplayPictureListener() {

                                        public void notifyMsnObjectRetrieval(
                                                MsnMessenger messenger,
                                                DisplayPictureRetrieveWorker worker,
                                                MsnObject msnObject,
                                                ResultStatus result,
                                                byte[] resultBytes,
                                                Object context) {

                                            Log.debug("MSN: Got avatar retrieval result: "+result);

                                            // Check for the value
                                            if (result == ResultStatus.GOOD) {

                                                try {
                                                    Log.debug("MSN: Found avatar of length "+resultBytes.length);
                                                    Avatar avatar = new Avatar(buddy.getJID(), msnAvatar.getSha1c(), resultBytes);
                                                    buddy.setAvatar(avatar);
                                                }
                                                catch (IllegalArgumentException e) {
                                                    Log.debug("MSN: Got null avatar, ignoring.");
                                                }
                                            }
                                        }
                                    });
                        }
                        catch (Exception e) {
                            Log.debug("MSN: Unable to retrieve MSN avatar: ", e);
                        }

                    }
                    else if (buddy.getAvatar() != null && msnAvatar == null) {
                        buddy.setAvatar(null);
                    }
                }
            }
            catch (NotFoundException e) {
                // Not in our contact list.  Ignore.
                Log.debug("MSN: Received presense notification for contact we don't care about: "+friend.getEmail().toString());
            }
        }
        else {
            getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(friend.getEmail().toString()), ((MSNTransport)getSession().getTransport()).convertMSNStatusToXMPP(friend.getStatus()), friend.getPersonalMessage());
        }
    }

    /**
     * Someone added us to their contact list.
     */
    public void contactAddedMe(MsnMessenger messenger, MsnContact friend) {
        Log.debug("MSN: Contact added me: "+ friend.getFriendlyName());
    	
    	final JID from = getSession().getTransport().convertIDToJID(friend.getEmail().toString());
        
        final Presence p = new Presence();
        p.setType(Presence.Type.subscribe);
        p.setTo(getSession().getJID());
        p.setFrom(from);

        getSession().getTransport().sendPacket(p);
    }

    /**
     * Someone removed us from their contact list.
     */
    public void contactRemovedMe(MsnMessenger messenger, MsnContact friend) {
        Log.debug("MSN: Contact removed me: "+ friend.getFriendlyName());

        Presence p = new Presence();
        p.setType(Presence.Type.unsubscribe);
        p.setTo(getSession().getJID());
        p.setFrom(getSession().getTransport().convertIDToJID(friend.getEmail().toString()));
        getSession().getTransport().sendPacket(p);
    }

    
    /**
     * A contact we added has been added to the server.
     */
    public void contactAddCompleted(MsnMessenger messenger, MsnContact contact, MsnList list) {
        Log.debug("MSN: Contact add completed: "+contact);
//        Presence p = new Presence();
//        p.setType(Presence.Type.subscribed);
//        p.setTo(getSession().getJID());
//        p.setFrom(getSession().getTransport().convertIDToJID(contact.getEmail().toString()));
//        getSession().getTransport().sendPacket(p);
        getSession().completedPendingContactAdd(contact);
    }

    /**
     * A contact we removed has been removed from the server.
     */
    public void contactRemoveCompleted(MsnMessenger messenger, MsnContact contact, MsnList list) {
        Log.debug("MSN: Contact remove completed: "+contact);
//        Presence p = new Presence();
//        p.setType(Presence.Type.unsubscribed);
//        p.setTo(getSession().getJID());
//        p.setFrom(getSession().getTransport().convertIDToJID(contact.getEmail().toString()));
//        getSession().getTransport().sendPacket(p);
    }

    /**
     * A group we added has been added to the server.
     */
    public void groupAddCompleted(MsnMessenger messenger, MsnGroup group) {
        Log.debug("MSN: Group add completed: "+group);
        getSession().storeGroup(group);
        getSession().completedPendingGroupAdd(group);
    }

    /**
     * A group we removed has been removed from the server.
     */
    public void groupRemoveCompleted(MsnMessenger messenger, MsnGroup group) {
        Log.debug("MSN: Group remove completed: "+group);
        getSession().unstoreGroup(group);
    }

    /**
     * Owner status has changed.
     */
    public void ownerStatusChanged(MsnMessenger messenger) {
        //getSession().setPresenceAndStatus(((MSNTransport)getSession().getTransport()).convertMSNStatusToXMPP(messenger.getOwner().getStatus()), messenger.getOwner().getPersonalMessage());
    }

    /**
     * Catches MSN exceptions.
     */
    public void exceptionCaught(MsnMessenger messenger, Throwable throwable) {
        Log.debug("MSN: Exception occurred for "+messenger.getOwner().getEmail()+" : "+throwable);        
        if (throwable instanceof IncorrectPasswordException) {
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.msn.passwordincorrect", "kraken"));
        }
        else if (throwable instanceof LoginException) {
            // This can be a number of things, but generally it's a failed username and password.
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.msn.passwordincorrect", "kraken"));
        }
        else if (throwable instanceof MsnProtocolException) {
            Log.debug("MSN: Protocol exception: "+throwable.toString());
        }
        else if (throwable instanceof MsgNotSendException) {
            getSession().getTransport().sendMessage(
                    getSession().getJID(),
                    getSession().getTransport().getJID(),
                    LocaleUtils.getLocalizedString("gateway.msn.sendmsgfailed", "kraken")+" "+throwable.toString(),
                    Message.Type.error
            );
        }
        else if (throwable instanceof UnknownMessageException) {
            Log.debug("MSN: Unknown message: "+throwable.toString());
        }
        else if (throwable instanceof UnsupportedProtocolException) {
            Log.debug("MSN: Protocol error: "+throwable.toString());
        }
        else if (throwable instanceof IOException) {
            Log.debug("MSN: IO error: "+throwable.toString());
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
            getSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.msn.disconnect", "kraken"));
        }
        else {
            Log.debug("MSN: Unknown error: "+throwable.toString(), throwable);
        }
    }

	public void contactAddInGroupCompleted(MsnMessenger arg0, MsnContact arg1,
			MsnGroup arg2) {
		// TODO Auto-generated method stub
		
	}

	public void contactAddedMe(MsnMessenger arg0, MsnContactPending[] arg1) {
		// TODO Auto-generated method stub
		
	}

	public void contactPersonalMessageChanged(MsnMessenger arg0, MsnContact arg1) {
		// TODO Auto-generated method stub
		
	}

	public void contactRemoveFromGroupCompleted(MsnMessenger arg0,
			MsnContact arg1, MsnGroup arg2) {
		// TODO Auto-generated method stub
		
	}

	public void ownerDisplayNameChanged(MsnMessenger arg0) {
		// TODO Auto-generated method stub
		
	}

	public void offlineMessageReceived(String arg0, String arg1, String arg2,
			MsnContact arg3) {
		// TODO Auto-generated method stub
		
	}

	public void p2pMessageReceived(MsnSwitchboard arg0, MsnP2PMessage arg1,
			MsnContact arg2) {
		// TODO Auto-generated method stub
		
	}

	public void logout(MsnMessenger arg0) {
		// TODO Auto-generated method stub
		
	}

	public void contactJoinSwitchboard(MsnSwitchboard arg0, MsnContact arg1) {
		// TODO Auto-generated method stub
		
	}

	public void contactLeaveSwitchboard(MsnSwitchboard arg0, MsnContact arg1) {
        final JID to = getSession().getJID();
        final JID from = getSession().getTransport().convertIDToJID(arg1.getEmail().toString());
        getSession().getTransport().getChatStateEventSource().isGone(from, to);
	}

	public void switchboardClosed(MsnSwitchboard arg0) {
		// TODO Auto-generated method stub
		
	}

	public void switchboardStarted(MsnSwitchboard arg0) {
		// TODO Auto-generated method stub
		
	}
	
    public void exceptionCaught(Session arg0, Throwable t) throws Exception{
        Log.debug("MSN: Session exceptionCaught for "+getSession().getRegistration().getUsername()+" : "+t);
    }

    public void messageReceived(Session arg0, net.sf.jml.net.Message message) throws Exception {
        Log.debug("MSN: Session messageReceived for "+getSession().getRegistration().getUsername()+" : "+message);
        // TODO: Kinda hacky, would like to improve on this later.
        if (message.toString().startsWith("OUT OTH")) {
            // Forced disconnect because account logged in elsewhere
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.msn.otherloggedin", "kraken"));
        }
        else if (message.toString().startsWith("OUT SDH")) {
            // Forced disconnect from server for maintenance
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.LOCKED_OUT);            
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.msn.disconnect", "kraken"));
        }
    }
    
    public void messageSent(Session arg0, net.sf.jml.net.Message message) throws Exception {
        Log.debug("MSN: Session messageSent for "+getSession().getRegistration().getUsername()+" : "+message);
    }



    public void sessionIdle(Session session) throws Exception {
    }

    public void sessionEstablished(Session session) {
        Log.debug("MSN: Session established for "+getSession().getRegistration().getUsername());
    }

    public void sessionTimeout(Session session) {
        // This is used to handle regular pings to the MSN server.  No need to mention it.
    }

    public void sessionClosed(Session session) {
        Log.debug("MSN: Session closed for "+getSession().getRegistration().getUsername());
    }

}
