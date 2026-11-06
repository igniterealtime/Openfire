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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify what {@link RequestingStreamManager}, the end of a stream that asks for Stream Management,
 * does when its session closes or would be resumed, and when it reports that negotiation has finished. How it asks for Stream Management, and how it answers the peer, is
 * verified through the stanza handler of outgoing server connections, in RespondingServerStanzaHandlerStreamManagementTest.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class RequestingStreamManagerTest
{
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);

    /**
     * Verifies that Stream Management is disabled when the session closes, without routing anything, as unacknowledged
     * stanzas of connections that Openfire opened are not handled yet.
     */
    @Test
    public void closeDisablesStreamManagementWithoutRouting()
    {
        // Setup test fixture.
        final RequestingStreamManager sm = enabledStreamManager();
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        assertFalse(sm.isEnabled(), "Stream Management must be disabled when the session closes.");
        verifyNoInteractions(router);
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
