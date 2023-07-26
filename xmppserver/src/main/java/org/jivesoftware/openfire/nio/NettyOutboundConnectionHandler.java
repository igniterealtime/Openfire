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
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateException;

/**
 * Outbound (S2S) specific ConnectionHandler that knows which subclass of {@link StanzaHandler} should be created
 * and how to build and configure a {@link NettyConnection}.
 *
 * @author Matthew Vivian
 * @author Alex Gidman
 */
public class NettyOutboundConnectionHandler extends NettyConnectionHandler {
    private static final Logger Log = LoggerFactory.getLogger(NettyOutboundConnectionHandler.class);
    private final DomainPair domainPair;
    private final int port;
    volatile boolean sslInitDone;

    public NettyOutboundConnectionHandler(ConnectionConfiguration configuration, DomainPair domainPair, int port) {
        super(configuration);
        this.domainPair = domainPair;
        this.port = port;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (sslInitDone) {
            super.channelActive(ctx);
        }
    }
    @Override
    NettyConnection createNettyConnection(ChannelHandlerContext ctx) {
        return new NettyConnection(ctx, null, configuration);
    }

    @Override
    StanzaHandler createStanzaHandler(NettyConnection connection) {
        return new RespondingServerStanzaHandler( XMPPServer.getInstance().getPacketRouter(), connection, domainPair );
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        super.exceptionCaught(ctx, cause);

        if (isCertificateException(cause) && configRequiresStrictCertificateValidation()) {
            Log.warn("Aborting attempt to create outgoing session as TLS handshake failed, and strictCertificateValidation is enabled.");
            throw new RuntimeException(cause);
        }
     }

    private static boolean configRequiresStrictCertificateValidation() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION, true);
    }

    public boolean isCertificateException(Throwable cause) {
        return cause instanceof CertificateException;
    }

    @Override
    public int getMaxIdleTime() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY, 360);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Log.trace("Adding NettyOutboundConnectionHandler");
        super.handlerAdded(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!sslInitDone && evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent e = (SslHandshakeCompletionEvent) evt;
            if (e.isSuccess()) {
                sslInitDone = true;
                ctx.fireChannelActive();
            } else {
                // SSL Handshake has failed, fall back to dialback
                RespondingServerStanzaHandler stanzaHandler = (RespondingServerStanzaHandler) ctx.channel().attr(NettyConnectionHandler.HANDLER).get();

                if (ServerDialback.isEnabled() && connectionConfigDoesNotRequireTls()) {
                    Log.debug("Unable to create a new session. Going to try connecting using server dialback as a fallback.");

                    // Use server dialback (pre XMPP 1.0) over a plain connection
                    final LocalOutgoingServerSession outgoingSession = new ServerDialback(domainPair).createOutgoingSession(port);
                    if (outgoingSession != null) {
                        Log.debug("Successfully created new session (using dialback as a fallback)!");
                        stanzaHandler.setSessionAuthenticated(true);
                        stanzaHandler.setSession(outgoingSession);
                    } else {
                        Log.warn("Unable to create a new session: Dialback (as a fallback) failed.");
                        stanzaHandler.setSession(null);
                    }
                } else {
                    Log.warn("Unable to create a new session: exhausted all options (not trying dialback as a fallback, as server dialback is disabled by configuration.");
                    stanzaHandler.setSession(null);
                }

                stanzaHandler.setAttemptedAllAuthenticationMethods(true);
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    private boolean connectionConfigDoesNotRequireTls() {
        return this.configuration.getTlsPolicy() != Connection.TLSPolicy.required;
    }
}
