/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Represents a connection of a Client Session that was established to a Connection Manager.
 * Connection Managers have their own physical connections to the server that are multiplexed
 * among connected clients. Each created {@link org.jivesoftware.openfire.session.ClientSession}
 * will use an instance of this class as its connection.
 *
 * @author Gaston Dombiak
 */
public class ClientSessionConnection extends VirtualConnection {

    private String connectionManagerName;
    private String serverName;
    private ConnectionMultiplexerManager multiplexerManager;
    private String hostName;
    private String hostAddress;

    public ClientSessionConnection(String connectionManagerName, String hostName, String hostAddress) {
        this.connectionManagerName = connectionManagerName;
        multiplexerManager = ConnectionMultiplexerManager.getInstance();
        serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        this.hostName = hostName;
        this.hostAddress = hostAddress;
    }

    /**
     * Delivers the packet to the Connection Manager that in turn will forward it to the
     * target user. Connection Managers may have one or many connections to the server so
     * just get any connection to the Connection Manager (uses a random) and use it.<p>
     *
     * If the packet to send does not have a TO attribute then wrap the packet with a
     * special IQ packet. The wrapper IQ packet will be sent to the Connection Manager
     * and the stream ID of this Client Session will be used for identifying that the wrapped
     * packet must be sent to the connected user. Since some packets can be exchanged before
     * the user has a binded JID we need to use the stream ID as the unique identifier.
     *
     * @param packet the packet to send to the user.
     */
    public void deliver(Packet packet) {
        String streamID = session.getStreamID().getID();
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName,streamID);
        if (multiplexerSession != null) {
            // Wrap packet so that the connection manager can figure out the target session
            Route wrapper = new Route(streamID);
            wrapper.setFrom(serverName);
            wrapper.setTo(connectionManagerName);
            wrapper.setChildElement(packet.getElement().createCopy());
            // Deliver wrapper
            multiplexerSession.process(wrapper);
            session.incrementServerPacketCount();
        }
    }

    /**
     * Delivers the stanza to the Connection Manager that in turn will forward it to the
     * target user. Connection Managers may have one or many connections to the server so
     * just get any connection to the Connection Manager (uses a random) and use it.<p>
     *
     * The stanza to send wrapped with a special IQ packet. The wrapper IQ packet will be
     * sent to the Connection Manager and the stream ID of this Client Session will be used
     * for identifying that the wrapped stanza must be sent to the connected user.
     *
     * @param text the stanza to send to the user.
     */
    public void deliverRawText(String text) {
        String streamID = session.getStreamID().getID();
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName,streamID);
        if (multiplexerSession != null) {
            // Wrap packet so that the connection manager can figure out the target session
            StringBuilder sb = new StringBuilder(200 + text.length());
            sb.append("<route from=\"").append(serverName);
            sb.append("\" to=\"").append(connectionManagerName);
            sb.append("\" streamid=\"").append(streamID).append("\">");
            sb.append(text);
            sb.append("</route>");
            // Deliver the wrapped stanza
            multiplexerSession.deliverRawText(sb.toString());
        }
    }

    public byte[] getAddress() throws UnknownHostException {
        if (hostAddress != null) {
            return InetAddress.getByName(hostAddress).getAddress();
        }
        return null;
    }

    public String getHostAddress() throws UnknownHostException {
        if (hostAddress != null) {
            return hostAddress;
        }
        // Return IP address of the connection manager that the client used to log in
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName);
        if (multiplexerSession != null) {
            return multiplexerSession.getHostAddress();
        }
        return null;
    }

    public String getHostName() throws UnknownHostException {
        if (hostName != null) {
            return hostName;
        }
        // Return IP address of the connection manager that the client used to log in
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName);
        if (multiplexerSession != null) {
            return multiplexerSession.getHostName();
        }
        return null;
    }

    public void systemShutdown() {
        // Do nothing since a system-shutdown error will be sent to the Connection Manager
        // that in turn will send a system-shutdown to connected clients. This is an
        // optimization to reduce number of packets being sent from the server.
    }

    /**
     * If the Connection Manager or the Client requested to close the connection then just do
     * nothing. But if the server originated the request to close the connection then we need
     * to send to the Connection Manager a packet letting him know that the Client Session needs
     * to be terminated.
     */
    @Override
	public void closeVirtualConnection() {
        // Figure out who requested the connection to be closed
        String streamID = session.getStreamID().getID();
        if (multiplexerManager.getClientSession(connectionManagerName, streamID) == null) {
            // Client or Connection manager requested to close the session
            // Do nothing since it has already been removed and closed
        }
        else {
            ConnectionMultiplexerSession multiplexerSession =
                    multiplexerManager.getMultiplexerSession(connectionManagerName,streamID);
            if (multiplexerSession != null) {
                // Server requested to close the client session so let the connection manager
                // know that he has to finish the client session
                IQ closeRequest = new IQ(IQ.Type.set);
                closeRequest.setFrom(serverName);
                closeRequest.setTo(connectionManagerName);
                Element child = closeRequest.setChildElement("session",
                        "http://jabber.org/protocol/connectionmanager");
                child.addAttribute("id", streamID);
                child.addElement("close");
                multiplexerSession.process(closeRequest);
            }
        }
    }
}
