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
package org.jivesoftware.messenger.spi;


import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.transport.TransportHandler;
import javax.xml.stream.XMLStreamException;

/**
 * In-memory implementation of the packet transporter service.
 *
 * @author Iain Shigeoka
 */
public class PacketTransporterImpl extends BasicModule implements PacketTransporter {

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    public TransportHandler transportHandler;

    /**
     * deliverer for xmpp server
     */
    public PacketDeliverer deliverer;

    /**
     * xmpp server
     */
    public XMPPServer xmppServer;

    /**
     * This is a singleton, you can't create one. Be very careful not to do anything
     * that refers back to the factory's create method. Do initialization in the init()
     * method if at all possible.
     */
    public PacketTransporterImpl() {
        super("XMPP Packet Transporter");
    }

    /**
     * Obtain the transport handler that this transporter uses for delivering
     * transport packets.
     *
     * @return The transport handler instance used by this transporter
     */
    public TransportHandler getTransportHandler() {
        return transportHandler;
    }

    /**
     * Delivers the given packet based on packet recipient and sender. The
     * deliverer defers actual routing decisions to other classes.
     * <h2>Warning</h2>
     * Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.
     *
     * @param packet The packet to route
     * @throws NullPointerException If the packet is null or the
     *                              packet could not be routed
     */
    public void deliver(XMPPPacket packet)
            throws UnauthorizedException, PacketException, XMLStreamException {
        if (packet == null) {
            throw new NullPointerException();
        }

        if (xmppServer != null && xmppServer.isLocal(packet.getRecipient())) {
            deliverer.deliver(packet);
        }
        else if (transportHandler != null) {
            transportHandler.process(packet);
        }
        else {
            Log.warn("Could not deliver message: no deliverer available "
                    + packet.toString());
        }
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(XMPPServer.class, "xmppServer");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(TransportHandler.class, "transportHandler");
        return trackInfo;
    }
}
