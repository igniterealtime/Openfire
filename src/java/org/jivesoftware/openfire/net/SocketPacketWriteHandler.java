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
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
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
    private IQRouter iqRouter;
    private MessageRouter messageRouter;
    private PresenceRouter presenceRouter;

    public SocketPacketWriteHandler(SessionManager sessionManager, RoutingTable routingTable,
            OfflineMessageStrategy messageStrategy) {
        this.sessionManager = sessionManager;
        this.messageStrategy = messageStrategy;
        this.routingTable = routingTable;
        this.server = XMPPServer.getInstance();
        iqRouter = server.getIQRouter();
        messageRouter = server.getMessageRouter();
        presenceRouter = server.getPresenceRouter();
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
            messageRouter.routingFailed(packet);
        }
        else if (packet instanceof Presence) {
            presenceRouter.routingFailed(packet);
        }
        else {
            iqRouter.routingFailed(packet);
        }
    }
}
