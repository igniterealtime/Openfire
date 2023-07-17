/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.ClientStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;

/**
 * Client-specific ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created
 * and how to build and configure a {@link NettyConnection}.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public class NettyClientConnectionHandler extends NettyConnectionHandler{

    /**
     * Enable / disable backup delivery of stanzas to the 'offline message store' of the corresponding user when a stanza
     * failed to be delivered on a client connection. When disabled, stanzas that can not be delivered on the connection
     * are discarded.
     */
    public static final SystemProperty<Boolean> BACKUP_PACKET_DELIVERY_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.client.netty-backup-packet-delivery.enabled") // TODO - rename once MINA-specific is removed and NettyClientConnectionHandler becomes ClientConnectionHandler
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public NettyClientConnectionHandler(ConnectionConfiguration configuration) {
        super(configuration);
    }

    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        // TODO - can this be moved up to superclass? appears to be same as Server implementation
        final PacketDeliverer backupDeliverer = BACKUP_PACKET_DELIVERY_ENABLED.getValue() ? new OfflinePacketDeliverer() : null;
        return new NettyConnection(ctx, backupDeliverer, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new ClientStanzaHandler(XMPPServer.getInstance().getPacketRouter(), connection);

    }


    // TODO Do we need a sessionIdle function for the ClientConnectionHandler specifically? :
    /**
     * In addition to the functionality provided by the parent class, this
     * method will send XMPP ping requests to the remote entity on every first
     * invocation of this method (which will occur after a period of half the
     * allowed connection idle time has passed, without any IO).
     *
     * XMPP entities must respond with either an IQ result or an IQ error
     * (feature-unavailable) stanza upon receiving the XMPP ping stanza. Both
     * responses will be received by Openfire and will cause the connection idle
     * count to be reset.
     *
     * Entities that do not respond to the IQ Ping stanzas can be considered
     * dead, and their connection will be closed by the parent class
     * implementation on the second invocation of this method.
     *
     * Note that whitespace pings that are sent by XMPP entities will also cause
     * the connection idle count to be reset.
     *
     * @see ConnectionHandler#sessionIdle(IoSession, IdleStatus)
     */

    @Override
    int getMaxIdleTime()
    {
        return JiveGlobals.getIntProperty( "xmpp.server.idle", 6 * 60 * 1000 ) / 1000;
    }

}
