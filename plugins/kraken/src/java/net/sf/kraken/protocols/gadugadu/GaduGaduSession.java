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

import java.util.*;

import net.sf.kraken.pseudoroster.PseudoRoster;
import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.pseudoroster.PseudoRosterManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.PresenceType;

import org.apache.log4j.Logger;
import org.xmpp.packet.JID;

import pl.mn.communicator.GGConfiguration;
import pl.mn.communicator.GGException;
import pl.mn.communicator.IServer;
import pl.mn.communicator.ISession;
import pl.mn.communicator.LocalStatus;
import pl.mn.communicator.LocalUser;
import pl.mn.communicator.LoginContext;
import pl.mn.communicator.OutgoingMessage;
import pl.mn.communicator.Session;
import pl.mn.communicator.User;

/**
 * Represents a Gadu-Gadu session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with Gadu-Gadu.
 *
 * @author Daniel Henninger
 */
public class GaduGaduSession extends TransportSession<GaduGaduBuddy> {

    static Logger Log = Logger.getLogger(GaduGaduSession.class);

    /**
     * Create a Gadu-Gadu Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public GaduGaduSession(Registration registration, JID jid, GaduGaduTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        idNumber = Integer.parseInt(registration.getUsername());
        pseudoRoster = PseudoRosterManager.getInstance().getPseudoRoster(registration);
    }

    /* Session instance */
    ISession iSession;

    /* Login context */
    LoginContext loginContext;

    /* Listener */
    GaduGaduListener listener;

    /* ID number of account */
    int idNumber;

    /* Locally stored roster -- GaduGadu's own roster doesn't work well */
    private PseudoRoster pseudoRoster;

    public PseudoRoster getPseudoRoster() {
        return pseudoRoster;
    }    

    /**
     * Loads the stored roster and adds them to have their presence monitored.
     */
    void loadRoster() {
        for (String contact : pseudoRoster.getContacts()) {
            User user = new User(Integer.valueOf(contact));
            // Load actual buddy roster
            getBuddyManager().storeBuddy(new GaduGaduBuddy(getBuddyManager(), pseudoRoster.getItem(contact)));
            // Set up each roster item to be monitored for presence
            try {
                iSession.getPresenceService().addMonitoredUser(user);
            }
            catch (GGException e) {
                Log.debug("GaduGadu: Error while setting up user to be monitored during add:", e);
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
        setPendingPresenceAndStatus(presenceType, verboseStatus);
        if (!isLoggedIn()) {
            loginContext = new LoginContext(idNumber, registration.getPassword());
            listener = new GaduGaduListener(this);
            iSession = new Session(new GGConfiguration());
            iSession.getConnectionService().addConnectionListener(listener);
            iSession.getLoginService().addLoginListener(listener);
            iSession.getMessageService().addMessageListener(listener);
            iSession.getContactListService().addContactListListener(listener);
            iSession.getPresenceService().addUserListener(listener);
            try {
                IServer iServer = iSession.getConnectionService().lookupServer(idNumber);
                iSession.getConnectionService().connect(iServer);
            }
            catch (GGException e) {
                Log.debug("GaduGadu: Unable to establish connection:", e);
                setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                sessionDisconnected("Unable to establish connection.");
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public void logOut() {
        try {
            iSession.getLoginService().logout();
        }
        catch (Exception e) {
            // Ignore
        }
        cleanUp();
        sessionDisconnectedNoReconnect(null);
    }

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public void cleanUp() {
        try {
            iSession.getConnectionService().disconnect();
        }
        catch (Exception e) {
            // Ignore
        }
        if (listener != null) {
            try {
                iSession.getConnectionService().removeConnectionListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                iSession.getLoginService().removeLoginListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                iSession.getMessageService().removeMessageListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                iSession.getContactListService().removeContactListlistener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            try {
                iSession.getPresenceService().removeUserListener(listener);
            }
            catch (Exception e) {
                // Ignore
            }
            
            listener = null;
        }
        iSession = null;
        loginContext = null;
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
        String contact = getTransport().convertJIDToID(jid);
        if (nickname == null || nickname.length() < 1) {
            nickname = jid.toBareJID();
        }
        LocalUser newUser = new LocalUser();
        newUser.setUin(Integer.parseInt(contact));
        newUser.setDisplayName(nickname);
        if (groups.size() > 0) {
            newUser.setGroup(groups.get(0));
        }
        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(contact)) {
            rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(nickname);
            rosterItem.setGroups(groups);
        }
        else {
            rosterItem = pseudoRoster.createItem(contact, nickname, groups);
        }
        getBuddyManager().storeBuddy(new GaduGaduBuddy(getBuddyManager(), newUser, rosterItem));
        try {
            iSession.getPresenceService().addMonitoredUser(new User(newUser.getUin()));
        }
        catch (GGException e) {
            Log.debug("GaduGadu: Error while setting up user to be monitored during add:", e);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(GaduGaduBuddy contact) {
        String ggContact = getTransport().convertJIDToID(contact.getJID());
        pseudoRoster.removeItem(ggContact);
        for (GaduGaduBuddy buddy : getBuddyManager().getBuddies()) {
            if (buddy.getJID().equals(contact.getJID())) {
                LocalUser byeUser = buddy.toLocalUser();
                try {
                    iSession.getPresenceService().removeMonitoredUser(new User(byeUser.getUin()));
                }
                catch (GGException e) {
                    Log.debug("GaduGadu: Error while removing user from being monitored during delete:", e);
                }
                break;
            }
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(GaduGaduBuddy contact) {
        String ggContact = getTransport().convertJIDToID(contact.getJID());
        if (pseudoRoster.hasItem(ggContact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(ggContact);
            rosterItem.setNickname(contact.getNickname());
            rosterItem.setGroups((List<String>)contact.getGroups());
        }
        else {
            PseudoRosterItem rosterItem = pseudoRoster.createItem(ggContact, contact.getNickname(), (List<String>)contact.getGroups());
            getBuddyManager().storeBuddy(new GaduGaduBuddy(getBuddyManager(), rosterItem));
        }
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("GaduGadu: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
        // TODO: Currently unimplemented
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
        try {
            iSession.getMessageService().sendMessage(OutgoingMessage.createNewMessage(Integer.parseInt(getTransport().convertJIDToID(jid)), message));
        }
        catch (GGException e) {
            Log.debug("GaduGadu: Exception while sending message:", e);
        }
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID,net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, byte[] data) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        try {
            iSession.getPresenceService().setStatus(new LocalStatus(((GaduGaduTransport)getTransport()).convertXMPPStatusToGaduGadu(presenceType, (verboseStatus != null && !verboseStatus.equals(""))), verboseStatus, new Date()));
        }
        catch (GGException e) {
            Log.debug("GaduGadu: Exception while setting status:", e);
        }
    }

}
