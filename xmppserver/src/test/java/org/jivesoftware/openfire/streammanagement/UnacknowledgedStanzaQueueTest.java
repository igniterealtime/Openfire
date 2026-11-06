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

import org.junit.jupiter.api.Test;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link UnacknowledgedStanzaQueue}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class UnacknowledgedStanzaQueueTest
{
    private static final int MAXIMUM = 10;

    /**
     * Verifies that the first stanza that is added is numbered 1, and that each next stanza is numbered one higher.
     */
    @Test
    void stanzasAreNumberedFromOne()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);

        // Execute system under test.
        final long first = queue.add(message("1"));
        final long second = queue.add(message("2"));

        // Verify result.
        assertEquals(1, first, "The first stanza must be numbered 1.");
        assertEquals(2, second, "The next stanza must be numbered one higher than the stanza that was added before it.");
    }

    /**
     * Verifies that a stanza that is added after all earlier stanzas were acknowledged is numbered one higher than the
     * number of acknowledged stanzas.
     */
    @Test
    void stanzaAddedAfterAcknowledgementIsNumberedOn()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));
        queue.acknowledge(2);

        // Execute system under test.
        final long result = queue.add(message("3"));

        // Verify result.
        assertEquals(3, result, "A stanza that is added after all earlier stanzas were acknowledged must be numbered on from the number of acknowledged stanzas.");
    }

    /**
     * Verifies that the queue holds its stanzas in the order in which they were added, with their numbers.
     */
    @Test
    void entriesAreInOrderOfAddition()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);

        // Execute system under test.
        queue.add(message("a"));
        queue.add(message("b"));
        queue.add(message("c"));

        // Verify result.
        assertEquals(List.of("a", "b", "c"), ids(queue), "The stanzas must be in the order in which they were added.");
        assertEquals(List.of(1L, 2L, 3L), queue.getEntries().stream().map(UnacknowledgedStanzaQueue.Entry::getNumber).toList(), "Each stanza must have the number that it was given when it was added.");
        assertEquals(3, queue.size(), "The size must be the number of stanzas that were added.");
        assertEquals(3L, queue.getLastNumber(), "The last number must be the number of the stanza that was added last.");
    }

    /**
     * Verifies that an entry holds the stanza that was added, and the time at which it was added.
     */
    @Test
    void entryHoldsStanzaAndTimeOfAddition()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        final Message message = message("1");
        final Instant before = Instant.now();

        // Execute system under test.
        queue.add(message);

        // Verify result.
        final UnacknowledgedStanzaQueue.Entry entry = queue.getEntries().iterator().next();
        final Instant after = Instant.now();
        assertSame(message, entry.getPacket(), "The entry must hold the stanza that was added.");
        assertFalse(entry.getTimestamp().isBefore(before) || entry.getTimestamp().isAfter(after), "The entry must hold the time at which the stanza was added, but was: " + entry.getTimestamp());
    }

    /**
     * Verifies that an entry is not marked as sent again when it is added, and is once it is marked.
     */
    @Test
    void entryIsMarkedAsSentAgainOnlyWhenMarked()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        final UnacknowledgedStanzaQueue.Entry entry = queue.getEntries().iterator().next();
        assertFalse(entry.isSentAgain(), "A stanza that was just added must not be marked as sent again.");

        // Execute system under test.
        entry.markSentAgain();

        // Verify result.
        assertTrue(entry.isSentAgain(), "A stanza that was marked as sent again must be reported as such.");
    }

    /**
     * Verifies that an acknowledgement removes the stanzas that it covers, and keeps the others.
     */
    @Test
    void acknowledgementRemovesCoveredStanzas()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));
        queue.add(message("3"));

        // Execute system under test.
        queue.acknowledge(2);

        // Verify result.
        assertEquals(List.of("3"), ids(queue), "The stanzas that the acknowledgement covers must be removed, and the others kept.");
    }

    /**
     * Verifies that an acknowledgement of zero stanzas removes nothing.
     */
    @Test
    void acknowledgementOfZeroRemovesNothing()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));

        // Execute system under test.
        queue.acknowledge(0);

        // Verify result.
        assertEquals(List.of("1", "2"), ids(queue), "An acknowledgement of zero stanzas must not remove any stanza.");
    }

    /**
     * Verifies that an acknowledgement of all stanzas empties the queue.
     */
    @Test
    void acknowledgementOfAllStanzasEmptiesQueue()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));

        // Execute system under test.
        queue.acknowledge(2);

        // Verify result.
        assertTrue(queue.isEmpty(), "An acknowledgement of all stanzas must empty the queue.");
        assertNull(queue.getLastNumber(), "An empty queue must not have a last number.");
        assertEquals("(none)", queue.describeLast(), "An empty queue must be described as having no last stanza.");
    }

    /**
     * Verifies that an acknowledgement of more stanzas than were added is refused, without changing the queue.
     */
    @Test
    void acknowledgementOfMoreStanzasThanAddedIsRefused()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));

        // Execute system under test.
        final IllegalStateException result = assertThrows(IllegalStateException.class, () -> queue.acknowledge(3), "An acknowledgement of more stanzas than were added must be refused.");

        // Verify result.
        assertTrue(result.getMessage().contains("our last unacknowledged stanza: 2"), "The message must report the last stanza that was added, but was: " + result.getMessage());
        assertEquals(List.of("1", "2"), ids(queue), "A refused acknowledgement must not remove any stanza.");
    }

    /**
     * Verifies that a value of 'h' that is negative, or larger than 2^32-1, is not a valid acknowledgement.
     */
    @Test
    void illegalValueIsNotAValidAcknowledgement()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);

        // Execute system under test & verify result.
        assertThrows(IllegalArgumentException.class, () -> queue.isValidAcknowledgement(-1), "A negative value of 'h' must be rejected.");
        assertThrows(IllegalArgumentException.class, () -> queue.isValidAcknowledgement(StreamManager.MASK + 1), "A value of 'h' larger than 2^32-1 must be rejected.");
    }

    /**
     * Verifies that, when no stanzas are unacknowledged, only the number of stanzas that were acknowledged before is a
     * valid acknowledgement.
     */
    @Test
    void acknowledgementWhenNoStanzasAreUnacknowledged()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.acknowledge(1);

        // Execute system under test & verify result.
        assertTrue(queue.isValidAcknowledgement(1), "Acknowledging the stanzas that were acknowledged before must be valid.");
        assertFalse(queue.isValidAcknowledgement(2), "Acknowledging a stanza that was not added must not be valid.");
    }

    /**
     * Verifies that, when stanzas are unacknowledged, an acknowledgement up to the last stanza is valid, and one beyond
     * it is not.
     */
    @Test
    void acknowledgementWhenStanzasAreUnacknowledged()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));

        // Execute system under test & verify result.
        assertTrue(queue.isValidAcknowledgement(2), "Acknowledging up to the last stanza must be valid.");
        assertFalse(queue.isValidAcknowledgement(3), "Acknowledging beyond the last stanza must not be valid.");
    }

    /**
     * Verifies that an acknowledgement that is lower than an earlier one is valid, but does not remove any stanzas, as
     * XEP-0198 § 4 has it acknowledge nothing new.
     */
    @Test
    void lowerAcknowledgementIsValidAndRemovesNothing()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        queue.add(message("2"));
        queue.add(message("3"));
        queue.acknowledge(2);
        assertTrue(queue.isValidAcknowledgement(1), "Test fixture expects a lower acknowledgement to be valid.");

        // Execute system under test.
        queue.acknowledge(1);

        // Verify result.
        assertEquals(List.of("3"), ids(queue), "A lower acknowledgement must not remove any stanzas.");
        assertEquals(4, queue.add(message("4")), "A lower acknowledgement must not change the numbering of stanzas.");
    }

    /**
     * Verifies that the number of the stanza that was sent last is reported whether or not it was acknowledged.
     */
    @Test
    void lastSentNumberIncludesAcknowledgedStanzas()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        assertEquals(0, queue.getLastSentNumber(), "Test fixture expects zero for a queue to which no stanzas were added.");
        queue.add(message("1"));
        queue.add(message("2"));

        // Execute system under test.
        queue.acknowledge(2);

        // Verify result.
        assertEquals(2, queue.getLastSentNumber(), "The number of the stanza that was sent last must be reported after it was acknowledged.");
    }

    /**
     * Verifies that the queue exceeds its maximum only when it holds more stanzas than the maximum.
     */
    @Test
    void maximumIsExceededOnlyAboveMaximum()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> 2);
        queue.add(message("1"));
        queue.add(message("2"));
        assertFalse(queue.exceedsMaximum(), "A queue that holds as many stanzas as its maximum must not exceed it.");

        // Execute system under test.
        queue.add(message("3"));

        // Verify result.
        assertTrue(queue.exceedsMaximum(), "A queue that holds more stanzas than its maximum must exceed it.");
    }

    /**
     * Verifies that a change of the maximum takes effect immediately.
     */
    @Test
    void changedMaximumTakesEffectImmediately()
    {
        // Setup test fixture.
        final AtomicInteger maximum = new AtomicInteger(5);
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(maximum::get);
        queue.add(message("1"));
        queue.add(message("2"));
        assertFalse(queue.exceedsMaximum(), "Test fixture expects the queue to be within its maximum.");

        // Execute system under test.
        maximum.set(1);

        // Verify result.
        assertTrue(queue.exceedsMaximum(), "A lowered maximum must take effect without adding a stanza.");
    }

    /**
     * Verifies that the entries of the queue cannot be changed through the collection that is returned for them.
     */
    @Test
    void entriesCannotBeChangedThroughTheReturnedCollection()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        final Collection<UnacknowledgedStanzaQueue.Entry> entries = queue.getEntries();

        // Execute system under test & verify result.
        assertThrows(UnsupportedOperationException.class, entries::clear, "The entries must not be changed through the collection that is returned for them.");
        assertEquals(1, queue.size(), "The queue must be unchanged.");
    }

    /**
     * Verifies that the collection that is returned for the entries of the queue is a copy, which later changes of the
     * queue do not affect, so that it can be iterated while the queue changes.
     */
    @Test
    void entriesAreACopy()
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        queue.add(message("1"));
        final Collection<UnacknowledgedStanzaQueue.Entry> entries = queue.getEntries();

        // Execute system under test.
        queue.add(message("2"));
        queue.acknowledge(1);

        // Verify result.
        assertEquals(List.of(1L), entries.stream().map(UnacknowledgedStanzaQueue.Entry::getNumber).toList(), "The returned collection must not reflect changes of the queue after it was returned.");
    }

    /**
     * Verifies that a stanza can be acknowledged with the largest legal value of 'h'.
     */
    @Test
    void acknowledgementWithLargestLegalValueRemovesCoveredStanza() throws Exception
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        setAcknowledged(queue, StreamManager.MASK - 1);
        queue.add(message("peer-counts-2^32-1"));

        // Execute system under test.
        queue.acknowledge(StreamManager.MASK);

        // Verify result.
        assertTrue(queue.isEmpty(), "The stanza that the largest legal value of 'h' covers must be removed.");
    }

    /**
     * Verifies that stanza numbers roll over from 2^32-1 to zero, as XEP-0198 § 4 requires of 'h'.
     */
    @Test
    void stanzaNumbersRollOver() throws Exception
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        setAcknowledged(queue, StreamManager.MASK - 1);
        queue.add(message("peer-counts-2^32-1"));

        // Execute system under test.
        final long result = queue.add(message("peer-counts-0"));

        // Verify result.
        assertEquals(0, result, "The stanza after the one numbered 2^32-1 must be numbered zero.");
    }

    /**
     * Verifies that an acknowledgement that rolled over removes only the stanzas that it covers.
     */
    @Test
    void acknowledgementAfterRollOverKeepsUncoveredStanzas() throws Exception
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        setAcknowledged(queue, StreamManager.MASK - 1);
        queue.add(message("peer-counts-2^32-1"));
        queue.add(message("peer-counts-0"));
        queue.add(message("peer-counts-1"));

        // Execute system under test.
        queue.acknowledge(0);

        // Verify result.
        assertEquals(List.of("peer-counts-1"), ids(queue), "An acknowledgement that rolled over must remove only the stanzas that it covers.");
    }

    /**
     * Verifies that, once the number of acknowledged stanzas has rolled over, an acknowledgement from before the
     * roll-over is valid as a lower value, while one that is higher than the number of stanzas that were sent is not.
     */
    @Test
    void acknowledgementFromBeforeRollOverIsValid() throws Exception
    {
        // Setup test fixture.
        final UnacknowledgedStanzaQueue queue = new UnacknowledgedStanzaQueue(() -> MAXIMUM);
        setAcknowledged(queue, StreamManager.MASK - 1);
        queue.add(message("peer-counts-2^32-1"));
        queue.add(message("peer-counts-0"));
        queue.add(message("peer-counts-1"));

        // Execute system under test.
        queue.acknowledge(0);

        // Verify result.
        assertTrue(queue.isValidAcknowledgement(StreamManager.MASK - 1), "An acknowledgement from before the roll-over must be valid as a lower value.");
        assertFalse(queue.isValidAcknowledgement(2), "An acknowledgement of more stanzas than were sent must not be valid after the roll-over.");
    }

    private static Message message(final String id)
    {
        final Message message = new Message();
        message.setID(id);
        return message;
    }

    private static List<String> ids(final UnacknowledgedStanzaQueue queue)
    {
        return queue.getEntries().stream().map(UnacknowledgedStanzaQueue.Entry::getPacket).map(Packet::getID).toList();
    }

    /**
     * Sets the private count of acknowledged stanzas, as adding 2^32 stanzas is not feasible in a unit test.
     */
    private static void setAcknowledged(final UnacknowledgedStanzaQueue queue, final long value) throws Exception
    {
        final Field field = UnacknowledgedStanzaQueue.class.getDeclaredField("acknowledged");
        field.setAccessible(true);
        field.setLong(queue, value);
    }
}
