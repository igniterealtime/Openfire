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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.component.InternalComponentManager;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.net.SocketPacketWriteHandler;
import org.xmpp.component.Component;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * In-memory implementation of the packet deliverer service
 *
 * @author Iain Shigeoka
 */
public class PacketDelivererImpl extends BasicModule implements PacketDeliverer {

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    protected SocketPacketWriteHandler deliverHandler;

    private OfflineMessageStrategy messageStrategy;
    private SessionManager sessionManager;

    private InternalComponentManager componentManager;

    public PacketDelivererImpl() {
        super("Packet Delivery");
        componentManager = InternalComponentManager.getInstance();
    }

    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (packet == null) {
            throw new PacketException("Packet was null");
        }
        if (deliverHandler == null) {
            throw new PacketException("Could not send packet - no route" + packet.toString());
        }
        // Check if the target of the packet is the a component itself. If it does then just pass
        // the packet to the component for processing it
        JID recipient = packet.getTo();
        if (recipient != null && recipient.getNode() == null && recipient.getResource() == null) {
            // Check for registered components
            Component component = componentManager.getComponent(recipient);
            if (component != null) {
                component.processPacket(packet);
                return;
            }
        }
        // Let the SocketPacketWriteHandler process the packet. SocketPacketWriteHandler may send
        // it over the socket or store it when user is offline or drop it.
        deliverHandler.process(packet);
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        sessionManager = server.getSessionManager();
    }

    public void start() throws IllegalStateException {
        super.start();
        deliverHandler =
                new SocketPacketWriteHandler(sessionManager,
                        XMPPServer.getInstance().getRoutingTable(), messageStrategy);
    }

    public void stop() {
        super.stop();
        deliverHandler = null;
    }
}
