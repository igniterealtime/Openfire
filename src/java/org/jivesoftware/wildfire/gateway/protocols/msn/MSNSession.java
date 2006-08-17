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

import java.util.Date;
import net.sf.jml.MsnMessenger;
import net.sf.jml.MsnProtocol;
import net.sf.jml.Email;
import net.sf.jml.impl.MsnMessengerFactory;
import org.jivesoftware.wildfire.gateway.PresenceType;
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

            Presence p = new Presence();
            p.setTo(getJID());
            p.setFrom(getTransport().getJID());
            getTransport().sendPacket(p);

            getRegistration().setLastLogin(new Date());

            loginStatus = true;
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
     * @see org.jivesoftware.wildfire.gateway.TransportSession#addContact(org.xmpp.packet.JID)
     */
    public void addContact(JID jid) {
        // @todo check jabber group and use it
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#removeContact(org.xmpp.packet.JID)
     */
    public void removeContact(JID jid) {
        // @todo check jabber group and use it
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
        // @todo need to implement this
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#updateStatus(org.jivesoftware.wildfire.gateway.PresenceType, String) 
     */
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
        // @todo need to implement this
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.TransportSession#resendContactStatuses(org.xmpp.packet.JID)
     */
    public void resendContactStatuses(JID jid) {
        // @todo need to implement this
    }

}
