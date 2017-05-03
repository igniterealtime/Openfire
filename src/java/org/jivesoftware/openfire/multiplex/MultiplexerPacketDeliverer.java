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

package org.jivesoftware.openfire.multiplex;

import org.dom4j.Element;
import org.jivesoftware.openfire.OfflineMessageStrategy;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Fallback method used by {@link org.jivesoftware.openfire.net.SocketConnection} when
 * connected to a connection manager. The fallback method will be used when a SocketConnection
 * fails to send a {@link Packet} (probably because the socket was closed).<p>
 *
 * The first attempt will be to send the packet using another connection to the same connection
 * manager (since managers may have a pool of connections to the server). And if that fails then
 * instances of {@link Message} may be stored offline for later retrieval. Since packets may be
 * wrapped by special IQ packets (read the Connection Manager JEP for more information) we need
 * to unwrap the packet and store the wrapped packet offline.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerPacketDeliverer implements PacketDeliverer {

	private static final Logger Log = LoggerFactory.getLogger(MultiplexerPacketDeliverer.class);

    private OfflineMessageStrategy messageStrategy;
    private String connectionManagerDomain;
    private ConnectionMultiplexerManager multiplexerManager;

    public MultiplexerPacketDeliverer() {
        this.messageStrategy = XMPPServer.getInstance().getOfflineMessageStrategy();
        multiplexerManager = ConnectionMultiplexerManager.getInstance();
    }

    public void setConnectionManagerDomain(String connectionManagerDomain) {
        this.connectionManagerDomain = connectionManagerDomain;
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        // Check if we can send the packet using another session
        if (connectionManagerDomain == null) {
            // Packet deliverer has not yet been configured so handle unprocessed packet
            handleUnprocessedPacket(packet);
        }
        else {
            // Try getting another session to the same connection manager 
            ConnectionMultiplexerSession session =
                    multiplexerManager.getMultiplexerSession(connectionManagerDomain);
            if (session == null || session.isClosed()) {
                // No other session was found so handle unprocessed packet
                handleUnprocessedPacket(packet);
            }
            else {
                // Send the packet using this other session to the same connection manager
                session.process(packet);
            }
        }
    }

    private void handleUnprocessedPacket(Packet packet) {
        if (packet instanceof Message) {
            messageStrategy.storeOffline((Message) packet);
        }
        else if (packet instanceof Presence) {
            // presence packets are dropped silently
            //dropPacket(packet);
        }
        else if (packet instanceof IQ) {
            IQ iq = (IQ) packet;
            // Check if we need to unwrap the packet
            Element child = iq.getChildElement();
            if (child != null && "session".equals(child.getName()) &&
                    "http://jabber.org/protocol/connectionmanager"
                            .equals(child.getNamespacePrefix())) {
                Element send = child.element("send");
                if (send != null) {
                    // Unwrap packet
                    Element wrappedElement = (Element) send.elements().get(0);
                    if ("message".equals(wrappedElement.getName())) {
                        handleUnprocessedPacket(new Message(wrappedElement));
                    }
                }
            }
            else {
                // IQ packets are logged but dropped
                Log.warn(LocaleUtils.getLocalizedString("admin.error.routing") + "\n" +
                        packet.toString());
            }
        }
    }
}
