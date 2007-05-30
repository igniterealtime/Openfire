/**
 * $RCSfile: PacketDelivererImpl.java,v $
 * $Revision: 2715 $
 * $Date: 2005-08-23 22:16:45 -0300 (Tue, 23 Aug 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.net.SocketPacketWriteHandler;
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

    public PacketDelivererImpl() {
        super("Packet Delivery");
    }

    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (packet == null) {
            throw new PacketException("Packet was null");
        }
        if (deliverHandler == null) {
            throw new PacketException("Could not send packet - no route" + packet.toString());
        }
        // Let the SocketPacketWriteHandler process the packet. SocketPacketWriteHandler may send
        // it over the socket or store it when user is offline or drop it.
        deliverHandler.process(packet);
    }

    public void start() throws IllegalStateException {
        super.start();
        deliverHandler = new SocketPacketWriteHandler(XMPPServer.getInstance().getRoutingTable());
    }

    public void stop() {
        super.stop();
        deliverHandler = null;
    }
}
