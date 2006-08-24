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

import net.sf.jml.*;
import net.sf.jml.impl.MsnMessengerFactory;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Represents a MSN session.
 * 
 * This is the interface with which the base transport functionality will
 * communicate with MSN.
 *
 * @author Daniel Henninger
 */
public class MSNSession extends TransportSession {

    /**
     * Create a MSN Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public MSNSession(Registration registration, JID jid, MSNTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        msnMessenger = MsnMessengerFactory.createMsnMessenger(registration.getUsername(), registration.getPassword());
        msnMessenger.setSupportedProtocol(new MsnProtocol[] { MsnProtocol.MSNP11 });
    }

    /**
     * MSN session
     */
    private MsnMessenger msnMessenger = null;

    /**
     * MSN contacts/friends.
     */
    private HashMap<String,MsnContact> msnContacts = new HashMap<String,MsnContact>();

    /**
     * Login status
     */
    private boolean loginStatus = false;

    /**
     * Log in to MSN.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Long representation of status.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!this.isLoggedIn()) {
            msnMessenger.getOwner().setInitStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
            msnMessenger.setLogIncoming(true);
            msnMessenger.setLogOutgoing(true);
            msnMessenger.addListener(new MSNListener(this));
            msnMessenger.login();
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        if (this.isLoggedIn()) {
            msnMessenger.logout();
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
            loginStatus = false;
        }
    }

    /**
     * Retrieves the manager for this session.
     */
    public MsnMessenger getManager() {
        return msnMessenger;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#isLoggedIn()
     */
    public Boolean isLoggedIn() {
        return loginStatus;
    }

    /**
     * Sets login status flag (am i logged in or not)
     */
    public void setLoginStatus(Boolean status) {
        loginStatus = status;
    }

    /**
     * Records information about a person on the user's contact list.
     */
    public void storeFriend(MsnContact msnContact) {
        msnContacts.put(msnContact.getEmail().toString(), msnContact);
    }

    /**
     * Syncs up the MSN roster with the jabber roster.
     */
    public void syncUsers() {
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (MsnContact friend : msnContacts.values()) {
            Log.debug("Syncing contact " + friend);
            ArrayList<String> friendGroups = new ArrayList<String>();
            for (MsnGroup group : friend.getBelongGroups()) {
                Log.debug("   Found group " + group);
                friendGroups.add(group.getGroupName());
            }
            if (friendGroups.size() < 1) {
                friendGroups.add("MSN Contacts");
            }
            legacyusers.add(new TransportBuddy(friend.getEmail().toString(), friend.getDisplayName(), friendGroups.get(0)));
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync MSN contact list for " + getJID());
        }

        // Lets send initial presence statuses
        for (MsnContact friend : msnContacts.values()) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(friend.getEmail().toString()));
            ((MSNTransport)getTransport()).setUpPresencePacket(p, friend.getStatus());
            getTransport().sendPacket(p);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.xmpp.packet.JID)
     */
    public void addContact(JID jid) {
        // @todo check jabber group and use it
        msnMessenger.addFriend(Email.parseStr(getTransport().convertJIDToID(jid)), getTransport().convertJIDToID(jid));
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.xmpp.packet.JID)
     */
    public void removeContact(JID jid) {
        // @todo check jabber group and use it
        msnMessenger.removeFriend(Email.parseStr(getTransport().convertJIDToID(jid)), false);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        msnMessenger.sendText(Email.parseStr(getTransport().convertJIDToID(jid)), message);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        MsnContact msnContact = msnContacts.get(getTransport().convertJIDToID(jid));
        if (msnContact == null) {
            return;
        }
        Presence p = new Presence();
        p.setTo(getJID());
        p.setFrom(getTransport().convertIDToJID(msnContact.getEmail().toString()));
        ((MSNTransport)getTransport()).setUpPresencePacket(p, msnContact.getStatus());
        getTransport().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        msnMessenger.getOwner().setStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        for (MsnContact friend : msnContacts.values()) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(friend.getEmail().toString()));
            ((MSNTransport)getTransport()).setUpPresencePacket(p, friend.getStatus());
            getTransport().sendPacket(p);
        }
    }

}
