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
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;

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

    private static boolean configRequiresStrictCertificateValidation() {
        return JiveGlobals.getBooleanProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION, true);
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

    /**
     * Called when SSL Handshake has been completed.
     *
     * If successful, attempts authentication via SASL, or dialback dependent on configuration and certificate validity.
     * If not successful, either attempts dialback on a plain un-encrypted connection, or throws an exception dependent
     * on configuration.
     *
     * @param ctx ChannelHandlerContext for the Netty channel
     * @param evt Event that has been triggered - this implementation specifically identifies SslHandshakeCompletionEvent
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (!sslInitDone && evt instanceof SslHandshakeCompletionEvent) {
            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
            RespondingServerStanzaHandler stanzaHandler = (RespondingServerStanzaHandler) ctx.channel().attr(NettyConnectionHandler.HANDLER).get();

            if (event.isSuccess()) {
                sslInitDone = true;

                NettyConnection connection = ctx.channel().attr(NettyConnectionHandler.CONNECTION).get();
                connection.setEncrypted(true);
                Log.debug("TLS negotiation was successful. Connection encrypted. Proceeding with authentication...");

                // If TLS cannot be used for authentication, it is permissible to use another authentication mechanism
                // such as dialback. RFC 6120 does not explicitly allow this, as it does not take into account any other
                // authentication mechanism other than TLS (it does mention dialback in an interoperability note. However,
                // RFC 7590 Section 3.4 writes: "In particular for XMPP server-to-server interactions, it can be reasonable
                // for XMPP server implementations to accept encrypted but unauthenticated connections when Server Dialback
                // keys [XEP-0220] are used." In short: if Dialback is allowed, unauthenticated TLS is better than no TLS.
                if (SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), domainPair.getRemote(), true)) {
                    Log.debug("SASL authentication will be attempted");
                    Log.debug("Send the stream header and wait for response...");
                    sendNewStreamHeader(connection);
                } else if (JiveGlobals.getBooleanProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION, true)) {
                    Log.warn("Aborting attempt to create outgoing session as certificate verification failed, and strictCertificateValidation is enabled.");
                    abandonSession(stanzaHandler);
                } else if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
                    Log.debug("Failed to verify certificates for SASL authentication. Will continue with dialback.");
                    sendNewStreamHeader(connection);
                } else {
                    Log.warn("Unable to authenticate the connection: Failed to verify certificates for SASL authentication and dialback is not available.");
                    abandonSession(stanzaHandler);
                }

                ctx.fireChannelActive();
            } else {
                // SSL Handshake has failed
                stanzaHandler.setSession(null);

                if (isSSLHandshakeException(event) && isCausedByCertificateError(event)){
                    if (configRequiresStrictCertificateValidation()) {
                        Log.warn("Aborting attempt to create outgoing session as TLS handshake failed, and strictCertificateValidation is enabled.");
                        throw new RuntimeException(event.cause());
                    } else {
                        Log.warn("TLS handshake failed due to a certificate validation error");
                    }
                }

                // fall back to dialback
                if (ServerDialback.isEnabled() && connectionConfigDoesNotRequireTls()) {
                    Log.debug("Unable to create a new TLS session. Going to try connecting using server dialback as a fallback.");

                    // Use server dialback (pre XMPP 1.0) over a plain connection
                    final LocalOutgoingServerSession outgoingSession = new ServerDialback(domainPair).createOutgoingSession(port);
                    if (outgoingSession != null) {
                        Log.debug("Successfully created new session (using dialback as a fallback)!");
                        stanzaHandler.setSessionAuthenticated(true);
                        stanzaHandler.setSession(outgoingSession);
                    } else {
                        Log.warn("Unable to create a new session: Dialback (as a fallback) failed.");
                    }
                } else {
                    Log.warn("Unable to create a new session: exhausted all options (not trying dialback as a fallback, as server dialback is disabled by configuration.");
                }

                stanzaHandler.setAttemptedAllAuthenticationMethods(true);
            }
        }

        super.userEventTriggered(ctx, evt);
    }

    private static boolean isSSLHandshakeException(SslHandshakeCompletionEvent event) {
        return event.cause() instanceof SSLHandshakeException;
    }

    private static boolean isCausedByCertificateError(SslHandshakeCompletionEvent event) {
        return event.cause().getMessage().contains("java.security.cert.CertPathBuilderException");
    }

    private static void abandonSession(RespondingServerStanzaHandler stanzaHandler) {
        stanzaHandler.setSession(null);
        stanzaHandler.setAttemptedAllAuthenticationMethods(true);
    }

    private void sendNewStreamHeader(NettyConnection connection) {
        StringBuilder openingStream = new StringBuilder();
        openingStream.append("<stream:stream");
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            openingStream.append(" xmlns:db=\"jabber:server:dialback\"");
        }
        openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        openingStream.append(" xmlns=\"jabber:server\"");
        openingStream.append(" from=\"").append(domainPair.getLocal()).append("\""); // OF-673
        openingStream.append(" to=\"").append(domainPair.getRemote()).append("\"");
        openingStream.append(" version=\"1.0\">");
        connection.deliverRawText(openingStream.toString());
    }

    private boolean connectionConfigDoesNotRequireTls() {
        return this.configuration.getTlsPolicy() != Connection.TLSPolicy.required;
    }
}
