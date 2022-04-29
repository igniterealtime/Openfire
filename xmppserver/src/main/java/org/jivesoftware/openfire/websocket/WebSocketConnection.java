/*
 * Copyright (C) 2015 Tom Evans, 2022 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;

/**
 * Following the conventions of the BOSH implementation, this class extends {@link VirtualConnection}
 * and delegates the expected XMPP connection behaviors to the corresponding {@link XmppWebSocket}.
 */
public class WebSocketConnection extends VirtualConnection
{
    private static final Logger Log = LoggerFactory.getLogger(WebSocketConnection.class);

    private InetSocketAddress remotePeer;
    private XmppWebSocket socket;
    private PacketDeliverer backupDeliverer;
    private ConnectionConfiguration configuration;
    private ConnectionType connectionType;

    public WebSocketConnection(XmppWebSocket socket, PacketDeliverer backupDeliverer, InetSocketAddress remotePeer) {
        this.socket = socket;
        this.backupDeliverer = backupDeliverer;
        this.remotePeer = remotePeer;
        this.connectionType = ConnectionType.SOCKET_C2S;
    }

    @Override
    public void closeVirtualConnection(@Nullable final StreamError error)
    {
        socket.closeSession(error);
    }

    @Override
    public byte[] getAddress() {
        return remotePeer.getAddress().getAddress();
    }

    @Override
    public String getHostAddress() {
        return remotePeer.getAddress().getHostAddress();
    }

    @Override
    public String getHostName()  {
        return remotePeer.getHostName();
    }

    @Override
    public void systemShutdown() {
        close(new StreamError(StreamError.Condition.system_shutdown));
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException
    {
        final String xml;
        if (Namespace.NO_NAMESPACE.equals(packet.getElement().getNamespace())) {
            // use string-based operation here to avoid cascading xmlns wonkery
            StringBuilder packetXml = new StringBuilder(packet.toXML());
            packetXml.insert(packetXml.indexOf(" "), " xmlns=\"jabber:client\"");
            xml = packetXml.toString();
        } else {
            xml = packet.toXML();
        }
        if (validate()) {
            deliverRawText(xml);
        } else {
            // use fallback delivery mechanism (offline)
            if (backupDeliverer != null) {
                backupDeliverer.deliver(packet);
            } else {
                Log.trace("Discarding packet that failed to be delivered to connection {}, for which no backup deliverer was configured.", this);
            }
        }
    }

    @Override
    public void deliverRawText(String text)
    {
        socket.deliver(text);
    }

    @Override
    public boolean validate() {
        return socket.isWebSocketOpen();
    }

    @Override
    public boolean isSecure() {
        return socket.isWebSocketSecure();
    }

    @Override
    @Nullable
    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    @Override
    public ConnectionConfiguration getConfiguration() {
        if (configuration == null) {
            final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
            configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
        }
        return configuration;
    }

    @Override
    public boolean isCompressed() {
        return XmppWebSocket.isCompressionEnabled();
    }

    @Override
    public void reinit(LocalSession session) {
        this.socket.setXmppSession((LocalClientSession)session);
        super.reinit(session);
    }
}
