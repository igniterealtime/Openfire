/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import org.xmpp.packet.JID;

/**
 * Interface for a transport session.
 *
 * This outlines all of the functionality that is required for a transport
 * to implement.  These are functions that the XMPP side of things are going
 * interact with.  The legacy transport itself is expected to handle messages
 * going to the Jabber user.
 *
 * @author Daniel Henninger
 */
public abstract class TransportSession {

    /**
     * Creates a TransportSession instance.
     *
     * @param registration Registration this session is associated with.
     */
    public TransportSession(Registration registration, JID jid, BaseTransport transport) {
        this.jid = jid;
        this.registration = registration;
        this.transport = transport;
    }

    /**
     * Registration that this session is associated with.
     */
    public Registration registration;

    /**
     * Transport this session is associated with.
     */
    public BaseTransport transport;

    /**
     * JID the session is associated with.  (includes specific resource)
     */
    public JID jid;

    /**
     * Retrieves the registration information associated with the session.
     *
     * @return Registration information of the user associated with the session.
     */
    public Registration getRegistration() {
        return registration;
    }

    /**
     * Retrieves the transport associated wtih the session.
     *
     * @return Transport associated with the session.
     */
    public BaseTransport getTransport() {
        return transport;
    }

    /**
     * Retrieves the jid associated with the session.
     *
     * @return JID of the user associated with this session.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * Logs in to the legacy service.
     */
    public abstract void logIn();

    /**
     * Log out of the legacy service.
     */
    public abstract void logOut();

    /**
     * Is the legacy service account logged in?
     *
     * @return True or false if the legacy account is logged in.
     */
    public abstract Boolean isLoggedIn();

    /**
     * Adds a legacy contact to the legacy service.
     *
     * @param jid JID associated with the legacy contact.
     */
    public abstract void addContact(JID jid);

    /**
     * Removes a legacy contact from the legacy service.
     *
     * @param jid JID associated with the legacy contact.
     */
    public abstract void removeContact(JID jid);

    /**
     * Sends an outgoing message through the legacy serivce.
     *
     * @param jid JID associated with the target contact.
     * @param message Message to be sent.
     */
    public abstract void sendMessage(JID jid, String message);

    /**
     * Asks the legacy service to send a presence packet for a contact.
     *
     * This is typically response to a probe.
     *
     * @param jid JID to be checked.
    */
    public abstract void retrieveContactStatus(JID jid);

}
