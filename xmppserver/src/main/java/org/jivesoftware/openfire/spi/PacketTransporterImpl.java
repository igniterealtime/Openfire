/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.transport.TransportHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

/**
 * In-memory implementation of the packet transporter service.
 *
 * @author Iain Shigeoka
 */
public class PacketTransporterImpl extends BasicModule  {

    private static final Logger Log = LoggerFactory.getLogger(PacketTransporterImpl.class);

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    private TransportHandler transportHandler;

    /**
     * deliverer for xmpp server
     */
    private PacketDeliverer deliverer;

    /**
     * xmpp server
     */
    private XMPPServer xmppServer;

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
     * @throws UnauthorizedException if the user is not authorised
     */
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (packet == null) {
            throw new NullPointerException();
        }

        if (xmppServer != null && xmppServer.isLocal(packet.getTo())) {
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

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        xmppServer = server;
        deliverer = server.getPacketDeliverer();
        transportHandler = server.getTransportHandler();
    }
}
