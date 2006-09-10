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

import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.roster.RosterItem;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.schwering.irc.lib.IRCConnection;

import java.io.IOException;

/**
 * Represents an IRC session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with IRC.
 *
 * @author Daniel Henninger
 */
public class IRCSession extends TransportSession {

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
        conn = new IRCConnection("irc.freenode.net", new int[] { 6667, 7000 }, registration.getPassword(), registration.getNickname(), registration.getUsername(), "Wildfire User");
        conn.setPong(true);
        conn.setDaemon(false);
        conn.setColors(false);
        conn.addIRCEventListener(new IRCListener(this));
    }

    /**
     * IRC connection.
     */
    IRCConnection conn;

    /**
     * Logs the session into IRC.
     *
     * @param presenceType Initial presence state.
     * @param verboseStatus Initial full status information.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!isLoggedIn()) {
            try {
                conn.connect();
            }
            catch (IOException e) {
                Log.error("IO error while connecting to IRC: "+e.toString());
            }
        }
    }

    /**
     * Logs the session out of IRC.
     */
    public void logOut() {
        if (isLoggedIn()) {
            conn.close();
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String)
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#isLoggedIn()
     */
    public Boolean isLoggedIn() {
        return conn.isConnected();
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void addContact(RosterItem item) {
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void removeContact(RosterItem item) {
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateContact(org.jivesoftware.wildfire.roster.RosterItem)
     */
    public void updateContact(RosterItem item) {
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    public void sendMessage(JID jid, String message) {
        conn.doPrivmsg(getTransport().convertJIDToID(jid), message);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#retrieveContactStatus(org.xmpp.packet.JID)
     */
    public void retrieveContactStatus(JID jid) {
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
    }

}
