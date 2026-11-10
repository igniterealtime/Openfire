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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify what {@link RequestingStreamManager}, the end of a stream that asks for Stream Management,
 * does when its session closes or is resumed, when it reports that negotiation has finished, and how it counts
 * stanzas that are sent while a request to enable Stream Management awaits an answer. It also verifies how it requests
 * a stream that can be resumed, records what the peer answers to that, and queues stanzas while the connection of its
 * session is broken, and how it asks the peer to resume the stream of a detached session on a new connection and
 * processes the answer. How it asks for Stream Management, and how it answers the peer, is
 * verified through the stanza handler of outgoing server connections, in RespondingServerStanzaHandlerStreamManagementTest.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RequestingStreamManagerTest
{
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);

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
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that Stream Management is disabled when the session closes, and that nothing is routed when no stanzas
     * are unacknowledged. How unacknowledged stanzas are handled is verified in
     * LocalOutgoingServerSessionStreamManagementTest.
     */
    @Test
    public void closeDisablesStreamManagement()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = enabledStreamManager();
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        assertFalse(sm.isEnabled(), "Stream Management must be disabled when the session closes.");
        assertTrue(mockingDetails(router).getInvocations().isEmpty(), "Nothing must be routed when no stanzas are unacknowledged.");
    }

    /**
     * Verifies that, on resumption, the stanzas that 'h' acknowledges are removed, the stanzas that remain
     * unacknowledged are sent again first, in order, and marked as sent again, and the queued stanzas are sent after
     * them, in order, and tracked, after which queueing stops.
     */
    @Test
    public void resumptionSendsUnacknowledgedThenQueuedStanzasInOrder() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(message("acked"));
        sm.sentStanza(message("unacked-1"));
        sm.sentStanza(message("unacked-2"));
        sm.startQueueing();
        sm.queue(message("queued-1"), true);
        sm.queue(message("queued-2"), true);
        final Connection connection = sm.session.getConnection();
        clearInvocations(connection);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 1);
        final boolean queuedAfterResumption = sm.queue(message("after"), false);

        // Verify result.
        assertEquals(List.of("unacked-1", "unacked-2", "queued-1", "queued-2"), delivered(connection).stream().map(Packet::getID).toList(), "The unacknowledged stanzas must be sent again first, then the queued stanzas, in order.");
        assertEquals(List.of("unacked-1", "unacked-2", "queued-1", "queued-2"), UnacknowledgedStanzas.ids(sm), "Every stanza that was sent must be tracked until it is acknowledged.");
        assertEquals(List.of("unacked-1", "unacked-2"), UnacknowledgedStanzas.sentAgainIds(sm), "The stanzas that are sent again must be marked as sent again, and the queued stanzas must not.");
        assertNotNull(delivered(connection).get(0).getExtension("delay", "urn:xmpp:delay"), "A message that is sent again must get a delay stamp.");
        assertNull(delivered(connection).get(2).getExtension("delay", "urn:xmpp:delay"), "A queued stanza was never sent before, and must not get a delay stamp.");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(sm), "No stanza must remain queued once they were sent.");
        assertFalse(queuedAfterResumption, "A stanza that is delivered once the queued stanzas were sent must be sent, rather than queued.");
    }

    /**
     * Verifies that, when the connection closes while the unacknowledged stanzas are sent again on resumption, only the
     * stanzas that were sent are marked as sent again, so that the others keep their one retry (decision 4 of OF-2425),
     * and that no other stanza is sent on the closed connection.
     */
    @Test
    public void stanzasNotSentAgainOnResumptionAsConnectionClosedKeepTheirRetry() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(message("unacked-1"));
        sm.sentStanza(message("unacked-2"));
        sm.startQueueing();
        sm.queue(message("queued"), true);
        final Connection connection = sm.session.getConnection();
        clearInvocations(connection);
        doAnswer(invocation -> { doReturn(true).when(connection).isClosed(); return null; }).when(connection).deliver(argThat(packet -> "unacked-1".equals(packet.getID())));

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        assertEquals(List.of("unacked-1"), delivered(connection).stream().map(Packet::getID).toList(), "No stanza must be sent once the connection closed.");
        assertEquals(List.of("unacked-1", "unacked-2"), UnacknowledgedStanzas.ids(sm), "Both stanzas must remain unacknowledged.");
        assertEquals(List.of("unacked-1"), UnacknowledgedStanzas.sentAgainIds(sm), "Only the stanza that was sent again must be marked as sent again.");
        assertEquals(List.of("queued"), UnacknowledgedStanzas.queuedIds(sm), "The queued stanza must stay queued, as it was not sent.");
        sm.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "2"));
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "The stanzas that were not sent must keep the number with which they are sent next, as the peer counts them.");
    }

    /**
     * Verifies that the stanzas that are sent on resumption are numbered as the peer counts them: an acknowledgement
     * that covers all of them is valid, and removes them.
     */
    @Test
    public void stanzasSentOnResumptionAreNumberedAsPeerCountsThem() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(message("acked"));
        sm.sentStanza(message("unacked"));
        sm.startQueueing();
        sm.queue(message("queued"), true);
        sm.onResume(SERVER_ADDRESS, 1);

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "3"));

        // Verify result.
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "An acknowledgement of the stanza that was sent again and the queued one must remove both.");
        verify(sm.session, never().description("A valid acknowledgement must not close the session.")).close(any(StreamError.class));
    }

    /**
     * Verifies that a stanza that was sent again before, and that is unacknowledged when the stream is resumed again, is
     * not sent again a second time, but returned to its sender as an error (decision 4 of OF-2425), and that the stanzas
     * that are sent again are numbered after the acknowledged ones without it.
     */
    @Test
    public void stanzaSentAgainBeforeIsReturnedAsErrorOnResumption() throws Exception
    {
        // Setup test fixture.
        final PacketRouter router = useServerWithRouter();
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(addressed(message("sent-again-before")));
        sm.onResume(SERVER_ADDRESS, 0);
        sm.sentStanza(addressed(message("new")));
        final Connection connection = sm.session.getConnection();
        clearInvocations(connection);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        assertEquals(List.of("new"), delivered(connection).stream().map(Packet::getID).toList(), "Only the stanza that was not sent again before must be sent again.");
        final List<Packet> routed = routed(router);
        assertEquals(List.of("sent-again-before"), routed.stream().map(Packet::getID).toList(), "The stanza that was sent again before must be returned to its sender.");
        assertEquals("error", routed.get(0).getElement().attributeValue("type"), "The stanza that was sent again before must be returned as an error.");
        assertEquals(List.of("new"), UnacknowledgedStanzas.ids(sm), "The stanza that is returned as an error must no longer be tracked.");
        sm.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "1"));
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "The stanza that is sent again must be numbered directly after the acknowledged stanzas, as the peer counts it.");
    }

    /**
     * Verifies that the stanzas are sent on resumption while the lock of the stream manager is held, under which
     * stanzas that are delivered to the session are queued, so that no such stanza can be written in between.
     */
    @Test
    public void resumptionSendsStanzasWhileHoldingLock() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(message("unacked"));
        sm.startQueueing();
        sm.queue(message("queued"), true);
        final Connection connection = sm.session.getConnection();
        final List<Boolean> heldLock = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { heldLock.add(Thread.holdsLock(sm)); return null; }).when(connection).deliver(any(Packet.class));

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        assertEquals(List.of(true, true), heldLock, "Every stanza must be sent while the lock of the stream manager is held.");
    }

    /**
     * Verifies that, when the connection closes while the queued stanzas are sent on resumption, the stanzas that were
     * not sent stay queued, and that queueing continues.
     */
    @Test
    public void queuedStanzasStayQueuedWhenConnectionClosesDuringResumption() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.startQueueing();
        sm.queue(message("queued-1"), true);
        sm.queue(message("queued-2"), true);
        final Connection connection = sm.session.getConnection();
        final AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> closed.get()).when(connection).isClosed();
        doAnswer(invocation -> { closed.set(true); return null; }).when(connection).deliver(any(Packet.class));

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);
        final boolean queuedAfterResumption = sm.queue(message("after"), false);

        // Verify result.
        assertEquals(List.of("queued-1"), UnacknowledgedStanzas.ids(sm), "Only the stanza that was sent before the connection closed must be tracked.");
        assertTrue(queuedAfterResumption, "Queueing must continue when not every queued stanza was sent.");
        assertEquals(List.of("queued-2", "after"), UnacknowledgedStanzas.queuedIds(sm), "The stanza that was not sent must stay queued, before the stanzas that are queued after it.");
    }

    /**
     * Verifies that Openfire asks the peer to resume the stream of a detached session with the SM-ID of that stream, the
     * number of stanzas that Openfire handled on it, and its version of Stream Management.
     */
    @Test
    public void resumeIsRequestedForStreamOfDetachedSession() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager targetSm = resumableStreamManager(StreamManager.NAMESPACE_V2);
        final LocalOutgoingServerSession target = target(targetSm);
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3, StreamManager.NAMESPACE_V2));

        // Verify result.
        final Element resume = lastDelivered(sm);
        assertEquals(QName.get("resume", StreamManager.NAMESPACE_V2), resume.getQName(), "The request must use the version of Stream Management of the stream that is resumed.");
        assertEquals("some-sm-id", resume.attributeValue("previd"), "The request must name the SM-ID of the stream that is resumed.");
        assertEquals("0", resume.attributeValue("h"), "The request must report the number of stanzas that Openfire handled on the stream that is resumed.");
        assertEquals(RequestingStreamManager.ResumeRequestStatus.PENDING, sm.getResumeRequestStatus(), "The request must await an answer of the peer.");
        assertFalse(sm.getResumeRequestFinished().isDone(), "The request must not be finished before the peer answers it.");
        assertFalse(sm.isEnabled(), "Asking to resume a stream must not enable Stream Management on the new stream.");
    }

    /**
     * Verifies that Openfire does not ask the peer to resume a stream when the peer does not offer the version of Stream
     * Management of that stream, or when resumption on server connections is disabled, and that the request is reported
     * as finished.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void resumeIsNotRequestedWhenNotPossible(final boolean versionOffered) throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager targetSm = resumableStreamManager();
        final LocalOutgoingServerSession target = target(targetSm);
        final RequestingStreamManager sm = newStreamManager();
        if (versionOffered) {
            StreamManager.SERVER_RESUME_ENABLED.setValue(false);
        }

        // Execute system under test.
        sm.requestResume(target, versionOffered ? List.of(StreamManager.NAMESPACE_V3) : List.of(StreamManager.NAMESPACE_V2));

        // Verify result.
        verify(sm.session, never().description("No request must be sent (version offered: " + versionOffered + ").")).deliverRawText(anyString());
        assertEquals(RequestingStreamManager.ResumeRequestStatus.NOT_SENT, sm.getResumeRequestStatus(), "The request must be recorded as not sent.");
        assertTrue(sm.getResumeRequestFinished().isDone(), "A request that is not sent must be reported as finished.");
        assertTrue(sm.abandonResume(), "A request that is not sent must be given up by the caller, which then ends the detached session.");
    }

    /**
     * Verifies that, when the peer resumes the stream, the detached session is resumed with the connection of the
     * session of this stream manager, with the value of 'h' that the peer reported.
     */
    @Test
    public void resumedResumesDetachedSession() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager targetSm = resumableStreamManager();
        targetSm.sentStanza(message("unacked"));
        final LocalOutgoingServerSession target = target(targetSm);
        doReturn(true).when(target).resumeWithConnectionOf(any(), anyLong());
        final RequestingStreamManager sm = newStreamManager();
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3));

        // Execute system under test.
        sm.process(resumed("some-sm-id", "1"));

        // Verify result.
        verify(target, times(1).description("The detached session must be resumed with the connection of this session.")).resumeWithConnectionOf((LocalOutgoingServerSession) sm.session, 1);
        assertEquals(RequestingStreamManager.ResumeRequestStatus.ANSWERED, sm.getResumeRequestStatus(), "The answer of the peer must be recorded.");
        assertTrue(sm.getResumeRequestFinished().isDone(), "The request must be finished once its answer is processed.");
        assertFalse(sm.abandonResume(), "A request that was answered must not be given up.");
        verify(sm.session, never().description("The connection must not be closed.")).close();
        verify(sm.session, never().description("The connection must not be closed with a stream error.")).close(any());
    }

    /**
     * Verifies that, when the detached session cannot be taken from the detached sessions as it was ended meanwhile,
     * the connection on which the stream was resumed is closed, and the session is not ended again.
     */
    @Test
    public void resumedForSessionThatWasEndedClosesConnection() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useServer().getSessionManager();
        final RequestingStreamManager targetSm = resumableStreamManager();
        final LocalOutgoingServerSession target = target(targetSm);
        doReturn(false).when(target).resumeWithConnectionOf(any(), anyLong());
        final RequestingStreamManager sm = newStreamManager();
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3));

        // Execute system under test.
        sm.process(resumed("some-sm-id", "0"));

        // Verify result.
        verify(sm.session, times(1).description("The connection on which the stream was resumed must be closed.")).close();
        verify(sessionManager, after(200).never().description("A session that was ended meanwhile must not be ended again.")).terminateDetached(any());
    }

    /**
     * Verifies that an answer to a request to resume a stream that does not match the request, or that reports an
     * invalid value of 'h', closes the connection with a stream error, and ends the detached session, without resuming
     * it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"other-sm-id", "not-a-number", "-1", "4294967296", "1", "namespace"})
    public void invalidResumedEndsDetachedSession(final String variant) throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useServer().getSessionManager();
        final RequestingStreamManager targetSm = resumableStreamManager();
        final LocalOutgoingServerSession target = target(targetSm);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3));
        final Element resumed = switch (variant) {
            case "other-sm-id" -> resumed("other-sm-id", "0");
            case "namespace" -> DocumentHelper.createElement(QName.get("resumed", StreamManager.NAMESPACE_V2)).addAttribute("previd", "some-sm-id").addAttribute("h", "0");
            default -> resumed("some-sm-id", variant);
        };

        // Execute system under test.
        sm.process(resumed);

        // Verify result.
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(sm.session, times(1).description("The connection must be closed with a stream error for an invalid answer (" + variant + ").")).close(captor.capture());
        assertEquals(StreamError.Condition.undefined_condition, captor.getValue().getCondition(), "The stream error must use the 'undefined-condition' condition.");
        if ("1".equals(variant)) {
            assertNotNull(captor.getValue().getElement().element(QName.get("handled-count-too-high", StreamManager.NAMESPACE_V3)), "A value of 'h' that is too high must be reported with 'handled-count-too-high', but was: " + captor.getValue().toXML());
        }
        verify(target, never().description("The detached session must not be resumed after an invalid answer.")).resumeWithConnectionOf(any(), anyLong());
        verify(sessionManager, timeout(5000).times(1).description("The detached session must be ended after an invalid answer.")).terminateDetached(target);
    }

    /**
     * Verifies that a refusal of the peer to resume the stream closes the connection, and ends the detached session.
     */
    @Test
    public void failedAnswerToResumeEndsDetachedSession() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useServer().getSessionManager();
        final RequestingStreamManager targetSm = resumableStreamManager();
        final LocalOutgoingServerSession target = target(targetSm);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3));

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3)).addAttribute("h", "0"));

        // Verify result.
        verify(sm.session, times(1).description("The connection must be closed when the peer refuses to resume the stream.")).close(isNull());
        verify(target, never().description("The detached session must not be resumed.")).resumeWithConnectionOf(any(), anyLong());
        verify(sessionManager, timeout(5000).times(1).description("The detached session must be ended when the peer refuses to resume its stream.")).terminateDetached(target);
        assertEquals(RequestingStreamManager.EnableRequestStatus.NOT_SENT, sm.getEnableRequestStatus(), "A refusal to resume must not be taken for an answer to a request to enable Stream Management.");
        assertTrue(sm.getResumeRequestFinished().isDone(), "The request must be finished once its answer is processed.");
    }

    /**
     * Verifies that an answer that arrives after Openfire gave up on a request to resume a stream is ignored.
     */
    @Test
    public void resumedAfterRequestWasGivenUpIsIgnored() throws Exception
    {
        // Setup test fixture.
        final SessionManager sessionManager = useServer().getSessionManager();
        final RequestingStreamManager targetSm = resumableStreamManager();
        final LocalOutgoingServerSession target = target(targetSm);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestResume(target, List.of(StreamManager.NAMESPACE_V3));
        assertTrue(sm.abandonResume(), "Test fixture expects the request to be given up.");
        clearInvocations(sm.session);

        // Execute system under test.
        sm.process(resumed("some-sm-id", "0"));

        // Verify result.
        verify(target, never().description("The detached session must not be resumed after the request was given up.")).resumeWithConnectionOf(any(), anyLong());
        verify(sm.session, never().description("Nothing must be sent in answer to an answer that arrives too late.")).deliverRawText(anyString());
        verify(sm.session, never().description("The connection must not be closed for an answer that arrives too late.")).close();
        verify(sm.session, never().description("The connection must not be closed with a stream error for an answer that arrives too late.")).close(any());
        verify(sessionManager, after(200).never().description("The detached session must not be ended for an answer that arrives too late.")).terminateDetached(any());
    }

    /**
     * Verifies that {@code <resumed/>} that does not answer a request of Openfire is refused, and changes nothing.
     */
    @Test
    public void resumedWithoutRequestIsRefused() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = enabledStreamManager();
        clearInvocations(sm.session);

        // Execute system under test.
        sm.process(resumed("some-sm-id", "0"));

        // Verify result.
        assertEquals(QName.get("failed", StreamManager.NAMESPACE_V3), lastDelivered(sm).getQName(), "A 'resumed' element that does not answer a request must be refused.");
        assertTrue(sm.isEnabled(), "Refusing the element must leave Stream Management enabled.");
        assertEquals(RequestingStreamManager.ResumeRequestStatus.NOT_SENT, sm.getResumeRequestStatus(), "The element must not be recorded as an answer.");
    }

    /**
     * Verifies that negotiation is not reported as finished while a request to enable Stream Management awaits an
     * answer of the peer.
     */
    @Test
    public void negotiationIsNotFinishedWhileRequestIsPending()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Verify result.
        assertFalse(sm.getNegotiationOutcome().isDone(), "Negotiation must not be reported as finished while the request awaits an answer.");
    }

    /**
     * Verifies that negotiation is reported as finished, with the outcome, when the peer enables Stream Management at
     * the request of Openfire.
     */
    @Test
    public void negotiationIsFinishedWhenEnabled()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));

        // Verify result.
        final CompletableFuture<RequestingStreamManager.EnableRequestStatus> outcome = sm.getNegotiationOutcome();
        assertTrue(outcome.isDone(), "Negotiation must be reported as finished once the peer enabled Stream Management.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.ENABLED, outcome.join(), "The outcome must report that Stream Management was enabled.");
    }

    /**
     * Verifies that negotiation is reported as finished, with the outcome, when the peer refuses to enable Stream
     * Management at the request of Openfire.
     */
    @Test
    public void negotiationIsFinishedWhenRefused()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3)));

        // Verify result.
        final CompletableFuture<RequestingStreamManager.EnableRequestStatus> outcome = sm.getNegotiationOutcome();
        assertTrue(outcome.isDone(), "Negotiation must be reported as finished once the peer refused Stream Management.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.REFUSED, outcome.join(), "The outcome must report that Stream Management was refused.");
    }

    /**
     * Verifies that no request to enable Stream Management is sent once Openfire decided not to ask for it, for example
     * when the session is in use already, as stanzas that were sent before such a request would not be counted as the
     * peer counts them.
     */
    @Test
    public void enableIsNotSentAfterItWasSkipped()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        sm.skipEnable();

        // Execute system under test.
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Verify result.
        verify(sm.session, never().description("No request must be sent after Openfire decided not to ask for Stream Management.")).deliverRawText(anyString());
        assertEquals(RequestingStreamManager.EnableRequestStatus.SKIPPED, sm.getEnableRequestStatus(), "The decision not to ask for Stream Management must be kept.");
    }

    /**
     * Verifies that the request to enable Stream Management is sent while holding the lock of the stream manager, under
     * which stanzas that are sent are counted, so that a stanza is counted exactly when it follows the request.
     */
    @Test
    public void enableIsSentUnderLock()
    {
        // Setup test fixture.
        StreamManager.SERVER_ACTIVE.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        final List<Boolean> lockHeld = new ArrayList<>();
        doAnswer(invocation -> { lockHeld.add(Thread.holdsLock(sm)); return null; }).when(sm.session).deliverRawText(anyString());

        // Execute system under test.
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Verify result.
        assertEquals(List.of(true), lockHeld, "The request must be sent once, while holding the lock of the stream manager.");
    }

    /**
     * Verifies that negotiation is reported as finished, without a request, when Openfire decides not to ask the peer to
     * enable Stream Management.
     */
    @Test
    public void negotiationIsFinishedWhenSkipped()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.skipEnable();

        // Verify result.
        final CompletableFuture<RequestingStreamManager.EnableRequestStatus> outcome = sm.getNegotiationOutcome();
        assertTrue(outcome.isDone(), "Negotiation must be reported as finished once Openfire decided not to ask for Stream Management.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.SKIPPED, outcome.join(), "The outcome must report that Openfire decided not to send a request.");
        verify(sm.session, never().description("Nothing must be sent to the peer.")).deliverRawText(anyString());
    }

    /**
     * Verifies that deciding not to ask for Stream Management after a request was sent does not finish negotiation, as
     * that request still awaits an answer.
     */
    @Test
    public void skipAfterRequestDoesNotFinishNegotiation()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.skipEnable();

        // Verify result.
        assertFalse(sm.getNegotiationOutcome().isDone(), "Negotiation must not be reported as finished while the request awaits an answer.");
        assertEquals(RequestingStreamManager.EnableRequestStatus.PENDING, sm.getEnableRequestStatus(), "The request must still await an answer.");
    }

    /**
     * Verifies that an 'enabled' element that does not answer a request of Openfire does not finish negotiation.
     */
    @Test
    public void enabledWithoutRequestDoesNotFinishNegotiation()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));

        // Verify result.
        assertFalse(sm.getNegotiationOutcome().isDone(), "An answer to a request that was not made must not finish negotiation.");
    }

    /**
     * Verifies that completing the future that reports the outcome of negotiation does not affect the stream manager.
     */
    @Test
    public void completingReturnedFutureDoesNotFinishNegotiation()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.getNegotiationOutcome().complete(RequestingStreamManager.EnableRequestStatus.ENABLED);

        // Verify result.
        assertFalse(sm.getNegotiationOutcome().isDone(), "Negotiation must not be reported as finished by completing a future that was returned.");
    }

    /**
     * Verifies that stanzas that are sent while a request to enable Stream Management awaits an answer are not tracked,
     * while those that are sent once the peer enabled it are.
     */
    @Test
    public void stanzasSentWhileRequestIsPendingAreNotTracked()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("pending-1"));
        sm.sentStanza(message("pending-2"));

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        sm.sentStanza(message("enabled-1"));

        // Verify result.
        assertEquals(List.of("enabled-1"), UnacknowledgedStanzas.ids(sm), "Only the stanza that was sent after the peer enabled Stream Management must be tracked.");
    }

    /**
     * Verifies that an acknowledgement that counts the stanzas that were sent while a request to enable Stream Management
     * awaited an answer is valid, as the peer counts them from the request on (XEP-0198 § 4), and that it removes the
     * tracked stanzas that it covers.
     */
    @Test
    public void acknowledgementThatCountsStanzasSentWhileRequestWasPendingIsValid()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("pending-1"));
        sm.sentStanza(message("pending-2"));
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        sm.sentStanza(message("enabled-1"));
        sm.sentStanza(message("enabled-2"));

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "3"));

        // Verify result.
        verify(sm.session, never().description("An acknowledgement that counts the stanzas sent while the request awaited an answer must not close the session.")).close(any(StreamError.class));
        assertEquals(List.of("enabled-2"), UnacknowledgedStanzas.ids(sm), "Only the stanza that the acknowledgement does not cover must remain.");
    }

    /**
     * Verifies that an acknowledgement of more stanzas than were sent since the request to enable Stream Management
     * closes the session, also when stanzas were sent while the request awaited an answer.
     */
    @Test
    public void acknowledgementOfMoreStanzasThanSentSinceRequestClosesSession()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("pending-1"));
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        sm.sentStanza(message("enabled-1"));

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("a", StreamManager.NAMESPACE_V3)).addAttribute("h", "3"));

        // Verify result.
        verify(sm.session, times(1).description("An acknowledgement of more stanzas than were sent must close the session with a stream error.")).close(any(StreamError.class));
    }

    /**
     * Verifies that no stanzas are tracked when the peer refuses to enable Stream Management, neither those that were
     * sent while the request awaited an answer, nor those that are sent after.
     */
    @Test
    public void stanzasAreNotTrackedWhenRefused()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("pending-1"));

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("failed", StreamManager.NAMESPACE_V3)));
        sm.sentStanza(message("refused-1"));

        // Verify result.
        assertFalse(sm.isEnabled(), "Stream Management must not be enabled after the peer refused it.");
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "No stanzas must be tracked when the peer refused Stream Management.");
    }

    /**
     * Verifies that a stanza that is routed again when the session closes is marked as such, and that a copy of it gets
     * the marker when it is copied with {@link RequestingStreamManager#copyRoutedAgainMarker(Packet, Packet)}.
     */
    @Test
    public void stanzaRoutedAgainOnCloseIsMarkedAndCopyKeepsMarker()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = enabledStreamManager();
        sm.sentStanza(message("1"));
        final PacketRouter router = mock(PacketRouter.class);
        sm.onClose(router, SERVER_ADDRESS);
        final Packet routedAgain = routed(router).get(0);
        final Packet copy = routedAgain.createCopy();

        // Execute system under test.
        RequestingStreamManager.copyRoutedAgainMarker(routedAgain, copy);

        // Verify result.
        assertTrue(RequestingStreamManager.isRoutedAgain(routedAgain), "A stanza that is routed again when the session closes must be marked as routed again.");
        assertTrue(RequestingStreamManager.isRoutedAgain(copy), "A copy of a stanza that is routed again must get the marker.");
    }

    /**
     * Verifies that a copy of a stanza that is not routed again does not get the marker.
     */
    @Test
    public void copyOfStanzaThatIsNotRoutedAgainIsNotMarked()
    {
        // Setup test fixture.
        final Message original = message("1");
        final Packet copy = original.createCopy();

        // Execute system under test.
        RequestingStreamManager.copyRoutedAgainMarker(original, copy);

        // Verify result.
        assertFalse(RequestingStreamManager.isRoutedAgain(copy), "A copy of a stanza that is not routed again must not be marked as routed again.");
    }

    /**
     * Verifies that a stream manager tracks a stanza that another session routed again as sent again, and other stanzas
     * as sent for the first time.
     */
    @Test
    public void stanzaRoutedAgainIsTrackedAsSentAgain()
    {
        // Setup test fixture.
        final RequestingStreamManager closing = enabledStreamManager();
        closing.sentStanza(message("1"));
        final PacketRouter router = mock(PacketRouter.class);
        closing.onClose(router, SERVER_ADDRESS);
        final Packet routedAgain = routed(router).get(0);
        final RequestingStreamManager sm = enabledStreamManager();

        // Execute system under test.
        sm.sentStanza(routedAgain);
        sm.sentStanza(message("2"));

        // Verify result.
        final List<UnacknowledgedStanzaQueue.Entry> entries = List.copyOf(sm.getUnacknowledgedEntries());
        assertEquals(List.of("1", "2"), entries.stream().map(entry -> entry.getPacket().getID()).toList(), "Both stanzas must be tracked.");
        assertTrue(entries.get(0).isSentAgain(), "A stanza that another session routed again must be tracked as sent again.");
        assertFalse(entries.get(1).isSentAgain(), "A stanza that is sent for the first time must not be tracked as sent again.");
    }

    /**
     * Verifies that a stream that can be resumed is requested when resumption on server connections is enabled.
     */
    @Test
    public void resumptionIsRequestedWhenEnabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sm.session, times(1).description("One request to enable Stream Management must be sent.")).deliverRawText(captor.capture());
        final Element enable = DocumentHelper.parseText(captor.getValue()).getRootElement();
        assertEquals(QName.get("enable", StreamManager.NAMESPACE_V3), enable.getQName(), "The request must be an 'enable' element in the requested namespace.");
        assertEquals("true", enable.attributeValue("resume"), "The request must ask for a stream that can be resumed, but was: " + captor.getValue());
    }

    /**
     * Verifies that a stream that can be resumed is not requested when resumption on server connections is disabled.
     */
    @Test
    public void resumptionIsNotRequestedWhenDisabled() throws Exception
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();

        // Execute system under test.
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Verify result.
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sm.session, times(1).description("One request to enable Stream Management must be sent.")).deliverRawText(captor.capture());
        assertNull(DocumentHelper.parseText(captor.getValue()).getRootElement().attributeValue("resume"), "The request must not ask for a stream that can be resumed, but was: " + captor.getValue());
    }

    /**
     * Verifies that the stream can be resumed when the peer allows it, with either lexical form of a true boolean
     * (XEP-0198 § 5), and that its SM-ID and preferred maximum resumption time are recorded.
     */
    @ParameterizedTest
    @ValueSource(strings = {"true", "1"})
    public void resumableEnabledIsRecorded(final String resume)
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", resume).addAttribute("id", "some-sm-id").addAttribute("max", "90"));

        // Verify result.
        assertTrue(sm.isEnabled(), "Stream Management must be enabled.");
        assertTrue(sm.getResume(), "The stream must be resumable when the peer allows it.");
        assertEquals("some-sm-id", sm.getSmId(), "The SM-ID that the peer provided must be recorded.");
        assertEquals(Duration.ofSeconds(90), sm.getPeerMaximumResumptionTime(), "The preferred maximum resumption time of the peer must be recorded.");
    }

    /**
     * Verifies that the stream can be resumed when the peer allows it without a preferred maximum resumption time, and
     * that a value that is not a positive number of seconds is ignored.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-5", "soon"})
    public void invalidMaximumResumptionTimeIsIgnored(final String max)
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        final Element enabled = DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true").addAttribute("id", "some-sm-id");
        if (!max.isEmpty()) {
            enabled.addAttribute("max", max);
        }

        // Execute system under test.
        sm.process(enabled);

        // Verify result.
        assertTrue(sm.getResume(), "The stream must be resumable when the peer allows it, whatever its 'max' attribute.");
        assertNull(sm.getPeerMaximumResumptionTime(), "No preferred maximum resumption time must be recorded for 'max' value: '" + max + "'");
    }

    /**
     * Verifies that the stream cannot be resumed when the peer offers resumption without an SM-ID, which XEP-0198 § 5
     * requires, or does not allow it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"no-id", "blank-id", "false", "absent"})
    public void enabledWithoutResumptionIsNotResumable(final String variant)
    {
        // Setup test fixture.
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        final Element enabled = DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3));
        switch (variant) {
            case "no-id" -> enabled.addAttribute("resume", "true");
            case "blank-id" -> enabled.addAttribute("resume", "true").addAttribute("id", " ");
            case "false" -> enabled.addAttribute("resume", "false").addAttribute("id", "some-sm-id");
            default -> enabled.addAttribute("id", "some-sm-id");
        }

        // Execute system under test.
        sm.process(enabled);

        // Verify result.
        assertTrue(sm.isEnabled(), "Stream Management must be enabled, also without resumption.");
        assertFalse(sm.getResume(), "The stream must not be resumable for an answer that does not allow it: " + enabled.asXML());
        assertNull(sm.getSmId(), "No SM-ID must be recorded for a stream that cannot be resumed.");
    }

    /**
     * Verifies that an offer of resumption is not used when Openfire did not request it, also when resumption on server
     * connections was enabled after the request was sent.
     */
    @Test
    public void resumptionThatWasNotRequestedIsNotUsed()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);

        // Execute system under test.
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)).addAttribute("resume", "true").addAttribute("id", "some-sm-id"));

        // Verify result.
        assertTrue(sm.isEnabled(), "Stream Management must be enabled.");
        assertFalse(sm.getResume(), "The stream must not be resumable, as resumption was not requested.");
    }

    /**
     * Verifies that a stanza is not queued while the connection of the session is open, so that it is sent.
     */
    @Test
    public void stanzaIsNotQueuedWhileConnectionIsOpen()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();

        // Execute system under test.
        final boolean queued = sm.queue(message("1"), false);

        // Verify result.
        assertFalse(queued, "A stanza must not be queued while the connection of the session is open.");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(sm), "No stanza must be queued.");
    }

    /**
     * Verifies that a stanza is queued when the connection of a session of which the stream can be resumed is broken.
     */
    @Test
    public void stanzaIsQueuedWhenConnectionOfResumableStreamIsBroken()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();

        // Execute system under test.
        final boolean queued = sm.queue(message("1"), true);

        // Verify result.
        assertTrue(queued, "A stanza must be queued when the connection of a session of which the stream can be resumed is broken.");
        assertEquals(List.of("1"), UnacknowledgedStanzas.queuedIds(sm), "The stanza must be queued.");
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "A queued stanza must not be tracked as unacknowledged, as it is not sent.");
    }

    /**
     * Verifies that a stanza is not queued when the connection is broken, while the stream cannot be resumed, or while
     * resumption on server connections is disabled.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stanzaIsNotQueuedWhenConnectionOfStreamThatIsNotResumedIsBroken(final boolean resumable)
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumable ? resumableStreamManager() : enabledStreamManager();
        StreamManager.SERVER_RESUME_ENABLED.setValue(false);

        // Execute system under test.
        final boolean queued = sm.queue(message("1"), true);

        // Verify result.
        assertFalse(queued, "A stanza must not be queued for a stream that is not resumed (resumable: " + resumable + ", resumption on server connections enabled: false).");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(sm), "No stanza must be queued.");
    }

    /**
     * Verifies that, once queueing started, stanzas are queued in order, also when the caller reports an open
     * connection, so that a stanza delivered while the session is resumed cannot pass the queued ones.
     */
    @Test
    public void stanzasAreQueuedInOrderOnceQueueingStarted()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.startQueueing();

        // Execute system under test.
        final boolean first = sm.queue(message("1"), true);
        final boolean second = sm.queue(message("2"), false);

        // Verify result.
        assertTrue(first && second, "Every stanza must be queued once queueing started.");
        assertEquals(List.of("1", "2"), UnacknowledgedStanzas.queuedIds(sm), "The stanzas must be queued in the order in which they were delivered.");
    }

    /**
     * Verifies that the stanzas that are queued are routed again when the session closes, after the unacknowledged
     * ones, in order, and that no stanza is queued after that.
     */
    @Test
    public void queuedStanzasAreRoutedAfterUnacknowledgedOnesOnClose()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = resumableStreamManager();
        sm.sentStanza(message("unacked"));
        sm.startQueueing();
        sm.queue(message("queued-1"), true);
        sm.queue(message("queued-2"), true);
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);
        final boolean queuedAfterClose = sm.queue(message("late"), true);

        // Verify result.
        assertEquals(List.of("unacked", "queued-1", "queued-2"), routed(router).stream().map(Packet::getID).toList(), "The unacknowledged stanza must be routed again first, then the queued stanzas, in order.");
        assertNull(routed(router).get(1).getExtension("delay", "urn:xmpp:delay"), "A queued stanza was never sent, and must not get a delay stamp.");
        assertFalse(queuedAfterClose, "No stanza must be queued after the session closed.");
        assertEquals(List.of(), UnacknowledgedStanzas.queuedIds(sm), "No stanza must remain queued after the session closed.");
    }

    /**
     * Returns the stanzas that were routed by the provided router, in order.
     */
    private static List<Packet> routed(final PacketRouter router)
    {
        return mockingDetails(router).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("route"))
            .map(invocation -> (Packet) invocation.getArgument(0))
            .toList();
    }

    /**
     * Returns the stanzas that were delivered on the provided connection, in order.
     */
    private static List<Packet> delivered(final Connection connection) throws Exception
    {
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(connection, atLeast(0)).deliver(captor.capture());
        return captor.getAllValues();
    }

    /**
     * Returns the last element that was delivered to the session of the provided stream manager.
     */
    private static Element lastDelivered(final RequestingStreamManager sm) throws Exception
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sm.session, atLeastOnce().description("Expected something to be delivered to the session.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    private static Element resumed(final String previd, final String h)
    {
        return DocumentHelper.createElement(QName.get("resumed", StreamManager.NAMESPACE_V3)).addAttribute("previd", previd).addAttribute("h", h);
    }

    /**
     * Returns a mock of a detached outgoing server session, of which the provided stream manager manages the stream.
     */
    private static LocalOutgoingServerSession target(final RequestingStreamManager streamManager)
    {
        final LocalOutgoingServerSession target = mock(LocalOutgoingServerSession.class);
        doReturn(streamManager).when(target).getStreamManager();
        return target;
    }

    /**
     * Uses a mocked server, of which the session manager is a mock, and returns it.
     */
    private static XMPPServer useServer()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        //noinspection deprecation
        XMPPServer.setInstance(server);
        return server;
    }

    /**
     * Uses a mocked server, and returns the mock of its packet router.
     */
    private static PacketRouter useServerWithRouter()
    {
        final PacketRouter router = mock(PacketRouter.class);
        final XMPPServer server = useServer();
        doReturn(router).when(server).getPacketRouter();
        return router;
    }

    /**
     * Gives a stanza addresses, so that an error can be returned for it.
     */
    private static Packet addressed(final Packet packet)
    {
        packet.setFrom(new JID("johndoe", Fixtures.XMPP_DOMAIN, "resource"));
        packet.setTo(new JID("remote.example.org"));
        return packet;
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    /**
     * Returns a stream manager of an authenticated outgoing server session, on which Openfire has not asked for Stream
     * Management.
     */
    private static RequestingStreamManager newStreamManager()
    {
        final LocalOutgoingServerSession session = mock(LocalOutgoingServerSession.class);
        doReturn(mock(Connection.class)).when(session).getConnection();
        doReturn(true).when(session).isAuthenticated();
        return new RequestingStreamManager(session);
    }

    /**
     * Returns a stream manager of an authenticated outgoing server session, on which the peer enabled Stream Management
     * with a stream that can be resumed, at the request of Openfire, with resumption on server connections enabled.
     */
    private static RequestingStreamManager resumableStreamManager()
    {
        return resumableStreamManager(StreamManager.NAMESPACE_V3);
    }

    /**
     * Returns a stream manager as {@link #resumableStreamManager()} does, using the provided version of Stream
     * Management.
     */
    private static RequestingStreamManager resumableStreamManager(final String namespace)
    {
        StreamManager.SERVER_ACTIVE.setValue(true);
        StreamManager.SERVER_RESUME_ENABLED.setValue(true);
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(namespace);
        sm.process(DocumentHelper.createElement(QName.get("enabled", namespace)).addAttribute("resume", "true").addAttribute("id", "some-sm-id"));
        assertTrue(sm.getResume(), "Test fixture expects the stream to be resumable.");
        return sm;
    }

    /**
     * Returns a stream manager of an authenticated outgoing server session, on which the peer enabled Stream Management
     * at the request of Openfire.
     */
    private static RequestingStreamManager enabledStreamManager()
    {
        final RequestingStreamManager sm = newStreamManager();
        sm.requestEnable(StreamManager.NAMESPACE_V3);
        sm.process(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));
        assertTrue(sm.isEnabled(), "Test fixture expects Stream Management to be enabled.");
        return sm;
    }
}
