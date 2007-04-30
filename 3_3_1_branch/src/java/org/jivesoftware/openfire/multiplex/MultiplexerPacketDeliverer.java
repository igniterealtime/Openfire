/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.multiplex;

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.OfflineMessageStrategy;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
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
            if (session == null || session.getConnection().isClosed()) {
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
