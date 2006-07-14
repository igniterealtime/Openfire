/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import org.xmpp.component.ComponentException;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import java.util.Arrays;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

import ymsg.network.event.SessionChatEvent;
import ymsg.network.event.SessionEvent;
import ymsg.network.event.SessionFriendEvent;
import ymsg.network.event.SessionNewMailEvent;

/**
 * <code>YahooSessionListener</code> is a call back mechanism for <code>ymsg9</code>
 * api.
 * 
 * @author Noah Campbell
 */
public class YahooSessionListener extends NoopSessionListener {

    /**
     * Gateway session associated with this listener.
     */
    private YahooGatewaySession gatewaySession;

    /**
     * Creates a YahooSessionListener that will translate events from the Yahoo!
     * connection into XMPP formated packets.
     * 
     * @param gateway The yahoo gateway.
     */
    public YahooSessionListener(YahooGatewaySession gateway) {
        this.gatewaySession = gateway;
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#newMailReceived(ymsg.network.event.SessionNewMailEvent)
     */
    @Override
    public void newMailReceived(SessionNewMailEvent snme) {
        try {
            Message message = new Message();
            message.setTo(gatewaySession.getSessionJID());
            message.setFrom(this.gatewaySession.getGateway().getJID());
            message.setBody("New Mail Received (" + snme.getMailCount() + ")");
            message.setType(Message.Type.headline);
            gatewaySession.getJabberEndpoint().sendPacket(message);
        }
        catch (Exception e) {
            Log.error("Unable to send message: " + e.getLocalizedMessage());
        }    
    }

    /** (non-Javadoc)
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#messageReceived(ymsg.network.event.SessionEvent)
     */
    @Override
    public void messageReceived(SessionEvent sessionEvent) {
        Log.debug(sessionEvent.toString());
        
        try {
            Message message = new Message();
            message.setTo(gatewaySession.getSessionJID());
            message.setBody(sessionEvent.getMessage());
            message.setType(Message.Type.chat);
            message.setFrom(this.gatewaySession.getGateway().whois(sessionEvent.getFrom()));
            gatewaySession.getJabberEndpoint().sendPacket(message);
            Log.debug(message.getElement().asXML());
        }
        catch (Exception e) {
            Log.error("unable to send message: "+ e.getLocalizedMessage());
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#friendsUpdateReceived(ymsg.network.event.SessionFriendEvent)
     */
    @Override
    public void friendsUpdateReceived(SessionFriendEvent event) {
        try {
            updateStatus(event);
        }
        catch (Exception e) {
            Log.debug(LocaleUtils.getLocalizedString("yahoosessionlistener.friendupdateerror", "gateway", Arrays.asList(e.getLocalizedMessage())));
        }
    }

    /**
     * Update a friends status.
     * 
     * @param event
     * @throws ComponentException
     */
    private void updateStatus(SessionFriendEvent event) throws ComponentException {
        Presence p = new Presence();
        p.setStatus(event.getFriend().getCustomStatusMessage());
        p.setFrom(gatewaySession.getGateway().whois(event.getFriend().getId()));
        p.setTo(gatewaySession.getSessionJID());
        gatewaySession.getJabberEndpoint().sendPacket(p);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#chatMessageReceived(ymsg.network.event.SessionChatEvent)
     */
    @Override
    public void chatMessageReceived(SessionChatEvent sessionEvent) {
        Log.debug(sessionEvent.toString());
        try {
            Message message = new Message();
            message.setTo(gatewaySession.getSessionJID());
            message.setBody(sessionEvent.getMessage());
            message.setFrom(this.gatewaySession.getGateway().whois(sessionEvent.getFrom()));
            gatewaySession.getJabberEndpoint().sendPacket(message);
        }
        catch (Exception e) {
            Log.error("unable to send message: "+ e.getLocalizedMessage());
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#connectionClosed(ymsg.network.event.SessionEvent)
     */
    @Override
    public void connectionClosed(SessionEvent arg0) {
        gatewaySession.getJabberEndpoint().getValve().close();
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#friendAddedReceived(ymsg.network.event.SessionFriendEvent)
     */
    @Override
    public void friendAddedReceived(SessionFriendEvent arg0) {
        // TODO Auto-generated method stub
        super.friendAddedReceived(arg0);
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.protocols.yahoo.NoopSessionListener#friendRemovedReceived(ymsg.network.event.SessionFriendEvent)
     */
    @Override
    public void friendRemovedReceived(SessionFriendEvent arg0) {
        // TODO Auto-generated method stub
        super.friendRemovedReceived(arg0);
    }

}
