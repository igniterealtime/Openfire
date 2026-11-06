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

import org.dom4j.Namespace;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies when {@link RespondingServerStanzaHandler} reports that the outgoing server session it negotiates is
 * authenticated, and which session it reports, by feeding it the data that a remote server sends.
 *
 * The futures are observed as {@code NettySessionInitializer#waitForSession} observes them: the session is obtained
 * from the handler at the moment that authentication is reported.
 */
public class RespondingServerStanzaHandlerTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String FEATURES_WITH_SASL_EXTERNAL = "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>EXTERNAL</mechanism></mechanisms></stream:features>";

    private static final String SASL_SUCCESS = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";

    private static final String FEATURES_WITH_STARTTLS = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>";

    private static final String TLS_PROCEED = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";

    private Connection connection;

    private CompletableFuture<Void> closeFuture;

    private AtomicReference<LocalSession> sessionBoundOnClose;

    private RespondingServerStanzaHandler handler;

    private CompletableFuture<LocalSession> authenticatedSession;

    private XMPPPacketReader reader;

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
        XMPPServer.setInstance(Fixtures.mockXMPPServer());

        connection = connectionStub();
        reader = newPacketReader();

        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
        authenticatedSession = handler.isSessionAuthenticated().thenApply(ignored -> handler.getSession());
    }

    @AfterEach
    public void tearDown()
    {
        JiveGlobals.deleteProperty(ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.getKey());
        XMPPServer.setInstance(null);
    }

    /**
     * Asserts that authentication is not reported when the remote server reports that SASL EXTERNAL succeeded, as the
     * stream (and with it, the session) is still to be restarted.
     */
    @Test
    public void testSaslSuccessDoesNotCompleteAuthentication() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);

        // Execute system under test.
        handler.process(SASL_SUCCESS, reader);

        // Verify result.
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported before the remote server has restarted the stream after SASL success.");
    }

    /**
     * Asserts that authentication is reported once the remote server restarts the stream after SASL success, and that
     * the session that is then obtained is the session of the restarted stream.
     */
    @Test
    public void testRestartedStreamAfterSaslSuccessCompletesAuthenticationWithNewSession() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        final LocalSession previousSession = handler.getSession();

        // Execute system under test.
        handler.process(streamHeader("stream-2"), reader);

        // Verify result.
        assertTrue(authenticatedSession.isDone(), "Authentication must be reported once the remote server has restarted the stream after SASL success.");
        final LocalSession result = authenticatedSession.join();
        assertNotSame(previousSession, result, "The session that is reported must not be the session of the stream from before the restart.");
        assertEquals("stream-2", result.getStreamID().getID(), "The session that is reported must be the session of the restarted stream.");
        assertInstanceOf(LocalOutgoingServerSession.class, result, "The session that is reported must be an outgoing server session.");
        assertTrue(result.isAuthenticated(), "The session of the restarted stream must be authenticated.");
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, ((LocalOutgoingServerSession) result).getAuthenticationMethod(), "The session of the restarted stream must be authenticated with SASL EXTERNAL.");
        assertEquals(new JID(null, REMOTE_DOMAIN, null), result.getAddress(), "The session of the restarted stream must have the remote domain as its address.");
        assertTrue(((LocalOutgoingServerSession) result).getOutgoingDomainPairs().isEmpty(), "The session of the restarted stream must not be registered for a domain pair before authentication is reported.");
    }

    /**
     * Asserts that a repeated SASL success, received after authentication was reported, is ignored: it does not cause
     * the stream to be restarted again, and does not add another callback for the closing of the connection.
     */
    @Test
    public void testRepeatedSaslSuccessIsIgnored() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        handler.process(streamHeader("stream-2"), reader);
        final LocalSession reportedSession = authenticatedSession.join();
        final int closeCallbacks = closeFuture.getNumberOfDependents();
        clearInvocations(connection);

        // Execute system under test.
        handler.process(SASL_SUCCESS, reader);

        // Verify result.
        verify(connection, never().description("A repeated SASL success must not cause a new stream header to be sent.")).deliverRawText(any());
        assertEquals(closeCallbacks, closeFuture.getNumberOfDependents(), "A repeated SASL success must not add another callback for the closing of the connection.");
        assertSame(reportedSession, handler.getSession(), "A repeated SASL success must not change the session.");
        assertTrue(reportedSession.isAuthenticated(), "The session must remain authenticated after a repeated SASL success.");
    }

    /**
     * Asserts that a repeated SASL success, received before the remote server restarted the stream, is ignored: it
     * does not cause another stream header to be sent, does not add another callback for the closing of the
     * connection, and does not prevent authentication from being reported for the session of the restarted stream.
     */
    @Test
    public void testRepeatedSaslSuccessBeforeStreamRestartIsIgnored() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        final int closeCallbacks = closeFuture.getNumberOfDependents();
        clearInvocations(connection);

        // Execute system under test.
        handler.process(SASL_SUCCESS, reader);
        handler.process(streamHeader("stream-2"), reader);

        // Verify result.
        verify(connection, never().description("A repeated SASL success must not cause another stream header to be sent.")).deliverRawText(argThat(text -> text.contains("<stream:stream")));
        assertEquals(closeCallbacks, closeFuture.getNumberOfDependents(), "A repeated SASL success must not add another callback for the closing of the connection.");
        assertTrue(authenticatedSession.isDone(), "Authentication must be reported once the remote server has restarted the stream, also after a repeated SASL success.");
        assertEquals("stream-2", authenticatedSession.join().getStreamID().getID(), "The session that is reported must be the session of the restarted stream.");
    }

    /**
     * Asserts that, when the remote server restarts the stream after TLS and again after SASL success, the session of
     * the stream that follows TLS is not reported as authenticated, and that authentication is reported for the session
     * of the stream that follows SASL success.
     */
    @Test
    public void testRestartedStreamsAfterTlsAndSaslCompleteAuthenticationWithSessionOfLastStream() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_STARTTLS, reader);
        handler.process(TLS_PROCEED, reader);
        handler.process(streamHeader("stream-2"), reader);
        final String streamIdAfterTls = handler.getSession().getStreamID().getID();
        final boolean authenticatedAfterTls = handler.getSession().isAuthenticated();
        final boolean authenticationReportedAfterTls = authenticatedSession.isDone();
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader("stream-3"), reader);

        // Verify result.
        assertEquals("stream-2", streamIdAfterTls, "A new session must be created for the stream that is restarted after TLS.");
        assertFalse(authenticatedAfterTls, "The session of the stream that is restarted after TLS must not be authenticated before SASL succeeded.");
        assertFalse(authenticationReportedAfterTls, "Authentication must not be reported when the stream is restarted after TLS.");
        assertTrue(authenticatedSession.isDone(), "Authentication must be reported once the remote server has restarted the stream after SASL success.");
        final LocalSession result = authenticatedSession.join();
        assertEquals("stream-3", result.getStreamID().getID(), "The session that is reported must be the session of the stream that is restarted after SASL success.");
        assertTrue(result.isAuthenticated(), "The session that is reported must be authenticated.");
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, ((LocalOutgoingServerSession) result).getAuthenticationMethod(), "The session that is reported must be authenticated with SASL EXTERNAL.");
    }

    /**
     * Asserts that closing the connection after authentication was reported does not withdraw the session that was
     * reported, nor report a failure to initiate a session.
     */
    @Test
    public void testConnectionClosedAfterRestartedStreamKeepsReportedSession() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        handler.process(streamHeader("stream-2"), reader);
        final LocalSession reportedSession = authenticatedSession.join();

        // Execute system under test.
        connection.close();

        // Verify result.
        assertSame(reportedSession, handler.getSession(), "The session that was reported must remain the session of the handler after the connection closes.");
        assertFalse(handler.haveAttemptedAllAuthenticationMethods().isDone(), "A failure to initiate a session must not be reported after authentication was reported.");
    }

    /**
     * Asserts that a valid dialback result reports authentication for the session of the stream on which it was
     * received, as dialback does not restart the stream.
     */
    @Test
    public void testValidDialbackResultCompletesAuthentication() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        final LocalSession streamSession = handler.getSession();

        // Execute system under test.
        handler.process("<db:result from='" + Fixtures.XMPP_DOMAIN + "' to='" + REMOTE_DOMAIN + "' type='valid'/>", reader);

        // Verify result.
        assertTrue(authenticatedSession.isDone(), "Authentication must be reported when a valid dialback result is received.");
        final LocalSession result = authenticatedSession.join();
        assertSame(streamSession, result, "The session that is reported must be the session of the stream on which dialback took place.");
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, ((LocalOutgoingServerSession) result).getAuthenticationMethod(), "The session must be authenticated with dialback.");
    }

    /**
     * Asserts that, when the connection closes after SASL success but before the remote server restarted the stream,
     * the failure to initiate a session is reported, instead of leaving the caller to wait for a timeout.
     */
    @Test
    public void testConnectionClosedAfterSaslSuccessReportsFailure() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        connection.close();

        // Verify result.
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a connection that closed before the stream was restarted.");
        assertTrue(handler.haveAttemptedAllAuthenticationMethods().isDone(), "The failure to initiate a session must be reported when the connection closes before the stream was restarted.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that a stream header that the remote server sends after the connection closed (after SASL success, but
     * before the stream was restarted) is ignored, rather than processed for a session that no longer exists.
     */
    @Test
    public void testRestartedStreamAfterConnectionClosedIsIgnored() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        connection.close();

        // Execute system under test.
        assertDoesNotThrow(() -> handler.process(streamHeader("stream-2"), reader), "A stream header that arrives after session initiation was abandoned must be ignored.");

        // Verify result.
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a stream that is restarted after the connection closed.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that stream features that the remote server sends after the connection closed (after SASL success, but
     * before the stream was restarted) are ignored, rather than processed for a session that no longer exists.
     */
    @Test
    public void testFeaturesAfterConnectionClosedAreIgnored() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        connection.close();
        handler.process(streamHeader("stream-2"), reader);
        clearInvocations(connection);

        // Execute system under test.
        assertDoesNotThrow(() -> handler.process(FEATURES_WITH_SASL_EXTERNAL, reader), "Stream features that arrive after session initiation was abandoned must be ignored.");

        // Verify result.
        verify(connection, never().description("Nothing must be sent in response to stream features that arrive after session initiation was abandoned.")).deliverRawText(any());
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a connection that closed before the stream was restarted.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that, when the session lost its authentication before the remote server restarted the stream after SASL
     * success (as happens when the connection is closed by another thread), authentication is not reported, and the
     * failure to initiate a session is reported instead.
     */
    @Test
    public void testRestartedStreamAfterSessionLostAuthenticationReportsFailure() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        handler.getSession().setStatus(Session.Status.CLOSED);

        // Execute system under test.
        handler.process(streamHeader("stream-2"), reader);

        // Verify result.
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a session that is not authenticated.");
        assertTrue(handler.haveAttemptedAllAuthenticationMethods().isDone(), "The failure to initiate a session must be reported when the session of the restarted stream is not authenticated.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that, when the stream header that restarts the stream after SASL success is refused for lacking a stream
     * ID (OF-2692), the failure to initiate a session is reported, instead of leaving the caller to wait for a timeout.
     */
    @Test
    public void testRestartedStreamWithoutIdAfterSaslSuccessReportsFailure() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader(null), reader);

        // Verify result.
        verify(connection, description("The connection must be closed with an 'invalid-xml' stream error.")).close(argThat((StreamError error) -> error != null && error.getCondition() == StreamError.Condition.invalid_xml));
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a restarted stream that was refused.");
        assertTrue(handler.haveAttemptedAllAuthenticationMethods().isDone(), "The failure to initiate a session must be reported when the restarted stream is refused.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that, by default, a stream that the remote server restarts after TLS negotiation is refused when it
     * reuses the stream ID of the previous stream, and that the failure to initiate a session is reported.
     */
    @Test
    public void testRestartedStreamReusingIdAfterTlsIsRefused() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_STARTTLS, reader);
        handler.process(TLS_PROCEED, reader);
        final LocalSession previousSession = handler.getSession();

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify result.
        verify(connection, description("The connection must be closed with an 'invalid-xml' stream error.")).close(argThat((StreamError error) -> error != null && error.getCondition() == StreamError.Condition.invalid_xml));
        assertSame(previousSession, sessionBoundOnClose.get(), "The connection must be bound to the session of the previous stream when it is closed, so that the stream error is sent.");
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a restarted stream that was refused.");
        assertTrue(handler.haveAttemptedAllAuthenticationMethods().isDone(), "The failure to initiate a session must be reported when the restarted stream is refused.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that, by default, a stream that the remote server restarts after SASL success is refused when it reuses
     * the stream ID of the previous stream, and that the failure to initiate a session is reported.
     */
    @Test
    public void testRestartedStreamReusingIdAfterSaslIsRefused() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        final LocalSession previousSession = handler.getSession();

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify result.
        verify(connection, description("The connection must be closed with an 'invalid-xml' stream error.")).close(argThat((StreamError error) -> error != null && error.getCondition() == StreamError.Condition.invalid_xml));
        assertSame(previousSession, sessionBoundOnClose.get(), "The connection must be bound to the session of the previous stream when it is closed, so that the stream error is sent.");
        assertFalse(authenticatedSession.isDone(), "Authentication must not be reported for a restarted stream that was refused.");
        assertTrue(handler.haveAttemptedAllAuthenticationMethods().isDone(), "The failure to initiate a session must be reported when the restarted stream is refused.");
        assertNull(handler.getSession(), "No session must be available when session initiation failed.");
    }

    /**
     * Asserts that, when configured to accept it, a stream that the remote server restarts after SASL success while
     * reusing the stream ID of the previous stream is accepted, and that authentication is reported for the session of
     * the previous stream, which continues.
     */
    @Test
    public void testRestartedStreamReusingIdAfterSaslIsAcceptedWhenConfigured() throws Exception
    {
        // Setup test fixture.
        ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.setValue(true);
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        final LocalSession previousSession = handler.getSession();

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify result.
        verify(connection, never().description("The connection must not be closed when a reused stream ID is accepted by configuration.")).close(nullable(StreamError.class));
        assertTrue(authenticatedSession.isDone(), "Authentication must be reported once the remote server has restarted the stream after SASL success.");
        final LocalSession result = authenticatedSession.join();
        assertSame(previousSession, result, "As the stream ID was reused, the session of the previous stream must continue and be reported.");
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, ((LocalOutgoingServerSession) result).getAuthenticationMethod(), "The session must be authenticated with SASL EXTERNAL.");
    }

    /**
     * Returns a stream header as sent by the remote server.
     *
     * @param streamId the stream ID, or null to send a header without one.
     * @return a stream header.
     */
    private static String streamHeader(final String streamId)
    {
        return "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback'"
            + " from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "'"
            + (streamId == null ? "" : " id='" + streamId + "'")
            + " version='1.0'>";
    }

    /**
     * Returns a connection that keeps the namespaces that are set on it, that keeps the session that it is bound to,
     * and that completes its close future when it is closed, as a real connection does. The session that the
     * connection is bound to when it is closed with a stream error is recorded in {@link #sessionBoundOnClose}, as a
     * real connection sends the stream error only when it is bound to a session.
     *
     * @return a connection.
     */
    private Connection connectionStub()
    {
        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();

        final Connection result = mock(Connection.class, withSettings().lenient());
        doReturn(configuration).when(result).getConfiguration();

        final AtomicReference<Set<Namespace>> namespaces = new AtomicReference<>(Set.of());
        doAnswer(invocation -> { namespaces.set(new HashSet<>(invocation.getArgument(0))); return null; }).when(result).setAdditionalNamespaces(any());
        doAnswer(invocation -> namespaces.get()).when(result).getAdditionalNamespaces();

        final AtomicReference<LocalSession> boundSession = new AtomicReference<>();
        doAnswer(invocation -> { boundSession.set(invocation.getArgument(0)); return null; }).when(result).init(any());
        doAnswer(invocation -> { boundSession.set(invocation.getArgument(0)); return null; }).when(result).reinit(any());

        sessionBoundOnClose = new AtomicReference<>();
        closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(result).getCloseFuture();
        doAnswer(invocation -> { sessionBoundOnClose.set(boundSession.get()); return closeFuture.complete(null); }).when(result).close(nullable(StreamError.class));
        doAnswer(invocation -> closeFuture.complete(null)).when(result).close();
        return result;
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
