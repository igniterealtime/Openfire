/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.xmpp.packet.Packet;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * This ChannelHandler writes packet data to connections.
 *
 * @author Iain Shigeoka
 * @see PacketRouter
 */
public class SocketPacketWriteHandler implements ChannelHandler {

    private SessionManager sessionManager;
    private OfflineMessageStrategy messageStrategy;
    private String serverName = XMPPServer.getInstance().getServerInfo().getName();
    private RoutingTable routingTable;

    public SocketPacketWriteHandler(SessionManager sessionManager, RoutingTable routingTable,
            OfflineMessageStrategy messageStrategy) {
        this.sessionManager = sessionManager;
        this.messageStrategy = messageStrategy;
        this.routingTable = routingTable;
    }

     public void process(Packet packet) throws UnauthorizedException, PacketException {
        try {
            JID recipient = packet.getTo();
            // Check if the target domain belongs to a remote server
            if (recipient != null && !recipient.getDomain().contains(serverName)) {
                try {
                    // Locate the route to the remote server and ask it to process the packet
                    routingTable.getRoute(recipient).process(packet);
                }
                catch (NoSuchRouteException e) {
                    // No root was found so either drop or store the packet
                    handleUnprocessedPacket(packet);
                }
                return;
            }
            // The target domain belongs to the local server
            if (recipient == null || (recipient.getNode() == null && recipient.getResource() == null)) {
                // no TO was found so send back the packet to the sender
                Session senderSession = sessionManager.getSession(packet.getFrom());
                if (senderSession != null && !senderSession.getConnection().isClosed()) {
                    senderSession.getConnection().deliver(packet);
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
                        session.getConnection().deliver(packet);
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
