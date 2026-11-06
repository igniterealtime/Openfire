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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.RemoteClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify how {@link StreamManager} resumes a stream, refuses to resume one, and handles the stanzas
 * that are unacknowledged when a stream is not resumed (XEP-0198 § 4 and § 5).
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerResumptionTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    private static final JID OTHER_ENTITY = new JID("janedoe", Fixtures.XMPP_DOMAIN, "other-resource", true);
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);
    private static final String STREAM_ID = "test-stream-id";

    private RoutingTable routingTable;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        routingTable = mock(RoutingTable.class);
        doReturn(routingTable).when(server).getRoutingTable();

        //noinspection deprecation
        XMPPServer.setInstance(server);
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that a resume request for a detached session that can be resumed reattaches that session to the
     * connection of the session that requested the resumption, using the value of 'h' from the request.
     */
    @Test
    void resumeRequestReattachesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        verify(original, times(1).description("The session must be reattached to the connection of the session that requested the resumption.")).reattach(resuming, 0L);
        verify(resuming, never().description("No failure is to be reported for a resumption that succeeds.")).deliverRawText(anyString());
    }

    /**
     * Verifies that a resume request for a session of which the former stream is still open closes that stream with a
     * 'conflict' stream error, as XEP-0198 § 5 recommends, and reattaches the session.
     */
    @Test
    void resumeRequestForSessionWithOpenStreamClosesThatStream() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final Connection formerConnection = mock(Connection.class);
        doReturn(false).when(original).isDetached();
        doReturn(formerConnection).when(original).getConnection();
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(formerConnection, times(1).description("The former stream that is still open must be closed.")).close(captor.capture());
        assertEquals(StreamError.Condition.conflict, captor.getValue().getCondition(), "The former stream must be closed with a 'conflict' stream error (XEP-0198 § 5).");
        verify(original, times(1).description("The session must be reattached to the connection of the session that requested the resumption.")).reattach(resuming, 0L);
    }

    /**
     * Verifies that the {@code <resumed/>} element identifies the former stream with its SM-ID, in the namespace that
     * the stream used, as XEP-0198 § 5 requires.
     */
    @ParameterizedTest
    @ValueSource(strings = {StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3})
    void resumedElementIdentifiesFormerStream(final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, namespace);
        final Connection connection = reattached(original);

        // Execute system under test.
        original.getStreamManager().onResume(SERVER_ADDRESS, 0);

        // Verify result.
        final Element resumed = firstRawTextDelivered(connection);
        assertEquals(QName.get("resumed", namespace), resumed.getQName(), "The response must be a 'resumed' element in the namespace of the stream.");
        assertEquals(previd, resumed.attributeValue("previd"), "The 'previd' attribute must be the SM-ID of the former stream.");
    }

    /**
     * Verifies that the 'h' attribute of the {@code <resumed/>} element is the number of stanzas that were handled on
     * the former stream, which is zero when none were handled (XEP-0198 § 5).
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void resumedElementReportsNumberOfHandledStanzas(final int handled) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        for (int i = 0; i < handled; i++) {
            sm.incrementServerProcessedStanzas();
        }
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        assertEquals(String.valueOf(handled), firstRawTextDelivered(connection).attributeValue("h"), "The 'h' attribute must be the number of stanzas that were handled on the former stream.");
    }

    /**
     * Verifies that the stanzas that the 'h' value of a resume request does not acknowledge are sent again, in order,
     * as XEP-0198 § 5 requires, and that they remain unacknowledged until the client acknowledges them.
     */
    @Test
    void unacknowledgedStanzasAreSentAgainOnResumption() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 1);

        // Verify result.
        assertEquals(List.of("2", "3"), deliveredIds(connection), "The stanzas that were not acknowledged must be sent again, in order.");
        assertEquals(List.of("2", "3"), unacknowledgedMessageIds(sm), "The stanzas that were sent again must remain unacknowledged.");
    }

    /**
     * Verifies that no stanzas are sent again on resumption when the 'h' value of the resume request acknowledges all of
     * them.
     */
    @Test
    void acknowledgedStanzasAreNotSentAgainOnResumption() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 2);

        // Verify result.
        verify(connection, never().description("Stanzas that were acknowledged in the resume request must not be sent again.")).deliver(any());
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain unacknowledged after the resume request acknowledged all of them.");
    }

    /**
     * Verifies that all kinds of stanzas are sent again on resumption, as XEP-0198 § 5 requires for any stanza that is
     * still unhandled.
     */
    @Test
    void stanzasOfAllKindsAreSentAgainOnResumption() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        sm.sentStanza(message("message"));
        sm.sentStanza(presence("presence"));
        sm.sentStanza(iq("iq", IQ.Type.get));
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        assertEquals(List.of("message", "presence", "iq"), deliveredIds(connection), "Every stanza that was not acknowledged must be sent again, whatever its kind.");
    }

    /**
     * Verifies that the {@code <resumed/>} element is sent before the stanzas that are sent again, as XEP-0198 § 5 has
     * them sent after the 'h' value of the response was processed.
     */
    @Test
    void resumedElementIsSentBeforeStanzasAreSentAgain() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        sm.sentStanza(message("1"));
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        final List<Invocation> resumed = invocations(connection, "deliverRawText").stream().filter(i -> isElement(i.getArgument(0), QName.get("resumed", StreamManager.NAMESPACE_V3))).toList();
        final List<Invocation> sentAgain = invocations(connection, "deliver");
        assertEquals(1, resumed.size(), "The <resumed/> element must be sent once.");
        assertEquals(1, sentAgain.size(), "The unacknowledged stanza must be sent again.");
        assertTrue(resumed.get(0).getSequenceNumber() < sentAgain.get(0).getSequenceNumber(), "The <resumed/> element must be sent before the stanzas that are sent again.");
    }

    /**
     * Verifies that unacknowledged messages and presences that are sent again on resumption have a delay element
     * (XEP-0203) with the time at which they were originally sent.
     */
    @Test
    void stanzasSentAgainOnResumptionHaveDelayElement() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        final Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        sm.sentStanza(message("message"));
        sm.sentStanza(presence("presence"));
        final Instant after = Instant.now();
        // The stamp has millisecond precision. Waiting makes a stamp of the time of resending fall after 'after'.
        Thread.sleep(2);
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(connection, times(2).description("Both unacknowledged stanzas must be sent again.")).deliver(captor.capture());
        for (final Packet sentAgain : captor.getAllValues()) {
            final Element delay = sentAgain.getElement().element(QName.get("delay", "urn:xmpp:delay"));
            assertNotNull(delay, "A stanza that is sent again must have a delay element (XEP-0203), but was: " + sentAgain.toXML());
            assertEquals(SERVER_ADDRESS.toBareJID(), delay.attributeValue("from"), "The delay element must name this server, but was: " + delay.asXML());
            final Instant stamp = new XMPPDateTimeFormat().parseString(delay.attributeValue("stamp")).toInstant();
            assertFalse(stamp.isBefore(before) || stamp.isAfter(after), "The delay element must have the time at which the stanza was originally sent, but was: " + stamp);
        }
    }

    /**
     * Verifies that an acknowledgement is requested after the unacknowledged stanzas were sent again on resumption, so
     * that the peer reports which of them it handled.
     */
    @Test
    void acknowledgementIsRequestedAfterStanzasAreSentAgain() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final StreamManager sm = original.getStreamManager();
        sm.sentStanza(message("1"));
        final Connection connection = reattached(original);

        // Execute system under test.
        sm.onResume(SERVER_ADDRESS, 0);

        // Verify result.
        final List<Invocation> sentAgain = invocations(connection, "deliver");
        final List<Invocation> requests = invocations(original, "deliverRawText").stream().filter(i -> isElement(i.getArgument(0), QName.get("r", StreamManager.NAMESPACE_V3))).toList();
        assertEquals(1, sentAgain.size(), "The unacknowledged stanza must be sent again.");
        assertEquals(1, requests.size(), "An acknowledgement must be requested once.");
        assertTrue(sentAgain.get(0).getSequenceNumber() < requests.get(0).getSequenceNumber(), "The acknowledgement must be requested after the stanzas were sent again.");
    }

    /**
     * Verifies that a resume request with a 'previd' that does not identify a session is refused with a
     * {@code <failed/>} element that has the 'item-not-found' condition, as XEP-0198 § 5 recommends.
     */
    @Test
    void resumeRequestForUnknownSessionIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        doReturn(null).when(routingTable).getClientRoute(FULL_JID);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.item_not_found);
        verify(original, never().description("No session must be resumed.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request with a 'previd' for the full JID of a session, but of another stream, is refused
     * with a {@code <failed/>} element that has the 'item-not-found' condition. That happens when the former session
     * timed out, and the user connected again with the same resource (XEP-0198 § 5).
     */
    @Test
    void resumeRequestForOtherStreamOfSameAddressIsRefusedWithItemNotFound() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final String previdOfOtherStream = Base64.getEncoder().encodeToString((FULL_JID.getResource() + "\0" + "other-stream-id").getBytes(StandardCharsets.UTF_8));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previdOfOtherStream, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.item_not_found);
        verify(original, never().description("A session of another stream must not be resumed.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request with a 'previd' that cannot be decoded is refused with a {@code <failed/>} element.
     * The condition currently is 'unexpected-request'. This deviates from XEP-0198 § 5, which says that the condition
     * SHOULD be 'item-not-found' when the 'previd' is not recognized as an earlier session.
     */
    @Test
    void resumeRequestWithMalformedPrevidIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, "not base64!", 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("No session must be resumed for a 'previd' that cannot be decoded.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request for a session that is connected to another cluster node is refused, as a session
     * can only be resumed on the cluster node that it was connected to. XEP-0198 does not define the condition for this;
     * it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestForSessionOnOtherClusterNodeIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final RemoteClientSession remote = mock(RemoteClientSession.class);
        doReturn(remote).when(routingTable).getClientRoute(FULL_JID);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("No session must be resumed.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request from an anonymous session is refused. XEP-0198 does not define the condition for
     * this; it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestFromAnonymousSessionIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();
        final AuthToken authToken = resuming.getAuthToken();
        doReturn(true).when(authToken).isAnonymous();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("No session must be resumed from an anonymous session.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request from a session that did not authenticate is refused, as XEP-0198 § 10 does not
     * allow a session to be resumed before the client authenticated. XEP-0198 does not define the condition for this; it
     * currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestBeforeAuthenticationIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();
        doReturn(null).when(resuming).getAuthToken();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("No session must be resumed before the client authenticated.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request from a session that already bound a resource is refused, as resumption replaces
     * resource binding (XEP-0198 § 3). XEP-0198 does not define the condition for this; it currently is
     * 'unexpected-request'.
     */
    @Test
    void resumeRequestFromSessionWithBoundResourceIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();
        doReturn(true).when(resuming).isAuthenticated();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("No session must be resumed from a session that already bound a resource.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request for a session that is being closed is refused (OF-2811). XEP-0198 does not define
     * the condition for this; it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestForClosedSessionIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        doReturn(true).when(original).isClosed();
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("A session that is being closed must not be resumed.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request for a stream of which resumption was disabled, such as one that was closed
     * formally, is refused. XEP-0198 does not define the condition for this; it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestForStreamWithResumptionDisabledIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        original.getStreamManager().formalClose();
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("A stream of which resumption was disabled must not be resumed.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request in another namespace than the one of the former stream is refused. XEP-0198 does
     * not define the condition for this; it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestInOtherNamespaceIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V2, previd, 0));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V2, PacketError.Condition.unexpected_request);
        verify(original, never().description("A stream must not be resumed in another namespace.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a resume request with a value of 'h' that acknowledges more stanzas than were sent is refused with a
     * {@code <failed/>} element, as XEP-0198 § 6 requires for an error with regard to a resume request. XEP-0198 does not
     * define the condition for this; it currently is 'unexpected-request'.
     */
    @Test
    void resumeRequestWithValueAboveNumberOfStanzasSentIsRefused() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        original.getStreamManager().sentStanza(message("1"));
        original.getStreamManager().sentStanza(message("2"));
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        new StreamManager(resuming).process(resume(StreamManager.NAMESPACE_V3, previd, 3));

        // Verify result.
        assertFailed(lastDelivered(resuming), StreamManager.NAMESPACE_V3, PacketError.Condition.unexpected_request);
        verify(original, never().description("A stream must not be resumed with a value of 'h' that is too high.")).reattach(any(), anyLong());
    }

    /**
     * Verifies that a refused resume request leaves the session that requested it open, without Stream Management, so
     * that the client can bind a resource instead, as XEP-0198 § 5 recommends. The session that was to be resumed is not
     * affected.
     */
    @Test
    void refusedResumeRequestAllowsClientToBindResource() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        doReturn(null).when(routingTable).getClientRoute(FULL_JID);
        final LocalClientSession resuming = resumingClientSession();
        final StreamManager sm = new StreamManager(resuming);

        // Execute system under test.
        sm.process(resume(StreamManager.NAMESPACE_V3, previd, 0));

        // Verify result.
        assertFalse(resuming.isClosed(), "The session that requested the resumption must not be closed.");
        assertFalse(sm.isEnabled(), "Stream Management must not be enabled on the session that requested the resumption.");
        assertFalse(original.isClosed(), "The session that was to be resumed must not be closed.");
    }

    /**
     * Verifies that an inline resume request (XEP-0198 § 9.2) that is refused returns a {@code <failed/>} element for
     * the caller to include in its response, without delivering anything itself, so that the client can proceed with
     * resource binding.
     */
    @Test
    void refusedInlineResumeRequestReturnsFailedElement() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession original = resumableClientSession();
        final String previd = enableResumption(original, StreamManager.NAMESPACE_V3);
        doReturn(null).when(routingTable).getClientRoute(FULL_JID);
        final LocalClientSession resuming = resumingClientSession();

        // Execute system under test.
        final Sasl2ResumeResult result = new StreamManager(resuming).processSasl2Resume(ResumeRequest.from(resume(StreamManager.NAMESPACE_V3, previd, 0)));

        // Verify result.
        assertFalse(result.isSuccess(), "The inline resume request must be refused.");
        assertFailed(result.getResultElement(), StreamManager.NAMESPACE_V3, PacketError.Condition.item_not_found);
        verify(resuming, never().description("Nothing must be delivered for an inline resume request: the caller includes the outcome in its response.")).deliverRawText(anyString());
        verify(original, never().description("No session must be resumed.")).reattachForSasl2(any());
    }

    /**
     * Verifies that unacknowledged messages are routed again, in order, when a stream is not resumed, as XEP-0198 § 4
     * has them treated as stanzas for an unavailable resource.
     */
    @Test
    void unacknowledgedMessagesAreRoutedAgainOnClose() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableClientSession();
        enableResumption(session, StreamManager.NAMESPACE_V3);
        final StreamManager sm = session.getStreamManager();
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of("2"), routedIds(router), "The message that was not acknowledged must be routed again, and the acknowledged one must not.");
    }

    /**
     * Verifies that an unacknowledged message that is routed again when a stream is not resumed has a delay element with
     * the time at which it was originally sent, as XEP-0198 § 4 recommends.
     */
    @Test
    void unacknowledgedMessageRoutedOnCloseHasDelayElement() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableClientSession();
        enableResumption(session, StreamManager.NAMESPACE_V3);
        final StreamManager sm = session.getStreamManager();
        final Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        sm.sentStanza(message("1"));
        final Instant after = Instant.now();
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        final List<Packet> routed = routed(router);
        assertEquals(1, routed.size(), "The unacknowledged message must be routed again, but routed were: " + routed);
        final Element delay = routed.get(0).getElement().element(QName.get("delay", "urn:xmpp:delay"));
        assertNotNull(delay, "The message must have a delay element (XEP-0203), but was: " + routed.get(0).toXML());
        final Instant stamp = new XMPPDateTimeFormat().parseString(delay.attributeValue("stamp")).toInstant();
        assertFalse(stamp.isBefore(before) || stamp.isAfter(after), "The delay element must have the time at which the message was originally sent, but was: " + stamp);
    }

    /**
     * Verifies that an unacknowledged IQ request is answered with a 'service-unavailable' error when a stream is not
     * resumed. XEP-0198 § 4 has it treated as a stanza for an unavailable resource, for which RFC 6121 § 8.5.3.2.3
     * defines that error, and RFC 6120 § 8.2.3 requires a response to every IQ request.
     */
    @Test
    void unacknowledgedIqRequestIsAnsweredWithErrorOnClose() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableClientSession();
        enableResumption(session, StreamManager.NAMESPACE_V3);
        final StreamManager sm = session.getStreamManager();
        sm.sentStanza(iq("request", IQ.Type.get));
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        final List<Packet> routed = routed(router);
        assertEquals(1, routed.size(), "An error must be returned for the unacknowledged IQ request, but routed were: " + routed);
        final IQ error = assertInstanceOf(IQ.class, routed.get(0), "The response to an IQ request must be an IQ.");
        assertEquals(IQ.Type.error, error.getType(), "The response must be an error.");
        assertEquals("request", error.getID(), "The error must have the ID of the request.");
        assertEquals(OTHER_ENTITY, error.getTo(), "The error must be addressed to the sender of the request.");
        assertEquals(PacketError.Condition.service_unavailable, error.getError().getCondition(), "The error must have the 'service-unavailable' condition.");
    }

    /**
     * Verifies that unacknowledged IQ responses are discarded when a stream is not resumed, as RFC 6120 § 8.2.3 does
     * not allow a response to an IQ response.
     */
    @ParameterizedTest
    @ValueSource(strings = {"result", "error"})
    void unacknowledgedIqResponseIsDiscardedOnClose(final String type) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableClientSession();
        enableResumption(session, StreamManager.NAMESPACE_V3);
        final StreamManager sm = session.getStreamManager();
        sm.sentStanza(iq("response", IQ.Type.valueOf(type)));
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of(), routed(router), "Nothing must be routed for an unacknowledged IQ response.");
    }

    /**
     * Verifies that unacknowledged presence is discarded when a stream is not resumed, as RFC 6121 § 8.5.3.2.2 has a
     * server silently ignore presence for an unavailable resource.
     */
    @Test
    void unacknowledgedPresenceIsDiscardedOnClose() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = resumableClientSession();
        enableResumption(session, StreamManager.NAMESPACE_V3);
        final StreamManager sm = session.getStreamManager();
        sm.sentStanza(presence("presence"));
        final PacketRouter router = mock(PacketRouter.class);

        // Execute system under test.
        sm.onClose(router, SERVER_ADDRESS);

        // Verify result.
        assertEquals(List.of(), routed(router), "Nothing must be routed for unacknowledged presence.");
    }

    /**
     * A mock of a detached client session that can be resumed: it has a full JID, a stream ID and a non-anonymous
     * authentication token, and is the route for its full JID.
     */
    private LocalClientSession resumableClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(true).when(session).isAuthenticated();
        doReturn(true).when(session).isDetached();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(BasicStreamIDFactory.createStreamID(STREAM_ID)).when(session).getStreamID();
        doReturn(session).when(routingTable).getClientRoute(FULL_JID);
        mockCloseTracking(session);
        return session;
    }

    /**
     * A mock of a new client session from which a resumption is requested: it authenticated as the same user, but did
     * not bind a resource.
     */
    private static LocalClientSession resumingClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(FULL_JID.getNode()).when(authToken).getUsername();
        doReturn(authToken).when(session).getAuthToken();
        doReturn(false).when(session).isAuthenticated();
        doReturn(Fixtures.XMPP_DOMAIN).when(session).getServerName();
        doReturn(BasicStreamIDFactory.createStreamID("resuming-stream-id")).when(session).getStreamID();
        mockCloseTracking(session);
        return session;
    }

    /**
     * Makes the session report itself as closed once it is closed, through either close() or close(StreamError). A mock
     * does not have one of these invoke the other, so verifying only one of them would miss a close through the other.
     */
    private static void mockCloseTracking(final LocalClientSession session)
    {
        final AtomicBoolean closed = new AtomicBoolean(false);
        doAnswer(invocation -> { closed.set(true); return null; }).when(session).close();
        doAnswer(invocation -> { closed.set(true); return null; }).when(session).close(nullable(StreamError.class));
        doAnswer(invocation -> closed.get()).when(session).isClosed();
    }

    /**
     * Enables Stream Management with resumption on a session, and returns the SM-ID from the {@code <enabled/>} element.
     * The stream manager is then available from the session.
     */
    private static String enableResumption(final LocalClientSession session, final String namespace) throws DocumentException
    {
        final StreamManager sm = new StreamManager(session);
        doReturn(sm).when(session).getStreamManager();
        final Element enable = DocumentHelper.createElement(QName.get("enable", namespace));
        enable.addAttribute("resume", "true");
        sm.process(enable);
        assertTrue(sm.getResume(), "Test fixture expects a resumable stream.");
        final String smId = lastDelivered(session).attributeValue("id");
        clearInvocations(session);
        return smId;
    }

    /**
     * Gives a session a new connection, as reattaching it to the connection of the session that resumed it does.
     */
    private static Connection reattached(final LocalClientSession session)
    {
        final Connection connection = mock(Connection.class);
        doReturn(false).when(session).isDetached();
        doReturn(connection).when(session).getConnection();
        return connection;
    }

    private static Element resume(final String namespace, final String previd, final long h)
    {
        return DocumentHelper.createElement(QName.get("resume", namespace)).addAttribute("previd", previd).addAttribute("h", String.valueOf(h));
    }

    private static Element ack(final String namespace, final String h)
    {
        return DocumentHelper.createElement(QName.get("a", namespace)).addAttribute("h", h);
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        message.setFrom(OTHER_ENTITY);
        message.setTo(FULL_JID);
        return message;
    }

    private static Presence presence(final String id)
    {
        final Presence presence = new Presence();
        presence.setID(id);
        presence.setFrom(OTHER_ENTITY);
        presence.setTo(FULL_JID);
        return presence;
    }

    private static IQ iq(final String id, final IQ.Type type)
    {
        final IQ iq = new IQ(type, id);
        iq.setFrom(OTHER_ENTITY);
        iq.setTo(FULL_JID);
        if (type == IQ.Type.get) {
            iq.setChildElement("query", "jabber:iq:version");
        }
        return iq;
    }

    /**
     * Returns the last element that was delivered to the session as raw text.
     */
    private static Element lastDelivered(final LocalSession session) throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce().description("Expected something to be delivered to the session.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    /**
     * Returns the invocations of a method of a mock, in the order in which they were made. Their sequence numbers
     * order them relative to invocations on other mocks.
     */
    private static List<Invocation> invocations(final Object mock, final String methodName)
    {
        return mockingDetails(mock).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals(methodName))
            .toList();
    }

    /**
     * Checks whether raw text is an XML element with the given name.
     */
    private static boolean isElement(final String xml, final QName name)
    {
        try {
            return DocumentHelper.parseText(xml).getRootElement().getQName().equals(name);
        } catch (DocumentException e) {
            return false;
        }
    }

    /**
     * Returns the first element that was delivered to the connection as raw text.
     */
    private static Element firstRawTextDelivered(final Connection connection) throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(connection, atLeastOnce().description("Expected something to be delivered to the connection.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getAllValues().get(0)).getRootElement();
    }

    /**
     * Returns the IDs of the stanzas that were delivered to the connection, in order.
     */
    private static List<String> deliveredIds(final Connection connection) throws Exception
    {
        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(connection, atLeast(0)).deliver(captor.capture());
        return captor.getAllValues().stream().map(Packet::getID).toList();
    }

    /**
     * Returns the stanzas that were routed, in order, through any of the methods of the router that route a stanza.
     */
    private static List<Packet> routed(final PacketRouter router)
    {
        return mockingDetails(router).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("route"))
            .map(invocation -> (Packet) invocation.getArgument(0))
            .toList();
    }

    /**
     * Returns the IDs of the stanzas that were routed, in order.
     */
    private static List<String> routedIds(final PacketRouter router)
    {
        return routed(router).stream().map(Packet::getID).toList();
    }

    /**
     * Returns the IDs of the messages that are unacknowledged, in order. This closes the stream manager, as it uses the
     * routing of unacknowledged messages on close to observe them.
     */
    private static List<String> unacknowledgedMessageIds(final StreamManager sm)
    {
        final PacketRouter router = mock(PacketRouter.class);
        sm.onClose(router, SERVER_ADDRESS);
        return routedIds(router);
    }

    private static void assertFailed(final Element failed, final String namespace, final PacketError.Condition condition)
    {
        assertEquals(QName.get("failed", namespace), failed.getQName(), "The response must be a 'failed' element in the namespace of the request, but was: " + failed.asXML());
        assertNotNull(failed.element(QName.get(condition.toXMPP(), "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the '" + condition.toXMPP() + "' condition, but was: " + failed.asXML());
    }
}
