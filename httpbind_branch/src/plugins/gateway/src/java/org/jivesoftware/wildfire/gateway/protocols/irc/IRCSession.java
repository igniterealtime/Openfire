/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.irc;

import org.jivesoftware.wildfire.gateway.*;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.schwering.irc.lib.IRCConnection;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an IRC session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with IRC.
 *
 * @author Daniel Henninger
 */
public class IRCSession extends TransportSession {

    final private PseudoRosterManager pseudoRosterManager = new PseudoRosterManager();

    /**
     * Create a MSN Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public IRCSession(Registration registration, JID jid, IRCTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        pseudoRoster = pseudoRosterManager.getPseudoRoster(registration);
        for (String contact : pseudoRoster.getContacts()) {
            buddyStatuses.put(contact, PresenceType.unavailable);
        }

        String server = "irc.freenode.net";
        int[] ports = new int[] { 7000, 6667 };
        String username = registration.getUsername();
        String password = registration.getPassword();
        password = (password == null || password.equals("")) ? null : password;
        String nickname = registration.getNickname();

        conn = new IRCConnection(server, ports, password, nickname, username, "Wildfire User");
        conn.setPong(true);
        conn.setDaemon(false);
        conn.setColors(false);
        ircListener = new IRCListener(this);
        conn.addIRCEventListener(ircListener);
    }

    /**
     * Our pseudo roster.
     *
     * No server side buddy list, so we track it all here.
     */
    private PseudoRoster pseudoRoster;

    /**
     * IRC connection.
     */
    public IRCConnection conn;

    /**
     * IRC listener.
     */
    IRCListener ircListener;

    /**
     * Tracks status of 'buddy list'.
     */
    ConcurrentHashMap<String, PresenceType> buddyStatuses = new ConcurrentHashMap<String, PresenceType>();

    /**
     * Logs the session into IRC.
     *
     * @param presenceType Initial presence state.
     * @param verboseStatus Initial full status information.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        setLoginStatus(TransportLoginStatus.LOGGED_IN);
        try {
            conn.connect();
        }
        catch (IOException e) {
            Log.error("IO error while connecting to IRC: ", e);
        }
    }

    /**
     * Logs the session out of IRC.
     */
    public void logOut() {
        setLoginStatus(TransportLoginStatus.LOGGING_OUT);
        ircListener.setSilenced(true);
        conn.doQuit();
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    /**
     * Retrieves the buddy status list.
     *
     * @return Hash of buddies mapped to presence type.
     */
    public ConcurrentHashMap<String, PresenceType> getBuddyStatuses() {
        return buddyStatuses;
    }

    /**
     * Gets the current presence status of a buddy.
     *
     * @param username Username to look up.
     * @return Presence type of a particular buddy.
     */
    public PresenceType getBuddyStatus(String username) {
        return buddyStatuses.get(username);
    }

    /**
     * Updates the current presence status of a buddy.
     *
     * @param username Username to set presence of.
     * @param presenceType New presence type.
     */
    public void setBuddyStatus(String username, PresenceType presenceType) {
        PresenceType buddyPresenceType = buddyStatuses.get(username);
        if (buddyPresenceType == null || !buddyPresenceType.equals(presenceType)) {
            Presence p = new Presence();
            if (presenceType.equals(PresenceType.unavailable)) {
                p.setType(Presence.Type.unavailable);
            }
            p.setTo(getJID());
            p.setFrom(getTransport().convertIDToJID(username));
            getTransport().sendPacket(p);
        }
        buddyStatuses.put(username, presenceType);
    }

    /**
     * @return the IRC connection associated with this session.
     */
    public IRCConnection getConnection() {
        return conn;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        String awayMsg = ((IRCTransport)getTransport()).convertJabStatusToIRC(presenceType, verboseStatus);
        if (awayMsg == null) {
            conn.doAway();
        }
        else {
            conn.doAway(awayMsg);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
            rosterItem.setGroups(item.getGroups().toString());
            conn.doIson(contact);
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), item.getGroups().toString());
            buddyStatuses.put(contact, PresenceType.unavailable);
            conn.doIson(contact);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        pseudoRoster.removeItem(contact);
        buddyStatuses.remove(contact);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
        String contact = getTransport().convertJIDToID(item.getJid());
        if (pseudoRoster.hasItem(contact)) {
            PseudoRosterItem rosterItem = pseudoRoster.getItem(contact);
            rosterItem.setNickname(item.getNickname());
            rosterItem.setGroups(item.getGroups().toString());
            conn.doIson(contact);
        }
        else {
            pseudoRoster.createItem(contact, item.getNickname(), item.getGroups().toString());
            buddyStatuses.put(contact, PresenceType.unavailable);
            conn.doIson(contact);
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        conn.doPrivmsg(getTransport().convertJIDToID(jid), message);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendServerMessage(String)
     */
    public void sendServerMessage(String message) {
        conn.send(message);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
        String contact = getTransport().convertJIDToID(jid);
        Presence p = new Presence();
        if (buddyStatuses.get(contact).equals(PresenceType.unavailable)) {
            p.setType(Presence.Type.unavailable);
        }
        p.setTo(jid);
        p.setFrom(getTransport().convertIDToJID(contact));
        getTransport().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        for (String contact : buddyStatuses.keySet()) {
            Presence p = new Presence();
            if (buddyStatuses.get(contact).equals(PresenceType.unavailable)) {
                p.setType(Presence.Type.unavailable);
            }
            p.setTo(jid);
            p.setFrom(getTransport().convertIDToJID(contact));
            getTransport().sendPacket(p);
        }
    }

}
