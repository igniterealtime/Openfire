/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelHandlerContext;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.openfire.net.MultiplexerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.SystemProperty;

import java.time.Duration;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NettyConnection}.
 *
 * @author Alex Gidman
 */
public class NettyMultiplexerConnectionHandler extends NettyConnectionHandler {

    /**
     * Enable / disable backup delivery of stanzas to other connections in the same connection manager when a stanza
     * failed to be delivered on a multiplexer (connection manager) connection. When disabled, stanzas that can not
     * be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.multiplex.backup-packet-delivery.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();
    public NettyMultiplexerConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? new MultiplexerPacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new MultiplexerStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);
    }

    @Override
    public Duration getMaxIdleTime() {
        return ConnectionSettings.Multiplex.IDLE_TIMEOUT_PROPERTY.getValue();
    }

    @Override
    public String toString()
    {
        return "NettyMultiplexerConnectionHandler{" +
            "sslInitDone=" + sslInitDone +
            ", configuration=" + configuration +
            '}';
    }
}
