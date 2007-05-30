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

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * This ChannelHandler writes packet data to connections.
 *
 * @author Iain Shigeoka
 * @see PacketRouter
 */
public class SocketPacketWriteHandler implements ChannelHandler {

    private XMPPServer server;
    private RoutingTable routingTable;

    public SocketPacketWriteHandler(RoutingTable routingTable) {
        this.routingTable = routingTable;
        this.server = XMPPServer.getInstance();
    }

     public void process(Packet packet) throws UnauthorizedException, PacketException {
        try {
            JID recipient = packet.getTo();
            // Check if the target domain belongs to a remote server or a component
            if (server.matchesComponent(recipient) || server.isRemote(recipient)) {
                routingTable.routePacket(recipient, packet);
            }
            // The target domain belongs to the local server
            if (recipient == null || (recipient.getNode() == null && recipient.getResource() == null)) {
                // no TO was found so send back the packet to the sender
                routingTable.routePacket(packet.getFrom(), packet);
            }
            else {
                routingTable.routePacket(recipient, packet);
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.deliver") + "\n" + packet.toString(), e);
        }
    }
}
