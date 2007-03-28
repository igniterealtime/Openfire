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

import net.sf.jml.*;
import net.sf.jml.message.MsnControlMessage;
import net.sf.jml.impl.BasicMessenger;
import net.sf.jml.impl.MsnMessengerFactory;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.*;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

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

        if (Email.parseStr(registration.getUsername()) == null) {
            Message m = new Message();
            m.setType(Message.Type.error);
            m.setTo(getJID());
            m.setFrom(getTransport().getJID());
            m.setBody(LocaleUtils.getLocalizedString("gateway.msn.illegalaccount", "gateway")+" "+registration.getUsername());
            return;
        }

        Log.debug("Creating MSN session for " + registration.getUsername());
        msnMessenger = MsnMessengerFactory.createMsnMessenger(registration.getUsername(), registration.getPassword());
        ((BasicMessenger)msnMessenger).addSessionListener(new MsnSessionListener(this));
        msnMessenger.setSupportedProtocol(new MsnProtocol[] { MsnProtocol.MSNP11 });
    }

    /**
     * MSN session
     */
    private MsnMessenger msnMessenger = null;

    /**
     * MSN contacts/friends.
     */
    private ConcurrentHashMap<String,MsnContact> msnContacts = new ConcurrentHashMap<String,MsnContact>();

    /**
     * MSN groups.
     */
    private ConcurrentHashMap<String,MsnGroup> msnGroups = new ConcurrentHashMap<String,MsnGroup>();

    /**
     * Pending MSN groups and contact to be added.
     */
    private ConcurrentHashMap<String,ArrayList<Email>> msnPendingGroups = new ConcurrentHashMap<String,ArrayList<Email>>();

    /**
     * Log in to MSN.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Long representation of status.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!this.isLoggedIn()) {
            try {
                Log.debug("Logging in to MSN session for " + msnMessenger.getOwner().getEmail());
                setLoginStatus(TransportLoginStatus.LOGGING_IN);
                msnMessenger.getOwner().setInitStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
                msnMessenger.setLogIncoming(false);
                msnMessenger.setLogOutgoing(false);
                msnMessenger.addListener(new MSNListener(this));
                ((BasicMessenger)msnMessenger).login(
                        JiveGlobals.getProperty("plugin.gateway.msn.connecthost", "messenger.hotmail.com"),
                        JiveGlobals.getIntProperty("plugin.gateway.msn.connectport", 1863));
            }
            catch (Exception e) {
                Log.error("MSN user is not able to log in: " + msnMessenger.getOwner().getEmail(), e);                
            }
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        if (this.isLoggedIn()) {
            setLoginStatus(TransportLoginStatus.LOGGING_OUT);            
            msnMessenger.logout();
        }
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    /**
     * Retrieves the manager for this session.
     *
     * @return Messenger instance the session is associated with.
     */
    public MsnMessenger getManager() {
        return msnMessenger;
    }

    /**
     * Records information about a person on the user's contact list.
     *
     * @param msnContact MSN contact we are storing a copy of.
     */
    public void storeFriend(MsnContact msnContact) {
        msnContacts.put(msnContact.getEmail().toString(), msnContact);
    }

    /**
     * Removes information about a person from the user's contact list.
     *
     * @param msnContact MSN contact we are removing a copy of.
     */
    public void unstoreFriend(MsnContact msnContact) {
        msnContacts.remove(msnContact.getEmail().toString());
    }

    /**
     * Records information about a group on the user's contact list.
     *
     * @param msnGroup MSN group we are storing a copy of.
     */
    public void storeGroup(MsnGroup msnGroup) {
        msnGroups.put(msnGroup.getGroupName(), msnGroup);
    }

    /**
     * Removes information about a group from the user's contact list.
     *
     * @param msnGroup MSN group we are removing a copy of.
     */
    public void unstoreGroup(MsnGroup msnGroup) {
        msnGroups.remove(msnGroup.getGroupName());
    }

    /**
     * Records a member of a pending new group that will be added later.
     *
     * @param groupName Name of group to be stored.
     * @param member Email address of member to be added.
     */
    public void storePendingGroup(String groupName, Email member) {
        if (!msnPendingGroups.containsKey(groupName)) {
            ArrayList<Email> newList = new ArrayList<Email>();
            newList.add(member);
            msnPendingGroups.put(groupName, newList);
        }
        else {
            ArrayList<Email> list = msnPendingGroups.get(groupName);
            list.add(member);
            msnPendingGroups.put(groupName, list);
        }
    }

    /**
     * Completes the addition of groups to a new contact after the contact has been created.
     *
     * @param msnContact Contact that was added.
     */
    public void completedPendingContactAdd(MsnContact msnContact) {
        try {
            Roster roster = getTransport().getRosterManager().getRoster(getJID().getNode());
            Email contact = msnContact.getEmail();
            JID contactJID = getTransport().convertIDToJID(contact.toString());
            RosterItem item = roster.getRosterItem(contactJID);
            syncContactGroups(contact, item.getGroups());
            unlockRoster(contactJID.toString());
        }
        catch (UserNotFoundException e) {
            Log.error("MSN: Unable to find roster when adding pendingcontact for "+getJID());
            Email contact = msnContact.getEmail();
            JID contactJID = getTransport().convertIDToJID(contact.toString());
            unlockRoster(contactJID.toString());
        }
    }

    /**
     * Completes the addition of a contact to a new group after the group has been created.
     *
     * @param msnGroup Group that was added.
     */
    public void completedPendingGroupAdd(MsnGroup msnGroup) {
        if (!msnPendingGroups.containsKey(msnGroup.getGroupName())) {
            // Nothing to do, no pending.
            return;
        }
        try {
            Roster roster = getTransport().getRosterManager().getRoster(getJID().getNode());
            for (Email contact : msnPendingGroups.get(msnGroup.getGroupName())) {
                JID contactJID = getTransport().convertIDToJID(contact.toString());
                RosterItem item = roster.getRosterItem(contactJID);
                lockRoster(contactJID.toString());
                syncContactGroups(contact, item.getGroups());
                unlockRoster(contactJID.toString());
            }
        }
        catch (UserNotFoundException e) {
            Log.error("MSN: Unable to find roster when adding pending group contacts for "+getJID());
        }
    }

    /**
     * Syncs up the MSN roster with the jabber roster.
     */
    public void syncUsers() {
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (MsnContact friend : msnContacts.values()) {
            ArrayList<String> friendGroups = new ArrayList<String>();
            for (MsnGroup group : friend.getBelongGroups()) {
                Log.debug("MSN: Found belong group for "+friend+" as "+group);
                friendGroups.add(group.getGroupName());
            }
            if (friendGroups.size() > 0) {
                legacyusers.add(new TransportBuddy(friend.getEmail().toString(), friend.getFriendlyName(), friendGroups.get(0)));
            }
            else {
                legacyusers.add(new TransportBuddy(friend.getEmail().toString(), friend.getFriendlyName(), null));
            }
        }
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync MSN contact list for " + getJID(), e);
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
     * @see org.jivesoftware.openfire.gateway.TransportSession#addContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        Email contact = Email.parseStr(getTransport().convertJIDToID(item.getJid()));
        if (contact == null) {
            Log.error("MSN: Unable to update illegal contact "+item.getJid());
            return;
        }
        String nickname = getTransport().convertJIDToID(item.getJid());
        if (item.getNickname() != null && !item.getNickname().equals("")) {
            nickname = item.getNickname();
        }
        lockRoster(item.getJid().toString());
        msnMessenger.addFriend(contact, nickname);
        try {
            getTransport().addOrUpdateRosterItem(getJID(), item.getJid(), nickname, item.getGroups());
        }
        catch (UserNotFoundException e) {
            Log.error("MSN: Unable to find roster when adding contact.");
        }
//        syncContactGroups(contact, item.getGroups());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#removeContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        Email contact = Email.parseStr(getTransport().convertJIDToID(item.getJid()));
        if (contact == null) {
            Log.error("MSN: Unable to update illegal contact "+item.getJid());
            return;
        }
        lockRoster(item.getJid().toString());
        msnMessenger.removeFriend(contact, false);
        unlockRoster(item.getJid().toString());
        msnContacts.remove(contact.toString());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        Email contact = Email.parseStr(getTransport().convertJIDToID(item.getJid()));
        if (contact == null) {
            Log.error("MSN: Unable to update illegal contact "+item.getJid());
            return;
        }
        String nickname = getTransport().convertJIDToID(item.getJid());
        if (item.getNickname() != null && !item.getNickname().equals("")) {
            nickname = item.getNickname();
        }
        MsnContact msnContact = msnContacts.get(contact.toString());
        if (msnContact == null) {
            return;
        }
        lockRoster(item.getJid().toString());
        if (!msnContact.getFriendlyName().equals(nickname)) {
            msnMessenger.renameFriend(contact, nickname);
        }
        syncContactGroups(contact, item.getGroups());
        unlockRoster(item.getJid().toString());
    }

    /**
     * Given a legacy contact and a list of groups, makes sure that the list is in sync with
     * the actual group list.
     *
     * @param contact Email address of contact.
     * @param groups List of groups contact should be in.
     */
    public void syncContactGroups(Email contact, List<String> groups) {
        MsnContact msnContact = msnContacts.get(contact.toString());
        if (msnContact == null) {
            return;
        }
        // Create groups that do not currently exist.
        for (String group : groups) {
            if (!msnGroups.containsKey(group)) {
                Log.debug("MSN: Group "+group+" is a new group, creating.");
                msnMessenger.addGroup(group);
                // Ok, short circuit here, we need to wait for this group to be added.  We'll be back.
                storePendingGroup(group, contact);
                return;
            }
        }
        // Make sure contact belongs to groups that we want.
        for (String group : groups) {
            Log.debug("MSN: Found "+contact+" should belong to group "+group);
            MsnGroup msnGroup = msnGroups.get(group);
            if (msnGroup != null && !msnContact.belongGroup(msnGroup)) {
                Log.debug("MSN: "+contact+" does not belong to "+group+", copying.");
                msnMessenger.copyFriend(contact, msnGroup.getGroupId());
            }
        }
        // Now we will clean up groups that we should no longer belong to.
        for (MsnGroup msnGroup : msnContact.getBelongGroups()) {
            Log.debug("MSN: Found "+contact+" belongs to group "+msnGroup.getGroupName());
            if (!groups.contains(msnGroup.getGroupName())) {
                Log.debug("MSN: "+contact+" should not belong to "+msnGroup.getGroupName()+", removing.");
                msnMessenger.removeFriend(contact, msnGroup.getGroupId());
            }
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        msnMessenger.sendText(Email.parseStr(getTransport().convertJIDToID(jid)), message);
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        // We don't care.
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendChatState(org.xmpp.packet.JID, org.jivesoftware.openfire.gateway.ChatStateType)
     */
    public void sendChatState(JID jid, ChatStateType chatState) {
        if (chatState.equals(ChatStateType.composing)) {
            Email jidEmail = Email.parseStr(getTransport().convertJIDToID(jid));
            MsnControlMessage mcm = new MsnControlMessage();
            mcm.setTypingUser(msnMessenger.getOwner().getEmail().getEmailAddress());
            for (MsnSwitchboard sb : msnMessenger.getActiveSwitchboards()) {
                if (sb.containContact(jidEmail)) {
                    sb.sendMessage(mcm, true);
                }
            }
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        if (isLoggedIn()) {
            MsnContact msnContact = msnContacts.get(getTransport().convertJIDToID(jid));
            Presence p = new Presence();
            p.setTo(getJID());
            if (msnContact != null) {
                p.setFrom(getTransport().convertIDToJID(msnContact.getEmail().toString()));
                ((MSNTransport)getTransport()).setUpPresencePacket(p, msnContact.getStatus());
            }
            else {
                // User was not found so send an error presence
                p.setFrom(jid);
                p.setError(PacketError.Condition.forbidden);
            }
            getTransport().sendPacket(p);
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateStatus(org.jivesoftware.openfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        if (isLoggedIn()) {
            try {
                msnMessenger.getOwner().setStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
            }
            catch (IllegalStateException e) {
//                // Hrm, not logged in?  Lets fix that.
//                msnMessenger.getOwner().setInitStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
//                msnMessenger.login();
            }
        }
        else {
//            // Hrm, not logged in?  Lets fix that.
//            msnMessenger.getOwner().setInitStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
//            msnMessenger.login();
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
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
