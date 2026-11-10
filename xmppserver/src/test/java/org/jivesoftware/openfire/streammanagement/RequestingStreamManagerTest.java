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
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify what {@link RequestingStreamManager}, the end of a stream that asks for Stream Management,
 * does when its session closes or would be resumed, when it reports that negotiation has finished, and how it counts
 * stanzas that are sent while a request to enable Stream Management awaits an answer. How it asks for Stream Management, and how it answers the peer, is
 * verified through the stanza handler of outgoing server connections, in RespondingServerStanzaHandlerStreamManagementTest.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RequestingStreamManagerTest
{
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);

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
     * Verifies that resuming the session is refused, as a stream on which Openfire asks for Stream Management cannot be
     * resumed.
     */
    @Test
    public void resumeIsRefused()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = enabledStreamManager();

        // Execute system under test & verify result.
        assertThrows(UnsupportedOperationException.class, () -> sm.onResume(SERVER_ADDRESS, 0), "Resuming a stream on which Openfire asks for Stream Management must be refused.");
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
     * Returns the stanzas that were routed by the provided router, in order.
     */
    private static List<Packet> routed(final PacketRouter router)
    {
        return mockingDetails(router).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("route"))
            .map(invocation -> (Packet) invocation.getArgument(0))
            .toList();
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
