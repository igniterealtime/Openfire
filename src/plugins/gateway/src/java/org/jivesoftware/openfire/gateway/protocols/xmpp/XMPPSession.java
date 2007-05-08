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

import java.util.Date;

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
        acctjid = new JID(registration.getUsername());

        Log.debug("Creating "+getTransport().getType()+" session for " + registration.getUsername());
        //XMPPConnection.DEBUG_ENABLED = true;
        config = new ConnectionConfiguration(
                JiveGlobals.getProperty("plugin.gateway."+getTransport().getType()+".connecthost",
                        (getTransport().getType().equals(TransportType.gtalk) ? "talk.google.com" : "jabber.org")),
                JiveGlobals.getIntProperty("plugin.gateway."+getTransport().getType()+".connectport", 5222),
                acctjid.getDomain());
        config.setCompressionEnabled(false);
        config.setReconnectionAllowed(false);
        listener = new XMPPListener(this);
    }

    /*
     * XMPP connection
     */
    private XMPPConnection conn = null;

    /**
     * XMPP listener
     */
    private XMPPListener listener = null;

    /*
     * XMPP connection configuration
     */
    private ConnectionConfiguration config = null;

    /**
     * Accounts's jid associated with this session.
     */
    private JID acctjid = null;

    public void logIn(PresenceType presenceType, String verboseStatus) {
        if (!isLoggedIn()) {
            new Thread() {
                public void run() {
                    try {
                        conn = new XMPPConnection(config);
                        conn.connect();
                        conn.addConnectionListener(listener);
                        conn.login(acctjid.getNode(), getRegistration().getPassword(), "IMGateway");
                        conn.getRoster().addRosterListener(listener);
                        conn.getChatManager().addChatListener(listener);

                        setLoginStatus(TransportLoginStatus.LOGGED_IN);
                        Presence p = new Presence();
                        p.setTo(getJIDWithHighestPriority());
                        p.setFrom(getTransport().getJID());
                        getTransport().sendPacket(p);

                        getRegistration().setLastLogin(new Date());
                    }
                    catch (XMPPException e) {
                        Log.error(getTransport().getType()+" user is not able to log in: "+getRegistration().getUsername(), e);
                    }
                }
            }.start();
        }
    }

    public void logOut() {
        if (isLoggedIn()) {
            cleanUp();
            sessionDisconnectedNoReconnect();
        }
    }

    public void cleanUp() {
        conn.removeConnectionListener(listener);
        conn.getRoster().removeRosterListener(listener);
        conn.getChatManager().removeChatListener(listener);
        conn.disconnect();
    }
    

    public void updateStatus(PresenceType presenceType, String verboseStatus) {
    }

    public void addContact(RosterItem item) {
    }

    public void removeContact(RosterItem item) {
    }

    public void updateContact(RosterItem item) {
    }

    public void sendMessage(JID jid, String message) {
        org.jivesoftware.smack.packet.Message m = new org.jivesoftware.smack.packet.Message();
        m.setFrom(getRegistration().getUsername());
        m.setTo(getTransport().convertJIDToID(jid));
        m.setBody(message);
        Log.debug("XMPP Sending Message: "+m.toXML());
        conn.sendPacket(m);
    }

    public void sendServerMessage(String message) {
        //Don't care
    }

    public void sendChatState(JID jid, ChatStateType chatState) {
    }

    public void retrieveContactStatus(JID jid) {
    }

    public void resendContactStatuses(JID jid) {
    }

    public String getBareJID(String jid) {
        return jid.substring(0, jid.indexOf("/"));
    }

}
