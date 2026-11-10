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
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.nio.NettySessionInitializer;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.openfire.streammanagement.UnacknowledgedStanzas;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

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
 * Verifies Stream Management (XEP-0198) on outgoing server sessions.
 *
 * {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)} does not make a new outgoing session available as a
 * route before negotiation of Stream Management on that session has finished: until the remote server enabled or
 * refused Stream Management, Openfire decided not to ask for it, or negotiation timed out. Until the session is a route,
 * stanzas for the remote domain are queued in the OutgoingSessionPromise, which sends them in order once
 * authenticateDomain returns.
 *
 * Once the remote server enabled Stream Management, the stanzas that the session delivers are tracked until the remote
 * server acknowledges them, and acknowledgements are requested.
 *
 * The connection is a mock, and the code that opens it is replaced by one that returns a session that is already
 * authenticated. The remote server is simulated by passing its answers and acknowledgements to the stream manager of
 * that session, as the stanza handler of the connection does.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS) // Tests that expect no wait use a negotiation timeout of an hour: if they wait after all, they fail instead.
public class LocalOutgoingServerSessionStreamManagementTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";

    private static final DomainPair DOMAIN_PAIR = new DomainPair(Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);

    private XMPPServer xmppServer;

    private RoutingTable routingTable;

    private Connection connection;

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

        xmppServer = Fixtures.mockXMPPServer();
        routingTable = Fixtures.mockRoutingTable();
        doReturn(routingTable).when(xmppServer).getRoutingTable();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        connection = mock(Connection.class, withSettings().lenient());
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
     * Verifies that stanzas that the session delivers once the remote server enabled Stream Management are tracked until
     * they are acknowledged.
     */
    @Test
    public void stanzasDeliveredWhileEnabledAreTracked() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();

        // Execute system under test.
        session.deliver(message("1"));
        session.deliver(message("2"));

        // Verify result.
        verify(connection, times(2).description("Each stanza must be delivered on the connection.")).deliver(any(Packet.class));
        assertEquals(List.of("1", "2"), UnacknowledgedStanzas.ids(session.getStreamManager()), "Each stanza that was delivered must be tracked until it is acknowledged.");
    }

    /**
     * Verifies that an acknowledgement of the remote server removes the stanzas that it acknowledges, and no others,
     * without closing the connection.
     */
    @Test
    public void acknowledgementRemovesAcknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(message("1"));
        session.deliver(message("2"));
        session.deliver(message("3"));

        // Execute system under test.
        session.getStreamManager().process(ack("2"));

        // Verify result.
        assertEquals(List.of("3"), UnacknowledgedStanzas.ids(session.getStreamManager()), "Only the stanza that was not acknowledged must remain.");
        verify(connection, never().description("A valid acknowledgement must not close the connection.")).close();
    }

    /**
     * Verifies that an acknowledgement of more stanzas than were sent closes the connection with a stream error that
     * reports the value of 'h' and the number of stanzas that were sent (XEP-0198 § 6), as for clients.
     */
    @Test
    public void acknowledgementOfMoreStanzasThanSentClosesConnection() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(message("1"));

        // Execute system under test.
        session.getStreamManager().process(ack("2"));

        // Verify result.
        verify(connection, times(1).description("An acknowledgement of more stanzas than were sent must close the connection.")).close();
        final Element streamError = lastRawTextDelivered();
        final Element handledCountTooHigh = streamError.element(QName.get("handled-count-too-high", StreamManager.NAMESPACE_V3));
        assertNotNull(handledCountTooHigh, "The stream error must include a 'handled-count-too-high' element, but was: " + streamError.asXML());
        assertEquals("2", handledCountTooHigh.attributeValue("h"), "The element must report the value of 'h' that was received.");
        assertEquals("1", handledCountTooHigh.attributeValue("send-count"), "The element must report the number of stanzas that were sent.");
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(session.getStreamManager()), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that an acknowledgement is requested from the remote server at the same frequency as from clients.
     */
    @Test
    public void acknowledgementIsRequestedAtDefaultFrequency() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        for (int i = 1; i <= 4; i++) {
            session.deliver(message(String.valueOf(i)));
        }
        verify(connection, never().description("No acknowledgement is to be requested below the default frequency of 5.")).deliverRawText(anyString());

        // Execute system under test.
        session.deliver(message("5"));

        // Verify result.
        assertEquals(QName.get("r", StreamManager.NAMESPACE_V3), lastRawTextDelivered().getQName(), "An acknowledgement is to be requested at the default frequency of 5, in the namespace of the stream.");
    }

    /**
     * Verifies that stanzas are not tracked, and no acknowledgement is requested, when the remote server does not offer
     * Stream Management.
     */
    @Test
    public void stanzasAreNotTrackedWhenNotOffered() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().skipEnable();

        // Execute system under test.
        for (int i = 1; i <= 5; i++) {
            session.deliver(message(String.valueOf(i)));
        }

        // Verify result.
        verify(connection, times(5).description("Each stanza must be delivered on the connection.")).deliver(any(Packet.class));
        verify(connection, never().description("No acknowledgement is to be requested when Stream Management is not enabled.")).deliverRawText(anyString());
        assertEquals(List.of(), UnacknowledgedStanzas.ids(session.getStreamManager()), "No stanzas must be tracked when Stream Management is not enabled.");
    }

    /**
     * Verifies that a stanza for a connection that is closed is routed again once the connection is torn down, as when
     * Stream Management is not enabled, and is not tracked, as it is not sent on this connection.
     */
    @Test
    public void stanzaForClosedConnectionIsRoutedAgainAndNotTracked() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        final PacketRouter packetRouter = mock(PacketRouter.class);
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        doReturn(true).when(connection).isClosed();
        closeFuture.complete(null);

        // Execute system under test.
        session.deliver(message("1"));

        // Verify result.
        verify(packetRouter, timeout(5000).description("A stanza for a closed connection must be routed again once the connection is torn down.")).route(any(Packet.class));
        verify(connection, never().description("A stanza must not be delivered on a closed connection.")).deliver(any(Packet.class));
        assertEquals(List.of(), UnacknowledgedStanzas.ids(session.getStreamManager()), "A stanza that is routed again must not be tracked on the closed connection.");
    }

    /**
     * Verifies that, when the session is used after negotiation timed out, and the remote server enables Stream
     * Management later, an acknowledgement that counts the stanzas sent in the meantime is valid. The remote server
     * counts stanzas from the request on (XEP-0198 § 4), while Openfire tracks them only once it is enabled.
     */
    @Test
    public void acknowledgementIsValidWhenEnabledAfterNegotiationTimedOut() throws Exception
    {
        // Setup test fixture.
        LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.setValue(Duration.ofMillis(200));
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        final DomainAuthResult result = authenticateDomain();
        assertTrue(result.isSuccess(), "Test fixture expects authentication to succeed after negotiation timed out. Diagnostic log: " + result.getDiagnosticLog());
        session.deliver(message("pending-1"));
        session.deliver(message("pending-2"));
        session.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        session.deliver(message("enabled-1"));
        session.deliver(message("enabled-2"));

        // Execute system under test.
        session.getStreamManager().process(ack("3"));

        // Verify result.
        verify(connection, never().description("An acknowledgement that counts the stanzas sent before Stream Management was enabled must not close the connection.")).close();
        assertEquals(List.of("enabled-2"), UnacknowledgedStanzas.ids(session.getStreamManager()), "Only the tracked stanza that the acknowledgement does not cover must remain.");
    }

    /**
     * Asks the remote server to enable Stream Management on {@link #session}, and passes its answer that enabled it,
     * after which the interaction with the connection is cleared.
     */
    private void enableStreamManagement()
    {
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        session.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");
        clearInvocations(connection);
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        message.setFrom(Fixtures.XMPP_DOMAIN);
        message.setTo(REMOTE_DOMAIN);
        return message;
    }

    private static Element ack(final String h)
    {
        return DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", h);
    }

    /**
     * Returns the last raw text that was delivered on the connection, parsed as XML.
     */
    private Element lastRawTextDelivered() throws Exception
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Expected raw text to be delivered on the connection.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
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
