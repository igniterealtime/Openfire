/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.xmpp;

import org.jivesoftware.openfire.gateway.*;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * Represents an XMPP session.
 *
 * This is the interface with which the base transport functionality will
 * communicate with an XMPP server.
 *
 * @author Daniel Henninger
 */
public class XMPPSession extends TransportSession {

    /**
     * Create an XMPP Session instance.
     *
     * @param registration Registration informationed used for logging in.
     * @param jid JID associated with this session.
     * @param transport Transport instance associated with this session.
     * @param priority Priority of this session.
     */
    public XMPPSession(Registration registration, JID jid, XMPPTransport transport, Integer priority) {
        super(registration, jid, transport, priority);

        Log.debug("Creating "+getTransport().getType()+" session for " + registration.getUsername());
        config = new ConnectionConfiguration(
                JiveGlobals.getProperty("plugin.gateway."+getTransport().getType()+".connecthost",
                        (getTransport().getType().equals(TransportType.gtalk) ? "talk.google.com" : "jabber.org")),
                JiveGlobals.getIntProperty("plugin.gateway."+getTransport().getType()+".connectport", 5222));
    }

    /*
     * XMPP connection
     */
    private XMPPConnection conn = null;

    /*
     * XMPP connection configuration
     */
    private ConnectionConfiguration config = null;

    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!this.isLoggedIn()) {
            try {
                setLoginStatus(TransportLoginStatus.LOGGING_IN);
                conn = new XMPPConnection(config);
                conn.connect();
                conn.login(this.getRegistration().getUsername(), this.getRegistration().getPassword(), "IMGateway");
            }
            catch (XMPPException e) {
                Log.error(getTransport().getType()+" user is not able to log in: "+this.getRegistration().getUsername(), e);
            }
        }
    }

    /**
     * Log out of MSN.
     */
    public void logOut() {
        if (this.isLoggedIn()) {
            setLoginStatus(TransportLoginStatus.LOGGING_OUT);
            conn.disconnect();
        }
        Presence p = new Presence(Presence.Type.unavailable);
        p.setTo(getJID());
        p.setFrom(getTransport().getJID());
        getTransport().sendPacket(p);
        setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }
    

    public void updateStatus(PresenceType presenceType, String verboseStatus) {
    }

    public void addContact(RosterItem item) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void removeContact(RosterItem item) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void updateContact(RosterItem item) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void sendMessage(JID jid, String message) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void sendServerMessage(String message) {
        //Don't care
    }

    public void sendChatState(JID jid, ChatStateType chatState) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void retrieveContactStatus(JID jid) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void resendContactStatuses(JID jid) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

}
