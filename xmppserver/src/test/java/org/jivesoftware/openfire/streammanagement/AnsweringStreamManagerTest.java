/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.StreamError;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link AnsweringStreamManager}, the end of a stream that answers
 * requests of the peer.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class AnsweringStreamManagerTest extends AbstractStreamManagerTest
{
    /**
     * Verifies that {@link AnsweringStreamManager#detachIfNeeded(LocalSession)} closes the pre-existing session's
     * connection with a 'conflict' {@link StreamError}, as recommended by XEP-0198 §5 for a former stream that is still
     * open when it is superseded by a resumed session.
     */
    @Test
    void closesOldConnectionWithAConflictStreamError()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        final Connection oldConnection = mock(Connection.class);
        when(otherSession.isDetached()).thenReturn(false);
        when(otherSession.getConnection()).thenReturn(oldConnection);
        final LocalSession tempSession = mock(LocalSession.class); // owns this StreamManager
        final AnsweringStreamManager sm = new AnsweringStreamManager(tempSession);

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        verify(otherSession, times(1).description("The pre-existing session must be marked detached before its connection is handed off to the resumed session.")).setDetached();

        final ArgumentCaptor<StreamError> errorCaptor = ArgumentCaptor.forClass(StreamError.class);
        verify(oldConnection, times(1).description("The pre-existing connection must be closed exactly once as part of the hand-off.")).close(errorCaptor.capture());
        final StreamError closeError = errorCaptor.getValue();
        assertNotNull(closeError, "The pre-existing connection must be closed with a StreamError, as recommended by XEP-0198 §5 for a former stream that is still open when superseded by a resumed session.");
        assertEquals(StreamError.Condition.conflict, closeError.getCondition(), "The StreamError used to close a superseded connection must use the 'conflict' condition, per the example in XEP-0198 §5.");
    }

    /**
     * Verifies that {@link AnsweringStreamManager#detachIfNeeded(LocalSession)} is a no-op for a session
     * that is already detached, leaving its (already absent) connection untouched.
     */
    @Test
    void isANoOpWhenAlreadyDetached()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        when(otherSession.isDetached()).thenReturn(true);
        final AnsweringStreamManager sm = new AnsweringStreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        verify(otherSession, never().description("An already-detached session has no connection to obtain; detachIfNeeded() must not attempt to read it.")).getConnection();
        verify(otherSession, never().description("An already-detached session must not be detached again.")).setDetached();
    }

    /**
     * Verifies that {@link AnsweringStreamManager#detachIfNeeded(LocalSession)} marks the pre-existing session as detached
     * strictly before closing its connection. This ordering is not incidental: it is what lets
     * NettyConnection#close(StreamError) tell a benign resumption hand-off (session already detached when close() runs)
     * apart from a genuine stream failure (session not yet detached), and so decide whether to disable resumption
     * (OF-2751). If this ordering were ever reversed, that distinction (and with it, the safety of resuming a session
     * across a hand-off) would silently break.
     */
    @Test
    void detachesSessionBeforeClosingItsConnection()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        final Connection oldConnection = mock(Connection.class);
        when(otherSession.isDetached()).thenReturn(false);
        when(otherSession.getConnection()).thenReturn(oldConnection);
        final AnsweringStreamManager sm = new AnsweringStreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        final InOrder inOrder = inOrder(otherSession, oldConnection);
        inOrder.verify(otherSession).setDetached();
        inOrder.verify(oldConnection).close(any(StreamError.class));
    }

    /**
     * Verifies that enabling Stream Management without requesting resumption results in an {@code <enabled/>} element
     * in the namespace of the request, without any of the attributes that relate to resumption.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void enableWithoutResume(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);

        // Execute system under test.
        sm.process(smElement("enable", namespace));

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", namespace), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected when resumption was not requested, but got: " + enabled.asXML());
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertFalse(sm.getResume(), "The stream must not be resumable when resumption was not requested.");
    }

    /**
     * Verifies that enabling Stream Management with resumption results in an {@code <enabled/>} element that contains
     * a resumption ID (based on the resource and stream ID), the cluster node to resume on, and the maximum resumption
     * time in seconds.
     */
    @Test
    void enableWithResumeV3() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V3), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertEquals("true", enabled.attributeValue("resume"), "The response must confirm that the stream is resumable.");
        assertEquals(Base64.getEncoder().encodeToString((FULL_JID.getResource() + "\0" + STREAM_ID).getBytes(StandardCharsets.UTF_8)), enabled.attributeValue("id"), "The resumption ID must be based on the resource and stream ID.");
        assertEquals(Fixtures.HOSTNAME, enabled.attributeValue("location"), "The response must identify the cluster node to resume on.");
        assertEquals(String.valueOf(DETACH_TIME_MILLIS / 1000), enabled.attributeValue("max"), "The response must report the session detach time, in seconds.");
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertTrue(sm.getResume(), "The stream must be resumable.");
    }

    /**
     * Verifies that the location hint is not sent in the older version of the protocol, while the other attributes
     * that relate to resumption are.
     */
    @Test
    void enableWithResumeV2HasNoLocation() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V2);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals(QName.get("enabled", StreamManager.NAMESPACE_V2), enabled.getQName(), "The response must be an 'enabled' element in the namespace of the request.");
        assertEquals("true", enabled.attributeValue("resume"), "The response must confirm that the stream is resumable.");
        assertNotNull(enabled.attributeValue("id"), "The response must contain a resumption ID.");
        assertNull(enabled.attributeValue("location"), "The location hint must not be sent in version 2 of the protocol.");
        assertEquals(String.valueOf(DETACH_TIME_MILLIS / 1000), enabled.attributeValue("max"), "The response must report the session detach time, in seconds.");
        assertTrue(sm.getResume(), "The stream must be resumable.");
    }

    /**
     * Verifies that the location hint is left out when it is disabled by configuration.
     */
    @Test
    void enableWithResumeOmitsLocationWhenDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.LOCATION_ENABLED.setValue(false);
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertNull(lastDelivered(session).attributeValue("location"), "The location hint must not be sent when it is disabled by configuration.");
    }

    /**
     * Verifies that the maximum resumption time is left out when it is disabled by configuration.
     */
    @Test
    void enableWithResumeOmitsMaxWhenDisabled() throws Exception
    {
        // Setup test fixture.
        StreamManager.MAX_SERVER_ENABLED.setValue(false);
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertNull(lastDelivered(session).attributeValue("max"), "The maximum resumption time must not be sent when it is disabled by configuration.");
    }

    /**
     * Verifies which values of the 'resume' attribute request resumption.
     */
    @ParameterizedTest
    @CsvSource({"true,true", "TRUE,true", "yes,true", "YES,true", "1,true", "false,false", "0,false", "no,false", "foobar,false"})
    void enableInterpretsResumeAttribute(final String value, final boolean expected) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", value);

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        assertEquals(expected, sm.getResume(), "Unexpected interpretation of resume attribute value '" + value + "'.");
        assertEquals(expected ? "true" : null, lastDelivered(session).attributeValue("resume"), "The response must reflect the interpretation of resume attribute value '" + value + "'.");
    }

    /**
     * Verifies that an anonymous session can enable Stream Management, but does not get a resumable stream.
     */
    @Test
    void enableWithResumeForAnonymousSessionIsNotResumable() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = session.getAuthToken();
        doReturn(true).when(authToken).isAnonymous();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals("enabled", enabled.getName(), "An anonymous session must be able to enable Stream Management.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected for an anonymous session, but got: " + enabled.asXML());
        assertTrue(sm.isEnabled(), "Stream Management must be enabled after a successful request.");
        assertFalse(sm.getResume(), "The stream of an anonymous session must not be resumable.");
    }

    /**
     * Verifies that a session that is not a client session can enable Stream Management, but does not get a resumable
     * stream.
     */
    @Test
    void enableWithResumeForNonClientSessionIsNotResumable() throws Exception
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);
        doReturn(true).when(session).isAuthenticated();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");

        // Execute system under test.
        sm.process(enable);

        // Verify result.
        final Element enabled = lastDelivered(session);
        assertEquals("enabled", enabled.getName(), "A session that is not a client session must be able to enable Stream Management.");
        assertTrue(enabled.attributes().isEmpty(), "No resumption attributes are expected for a session that is not a client session, but got: " + enabled.asXML());
        assertFalse(sm.getResume(), "The stream of a session that is not a client session must not be resumable.");
    }

    /**
     * Verifies that Stream Management cannot be enabled before the session is authenticated.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void enableBeforeAuthenticationFails(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        doReturn(false).when(session).isAuthenticated();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);

        // Execute system under test.
        sm.process(smElement("enable", namespace));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(session), namespace);
        assertFalse(sm.isEnabled(), "Stream Management must not be enabled on a session that is not authenticated.");
    }

    /**
     * Verifies that a second request to enable Stream Management fails, leaving the state from the first request intact.
     */
    @Test
    void enableTwiceFails() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("enable", StreamManager.NAMESPACE_V2));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(session), StreamManager.NAMESPACE_V2);
        assertTrue(sm.isEnabled(), "Stream Management must remain enabled after a second request failed.");

        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final Element ack = lastDelivered(session);
        assertEquals(QName.get("a", StreamManager.NAMESPACE_V3), ack.getQName(), "The namespace of the first request must remain in use.");
        assertEquals("1", ack.attributeValue("h"), "The count of handled stanzas must not be affected by a second request.");
    }

    /**
     * Verifies that a Stream Management element that the end that answers does not process, such as one that only the
     * end that asks receives, is answered with a 'failed' element that reports 'unexpected-request'.
     */
    @ParameterizedTest
    @ValueSource(strings = {"enabled", "resumed", "failed", "unknown"})
    void elementThatIsNotProcessedFails(final String name) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(smElement(name, StreamManager.NAMESPACE_V3));

        // Verify result.
        assertFailedWithUnexpectedRequest(lastDelivered(session), StreamManager.NAMESPACE_V3);
    }

    /**
     * Verifies that the count in a {@code <resumed/>} element rolls over like the count in {@code <a/>}, as XEP-0198 § 4
     * requires 'h' to be reset from 2^32-1 back to zero.
     */
    @Test
    void countInResumedElementRollsOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final AnsweringStreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        final Element result = sm.buildResumedElement();

        // Verify result.
        assertEquals("0", result.attributeValue("h"), "The count in a 'resumed' element must roll over to zero after 2^32-1.");
    }

    /**
     * Verifies that a resume request with a value of 'h' lower than the last acknowledged value is accepted when no
     * stanzas are unacknowledged. A client can send such a value when it restores a count that is older than its last
     * acknowledgement. XEP-0198 § 5 has 'h' in a resume request handled as in an {@code <a/>}, for which a lower value
     * does not acknowledge any new stanzas (§ 4).
     */
    @Test
    void resumeWithLowerValueIsAcceptedWhenNoStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new AnsweringStreamManager(resuming).process(resume(previd, 1));

        // Verify result.
        verify(original, times(1).description("The session must be resumed, as a lower value of 'h' is not too high.")).reattach(resuming, 1L);
        verify(resuming, never().description("No failure is to be reported for a lower value of 'h'.")).deliverRawText(anyString());
    }

    /**
     * Verifies that a resume request with a value of 'h' lower than the last acknowledged value is accepted when stanzas
     * are unacknowledged, and that the stanzas that remain unacknowledged are sent again on resumption, without the lower
     * value affecting the stanzas that the client acknowledges after that.
     */
    @Test
    void resumeWithLowerValueRedeliversUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        final LocalClientSession resuming = resumingClientSession();
        new AnsweringStreamManager(resuming).process(resume(previd, 1));
        verify(original, times(1).description("Test fixture expects the session to be resumed.")).reattach(resuming, 1L);
        final Connection newConnection = mock(Connection.class);
        doReturn(newConnection).when(original).getConnection();

        // Execute system under test.
        sm.redeliverUnackedStanzas(SERVER_ADDRESS, 1);
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(newConnection, times(1).description("Only the stanza that remained unacknowledged is to be sent again.")).deliver(captor.capture());
        assertEquals("3", captor.getValue().getID(), "The stanza that remained unacknowledged is to be sent again.");
        assertFalse(original.isClosed(), "Acknowledging the stanza that was sent again must not close the session.");
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after the stanza that was sent again is acknowledged.");
    }

    /**
     * Verifies that a resume request with a value of 'h' that is too high is refused after the acknowledged count rolled
     * over, as it is at any other time.
     */
    @Test
    void resumeWithValueAboveNumberOfStanzasSentIsRefusedAfterRollOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new AnsweringStreamManager(resuming).process(resume(previd, 5));

        // Verify result.
        verify(original, never().description("The session must not be resumed with a value of 'h' that is too high.")).reattach(any(), anyLong());
        assertFailedWithUnexpectedRequest(lastDelivered(resuming), StreamManager.NAMESPACE_V3);
    }

    /**
     * Verifies that a resume request with a value of 'h' from before the roll-over, which is lower than an earlier
     * acknowledgement that rolled over, is accepted.
     */
    @Test
    void resumeWithLowerValueFromBeforeRollOverIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final StreamManager sm = streamManagerWithResumption(original);
        final String previd = lastDelivered(original).attributeValue("id");
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new AnsweringStreamManager(resuming).process(resume(previd, StreamManager.MASK));

        // Verify result.
        verify(original, times(1).description("The session must be resumed, as a value from before the roll-over is not too high.")).reattach(resuming, StreamManager.MASK);
        verify(resuming, never().description("No failure is to be reported for a value from before the roll-over.")).deliverRawText(anyString());
    }

    /**
     * Verifies that an IQ request in a batch that is delivered after the stream was closed for exceeding the maximum
     * number of unacknowledged stanzas is answered with an error when the session closes. The fallback of the closed
     * connection drops IQ stanzas, so the request must remain tracked, as RFC 6120 § 8.2.3 requires a response to every
     * IQ request.
     */
    @Test
    void iqRequestInBatchAfterExceedingMaximumIsAnsweredWhenSessionCloses() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");
        final IQ request = new IQ(IQ.Type.get, "3");
        request.setFrom(new JID("contact", Fixtures.XMPP_DOMAIN, "desktop"));
        request.setTo(new JID("user", Fixtures.XMPP_DOMAIN, "mobile"));
        request.setChildElement("query", "jabber:iq:version");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), request));

        // Verify result.
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        final PacketRouter router = mock(PacketRouter.class);
        session.getStreamManager().onClose(router, SERVER_ADDRESS);
        final ArgumentCaptor<IQ> captor = ArgumentCaptor.forClass(IQ.class);
        verify(router, atLeast(0)).route(captor.capture());
        assertEquals(1, captor.getAllValues().size(), "Exactly one response must be routed for the IQ request, but these were routed: " + captor.getAllValues());
        final IQ error = captor.getValue();
        assertEquals(IQ.Type.error, error.getType(), "The response to the IQ request must be an error.");
        assertEquals("3", error.getID(), "The response must have the ID of the IQ request.");
        assertEquals(request.getFrom(), error.getTo(), "The response must be addressed to the sender of the IQ request.");
        assertEquals(PacketError.Condition.service_unavailable, error.getError().getCondition(), "The response must report the condition for an unavailable resource.");
    }
}
