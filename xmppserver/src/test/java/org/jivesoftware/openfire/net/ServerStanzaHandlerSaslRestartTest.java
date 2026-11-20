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
package org.jivesoftware.openfire.net;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that the stream that a remote server restarts on an incoming server connection after SASL negotiation gets
 * a new stream ID (RFC 6120 § 6.4.6), with a session that takes over what was negotiated on the previous stream
 * (OF-3447).
 */
public class ServerStanzaHandlerSaslRestartTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final StreamID ORIGINAL_STREAM_ID = new BasicStreamIDFactory().createStreamID("original-stream-id");

    private static final StreamID RESTARTED_STREAM_ID = new BasicStreamIDFactory().createStreamID("restarted-stream-id");

    private Connection connection;

    private SessionManager sessionManager;

    private LocalIncomingServerSession original;

    private ServerStanzaHandler handler;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setUp() throws Exception
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        sessionManager = xmppServer.getSessionManager();
        XMPPServer.setInstance(xmppServer);

        connection = mock(Connection.class, withSettings().lenient());
        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.CompressionPolicy.disabled).when(configuration).getCompressionPolicy();
        doReturn(configuration).when(connection).getConfiguration();

        doReturn(RESTARTED_STREAM_ID).when(sessionManager).nextStreamID();
        doAnswer(invocation -> new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)))
            .when(sessionManager).createIncomingServerSession(any(Connection.class), any(StreamID.class), anyString());

        original = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, ORIGINAL_STREAM_ID, REMOTE_DOMAIN);
        original.setLocalDomain(Fixtures.XMPP_DOMAIN);
        original.setClaimedIdentity(new JID(REMOTE_DOMAIN));
        original.addValidatedDomain(REMOTE_DOMAIN);
        original.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        handler = new ServerStanzaHandler(mock(PacketRouter.class), connection, false) {
            @Override
            void createSession(final String serverName, final XmlPullParser xpp, final Connection connection)
            {
                session = original;
            }
        };
        handler.initiateSession("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "' version='1.0'>", newPacketReader());
        clearInvocations(connection, sessionManager);
    }

    @AfterEach
    public void tearDown()
    {
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that the stream header that answers the stream that the remote server restarted after SASL negotiation
     * carries a new stream ID, and that the session for that stream uses it.
     */
    @Test
    public void restartedStreamAfterSaslGetsNewStreamId() throws Exception
    {
        // Setup test fixture.

        // Execute system under test.
        handler.saslSuccessful();

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, times(1).description("One stream header must be sent for the restarted stream.")).deliverRawText(captor.capture());
        final Element header = DocumentHelper.parseText(captor.getValue() + "</stream:stream>").getRootElement();
        assertEquals(RESTARTED_STREAM_ID.getID(), header.attributeValue("id"), "The stream header of the restarted stream must carry a new stream ID: " + captor.getValue());
        assertEquals(RESTARTED_STREAM_ID, handler.session.getStreamID(), "The session of the restarted stream must use the new stream ID.");
    }

    /**
     * Verifies that the session for the stream that the remote server restarted after SASL negotiation takes over what
     * was negotiated on the previous stream, and is the session of the connection.
     */
    @Test
    public void sessionOfRestartedStreamTakesOverNegotiatedState()
    {
        // Setup test fixture.

        // Execute system under test.
        handler.saslSuccessful();

        // Verify result.
        assertInstanceOf(LocalIncomingServerSession.class, handler.session, "The session of the restarted stream must be an incoming server session.");
        final LocalIncomingServerSession restarted = (LocalIncomingServerSession) handler.session;
        assertNotSame(original, restarted, "The restarted stream must have a session of its own.");
        assertEquals(Set.of(REMOTE_DOMAIN), Set.copyOf(restarted.getValidatedDomains()), "The session of the restarted stream must keep the validated domains.");
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, restarted.getAuthenticationMethod(), "The session of the restarted stream must keep the authentication method.");
        assertTrue(restarted.isAuthenticated(), "The session of the restarted stream must be authenticated.");
        assertEquals(Fixtures.XMPP_DOMAIN, restarted.getLocalDomain(), "The session of the restarted stream must keep the local domain.");
        assertEquals(new JID(REMOTE_DOMAIN), restarted.getClaimedIdentity().orElse(null), "The session of the restarted stream must keep the identity that the remote server claimed.");
        assertEquals(original.getFirstAuthentication(), restarted.getFirstAuthentication(), "The session of the restarted stream must keep the record of the first authentication.");
        verify(connection, times(1).description("The connection must be handed to the session of the restarted stream.")).reinit(restarted);
    }

    /**
     * Verifies that the session of the previous stream is unregistered, and that the session of the restarted stream is
     * registered under the new stream ID for each validated domain.
     */
    @Test
    public void sessionOfRestartedStreamReplacesPreviousRegistration()
    {
        // Setup test fixture.

        // Execute system under test.
        handler.saslSuccessful();

        // Verify result.
        final LocalIncomingServerSession restarted = (LocalIncomingServerSession) handler.session;
        verify(sessionManager, times(1).description("The session of the previous stream must be unregistered.")).unregisterIncomingServerSession(ORIGINAL_STREAM_ID);
        verify(sessionManager, times(1).description("The session of the restarted stream must be registered for its validated domain.")).registerIncomingServerSession(REMOTE_DOMAIN, restarted);
        verify(sessionManager, never().description("The session of the previous stream must not be registered again.")).registerIncomingServerSession(anyString(), same(original));
    }

    /**
     * Verifies that the session of the restarted stream is registered before the session of the previous stream is
     * unregistered, so that the validated domain is registered at all times, for example for a domain being blocked.
     */
    @Test
    public void sessionOfRestartedStreamIsRegisteredBeforePreviousIsUnregistered()
    {
        // Setup test fixture.
        final List<String> calls = new ArrayList<>();
        doAnswer(invocation -> calls.add("register " + ((LocalIncomingServerSession) invocation.getArgument(1)).getStreamID()))
            .when(sessionManager).registerIncomingServerSession(anyString(), any(LocalIncomingServerSession.class));
        doAnswer(invocation -> calls.add("unregister " + invocation.getArgument(0)))
            .when(sessionManager).unregisterIncomingServerSession(any(StreamID.class));

        // Execute system under test.
        handler.saslSuccessful();

        // Verify result.
        assertEquals(List.of("register " + RESTARTED_STREAM_ID, "unregister " + ORIGINAL_STREAM_ID), calls, "The session of the restarted stream must be registered before the session of the previous stream is unregistered.");
    }

    /**
     * Verifies that the session of the previous stream is unregistered also when registering the session of the
     * restarted stream fails, as the close listener of the connection no longer refers to the previous session and
     * would not unregister it when the connection closes.
     */
    @Test
    public void sessionOfPreviousStreamIsUnregisteredWhenRegisteringRestartedStreamFails()
    {
        // Setup test fixture.
        final RuntimeException failure = new IllegalStateException("Cache unavailable");
        doThrow(failure).when(sessionManager).registerIncomingServerSession(anyString(), any(LocalIncomingServerSession.class));

        // Execute system under test.
        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> handler.saslSuccessful(), "The failure to register the session of the restarted stream must not be hidden.");

        // Verify result.
        assertSame(failure, thrown, "The failure to register the session of the restarted stream must be the one that is thrown.");
        verify(sessionManager, times(1).description("The session of the previous stream must be unregistered, even though registering the session of the restarted stream failed.")).unregisterIncomingServerSession(ORIGINAL_STREAM_ID);
    }

    /**
     * Verifies that, when no session can be created for the stream that the remote server restarted after SASL
     * negotiation, the connection is closed with a stream error instead of the restarted stream being answered, and
     * the session of the previous stream remains the session of the connection.
     */
    @Test
    public void restartedStreamAfterSaslIsClosedWhenNoSessionCanBeCreated() throws Exception
    {
        // Setup test fixture.
        doThrow(new UnauthorizedException("Server not initialized")).when(sessionManager).createIncomingServerSession(any(Connection.class), any(StreamID.class), anyString());

        // Execute system under test.
        handler.saslSuccessful();

        // Verify result.
        verify(connection, times(1).description("The connection must be closed with an internal-server-error stream error.")).close(argThat(error -> error != null && error.getCondition() == StreamError.Condition.internal_server_error));
        verify(connection, never().description("No stream header must be sent for the restarted stream.")).deliverRawText(anyString());
        assertSame(original, handler.session, "The session of the previous stream must remain the session of the connection, so that closing the connection unregisters it.");
        verify(sessionManager, never().description("The session of the previous stream must not be unregistered before the connection is closed.")).unregisterIncomingServerSession(any(StreamID.class));
    }

    /**
     * Returns a namespace-aware packet reader, configured as the production code configures its own.
     *
     * @return a packet reader.
     */
    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }
}
