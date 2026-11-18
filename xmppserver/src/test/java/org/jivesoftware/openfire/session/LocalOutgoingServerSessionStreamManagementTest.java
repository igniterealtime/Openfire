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
import org.jivesoftware.openfire.nio.NetworkEntityUnreachableException;
import org.jivesoftware.openfire.server.OutgoingSessionPromise;
import org.jivesoftware.openfire.server.RemoteServerManager;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.OutboundResumeRequest;
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
     * The instances that authenticateDomain, or an attempt to resume a session, created to open a connection.
     */
    private final List<NettySessionInitializer> initialisers = new ArrayList<>();

    /**
     * The arguments with which an attempt to resume a session created an instance to open a connection.
     */
    private final List<List<?>> initialiserArguments = new ArrayList<>();

    /**
     * The attempts to resume a detached session that were started, which a test runs when it needs to.
     */
    private final List<Runnable> resumptionAttempts = new CopyOnWriteArrayList<>();

    /**
     * The delays after which another attempt to resume a detached session was scheduled, after an attempt failed. Such
     * an attempt is added to {@link #resumptionAttempts}.
     */
    private final List<Duration> retryDelays = new CopyOnWriteArrayList<>();

    /**
     * The authentications that {@link #authenticateDomainUntilNegotiationIsAwaited()} started on another thread, which
     * are ended before the next test starts.
     */
    private final List<CompletableFuture<DomainAuthResult>> backgroundAuthentications = new CopyOnWriteArrayList<>();

    private Executor originalResumptionExecutor;

    private BiConsumer<Runnable, Duration> originalRetryScheduler;

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

        originalResumptionExecutor = LocalOutgoingServerSession.resumptionExecutor;
        LocalOutgoingServerSession.resumptionExecutor = resumptionAttempts::add;
        originalRetryScheduler = LocalOutgoingServerSession.resumptionRetryScheduler;
        LocalOutgoingServerSession.resumptionRetryScheduler = (task, delay) -> { retryDelays.add(delay); resumptionAttempts.add(task); };
    }

    @AfterEach
    public void tearDown()
    {
        // An authentication that a failed test left waiting for negotiation of Stream Management stops waiting once the connection is closed.
        closeFuture.complete(null);
        for (final CompletableFuture<DomainAuthResult> authentication : backgroundAuthentications) {
            try {
                authentication.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // Fails here, rather than in a later test that the still-running authentication would disturb.
                fail("An authentication that the test started on another thread did not end within ten seconds after its connection was closed.");
            } catch (Exception e) {
                // Its outcome was checked by the test, if the test got that far.
            }
        }
        LocalOutgoingServerSession.resumptionExecutor = originalResumptionExecutor;
        LocalOutgoingServerSession.resumptionRetryScheduler = originalRetryScheduler;
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a session on which Openfire asked to enable Stream Management is made available as a route only
     * once the remote server answered.
     */
    @Test
    public void routeIsAddedOnlyAfterRemoteServerEnabledStreamManagement() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        final CompletableFuture<DomainAuthResult> authentication = authenticateDomainUntilNegotiationIsAwaited();
        final List<RequestingStreamManager.EnableRequestStatus> statusWhenRoutedBeforeAnswer = List.copyOf(statusWhenRouted);
        session.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        final DomainAuthResult result = authentication.get(10, TimeUnit.SECONDS);

        // Verify result.
        assertEquals(List.of(), statusWhenRoutedBeforeAnswer, "The session must not be added as a route while the remote server has not answered.");
        assertTrue(result.isSuccess(), "Authentication was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(RequestingStreamManager.EnableRequestStatus.ENABLED), statusWhenRouted, "The session must be added as a route once, and only after the remote server enabled Stream Management.");
        assertTrue(session.checkOutgoingDomainPair(DOMAIN_PAIR), "The domain pair must be authenticated on the session.");
    }

    /**
     * Verifies that a session on which the remote server refused Stream Management is made available as a route only
     * once the remote server answered.
     */
    @Test
    public void routeIsAddedOnlyAfterRemoteServerRefusedStreamManagement() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        final CompletableFuture<DomainAuthResult> authentication = authenticateDomainUntilNegotiationIsAwaited();
        final List<RequestingStreamManager.EnableRequestStatus> statusWhenRoutedBeforeAnswer = List.copyOf(statusWhenRouted);
        session.getStreamManager().process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3)));
        final DomainAuthResult result = authentication.get(10, TimeUnit.SECONDS);

        // Verify result.
        assertEquals(List.of(), statusWhenRoutedBeforeAnswer, "The session must not be added as a route while the remote server has not answered.");
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
    public void routeIsAddedOnlyAfterFeaturesWhenEnabledForServers() throws Exception
    {
        // Execute system under test.
        final CompletableFuture<DomainAuthResult> authentication = authenticateDomainUntilNegotiationIsAwaited();
        final List<Boolean> negotiationFinishedWhenRoutedBeforeFeatures = List.copyOf(negotiationFinishedWhenRouted);
        session.getStreamManager().skipEnable();
        final DomainAuthResult result = authentication.get(10, TimeUnit.SECONDS);

        // Verify result.
        assertEquals(List.of(), negotiationFinishedWhenRoutedBeforeFeatures, "The session must not be added as a route before the features were received.");
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
    public void sessionIsNotUsedWhenConnectionClosesDuringNegotiation() throws Exception
    {
        // Setup test fixture.
        session.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        final CompletableFuture<DomainAuthResult> authentication = authenticateDomainUntilNegotiationIsAwaited();
        closeFuture.complete(null);
        final DomainAuthResult result = authentication.get(10, TimeUnit.SECONDS);

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
     * Verifies that a stanza that is delivered while the session is detached, and that obtains the lock of the stream
     * manager only after the stream was resumed on a new connection, is sent on that connection rather than queued, as
     * the connection is read again under that lock. Queueing would otherwise start again on a healthy connection.
     */
    @Test
    public void stanzaDeliveredDuringResumptionIsSentOnNewConnection() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession detached = detachedSession(sessionManager);
        final RequestingStreamManager streamManager = detached.getStreamManager();
        final Connection newConnection = mock(Connection.class);
        final Thread deliverer;

        // Execute system under test.
        synchronized (streamManager) {
            deliverer = new Thread(() -> {
                try {
                    detached.deliver(message("during-resumption"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            deliverer.start();
            awaitBlockedOn(deliverer, streamManager);
            // As a resumption does: LocalSession#reattach sets the connection, then RequestingStreamManager#onResume stops queueing under this lock.
            detached.conn = newConnection;
            streamManager.onResume(SERVER_ADDRESS, 0);
            assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(streamManager), "Test fixture expects nothing to be queued after resumption.");
            clearInvocations(newConnection);
        }
        deliverer.join(TimeUnit.SECONDS.toMillis(10));

        // Verify result.
        assertFalse(deliverer.isAlive(), "Test fixture expects the delivering thread to have finished.");
        verify(newConnection, times(1).description("The stanza must be sent on the connection on which the stream was resumed.")).deliver(argThat(packet -> "during-resumption".equals(packet.getID())));
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(streamManager), "The stanza must not be queued once the stream is resumed.");
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
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager);

        // Verify result.
        assertEquals(List.of(true), routeRemovedWhenRouted, "The unacknowledged stanza must be routed again once, after the route of the session was removed.");
        assertFalse(session.getStreamManager().isEnabled(), "Stream Management must be disabled when the session closes.");
    }

    /**
     * Verifies that a stanza that is routed again when a connection breaks, and that OutgoingSessionPromise queues and
     * then hands to the session of a new connection, is tracked by that session as sent again, so that it is returned
     * to its sender as an error rather than sent a third time when that connection breaks too (decision 4 of OF-2425).
     * OutgoingSessionPromise queues a copy of the stanza, which must keep the marker of the stanza.
     */
    @Test
    public void stanzaRoutedAgainThroughOutgoingSessionPromiseIsTrackedAsSentAgain() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        final Message message = message("message");
        message.setFrom(SENDER);
        message.setType(Message.Type.chat);
        session.deliver(message);
        final PacketRouter firstRouter = mock(PacketRouter.class);
        session.getStreamManager().onClose(firstRouter, SERVER_ADDRESS);
        final List<Packet> routedAtFirstBreak = routed(firstRouter);
        assertEquals(List.of("message"), routedAtFirstBreak.stream().map(Packet::getID).toList(), "Test fixture expects the unacknowledged stanza to be routed again at the first break.");

        final LocalOutgoingServerSession secondSession = newSessionWithStreamManagement("stream-2");
        secondSession.addOutgoingDomainPair(DOMAIN_PAIR);
        doReturn(secondSession).when(routingTable).getServerRoute(DOMAIN_PAIR);
        final OutgoingSessionPromise promise = OutgoingSessionPromise.getInstance();
        final Field routingTableField = OutgoingSessionPromise.class.getDeclaredField("routingTable");
        routingTableField.setAccessible(true);
        final Object originalRoutingTable = routingTableField.get(promise);
        routingTableField.set(promise, routingTable);
        try {
            // Execute system under test.
            promise.createProcess(DOMAIN_PAIR, routedAtFirstBreak.get(0));
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (UnacknowledgedStanzas.ids(secondSession.getStreamManager()).isEmpty() && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
        } finally {
            routingTableField.set(promise, originalRoutingTable);
        }
        final PacketRouter secondRouter = mock(PacketRouter.class);
        secondSession.getStreamManager().onClose(secondRouter, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("message"), UnacknowledgedStanzas.sentAgainIds(secondSession.getStreamManager()), "The session of the new connection must track the stanza that OutgoingSessionPromise handed to it as sent again.");
        final List<Packet> routedAtSecondBreak = routed(secondRouter);
        assertEquals(1, routedAtSecondBreak.size(), "The stanza must be handled once at the second break, but these were routed: " + routedAtSecondBreak);
        assertEquals(Message.Type.error, ((Message) routedAtSecondBreak.get(0)).getType(), "A stanza that was sent again must be returned to its sender as an error, not routed again.");
        assertEquals(SENDER, routedAtSecondBreak.get(0).getTo(), "The error must be addressed to the sender of the stanza.");
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
        assertEquals(List.of("fresh", "message"), routedAtSecondBreak.stream().map(Packet::getID).toList(), "At the second break, each unacknowledged stanza must be handled once, the error for the message that was sent again after the stanza that is routed again, and the presence that was sent again must be dropped.");

        final Message messageError = (Message) routedAtSecondBreak.get(1);
        assertEquals(Message.Type.error, messageError.getType(), "A message that was sent again must be returned to its sender as an error, not routed again.");
        assertEquals(SENDER, messageError.getTo(), "The error must be addressed to the sender of the message.");
        assertEquals(new JID(REMOTE_DOMAIN), messageError.getFrom(), "The error must come from the recipient of the message.");
        assertEquals("thread-1", messageError.getThread(), "The error must keep the thread of the message.");
        assertEquals(PacketError.Condition.service_unavailable, messageError.getError().getCondition(), "The error must report the 'service-unavailable' condition.");
        assertEquals(PacketError.Type.cancel, messageError.getError().getType(), "The error must have the type 'cancel', the type that RFC 6120 associates with 'service-unavailable'.");
        assertNull(messageError.getBody(), "The error must not include the content of the message.");

        final Packet fresh = routedAtSecondBreak.get(0);
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
     * Verifies that a session of which the stream can be resumed is detached when its connection breaks, while
     * resumption on server connections is enabled, and that it stays the route for its domain pairs (decision 7 of
     * OF-2425), without handling its unacknowledged stanzas yet.
     */
    @Test
    public void resumableSessionIsDetachedAndKeepsItsRoutesWhenConnectionBreaks() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        assertTrue(sessionManager.isDetached(resumable), "A session of which the stream can be resumed must be detached when its connection breaks.");
        assertNull(resumable.getConnection(), "A detached session must have no connection.");
        verify(routingTable, never().description("The routes of a detached session must be kept.")).removeServerRoute(any(DomainPair.class));
        assertTrue(resumable.checkOutgoingDomainPair(DOMAIN_PAIR), "The domain pair must remain authenticated on a detached session.");
        verify(packetRouter, never().description("No stanza must be routed again while the session can still be resumed.")).route(any(Packet.class));
        assertEquals(List.of("unacked"), UnacknowledgedStanzas.ids(resumable.getStreamManager()), "The unacknowledged stanza must be kept while the session can still be resumed.");
    }

    /**
     * Verifies that a session of which the stream can be resumed is not detached when resumption on server connections
     * is disabled by the time its connection breaks: its routes are removed and its unacknowledged stanzas are handled,
     * as for a session that cannot be resumed.
     */
    @Test
    public void resumableSessionIsNotDetachedWhenServerResumptionIsDisabled() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session must not be detached when resumption on server connections is disabled.");
        verify(routingTable, times(1).description("The route of a session that is not detached must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of("unacked"), routed(packetRouter).stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again, as for a session that cannot be resumed.");
    }

    /**
     * Verifies that a session is not detached when the remote server enabled Stream Management without a stream that
     * can be resumed, although Openfire requested one.
     */
    @Test
    public void sessionThatCannotBeResumedIsNotDetached() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalOutgoingServerSession notResumable = newSession("stream-not-resumable");
        notResumable.addOutgoingDomainPair(DOMAIN_PAIR);
        notResumable.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        notResumable.getStreamManager().process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        assertFalse(notResumable.getStreamManager().getResume(), "Test fixture expects the stream not to be resumable.");
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, notResumable);

        // Verify result.
        assertFalse(sessionManager.isDetached(notResumable), "A session of which the stream cannot be resumed must not be detached.");
        verify(routingTable, times(1).description("The route of a session that is not detached must be removed.")).removeServerRoute(DOMAIN_PAIR);
    }

    /**
     * Verifies that stanzas that are delivered to a detached session are queued in order, behind the unacknowledged
     * ones, and are neither sent nor counted, nor routed elsewhere.
     */
    @Test
    public void stanzasDeliveredToDetachedSessionAreQueued() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        doReturn(true).when(connection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        clearInvocations(connection);

        // Execute system under test.
        resumable.deliver(message("queued-1"));
        resumable.deliver(presence("queued-2"));

        // Verify result.
        assertEquals(List.of("queued-1", "queued-2"), UnacknowledgedStanzas.queuedIds(resumable.getStreamManager()), "Stanzas delivered to a detached session must be queued in the order in which they were delivered.");
        assertEquals(List.of("unacked"), UnacknowledgedStanzas.ids(resumable.getStreamManager()), "Queued stanzas must not be tracked as unacknowledged, as they are not sent.");
        verify(connection, never().description("A stanza for a detached session must not be delivered on its former connection.")).deliver(any(Packet.class));
        verify(packetRouter, after(200).never().description("A stanza for a detached session must not be routed elsewhere.")).route(any(Packet.class));
    }

    /**
     * Verifies that a stanza that is delivered after the connection of a session of which the stream can be resumed
     * broke, but before the session is detached, is queued before the stanzas that are delivered after it is detached,
     * rather than routed again.
     */
    @Test
    public void stanzaDeliveredBeforeDetachingIsQueuedFirst() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        doReturn(true).when(connection).isClosed();

        // Execute system under test.
        resumable.deliver(message("before-detach"));
        fireOutgoingConnectionClose(sessionManager, resumable);
        resumable.deliver(message("after-detach"));

        // Verify result.
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the session to be detached.");
        assertEquals(List.of("before-detach", "after-detach"), UnacknowledgedStanzas.queuedIds(resumable.getStreamManager()), "Both stanzas must be queued, in the order in which they were delivered.");
        verify(connection, never().description("A stanza must not be delivered on a closed connection.")).deliver(any(Packet.class));
        verify(packetRouter, after(200).never().description("A stanza for a session that is detached must not be routed again.")).route(any(Packet.class));
    }

    /**
     * Verifies that a detached session is ended once it was detached for longer than the server detach timeout: its
     * routes are removed, after which its unacknowledged stanzas are handled as for a session that closes without being
     * resumed, and then its queued stanzas are routed again, in order (decision 7 of OF-2425).
     */
    @Test
    public void detachedSessionIsEndedAfterServerDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(1));
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        resumable.deliver(iq("unacked-request", IQ.Type.get));
        doReturn(true).when(connection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        resumable.deliver(message("queued-1"));
        resumable.deliver(message("queued-2"));
        setDetachedSince(resumable, Instant.now().minus(Duration.ofMinutes(2)));

        final AtomicBoolean routeRemoved = new AtomicBoolean(false);
        doAnswer(invocation -> { routeRemoved.set(true); return true; }).when(routingTable).removeServerRoute(DOMAIN_PAIR);
        final List<Boolean> routeRemovedWhenRouted = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { routeRemovedWhenRouted.add(routeRemoved.get()); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session that was detached for longer than the server detach timeout must no longer be detached.");
        verify(routingTable, times(1).description("The route of a session that is ended must be removed.")).removeServerRoute(DOMAIN_PAIR);
        final List<Packet> routed = routed(packetRouter);
        assertEquals(List.of("unacked", "queued-1", "queued-2", "unacked-request"), routed.stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again first, then the queued stanzas, in order, and the IQ request must be returned as an error last.");
        assertEquals(IQ.Type.error, ((IQ) routed.get(3)).getType(), "An unacknowledged IQ request must be returned to its sender as an error, as for a session that closes without being resumed.");
        assertEquals(List.of(true, true, true, true), routeRemovedWhenRouted, "Every stanza must be routed only after the route of the session was removed.");
        assertFalse(resumable.getStreamManager().isEnabled(), "Stream Management must be disabled once the session is ended.");
    }

    /**
     * Verifies that two detached sessions with the same stream ID, which the remote servers chose, are each registered
     * as detached, and are each ended once their detach timeout passed, so that neither keeps its routes.
     */
    @Test
    public void detachedSessionsWithSameStreamIdAreEachEnded() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(1));
        final LocalOutgoingServerSession first = detachedSession(sessionManager);
        final LocalOutgoingServerSession second = detachedSession(sessionManager);
        assertEquals(first.getStreamID(), second.getStreamID(), "Test fixture expects both sessions to have the same stream ID.");
        final boolean bothDetached = sessionManager.isDetached(first) && sessionManager.isDetached(second);
        setDetachedSince(first, Instant.now().minus(Duration.ofMinutes(2)));
        setDetachedSince(second, Instant.now().minus(Duration.ofMinutes(2)));

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertTrue(bothDetached, "Both sessions must be registered as detached.");
        assertFalse(sessionManager.isDetached(first), "The first session must be ended once its detach timeout passed.");
        assertFalse(sessionManager.isDetached(second), "The second session must be ended once its detach timeout passed.");
        verify(routingTable, times(2).description("The route of each session must be removed when it is ended.")).removeServerRoute(DOMAIN_PAIR);
    }

    /**
     * Verifies that a detached session is ended once, when it is ended more than once, for example by the cleanup task
     * and because its queue is full at the same time.
     */
    @Test
    public void detachedSessionIsEndedOnlyOnce() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.deliver(message("queued"));

        // Execute system under test.
        sessionManager.terminateDetached(resumable);
        sessionManager.terminateDetached(resumable);

        // Verify result.
        verify(routingTable, times(1).description("The route of a detached session must be removed only by the first request to end it.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of("queued"), routed(packetRouter).stream().map(Packet::getID).toList(), "The queued stanza must be routed again once.");
    }

    /**
     * Verifies that a detached session is ended without holding the monitor of the session manager or the lock of its
     * stream manager while its routes are removed and its stanzas are routed.
     */
    @Test
    public void detachedSessionIsEndedWithoutHoldingLocks() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.deliver(message("queued"));
        final RequestingStreamManager streamManager = resumable.getStreamManager();
        final List<Boolean> heldLock = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { heldLock.add(Thread.holdsLock(sessionManager) || Thread.holdsLock(streamManager)); return true; }).when(routingTable).removeServerRoute(any(DomainPair.class));
        doAnswer(invocation -> { heldLock.add(Thread.holdsLock(sessionManager) || Thread.holdsLock(streamManager)); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        sessionManager.terminateDetached(resumable);

        // Verify result.
        assertEquals(List.of(false, false), heldLock, "The route must be removed and the stanza routed, without holding the monitor of the session manager or the lock of the stream manager.");
    }

    /**
     * Verifies that a detached session that is ended removes the route of its domain pair without holding the mutex with
     * which stanzas for that domain pair are routed, as removing a route can wait for a cluster-wide lock, and then
     * routes the stanzas of that domain pair again while holding that mutex, so that a stanza for the domain pair that
     * is routed meanwhile cannot go out ahead of them. A stanza of another domain pair is routed after that, without
     * holding that mutex.
     */
    @Test
    public void detachedSessionRoutesStanzasAgainUnderMutexOfDomainPair() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        doReturn(true).when(connection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        resumable.deliver(message("queued"));
        final Message unrelated = message("queued-unrelated");
        unrelated.setTo("unrelated.example.com");
        resumable.deliver(unrelated);

        final Object mutex = OutgoingSessionPromise.getInstance().getMutex(DOMAIN_PAIR);
        final List<String> events = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { events.add("remove " + invocation.getArgument(0) + " " + Thread.holdsLock(mutex)); return true; }).when(routingTable).removeServerRoute(any(DomainPair.class));
        doAnswer(invocation -> { events.add("route " + ((Packet) invocation.getArgument(0)).getID() + " " + Thread.holdsLock(mutex)); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        sessionManager.terminateDetached(resumable);

        // Verify result.
        assertEquals(List.of("remove " + DOMAIN_PAIR + " false", "route unacked true", "route queued true", "route queued-unrelated false"), events, "The route of the domain pair must be removed without holding its mutex, and then its unacknowledged and queued stanzas routed again while holding it, after which a stanza of another domain pair must be routed without holding it.");
    }

    /**
     * Verifies that a stanza that is delivered to a detached session while it is ended, before the route of its domain
     * pair is removed, is handled after the stanzas that were queued before it, and once. This guards the order of the
     * stanzas of a session that is ended; it does not reproduce two threads that route stanzas for the domain pair at
     * the same time.
     */
    @Test
    public void stanzaDeliveredWhileDetachedSessionIsEndedIsRoutedAfterEarlierStanzas() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.deliver(message("queued"));
        doAnswer(invocation -> { resumable.deliver(message("late")); return true; }).when(routingTable).removeServerRoute(DOMAIN_PAIR);

        // Execute system under test.
        sessionManager.terminateDetached(resumable);

        // Verify result.
        assertEquals(List.of("queued", "late"), routed(packetRouter).stream().map(Packet::getID).toList(), "A stanza that is delivered while the session is ended must be routed again once, after the stanza that was queued before it.");
    }

    /**
     * Verifies that the close listener for outgoing server sessions, for a session that is not detached, removes the
     * route of each domain pair without holding the mutex with which stanzas for that domain pair are routed, and then
     * routes the unacknowledged stanzas of that domain pair again while holding that mutex.
     */
    @Test
    public void closeListenerRemovesRouteThenRoutesStanzasAgainUnderMutexOfDomainPair() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        session.addOutgoingDomainPair(DOMAIN_PAIR);
        enableStreamManagement();
        session.deliver(message("1"));
        session.deliver(message("2"));
        doReturn(true).when(connection).isClosed();

        final Object mutex = OutgoingSessionPromise.getInstance().getMutex(DOMAIN_PAIR);
        final List<String> events = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { events.add("remove " + Thread.holdsLock(mutex)); return true; }).when(routingTable).removeServerRoute(DOMAIN_PAIR);
        doAnswer(invocation -> { events.add("route " + ((Packet) invocation.getArgument(0)).getID() + " " + Thread.holdsLock(mutex)); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager);

        // Verify result.
        assertEquals(List.of("remove false", "route 1 true", "route 2 true"), events, "The route must be removed without holding the mutex of the domain pair, and then the unacknowledged stanzas routed again in order while holding it.");
    }

    /**
     * Verifies that the stanzas of a session that is closed are handled once when the session is closed again while
     * the first close is still handling them, as when a detached session is ended while its close listener runs.
     */
    @Test
    public void closingAgainWhileClosingDoesNotHandleStanzasTwice() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        session.addOutgoingDomainPair(DOMAIN_PAIR);
        enableStreamManagement();
        session.deliver(message("1"));
        session.deliver(message("2"));
        doReturn(true).when(connection).isClosed();
        doAnswer(invocation -> { session.getStreamManager().onClose(packetRouter, SERVER_ADDRESS, List.of(DOMAIN_PAIR), domainPair -> {}); return true; }).when(routingTable).removeServerRoute(DOMAIN_PAIR);

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager);

        // Verify result.
        assertEquals(List.of("1", "2"), routed(packetRouter).stream().map(Packet::getID).toList(), "Each unacknowledged stanza must be routed again once, also when the session is closed again while its stanzas are being handled.");
    }

    /**
     * Verifies that the close listener for outgoing server sessions routes the error that returns an unacknowledged IQ
     * request to its sender without holding the mutex of the domain pair, after the stanzas that are routed again under
     * that mutex. The error goes to a local sender, which can route other stanzas on the same thread, to another remote
     * domain, of which another thread can hold the mutex while it waits for this one.
     */
    @Test
    public void closeListenerRoutesErrorsWithoutHoldingMutexOfDomainPair() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        session.addOutgoingDomainPair(DOMAIN_PAIR);
        enableStreamManagement();
        session.deliver(iq("request", IQ.Type.get));
        session.deliver(message("message"));
        doReturn(true).when(connection).isClosed();

        final Object mutex = OutgoingSessionPromise.getInstance().getMutex(DOMAIN_PAIR);
        final List<String> events = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { events.add("route " + ((Packet) invocation.getArgument(0)).getID() + " " + Thread.holdsLock(mutex)); return null; }).when(packetRouter).route(any(Packet.class));

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager);

        // Verify result.
        assertEquals(List.of("route message true", "route request false"), events, "The message must be routed again while holding the mutex of the domain pair, and the error for the IQ request routed after it, without holding that mutex.");
        assertEquals(IQ.Type.error, ((IQ) routed(packetRouter).get(1)).getType(), "The unacknowledged IQ request must be returned to its sender as an error.");
    }

    /**
     * Verifies that a queued stanza keeps the marker of a stanza that was routed again, so that the session that sends
     * it next after the detached session is ended tracks it as sent again.
     */
    @Test
    public void queuedStanzaKeepsMarker() throws Exception
    {
        // Setup test fixture.
        enableStreamManagement();
        session.deliver(presence("presence"));
        final PacketRouter firstRouter = mock(PacketRouter.class);
        session.getStreamManager().onClose(firstRouter, SERVER_ADDRESS);
        final Packet routedAgain = routed(firstRouter).get(0);

        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.deliver(routedAgain);

        // Execute system under test.
        sessionManager.terminateDetached(resumable);

        // Verify result.
        final LocalOutgoingServerSession nextSession = newSessionWithStreamManagement("stream-next");
        deliverAsQueued(routed(packetRouter), nextSession);
        assertEquals(List.of("presence"), UnacknowledgedStanzas.sentAgainIds(nextSession.getStreamManager()), "The session that sends the queued stanza next must track it as sent again.");
    }

    /**
     * Verifies that a detached session is ended when more stanzas are queued than the maximum allows, after which every
     * queued stanza, including the one that exceeded the maximum, is routed again.
     */
    @Test
    public void exceedingMaximumOfQueuedStanzasEndsDetachedSession() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        RequestingStreamManager.MAX_QUEUED_WHILE_BROKEN.setValue(2);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.deliver(message("queued-1"));
        resumable.deliver(message("queued-2"));
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the session to stay detached while the maximum is not exceeded.");

        // Execute system under test.
        resumable.deliver(message("queued-3"));

        // Verify result.
        verify(packetRouter, timeout(5000).times(3).description("Every queued stanza must be routed again once the maximum is exceeded.")).route(any(Packet.class));
        assertEquals(List.of("queued-1", "queued-2", "queued-3"), routed(packetRouter).stream().map(Packet::getID).toList(), "The queued stanzas must be routed again in order.");
        assertFalse(sessionManager.isDetached(resumable), "A session of which the queue exceeded the maximum must no longer be detached.");
        verify(routingTable, times(1).description("The route of a session that is ended must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertFalse(resumable.getStreamManager().getResume(), "A session of which the queue exceeded the maximum must not be resumable.");
    }

    /**
     * Verifies that a session of which the queue exceeds the maximum before it is detached is not detached, but closed
     * as a session that cannot be resumed, which routes its queued stanzas again.
     */
    @Test
    public void exceedingMaximumOfQueuedStanzasBeforeDetachingPreventsDetaching() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        RequestingStreamManager.MAX_QUEUED_WHILE_BROKEN.setValue(0);
        final LocalOutgoingServerSession resumable = resumableSession();
        doReturn(true).when(connection).isClosed();
        resumable.deliver(message("queued"));

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);
        closeFuture.complete(null);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session of which the queue exceeded the maximum must not be detached.");
        verify(routingTable, times(1).description("The route of a session that is not detached must be removed.")).removeServerRoute(DOMAIN_PAIR);
        verify(packetRouter, after(200).times(1).description("The queued stanza must be routed again once.")).route(any(Packet.class));
        assertEquals(List.of("queued"), routed(packetRouter).stream().map(Packet::getID).toList(), "The queued stanza must be routed again.");
    }

    /**
     * Verifies that a session of which the queue exceeds the maximum while it is resumed, after it was taken from the
     * detached sessions, is not ended: every queued stanza, including the one that exceeded the maximum, is sent once on
     * the resumed stream, none is routed again, and the stream can then not be resumed again.
     */
    @Test
    public void exceedingMaximumOfQueuedStanzasWhileSessionIsResumedSendsThemOnResumedStream() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        final SessionManager sessionManager = spy(new SessionManager());
        sessionManager.initialize(xmppServer);
        doReturn(sessionManager).when(xmppServer).getSessionManager();
        RequestingStreamManager.MAX_QUEUED_WHILE_BROKEN.setValue(1);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued-1"));
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        final AtomicBoolean deliveredWhileResumed = new AtomicBoolean(false);
        // A stanza is delivered, as on another thread, once the session is taken from the detached sessions to be resumed.
        doAnswer(invocation -> {
            final Object taken = invocation.callRealMethod();
            if (invocation.getArgument(0) == resumable && Boolean.TRUE.equals(taken) && deliveredWhileResumed.compareAndSet(false, true)) {
                resumable.deliver(message("queued-2"));
            }
            return taken;
        }).when(sessionManager).removeDetached(any());

        // Execute system under test.
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));

        // Verify result.
        assertTrue(deliveredWhileResumed.get(), "Test fixture expects a stanza to be delivered while the session is resumed.");
        verify(sessionManager, timeout(5000).description("Test fixture expects exceeding the maximum to try to end the session.")).terminateDetached(resumable);
        verify(packetRouter, after(200).never().description("No stanza of a session that is resumed must be routed again.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session that is resumed must not be detached.");
        assertSame(newConnection, resumable.getConnection(), "A session that is resumed must keep the new connection.");
        assertEquals(List.of("unacked", "queued-1", "queued-2"), deliveredIds(newConnection), "Every stanza, including the one that exceeded the maximum, must be sent once on the resumed stream, in order.");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(resumable.getStreamManager()), "No stanza must remain queued once the session is resumed.");
        verify(routingTable, never().description("The routes of a session that is resumed must be kept.")).removeServerRoute(any(DomainPair.class));
        assertFalse(resumable.getStreamManager().getResume(), "The stream of a session of which the queue exceeded the maximum must not be resumable again.");
    }

    /**
     * Verifies that a stanza that reaches a detached session after it was ended is routed again, rather than queued or
     * dropped, as its route is removed by then.
     */
    @Test
    public void stanzaForEndedSessionIsRoutedAgain() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        sessionManager.terminateDetached(resumable);

        // Execute system under test.
        resumable.deliver(message("late"));

        // Verify result.
        verify(packetRouter, timeout(5000).description("A stanza for a session that was ended must be routed again.")).route(any(Packet.class));
        assertEquals(List.of("late"), routed(packetRouter).stream().map(Packet::getID).toList(), "The stanza must be routed again.");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(resumable.getStreamManager()), "A stanza for a session that was ended must not be queued.");
    }

    /**
     * Verifies that a detached session does not authenticate another domain pair, as it has no connection on which to
     * do so.
     */
    @Test
    public void detachedSessionDoesNotAuthenticateSubdomain() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        resumable.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        final DomainPair other = new DomainPair(Fixtures.XMPP_DOMAIN, "other." + REMOTE_DOMAIN);

        // Execute system under test.
        final boolean result = resumable.authenticateSubdomain(other);

        // Verify result.
        assertFalse(result, "A detached session must not authenticate another domain pair.");
        assertFalse(resumable.checkOutgoingDomainPair(other), "The other domain pair must not be authenticated on the detached session.");
    }

    /**
     * Verifies that authenticating a domain pair of a detached session succeeds without opening a new connection, as the
     * detached session stays the route for it (decision 7).
     */
    @Test
    public void authenticateDomainForDomainPairOfDetachedSessionOpensNoConnection() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        doReturn(resumable).when(routingTable).getServerRoute(DOMAIN_PAIR);

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain(DOMAIN_PAIR, newSessionWithStreamManagement("stream-new"));

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication of a domain pair of a detached session was expected to succeed. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(List.of(), initialisers, "No new connection must be opened for a domain pair of a detached session.");
        verify(routingTable, never().description("No other session must be added as a route for a domain pair of a detached session.")).addServerRoute(any(DomainPair.class), any(LocalOutgoingServerSession.class));
        assertTrue(sessionManager.isDetached(resumable), "The session must stay detached.");
    }

    /**
     * Verifies that a domain pair of another local domain gets a connection of its own while a session for the same
     * remote domain is detached, as it does while that session is connected, and is not added to the detached session.
     */
    @Test
    public void authenticateDomainForOtherLocalDomainOpensConnectionOfItsOwn() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = detachedSession(sessionManager);
        doReturn(resumable).when(routingTable).getServerRoute(DOMAIN_PAIR);
        final DomainPair otherPair = new DomainPair("conference." + Fixtures.XMPP_DOMAIN, REMOTE_DOMAIN);
        final LocalOutgoingServerSession newConnectionSession = newSessionWithStreamManagement("stream-other");

        // Execute system under test.
        final DomainAuthResult result = authenticateDomain(otherPair, newConnectionSession);

        // Verify result.
        assertTrue(result.isSuccess(), "Authentication of the domain pair of another local domain was expected to succeed on a connection of its own. Diagnostic log: " + result.getDiagnosticLog());
        assertEquals(1, initialisers.size(), "A connection of its own must be opened for the domain pair of another local domain.");
        assertTrue(newConnectionSession.checkOutgoingDomainPair(otherPair), "The domain pair of the other local domain must be authenticated on the new connection.");
        assertFalse(resumable.checkOutgoingDomainPair(otherPair), "The domain pair of the other local domain must not be added to the detached session.");
        assertTrue(sessionManager.isDetached(resumable), "The session must stay detached.");
    }

    /**
     * Verifies that a session that is detached when its connection breaks is resumed on a new connection, of which the
     * session is not made a route: Openfire asks the remote server to resume the stream, and once it did, the stanzas
     * that the remote server did not receive are sent first, then the queued stanzas, in order, and then the stanzas that
     * are delivered after that (decision 7 of OF-2425).
     */
    @Test
    public void detachedSessionIsResumedOnNewConnection() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("acked"));
        resumable.deliver(message("unacked"));
        resumable.getStreamManager().process(ack("1"));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued"));
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "1"));
        resumable.deliver(message("after"));

        // Verify result.
        assertSame(resumable, initialiserArguments.get(0).get(2), "The new connection must be opened to resume the detached session.");
        final Element resume = lastResumeRequest(newConnection);
        assertEquals("sm-id-1", resume.attributeValue("previd"), "The remote server must be asked to resume the stream with its SM-ID.");
        assertEquals("0", resume.attributeValue("h"), "The remote server sent no stanzas, so Openfire handled none.");
        assertFalse(sessionManager.isDetached(resumable), "A resumed session must no longer be detached.");
        assertSame(newConnection, resumable.getConnection(), "A resumed session must use the new connection.");
        assertEquals(List.of("unacked", "queued", "after"), deliveredIds(newConnection), "The stanza that the remote server did not receive must be sent first, then the queued stanza, then the stanza that is delivered after resumption.");
        assertEquals(List.of("unacked", "queued", "after"), UnacknowledgedStanzas.ids(resumable.getStreamManager()), "Every stanza that is sent on the resumed stream must be tracked.");
        verify(routingTable, never().description("The routes of a resumed session must be kept.")).removeServerRoute(any(DomainPair.class));
        verify(routingTable, never().description("The session of the new connection must not be made a route.")).addServerRoute(any(DomainPair.class), same(provider));
        verify(packetRouter, never().description("No stanza must be routed again when the session is resumed.")).route(any(Packet.class));
        assertEquals(ServerSession.AuthenticationMethod.DIALBACK, resumable.getAuthenticationMethod(), "A resumed session must report the authentication method of the new connection.");
        verify(newConnection, times(1).description("The close listener of the resumed session must be registered on the new connection.")).registerCloseListener(any(), same(resumable));
        verify(initialisers.get(0), never().description("The new connection of a resumed session must not be closed.")).stop();
    }

    /**
     * Verifies that the close listener does not detach a session of which the stream is resumed on a new connection
     * after the listener checked the connection that closed, and before it would detach the session: the session keeps
     * using the new connection, is not detached, and no other attempt to resume it is started.
     */
    @Test
    public void sessionResumedWhileCloseIsHandledIsNotDetached() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        final AtomicBoolean resumedDuringCheck = new AtomicBoolean(false);
        // Another notification for the closed connection is handled, and the stream resumed, as on another thread, while the close listener checks the connection.
        doAnswer(invocation -> {
            if (resumedDuringCheck.compareAndSet(false, true)) {
                fireOutgoingConnectionClose(sessionManager, resumable);
                runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));
            }
            return true;
        }).when(connection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);
        resumable.deliver(message("after"));

        // Verify result.
        assertTrue(resumedDuringCheck.get(), "Test fixture expects the stream to be resumed while the close listener checks the connection.");
        assertSame(newConnection, resumable.getConnection(), "A session that was resumed while the close of its former connection was handled must keep using the new connection.");
        assertFalse(sessionManager.isDetached(resumable), "A session that was resumed while the close of its former connection was handled must not be detached.");
        assertTrue(resumptionAttempts.isEmpty(), "No other attempt to resume a session that was resumed must be started.");
        assertEquals(List.of("after"), deliveredIds(newConnection), "A stanza that is delivered to the resumed session must be sent on the new connection.");
    }

    /**
     * Verifies that a stanza that is delivered while the session is resumed, after the new connection is attached, is
     * sent after the stanzas that are sent again and the queued ones, rather than in between (OF-3420).
     */
    @Test
    public void stanzaDeliveredWhileSessionIsResumedIsSentAfterQueuedStanzas() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued"));
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        final List<String> delivered = new CopyOnWriteArrayList<>();
        final Thread[] sender = new Thread[1];
        doAnswer(invocation -> {
            final Packet packet = invocation.getArgument(0);
            delivered.add(packet.getID());
            if (sender[0] == null) {
                // Deliver a stanza from another thread while the first stanza is sent again on the new connection.
                sender[0] = new Thread(() -> { try { resumable.deliver(message("concurrent")); } catch (Exception e) { throw new RuntimeException(e); } });
                sender[0].start();
                sender[0].join(200);
            }
            return null;
        }).when(newConnection).deliver(any(Packet.class));

        // Execute system under test.
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));

        // Verify result.
        sender[0].join(5000);
        assertEquals(List.of("unacked", "queued", "concurrent"), delivered, "A stanza that is delivered while the session is resumed must be sent after the stanzas that are sent again and the queued ones.");
    }

    /**
     * Verifies that a detached session is ended when no new connection can be authenticated to resume it, and no other
     * attempt can be made before its detach timeout passes: its routes are removed, its unacknowledged stanzas are
     * handled, and its queued stanzas are routed again.
     */
    @Test
    public void detachedSessionIsEndedWhenNoNewConnectionIsAuthenticated() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMillis(500));
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued"));

        // Execute system under test.
        runResumptionAttempt(null, provider -> {});

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session that could not be resumed must no longer be detached.");
        verify(routingTable, times(1).description("The route of a session that could not be resumed must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of("unacked", "queued"), routed(packetRouter).stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again first, then the queued stanza.");
        verify(initialisers.get(0), times(1).description("The attempt to open a new connection must be stopped.")).stop();
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be made when it would start after the detach timeout passed.");
    }

    /**
     * Verifies that an attempt to resume a detached session that is interrupted while it connects, for example as
     * Openfire shuts down, ends the session instead of scheduling another attempt, and keeps the interrupt. Connecting
     * keeps the interrupt, and reports it as a remote domain that cannot be reached.
     */
    @Test
    public void detachedSessionIsEndedWhenAttemptIsInterruptedWhileConnecting() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        final CompletableFuture<LocalSession> interruptedWhileConnecting = new CompletableFuture<>() {
            @Override
            public LocalSession get(final long timeout, final TimeUnit unit) throws ExecutionException
            {
                Thread.currentThread().interrupt();
                throw new ExecutionException(new NetworkEntityUnreachableException("Interrupted while connecting."));
            }
        };

        // Execute system under test.
        final boolean interrupted;
        try {
            runResumptionAttempt(null, provider -> {}, interruptedWhileConnecting);
        } finally {
            interrupted = Thread.interrupted();
        }

        // Verify result.
        assertTrue(interrupted, "The interrupt must be kept.");
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be made after an attempt was interrupted.");
        assertFalse(sessionManager.isDetached(resumable), "A session of which the attempt to resume was interrupted must no longer be detached.");
        assertEquals(List.of("unacked"), routed(packetRouter).stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again.");
    }

    /**
     * Verifies that the first attempt to resume a detached session connects to the location that the remote server named
     * when it enabled Stream Management first (XEP-0198 § 3), and that an attempt after that one failed connects to the
     * hosts of the remote domain only, as for any new connection.
     */
    @Test
    public void firstAttemptToResumeConnectsToLocationOfRemoteServer() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession(null, "node2.example.com:5270");
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(null, p -> {});
        runResumptionAttempt(null, p -> {});

        // Verify result.
        assertEquals(2, initialiserArguments.size(), "Test fixture expects a new connection to be opened for each attempt.");
        assertEquals("node2.example.com:5270", initialiserArguments.get(0).get(3), "The first attempt must connect to the location that the remote server named first.");
        assertNull(initialiserArguments.get(1).get(3), "An attempt after the first one failed must not connect to the location that the remote server named.");
    }

    /**
     * Verifies that the first attempt to resume a session that was resumed and then detached again connects to the
     * location that the remote server named, also when an earlier attempt, before the session was resumed, did not.
     */
    @Test
    public void firstAttemptAfterDetachingAgainConnectsToLocationOfRemoteServer() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession(null, "node2.example.com:5270");
        breakConnection(sessionManager, resumable);
        runResumptionAttempt(null, p -> {});
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));
        assertFalse(sessionManager.isDetached(resumable), "Test fixture expects the session to be resumed.");
        assertNull(initialiserArguments.get(1).get(3), "Test fixture expects the attempt after the first one not to connect to the location.");
        doReturn(true).when(newConnection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(null, p -> {});

        // Verify result.
        assertEquals(3, initialiserArguments.size(), "Test fixture expects a new connection to be opened for each attempt.");
        assertEquals("node2.example.com:5270", initialiserArguments.get(2).get(3), "The first attempt after the session was detached again must connect to the location that the remote server named.");
    }

    /**
     * Verifies that an attempt to resume a detached session connects to the hosts of the remote domain only when the
     * remote server named no location for reconnecting.
     */
    @Test
    public void attemptToResumeWithoutLocationConnectsToRemoteDomain() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(null, p -> {});

        // Verify result.
        assertEquals(1, initialiserArguments.size(), "Test fixture expects a new connection to be opened for the attempt.");
        assertNull(initialiserArguments.get(0).get(3), "An attempt must not connect to a location when the remote server named none.");
    }

    /**
     * Verifies that another attempt to resume a detached session is made when no new connection could be authenticated,
     * after a delay that doubles after each failed attempt, while the session, its routes and its stanzas are kept, and
     * that a later attempt can resume the stream.
     */
    @Test
    public void failedAttemptToResumeIsMadeAgainWithIncreasingDelay() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued"));
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(null, p -> {});
        runResumptionAttempt(null, p -> {});
        final boolean detachedAfterFailures = sessionManager.isDetached(resumable);
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));

        // Verify result.
        assertTrue(detachedAfterFailures, "A session must stay detached while another attempt to resume it can be made.");
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)), retryDelays, "Another attempt must be made after each failed attempt, with a delay that doubles.");
        assertFalse(sessionManager.isDetached(resumable), "A session that a later attempt resumed must no longer be detached.");
        assertEquals(List.of("unacked", "queued"), deliveredIds(newConnection), "The stanzas must be sent on the connection of the attempt that resumed the stream, in order.");
        verify(routingTable, never().description("The routes of a session must be kept while attempts to resume it are made.")).removeServerRoute(any(DomainPair.class));
        verify(packetRouter, never().description("No stanza must be routed again while attempts to resume the session are made.")).route(any(Packet.class));
    }

    /**
     * Verifies that the delay before another attempt to resume a detached session stops doubling once it reaches its
     * maximum of 30 seconds, and stays at that maximum for later attempts.
     */
    @Test
    public void delayBetweenAttemptsToResumeIsLimited() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofHours(1));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        for (int i = 0; i < 8; i++) {
            runResumptionAttempt(null, p -> {});
        }

        // Verify result.
        final List<Duration> expected = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(16), Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofSeconds(30));
        assertEquals(expected, retryDelays, "The delay between attempts must double after each failed attempt, up to 30 seconds, and stay at 30 seconds after that.");
        assertTrue(sessionManager.isDetached(resumable), "A session must stay detached while another attempt to resume it can be made.");
    }

    /**
     * Verifies that a request to resume the stream that was sent on a new connection that was not authenticated in time
     * is given up before that connection is closed and another attempt is scheduled, so that an answer that arrives on
     * it later does not resume the session.
     */
    @Test
    public void lateAnswerOnConnectionThatWasNotAuthenticatedInTimeIsIgnored() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        runResumptionAttempt(provider, p -> p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3)), CompletableFuture.failedFuture(new TimeoutException("Not authenticated in time.")));

        // Execute system under test.
        provider.getStreamManager().process(resumed("sm-id-1", "0"));

        // Verify result.
        assertEquals(OutboundResumeRequest.Status.ABANDONED, provider.getStreamManager().getResumeRequest().getStatus(), "The request on the connection of the failed attempt must be given up.");
        final List<String> calls = mockingDetails(initialisers.get(0)).getInvocations().stream().map(invocation -> invocation.getMethod().getName()).filter(name -> name.equals("abandonResume") || name.equals("stop")).toList();
        assertEquals(List.of("abandonResume", "stop"), calls, "The request must be given up before the connection of the failed attempt is closed.");
        assertTrue(sessionManager.isDetached(resumable), "A late answer on the connection of the failed attempt must not resume the session.");
        assertNotSame(provider.getConnection(), resumable.getConnection(), "The session must not take over the connection of the failed attempt.");
        assertEquals(List.of(Duration.ofSeconds(1)), retryDelays, "Another attempt must be scheduled after the failed attempt.");
    }

    /**
     * Verifies that an attempt to resume the stream that failed while the remote server answered the request on its
     * connection anyway leaves the session to the processing of that answer: the connection is not closed, and no other
     * attempt is made.
     */
    @Test
    public void attemptThatFailedAfterRemoteServerResumedLeavesSessionResumed() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"), CompletableFuture.failedFuture(new TimeoutException("Not authenticated in time.")));

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "Test fixture expects the remote server to have resumed the session.");
        assertSame(newConnection, resumable.getConnection(), "The resumed session must keep the connection on which its stream was resumed.");
        verify(initialisers.get(0), never().description("The connection on which the stream was resumed must not be closed.")).stop();
        assertEquals(List.of(), retryDelays, "No other attempt must be scheduled for a session that was resumed.");
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be made for a session that was resumed.");
    }

    /**
     * Verifies that no attempt to resume a session is made once the session is no longer detached, for example because
     * it was ended while an attempt was scheduled.
     */
    @Test
    public void noAttemptToResumeIsMadeForSessionThatIsNoLongerDetached() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        runResumptionAttempt(null, p -> {});
        sessionManager.terminateDetached(resumable);
        final int initialisersBefore = initialisers.size();

        // Execute system under test.
        runResumptionAttempt(null, p -> {});

        // Verify result.
        assertEquals(initialisersBefore, initialisers.size(), "No new connection must be opened for a session that is no longer detached.");
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be scheduled for a session that is no longer detached.");
    }

    /**
     * Verifies that no other attempt to resume a session is made when the remote server refused to resume its stream
     * with {@code <failed/>}, which is final: the session is ended at once.
     */
    @Test
    public void refusalToResumeIsNotRetried() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), p -> {
            p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3));
            p.getStreamManager().process(failed(null));
        });

        // Verify result.
        verify(routingTable, timeout(5000).times(1).description("The route of a session of which the stream is refused must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be made after the remote server refused to resume the stream.");
        assertEquals(List.of(), retryDelays, "No other attempt must be scheduled after the remote server refused to resume the stream.");
    }

    /**
     * Verifies that no other attempt to resume a session is made when the remote server does not offer the version of
     * Stream Management of its stream, as another connection would not either: the session is ended at once.
     */
    @Test
    public void streamThatCannotBeResumedOnNewConnectionIsNotRetried() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), p -> p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V2)));

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream cannot be resumed on a new connection must be ended.");
        verify(routingTable, times(1).description("The route of a session of which the stream cannot be resumed must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of(), resumptionAttempts, "No other attempt must be made when the remote server does not offer the version of the stream.");
    }

    /**
     * Verifies that a detached session is ended, and the new connection is closed, when the remote server refuses to
     * resume its stream.
     */
    @Test
    public void detachedSessionIsEndedWhenRemoteServerRefusesToResume() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, p -> {
            p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3));
            p.getStreamManager().process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3)).addElement(QName.get("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas")).getParent());
        });

        // Verify result.
        verify(routingTable, timeout(5000).times(1).description("The route of a session of which the stream is not resumed must be removed.")).removeServerRoute(DOMAIN_PAIR);
        verify(packetRouter, timeout(5000).times(1).description("The unacknowledged stanza must be routed again.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream is not resumed must no longer be detached.");
        verify(newConnection, atLeastOnce().description("The new connection must be closed.")).close();
        assertNull(resumable.getConnection(), "The session must not take over the new connection.");
    }

    /**
     * Verifies that a detached session is ended when the remote server does not answer the request to resume its stream
     * in time, and that an answer that arrives after that is ignored.
     */
    @Test
    public void detachedSessionIsEndedWhenRemoteServerDoesNotAnswerInTime() throws Exception
    {
        // Setup test fixture.
        LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.setValue(Duration.ofMillis(100));
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMillis(500));
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, p -> p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3)));
        provider.getStreamManager().process(resumed("sm-id-1", "0"));

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream is not resumed in time must no longer be detached.");
        verify(routingTable, times(1).description("The route of a session of which the stream is not resumed in time must be removed.")).removeServerRoute(DOMAIN_PAIR);
        verify(initialisers.get(0), times(1).description("The new connection must be closed.")).stop();
        assertNull(resumable.getConnection(), "An answer that arrives too late must not let the session take over the new connection.");
        assertEquals(List.of(), deliveredIds(newConnection), "Nothing must be sent on the new connection after an answer that arrives too late.");
    }

    /**
     * Verifies that a stream that the remote server resumes after its detached session was ended, for example by the
     * cleanup task, is not resumed: the new connection is closed, and the session is not ended again. A close
     * notification for the session that arrives after that is ignored.
     */
    @Test
    public void resumedStreamOfSessionThatWasEndedClosesNewConnection() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, p -> {
            p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3));
            sessionManager.terminateDetached(resumable);
            p.getStreamManager().process(resumed("sm-id-1", "0"));
        });
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        verify(newConnection, atLeastOnce().description("The new connection must be closed, as the session was ended.")).close();
        assertNull(resumable.getConnection(), "A session that was ended must not take over the new connection.");
        assertEquals(List.of(), deliveredIds(newConnection), "Nothing must be sent on the new connection.");
        verify(routingTable, times(1).description("The route of the session must be removed once, when it was ended.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of("unacked"), routed(packetRouter).stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again once, when the session was ended.");
    }

    /**
     * Verifies that a close notification for the former connection of a resumed session is ignored, as the session uses
     * another, open connection.
     */
    @Test
    public void staleCloseNotificationOfResumedSessionIsIgnored() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        runResumptionAttempt(newSessionForResumption("stream-new"), remoteServerThatResumes(resumable, "0"));

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A stale close notification must not detach the resumed session.");
        verify(routingTable, never().description("A stale close notification must not remove the routes of the resumed session.")).removeServerRoute(any(DomainPair.class));
        assertEquals(List.of(), resumptionAttempts, "A stale close notification must not start another attempt to resume the session.");
    }

    /**
     * Verifies that a resumed session is detached again, and that another attempt to resume it is started, when its new
     * connection breaks.
     */
    @Test
    public void resumedSessionIsDetachedAgainWhenNewConnectionBreaks() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));
        doReturn(true).when(newConnection).isClosed();

        // Execute system under test.
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        assertTrue(sessionManager.isDetached(resumable), "A resumed session of which the new connection breaks must be detached again.");
        assertEquals(1, resumptionAttempts.size(), "Another attempt to resume the session must be started.");
        verify(routingTable, never().description("The routes of a session that is detached again must be kept.")).removeServerRoute(any(DomainPair.class));
    }

    /**
     * Verifies that a detached session that is not resumed within the server detach timeout is ended, and that each of
     * its stanzas is then handled once, as decision 4 of OF-2425 describes: the unacknowledged message is routed again,
     * the unacknowledged IQ request is returned as an error, the queued stanza is routed again, and the acknowledged
     * stanza is not handled.
     */
    @Test
    public void givingUpAfterServerDetachTimeoutHandlesEachStanzaOnce() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(1));
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);
        setDetachedSince(resumable, Instant.now().minus(Duration.ofMinutes(2)));

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session that was detached for longer than the server detach timeout must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a detached session is ended once the preferred maximum resumption time of the remote server passed,
     * when that is shorter than the server detach timeout, and that each of its stanzas is then handled once.
     */
    @Test
    public void givingUpAfterPeerMaximumResumptionTimeHandlesEachStanzaOnce() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(2));
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, "30");
        setDetachedSince(resumable, Instant.now().minus(Duration.ofMinutes(1)));

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A session that was detached for longer than the preferred maximum resumption time of the remote server must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a detached session is kept while the preferred maximum resumption time of the remote server has not
     * passed.
     */
    @Test
    public void detachedSessionIsKeptWithinPeerMaximumResumptionTime() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, "300");
        setDetachedSince(resumable, Instant.now().minus(Duration.ofMinutes(1)));

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertTrue(sessionManager.isDetached(resumable), "A session must be kept while neither the preferred maximum resumption time of the remote server nor the server detach timeout passed.");
        verify(packetRouter, never().description("No stanza of a session that is kept must be routed.")).route(any(Packet.class));
    }

    /**
     * Verifies that the time that an outgoing server session is kept while detached is the shorter of the server detach
     * timeout and the preferred maximum resumption time of the remote server, and that a negative server detach timeout
     * does not keep a session beyond the latter.
     */
    @Test
    public void detachTimeoutOfOutgoingSessionIsLimitedByPeerMaximumResumptionTime() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession withMaximum = resumableSession("30");
        final LocalOutgoingServerSession withoutMaximum = resumableSession(null);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(2));

        // Execute system under test.
        final Duration peerShorter = sessionManager.getDetachTimeout(withMaximum);
        final Duration noPeerMaximum = sessionManager.getDetachTimeout(withoutMaximum);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofSeconds(10));
        final Duration serverShorter = sessionManager.getDetachTimeout(withMaximum);

        // Verify result.
        assertEquals(Duration.ofSeconds(30), peerShorter, "The preferred maximum resumption time of the remote server must be used when it is shorter than the server detach timeout.");
        assertEquals(Duration.ofMinutes(2), noPeerMaximum, "The server detach timeout must be used when the remote server reported no preferred maximum resumption time.");
        assertEquals(Duration.ofSeconds(10), serverShorter, "The server detach timeout must be used when it is shorter than the preferred maximum resumption time of the remote server.");
    }

    /**
     * Verifies that a detached session is ended when the remote server refuses to resume its stream with
     * {@code <failed/>}, and that each of its stanzas is then handled once.
     */
    @Test
    public void givingUpOnFailedHandlesEachStanzaOnce() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), remoteServerThatRefuses(resumable, null));

        // Verify result.
        verify(packetRouter, timeout(5000).times(3).description("Each stanza of a session of which the stream is refused must be handled.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream is refused must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a detached session is ended once more stanzas are queued than the maximum allows, and that each of
     * its stanzas is then handled once.
     */
    @Test
    public void givingUpOnFullQueueHandlesEachStanzaOnce() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        RequestingStreamManager.MAX_QUEUED_WHILE_BROKEN.setValue(1);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        resumable.deliver(message("queued-2"));

        // Verify result.
        verify(packetRouter, timeout(5000).times(4).description("Each stanza of a session of which the queue is full must be handled.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session of which the queue is full must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "queued-2", "unacked-request"));
    }

    /**
     * Verifies that the stanzas that the remote server reports to have received in the 'h' of the {@code <failed/>}
     * with which it refuses to resume a stream (XEP-0198 § 5) are not routed again or returned as an error when the
     * session is ended.
     */
    @Test
    public void hOfFailedAcknowledgesStanzasBeforeFallback() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), remoteServerThatRefuses(resumable, "2"));

        // Verify result.
        verify(packetRouter, timeout(5000).times(2).description("The stanzas that the 'h' of <failed/> does not acknowledge must be handled.")).route(any(Packet.class));
        verify(packetRouter, after(200).times(2).description("The stanza that the 'h' of <failed/> acknowledges must not be handled.")).route(any(Packet.class));
        assertEachStanzaHandledOnce(packetRouter, List.of("queued", "unacked-request"));
    }

    /**
     * Verifies that an invalid 'h' of the {@code <failed/>} with which the remote server refuses to resume a stream, which
     * acknowledges stanzas that were not sent, is ignored: the session is ended, and all its stanzas are handled.
     */
    @Test
    public void invalidHOfFailedIsIgnored() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), remoteServerThatRefuses(resumable, "7"));

        // Verify result.
        verify(packetRouter, timeout(5000).times(3).description("Each stanza must be handled when the 'h' of <failed/> is invalid.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream is refused must be ended, also when the 'h' of <failed/> is invalid.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that the 'h' of a {@code <failed/>} in another namespace than that of the request to resume the stream is
     * ignored: the session is ended, and all its stanzas are handled.
     */
    @Test
    public void hOfFailedInOtherNamespaceIsIgnored() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);
        final Element failedInOtherNamespace = DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V2)).addAttribute("h", "2");

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), provider -> {
            provider.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3));
            provider.getStreamManager().process(failedInOtherNamespace);
        });

        // Verify result.
        verify(packetRouter, timeout(5000).times(3).description("Each stanza must be handled when <failed/> is in another namespace than the request.")).route(any(Packet.class));
        assertFalse(sessionManager.isDetached(resumable), "A session of which the stream is refused must be ended, also when <failed/> is in another namespace than the request.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a 'h' of {@code <failed/>} that is not a number is ignored, and that each stanza is handled.
     */
    @Test
    public void nonNumericHOfFailedIsIgnored() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), remoteServerThatRefuses(resumable, "many"));

        // Verify result.
        verify(packetRouter, timeout(5000).times(3).description("Each stanza must be handled when the 'h' of <failed/> is not a number.")).route(any(Packet.class));
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a session that is resumed, and detached again before the remote server acknowledged anything on the
     * resumed stream, is kept only until the detach timeout passes since it was first detached, so that a stream that
     * breaks each time it is resumed is not kept indefinitely.
     */
    @Test
    public void sessionDetachedAgainBeforeAcknowledgementKeepsDeadlineOfFirstDetach() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(2));
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));
        breakConnection(sessionManager, resumable);
        final Instant firstDetach = Instant.now().minus(Duration.ofMinutes(3));
        setDetachedSince(resumable, firstDetach);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));
        doReturn(true).when(newConnection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the session to be detached again.");

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertEquals(firstDetach, resumable.getDetachedSince(), "A session that is detached again before its resumed stream was acknowledged must be kept while detached from its first detach.");
        assertFalse(sessionManager.isDetached(resumable), "A session that is detached again before its resumed stream was acknowledged must be ended once the detach timeout passed since its first detach.");
        assertEquals(List.of("unacked"), routed(packetRouter).stream().map(Packet::getID).toList(), "The stanza that was sent again on the resumed stream must be handled once.");
        assertEquals(IQ.Type.error.toString(), routed(packetRouter).get(0).getElement().attributeValue("type"), "A stanza that was sent again on the resumed stream, and is unacknowledged again, must be returned as an error (decision 4).");
    }

    /**
     * Verifies that a session that is resumed, and detached again after the remote server acknowledged stanzas on the
     * resumed stream, gets a new detach timeout, measured from that detach.
     */
    @Test
    public void sessionDetachedAgainAfterAcknowledgementGetsNewDeadline() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        SessionManager.SERVER_SESSION_DETACH_TIMEOUT.setValue(Duration.ofMinutes(2));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final Instant firstDetach = Instant.now().minus(Duration.ofMinutes(3));
        setDetachedSince(resumable, firstDetach);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));
        resumable.deliver(message("after"));
        resumable.getStreamManager().process(ack("1"));
        doReturn(true).when(newConnection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the session to be detached again.");

        // Execute system under test.
        runDetachedCleanupTask(sessionManager);

        // Verify result.
        assertTrue(resumable.getDetachedSince().isAfter(firstDetach), "A session that is detached again after its resumed stream was acknowledged must be kept while detached from its new detach.");
        assertTrue(sessionManager.isDetached(resumable), "A session that is detached again after its resumed stream was acknowledged must be kept within the detach timeout since its new detach.");
    }

    /**
     * Verifies that an acknowledgement is requested on a resumed stream, also when no stanza is sent on it, so that the
     * remote server confirms that the resumed stream works.
     */
    @Test
    public void acknowledgementIsRequestedOnResumedStream() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);
        final LocalOutgoingServerSession provider = newSessionForResumption("stream-new");
        final Connection newConnection = provider.getConnection();

        // Execute system under test.
        runResumptionAttempt(provider, remoteServerThatResumes(resumable, "0"));

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(newConnection, atLeastOnce().description("Expected raw text to be delivered on the new connection.")).deliverRawText(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(text -> text.startsWith("<r ")), "An acknowledgement must be requested on the resumed stream, but this was delivered: " + captor.getAllValues());
        assertFalse(resumable.getStreamManager().isAcknowledgedSinceResumption(), "The resumed stream must not be taken to work before the remote server acknowledged anything on it.");
    }

    /**
     * Verifies that another attempt to resume a detached session is made when the remote server does not answer the
     * request to resume its stream in time, while the detach timeout has not passed.
     */
    @Test
    public void unansweredRequestToResumeIsMadeAgain() throws Exception
    {
        // Setup test fixture.
        LocalOutgoingServerSession.STREAM_MANAGEMENT_NEGOTIATION_TIMEOUT.setValue(Duration.ofMillis(100));
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession resumable = resumableSession();
        breakConnection(sessionManager, resumable);

        // Execute system under test.
        runResumptionAttempt(newSessionForResumption("stream-new"), p -> p.getStreamManager().getResumeRequest().send(resumable, List.of(StreamManager.NAMESPACE_V3)));

        // Verify result.
        assertTrue(sessionManager.isDetached(resumable), "A session must stay detached while another attempt to resume it can be made.");
        assertEquals(List.of(Duration.ofSeconds(1)), retryDelays, "Another attempt must be scheduled after a request that was not answered in time.");
        verify(initialisers.get(0), times(1).description("The connection of the attempt that was not answered must be closed.")).stop();
    }

    /**
     * Verifies that a detached session that Openfire closes on purpose is ended now, rather than kept until its detach
     * timeout, and that each of its stanzas is then handled once.
     */
    @Test
    public void detachedSessionThatIsClosedOnPurposeIsEnded() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);

        // Execute system under test.
        sessionManager.closeWithoutResumption(resumable);

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A detached session that is closed on purpose must be ended.");
        assertFalse(resumable.getStreamManager().getResume(), "A session that is closed on purpose must not be resumable.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a session with an open connection that Openfire closes on purpose is not detached when its
     * connection closes, but ended, as its stream is made non-resumable before the connection is closed.
     */
    @Test
    public void connectedSessionThatIsClosedOnPurposeIsNotDetached() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));

        // Execute system under test.
        sessionManager.closeWithoutResumption(resumable);
        doReturn(true).when(connection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);

        // Verify result.
        verify(connection, atLeastOnce().description("The connection of a session that is closed on purpose must be closed.")).close();
        assertFalse(sessionManager.isDetached(resumable), "A session that is closed on purpose must not be detached when its connection closes.");
        assertEquals(List.of(), resumptionAttempts, "No attempt must be made to resume a session that is closed on purpose.");
        verify(routingTable, times(1).description("The route of a session that is closed on purpose must be removed.")).removeServerRoute(DOMAIN_PAIR);
        assertEquals(List.of("unacked"), routed(packetRouter).stream().map(Packet::getID).toList(), "The unacknowledged stanza of a session that is closed on purpose must be routed again.");
    }

    /**
     * Verifies that a session that Openfire closes on purpose while its connection was closing already, and that the
     * close listener of that connection detaches afterwards, as it found the session resumable before it was made
     * non-resumable, is ended once the connection has closed, rather than kept until its detach timeout.
     */
    @Test
    public void sessionDetachedByClosingConnectionAfterCloseOnPurposeIsEnded() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = resumableSession();
        resumable.deliver(message("unacked"));

        // Execute system under test.
        sessionManager.closeWithoutResumption(resumable);
        // As the close listener does when it found the session resumable before closeWithoutResumption made it non-resumable.
        doReturn(true).when(connection).isClosed();
        resumable.setDetached();
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the close listener to have detached the session.");
        closeFuture.complete(null);

        // Verify result.
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sessionManager.isDetached(resumable) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(sessionManager.isDetached(resumable), "A session that is detached after it was closed on purpose must be ended once its connection has closed.");
        verify(packetRouter, timeout(5000).times(1).description("The unacknowledged stanza of the ended session must be routed again.")).route(argThat((Packet packet) -> "unacked".equals(packet.getID())));
    }

    /**
     * Verifies the time that a detached session is still kept: what remains of its detach timeout since it was detached,
     * and zero once that has passed.
     */
    @Test
    public void timeLeftWhileDetachedIsWhatRemainsOfDetachTimeout() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useSessionManager(mock(PacketRouter.class));
        final LocalOutgoingServerSession detached = detachedSession(sessionManager);
        final Duration timeout = sessionManager.getDetachTimeout(detached);
        assertTrue(timeout.compareTo(Duration.ofSeconds(60)) > 0, "Test fixture expects a detach timeout of more than a minute, but it is " + timeout);

        // Execute system under test.
        setDetachedSince(detached, Instant.now().minus(timeout).plusSeconds(30));
        final Duration leftBefore = detached.getTimeLeftWhileDetached();
        setDetachedSince(detached, Instant.now().minus(timeout).minusSeconds(1));
        final Duration leftAfter = detached.getTimeLeftWhileDetached();

        // Verify result.
        assertNotNull(leftBefore, "A detached session with a detach timeout must report the time that it is still kept.");
        assertTrue(leftBefore.compareTo(Duration.ofSeconds(25)) > 0 && leftBefore.compareTo(Duration.ofSeconds(30)) <= 0, "The time left must be what remains of the detach timeout, about 30 seconds, but was " + leftBefore);
        assertEquals(Duration.ZERO, leftAfter, "No time must be left once the detach timeout has passed.");
    }

    /**
     * Verifies that a detached session that another cluster node asks to close is ended now, rather than kept until its
     * detach timeout.
     */
    @Test
    public void detachedSessionThatAnotherClusterNodeClosesIsEnded() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);
        doReturn(resumable).when(routingTable).getServerRoute(DOMAIN_PAIR);
        final OutgoingServerSessionTask task = new OutgoingServerSessionTask(DOMAIN_PAIR, RemoteSessionTask.Operation.close);

        // Execute system under test.
        task.run();

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A detached session that another cluster node closes must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Verifies that a detached session of a remote domain that a changed permission policy no longer allows is ended
     * now, rather than kept until its detach timeout.
     */
    @Test
    public void detachedSessionOfDomainThatIsNoLongerAllowedIsEnded() throws Exception
    {
        // Setup test fixture.
        final PacketRouter packetRouter = mock(PacketRouter.class);
        final SessionManager sessionManager = useSessionManager(packetRouter);
        final LocalOutgoingServerSession resumable = brokenSessionWithStanzas(sessionManager, null);
        doReturn(resumable).when(routingTable).getServerRoute(DOMAIN_PAIR);
        doReturn(List.of(DOMAIN_PAIR)).when(routingTable).getServerRoutes();

        // Execute system under test.
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class, CALLS_REAL_METHODS)) {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(false);
            RemoteServerManager.setPermissionPolicy(RemoteServerManager.PermissionPolicy.whitelist);
        }

        // Verify result.
        assertFalse(sessionManager.isDetached(resumable), "A detached session of a remote domain that is no longer allowed must be ended.");
        assertEachStanzaHandledOnce(packetRouter, List.of("unacked-message", "queued", "unacked-request"));
    }

    /**
     * Creates a session as {@link #resumableSession(String)} does, with the provided preferred maximum resumption time of
     * the remote server, on which a stanza is acknowledged, a message and an IQ request are unacknowledged, of which the
     * connection breaks, and on which a stanza is queued after that.
     */
    private LocalOutgoingServerSession brokenSessionWithStanzas(final SessionManager sessionManager, final String max) throws Exception
    {
        final LocalOutgoingServerSession resumable = resumableSession(max);
        resumable.deliver(message("acked"));
        resumable.getStreamManager().process(ack("1"));
        resumable.deliver(message("unacked-message"));
        resumable.deliver(iq("unacked-request", IQ.Type.get));
        breakConnection(sessionManager, resumable);
        resumable.deliver(message("queued"));
        return resumable;
    }

    /**
     * Verifies that exactly the stanzas with the provided IDs were routed, each once, in order, and that an IQ request
     * among them was returned to its sender as an error while the others were routed again (decision 4 of OF-2425). The
     * errors are routed after the stanzas that are routed again.
     */
    private void assertEachStanzaHandledOnce(final PacketRouter packetRouter, final List<String> expectedIds)
    {
        final List<Packet> routed = routed(packetRouter);
        assertEquals(expectedIds, routed.stream().map(Packet::getID).toList(), "Exactly these stanzas must be handled, each once, in order, the errors last.");
        for (final Packet packet : routed) {
            if (packet instanceof IQ iq) {
                assertEquals(IQ.Type.error, iq.getType(), "An unacknowledged IQ request must be returned to its sender as an error: " + iq.getID());
            } else {
                assertNull(packet.getError(), "A message must be routed again, not returned as an error: " + packet.getID());
            }
        }
        verify(routingTable, times(1).description("The route of a session that is ended must be removed once.")).removeServerRoute(DOMAIN_PAIR);
    }

    /**
     * Returns what the stanza handler of a new connection and a remote server that refuses to resume the stream of the
     * provided session do: the handler asks to resume the stream, and the remote server answers with {@code <failed/>},
     * with the provided value of 'h', if any.
     */
    private static Consumer<LocalOutgoingServerSession> remoteServerThatRefuses(final LocalOutgoingServerSession target, final String h)
    {
        return provider -> {
            provider.getStreamManager().getResumeRequest().send(target, List.of(StreamManager.NAMESPACE_V3));
            provider.getStreamManager().process(failed(h));
        };
    }

    private static Element failed(final String h)
    {
        final Element failed = DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3));
        failed.addElement(QName.get("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas"));
        if (h != null) {
            failed.addAttribute("h", h);
        }
        return failed;
    }

    /**
     * Breaks the connection of the provided session that can be resumed, as the connection does when it closes, and has
     * the close listener of the provided session manager detach it, which starts an attempt to resume it.
     */
    private void breakConnection(final SessionManager sessionManager, final LocalOutgoingServerSession target) throws Exception
    {
        doReturn(true).when(connection).isClosed();
        // As NettyConnection#close does, which clears the authentication method.
        target.setStatus(Session.Status.CLOSED);
        fireOutgoingConnectionClose(sessionManager, target);
        assertTrue(sessionManager.isDetached(target), "Test fixture expects the session to be detached.");
        assertNull(target.getAuthenticationMethod(), "Test fixture expects the authentication method to be cleared when the connection closed.");
    }

    /**
     * Creates an outgoing session for {@link #DOMAIN_PAIR} on a mocked connection of its own, authenticated with dialback,
     * as the session of a new connection on which a detached session is resumed.
     */
    private static LocalOutgoingServerSession newSessionForResumption(final String streamId)
    {
        final Connection newConnection = mock(Connection.class, withSettings().lenient());
        doReturn(new CompletableFuture<Void>()).when(newConnection).getCloseFuture();
        final LocalOutgoingServerSession newSession = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, newConnection, BasicStreamIDFactory.createStreamID(streamId));
        newSession.setAddress(new JID(null, REMOTE_DOMAIN, null));
        newSession.setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
        return newSession;
    }

    /**
     * Returns what the stanza handler of a new connection and a remote server that resumes the stream of the provided
     * session do: the handler asks to resume the stream, and the remote server answers with the provided value of 'h'.
     */
    private static Consumer<LocalOutgoingServerSession> remoteServerThatResumes(final LocalOutgoingServerSession target, final String h)
    {
        return provider -> {
            provider.getStreamManager().getResumeRequest().send(target, List.of(StreamManager.NAMESPACE_V3));
            provider.getStreamManager().process(resumed("sm-id-1", h));
        };
    }

    /**
     * Runs the one attempt to resume a session that was started, where opening a new connection gives the provided
     * session, after what the stanza handler of that connection and the remote server do, which the provided consumer
     * simulates. A null session simulates a connection that could not be authenticated.
     */
    private void runResumptionAttempt(final LocalOutgoingServerSession provider, final Consumer<LocalOutgoingServerSession> handlerAndRemoteServer)
    {
        runResumptionAttempt(provider, handlerAndRemoteServer, CompletableFuture.completedFuture(provider));
    }

    /**
     * Runs the one attempt to resume a session that was started, as {@link #runResumptionAttempt(LocalOutgoingServerSession, Consumer)}
     * does, where opening the new connection gives the provided result instead of the provided session, for example
     * one that fails as the connection is not authenticated in time.
     */
    private void runResumptionAttempt(final LocalOutgoingServerSession provider, final Consumer<LocalOutgoingServerSession> handlerAndRemoteServer, final CompletableFuture<LocalSession> initialiseResult)
    {
        assertEquals(1, resumptionAttempts.size(), "Test fixture expects one attempt to resume the session to be started.");
        final Runnable attempt = resumptionAttempts.remove(0);
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedConstruction<NettySessionInitializer> constructed = mockConstruction(NettySessionInitializer.class,
                 (initialiser, context) -> {
                     initialiserArguments.add(new ArrayList<>(context.arguments())); // May hold null, which List.copyOf refuses.
                     doAnswer(invocation -> {
                         if (provider != null) {
                             handlerAndRemoteServer.accept(provider);
                         }
                         return initialiseResult;
                     }).when(initialiser).init(any());
                     doAnswer(invocation -> abandonResumeAsHandlerDoes(provider)).when(initialiser).abandonResume();
                 }))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            remoteServers.when(() -> RemoteServerManager.getPortForServer(anyString())).thenReturn(5269);

            attempt.run();
            initialisers.addAll(constructed.constructed());
        }
    }

    /**
     * Gives up on the request to resume a stream on the provided session, as the stanza handler of its connection does
     * (see {@link org.jivesoftware.openfire.net.RespondingServerStanzaHandler#abandonResume()}).
     *
     * @return false if the request was answered already, otherwise true.
     */
    private static boolean abandonResumeAsHandlerDoes(final LocalOutgoingServerSession provider)
    {
        if (provider == null) {
            return true;
        }
        final RequestingStreamManager streamManager = provider.getStreamManager();
        return streamManager.getResumeRequest().abandon() || streamManager.getResumeRequest().getStatus() != OutboundResumeRequest.Status.ANSWERED;
    }

    private static Element resumed(final String previd, final String h)
    {
        return DocumentHelper.createElement(QName.get("resumed", StreamManager.NAMESPACE_V3)).addAttribute("previd", previd).addAttribute("h", h);
    }

    /**
     * Returns the IDs of the stanzas that were delivered on the provided connection, in order.
     */
    private static List<String> deliveredIds(final Connection target) throws Exception
    {
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(target, atLeast(0)).deliver(captor.capture());
        return captor.getAllValues().stream().map(Packet::getID).toList();
    }

    /**
     * Returns the last request to resume a stream that was delivered on the provided connection.
     */
    private static Element lastResumeRequest(final Connection target) throws Exception
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(target, atLeastOnce().description("Expected raw text to be delivered on the connection.")).deliverRawText(captor.capture());
        Element result = null;
        for (final String text : captor.getAllValues()) {
            if (text.startsWith("<resume")) {
                result = DocumentHelper.parseText(text).getRootElement();
            }
        }
        assertNotNull(result, "Expected a request to resume a stream to be delivered on the connection, but these were delivered: " + captor.getAllValues());
        return result;
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
     * Creates a session manager, initialised with the mocked server, routing table and the provided packet router,
     * which the sessions that are created after this use.
     */
    private SessionManager useSessionManager(final PacketRouter packetRouter)
    {
        doReturn(packetRouter).when(xmppServer).getPacketRouter();
        final SessionManager sessionManager = new SessionManager();
        sessionManager.initialize(xmppServer);
        doReturn(sessionManager).when(xmppServer).getSessionManager();
        return sessionManager;
    }

    /**
     * Creates an authenticated outgoing session for the remote domain on {@link #connection}.
     */
    private LocalOutgoingServerSession newSession(final String streamId)
    {
        final LocalOutgoingServerSession newSession = new LocalOutgoingServerSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(streamId));
        newSession.setAddress(new JID(null, REMOTE_DOMAIN, null));
        newSession.setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
        return newSession;
    }

    /**
     * Creates an authenticated outgoing session on {@link #connection} that is the route for {@link #DOMAIN_PAIR}, on
     * which the remote server enabled Stream Management with a stream that can be resumed, with resumption on server
     * connections enabled. The interaction with the connection and the routing table is cleared afterwards.
     */
    private LocalOutgoingServerSession resumableSession()
    {
        return resumableSession(null);
    }

    /**
     * Creates a session as {@link #resumableSession()} does, of which the remote server reported the provided preferred
     * maximum resumption time, in seconds, if any.
     */
    private LocalOutgoingServerSession resumableSession(final String max)
    {
        return resumableSession(max, null);
    }

    /**
     * Creates a session as {@link #resumableSession()} does, of which the remote server reported the provided preferred
     * maximum resumption time, in seconds, and location for reconnecting, if any.
     */
    private LocalOutgoingServerSession resumableSession(final String max, final String location)
    {
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final LocalOutgoingServerSession resumable = newSession("stream-resumable");
        resumable.addOutgoingDomainPair(DOMAIN_PAIR);
        resumable.getStreamManager().requestEnable(StreamManager.NAMESPACE_V3);
        final Element enabled = DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true").addAttribute("id", "sm-id-1");
        if (max != null) {
            enabled.addAttribute("max", max);
        }
        if (location != null) {
            enabled.addAttribute("location", location);
        }
        resumable.getStreamManager().process(enabled);
        assertTrue(resumable.getStreamManager().getResume(), "Test fixture expects the stream to be resumable.");
        clearInvocations(connection, routingTable);
        return resumable;
    }

    /**
     * Creates a session as {@link #resumableSession()} does, and detaches it by closing its connection, through the
     * close listener of the provided session manager.
     */
    private LocalOutgoingServerSession detachedSession(final SessionManager sessionManager) throws Exception
    {
        final LocalOutgoingServerSession resumable = resumableSession();
        doReturn(true).when(connection).isClosed();
        fireOutgoingConnectionClose(sessionManager, resumable);
        assertTrue(sessionManager.isDetached(resumable), "Test fixture expects the session to be detached.");
        return resumable;
    }

    /**
     * Sets the moment at which the provided session was most recently detached, and the moment from which the time that
     * it is kept while detached is measured, so that a test can have a session that was detached in the past.
     */
    private static void setDetachedSince(final LocalOutgoingServerSession target, final Instant moment) throws Exception
    {
        final Field lastDetachedDate = LocalSession.class.getDeclaredField("lastDetachedDate");
        lastDetachedDate.setAccessible(true);
        lastDetachedDate.set(target, moment);
        final Field detachedSince = LocalOutgoingServerSession.class.getDeclaredField("detachedSince");
        detachedSince.setAccessible(true);
        detachedSince.set(target, moment);
    }

    /**
     * Runs the private task of the provided session manager that ends detached sessions once.
     */
    private static void runDetachedCleanupTask(final SessionManager sessionManager) throws Exception
    {
        final Class<?> taskClass = Class.forName(SessionManager.class.getName() + "$DetachedCleanupTask");
        final Constructor<?> constructor = taskClass.getDeclaredConstructor(SessionManager.class);
        constructor.setAccessible(true);
        ((Runnable) constructor.newInstance(sessionManager)).run();
    }

    /**
     * Invokes the private close listener for outgoing server sessions of the provided session manager for
     * {@link #session}, and waits for it to complete.
     */
    private void fireOutgoingConnectionClose(final SessionManager sessionManager) throws Exception
    {
        fireOutgoingConnectionClose(sessionManager, session);
    }

    /**
     * Invokes the private close listener for outgoing server sessions of the provided session manager for the provided
     * session, and waits for it to complete.
     */
    private static void fireOutgoingConnectionClose(final SessionManager sessionManager, final LocalOutgoingServerSession target) throws Exception
    {
        final Field listenerField = SessionManager.class.getDeclaredField("outgoingServerListener");
        listenerField.setAccessible(true);
        final Object listener = listenerField.get(sessionManager);

        final Method method = listener.getClass().getDeclaredMethod("onConnectionClosing", Object.class);
        method.setAccessible(true);

        final Object result = method.invoke(listener, target);
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
        return authenticateDomain(DOMAIN_PAIR, session);
    }

    /**
     * Invokes {@link #authenticateDomain()} on another thread, and returns once that thread waits for negotiation of
     * Stream Management on {@link #session} to finish, failing when it does not wait within ten seconds. A test that
     * answers only then knows that authentication waited for the answer, rather than that the answer came first.
     */
    private CompletableFuture<DomainAuthResult> authenticateDomainUntilNegotiationIsAwaited()
    {
        final CompletableFuture<Thread> authenticating = new CompletableFuture<>();
        final CompletableFuture<DomainAuthResult> authentication = CompletableFuture.supplyAsync(() -> {
            authenticating.complete(Thread.currentThread());
            return authenticateDomain();
        });
        backgroundAuthentications.add(authentication);
        final Thread thread = authenticating.join();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!(thread.getState() == Thread.State.TIMED_WAITING && Arrays.stream(thread.getStackTrace()).anyMatch(frame -> frame.getMethodName().equals("awaitStreamManagementNegotiation")))) {
            assertFalse(authentication.isDone(), "Test fixture expects authentication to wait for negotiation of Stream Management, but it finished.");
            assertTrue(System.nanoTime() < deadline, "Test fixture expects authentication to wait for negotiation of Stream Management within ten seconds.");
            Thread.onSpinWait();
        }
        return authentication;
    }

    /**
     * Invokes {@link LocalOutgoingServerSession#authenticateDomain(DomainPair)} for the provided domain pair, where
     * opening a new connection gives the provided session.
     */
    private DomainAuthResult authenticateDomain(final DomainPair domainPair, final LocalOutgoingServerSession newConnectionSession)
    {
        try (final MockedStatic<RemoteServerManager> remoteServers = mockStatic(RemoteServerManager.class);
             final MockedConstruction<NettySessionInitializer> constructed = mockConstruction(NettySessionInitializer.class,
                 (initialiser, context) -> doReturn(CompletableFuture.completedFuture(newConnectionSession)).when(initialiser).init(any())))
        {
            remoteServers.when(() -> RemoteServerManager.canAccess(anyString())).thenReturn(true);
            remoteServers.when(() -> RemoteServerManager.getPortForServer(anyString())).thenReturn(5269);

            final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(domainPair);
            initialisers.addAll(constructed.constructed());
            return result;
        }
    }

    /**
     * Waits until a thread waits to obtain the lock of an object, failing when it ends before that, or does not wait
     * within ten seconds.
     */
    private static void awaitBlockedOn(final Thread thread, final Object monitor)
    {
        final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            final ThreadInfo info = threads.getThreadInfo(thread.getId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED && info.getLockInfo() != null && info.getLockInfo().getIdentityHashCode() == System.identityHashCode(monitor)) {
                return;
            }
            assertTrue(thread.isAlive(), "Test fixture expects the thread to wait for the lock, but it ended.");
            assertTrue(System.nanoTime() < deadline, "Test fixture expects the thread to wait for the lock within ten seconds.");
            Thread.onSpinWait();
        }
    }
}
