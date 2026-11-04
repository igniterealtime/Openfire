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
package org.jivesoftware.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.IQResultListenerTask;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.keystore.KeystoreTestUtils;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.server.s2stest.LogSummary;
import org.jivesoftware.openfire.server.s2stest.PingOutcome;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.RemoteOutgoingServerSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the parts of {@link S2STestService} that the checklist relies on: how the reply to the ping is captured
 * and classified, the certificate output, and the captured log lines.
 *
 * @author Matthew Vivian
 */
public class S2STestServiceTest
{
    private static final String LOCAL = "example.com";
    private static final String REMOTE = "remote.example.org";

    private IQ ping;
    private S2STestService.PingInterceptor interceptor;
    private S2STestService.PingReplyListener listener;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        ping = new IQ(IQ.Type.get, "s2s-test-ping");
        ping.setChildElement("ping", IQPingHandler.NAMESPACE);
        ping.setFrom(LOCAL);
        ping.setTo(REMOTE);
        interceptor = new S2STestService.PingInterceptor(ping);
        listener = new S2STestService.PingReplyListener();
    }

    private IQ replyTo(final IQ request, final IQ.Type type)
    {
        final IQ reply = new IQ(type, request.getID());
        reply.setFrom(REMOTE);
        reply.setTo(LOCAL);
        return reply;
    }

    @Test
    public void testPingSentOverAnOutgoingServerSessionIsRecorded() throws Exception
    {
        // Execute system under test: the ping is delivered over the outgoing server session.
        interceptor.interceptPacket(ping, mock(OutgoingServerSession.class), false, false);
        interceptor.interceptPacket(ping, mock(OutgoingServerSession.class), false, true);

        // Verify results.
        assertTrue(interceptor.isPingSent(), "a ping delivered over the outgoing server session must count as sent to the remote");
    }

    @Test
    public void testPingHandedToAClosingSessionIsNotSent() throws Exception
    {
        // Setup test fixture: the existing session was closed by the test, but its route is not removed yet, so the
        // router hands it the ping. The session only schedules the ping for redelivery, and does not send it.
        final OutgoingServerSession closing = mock(OutgoingServerSession.class);
        when(closing.isClosed()).thenReturn(true);

        // Execute system under test.
        interceptor.interceptPacket(ping, closing, false, false);
        interceptor.interceptPacket(ping, closing, false, true);

        // Verify results.
        assertFalse(interceptor.isPingSent(), "a ping handed to a closed session has not reached the remote, so an error that follows must be read as this server's own");
    }

    @Test
    public void testClosingWaitsForTheTeardownOfALocalSession() throws Exception
    {
        // Setup test fixture: the connection finishes its teardown (including removal of the route) some time after
        // it is closed.
        final CompletableFuture<Void> teardown = new CompletableFuture<>();
        final Connection connection = mock(Connection.class);
        when(connection.getCloseFuture()).thenReturn(teardown);
        final LocalOutgoingServerSession session = mock(LocalOutgoingServerSession.class);
        when(session.getConnection()).thenReturn(connection);
        doAnswer(invocation -> {
            CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> teardown.complete(null));
            return null;
        }).when(session).close();

        // Execute system under test.
        S2STestService.closeAndAwaitTeardown(session, Duration.ofSeconds(10));

        // Verify results.
        verify(session, times(1).description("the existing session must be closed")).close();
        assertTrue(teardown.isDone(), "the ping must not be routed before the closed session's route is removed, or it can be handed to that session");
    }

    @Test
    public void testClosingGivesUpWaitingAfterTheBound() throws Exception
    {
        // Setup test fixture: a teardown that never finishes.
        final Connection connection = mock(Connection.class);
        when(connection.getCloseFuture()).thenReturn(new CompletableFuture<>());
        final LocalOutgoingServerSession session = mock(LocalOutgoingServerSession.class);
        when(session.getConnection()).thenReturn(connection);
        final Duration bound = Duration.ofMillis(100);

        // Execute system under test.
        final long start = System.nanoTime();
        S2STestService.closeAndAwaitTeardown(session, bound);
        final Duration waited = Duration.ofNanos(System.nanoTime() - start);

        // Verify results.
        verify(session, times(1).description("the existing session must be closed")).close();
        assertTrue(waited.compareTo(bound) >= 0, "the test must wait for the teardown up to the bound");
        assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0, "a teardown that does not finish must not hold up the test beyond the bound");
    }

    @Test
    public void testClosingASessionOnAnotherNodeDoesNotWait() throws Exception
    {
        // Setup test fixture: a session held by another cluster node has no connection on this one.
        final RemoteOutgoingServerSession session = mock(RemoteOutgoingServerSession.class);

        // Execute system under test.
        final long start = System.nanoTime();
        S2STestService.closeAndAwaitTeardown(session, Duration.ofSeconds(10));
        final Duration waited = Duration.ofNanos(System.nanoTime() - start);

        // Verify results.
        verify(session, times(1).description("the existing session must be closed, on whichever node holds it")).close();
        assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0, "there is no local teardown to wait for");
    }

    @Test
    public void testPingRoutedOnlyWithinThisServerIsNotSent() throws Exception
    {
        // Execute system under test: the ping passes through the router, but no session delivers it.
        interceptor.interceptPacket(ping, null, true, false);
        interceptor.interceptPacket(ping, null, true, true);

        // Verify results.
        assertFalse(interceptor.isPingSent(), "a ping that only passed through this server's router has not reached the remote");
    }

    @Test
    public void testPingDeliveredToAClientIsNotSentToTheRemote() throws Exception
    {
        // Execute system under test.
        interceptor.interceptPacket(ping, mock(ClientSession.class), false, true);

        // Verify results.
        assertFalse(interceptor.isPingSent(), "only delivery over an outgoing server session counts as sent to the remote");
    }

    @Test
    public void testPingIsRegisteredForItsReplyBeforeItIsRouted()
    {
        // Setup test fixture.
        final IQRouter router = mock(IQRouter.class);
        final List<String> calls = new ArrayList<>();
        doAnswer(invocation -> calls.add("listen")).when(router).addIQResultListener(any(), any(), anyLong());
        doAnswer(invocation -> calls.add("route")).when(router).route(any(IQ.class));

        // Execute system under test.
        final S2STestService.PingReplyListener registered = S2STestService.sendPing(router, ping, Duration.ofSeconds(30));

        // Verify results.
        verify(router, description("the reply must be listened for under the ping's ID, for as long as the test waits")).addIQResultListener("s2s-test-ping", registered, 30_000L);
        verify(router, description("the ping itself must be routed")).route(ping);
        assertEquals(List.of("listen", "route"), calls, "the ping must be routed only once its reply is listened for, or a quick reply could be missed");
    }

    @Test
    public void testReplyReachingTheListenerReleasesTheWait() throws Exception
    {
        // Setup test fixture.
        final IQ reply = replyTo(ping, IQ.Type.result);

        // Execute system under test.
        listener.receivedAnswer(reply);
        final long start = System.nanoTime();
        final IQ received = listener.awaitReply(Duration.ofSeconds(10));

        // Verify results.
        assertSame(reply, received, "the reply to the ping must be taken");
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0, "the reply must end the test's wait");
    }

    @Test
    public void testAnswerTimeoutReleasesTheWait() throws Exception
    {
        // Execute system under test.
        listener.answerTimeout(ping.getID());
        final long start = System.nanoTime();
        final IQ received = listener.awaitReply(Duration.ofSeconds(10));

        // Verify results.
        assertNull(received, "no reply arrived before the router gave up on it");
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0, "the router giving up on the reply must end the test's wait");
    }

    @Test
    public void testReplyArrivingAfterTheFirstWaitIsStillTaken() throws Exception
    {
        // Setup test fixture: the first wait for the reply runs out, as when a slow address used up the reply wait.
        assertNull(listener.awaitReply(Duration.ofMillis(10)), "no reply has arrived yet");
        final IQ reply = replyTo(ping, IQ.Type.result);

        // Execute system under test.
        listener.receivedAnswer(reply);
        final IQ received = listener.awaitReply(Duration.ofSeconds(5));

        // Verify results.
        assertSame(reply, received, "a reply arriving after the first wait ran out must still reach a later wait");
    }

    @Test
    public void testRouterKeepsListeningLongerThanEveryWaitOfTheTest()
    {
        // Setup test fixture.
        final Duration replyWait = Duration.ofSeconds(30);

        // Execute system under test.
        final Duration listening = S2STestService.replyListeningTime(replyWait);

        // Verify results.
        assertTrue(listening.compareTo(replyWait.plus(S2STestService.ATTEMPT_GRACE).plus(S2STestService.LATE_REPLY_WAIT)) >= 0,
            "the router must keep listening for the reply until the test stops waiting for it, or a late reply is dropped: " + listening);
    }

    @Test
    public void testTestWaitsLongerForTheReplyWhenTheSessionWasSetUpAfterTheReplyWait()
    {
        assertEquals(S2STestService.LATE_REPLY_WAIT, S2STestService.lateReplyWait(false, AttemptRecorder.Outcome.NEW_SESSION),
            "a session set up after the reply wait ran out has only just sent the ping, so its reply must be given time to arrive");
    }

    @Test
    public void testTestDoesNotWaitLongerOtherwise()
    {
        assertEquals(Duration.ZERO, S2STestService.lateReplyWait(true, AttemptRecorder.Outcome.NEW_SESSION),
            "a session set up within the reply wait had the whole wait for its reply");
        assertEquals(Duration.ZERO, S2STestService.lateReplyWait(false, AttemptRecorder.Outcome.FAILED),
            "a failed attempt sent no ping, so no reply is coming");
        assertEquals(Duration.ZERO, S2STestService.lateReplyWait(false, AttemptRecorder.Outcome.REUSED_SESSION),
            "a reused session sent the ping at once, so the reply wait was not used up by the connection");
        assertEquals(Duration.ZERO, S2STestService.lateReplyWait(false, AttemptRecorder.Outcome.IN_PROGRESS),
            "an attempt still in progress after the grace period is not waited for any longer");
    }

    @Test
    public void testAnswerWithThePingsIdIsTakenAsTheReply() throws Exception
    {
        // Setup test fixture: the router has already removed the listener when it passes on a response with the ping's
        // ID, so the listener gets no second answer: whatever it is passed is the reply, whichever address it is from.
        final IQ answer = new IQ(IQ.Type.result, ping.getID());
        answer.setFrom("other.example.net");
        answer.setTo(LOCAL);

        // Execute system under test.
        listener.receivedAnswer(answer);
        final IQ received = listener.awaitReply(Duration.ofMillis(100));

        // Verify results.
        assertSame(answer, received, "the response the router matched to the ping's ID must be taken as the reply, as the listener receives no other");
    }

    @Test
    public void testReplyRoutedOnThisNodeReachesTheListener() throws Exception
    {
        final XMPPServer previous = XMPPServer.getInstance();
        try {
            // Setup test fixture: a router as on a running server, with clustering off.
            final IQRouter router = startedRouter();
            router.addIQResultListener(ping.getID(), listener, 30_000L);
            final IQ reply = replyTo(ping, IQ.Type.result);

            // Execute system under test.
            router.route(reply);

            // Verify results.
            assertSame(reply, listener.awaitReply(Duration.ofSeconds(5)), "a reply that arrives on this node must reach the test");
        } finally {
            XMPPServer.setInstance(previous);
        }
    }

    @Test
    public void testReplyForwardedFromAnotherNodeReachesTheListener() throws Exception
    {
        final XMPPServer previous = XMPPServer.getInstance();
        try {
            // Setup test fixture: the remote's connection to this cluster ends on another node, which forwards the
            // reply to the node that registered for it.
            final IQRouter router = startedRouter();
            router.addIQResultListener(ping.getID(), listener, 30_000L);
            final IQ reply = replyTo(ping, IQ.Type.result);

            // Execute system under test.
            new IQResultListenerTask(reply).run();

            // Verify results.
            final IQ received = listener.awaitReply(Duration.ofSeconds(5));
            assertNotNull(received, "a reply forwarded from another node must reach the test");
            assertEquals(reply.toXML(), received.toXML(), "the forwarded reply must be the remote's reply");
        } finally {
            XMPPServer.setInstance(previous);
        }
    }

    @Test
    public void testOwnErrorRoutedOnThisNodeReachesTheListenerAndIsClassifiedAsLocal() throws Exception
    {
        final XMPPServer previous = XMPPServer.getInstance();
        try {
            // Setup test fixture: no session could be made, so this server routes its own error on the remote's behalf,
            // as OutgoingSessionPromise does.
            final IQRouter router = startedRouter();
            router.addIQResultListener(ping.getID(), listener, 30_000L);
            final IQ ownError = replyTo(ping, IQ.Type.error);
            ownError.setError(PacketError.Condition.remote_server_not_found);

            // Execute system under test.
            router.route(ownError);
            final PingOutcome outcome = PingOutcome.fromReply(listener.awaitReply(Duration.ofSeconds(5)), interceptor.isPingSent());

            // Verify results.
            assertEquals(PingOutcome.localError("remote-server-not-found"), outcome, "this server's own error must reach the test, and be classified as its own because the ping was not sent");
        } finally {
            XMPPServer.setInstance(previous);
        }
    }

    /**
     * @return an IQ router initialised against a mock server, which it is also registered as the instance of.
     */
    private static IQRouter startedRouter()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        final IQRouter router = new IQRouter();
        doReturn(router).when(server).getIQRouter();
        doReturn(NodeID.getInstance(new byte[] { 1 })).when(server).getNodeID();
        XMPPServer.setInstance(server);
        router.initialize(server);
        return router;
    }

    @Test
    public void testOwnErrorWithoutSendingIsClassifiedAsLocal() throws Exception
    {
        // Setup test fixture: no session could be made, so this server answers on the remote's behalf.
        final IQ ownError = replyTo(ping, IQ.Type.error);
        ownError.setError(PacketError.Condition.remote_server_not_found);
        interceptor.interceptPacket(ping, null, true, false);

        // Execute system under test.
        listener.receivedAnswer(ownError);
        final PingOutcome outcome = PingOutcome.fromReply(listener.awaitReply(Duration.ofSeconds(5)), interceptor.isPingSent());

        // Verify results.
        assertEquals(PingOutcome.localError("remote-server-not-found"), outcome, "an error for a ping that never left must be classified as this server's own");
    }

    @Test
    public void testErrorOverARouteOnAnotherNodeIsNotJudged()
    {
        // Setup test fixture: the route to the remote is held by another cluster node, which sends the ping, so this
        // node cannot see whether it was sent.
        final IQ error = replyTo(ping, IQ.Type.error);
        error.setError(PacketError.Condition.remote_server_not_found);

        // Execute system under test.
        final Optional<PingOutcome> outcome = S2STestService.pingOutcome(error, false, mock(RemoteOutgoingServerSession.class));

        // Verify results.
        assertTrue(outcome.isEmpty(), "an error over a route held by another node cannot be attributed, so the ping must not be judged");
    }

    @Test
    public void testNoReplyOverARouteOnAnotherNodeIsNotJudged()
    {
        assertTrue(S2STestService.pingOutcome(null, false, mock(RemoteOutgoingServerSession.class)).isEmpty(), "this node cannot see whether the node holding the route sent the ping, so a missing reply must not fail it");
    }

    @Test
    public void testResultOverARouteOnAnotherNodeIsJudged()
    {
        // Setup test fixture: only the remote server answers a ping with a result, whichever node it arrives on.
        final IQ result = replyTo(ping, IQ.Type.result);

        // Execute system under test.
        final Optional<PingOutcome> outcome = S2STestService.pingOutcome(result, false, mock(RemoteOutgoingServerSession.class));

        // Verify results.
        assertEquals(Optional.of(PingOutcome.result()), outcome, "a result can only come from the remote, so it must be judged even over another node's route");
    }

    @Test
    public void testErrorWithoutASessionIsClassifiedAsLocal()
    {
        // Setup test fixture.
        final IQ error = replyTo(ping, IQ.Type.error);
        error.setError(PacketError.Condition.remote_server_not_found);

        // Execute system under test.
        final Optional<PingOutcome> outcome = S2STestService.pingOutcome(error, false, null);

        // Verify results.
        assertEquals(Optional.of(PingOutcome.localError("remote-server-not-found")), outcome, "with no session anywhere, the ping never left this server and the error is its own");
    }

    @Test
    public void testRemoteErrorAfterSendingIsClassifiedAsRemote() throws Exception
    {
        // Setup test fixture.
        final IQ remoteError = replyTo(ping, IQ.Type.error);
        remoteError.setError(PacketError.Condition.service_unavailable);
        interceptor.interceptPacket(ping, mock(OutgoingServerSession.class), false, true);

        // Execute system under test.
        listener.receivedAnswer(remoteError);
        final PingOutcome outcome = PingOutcome.fromReply(listener.awaitReply(Duration.ofSeconds(5)), interceptor.isPingSent());

        // Verify results.
        assertEquals(PingOutcome.remoteError("service-unavailable"), outcome, "an error for a ping that was sent must be classified as the remote's reply");
    }

    @Test
    public void testTrafficWithTheDomainIsLogged() throws Exception
    {
        // Execute system under test.
        interceptor.interceptPacket(ping, null, true, false);
        interceptor.interceptPacket(replyTo(ping, IQ.Type.result), null, true, false);

        // Verify results.
        final String stanzas = interceptor.getStanzas();
        assertTrue(stanzas.contains("s2s-test-ping"), "the ping must appear in the XMPP output");
        assertEquals(2, stanzas.lines().count(), "each stanza exchanged with the domain must be logged once, before processing");
    }

    @Test
    public void testCertificateOutputIsTheCertificateInPem() throws Exception
    {
        // Setup test fixture.
        final X509Certificate certificate = KeystoreTestUtils.generateValidCertificateChain("remote.example.org").getCertificateChain()[0];

        // Execute system under test.
        final String output = S2STestService.formatCertificates(new Certificate[] { certificate });

        // Verify results.
        final int begin = output.indexOf("-----BEGIN CERTIFICATE-----");
        final int end = output.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length();
        assertTrue(begin >= 0 && end > begin, "the output must contain a PEM block");
        final Certificate parsed = CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(output.substring(begin, end).getBytes(StandardCharsets.US_ASCII)));
        assertEquals(certificate, parsed, "the PEM block must hold the certificate itself, not only its public key");
        assertTrue(output.contains("remote.example.org"), "the output must still show the subject for reading at a glance");
    }

    /**
     * Verifies that the PEM block has no empty line, whatever the length of the certificate. OpenSSL refuses a block
     * with an empty line before the END marker, which a naive wrap at 64 characters produces when the base64 text is
     * an exact multiple of 64 characters long. Java's CertificateFactory accepts such a block, so parsing it back, as
     * the other test does, cannot catch this.
     *
     * The certificates are signed with Ed25519, whose signature always has the same length, so each extra character
     * in the subject name adds one byte to the encoding (a few more where a length field grows), and 96 consecutive
     * name lengths cover every length of the encoding modulo 48 (48 bytes being 64 base64 characters).
     */
    @Test
    public void testCertificateOutputHasNoEmptyLineInThePemBlockForAnyLength() throws Exception
    {
        // Setup test fixture.
        final KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final Set<Integer> remaindersCovered = new HashSet<>();
        for (int nameLength = 1; nameLength <= 96; nameLength++) {
            final X509Certificate certificate = selfSignedCertificate(keyPair, "a".repeat(nameLength));
            remaindersCovered.add(certificate.getEncoded().length % 48);

            // Execute system under test.
            final String output = S2STestService.formatCertificates(new Certificate[] { certificate });

            // Verify results.
            final int begin = output.indexOf("-----BEGIN CERTIFICATE-----");
            final int end = output.indexOf("-----END CERTIFICATE-----");
            assertTrue(begin >= 0 && end > begin, "the output must contain a PEM block");
            // PemWriter ends its lines with the platform's line separator, so treat CRLF as one line break.
            final String block = output.substring(begin, end).replace("\r\n", "\n");
            assertFalse(block.contains("\n\n"), "no line in the PEM block may be empty (encoded length " + certificate.getEncoded().length + ")");
        }
        assertEquals(48, remaindersCovered.size(), "the certificates must cover every encoded length modulo 48");
    }

    private static X509Certificate selfSignedCertificate(final KeyPair keyPair, final String commonName) throws Exception
    {
        final X500Name name = new X500Name("CN=" + commonName);
        final Date notBefore = new Date(0);
        final Date notAfter = new Date(86_400_000L);
        return new JcaX509CertificateConverter().getCertificate(
            new JcaX509v3CertificateBuilder(name, BigInteger.ONE, notBefore, notAfter, name, keyPair.getPublic())
                .build(new JcaContentSignerBuilder("Ed25519").build(keyPair.getPrivate())));
    }
    @Test
    public void testAttemptStartedDuringTheTestIsUsed()
    {
        // Setup test fixture.
        final Instant testStart = Instant.now();
        final AttemptRecorder attempt = new AttemptRecorder(LOCAL, REMOTE);
        attempt.tcp(AttemptMilestones.Tcp.connected("192.0.2.10:5269"));

        // Execute system under test.
        final Optional<AttemptMilestones> milestones = S2STestService.milestonesOfThisTest(Optional.of(attempt), testStart);

        // Verify results.
        assertTrue(milestones.orElseThrow().getTcp().isPresent(), "the milestones of an attempt made during the test must be used");
    }

    @Test
    public void testAttemptFromBeforeTheTestIsIgnored() throws Exception
    {
        // Setup test fixture: an attempt that started before this test did.
        final AttemptRecorder earlier = new AttemptRecorder(LOCAL, REMOTE);
        earlier.tcp(AttemptMilestones.Tcp.connected("192.0.2.10:5269"));
        final Instant testStart = earlier.getStarted().plusMillis(1);

        // Execute system under test.
        final Optional<AttemptMilestones> milestones = S2STestService.milestonesOfThisTest(Optional.of(earlier), testStart);

        // Verify results.
        assertTrue(milestones.isEmpty(), "an attempt from before the test must not be reported as this test's, or its steps would show unearned passes");
    }

    @Test
    public void testNoAttemptMeansNoMilestones()
    {
        assertTrue(S2STestService.milestonesOfThisTest(Optional.empty(), Instant.now()).isEmpty(), "with no attempt there are no milestones");
    }

    @Test
    public void testCapturedLogLinesCarryTheirLevel()
    {
        // Setup test fixture.
        final StringWriter writer = new StringWriter();
        final S2STestService service = new S2STestService(new org.xmpp.packet.JID(REMOTE));
        final String appender = S2STestService.newAppenderName();
        service.addAppender(appender, writer, new AtomicInteger());
        final Logger logger = LogManager.getLogger(S2STestServiceTest.class);

        // Execute system under test.
        try {
            logger.warn("a warning during the test");
        } finally {
            service.removeAppender(appender);
        }

        // Verify results.
        assertTrue(writer.toString().contains("WARN"), "each captured log line must show its level, so warnings can be told from information");
        assertTrue(writer.toString().contains("a warning during the test"), "the message itself must be captured");
    }

    @Test
    public void testCapturedWarningsAndErrorsAreCounted()
    {
        // Setup test fixture.
        final AtomicInteger warningsAndErrors = new AtomicInteger();
        final S2STestService service = new S2STestService(new org.xmpp.packet.JID(REMOTE));
        final String appender = S2STestService.newAppenderName();
        service.addAppender(appender, new StringWriter(), warningsAndErrors);
        final Logger logger = LogManager.getLogger(S2STestServiceTest.class);

        // Execute system under test.
        try {
            logger.info("information during the test");
            logger.warn("a warning during the test");
            logger.error("an error during the test");
        } finally {
            service.removeAppender(appender);
        }

        // Verify results.
        assertEquals(2, warningsAndErrors.get(), "every warning and error logged during the test must be counted, and nothing else");
    }

    @Test
    public void testLogOfThisTestCountsTheAttemptAndReportsTheRestAsOther()
    {
        // Setup test fixture: the attempt logged two warnings and an error; five were logged in all during the test.
        final Instant testStart = Instant.now();
        final AttemptRecorder attempt = new AttemptRecorder(LOCAL, REMOTE);
        attempt.diagnosticLog(List.of("DEBUG: Creating new session...", "WARN: Cannot connect", "WARN: Unable to authenticate", "ERROR: An exception occurred"));

        // Execute system under test.
        final LogSummary log = S2STestService.logOfThisTest(Optional.of(attempt), testStart, 5).orElseThrow();

        // Verify results.
        assertEquals(new LogSummary(2, 1, 2), log, "the attempt's own warnings and errors must decide the check, and the rest must be reported as other");
    }

    @Test
    public void testLogOfThisTestNeverReportsANegativeOtherCount()
    {
        // Setup test fixture: the diagnostic log can name a problem at a higher level than the server logged it at.
        final Instant testStart = Instant.now();
        final AttemptRecorder attempt = new AttemptRecorder(LOCAL, REMOTE);
        attempt.diagnosticLog(List.of("WARN: Unable to authenticate", "WARN: Fail to create new session"));

        // Execute system under test.
        final LogSummary log = S2STestService.logOfThisTest(Optional.of(attempt), testStart, 1).orElseThrow();

        // Verify results.
        assertEquals(new LogSummary(2, 0, 0), log, "other warnings and errors must not go below zero");
    }

    @Test
    public void testLogOfAnUnfinishedAttemptIsNotJudged()
    {
        // Setup test fixture: an attempt that has not handed over its diagnostic log yet.
        final Instant testStart = Instant.now();
        final AttemptRecorder attempt = new AttemptRecorder(LOCAL, REMOTE);

        // Execute system under test.
        final Optional<LogSummary> log = S2STestService.logOfThisTest(Optional.of(attempt), testStart, 3);

        // Verify results.
        assertTrue(log.isEmpty(), "without the attempt's log there is nothing to judge, so the check must not pass");
    }

    @Test
    public void testLogOfAnAttemptFromBeforeTheTestIsNotJudged()
    {
        // Setup test fixture.
        final AttemptRecorder earlier = new AttemptRecorder(LOCAL, REMOTE);
        earlier.diagnosticLog(List.of("DEBUG: Authentication successful."));
        final Instant testStart = earlier.getStarted().plusMillis(1);

        // Execute system under test.
        final Optional<LogSummary> log = S2STestService.logOfThisTest(Optional.of(earlier), testStart, 0);

        // Verify results.
        assertTrue(log.isEmpty(), "the log of an attempt from before the test must not be reported as this test's");
    }
}
