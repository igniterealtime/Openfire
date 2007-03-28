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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.*;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import ymsg.network.*;

import java.io.IOException;
import java.util.*;

/**
 * Represents a Yahoo session.
 * 
 * This is the interface with which the base transport functionality will
 * communicate with Yahoo.
 *
 * @author Daniel Henninger
 * Heavily inspired by Noah Campbell's work.
 */
public class YahooSession extends TransportSession {

    final private PseudoRosterManager pseudoRosterManager = new PseudoRosterManager();

    /**
     * Create a Yahoo Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session
     * @param priority Priority of this session
     */
    public YahooSession(Registration registration, JID jid, YahooTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        pseudoRoster = pseudoRosterManager.getPseudoRoster(registration);

        yahooSession = new Session(new DirectConnectionHandler(
                JiveGlobals.getProperty("plugin.gateway.yahoo.connecthost", "scs.msg.yahoo.com"),
                JiveGlobals.getIntProperty("plugin.gateway.yahoo.connectport", 5050)
        ));
        yahooSession.addSessionListener(new YahooSessionListener(this));
    }

    /**
     * Our pseudo roster.
     *
     * We only really use it for nickname tracking.
     */
    private PseudoRoster pseudoRoster;

    /**
     * How many attempts have been made so far?
     */
    private Integer loginAttempts = 0;

    /**
     * Yahoo session
     */
    private final Session yahooSession;

    /**
     * Stored Last Presence Type
     */
    public PresenceType presenceType = null;

    /**
     * Stored Last Verbose Status
     */
    public String verboseStatus = null;

    /**
     * Log in to Yahoo.
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Long representation of status.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
        final PresenceType pType = presenceType;
        if (!isLoggedIn() && getLoginStatus() != TransportLoginStatus.LOGGING_IN && loginAttempts <= 3) {
            setLoginStatus(TransportLoginStatus.LOGGING_IN);
            new Thread() {
                public void run() {
                    try {
                        loginAttempts++;
                        yahooSession.setStatus(StatusConstants.STATUS_AVAILABLE);
                        yahooSession.login(registration.getUsername(), registration.getPassword());
                        setLoginStatus(TransportLoginStatus.LOGGED_IN);

                        Presence p = new Presence();
                        p.setTo(getJID());
                        p.setFrom(getTransport().getJID());
                        getTransport().sendPacket(p);

                        yahooSession.setStatus(((YahooTransport)getTransport()).convertJabStatusToYahoo(pType));

                        getRegistration().setLastLogin(new Date());

                        syncUsers();
                    }
                    catch (LoginRefusedException e) {
                        yahooSession.reset();
                        String reason = LocaleUtils.getLocalizedString("gateway.yahoo.loginrefused", "gateway");
                        switch((int)e.getStatus()) {
                            case (int)StatusConstants.STATUS_BADUSERNAME:
                                reason = LocaleUtils.getLocalizedString("gateway.yahoo.unknownuser", "gateway");
                                break;
                            case (int)StatusConstants.STATUS_BAD:
                                reason = LocaleUtils.getLocalizedString("gateway.yahoo.badpassword", "gateway");
                                break;
                            case (int)StatusConstants.STATUS_LOCKED:
                                AccountLockedException e2 = (AccountLockedException)e;
                                if(e2.getWebPage() != null) {
                                    reason = LocaleUtils.getLocalizedString("gateway.yahoo.accountlockedwithurl", "gateway", Arrays.asList(e2.getWebPage().toString()));
                                }
                                else {
                                    reason = LocaleUtils.getLocalizedString("gateway.yahoo.accountlocked", "gateway");
                                }
                                break;
                        }

                        Log.warn("Yahoo login failed for "+getJID()+": "+reason);

                        getTransport().sendMessage(
                                getJID(),
                                getTransport().getJID(),
                                reason,
                                Message.Type.error
                        );
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                    }
                    catch (IOException e) {
                        Log.error("Yahoo login caused IO exception:", e);

                        getTransport().sendMessage(
                                getJID(),
                                getTransport().getJID(),
                                LocaleUtils.getLocalizedString("gateway.yahoo.unknownerror", "gateway"),
                                Message.Type.error
                        );
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                    }
                }
            }.run();
        }
    }

    /**
     * Log out of Yahoo.
     */
    public void logOut() {
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
        loginAttempts = 0;
        try {
            yahooSession.logout();
        }
        catch (IOException e) {
            Log.debug("Failed to log out from Yahoo.");
        }
        yahooSession.reset();
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
    }

    /**
     * Syncs up the yahoo roster with the jabber roster.
     */
    public void syncUsers() {
        // First we need to get a good mapping of users to what groups they are in.
        HashMap<String,ArrayList<String>> userToGroups = new HashMap<String,ArrayList<String>>();
        for (YahooGroup group : yahooSession.getGroups()) {
            for (Enumeration e = group.getMembers().elements(); e.hasMoreElements();) {
                YahooUser user = (YahooUser)e.nextElement();
                ArrayList<String> groups;
                if (userToGroups.containsKey(user.getId())) {
                    groups = userToGroups.get(user.getId());
                }
                else {
                    groups = new ArrayList<String>();
                }
                if (!groups.contains(group.getName())) {
                    groups.add(group.getName());
                }
                userToGroups.put(user.getId(), groups);
            }
        }
        // Now we will run through the entire list of users and set up our sync group.
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (Object userObj : yahooSession.getUsers().values()) {
            YahooUser user = (YahooUser)userObj;
            PseudoRosterItem rosterItem = pseudoRoster.getItem(user.getId());
            String nickname = null;
            if (rosterItem != null) {
                nickname = rosterItem.getNickname();
            }
            if (nickname == null) {
                nickname = user.getId();
            }
            if (userToGroups.containsKey(user.getId()) && !userToGroups.get(user.getId()).get(0).equals("Transport Buddies")) {
                legacyusers.add(new TransportBuddy(user.getId(), nickname, userToGroups.get(user.getId()).get(0)));
            }
            else {
                legacyusers.add(new TransportBuddy(user.getId(), nickname, null));
            }
        }
        // Lets try the actual sync.
        try {
            getTransport().syncLegacyRoster(getJID(), legacyusers);
        }
        catch (UserNotFoundException e) {
            Log.error("Unable to sync yahoo contact list for " + getJID());
        }

        // Ok, now lets check presence
        for (Object userObj : yahooSession.getUsers().values()) {
            YahooUser user = (YahooUser)userObj;
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(user.getId()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
            getTransport().sendPacket(p);
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#addContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        // Syncing will take are of add.
        String contact = getTransport().convertJIDToID(item.getJid());
        lockRoster(item.getJid().toString());
        syncContactGroups(contact, item.getGroups());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), null);
        }
        unlockRoster(item.getJid().toString());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#removeContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        lockRoster(item.getJid().toString());
        for (YahooGroup yahooGroup : yahooSession.getGroups()) {
            if (yahooGroup.getIndexOfFriend(contact) != -1) {
                try {
                    yahooSession.removeFriend(contact, yahooGroup.getName());
                    pseudoRoster.removeItem(contact);
                }
                catch (IOException e) {
                    Log.error("Failed to remove yahoo user.");
                }
            }
        }
        unlockRoster(item.getJid().toString());
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateContact(org.jivesoftware.openfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        lockRoster(item.getJid().toString());
        syncContactGroups(contact, item.getGroups());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), null);
        }
        unlockRoster(item.getJid().toString());
    }

    /**
     * Given a legacy contact and a list of groups, makes sure that the list is in sync with
     * the actual group list.
     *
     * @param contact Email address of contact.
     * @param groups List of groups contact should be in.
     */
    public void syncContactGroups(String contact, List<String> groups) {
        if (groups.size() == 0) {
            groups.add("Transport Buddies");
        }
        HashMap<String,YahooGroup> yahooGroups = new HashMap<String,YahooGroup>();
        // Lets create a hash of these for easier reference.
        for (YahooGroup yahooGroup : yahooSession.getGroups()) {
            yahooGroups.put(yahooGroup.getName(), yahooGroup);
        }
        // Create groups(add user to them) that do not currently exist.
        for (String group : groups) {
            if (!yahooGroups.containsKey(group)) {
                try {
                    Log.debug("Yahoo: Adding contact "+contact+" to non-existent group "+group);
                    yahooSession.addFriend(contact, group);
                }
                catch (IOException e) {
                    Log.error("Error while syncing Yahoo groups.");
                }
            }
        }
        // Now we handle adds and removes, syncing the two lists.
        for (YahooGroup yahooGroup : yahooSession.getGroups()) {
            if (groups.contains(yahooGroup.getName())) {
                if (yahooGroup.getIndexOfFriend(contact) == -1) {
                    try {
                        Log.debug("Yahoo: Adding contact "+contact+" to existing group "+yahooGroup.getName());
                        yahooSession.addFriend(contact, yahooGroup.getName());
                    }
                    catch (IOException e) {
                        Log.error("Error while syncing Yahoo groups.");
                    }
                }
            }
            else {
                if (yahooGroup.getIndexOfFriend(contact) != -1) {
                    try {
                        Log.debug("Yahoo: Removing contact "+contact+" from group "+yahooGroup.getName());
                        yahooSession.removeFriend(contact, yahooGroup.getName());
                    }
                    catch (IOException e) {
                        Log.error("Error while syncing Yahoo groups.");
                    }
                }
            }
        }
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        try {
            yahooSession.sendMessage(jid.getNode(), message);
        }
        catch (IOException e) {
            Log.error("Failed to send message to yahoo user.");
        }
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
        // TODO: Handle this
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#updateStatus(org.jivesoftware.openfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        try {
            if (isLoggedIn()) {
                yahooSession.setStatus(((YahooTransport)getTransport()).convertJabStatusToYahoo(presenceType));
            }
            else {
                // TODO: Should we consider auto-logging back in?
            }
        }
        catch (Exception e) {
            Log.error("Unable to set Yahoo Status:", e);
        }
        Presence p = new Presence();
        if (presenceType == PresenceType.away) {
            p.setShow(Presence.Show.away);
        }
        else if (presenceType == PresenceType.xa) {
            p.setShow(Presence.Show.xa);
        }
        else if (presenceType == PresenceType.dnd) {
            p.setShow(Presence.Show.dnd);
        }
        else if (presenceType == PresenceType.chat) {
            p.setShow(Presence.Show.chat);
        }
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
    }

    /**
     * @see org.jivesoftware.openfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        if (isLoggedIn()) {
            YahooUser user = yahooSession.getUser(jid.getNode());
            Presence p = new Presence();
            p.setTo(getJID());
            if (user != null) {
                // User was found so update presence accordingly
                p.setFrom(getTransport().convertIDToJID(user.getId()));

                String custommsg = user.getCustomStatusMessage();
                if (custommsg != null) {
                    p.setStatus(custommsg);
                }

                ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
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
     * @see org.jivesoftware.openfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        for (Object userObj : yahooSession.getUsers().values()) {
            YahooUser user = (YahooUser)userObj;
            Presence p = new Presence();
            p.setTo(jid);
            p.setFrom(getTransport().convertIDToJID(user.getId()));

            String custommsg = user.getCustomStatusMessage();
            if (custommsg != null) {
                p.setStatus(custommsg);
            }

            ((YahooTransport)getTransport()).setUpPresencePacket(p, user.getStatus());
            getTransport().sendPacket(p);
        }
    }

}
