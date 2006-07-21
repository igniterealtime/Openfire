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

import java.util.ArrayList;
import java.util.List;
import org.hn.sleek.jmml.Contact;
import org.hn.sleek.jmml.ContactList;
import org.hn.sleek.jmml.ContactStatus;
import org.hn.sleek.jmml.MessengerServerManager;
import org.hn.sleek.jmml.MSNException;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportBuddy;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.jivesoftware.wildfire.user.UserNotFoundException;
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
     *
     * @param presenceType Type of presence.
     * @param verboseStatus Long representation of status.
     */
    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!this.isLoggedIn()) {
            try {
                msnManager.signIn(registration.getUsername(), registration.getPassword(), ((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));

                Presence p = new Presence();
                p.setTo(getJID());
                p.setFrom(getTransport().getJID());
                getTransport().sendPacket(p);

                msnManager.setStatus(ContactStatus.ONLINE);
                msnManager.setPrivacyMode(true);
                msnManager.setReverseListBehaviour(true);
            }
            catch (MSNException e) {
                Log.error("MSN exception thrown while logging in:", e);
            }
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        if (this.isLoggedIn()) {
            try {
                msnManager.signOut();
                Presence p = new Presence(Presence.Type.unavailable);
                p.setTo(getJID());
                p.setFrom(getTransport().getJID());
                getTransport().sendPacket(p);
            }
            catch (MSNException e) {
                Log.error("MSN exception thrown while logging out:", e);
            }
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
            msnManager.sendMessage(getTransport().convertJIDToID(jid), message);
        }
        catch (MSNException e) {
            Log.error("MSN exception while sending message:", e);
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

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        try {
            msnManager.setStatus(((MSNTransport)getTransport()).convertJabStatusToMSN(presenceType));
        }
        catch (MSNException e) {
            Log.error("MSN exception while setting status:", e);
        }
    }

    /**
     * Retrieves the manager for this session.
     */
    public MessengerServerManager getManager() {
        return msnManager;
    }

}
