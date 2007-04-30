/**
 * $RCSfile: SocketPacketWriteHandler.java,v $
 * $Revision: 3137 $
 * $Date: 2005-12-01 02:11:05 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * This ChannelHandler writes packet data to connections.
 *
 * @author Iain Shigeoka
 * @see PacketRouter
 */
public class SocketPacketWriteHandler implements ChannelHandler {

    private XMPPServer server;
    private SessionManager sessionManager;
    private OfflineMessageStrategy messageStrategy;
    private RoutingTable routingTable;

    public SocketPacketWriteHandler(SessionManager sessionManager, RoutingTable routingTable,
            OfflineMessageStrategy messageStrategy) {
        this.sessionManager = sessionManager;
        this.messageStrategy = messageStrategy;
        this.routingTable = routingTable;
        this.server = XMPPServer.getInstance();
    }

     public void process(Packet packet) throws UnauthorizedException, PacketException {
        try {
            JID recipient = packet.getTo();
            // Check if the target domain belongs to a remote server or a component
            if (server.matchesComponent(recipient) || server.isRemote(recipient)) {
                // Locate the route to the remote server or component and ask it
                // to process the packet
                ChannelHandler route = routingTable.getRoute(recipient);
                if (route != null) {
                    route.process(packet);
                }
                else {
                    // No root was found so either drop or store the packet
                    handleUnprocessedPacket(packet);
                }
                return;
            }
            // The target domain belongs to the local server
            if (recipient == null || (recipient.getNode() == null && recipient.getResource() == null)) {
                // no TO was found so send back the packet to the sender
                ClientSession senderSession = sessionManager.getSession(packet.getFrom());
                if (senderSession != null) {
                    senderSession.process(packet);
                }
                else {
                    // The sender is no longer available so drop the packet
                    dropPacket(packet);
                }
            }
            else {
                Session session = sessionManager.getBestRoute(recipient);
                if (session == null) {
                    handleUnprocessedPacket(packet);
                }
                else {
                    try {
                        session.process(packet);
                    }
                    catch (Exception e) {
                        // do nothing
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.deliver") + "\n" + packet.toString(), e);
        }
    }

    private void handleUnprocessedPacket(Packet packet) {
        if (packet instanceof Message) {
            messageStrategy.storeOffline((Message)packet);
        }
        else if (packet instanceof Presence) {
            // presence packets are dropped silently
            //dropPacket(packet);
        }
        else {
            // IQ packets are logged but dropped
            dropPacket(packet);
        }
    }

    /**
     * Drop the packet.
     *
     * @param packet The packet being dropped
     */
    private void dropPacket(Packet packet) {
        Log.warn(LocaleUtils.getLocalizedString("admin.error.routing") + "\n" + packet.toString());
    }
}
