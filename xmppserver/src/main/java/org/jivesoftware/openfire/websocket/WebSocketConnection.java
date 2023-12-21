/*
 * Copyright (C) 2015 Tom Evans, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.jivesoftware.openfire.websocket.WebSocketClientConnectionHandler.STREAM_SUBSTITUTION_ENABLED;

/**
 * Following the conventions of the BOSH implementation, this class extends {@link VirtualConnection}
 * and delegates the expected XMPP connection behaviors to the corresponding {@link WebSocketClientConnectionHandler}.
 */
public class WebSocketConnection extends VirtualConnection
{
    private static final Logger Log = LoggerFactory.getLogger(WebSocketConnection.class);

    private InetSocketAddress remotePeer;
    private WebSocketClientConnectionHandler socket;
    private PacketDeliverer backupDeliverer;
    private ConnectionConfiguration configuration;
    private ConnectionType connectionType;
    private WebSocketClientStanzaHandler stanzaHandler;

    public WebSocketConnection(WebSocketClientConnectionHandler socket, PacketDeliverer backupDeliverer, InetSocketAddress remotePeer) {
        this.socket = socket;
        this.backupDeliverer = backupDeliverer;
        this.remotePeer = remotePeer;
        this.connectionType = ConnectionType.SOCKET_C2S;
    }

    @Override
    public void closeVirtualConnection(@Nullable final StreamError error)
    {
        try {
            if (error != null) {
                deliverRawText0(error.toXML());
            }

            final String closeElement;
            if (STREAM_SUBSTITUTION_ENABLED.getValue()) {
                closeElement = "</stream:stream>";
            } else {
                closeElement = "<" + WebSocketClientStanzaHandler.STREAM_FOOTER + " xmlns='"+WebSocketClientStanzaHandler.FRAMING_NAMESPACE+"'/>";
            }
            deliverRawText0(closeElement);
        } finally {
            socket.getWsSession().close();
        }
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
        if (isClosed()) {
            if (backupDeliverer != null) {
                backupDeliverer.deliver(packet);
            } else {
                Log.trace("Discarding packet that was due to be delivered on closed connection {}, for which no backup deliverer was configured.", this);
            }
        }
        else {
            boolean errorDelivering = false;
            try {
                final String xml;
                if (Namespace.NO_NAMESPACE.equals(packet.getElement().getNamespace())) {
                    // use string-based operation here to avoid cascading xmlns wonkery
                    StringBuilder packetXml = new StringBuilder(packet.toXML());
                    packetXml.insert(packetXml.indexOf(" "), " xmlns=\"jabber:client\"");
                    xml = packetXml.toString();
                } else {
                    xml = packet.toXML();
                }
                socket.getWsSession().getRemote().sendString(xml);
            } catch (Exception e) {
                Log.debug("Error delivering packet:\n" + packet, e);
                errorDelivering = true;
            }
            if (errorDelivering) {
                close();
                // Retry sending the packet again. Most probably if the packet is a
                // Message it will be stored offline
                if (backupDeliverer != null) {
                    backupDeliverer.deliver(packet);
                } else {
                    Log.trace("Discarding packet that failed to be delivered to connection {}, for which no backup deliverer was configured.", this);
                }
            }
            else {
                session.incrementServerPacketCount();
            }
        }
    }

    @Override
    public void deliverRawText(String text)
    {
        if (!isClosed()) {
            deliverRawText0(text);
        }
    }

    private void deliverRawText0(String text)
    {
        boolean errorDelivering = false;
        try {
            socket.getWsSession().getRemote().sendString(text);
        } catch (Exception e) {
            Log.debug("Error delivering raw text:\n" + text, e);
            errorDelivering = true;
        }

        // Attempt to close the connection if delivering text fails.
        if (errorDelivering) {
            close();
        }
    }

    @Override
    public boolean validate() {
        return socket.isWebSocketOpen();
    }

    @Override
    @Deprecated // Remove in Openfire 4.9 or later.
    public boolean isSecure() {
        return isEncrypted();
    }

    @Override
    public boolean isEncrypted() {
        return socket.isWebSocketEncrypted();
    }

    @Override
    @Nullable
    public PacketDeliverer getPacketDeliverer() {
        return backupDeliverer;
    }

    @Override
    public ConnectionConfiguration getConfiguration() {
        if (configuration == null) {
            final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
            configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
        }
        return configuration;
    }

    @Override
    public boolean isCompressed() {
        return WebSocketClientConnectionHandler.isCompressionEnabled();
    }

    @Override
    public Optional<String> getTLSProtocolName() {
        return Optional.ofNullable(this.socket.getTLSProtocolName());
    }

    @Override
    public Optional<String> getCipherSuiteName() {
        return Optional.ofNullable(this.socket.getCipherSuiteName());
    }

    void setStanzaHandler(final WebSocketClientStanzaHandler stanzaHandler) {
        this.stanzaHandler = stanzaHandler;
    }

    WebSocketClientStanzaHandler getStanzaHandler() {
        return stanzaHandler;
    }

    @Override
    public void reinit(LocalSession session) {
        super.reinit(session);
        stanzaHandler.setSession(session);
    }

    @Override
    public String toString()
    {
        return "WebSocketConnection{" +
            "jid=" + (getStanzaHandler() == null ? "(null)" : getStanzaHandler().getAddress()) +
            ", remotePeer=" + remotePeer +
            ", socket=" + socket +
            ", connectionType=" + connectionType +
            '}';
    }
}
