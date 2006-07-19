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

import org.hn.sleek.jmml.ContactStatus;
import org.hn.sleek.jmml.MessengerServerManager;
import org.hn.sleek.jmml.MSNException;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
     */
    public MSNSession(Registration registration, JID jid, MSNTransport transport) {
        super(registration, jid, transport);

        msnManager = MessengerServerManager.getInstance();
        msnManager.addMessengerClientListener(new MSNListener(this));
    }

    /**
     * MSN session
     */
    private MessengerServerManager msnManager = null;

    /**
     * Log in to MSN.
     */
    public void logIn() {
        try {
            msnManager.signIn(registration.getUsername(), registration.getPassword(), ContactStatus.ONLINE);
            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
            msnManager.setStatus(ContactStatus.ONLINE);
        }
        catch (MSNException e) {
            Log.error("MSN exception thrown while logging in: " + e.toString());
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        try {
            msnManager.signOut();
            Presence p = new Presence(Presence.Type.unavailable);
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);
        }
        catch (MSNException e) {
            Log.error("MSN exception thrown while logging out: " + e.toString());
        }
    }

    /**
     * Have we successfully logged in to MSN?
     */
    public Boolean isLoggedIn() {
        return msnManager.isConnected();
    }

    /**
     * Adds a contact to the user's MSN contact list.
     *
     * @param jid JID of contact to be added.
     */
    public void addContact(JID jid) {
        // TODO: check jabber group and use it
    }

    /**
     * Removes a contact from the user's MSN contact list.
     *
     * @param jid JID of contact to be added.
     */
    public void removeContact(JID jid) {
        // TODO: check jabber group and use it
    }

    /**
     * Sends a message from the jabber user to a MSN contact.
     *
     * @param jid JID of contact to send message to.
     * @param message Message to send to yahoo contact.
     */
    public void sendMessage(JID jid, String message) {
        try {
            msnManager.sendMessage(jid.getNode(), message);
        }
        catch (MSNException e) {
            Log.error("MSN exception while sending message: " + e.toString());
        }
    }

    /**
     * Asks for transport to send information about a contact if possible.
     *
     * @param jid JID of contact to be probed.
     */
    public void retrieveContactStatus(JID jid) {
        // TODO: yeah
    }

}
