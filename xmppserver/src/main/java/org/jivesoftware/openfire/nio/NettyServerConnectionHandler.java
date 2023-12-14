/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Server-specific ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created
 * and how to build and configure a {@link NettyConnection}.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public class NettyServerConnectionHandler extends NettyConnectionHandler
{
    private static final Logger Log = LoggerFactory.getLogger(NettyServerConnectionHandler.class);

    /**
     * Enable / disable backup delivery of stanzas to the XMPP server itself when a stanza failed to be delivered on a
     * server-to-server connection. When disabled, stanzas that can not be delivered on the connection are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("xmpp.server.backup-packet-delivery.enabled")
            .setDefaultValue(true)
            .setDynamic(true)
            .build();
    private final boolean directTLS;

    public NettyServerConnectionHandler(ConnectionConfiguration configuration)
    {
        super(configuration);
        this.directTLS = configuration.getTlsPolicy() == Connection.TLSPolicy.directTLS;
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? XMPPServer.getInstance().getPacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new ServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection, directTLS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Log.trace("Adding NettyServerConnectionHandler");
        super.handlerAdded(ctx);
    }

    public Duration getMaxIdleTime() {
        return ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY.getValue();
    }

    @Override
    public String toString()
    {
        return "NettyServerConnectionHandler{" +
            "directTLS=" + directTLS +
            ", sslInitDone=" + sslInitDone +
            ", configuration=" + configuration +
            '}';
    }
}
