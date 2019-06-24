/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.net;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;

import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

/**
 * A SocketReader specialized for server connections. This reader will be used when the open
 * stream contains a jabber:server namespace. Server-to-server communication requires two
 * TCP connections between the servers where one is used for sending packets whilst the other
 * connection is used for receiving packets. The connection used for receiving packets will use
 * a ServerSocketReader since the other connection will not receive packets.<p>
 *
 * The received packets will be routed using another thread to ensure that many received packets
 * could be routed at the same time. To avoid creating new threads every time a packet is received
 * each {@code ServerSocketReader} instance uses a {@link ThreadPoolExecutor}. By default the
 * maximum number of threads that the executor may have is 50. However, this value may be modified
 * by changing the property <b>xmpp.server.processing.max.threads</b>.
 *
 * @author Gaston Dombiak
 */
public class ServerSocketReader extends SocketReader {

    private static final Logger Log = LoggerFactory.getLogger(ServerSocketReader.class);

    public ServerSocketReader(PacketRouter router, RoutingTable routingTable, String serverName,
            Socket socket, SocketConnection connection, boolean useBlockingMode, boolean directTLS) {
        super(router, routingTable, serverName, socket, connection, useBlockingMode, directTLS);
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    @Override
    protected void processIQ(final IQ packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            try {
                super.processIQ(packet);
            }
            catch (UnauthorizedException e) {
                Log.error("Error processing packet", e);
            }
        }
        catch (PacketRejectedException e) {
            Log.debug("IQ rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    @Override
    protected void processPresence(final Presence packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            try {
                super.processPresence(packet);
            }
            catch (UnauthorizedException e) {
                Log.error("Error processing packet", e);
            }
        }
        catch (PacketRejectedException e) {
            Log.debug("Presence rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    @Override
    protected void processMessage(final Message packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            try {
                ServerSocketReader.super.processMessage(packet);
            }
            catch (UnauthorizedException e) {
                Log.error("Error processing packet", e);
            }
        }
        catch (PacketRejectedException e) {
            Log.debug("Message rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Remote servers may send subsequent db:result packets so we need to process them in order
     * to validate new domains.
     *
     * @param doc the unknown DOM element that was received
     * @return true if the packet is a db:result packet otherwise false.
     */
    @Override
    protected boolean processUnknowPacket(Element doc) {
        // Handle subsequent db:result packets
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
            if (!((LocalIncomingServerSession) session).validateSubsequentDomain(doc)) {
                open = false;
            }
            return true;
        }
        else if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
            // The Receiving Server is reusing an existing connection for sending the
            // Authoritative Server a request for verification of a key
            ((LocalIncomingServerSession) session).verifyReceivedKey(doc);
            return true;
        }
        return false;
    }

    /**
     * Make sure that the received packet has a TO and FROM values defined and that it was sent
     * from a previously validated domain. If the packet does not matches any of the above
     * conditions then a PacketRejectedException will be thrown.
     *
     * @param packet the received packet.
     * @throws PacketRejectedException if the packet does not include a TO or FROM or if the packet
     *                                 was sent from a domain that was not previously validated.
     */
    private void packetReceived(Packet packet) throws PacketRejectedException {
        if (packet.getTo() == null || packet.getFrom() == null) {
            Log.debug("Closing IncomingServerSession due to packet with no TO or FROM: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes no TO or FROM
            StreamError error = new StreamError(StreamError.Condition.improper_addressing);
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            open = false;
            throw new PacketRejectedException("Packet with no TO or FROM attributes");
        }
        else if (!((LocalIncomingServerSession) session).isValidDomain(packet.getFrom().getDomain())) {
            Log.debug("Closing IncomingServerSession due to packet with invalid domain: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes an invalid FROM
            StreamError error = new StreamError(StreamError.Condition.invalid_from);
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            open = false;
            throw new PacketRejectedException("Packet with no TO or FROM attributes");
        }
    }

    @Override
    protected void shutdown() {
        super.shutdown();
    }

    @Override
    boolean createSession(String namespace) throws UnauthorizedException, XmlPullParserException,
            IOException {
        if ("jabber:server".equals(namespace)) {
            // The connected client is a server so create an IncomingServerSession
            session = LocalIncomingServerSession.createSession(serverName, reader, connection, directTLS);
            // After the session has been created, inform all listeners as well.
            ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_created);
            return true;
        }
        return false;
    }

    @Override
    String getNamespace() {
        return "jabber:server";
    }

    @Override
    public String getExtraNamespaces() {
        return "xmlns:db=\"jabber:server:dialback\"";
    }

    @Override
    String getName() {
        return "Server SR - " + hashCode();
    }

    @Override
    boolean validateHost() {
        return true;
    }
}
