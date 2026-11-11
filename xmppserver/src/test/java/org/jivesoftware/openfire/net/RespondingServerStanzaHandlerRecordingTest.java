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
import org.jivesoftware.openfire.nio.NettyConnection;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Authentication;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.StreamError;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies what {@link RespondingServerStanzaHandler} records about authentication for the S2S connection test, when
 * the remote server restarts the stream after SASL EXTERNAL succeeded, or fails to. Authentication succeeds only once
 * the remote server has restarted the stream (OF-3447), so certificate login must not be recorded before that.
 *
 * @author Matthew Vivian
 */
public class RespondingServerStanzaHandlerRecordingTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final String FEATURES_WITH_SASL_EXTERNAL = "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>EXTERNAL</mechanism></mechanisms></stream:features>";

    private static final String SASL_SUCCESS = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";

    private static final String FEATURES_WITH_STARTTLS = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/></stream:features>";

    private static final String TLS_PROCEED = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";

    private NettyConnection connection;

    private AttemptRecorder recorder;

    private RespondingServerStanzaHandler handler;

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

        recorder = new AttemptRecorder(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);
        connection = connectionStub(recorder);
        reader = newPacketReader();
        handler = new RespondingServerStanzaHandler(mock(PacketRouter.class), connection, new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN));
    }

    @AfterEach
    public void tearDown()
    {
        JiveGlobals.deleteProperty(ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.getKey());
        XMPPServer.setInstance(null);
    }

    @Test
    public void testSaslSuccessIsNotYetRecordedAsCertificateLogin() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);

        // Execute system under test.
        handler.process(SASL_SUCCESS, reader);

        // Verify results.
        assertTrue(recorder.snapshot().getAuthentication().isEmpty(), "authentication must not be recorded as succeeded before the remote server has restarted the stream");
    }

    @Test
    public void testRestartedStreamAfterSaslSuccessIsRecordedAsCertificateLogin() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader("stream-2"), reader);

        // Verify results.
        assertEquals(Authentication.saslExternal(), recorder.snapshot().getAuthentication().orElse(null), "certificate login must be recorded once the remote server has restarted the stream");
    }

    @Test
    public void testRestartedStreamReusingIdAfterSaslIsRecordedAsCertificateLoginByDefault() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify results.
        assertEquals(Authentication.saslExternal(), recorder.snapshot().getAuthentication().orElse(null), "a reused stream ID is accepted by default, so certificate login must be recorded");
    }

    @Test
    public void testRefusedReusedStreamIdAfterSaslIsRecorded() throws Exception
    {
        // Setup test fixture.
        ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.setValue(false);
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify results.
        assertEquals(Authentication.reusedStreamId(), recorder.snapshot().getAuthentication().orElse(null), "a restarted stream refused for reusing the stream ID must be recorded as such, not as certificate login");
    }

    @Test
    public void testRefusedReusedStreamIdAfterTlsIsRecorded() throws Exception
    {
        // Setup test fixture.
        ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.setValue(false);
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_STARTTLS, reader);
        handler.process(TLS_PROCEED, reader);

        // Execute system under test.
        handler.process(streamHeader("stream-1"), reader);

        // Verify results.
        assertEquals(Authentication.reusedStreamId(), recorder.snapshot().getAuthentication().orElse(null), "a stream restarted after TLS and refused for reusing the stream ID must be recorded as such");
    }

    @Test
    public void testRestartedStreamWithoutIdIsRecorded() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        handler.process(streamHeader(null), reader);

        // Verify results.
        assertEquals(Authentication.restartedStreamMissingId(), recorder.snapshot().getAuthentication().orElse(null), "a restarted stream refused for lacking a stream ID must be recorded as such");
        assertEquals(AttemptMilestones.Stream.opened(REMOTE_DOMAIN), recorder.snapshot().getStream().orElse(null), "the first stream opened, so the stream check must not fail");
    }

    @Test
    public void testConnectionClosedAfterSaslSuccessIsRecorded() throws Exception
    {
        // Setup test fixture.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);

        // Execute system under test.
        connection.close();

        // Verify results.
        assertEquals(Authentication.closedAfterExternalAccepted(), recorder.snapshot().getAuthentication().orElse(null), "a connection that closed after the remote accepted the certificate, before it restarted the stream, must be recorded as such");
    }

    @Test
    public void testSessionThatLostAuthenticationBeforeTheRestartIsRecorded() throws Exception
    {
        // Setup test fixture: the session is closed by another thread before the remote restarts the stream.
        handler.process(streamHeader("stream-1"), reader);
        handler.process(FEATURES_WITH_SASL_EXTERNAL, reader);
        handler.process(SASL_SUCCESS, reader);
        handler.getSession().setStatus(Session.Status.CLOSED);

        // Execute system under test.
        handler.process(streamHeader("stream-2"), reader);

        // Verify results.
        assertEquals(Authentication.closedAfterExternalAccepted(), recorder.snapshot().getAuthentication().orElse(null), "authentication lost before the restart must not be recorded as certificate login");
    }

    private static String streamHeader(final String streamId)
    {
        return "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' xmlns:db='jabber:server:dialback'"
            + " from='" + REMOTE_DOMAIN + "' to='" + Fixtures.XMPP_DOMAIN + "'"
            + (streamId == null ? "" : " id='" + streamId + "'")
            + " version='1.0'>";
    }

    /**
     * Returns a connection of an outbound attempt, that keeps the namespaces that are set on it and completes its close
     * future when it is closed, as a real connection does.
     *
     * @param recorder the recorder of the attempt that the connection belongs to.
     * @return a connection.
     */
    private static NettyConnection connectionStub(final AttemptRecorder recorder)
    {
        final ConnectionConfiguration configuration = mock(ConnectionConfiguration.class, withSettings().lenient());
        doReturn(Connection.TLSPolicy.optional).when(configuration).getTlsPolicy();

        final NettyConnection result = mock(NettyConnection.class, withSettings().lenient());
        doReturn(configuration).when(result).getConfiguration();
        doReturn(Optional.of(recorder)).when(result).getAttemptRecorder();

        final AtomicReference<Set<Namespace>> namespaces = new AtomicReference<>(Set.of());
        doAnswer(invocation -> { namespaces.set(new HashSet<>(invocation.getArgument(0))); return null; }).when(result).setAdditionalNamespaces(any());
        doAnswer(invocation -> namespaces.get()).when(result).getAdditionalNamespaces();

        final CompletableFuture<Void> closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(result).getCloseFuture();
        doAnswer(invocation -> closeFuture.complete(null)).when(result).close(nullable(StreamError.class));
        doAnswer(invocation -> closeFuture.complete(null)).when(result).close();
        return result;
    }

    private static XMPPPacketReader newPacketReader() throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XMPPPacketReader reader = new XMPPPacketReader();
        reader.setXPPFactory(factory);
        return reader;
    }
}
