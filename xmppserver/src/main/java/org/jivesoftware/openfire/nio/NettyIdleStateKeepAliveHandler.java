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

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;
import static org.jivesoftware.openfire.nio.NettyConnectionHandler.IDLE_FLAG;

/**
 * A NettyIdleStateKeepAliveHandler listens for IdleStateEvents triggered by an IdleStateHandler.
 *
 * When invoked, the existence of a flag on the ChannelHandlerContext is determined. If this flag does not exist, then
 * a keep-alive check if performed and the flag is set. If the does exist, then it is assumed that the peer failed the
 * keep-alive check and the connection should be closed.
 *
 * The keep-alive check is implemented as an XMPP ping request. XMPP entities must respond with either an IQ result or
 * an IQ error (feature-unavailable) stanza upon receiving the XMPP ping stanza. Both responses will be received by
 * Openfire. As any inbound data will cause the flag to be reset, the connection will no longer be deemed 'idle'.
 *
 * Entities that do not respond to the IQ Ping stanzas can be considered dead, and their connection will be closed when
 * the IdleStateHandler triggers the second idle event in a row.
 *
 * Note that whitespace pings that are sent by XMPP entities will also cause the connection idle count to be reset.
 *
 * @see IdleStateEvent
 * @see io.netty.handler.timeout.IdleStateHandler
 *
 * @author Alex Gidman
 * */
public class NettyIdleStateKeepAliveHandler extends ChannelDuplexHandler {

    private final boolean clientConnection;
    private static final Logger Log = LoggerFactory.getLogger(NettyIdleStateKeepAliveHandler.class);

    public NettyIdleStateKeepAliveHandler(boolean clientConnection) {
        this.clientConnection = clientConnection;
    }

    /**
     * Processes IdleStateEvents triggered by an IdleStateHandler.
     * If the IdleStateEvent is an idle read state, the Netty channel is closed.
     * If the IdleStateEvent is an idle write state, an XMPP ping  request is sent
     * to the remote entity.
     *
     * @param ctx ChannelHandlerContext
     * @param evt Event caught, expect IdleStateEvent
     * @throws Exception when attempting to deliver ping packet
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
            final boolean doPing = ConnectionSettings.Client.KEEP_ALIVE_PING_PROPERTY.getValue() && clientConnection;
            final Channel channel = ctx.channel();
            if (channel.attr(IDLE_FLAG).getAndSet(true) == null) {
                // Idle flag is now set, but wasn't present before.
                if (doPing) {
                    sendPingPacket(channel);
                }
            } else {
                // Idle flag already present. Connection has been idle for a while. Close it.
                final NettyConnection connection = channel.attr(CONNECTION).get();
                Log.debug("Closing connection because of inactivity: {}", connection);
                connection.close(new StreamError(StreamError.Condition.connection_timeout, doPing ? "Connection has been idle and did not respond to a keep-alive check." : "Connection has been idle."), doPing);
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    /**
     * Sends an IQ ping packet on the channel associated with the channel handler context.
     *
     * @param channel Channel over which to send a ping.
     * @throws UnauthorizedException when attempting to deliver ping packet
     */
    private void sendPingPacket(Channel channel) throws UnauthorizedException {
        NettyConnection connection = channel.attr(CONNECTION).get();
        JID entity = connection.getSession() == null ? null : connection.getSession().getAddress();
        if (entity != null) {
            // Ping the connection to see if it is alive.
            final IQ pingRequest = new IQ(IQ.Type.get);
            pingRequest.setChildElement("ping", IQPingHandler.NAMESPACE);
            pingRequest.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
            pingRequest.setTo(entity);

            Log.debug("Pinging connection that has been idle: {}", connection);

            // OF-1497: Ensure that data sent to the client is processed through LocalClientSession, to avoid
            // synchronisation issues with stanza counts related to Stream Management (XEP-0198)!
            LocalClientSession ofSession = (LocalClientSession) SessionManager.getInstance().getSession(entity);
            if (ofSession == null) {
                Log.warn("Trying to ping a Netty connection that's idle, but has no corresponding Openfire session. Netty Connection: {}", connection);
            } else {
                ofSession.deliver(pingRequest);
            }
        }
    }
}
