/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;


import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.transport.TransportHandler;
import javax.xml.stream.XMLStreamException;

/**
 * A service for transporting packets in/out of the current server domain.
 *
 * @author Iain Shigeoka
 */
public interface PacketTransporter {

    /**
     * Obtain the transport handler that this transporter uses for delivering
     * transport packets.
     *
     * @return The transport handler instance used by this transporter
     */
    public TransportHandler getTransportHandler();

    /**
     * Delivers the given packet based on packet recipient and sender. The
     * deliverer defers actual routing decisions to other classes.
     * <h2>Warning</h2>
     * Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.
     *
     * @param packet The packet to route
     * @throws NullPointerException If the packet is null or the packet could not be routed
     */
    public void deliver(XMPPPacket packet) throws UnauthorizedException, PacketException, XMLStreamException;
}
