/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import io.netty.util.Attribute;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.RespondingServerStanzaHandler;
import org.jivesoftware.openfire.net.StanzaHandler;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the way {@link NettyOutboundConnectionHandler} handles a TLS handshake that fails, on a connection that
 * is opened to resume the stream of a detached session and on any other connection.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class NettyOutboundConnectionHandlerTlsFailureTest
{
    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, "remote.example.org");

    private ChannelHandlerContext ctx;

    private Channel channel;

    private RespondingServerStanzaHandler stanzaHandler;

    private ConnectionConfiguration configuration;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();
        // Server Dialback over a plain connection is allowed as a fallback.
        JiveGlobals.setProperty(ConnectionSettings.Server.DIALBACK_ENABLED, "true");
        configuration = mock(ConnectionConfiguration.class);
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();

        stanzaHandler = mock(RespondingServerStanzaHandler.class);
        @SuppressWarnings("unchecked")
        final Attribute<StanzaHandler> handlerAttribute = mock(Attribute.class);
        doReturn(stanzaHandler).when(handlerAttribute).get();
        channel = mock(Channel.class);
        doReturn(handlerAttribute).when(channel).attr(NettyConnectionHandler.HANDLER);
        ctx = mock(ChannelHandlerContext.class);
        doReturn(channel).when(ctx).channel();
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that a failed TLS handshake on a connection that is opened to resume the stream of a detached session
     * closes that connection without falling back to Server Dialback over a separate plain connection, of which the
     * session would never ask to resume the stream, and of which the connection would stay open.
     */
    @Test
    public void tlsFailureOnConnectionForResumptionDoesNotFallBackToDialback() throws Exception
    {
        // Setup test fixture.
        final NettyOutboundConnectionHandler handler = new NettyOutboundConnectionHandler(configuration, DOMAIN_PAIR, 5269, mock(LocalOutgoingServerSession.class));

        try (final MockedConstruction<ServerDialback> dialback = mockConstruction(ServerDialback.class))
        {
            // Execute system under test.
            handler.userEventTriggered(ctx, new SslHandshakeCompletionEvent(new SSLHandshakeException("test")));

            // Verify result.
            assertEquals(0, dialback.constructed().size(), "No Server Dialback over a separate plain connection must be attempted for a connection that is opened to resume a stream.");
        }
        verify(stanzaHandler, times(1).description("The stanza handler must no longer have a session for the connection that failed.")).setSession(null);
        verify(stanzaHandler, times(1).description("The stanza handler must be told that no authentication method is left.")).setAttemptedAllAuthenticationMethods();
        verify(channel, times(1).description("The connection must be closed.")).close();
    }

    /**
     * Verifies that a failed TLS handshake on a connection that is not opened to resume a stream still falls back to
     * Server Dialback over a separate plain connection, when the configuration allows that.
     */
    @Test
    public void tlsFailureOnOtherConnectionFallsBackToDialback() throws Exception
    {
        // Setup test fixture.
        final NettyOutboundConnectionHandler handler = new NettyOutboundConnectionHandler(configuration, DOMAIN_PAIR, 5269, null);

        try (final MockedConstruction<ServerDialback> dialback = mockConstruction(ServerDialback.class))
        {
            // Execute system under test.
            handler.userEventTriggered(ctx, new SslHandshakeCompletionEvent(new SSLHandshakeException("test")));

            // Verify result.
            assertEquals(1, dialback.constructed().size(), "Server Dialback over a separate plain connection must be attempted.");
            verify(dialback.constructed().get(0), times(1).description("A session must be created over a separate plain connection.")).createOutgoingSession(5269);
        }
    }
}
