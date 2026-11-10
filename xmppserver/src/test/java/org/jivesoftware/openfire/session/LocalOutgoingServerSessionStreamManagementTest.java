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
import org.jivesoftware.openfire.SessionManager;
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
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * server acknowledges them, and acknowledgements are requested. When the session closes without being resumed, the
 * stanzas that the remote server did not acknowledge are routed again, or returned to their sender as an error.
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

    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);

    private static final JID SENDER = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource");

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
     * Verifies that, when the session closes without being resumed, unacknowledged messages, presences and IQ responses
     * are routed again, in the order in which they were sent, so that they can go out over a new connection (decision 4
     * of OF-2425).
     */
    @Test
    public void unacknowledgedMessagesPresencesAndIqResponsesAreRoutedAgainOnClose() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(message("message"));
        session.deliver(presence("presence"));
        session.deliver(iq("result", IQ.Type.result));
        session.deliver(iq("error", IQ.Type.error));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        final List<Packet> routed = routed(packetRouter);
        assertEquals(List.of("message", "presence", "result", "error"), routed.stream().map(Packet::getID).toList(), "Each unacknowledged message, presence and IQ response must be routed again, in the order in which it was sent.");
        assertEquals(List.of(REMOTE_DOMAIN, REMOTE_DOMAIN, REMOTE_DOMAIN, REMOTE_DOMAIN), routed.stream().map(packet -> packet.getTo().toString()).toList(), "Each stanza that is routed again must still be addressed to its recipient.");
        assertEquals(IQ.Type.error, ((IQ) routed.get(3)).getType(), "An IQ error must be routed again as it was sent.");
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must be disabled when the session closes.");
    }

    /**
     * Verifies that messages and presences that are routed again when the session closes get a delay stamp from this
     * server, while IQ responses do not.
     */
    @Test
    public void messagesAndPresencesRoutedAgainOnCloseGetDelayStamp() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(message("message"));
        session.deliver(presence("presence"));
        session.deliver(iq("result", IQ.Type.result));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        final List<Packet> routed = routed(packetRouter);
        for (final Packet packet : routed.subList(0, 2)) {
            final Element delay = packet.getElement().element(QName.get("delay", "urn:xmpp:delay"));
            assertNotNull(delay, "A " + packet.getElement().getName() + " that is routed again must have a delay stamp.");
            assertEquals(Fixtures.XMPP_DOMAIN, delay.attributeValue("from"), "The delay stamp must name this server as the entity that delayed the stanza.");
            assertNotNull(delay.attributeValue("stamp"), "The delay stamp must record the time at which the stanza was first sent.");
        }
        assertNull(routed.get(2).getElement().element(QName.get("delay", "urn:xmpp:delay")), "An IQ response that is routed again must not get a delay stamp.");
    }

    /**
     * Verifies that, when the session closes without being resumed, the sender of an unacknowledged IQ request receives
     * a 'service-unavailable' error, and the request is not routed again, as it can have side effects (decision 4 of
     * OF-2425).
     */
    @Test
    public void unacknowledgedIqRequestsAreReturnedAsErrorOnClose() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(iq("get", IQ.Type.get));
        session.deliver(iq("set", IQ.Type.set));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        final List<Packet> routed = routed(packetRouter);
        assertEquals(List.of("get", "set"), routed.stream().map(Packet::getID).toList(), "Each unacknowledged IQ request must be answered, in the order in which it was sent.");
        for (final Packet packet : routed) {
            final IQ error = (IQ) packet;
            assertEquals(IQ.Type.error, error.getType(), "An unacknowledged IQ request must be answered with an error, not routed again.");
            assertEquals(SENDER, error.getTo(), "The error must be addressed to the sender of the request.");
            assertEquals(new JID(REMOTE_DOMAIN), error.getFrom(), "The error must come from the recipient of the request.");
            assertEquals(PacketError.Condition.service_unavailable, error.getError().getCondition(), "The error must report the 'service-unavailable' condition.");
            assertEquals(PacketError.Type.cancel, error.getError().getType(), "The error must have the type 'cancel', the type that RFC 6120 associates with 'service-unavailable'.");
        }
    }

    /**
     * Verifies that stanzas that the remote server acknowledged are not handled when the session closes.
     */
    @Test
    public void acknowledgedStanzasAreNotHandledOnClose() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(message("1"));
        session.deliver(iq("2", IQ.Type.get));
        session.deliver(message("3"));
        session.getStreamManager().process(ack("2"));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("3"), routed(packetRouter).stream().map(Packet::getID).toList(), "Only the stanza that was not acknowledged must be routed again.");
    }

    /**
     * Verifies that nothing is routed when a session on which Stream Management is not enabled closes, as its stanzas are
     * not tracked.
     */
    @Test
    public void nothingIsHandledOnCloseWhenNotEnabled() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().skipEnable();
        session.deliver(message("1"));
        session.deliver(iq("2", IQ.Type.get));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of(), routed(packetRouter), "Nothing must be routed when Stream Management is not enabled.");
    }

    /**
     * Verifies that a stanza that is delivered before negotiation of Stream Management finished is sent while holding
     * the lock of the stream manager, under which the request to enable Stream Management is sent, so that whether the
     * stanza is counted matches whether the remote server counts it, and that a stanza is sent without that lock once
     * Openfire decided not to ask for Stream Management.
     */
    @Test
    public void stanzaIsSentUnderLockUntilStreamManagementIsSkipped() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager streamManager = session.getStreamManager();
        final List<String> lockHeld = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { lockHeld.add(((Packet) invocation.getArgument(0)).getID() + " " + Thread.holdsLock(streamManager)); return null; }).when(connection).deliver(any(Packet.class));

        // Execute system under test.
        session.deliver(message("before-negotiation"));
        streamManager.skipEnable();
        session.deliver(message("after-skip"));

        // Verify result.
        assertEquals(List.of("before-negotiation true", "after-skip false"), lockHeld, "A stanza must be sent under the lock of the stream manager until Openfire decided not to ask for Stream Management, and without it after that.");
    }

    /**
     * Verifies that stanzas that were sent while the request to enable Stream Management awaited an answer are not
     * handled when the session closes, as they are counted but not tracked, while those sent after it are.
     */
    @Test
    public void stanzasSentWhileRequestWasPendingAreNotHandledOnClose() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        session.deliver(message("pending"));
        session.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        session.deliver(message("enabled"));
        final PacketRouter packetRouter = mock(PacketRouter.class);

        // Execute system under test.
        session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("enabled"), routed(packetRouter).stream().map(Packet::getID).toList(), "Only the stanza that was tracked must be routed again.");
    }

    /**
     * Verifies that the close listener for outgoing server sessions handles the unacknowledged stanzas of the session,
     * and does so only after the routes of the session were removed, so that the stanzas that are routed again do not
     * reach the session that closed.
     */
    @Test
    public void closeListenerHandlesUnacknowledgedStanzasAfterRoutesAreRemoved() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        final SessionManager sessionManager = new SessionManager();
        sessionManager.initialize(xmppServer);
        session.addOutgoingDomainPair(DOMAIN_PAIR);
        enableStreamManagement();
        session.deliver(message("1"));

        final AtomicBoolean routeRemoved = new AtomicBoolean(false);
        doAnswer(invocation -> { routeRemoved.set(true); return null; }).when(routingTable).removeServerRoute(DOMAIN_PAIR);
        final List<Boolean> routeRemovedWhenRouted = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { routeRemovedWhenRouted.add(routeRemoved.get()); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager);

        // Verify result.
        assertEquals(List.of(true), routeRemovedWhenRouted, "The unacknowledged stanza must be routed again once, after the route of the session was removed.");
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must be disabled when the session closes.");
    }

    /**
     * Verifies that a stanza that is unacknowledged when two connections in a row break is sent again once, on the
     * second connection, and is then returned to its sender as an error, or dropped when it is a presence, while a
     * stanza that was first sent on the second connection is routed again (decision 4 of OF-2425).
     */
    @Test
    public void stanzaUnacknowledgedAtTwoBreaksInARowIsReturnedAsErrorAfterOneRetry() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        final Message message = message("message");
        message.setFrom(SENDER);
        message.setType(Message.Type.chat);
        message.setThread("thread-1");
        session.deliver(message);
        session.deliver(presence("presence"));
        final PacketRouter firstRouter = mock(PacketRouter.class);
        session.getStreamManager().onClose(firstRouter, SERVER_ADDRESS);
        final List<Packet> routedAtFirstBreak = routed(firstRouter);

        final LocalOutgoingServerSession secondSession = newSessionWithStreamManagement("stream-2");
        deliverAsQueued(routedAtFirstBreak, secondSession);
        secondSession.deliver(message("fresh"));
        final PacketRouter secondRouter = mock(PacketRouter.class);

        // Execute system under test.
        secondSession.getStreamManager().onClose(secondRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("message", "presence"), routedAtFirstBreak.stream().map(Packet::getID).toList(), "At the first break, each unacknowledged stanza must be routed again.");
        assertEquals(List.of("message", "presence"), UnacknowledgedStanzas.sentAgainIds(secondSession.getStreamManager()), "The second session must track the stanzas that were routed again as sent again, and only those.");

        final List<Packet> routedAtSecondBreak = routed(secondRouter);
        assertEquals(List.of("message", "fresh"), routedAtSecondBreak.stream().map(Packet::getID).toList(), "At the second break, each unacknowledged stanza must be handled once, in the order in which it was sent, and the presence that was sent again must be dropped.");

        final Message messageError = (Message) routedAtSecondBreak.get(0);
        assertEquals(Message.Type.error, messageError.getType(), "A message that was sent again must be returned to its sender as an error, not routed again.");
        assertEquals(SENDER, messageError.getTo(), "The error must be addressed to the sender of the message.");
        assertEquals(new JID(REMOTE_DOMAIN), messageError.getFrom(), "The error must come from the recipient of the message.");
        assertEquals("thread-1", messageError.getThread(), "The error must keep the thread of the message.");
        assertEquals(PacketError.Condition.service_unavailable, messageError.getError().getCondition(), "The error must report the 'service-unavailable' condition.");
        assertEquals(PacketError.Type.cancel, messageError.getError().getType(), "The error must have the type 'cancel', the type that RFC 6120 associates with 'service-unavailable'.");
        assertNull(messageError.getBody(), "The error must not include the content of the message.");

        final Packet fresh = routedAtSecondBreak.get(1);
        assertEquals(new JID(REMOTE_DOMAIN), fresh.getTo(), "A stanza that was first sent on the second connection must be routed again to its recipient.");
        assertNull(fresh.getError(), "A stanza that was first sent on the second connection must not be turned into an error.");
    }

    /**
     * Verifies that a stanza that was sent again, and is unacknowledged when the second connection breaks, is neither
     * routed again nor answered with an error when no error may be returned for it: an IQ result, a stanza of type
     * 'error' (RFC 6120 § 8.2.3 and § 8.3.1), and a presence of any type, which RFC 6121 § 8.5.3.2.2 says to ignore
     * silently when it has no type or is of type 'unavailable'.
     */
    @Test
    public void stanzaSentAgainIsDroppedAtSecondBreakWhenNoErrorMayBeReturned() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(iq("result", IQ.Type.result));
        session.deliver(iq("iq-error", IQ.Type.error));
        final Message messageError = message("message-error");
        messageError.setType(Message.Type.error);
        session.deliver(messageError);
        final Presence presenceError = presence("presence-error");
        presenceError.setType(Presence.Type.error);
        session.deliver(presenceError);
        session.deliver(presence("available"));
        final Presence unavailable = presence("unavailable");
        unavailable.setType(Presence.Type.unavailable);
        session.deliver(unavailable);
        final Presence probe = presence("probe");
        probe.setType(Presence.Type.probe);
        probe.setFrom(SENDER.asBareJID());
        session.deliver(probe);
        final Presence subscribe = presence("subscribe");
        subscribe.setType(Presence.Type.subscribe);
        subscribe.setFrom(SENDER.asBareJID());
        session.deliver(subscribe);
        final PacketRouter firstRouter = mock(PacketRouter.class);
        session.getStreamManager().onClose(firstRouter, SERVER_ADDRESS);

        final LocalOutgoingServerSession secondSession = newSessionWithStreamManagement("stream-2");
        deliverAsQueued(routed(firstRouter), secondSession);
        final PacketRouter secondRouter = mock(PacketRouter.class);

        // Execute system under test.
        secondSession.getStreamManager().onClose(secondRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("result", "iq-error", "message-error", "presence-error", "available", "unavailable", "probe", "subscribe"), UnacknowledgedStanzas.sentAgainIds(secondSession.getStreamManager()), "Test fixture expects each stanza to be tracked as sent again on the second session.");
        assertEquals(List.of(), routed(secondRouter), "No stanza must be routed again, and no error must be returned, for stanzas that may not be answered with an error.");
    }

    /**
     * Verifies that a stanza that was routed again, and that reaches a session of which the connection is closed, keeps
     * the marker when that session routes it once more, so that the session that sends it next tracks it as sent again.
     */
    @Test
    public void stanzaRoutedFromClosedConnectionKeepsMarker() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(presence("presence"));
        final PacketRouter firstRouter = mock(PacketRouter.class);
        session.getStreamManager().onClose(firstRouter, SERVER_ADDRESS);
        final Packet routedAgain = routed(firstRouter).get(0);

        final PacketRouter packetRouter = mock(PacketRouter.class);
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        final Connection closedConnection = mock(Connection.class, withSettings().lenient());
        doReturn(true).when(closedConnection).isClosed();
        doReturn(CompletableFuture.completedFuture(null)).when(closedConnection).getCloseFuture();
        final LocalOutgoingServerSession closedSession = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, closedConnection, BasicStreamIDFactory.createStreamID("stream-closed"));

        // Execute system under test.
        closedSession.deliver(routedAgain);

        // Verify result.
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(packetRouter, timeout(5000).description("The session of which the connection is closed must route the stanza once more.")).route(captor.capture());
        final LocalOutgoingServerSession nextSession = newSessionWithStreamManagement("stream-next");
        nextSession.deliver(captor.getValue());
        assertEquals(List.of("presence"), UnacknowledgedStanzas.sentAgainIds(nextSession.getStreamManager()), "The session that sends the stanza next must track it as sent again.");
    }

    /**
     * Creates an authenticated outgoing session for {@link #DOMAIN_PAIR} on a mocked connection of its own, on which the
     * remote server enabled Stream Management, as a new connection that replaces {@link #session}.
     */
    private static LocalOutgoingServerSession newSessionWithStreamManagement(final String streamId)
    {
        final Connection newConnection = mock(Connection.class, withSettings().lenient());
        doReturn(new CompletableFuture<Void>()).when(newConnection).getCloseFuture();
        final LocalOutgoingServerSession newSession = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, newConnection, BasicStreamIDFactory.createStreamID(streamId));
        newSession.setAddress(new JID(null, REMOTE_DOMAIN, null));
        newSession.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        newSession.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        newSession.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        assertTrue(newSession.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled on the new session.");
        return newSession;
    }

    /**
     * Has the provided session deliver the stanzas that were routed again, as OutgoingSessionPromise does when it sends
     * the stanzas that it queued for a new connection: each is a copy, which keeps the marker of the stanza.
     */
    private static void deliverAsQueued(final List<Packet> routedAgain, final LocalOutgoingServerSession target) throws Exception
    {
        for (final Packet packet : routedAgain) {
            final Packet copy = packet.createCopy();
            RequestingStreamManager.copyRoutedAgainMarker(packet, copy);
            target.deliver(copy);
        }
    }

    /**
     * Invokes the private close listener for outgoing server sessions of the provided session manager for
     * {@link #session}, and waits for it to complete.
     */
    private void fireOutgoingConnectionClose(final SessionManager sessionManager) throws Exception
    {
        final Field listenerField = SessionManager.class.getDeclaredField("outgoingServerListener");
        listenerField.setAccessible(true);
        final Object listener = listenerField.get(sessionManager);

        final Method method = listener.getClass().getDeclaredMethod("onConnectionClosing", Object.class);
        method.setAccessible(true);

        final Object result = method.invoke(listener, session);
        if (result instanceof CompletableFuture) {
            ((CompletableFuture<?>) result).join();
        }
    }

    /**
     * Returns the stanzas that were routed by the provided router, through any of its methods, in order.
     */
    private static List<Packet> routed(final PacketRouter packetRouter)
    {
        return mockingDetails(packetRouter).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("route"))
            .map(invocation -> (Packet) invocation.getArgument(0))
            .toList();
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

    private static Presence presence(final String id)
    {
        final Presence presence = new Presence();
        presence.setID(id);
        presence.setFrom(SENDER);
        presence.setTo(REMOTE_DOMAIN);
        return presence;
    }

    private static IQ iq(final String id, final IQ.Type type)
    {
        final IQ iq = new IQ(type, id);
        iq.setFrom(SENDER);
        iq.setTo(REMOTE_DOMAIN);
        return iq;
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
