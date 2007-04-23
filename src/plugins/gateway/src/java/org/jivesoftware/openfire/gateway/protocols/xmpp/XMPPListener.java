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
import org.jivesoftware.util.Log;
import org.xmpp.packet.Presence;
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
        Log.debug("XMPP got message: "+message.toXML());
        xmppSession.getTransport().sendMessage(
                xmppSession.getJIDWithHighestPriority(),
                xmppSession.getTransport().convertIDToJID(xmppSession.getBareJID(message.getFrom())),
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
        Presence p = new Presence();
        if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.available)) {
            // Nothing to do
        }
        else if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.unavailable)) {
            p.setType(Presence.Type.unavailable);
        }
        else if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.subscribe)) {
            p.setType(Presence.Type.subscribe);
        }
        else if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.subscribed)) {
            p.setType(Presence.Type.subscribed);
        }
        else if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.unsubscribe)) {
            p.setType(Presence.Type.unsubscribe);
        }
        else if (presence.getType().equals(org.jivesoftware.smack.packet.Presence.Type.unsubscribed)) {
            p.setType(Presence.Type.unsubscribed);
        }
        p.setTo(xmppSession.getJID());
        p.setFrom(xmppSession.getTransport().convertIDToJID(xmppSession.getBareJID(presence.getFrom())));
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
