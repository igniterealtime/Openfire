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
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.xmpp.packet.StreamError;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify what {@link StreamManager} implements for both ends of a stream, without a resumption or a
 * detached session that is ended.
 *
 * Tests that use a stream manager run against both {@link AnsweringStreamManager} and {@link RequestingStreamManager},
 * each with a session of a type that it is used for. A few tests need a step that only the end that answers has, such
 * as enabling resumption, or exercise how client sessions deliver stanzas, and run against that end only.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerTest extends AbstractStreamManagerTest
{
    static Stream<Arguments> endsAndNamespaces()
    {
        return Arrays.stream(End.values()).flatMap(end -> Stream.of(StreamManager.NAMESPACE_V2, StreamManager.NAMESPACE_V3).map(namespace -> Arguments.of(end, namespace)));
    }

    static Stream<Arguments> endsAndNonNumericValues()
    {
        return Arrays.stream(End.values()).flatMap(end -> Stream.of("not-a-number", "", "1.5", "9223372036854775808").map(h -> Arguments.of(end, h)));
    }

    @Test
    public void testValidateOutboundAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final long h = 0;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_peerAcksSentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = 1L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_peerAcksSentStanzaWithMoreInflight() throws Exception
    {
        // Setup test fixture.
        final long h = 10;
        final long oldH = 4;
        final Long lastUnackedX = 12L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_peerAcksUnsentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_peerAcksUnsentStanzaA() throws Exception
    {
        // Setup test fixture.
        final long h = 3;
        final long oldH = 1;
        final Long lastUnackedX = 2L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX - 1;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase_sent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase1() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase2() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX-2;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase3() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase3a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase3_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX - 2;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase4() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase4a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 3L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase4_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase5() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 3;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase5a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 4;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateOutboundAcknowledgement_rollover_edgecase5_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 5;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = UnacknowledgedStanzaQueue.isValidAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Verifies that an {@code <r/>} is answered with the number of stanzas that were handled since enabling.
     */
    @ParameterizedTest
    @MethodSource("endsAndNamespaces")
    void requestIsAnsweredWithCountOfHandledStanzas(final End end, final String namespace) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, namespace);
        sm.incrementHandledInboundStanzas();
        sm.incrementHandledInboundStanzas();
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("r", namespace));

        // Verify result.
        final Element ack = lastDelivered(session);
        assertEquals(QName.get("a", namespace), ack.getQName(), "An 'a' element must be sent, in the namespace of the stream.");
        assertEquals("3", ack.attributeValue("h"), "The count must equal the number of handled stanzas.");
    }

    /**
     * Verifies that an {@code <r/>} is answered with zero when no stanzas have been handled.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void requestIsAnsweredWithZeroWhenNoStanzasWereHandled(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("0", lastDelivered(session).attributeValue("h"), "The count must be zero when no stanzas were handled.");
    }

    /**
     * Verifies that the count of handled stanzas is cumulative: it is not reset by reporting it.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void countOfHandledStanzasIsCumulative(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.incrementHandledInboundStanzas();
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        sm.incrementHandledInboundStanzas();

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("2", lastDelivered(session).attributeValue("h"), "The count must include stanzas handled before the previous acknowledgement.");
    }

    /**
     * Verifies that stanzas handled before Stream Management was enabled are not counted.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzasHandledBeforeEnablingAreNotCounted(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.newStreamManager(session);
        sm.incrementHandledInboundStanzas();
        end.enable(sm, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        assertEquals("0", lastDelivered(session).attributeValue("h"), "Stanzas handled before enabling Stream Management must not be counted.");
    }

    /**
     * Verifies that an {@code <r/>} is silently ignored when Stream Management is not enabled.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void requestIsIgnoredWhenNotEnabled(final End end)
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.newStreamManager(session);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("Nothing is to be sent in response to a request when Stream Management is not enabled.")).deliverRawText(anyString());
    }

    /**
     * Verifies that no acknowledgement is sent on a detached session.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void requestIsNotAnsweredOnDetachedSession(final End end)
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("Nothing can be sent on a detached session.")).deliverRawText(anyString());
    }

    /**
     * Verifies that an {@code <a/>} removes the stanzas that it acknowledges, and no others.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementRemovesAcknowledgedStanzas(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of("3"), UnacknowledgedStanzas.ids(sm), "Only the stanza that was not acknowledged must remain.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that an {@code <a/>} that covers all stanzas that were sent leaves nothing unacknowledged.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfAllStanzasRemovesAll(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Verify result.
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "No stanzas must remain after all of them were acknowledged.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that an {@code <a/>} with a value of zero does not remove any stanzas.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfZeroRemovesNothing(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Verify result.
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "A stanza must remain when no stanzas were acknowledged.");
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
    }

    /**
     * Verifies that stanzas are numbered on from the last acknowledged value, after all earlier stanzas were
     * acknowledged.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzasAreNumberedOnAfterAcknowledgement(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertEquals(List.of("4"), UnacknowledgedStanzas.ids(sm), "The stanzas that were sent after an acknowledgement must be numbered on from its value.");
    }

    /**
     * Verifies that repeating the last acknowledgement has no effect, also when no stanzas remain.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void repeatedAcknowledgementIsAccepted(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "Repeating an acknowledgement must not close the session.");
    }

    /**
     * Verifies that stanzas that are sent before Stream Management is enabled are not tracked.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzasSentBeforeEnablingAreNotTracked(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.newStreamManager(session);
        sm.sentStanza(message("before"));
        end.enable(sm, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.sentStanza(message("after"));

        // Verify result.
        assertEquals(List.of("after"), UnacknowledgedStanzas.ids(sm), "Only stanzas sent after enabling Stream Management must be tracked.");
    }

    /**
     * Verifies that an {@code <a/>} is silently ignored when Stream Management is not enabled, whatever its value.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementIsIgnoredWhenNotEnabled(final End end)
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.newStreamManager(session);

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "not-a-number"));

        // Verify result.
        verify(session, never().description("Nothing is to be sent in response to an acknowledgement when Stream Management is not enabled.")).deliverRawText(anyString());
        assertFalse(session.isClosed(), "An acknowledgement must not close the session when Stream Management is not enabled.");
    }

    /**
     * Verifies that an {@code <a/>} without a 'h' attribute is silently ignored.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementWithoutValueIsIgnored(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(smElement("a", StreamManager.NAMESPACE_V3));

        // Verify result.
        verify(session, never().description("An acknowledgement without a value does not cause anything to be sent.")).deliverRawText(anyString());
        assertFalse(session.isClosed(), "An acknowledgement without a value does not close the session.");
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "An acknowledgement without a value must not remove any stanzas.");
    }

    /**
     * Verifies that acknowledging a stanza while none were sent closes the session with a stream error.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfStanzaWhenNoneWereSentClosesSession(final End end)
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send.");
    }

    /**
     * Verifies that acknowledging more stanzas than were sent closes the session with a stream error, without removing
     * any stanzas.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfMoreStanzasThanSentClosesSession(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send.");
        assertEquals(List.of("1", "2"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that the stream error for a value of 'h' that is too high contains the element that XEP-0198 § 6 defines
     * for it, which reports that value and the number of stanzas that were sent.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfMoreStanzasThanSentReportsHandledCountTooHigh(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertHandledCountTooHigh(session, "3", "2");
    }

    /**
     * Verifies that a value of 'h' that is far above the number of stanzas that were sent is recognised as too high
     * (XEP-0198 § 6), rather than being mistaken for a value that is lower than an earlier one.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementFarAboveNumberOfStanzasSentClosesSession(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "3000000000"));

        // Verify result.
        assertHandledCountTooHigh(session, "3000000000", "1");
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' that is not a number that fits a long closes the session with a stream error.
     */
    @ParameterizedTest
    @MethodSource("endsAndNonNumericValues")
    void acknowledgementWithNonNumericValueClosesSession(final End end, final String h) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, h));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "is not a number");
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a negative value of 'h' closes the session with a stream error.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementWithNegativeValueClosesSession(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "-1"));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition, "negative value");
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that a value of 'h' above 2^32-1 closes the session with a stream error, like other invalid values. XEP-0198
     * § 13 defines 'h' as an xs:unsignedInt, and § 6 allows for misuse of stream management to terminate the stream. The
     * session is closed with the error, which also makes it non-resumable (OF-3443).
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementWithValueAboveLegalMaximumClosesSession(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK + 1)));

        // Verify result.
        assertSessionClosedWithStreamError(session, StreamError.Condition.undefined_condition);
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one does not close the session or bring back
     * stanzas. XEP-0198 § 4 states that an {@code <a/>} only indicates that new stanzas were processed if 'h' has been
     * incremented, and § 6 only requires closing the stream for a value that is too high.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void lowerAcknowledgementIsNotAnErrorWhileStanzasAreUnacknowledged(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("3"), UnacknowledgedStanzas.ids(sm), "Stanzas that were acknowledged earlier must not come back.");
    }

    /**
     * Verifies that an acknowledgement with a value lower than an earlier one is also no error when no stanzas are
     * unacknowledged, and that it does not affect the numbers of the stanzas that follow (XEP-0198 § 4 and § 6).
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void lowerAcknowledgementIsNotAnErrorWhenNoStanzasAreUnacknowledged(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));
        sm.sentStanza(message("3"));
        sm.sentStanza(message("4"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "3"));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("4"), UnacknowledgedStanzas.ids(sm), "A lower acknowledgement must not affect the numbers of the stanzas that follow.");
    }

    /**
     * Verifies that an {@code <r/>} is sent each time the number of unacknowledged stanzas reaches a multiple of the
     * default request frequency.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementIsRequestedAtDefaultFrequency(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        for (int i = 1; i <= 4; i++) {
            sm.sentStanza(message(String.valueOf(i)));
        }
        verify(session, never().description("No acknowledgement is to be requested below the default frequency of 5.")).deliverRawText(anyString());

        // Execute system under test.
        sm.sentStanza(message("5"));

        // Verify result.
        assertEquals(QName.get("r", StreamManager.NAMESPACE_V3), lastDelivered(session).getQName(), "An acknowledgement is to be requested at the default frequency of 5, in the namespace of the stream.");
    }

    /**
     * Verifies that the frequency at which an {@code <r/>} is sent can be configured.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementIsRequestedAtConfiguredFrequency(final End end)
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "2");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        for (int i = 1; i <= 4; i++) {
            sm.sentStanza(message(String.valueOf(i)));
        }

        // Verify result.
        verify(session, times(2).description("An acknowledgement is to be requested for every second unacknowledged stanza.")).deliverRawText(anyString());
    }

    /**
     * Verifies that the request frequency is based on the number of stanzas that are unacknowledged, rather than the
     * number of stanzas that were sent.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementRequestDependsOnNumberOfUnacknowledgedStanzas(final End end)
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "2");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        verify(session, never().description("No acknowledgement is to be requested when only one stanza is unacknowledged.")).deliverRawText(anyString());
    }

    /**
     * Verifies that no {@code <r/>} is sent on a detached session, while stanzas are still tracked.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementIsNotRequestedOnDetachedSession(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.requestFrequency", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        doReturn(true).when(session).isDetached();

        // Execute system under test.
        sm.sentStanza(message("1"));

        // Verify result.
        verify(session, never().description("Nothing can be sent on a detached session.")).deliverRawText(anyString());
        assertEquals(List.of("1"), UnacknowledgedStanzas.ids(sm), "A stanza must be tracked on a detached session.");
    }

    /**
     * Verifies that the reported count of handled stanzas rolls over to zero after 2^32-1, as defined by XEP-0198 § 4.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void countOfHandledStanzasRollsOver(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, HANDLED_COUNTER_FIELD, StreamManager.MASK);

        // Execute system under test.
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String before = lastDelivered(session).attributeValue("h");
        sm.incrementHandledInboundStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String after = lastDelivered(session).attributeValue("h");
        sm.incrementHandledInboundStanzas();
        clearInvocations(session);
        sm.process(smElement("r", StreamManager.NAMESPACE_V3));
        final String afterNext = lastDelivered(session).attributeValue("h");

        // Verify result.
        assertEquals(String.valueOf(StreamManager.MASK), before, "2^32-1 is the largest value that can be reported.");
        assertEquals("0", after, "The reported count must roll over to zero after 2^32-1.");
        assertEquals("1", afterNext, "The reported count must continue from zero after rolling over.");
    }

    /**
     * Verifies that a stanza can be acknowledged with the largest legal value of 'h'.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementWithLargestLegalValueIsAccepted(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK)));

        // Verify result.
        assertFalse(session.isClosed(), "An acknowledgement with the largest legal value of 'h' must not close the session.");
        assertEquals(List.of("2"), UnacknowledgedStanzas.ids(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over removes the stanzas that were sent around the roll-over, when
     * all of them are acknowledged. Numbering then restarts at the value that was acknowledged.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementAfterRollOverRemovesAcknowledgedStanzas(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "Acknowledgements that rolled over must not close the session.");
        assertEquals(List.of("peer-counts-2"), UnacknowledgedStanzas.ids(sm), "Only the stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that an acknowledgement that rolled over only removes the stanzas that it acknowledges. XEP-0198 § 4 leaves
     * a stanza the responsibility of the sender until it is acknowledged.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementAfterRollOverKeepsUnacknowledgedStanzas(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Verify result.
        assertEquals(List.of("peer-counts-1"), UnacknowledgedStanzas.ids(sm), "The stanza that was not acknowledged must remain.");
    }

    /**
     * Verifies that stanzas that are sent after an acknowledgement that rolled over are numbered on from the stanzas that
     * are still unacknowledged, as XEP-0198 § 4 has both parties count every stanza.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void stanzasSentAfterRollOverAreNumberedOnFromUnacknowledgedStanzas(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.sentStanza(message("peer-counts-2"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertEquals(List.of("peer-counts-2"), UnacknowledgedStanzas.ids(sm), "An acknowledgement must not cover a stanza that the peer counts after it.");
    }

    /**
     * Verifies that successive valid acknowledgements around the roll-over are accepted. XEP-0198 § 6 only calls for
     * closing the stream when a value is higher than the number of stanzas that were sent.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void successiveAcknowledgementsAfterRollOverAreAccepted(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "1"));

        // Verify result.
        assertFalse(session.isClosed(), "A valid acknowledgement must not close the session.");
        assertEquals(List.of(), UnacknowledgedStanzas.ids(sm), "No stanzas must remain after all of them were acknowledged.");
    }

    /**
     * Verifies that an acknowledgement with a value from before the roll-over, which is lower than an earlier one that
     * rolled over, is no error and does not remove any stanzas (XEP-0198 § 4 and § 6).
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void lowerAcknowledgementFromBeforeRollOverIsNotAnError(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));
        sm.sentStanza(message("peer-counts-1"));
        sm.process(ack(StreamManager.NAMESPACE_V3, "0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, String.valueOf(StreamManager.MASK)));

        // Verify result.
        assertFalse(session.isClosed(), "A lower acknowledgement is not too high, so it must not close the session.");
        assertEquals(List.of("peer-counts-1"), UnacknowledgedStanzas.ids(sm), "A lower acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that acknowledging a stanza that was never sent is detected around the roll-over, as XEP-0198 § 6 requires
     * at any other time. The value of 'send-count' has to roll over too, as it is an xs:unsignedInt (§ 13).
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void acknowledgementOfUnsentStanzaIsRejectedAroundRollOver(final End end) throws Exception
    {
        // Setup test fixture.
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        setCounter(sm, ACKNOWLEDGED_COUNTER_FIELD, StreamManager.MASK - 1);
        sm.sentStanza(message("peer-counts-2^32-1"));
        sm.sentStanza(message("peer-counts-0"));

        // Execute system under test.
        sm.process(ack(StreamManager.NAMESPACE_V3, "5"));

        // Verify result.
        assertHandledCountTooHigh(session, "5", "0");
        assertEquals(List.of("peer-counts-2^32-1", "peer-counts-0"), UnacknowledgedStanzas.ids(sm), "An invalid acknowledgement must not remove any stanzas.");
    }

    /**
     * Verifies that the stream remains open, with all stanzas tracked, at the maximum number of unacknowledged stanzas.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void reachingMaximumOfUnacknowledgedStanzasKeepsStreamOpen(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);

        // Execute system under test.
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Verify result.
        assertFalse(session.getConnection().isClosed(), "The stream must not be closed at the maximum number of unacknowledged stanzas.");
        assertFalse(session.isClosed(), "The session must not be closed at the maximum number of unacknowledged stanzas.");
        assertTrue(sm.isEnabled(), "Stream Management must remain enabled at the maximum number of unacknowledged stanzas.");
        assertEquals(List.of("1", "2", "3"), UnacknowledgedStanzas.ids(sm), "All stanzas must be tracked at the maximum number of unacknowledged stanzas.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas closes the stream with a 'policy-violation'
     * stream error, so that the peer learns why. XEP-0198 defines no such maximum, and no way to stop tracking stanzas on
     * an open stream.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void exceedingMaximumOfUnacknowledgedStanzasClosesStream(final End end)
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.sentStanza(message("4"));

        // Verify result.
        final ArgumentCaptor<StreamError> captor = ArgumentCaptor.forClass(StreamError.class);
        verify(session.getConnection(), times(1).description("The stream must be closed when the maximum number of unacknowledged stanzas is exceeded.")).close(captor.capture());
        assertNotNull(captor.getValue(), "The stream must be closed with a stream error, so that the peer learns why.");
        assertEquals(StreamError.Condition.policy_violation, captor.getValue().getCondition(), "The stream error must use the 'policy-violation' condition, as a configured limit was exceeded.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas does not discard them. XEP-0198 § 4 leaves a
     * stanza the responsibility of the sender until it is acknowledged, and states that a server should treat it as a
     * stanza for an unavailable resource when the stream ends.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void exceedingMaximumOfUnacknowledgedStanzasDoesNotDiscardThem(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "3");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Execute system under test.
        sm.sentStanza(message("4"));

        // Verify result.
        assertEquals(List.of("1", "2", "3", "4"), UnacknowledgedStanzas.ids(sm), "Stanzas that were not acknowledged must remain tracked, to be handled when the session closes.");
    }

    /**
     * Verifies that exceeding the maximum number of unacknowledged stanzas disables resumption, so that the session closes
     * and its unacknowledged stanzas are handled, rather than being kept for a peer that does not acknowledge them.
     */
    @Test
    void exceedingMaximumOfUnacknowledgedStanzasDisablesResumption()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalClientSession session = authenticatedClientSession();
        final AuthToken authToken = mock(AuthToken.class);
        doReturn(authToken).when(session).getAuthToken();
        final AnsweringStreamManager sm = new AnsweringStreamManager(session);
        final Element enable = smElement("enable", StreamManager.NAMESPACE_V3);
        enable.addAttribute("resume", "true");
        sm.process(enable);
        assertTrue(sm.getResume(), "Test fixture expects a resumable stream.");
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.sentStanza(message("2"));

        // Verify result.
        assertFalse(sm.getResume(), "The stream must not be resumable after the maximum number of unacknowledged stanzas was exceeded.");
    }

    /**
     * Verifies that the stream is closed once, rather than for every stanza that is sent after exceeding the maximum
     * number of unacknowledged stanzas, and that stanzas that are sent after that are not tracked. The connection is
     * closed, so it hands those stanzas to its fallback, which would deliver them a second time if they were also routed
     * again when the session closes.
     */
    @ParameterizedTest
    @EnumSource(End.class)
    void exceedingMaximumOfUnacknowledgedStanzasClosesStreamOnce(final End end) throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final LocalSession session = end.newSession();
        final StreamManager sm = end.enabledStreamManager(session, StreamManager.NAMESPACE_V3);
        sm.sentStanza(message("1"));

        // Execute system under test.
        sm.sentStanza(message("2"));
        sm.sentStanza(message("3"));

        // Verify result.
        verify(session.getConnection(), times(1).description("The stream must be closed once.")).close(any());
        assertEquals(List.of("1", "2"), UnacknowledgedStanzas.ids(sm), "Stanzas that are sent after the stream was closed must not be tracked, as the closed connection hands them to its fallback.");
    }

    /**
     * Verifies that a message in a batch that is delivered after the stream was closed for exceeding the maximum number of
     * unacknowledged stanzas is handed to the connection only, which stores it offline, and not tracked to be routed again.
     */
    @Test
    void stanzasInBatchAfterExceedingMaximumAreNotTracked() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), message("3")));

        // Verify result.
        verify(connection, times(1).description("The stream must be closed once.")).close(any());
        verify(connection, times(3).description("Each stanza in the batch is to be handed to the connection, which hands it to its fallback once it is closed.")).deliver(any());
        assertEquals(List.of("1", "2"), unacknowledgedMessageIds(session.getStreamManager()), "A message that is handed to the closed connection must not also be tracked, as it would then be delivered twice.");
    }

    /**
     * Verifies that a stanza in a batch that is delivered after the stream was closed for exceeding the maximum number of
     * unacknowledged stanzas is still tracked when the connection has no fallback, as the closed connection then drops
     * the stanza, and it would otherwise be lost.
     */
    @Test
    void stanzasInBatchAfterExceedingMaximumAreTrackedWithoutFallback() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("stream.management.max-unacked", "1");
        final Connection connection = closableConnection();
        doReturn(null).when(connection).getPacketDeliverer();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, BasicStreamIDFactory.createStreamID(STREAM_ID), Locale.ENGLISH);
        session.setStatus(Session.Status.AUTHENTICATED);
        session.getStreamManager().process(smElement("enable", StreamManager.NAMESPACE_V3));
        assertTrue(session.getStreamManager().isEnabled(), "Test fixture expects Stream Management to be enabled.");

        // Execute system under test.
        session.pushPackets(List.of(message("1"), message("2"), message("3")));

        // Verify result.
        assertTrue(connection.isClosed(), "Test fixture expects the stream to be closed for exceeding the maximum.");
        assertEquals(List.of("1", "2", "3"), unacknowledgedMessageIds(session.getStreamManager()), "A stanza that the closed connection drops, as it has no fallback, must be tracked, so that it is routed when the session closes.");
    }
}
