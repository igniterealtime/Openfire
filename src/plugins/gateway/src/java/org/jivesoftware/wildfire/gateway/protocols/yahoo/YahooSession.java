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

import java.io.IOException;
import java.util.*;

import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.*;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Message;
import ymsg.network.LoginRefusedException;
import ymsg.network.Session;
import ymsg.network.YahooGroup;
import ymsg.network.YahooUser;

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

        yahooSession = new Session();
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
                        Log.warn("Yahoo login failed for " + getJID());

                        Message m = new Message();
                        m.setType(Message.Type.error);
                        m.setTo(getJID());
                        m.setFrom(getTransport().getJID());
                        m.setBody("Failed to log into Yahoo! messenger account.  (login refused)");
                        getTransport().sendPacket(m);
                        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                    }
                    catch (IOException e) {
                        Log.error("Yahoo login caused IO exception:", e);

                        Message m = new Message();
                        m.setType(Message.Type.error);
                        m.setTo(getJID());
                        m.setFrom(getTransport().getJID());
                        m.setBody("Failed to log into Yahoo! messenger account.  (unknown error)");
                        getTransport().sendPacket(m);
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
        List<TransportBuddy> legacyusers = new ArrayList<TransportBuddy>();
        for (YahooGroup group : yahooSession.getGroups()) {
            for (Enumeration e = group.getMembers().elements(); e.hasMoreElements();) {
                YahooUser user = (YahooUser)e.nextElement();
                PseudoRosterItem rosterItem = pseudoRoster.getItem(user.getId());
                String nickname = null;
                if (rosterItem != null) {
                    nickname = rosterItem.getNickname();
                }
                if (nickname == null) {
                    nickname = user.getId();
                }
                legacyusers.add(new TransportBuddy(user.getId(), nickname, group.getName()));
            }
        }
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
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        // Syncing will take are of add.
        String contact = getTransport().convertJIDToID(item.getJid());
        syncContactGroups(contact, item.getGroups());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), null);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
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
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        syncContactGroups(contact, item.getGroups());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), null);
        }
    }

    /**
     * Given a legacy contact and a list of groups, makes sure that the list is in sync with
     * the actual group list.
     *
     * @param contact Email address of contact.
     * @param groups List of groups contact should be in.
     */
    public void syncContactGroups(String contact, List<String> groups) {
        if (groups.isEmpty()) {
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
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
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
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        // We don't care.
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
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
        this.presenceType = presenceType;
        this.verboseStatus = verboseStatus;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        YahooUser user = yahooSession.getUser(jid.getNode());
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

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
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
