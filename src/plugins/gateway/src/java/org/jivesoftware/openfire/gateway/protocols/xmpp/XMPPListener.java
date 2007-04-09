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

import org.jivesoftware.smack.*;
import org.xmpp.packet.Presence;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import java.util.Collection;

/**
 * Handles incoming events from XMPP server.
 *
 * @author Daniel Henninger
 */
public class XMPPListener implements MessageListener, RosterListener, ConnectionListener, ChatManagerListener {

    /**
     * Creates an XMPP listener instance and ties to session.
     *
     * @param session Session this listener is associated with.
     */
    public XMPPListener(XMPPSession session) {
        this.xmppSession = session;
    }

    /**
     * Session instance that the listener is associated with.
     */
    public XMPPSession xmppSession = null;

    /**
     * Handlings incoming messages.
     *
     * @param chat Chat instance this message is associated with.
     * @param message Message received.
     */
    public void processMessage(Chat chat, org.jivesoftware.smack.packet.Message message) {
        xmppSession.getTransport().sendMessage(
                xmppSession.getJIDWithHighestPriority(),
                xmppSession.getTransport().convertIDToJID(message.getFrom()),
                message.getBody()
        );
    }

    public void entriesAdded(Collection<String> collection) {
        //Ignoring for now
    }

    public void entriesUpdated(Collection<String> collection) {
        //Ignoring for now
    }

    public void entriesDeleted(Collection<String> collection) {
        //Ignoring for now
    }

    public void presenceChanged(org.jivesoftware.smack.packet.Presence presence) {
        Element presElem = DocumentHelper.createElement(presence.toXML());
        Presence p = new Presence(presElem);
        p.setTo(xmppSession.getJID());
        p.setFrom(xmppSession.getTransport().convertIDToJID(presence.getFrom()));
        xmppSession.getTransport().sendPacket(p);
    }

    public void connectionClosed() {
        Presence p = new Presence();
        p.setType(Presence.Type.unavailable);
        p.setTo(xmppSession.getJID());
        p.setFrom(xmppSession.getTransport().getJID());
        xmppSession.getTransport().sendPacket(p);
    }

    public void connectionClosedOnError(Exception exception) {
        xmppSession.getTransport().sendMessage(
                xmppSession.getJIDWithHighestPriority(),
                xmppSession.getTransport().getJID(),
                "Conection closed."
        );

        Presence p = new Presence();
        p.setType(Presence.Type.unavailable);
        p.setTo(xmppSession.getJID());
        p.setFrom(xmppSession.getTransport().getJID());
        xmppSession.getTransport().sendPacket(p);
    }

    public void reconnectingIn(int i) {
        //Ignoring for now
    }

    public void reconnectionSuccessful() {
        //Ignoring for now
    }

    public void reconnectionFailed(Exception exception) {
        //Ignoring for now
    }

    public void chatCreated(Chat chat, boolean b) {
        //More or less can ignore this.
    }

}
