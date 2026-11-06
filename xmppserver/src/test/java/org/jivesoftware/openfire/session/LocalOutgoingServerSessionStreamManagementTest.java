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
package org.jivesoftware.openfire.session;

import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.nio.NettySessionInitializer;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.xmpp.packet.JID;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)} does not make a new outgoing session
 * available as a route before negotiation of Stream Management (XEP-0198) on that session has finished: until the
 * remote server enabled or refused Stream Management, Openfire decided not to ask for it, or negotiation timed out.
 * Until the session is a route, stanzas for the remote domain are queued in the OutgoingSessionPromise, which sends
 * them in order once authenticateDomain returns.
 *
 * The connection is a mock, and the code that opens it is replaced by one that returns a session that is already
 * authenticated. The remote server is simulated by passing its answer to the stream manager of that session.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS) // Tests that expect no wait use a negotiation timeout of an hour: if they wait after all, they fail instead.
public class LocalOutgoingServerSessionStreamManagementTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);

    private RoutingTable routingTable;

    private CompletableFuture<Void> closeFuture;

    private LocalOutgoingServerSession session;

    /**
     * The status of the request to enable Stream Management at each moment that the session was added as a route.
     */
    private List<RequestingStreamManager.EnableRequestStatus> statusWhenRouted;

    /**
     * Whether negotiation of Stream Management had finished at each moment that the session was added as a route.
     */
    private List<Boolean> negotiationFinishedWhenRouted;

    /**
     * The instances that authenticateDomain created to open a connection.
     */
    private final List<NettySessionInitializer> initialisers = new ArrayList<>();

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
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        routingTable = Fixtures.mockRoutingTable();
        doReturn(routingTable).when(xmppServer).getRoutingTable();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final Connection connection = mock(Connection.class, withSettings().lenient());
        closeFuture = new CompletableFuture<>();
        doReturn(closeFuture).when(connection).getCloseFuture();

        session = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID("stream-1"));
        session.setAddress(new JID(null, REMOTE_DOMAIN, null));
        session.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);

        statusWhenRouted = new CopyOnWriteArrayList<>();
        negotiationFinishedWhenRouted = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            statusWhenRouted.add(session.getStreamManager().getEnableRequestStatus());
            negotiationFinishedWhenRouted.add(session.getStreamManager().getNegotiationOutcome().isDone());
            return null;
        }).when(routingTable).addServerRoute(any(DomainPair.class), any(LocalOutgoingServerSession.class));

        StreamManager.SERVER_ACTIVE.setValue(true);
        LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.setValue(Duration.ofHours(1));
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a session on which Openfire asked to enable Stream Management is made available as a route only
     * once the remote server answered.
     */
    @Test
    public void routeIsAddedOnlyAfterRemoteServerEnabledStreamManagement()
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        CompletableFuture.runAsync(
            () -> session.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3))),
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS));

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(RequestingStreamManager.EnableRequestStatus.ENABLED), statusWhenRouted, "The session must be added as a route once, and only after the remote server enabled Stream Management.");
        assertTrue(session.checkOutgoingDomainPair(DOMAIN_PAIR), "The domain pair must be authenticated on the session.");
    }

    /**
     * Verifies that a session on which the remote server refused Stream Management is made available as a route only
     * once the remote server answered.
     */
    @Test
    public void routeIsAddedOnlyAfterRemoteServerRefusedStreamManagement()
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        CompletableFuture.runAsync(
            () -> session.getStreamManager().process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3))),
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS));

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed, as a refusal of Stream Management does not prevent use of the session. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(RequestingStreamManager.EnableRequestStatus.REFUSED), statusWhenRouted, "The session must be added as a route once, and only after the remote server refused Stream Management.");
    }

    /**
     * Verifies that a session to a remote server that does not offer Stream Management is made available as a route
     * without waiting.
     */
    @Test
    public void routeIsAddedWithoutWaitingWhenNotOffered()
    {
        // Setup test fixture.
        session.getStreamManager().skipEnable();

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(RequestingStreamManager.EnableRequestStatus.SKIPPED), statusWhenRouted, "The session must be added as a route once.");
        verify(routingTable, description("The session must be added as a route for the domain pair.")).addServerRoute(DOMAIN_PAIR, session);
    }

    /**
     * Verifies that, when Stream Management is not enabled for servers, a session is made available as a route without
     * waiting for features that would tell whether the remote server offers Stream Management, which the remote server
     * sends after the stream is restarted after SASL EXTERNAL.
     */
    @Test
    public void routeIsAddedWithoutWaitingWhenDisabledForServers()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(false);
        assertFalse(session.getStreamManager().getNegotiationOutcome().isDone(), "Negotiation was expected not to have finished yet; without that this test proves nothing.");

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        verify(routingTable, description("The session must be added as a route for the domain pair.")).addServerRoute(DOMAIN_PAIR, session);
    }

    /**
     * Verifies that, when Stream Management is enabled for servers, a session is not made available as a route before
     * the features that tell whether the remote server offers Stream Management have been received.
     */
    @Test
    public void routeIsAddedOnlyAfterFeaturesWhenEnabledForServers()
    {
        // Setup test fixture.
        CompletableFuture.runAsync(
            () -> session.getStreamManager().skipEnable(),
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS));

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(true), negotiationFinishedWhenRouted, "The session must be added as a route once, and only after negotiation finished.");
    }

    /**
     * Verifies that a session on which the remote server does not answer a request to enable Stream Management is made
     * available as a route once negotiation timed out.
     */
    @Test
    public void routeIsAddedAfterNegotiationTimedOut()
    {
        // Setup test fixture.
        LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.setValue(Duration.ofMillis(200));
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication was expected to succeed after negotiation timed out. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(RequestingStreamManager.EnableRequestStatus.PENDING), statusWhenRouted, "The session must be added as a route once, while the request still awaits an answer.");
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must not be enabled without an answer of the remote server.");
    }

    /**
     * Verifies that a session whose connection closes before negotiation of Stream Management has finished is not made
     * available as a route, and that authentication is reported as failed.
     */
    @Test
    public void sessionIsNotUsedWhenConnectionClosesDuringNegotiation()
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        CompletableFuture.runAsync(() -> closeFuture.complete(null), CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS));

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain();

        // Verify result.
        assertFalse(result.isSuccess(), "Authentication must be reported as failed when the connection closed before negotiation finished.");
        assertTrue(statusWhenRouted.isEmpty(), "The session must not be added as a route.");
        assertEquals(1, initialisers.size(), "Exactly one connection was expected to be opened.");
        verify(initialisers.get(0), description("The connection that was opened must be stopped.")).stop();
    }

    /**
     * Invokes {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)} for {@link #DOMAIN_PAIR}, where opening
     * a new connection gives {@link #session}.
     */
    private DomainAuthResult authenticateDomain()
    {
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedConstruction<NettySessionInitializer> constructed = mockConstruction(NettySessionInitializer.class,
                 (initialiser, context) -> doReturn(CompletableFuture.completedFuture(session)).when(initialiser).init(any())))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            remoteServers.when(() -> RemoteServerManager.getPortForServer(anyString())).thenReturn(5269);

            final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(DOMAIN_PAIR);
            initialisers.addAll(constructed.constructed());
            return result;
        }
    }
}
