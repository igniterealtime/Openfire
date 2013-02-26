/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.gadugadu;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;

import pl.mn.communicator.GGException;
import pl.mn.communicator.IIncommingMessage;
import pl.mn.communicator.ILocalStatus;
import pl.mn.communicator.IOutgoingMessage;
import pl.mn.communicator.IRemoteStatus;
import pl.mn.communicator.IUser;
import pl.mn.communicator.LocalUser;
import pl.mn.communicator.MessageStatus;
import pl.mn.communicator.User;
import pl.mn.communicator.event.ConnectionListener;
import pl.mn.communicator.event.ContactListListener;
import pl.mn.communicator.event.LoginFailedEvent;
import pl.mn.communicator.event.LoginListener;
import pl.mn.communicator.event.MessageListener;
import pl.mn.communicator.event.UserListener;

/**
 * @author Daniel Henninger
 */
public class GaduGaduListener implements ConnectionListener, LoginListener, MessageListener, ContactListListener, UserListener {

    static Logger Log = Logger.getLogger(GaduGaduListener.class);

    GaduGaduListener(GaduGaduSession session) {
        this.gadugaduSessionRef = new WeakReference<GaduGaduSession>(session);
    }

    WeakReference<GaduGaduSession> gadugaduSessionRef;

    public GaduGaduSession getSession() {
        return gadugaduSessionRef.get();
    }

    public void connectionEstablished() {
        Log.debug("GaduGadu: Connection established");
        try {
            getSession().iSession.getLoginService().login(getSession().loginContext);
        }
        catch (GGException e) {
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
            getSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.gadugadu.unabletoconnect", "kraken"));
        }
    }

    public void connectionClosed() {
        Log.debug("GaduGadu: Connection closed");
        getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
    }

    public void connectionError(Exception exception) {
        Log.debug("GaduGadu: Connection error:", exception);
        getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
        getSession().setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
        getSession().sessionDisconnected(LocaleUtils.getLocalizedString("gateway.gadugadu.connectionlost", "kraken"));
    }

    public void loginOK() {
        Log.debug("GaduGadu: Login successful");
        getSession().setLoginStatus(TransportLoginStatus.LOGGED_IN);
        getSession().loadRoster();

        try {
            getSession().getTransport().syncLegacyRoster(getSession().getJID(), getSession().getBuddyManager().getBuddies());
        }
        catch (UserNotFoundException e) {
            Log.debug("GaduGadu: User not found while syncing legacy roster:", e);
        }
        getSession().getBuddyManager().activate();

        // If we do not have any pseudo roster items, lets see what we can get from the server.
        if (getSession().getPseudoRoster().getContacts().isEmpty()) {
            try {
                getSession().iSession.getContactListService().importContactList();
            }
            catch (GGException e) {
                Log.debug("GaduGadu: Unable to retrieve contact list.");
            }
        }
    }

    public void loginFailed(LoginFailedEvent event) {
        Log.debug("GaduGadu: Login failed: "+event);
        if (event.getReason() == LoginFailedEvent.INCORRECT_PASSWORD) {
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.USERNAME_OR_PASSWORD_INCORRECT);
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.gadugadu.passwordincorrect", "kraken"));
        }
        else {
            getSession().setLoginStatus(TransportLoginStatus.DISCONNECTED);
            getSession().setFailureStatus(ConnectionFailureReason.UNKNOWN);
            getSession().sessionDisconnectedNoReconnect(LocaleUtils.getLocalizedString("gateway.gadugadu.loginfailed", "kraken"));
        }

    }

    public void loggedOut() {
        Log.debug("GaduGadu: Logged out");
        // We take care of this elsewhere.
    }

    public void messageSent(IOutgoingMessage message) {
        Log.debug("GaduGadu: Message sent: "+message);
        // Don't care for now
    }

    public void messageArrived(IIncommingMessage message) {
        Log.debug("GaduGadu: Message arrived: "+message);
        getSession().getTransport().sendMessage(
                getSession().getJID(),
                getSession().getTransport().convertIDToJID(Integer.toString(message.getRecipientUin())),
                message.getMessageBody()
        );
    }

    public void messageDelivered(int uin, int messageID, MessageStatus deliveryStatus) {
        Log.debug("GaduGadu: Message delivered");
        // Don't care for now
    }

    public void contactListExported() {
        Log.debug("GaduGadu: Contact list exported");
        // Not really sure what this is
    }

    @SuppressWarnings("unchecked")
    public void contactListReceived(Collection collection) {
        Log.debug("GaduGadu: Contact list received: "+collection);
        for (Object localUserObj : collection) {
            LocalUser localUser = (LocalUser)localUserObj;
            if (localUser.getUin() > 0) {
                String ggContact = Integer.toString(localUser.getUin());
                String nickname = localUser.getDisplayName();
                List<String> groups = new ArrayList<String>();
                groups.add(localUser.getGroup());
                if (getSession().getPseudoRoster().hasItem(ggContact)) {
                    PseudoRosterItem rosterItem = getSession().getPseudoRoster().getItem(ggContact);
                    rosterItem.setNickname(nickname);
                    rosterItem.setGroups(groups);
                }
                else {
                    PseudoRosterItem rosterItem = getSession().getPseudoRoster().createItem(ggContact, nickname, groups);
                    getSession().getBuddyManager().storeBuddy(new GaduGaduBuddy(getSession().getBuddyManager(), localUser, rosterItem));
                }
                try {
                    getSession().iSession.getPresenceService().addMonitoredUser(new User(localUser.getUin()));
                }
                catch (GGException e) {
                    Log.debug("GaduGadu: Error while setting up user to be monitored:", e);
                }
            }
            else {
                Log.debug("GaduGadu: Ignoring user with UIN less than -1: "+localUser);
            }
        }
    }

    public void localStatusChanged(ILocalStatus iLocalStatus) {
        Log.debug("GaduGadu: Local status changed: "+iLocalStatus);
        getSession().setPresenceAndStatus(((GaduGaduTransport)getSession().getTransport()).convertGaduGaduStatusToXMPP(iLocalStatus.getStatusType()), "");
    }

    public void userStatusChanged(IUser iUser, IRemoteStatus iRemoteStatus) {
        Log.debug("GaduGadu: User status changed for "+iUser+" to "+iRemoteStatus);
        if (getSession().getBuddyManager().isActivated()) {
            try {
                GaduGaduBuddy buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(Integer.toString(iUser.getUin())));
                buddy.setPresenceAndStatus(((GaduGaduTransport)getSession().getTransport()).convertGaduGaduStatusToXMPP(iRemoteStatus.getStatusType()), iRemoteStatus.getDescription());
            }
            catch (NotFoundException e) {
                // Not in our contact list.  Ignore.
                Log.debug("GaduGadu: Received presense notification for contact we don't care about: "+iUser.getUin());
            }
        }
        else {
            getSession().getBuddyManager().storePendingStatus(getSession().getTransport().convertIDToJID(Integer.toString(iUser.getUin())), ((GaduGaduTransport)getSession().getTransport()).convertGaduGaduStatusToXMPP(iRemoteStatus.getStatusType()), iRemoteStatus.getDescription());
        }
    }

}
