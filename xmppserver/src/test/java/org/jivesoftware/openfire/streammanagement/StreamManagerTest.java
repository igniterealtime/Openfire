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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link StreamManager}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerTest
{
    private static final JID FULL_JID = new JID("johndoe", Fixtures.XMPP_DOMAIN, "test-resource", true);
    private static final JID SERVER_ADDRESS = new JID(Fixtures.XMPP_DOMAIN);
    private static final String STREAM_ID = "test-stream-id";

    // OF-3433: Names of private fields, used to reach the roll-over at 2^32 without sending that many stanzas.
    private static final String HANDLED_COUNTER_FIELD = "serverProcessedStanzas";
    private static final String ACKNOWLEDGED_COUNTER_FIELD = "clientProcessedStanzas";

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @Test
    public void testValidateClientAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final long h = 0;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = 1L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanzaWithMoreInflight() throws Exception
    {
        // Setup test fixture.
        final long h = 10;
        final long oldH = 4;
        final Long lastUnackedX = 12L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanzaA() throws Exception
    {
        // Setup test fixture.
        final long h = 3;
        final long oldH = 1;
        final Long lastUnackedX = 2L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX - 1;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }


    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_sent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase1() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase2() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX-2;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX - 2;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 3L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 3;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 4;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 5;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Verifies that {@link StreamManager#detachIfNeeded(LocalClientSession)} closes the pre-existing session's
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
        final StreamManager sm = new StreamManager(tempSession);

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
     * Verifies that {@link StreamManager#detachIfNeeded(LocalClientSession)} is a no-op for a session
     * that is already detached, leaving its (already absent) connection untouched.
     */
    @Test
    void isANoOpWhenAlreadyDetached()
    {
        // Setup test fixture.
        final LocalClientSession otherSession = mock(LocalClientSession.class);
        when(otherSession.isDetached()).thenReturn(true);
        final StreamManager sm = new StreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        verify(otherSession, never().description("An already-detached session has no connection to obtain; detachIfNeeded() must not attempt to read it.")).getConnection();
        verify(otherSession, never().description("An already-detached session must not be detached again.")).setDetached();
    }

    /**
     * Verifies that {@link StreamManager#detachIfNeeded(LocalClientSession)} marks the pre-existing session as detached
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
        final StreamManager sm = new StreamManager(mock(LocalSession.class));

        // Execute system under test.
        sm.detachIfNeeded(otherSession);

        // Verify results.
        final InOrder inOrder = inOrder(otherSession, oldConnection);
        inOrder.verify(otherSession).setDetached();
        inOrder.verify(oldConnection).close(any(StreamError.class));
    }

    /**
     * Verifies that an {@code <a/>} removes the stanzas that it acknowledges, and no others.
     */
    @Test
    void acknowledgementRemovesAcknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of("3"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
        verify(session, never().description("A valid acknowledgement must not close the session.")).close();
    }

    /**
     * Verifies that an {@code <a/>} that covers all stanzas that were sent leaves nothing unacknowledged.
     */
    @Test
    void acknowledgementOfAllStanzasRemovesAll() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after all of them were acknowledged.");
        verify(session, never().description("A valid acknowledgement must not close the session.")).close();
    }

    /**
     * Verifies that stanzas are numbered on from the last acknowledged value, after all earlier stanzas were
     * acknowledged.
     */
    @Test
    void stanzasAreNumberedOnAfterAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertEquals(List.of("4"), unacknowledgedMessageIds(sm), "The stanzas that were sent after an acknowledgement must be numbered on from its value.");
    }

    /**
     * Verifies that repeating the last acknowledgement has no effect, also when no stanzas remain.
     */
    @Test
    void repeatedAcknowledgementIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        verify(session, never().description("Repeating an acknowledgement must not close the session.")).close();
    }

    /**
     * Verifies that acknowledging a stanza while none were sent closes the session with a stream error.
     */
    @Test
    void acknowledgementOfStanzaWhenNoneWereSentClosesSession()
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, "You acknowledged stanzas that we didn't send.");
    }

    /**
     * Verifies that acknowledging more stanzas than were sent closes the session with a stream error, without removing
     * any stanzas.
     */
    @Test
    void acknowledgementOfMoreStanzasThanSentClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertSessionClosedWithStreamError(session, "You acknowledged stanzas that we didn't send.");
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that the stream error for a value of 'h' that is too high contains the element that XEP-0198 § 6 defines
     * for it, which reports that value and the number of stanzas that were sent.
     */
    @Test
    void acknowledgementOfMoreStanzasThanSentReportsHandledCountTooHigh() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        final String streamError = assertSessionClosedWithStreamError(session);
        assertHandledCountTooHigh(streamError, "3", "2");
    }

    /**
     * Verifies that a value of 'h' that is far above the number of stanzas that were sent is recognised as too high
     * (XEP-0198 § 6), rather than being mistaken for a value that is lower than an earlier one.
     */
    @Test
    void acknowledgementFarAboveNumberOfStanzasSentClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3000000000"));

        // Verify result.
        final String streamError = assertSessionClosedWithStreamError(session);
        assertHandledCountTooHigh(streamError, "3000000000", "1");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' that is not a number that fits a long closes the session with a stream error.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "", "1.5", "9223372036854775808"})
    void acknowledgementWithNonNumericValueClosesSession(final String h) throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, h));

        // Verify result.
        assertSessionClosedWithStreamError(session, "is not a number");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a negative value of 'h' closes the session with a stream error.
     */
    @Test
    void acknowledgementWithNegativeValueClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "-1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, "negative value");
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' above 2^32-1 closes the session with a stream error, like other invalid values. XEP-0198
     * § 13 defines 'h' as an xs:unsignedInt, and § 6 allows for misuse of stream management to terminate the stream.
     */
    @Test
    void acknowledgementWithValueAboveLegalMaximumClosesSession() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK + 1)));

        // Verify result.
        assertSessionClosedWithStreamError(session);
        assertEquals(List.of("1"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one does not close the session or bring back
     * stanzas. XEP-0198 § 4 states that an {@code <a/>} only indicates that new stanzas were processed if 'h' has been
     * incremented, and § 6 only requires closing the stream for a value that is too high.
     */
    @Test
    void lowerAcknowledgementIsNotAnErrorWhileStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        verify(session, never().description("A lower acknowledgement is not too high, so it must not close the session.")).close();
        assertEquals(List.of("3"), unacknowledgedMessageIds(sm), "Stanzas that were acknowledged earlier must not come back.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one is also no error when no stanzas are
     * unacknowledged, and that it does not affect the numbers of the stanzas that follow (XEP-0198 § 4 and § 6).
     */
    @Test
    void lowerAcknowledgementIsNotAnErrorWhenNoStanzasAreUnacknowledged() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        verify(session, never().description("A lower acknowledgement is not too high, so it must not close the session.")).close();
        assertEquals(List.of("4"), unacknowledgedMessageIds(sm), "A lower acknowledgement must not affect the numbers of the stanzas that follow.");
    }

    /**
     * Verifies that the reported count of handled stanzas rolls over to zero after 2^32-1, as defined by XEP-0198 § 4.
     */
    @Test
    void countOfHandledStanzasRollsOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String before = lastDelivered(session).attributeValue("h");
        sm.incrementServerProcessedStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String after = lastDelivered(session).attributeValue("h");
        sm.incrementServerProcessedStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String afterNext = lastDelivered(session).attributeValue("h");

        // Verify result.
        assertEquals(String.valueOf(StreamManager.MASK), before, "2^32-1 is the largest value that can be reported.");
        assertEquals("0", after, "The reported count must roll over to zero after 2^32-1.");
        assertEquals("1", afterNext, "The reported count must continue from zero after rolling over.");
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
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);
        sm.incrementServerProcessedStanzas();

        // Execute system under test.
        final Element result = sm.buildResumedElement();

        // Verify result.
        assertEquals("0", result.attributeValue("h"), "The count in a 'resumed' element must roll over to zero after 2^32-1.");
    }

    /**
     * Verifies that a stanza can be acknowledged with the largest legal value of 'h'.
     */
    @Test
    void acknowledgementWithLargestLegalValueIsAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK)));

        // Verify result.
        verify(session, never().description("An acknowledgement with the largest legal value of 'h' must not close the session.")).close();
        assertEquals(List.of("2"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over removes the stanzas that were sent around the roll-over, when
     * all of them are acknowledged. Numbering then restarts at the value that was acknowledged.
     */
    @Test
    void acknowledgementAfterRollOverRemovesAcknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        verify(session, never().description("Acknowledgements that rolled over must not close the session.")).close();
        assertEquals(List.of("peer-counts-2"), unacknowledgedMessageIds(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over only removes the stanzas that it acknowledges. XEP-0198 § 4 leaves
     * a stanza the responsibility of the sender until it is acknowledged.
     */
    @Test
    void acknowledgementAfterRollOverKeepsUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Verify result.
        assertEquals(List.of("peer-counts-1"), unacknowledgedMessageIds(sm), "The stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that stanzas that are sent after an acknowledgement that rolled over are numbered on from the stanzas that
     * are still unacknowledged, as XEP-0198 § 4 has both parties count every stanza.
     */
    @Test
    void stanzasSentAfterRollOverAreNumberedOnFromUnacknowledgedStanzas() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertEquals(List.of("peer-counts-2"), unacknowledgedMessageIds(sm), "An acknowledgement must not cover a stanza that the peer counts after it.");
    }

    /**
     * Verifies that successive valid acknowledgements around the roll-over are accepted. XEP-0198 § 6 only calls for
     * closing the stream when a value is higher than the number of stanzas that were sent.
     */
    @Test
    void successiveAcknowledgementsAfterRollOverAreAccepted() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        verify(session, never().description("A valid acknowledgement must not close the session.")).close();
        assertEquals(List.of(), unacknowledgedMessageIds(sm), "No stanzas must remain after all of them were acknowledged.");
    }

    /**
     * Verifies that acknowledging a stanza that was never sent is detected around the roll-over, as XEP-0198 § 6 requires
     * at any other time. The value of 'send-count' has to roll over too, as it is an xs:unsignedInt (§ 13).
     */
    @Test
    void acknowledgementOfUnsentStanzaIsRejectedAroundRollOver() throws Exception
    {
        // Setup test fixture.
        final LocalClientSession session = authenticatedClientSession();
        final StreamManager sm = enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "5"));

        // Verify result.
        final String streamError = assertSessionClosedWithStreamError(session);
        assertHandledCountTooHigh(streamError, "5", "0");
        assertEquals(List.of("peer-counts-2^32-1", "peer-counts-0"), unacknowledgedMessageIds(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * A mock of an authenticated client session that has a full JID and a stream ID.
     */
    private static LocalClientSession authenticatedClientSession()
    {
        final LocalClientSession session = mock(LocalClientSession.class);
        doReturn(true).when(session).isAuthenticated();
        doReturn(FULL_JID).when(session).getAddress();
        doReturn(BasicStreamIDFactory.createStreamID(STREAM_ID)).when(session).getStreamID();
        return session;
    }

    /**
     * Creates a stream manager with Stream Management enabled, and clears the interaction with the session that resulted
     * from enabling it.
     */
    private static StreamManager enabledStreamManager(final LocalSession session, final String namespace)
    {
        final StreamManager sm = new StreamManager(session);
        sm.process(smElement("enable", namespace));
        assertTrue(sm.isEnabled(), "Test fixture expects Stream Management to be enabled.");
        clearInvocations(session);
        return sm;
    }

    private static Element smElement(final String name, final String namespace)
    {
        return DocumentHelper.createElement(QName.get(name, namespace));
    }

    private static Element ack(final String namespace, final String h)
    {
        return smElement("a", namespace).addAttribute("h", h);
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    /**
     * Returns the last element that was delivered to the session.
     */
    private static Element lastDelivered(final LocalSession session) throws DocumentException
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session, atLeastOnce().description("Expected something to be delivered to the session.")).deliverRawText(captor.capture());
        return DocumentHelper.parseText(captor.getValue()).getRootElement();
    }

    /**
     * Returns the IDs of the messages that are unacknowledged, in order. This closes the stream manager, as it uses the
     * routing of unacknowledged messages on close to observe them.
     */
    private static List<String> unacknowledgedMessageIds(final StreamManager sm)
    {
        final PacketRouter router = mock(PacketRouter.class);
        sm.onClose(router, SERVER_ADDRESS);

        final ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(router, atLeast(0)).route(captor.capture());
        return captor.getAllValues().stream().map(Packet::getID).toList();
    }

    private static void assertFailedWithUnexpectedRequest(final Element failed, final String namespace)
    {
        assertEquals(QName.get("failed", namespace), failed.getQName(), "The response must be a 'failed' element in the namespace of the request.");
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")), "The response must report the 'unexpected-request' condition, but was: " + failed.asXML());
    }

    private static void assertSessionClosedWithStreamError(final LocalSession session, final String expectedText)
    {
        final String streamError = assertSessionClosedWithStreamError(session);
        assertTrue(streamError.contains(expectedText), "The stream error must contain the text '" + expectedText + "', but was: " + streamError);
    }

    /**
     * Verifies that one stream error was sent and that the session was closed, and returns that stream error.
     */
    private static String assertSessionClosedWithStreamError(final LocalSession session)
    {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(session, times(1).description("Exactly one stream error must be sent.")).deliverRawText(captor.capture());
        assertTrue(captor.getValue().contains("undefined-condition"), "The stream error must use the 'undefined-condition' condition, but was: " + captor.getValue());
        verify(session, times(1).description("The session must be closed after the stream error was sent.")).close();
        return captor.getValue();
    }

    private static void assertHandledCountTooHigh(final String streamError, final String expectedH, final String expectedSendCount) throws DocumentException
    {
        final Element element = DocumentHelper.parseText(streamError).getRootElement().element(QName.get("handled-count-too-high", StreamManager.NAMESPACE_V3));
        assertNotNull(element, "The stream error must include a 'handled-count-too-high' element, but was: " + streamError);
        assertEquals(expectedH, element.attributeValue("h"), "The element must report the value of 'h' that was received.");
        assertEquals(expectedSendCount, element.attributeValue("send-count"), "The element must report the number of stanzas that were sent.");
    }

    /**
     * Sets a private counter of a stream manager, as sending 2^32 stanzas is not feasible in a unit test.
     */
    private static void setCounter(final StreamManager sm, final String fieldName, final long value) throws Exception
    {
        final Field field = StreamManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicLong) field.get(sm)).set(value);
    }
}
