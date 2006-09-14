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

import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.awt.*;

import rath.msnm.MSNMessenger;
import rath.msnm.SwitchboardSession;
import rath.msnm.msg.MimeMessage;
import rath.msnm.entity.MsnFriend;
import rath.msnm.entity.Group;
import rath.msnm.entity.ServerInfo;

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

        msnMessenger = new MSNMessenger(registration.getUsername(), registration.getPassword());
    }

    /**
     * MSN session
     */
    private MSNMessenger msnMessenger = null;

    /**
     * MSN contacts/friends.
     */
    private ConcurrentHashMap<String,MsnFriend> msnContacts = new ConcurrentHashMap<String,MsnFriend>();

    /**
     * MSN groups.
     */
    private ConcurrentHashMap<String, Group> msnGroups = new ConcurrentHashMap<String,Group>();

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
            msnMessenger.setInitialStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
            msnMessenger.addMsnListener(new MSNListener(this));
            msnMessenger.login();
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        if (this.isLoggedIn()) {
            msnMessenger.logout();
        }
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        loginStatus = false;
    }

    /**
     * Retrieves the manager for this session.
     */
    public MSNMessenger getManager() {
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
    public void storeFriend(MsnFriend msnContact) {
        msnContacts.put(msnContact.getLoginName(), msnContact);
    }

    /**
     * Records information about a group on the user's contact list.
     */
    public void storeGroup(Group msnGroup) {
        msnGroups.put(msnGroup.getName(), msnGroup);
    }

    /**
     * Syncs up the MSN roster with the jabber roster.
     */
    public void syncUsers() {
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (MsnFriend friend : msnContacts.values()) {
            ArrayList<String> friendGroups = new ArrayList<String>();
            if (friendGroups.size() < 1) {
                friendGroups.add("MSN Contacts");
            }
            legacyusers.add(new TransportBuddy(friend.getLoginName(), friend.getFriendlyName(), friendGroups.get(0)));
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync MSN contact list for " + getJID());
        }

        // Lets send initial presence statuses
        for (MsnFriend friend : msnContacts.values()) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(friend.getLoginName()));
            ((MSNTransport)getTransport()).setUpPresencePacket(p, friend.getStatus());
            getTransport().sendPacket(p);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
//        String contact = getTransport().convertJIDToID(item.getJid());
//        String nickname = getTransport().convertJIDToID(item.getJid());
//        if (item.getNickname() != null && !item.getNickname().equals("")) {
//            nickname = item.getNickname();
//        }
//        try {
//            msnMessenger.addFriend(contact);
//        }
//        catch (IOException e) {
//            Log.error("Error while adding MSN contact.");
//        }
//        syncContactGroups(contact, item.getGroups());
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
//        Email contact = Email.parseStr(getTransport().convertJIDToID(item.getJid()));
//        MsnContact msnContact = msnContacts.get(contact.toString());
//        for (MsnGroup msnGroup : msnContact.getBelongGroups()) {
//            msnMessenger.removeFriend(contact, msnGroup.getGroupId());
//        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
//        Email contact = Email.parseStr(getTransport().convertJIDToID(item.getJid()));
//        String nickname = getTransport().convertJIDToID(item.getJid());
//        if (item.getNickname() != null && !item.getNickname().equals("")) {
//            nickname = item.getNickname();
//        }
//        msnMessenger.renameFriend(contact, nickname);
//        syncContactGroups(contact, item.getGroups());
    }

    /**
     * Given a legacy contact and a list of groups, makes sure that the list is in sync with
     * the actual group list.
     *
     * @param contact Email address of contact.
     * @param groups List of groups contact should be in.
     */
//    public void syncContactGroups(String contact, List<String> groups) {
//        if (groups.isEmpty()) {
//            groups.add("Transport Buddies");
//        }
//        MsnFriend msnContact = msnContacts.get(contact.toString());
//        // Create groups that do not currently exist.
//        for (String group : groups) {
//            if (!msnGroups.containsKey(group)) {
//                try {
//                    msnMessenger.addGroup(group);
//                }
//                catch (IOException e) {
//                    Log.error("Error while adding MSN group.");
//                }
//            }
//        }
//        // Lets update our list of groups.
//        for (Group msnGroup : msnMessenger.getContactList().getGroups()) {
//            storeGroup(msnGroup);
//        }
//        // Make sure contact belongs to groups that we want.
//        for (String group : groups) {
//            MsnGroup msnGroup = msnGroups.get(group);
//            if (!msnContact.belongGroup(msnGroup)) {
//                msnMessenger.copyFriend(contact, group);
//            }
//        }
//        // Now we will clean up groups that we should no longer belong to.
//        for (MsnGroup msnGroup : msnContact.getBelongGroups()) {
//            if (!groups.contains(msnGroup.getGroupName())) {
//                msnMessenger.removeFriend(contact, msnGroup.getGroupId());
//            }
//        }
//    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        String contact = getTransport().convertJIDToID(jid);
        SwitchboardSession session = msnMessenger.findSwitchboardSession(contact);
        if (session == null) {
            Log.debug("New session being created.");
            session = new SwitchboardSession(msnMessenger, ServerInfo.getDefaultServerInfo(), contact);
            session.
        }
        try {
            MimeMessage mimeMessage = new MimeMessage(message, Color.black);
            mimeMessage.setKind(MimeMessage.KIND_MESSAGE);
            mimeMessage.setFontName("");
//            session.sendInstantMessage(mimeMessage);
            msnMessenger.sendMessage(contact, mimeMessage);
        }
        catch (IOException e) {
            Log.error("Failed to send MSN message.");
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        // We don't care.
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        MsnFriend msnContact = msnContacts.get(getTransport().convertJIDToID(jid));
        if (msnContact == null) {
            return;
        }
        Presence p = new Presence();
        p.setTo(getJID());
        p.setFrom(getTransport().convertIDToJID(msnContact.getLoginName()));
        ((MSNTransport)getTransport()).setUpPresencePacket(p, msnContact.getStatus());
        getTransport().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        if (isLoggedIn()) {
            try {
                msnMessenger.getOwner().setStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
            }
            catch (IllegalStateException e) {
                // Hrm, not logged in?  Lets fix that.
                msnMessenger.setInitialStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
                msnMessenger.login();
            }
        }
        else {
            // Hrm, not logged in?  Lets fix that.
            msnMessenger.setInitialStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
            msnMessenger.login();
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        for (MsnFriend friend : msnContacts.values()) {
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(friend.getLoginName()));
            ((MSNTransport)getTransport()).setUpPresencePacket(p, friend.getStatus());
            getTransport().sendPacket(p);
        }
    }

}
