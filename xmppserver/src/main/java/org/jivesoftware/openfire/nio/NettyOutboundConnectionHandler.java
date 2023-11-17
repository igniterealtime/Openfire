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
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import org.dom4j.*;
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
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.time.Duration;

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
    public Duration getMaxIdleTime() {
        return ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY.getValue();
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

                final NettyConnection connection = ctx.channel().attr(NettyConnectionHandler.CONNECTION).get();

                if (SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), domainPair.getRemote(), true)) {
                    Log.debug("TLS negotiation with '{}' was successful. Connection encrypted. Proceeding with authentication.", domainPair.getRemote());
                    sendNewStreamHeader(connection);
                    connection.setEncrypted(true);
                    ctx.fireChannelActive();
                } else {
                    Log.debug("TLS negotiation with '{}' was successful, but peer's certificates are not valid for its domain.", domainPair.getRemote());

                    if (JiveGlobals.getBooleanProperty(ConnectionSettings.Server.STRICT_CERTIFICATE_VALIDATION, true)) {
                        Log.warn("Strict certificate validation is enabled. Aborting session with '{}' as its certificates are not valid for its domain.", domainPair.getRemote());
                        stanzaHandler.setSession(null);
                        stanzaHandler.setAttemptedAllAuthenticationMethods();
                        ctx.channel().close();
                        return;
                    }

                    if (!ServerDialback.isEnabled() && !ServerDialback.isEnabledForSelfSigned()) {
                        Log.warn("As peer's certificates are not valid for its domain ('{}'), the SASL EXTERNAL authentication mechanism cannot be used. The Server Dialback authentication mechanism is disabled by configuration. Aborting session, as this leaves no available authentication mechanisms.", domainPair.getRemote());
                        stanzaHandler.setSession(null);
                        stanzaHandler.setAttemptedAllAuthenticationMethods();
                        ctx.channel().close();
                        return;
                    }

                    // If TLS cannot be used for authentication, it is permissible to use another authentication mechanism
                    // such as dialback. RFC 6120 does not explicitly allow this, as it does not take into account any other
                    // authentication mechanism other than TLS (it does mention dialback in an interoperability note. However,
                    // RFC 7590 Section 3.4 writes: "In particular for XMPP server-to-server interactions, it can be reasonable
                    // for XMPP server implementations to accept encrypted but unauthenticated connections when Server Dialback
                    // keys [XEP-0220] are used." In short: if Dialback is allowed, unauthenticated TLS is better than no TLS.

                    Log.warn("As peer's certificates are not valid for its domain ('{}'), the SASL EXTERNAL authentication mechanism cannot be used. The Server Dialback authentication mechanism is available.", domainPair.getRemote());

                    sendNewStreamHeader(connection);
                    connection.setEncrypted(true);
                    ctx.fireChannelActive();
                }
            } else {
                // SSL Handshake has failed
                Log.debug("TLS negotiation with '{}' was unsuccessful", domainPair.getRemote(), event.cause());
                ctx.pipeline().remove(SslHandler.class);

                if (isCertificateException(event) && configRequiresStrictCertificateValidation()) {
                    Log.warn("TLS negotiation with '{}' was unsuccessful, caused by a certificate issue. Aborting session, as by configuration Openfire is prohibited to set up a connection with a peer that provides an invalid certificate.", domainPair.getRemote(), event.cause());
                    stanzaHandler.setSession(null);
                    stanzaHandler.setAttemptedAllAuthenticationMethods();
                    ctx.channel().close();
                    return;
                }

                if (ServerDialback.isEnabled() && connectionConfigDoesNotRequireTls()) {
                    Log.debug("By configuration, TLS is not required. As the Server Dialback authentication mechanism is available, it may be used for authentication over a plain connection.");

                    // The original connection is probably unusable, as the TLS handshake failed (which the peer will know about).
                    // Instead of attempting Server Dialback on this connection, create a new connection and try it on that.
                    final LocalOutgoingServerSession outgoingSession = new ServerDialback(domainPair).createOutgoingSession(port);
                    if (outgoingSession != null) {
                        Log.info("TLS negotiation with '{}' was unsuccessful, but Server Dialback authentication over a plain connection (as a fallback) succeeded. Session successfully established on an unencrypted connection.", domainPair.getRemote());
                        stanzaHandler.setSession(outgoingSession);
                        stanzaHandler.setSessionAuthenticated();
                        ctx.fireChannelActive();
                        return;
                    } else {
                        Log.warn("TLS negotiation with '{}' was unsuccessful, and Server Dialback over a plain connection (as a fallback) failed. Aborting session.", domainPair.getRemote());
                        stanzaHandler.setSession(null);
                        stanzaHandler.setAttemptedAllAuthenticationMethods();
                        ctx.channel().close();
                        return;
                    }
                }

                Log.warn("TLS negotiation with '{}' was unsuccessful. Unable to create a new session: exhausted all options", domainPair.getRemote());
                stanzaHandler.setSession(null);
                stanzaHandler.setAttemptedAllAuthenticationMethods();
                ctx.channel().close();
            }
        }
    }

    private static boolean isCertificateException(SslHandshakeCompletionEvent event) {
        return event.cause().getCause() instanceof CertificateException;
    }

    private void sendNewStreamHeader(NettyConnection connection) {
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("", "jabber:server"));
        if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
            stream.add(Namespace.get("db", "jabber:server:dialback"));
        }
        stream.addAttribute("from", domainPair.getLocal()); // OF-673
        stream.addAttribute("to", domainPair.getRemote());
        stream.addAttribute("version", "1.0");

        connection.deliverRawText(StringUtils.asUnclosedStream(document));
    }

    private boolean connectionConfigDoesNotRequireTls() {
        return this.configuration.getTlsPolicy() != Connection.TLSPolicy.required;
    }

    public DomainPair getDomainPair()
    {
        return domainPair;
    }

    public int getPort()
    {
        return port;
    }

    @Override
    public String toString()
    {
        return "NettyOutboundConnectionHandler{" +
            "domainPair=" + domainPair +
            ", port=" + port +
            ", sslInitDone=" + sslInitDone +
            ", configuration=" + configuration +
            '}';
    }
}
