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

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * The outbound stanzas of a stream that the peer has not acknowledged yet (XEP-0198 § 4), in the order in which they
 * were sent, together with the number of outbound stanzas that the peer has acknowledged.
 *
 * Each stanza is numbered when it is added, so that an acknowledgement, which reports a number of stanzas, can be
 * matched to the stanzas that it covers.
 *
 * Each method is thread-safe, and holds the lock of the instance while it runs. A sequence of calls that depends on
 * the queue not changing in between, such as checking an acknowledgement and then processing it, still needs the
 * caller to hold a lock that guards all changes of the queue.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
class UnacknowledgedStanzaQueue
{
    /**
     * The logger to use, which names the session of the stream.
     */
    private final Logger Log;

    /**
     * An outbound stanza that the peer has not acknowledged yet.
     *
     * The methods of an entry are thread-safe. The stanza that an entry holds is not copied: a caller that changes it
     * must hold a lock that guards all users of the stanza.
     */
    static class Entry
    {
        private final long number;
        private final Instant timestamp;
        private final Packet packet;
        private volatile boolean sentAgain;

        Entry(final long number, @Nonnull final Packet packet, final boolean sentAgain)
        {
            this(number, packet, sentAgain, Instant.now());
        }

        private Entry(final long number, @Nonnull final Packet packet, final boolean sentAgain, @Nonnull final Instant timestamp)
        {
            this.number = number;
            this.packet = packet;
            this.sentAgain = sentAgain;
            this.timestamp = timestamp;
        }

        /**
         * The number of the stanza, which the peer reports in an acknowledgement once it handled this stanza.
         *
         * @return the number of the stanza.
         */
        long getNumber()
        {
            return number;
        }

        /**
         * The time at which the stanza was added, which is when it was sent for the first time.
         *
         * @return the time at which the stanza was added.
         */
        @Nonnull
        Instant getTimestamp()
        {
            return timestamp;
        }

        /**
         * The stanza.
         *
         * @return the stanza.
         */
        @Nonnull
        Packet getPacket()
        {
            return packet;
        }

        /**
         * Whether the stanza was sent again after it was first sent, on a resumed stream or on another connection,
         * because the connection on which it was sent before broke while the stanza was unacknowledged.
         *
         * @return true if the stanza was sent again.
         */
        boolean isSentAgain()
        {
            return sentAgain;
        }

        /**
         * Records that the stanza was sent again after it was first sent.
         */
        void markSentAgain()
        {
            this.sentAgain = true;
        }
    }

    /**
     * The stanzas that were sent but not acknowledged, in the order in which they were sent.
     */
    private final Deque<Entry> entries = new LinkedList<>();

    /**
     * The number of outbound stanzas that the peer has acknowledged as handled.
     */
    private long acknowledged = 0;

    /**
     * Whether the number of acknowledged stanzas has rolled over from (2^32)-1 to zero at least once.
     */
    private boolean rolledOver = false;

    /**
     * Provides the maximum number of stanzas to keep.
     */
    private final IntSupplier maximum;

    /**
     * Creates an empty queue that logs through a logger of its own class.
     *
     * @param maximum provides the maximum number of stanzas to keep. It is consulted whenever it is needed, so that a
     *                change of the value takes effect immediately. It is invoked while the lock of the queue is held.
     */
    UnacknowledgedStanzaQueue(@Nonnull final IntSupplier maximum)
    {
        this(LoggerFactory.getLogger(UnacknowledgedStanzaQueue.class), maximum);
    }

    /**
     * Creates an empty queue.
     *
     * @param log the logger to use, so that what is logged can be related to the session of the stream.
     * @param maximum provides the maximum number of stanzas to keep. It is consulted whenever it is needed, so that a
     *                change of the value takes effect immediately. It is invoked while the lock of the queue is held.
     */
    UnacknowledgedStanzaQueue(@Nonnull final Logger log, @Nonnull final IntSupplier maximum)
    {
        this.Log = log;
        this.maximum = maximum;
    }

    /**
     * Adds a stanza that is sent to the peer, numbering it one higher than the stanza that was sent before it. Numbers
     * roll over from (2^32)-1 to zero, as 'h' does (XEP-0198 § 4).
     *
     * @param packet the stanza that is sent.
     * @return the number of the stanza.
     */
    synchronized long add(@Nonnull final Packet packet)
    {
        return add(packet, false);
    }

    /**
     * Adds a stanza that is sent to the peer, numbering it one higher than the stanza that was sent before it. Numbers
     * roll over from (2^32)-1 to zero, as 'h' does (XEP-0198 § 4).
     *
     * @param packet the stanza that is sent.
     * @param sentAgain whether the stanza was sent before, on a connection that broke before the stanza was
     *                  acknowledged (see {@link Entry#isSentAgain()}).
     * @return the number of the stanza.
     */
    synchronized long add(@Nonnull final Packet packet, final boolean sentAgain)
    {
        // The next ID is one higher than the last stanza that was sent (which might be unacknowledged!), rolling over after 2^32-1 (XEP-0198 § 4).
        final long number = ( 1 + getLastSentNumber() ) & StreamManager.MASK;
        entries.addLast(new Entry(number, packet, sentAgain));
        return number;
    }

    /**
     * Records that a number of stanzas were sent to the peer without being added to the queue, as if the peer had
     * acknowledged them: stanzas that are added later are numbered after them, and an acknowledgement that covers them
     * is valid. This is for stanzas that the peer counts, but that are not tracked.
     *
     * @param count the number of stanzas that were sent without being added.
     * @throws IllegalArgumentException when 'count' is negative.
     * @throws IllegalStateException when the queue holds stanzas.
     */
    synchronized void skip(final long count)
    {
        if (count < 0) {
            throw new IllegalArgumentException("Argument 'count' cannot be negative, but was: " + count);
        }
        if (!entries.isEmpty()) {
            throw new IllegalStateException("Stanzas can only be skipped while none are unacknowledged, but " + entries.size() + " are.");
        }
        final long total = acknowledged + count;
        if (total > StreamManager.MASK) {
            rolledOver = true;
        }
        acknowledged = total & StreamManager.MASK;
    }

    /**
     * Checks whether the queue holds more stanzas than its maximum.
     *
     * @return true if the queue holds more stanzas than its maximum.
     */
    synchronized boolean exceedsMaximum()
    {
        return entries.size() > maximum.getAsInt();
    }

    /**
     * Checks if the number of stanzas that the peer acknowledges is not higher than the number of stanzas that were sent
     * to the peer.
     *
     * @param h The number of stanzas that the peer acknowledges it has received.
     * @return false if fewer stanzas were sent to the peer than the number it is acknowledging.
     * @throws IllegalArgumentException when 'h' is negative, or larger than 2^32-1.
     */
    synchronized boolean isValidAcknowledgement(final long h)
    {
        if (h < 0) {
            throw new IllegalArgumentException("Argument 'h' cannot be negative, but was: " + h);
        }
        if (h > StreamManager.MASK) {
            throw new IllegalArgumentException("Argument 'h' cannot be larger than 2^32 -1, but was: " + h);
        }
        return isValidAcknowledgement(h, acknowledged, getLastNumber(), rolledOver);
    }

    /**
     * Checks a value of 'h' against the stanzas that were sent, on a stream on which the acknowledged count has not
     * rolled over.
     *
     * @param h The value to check.
     * @param oldH The last acknowledged value.
     * @param lastUnackedX The number of the last stanza sent, or null if all stanzas that were sent are acknowledged.
     * @return false if the value is higher than the number of stanzas that were sent.
     * @see #isValidAcknowledgement(long, long, Long, boolean)
     */
    @VisibleForTesting
    static boolean isValidAcknowledgement(final long h, final long oldH, @Nullable final Long lastUnackedX)
    {
        return isValidAcknowledgement(h, oldH, lastUnackedX, false);
    }

    /**
     * Checks a value of 'h' against the stanzas that were sent, taking into account that stanza numbers and 'h' roll
     * over from 2^32-1 to zero (XEP-0198 § 4).
     *
     * A value from the last acknowledged value up to and including the number of the last stanza sent is valid. A value
     * lower than the last acknowledged value is valid too, as it is not higher than the number of stanzas that were sent
     * (XEP-0198 § 6), but it does not acknowledge any new stanzas (XEP-0198 § 4).
     *
     * A value counts as lower when it is at most half of the range below the last acknowledged value, counting back
     * across the roll-over. Until the acknowledged count has rolled over, it can't be below zero either, as the values
     * that would be reached by counting back across the roll-over were never sent.
     *
     * @param h The value to check.
     * @param oldH The last acknowledged value.
     * @param lastUnackedX The number of the last stanza sent, or null if all stanzas that were sent are acknowledged.
     * @param rolledOver Whether the acknowledged count has rolled over at least once.
     * @return false if the value is higher than the number of stanzas that were sent.
     */
    @VisibleForTesting
    static boolean isValidAcknowledgement(final long h, final long oldH, @Nullable final Long lastUnackedX, final boolean rolledOver)
    {
        final long lastSentX = lastUnackedX == null ? oldH : lastUnackedX;
        if (isInRange(h, oldH, lastSentX)) {
            return true;
        }
        final long earliestLowerH = rolledOver ? (oldH - (StreamManager.MASK >> 1)) & StreamManager.MASK : Math.max(0, oldH - (StreamManager.MASK >> 1));
        return isInRange(h, earliestLowerH, oldH);
    }

    /**
     * Checks if a value is in a range, taking into account that the range can include the roll-over from 2^32-1 to zero
     * (XEP-0198 § 4).
     *
     * @param value The value to check.
     * @param first The first value of the range.
     * @param last The last value of the range.
     * @return true if the value is in the range.
     */
    private static boolean isInRange(final long value, final long first, final long last)
    {
        return ((value - first) & StreamManager.MASK) <= ((last - first) & StreamManager.MASK);
    }

    /**
     * Processes an acknowledgement from the peer: records the number of stanzas that it acknowledges, and removes the
     * stanzas that it covers. A value that is lower than an earlier one acknowledges nothing new (XEP-0198 § 4), and
     * leaves the queue unchanged.
     *
     * @param h The number of stanzas that the peer acknowledges it has handled.
     * @throws IllegalArgumentException when 'h' is negative, or larger than 2^32-1.
     * @throws IllegalStateException when the peer acknowledges more stanzas than were sent to it.
     */
    synchronized void acknowledge(final long h)
    {
        if ( !isValidAcknowledgement(h) ) {
            // All paths leading up to here should have checked for this. Race condition?
            throw new IllegalStateException( "Peer acknowledges stanzas that we didn't send! Peer Ack h: "+h+", our last unacknowledged stanza: " + describeLast() );
        }

        final long oldH = acknowledged;
        if ( !isInRange( h, oldH, getLastSentNumber() ) )
        {
            // OF-3433: A value lower than an earlier one does not acknowledge anything new (XEP-0198 § 4).
            Log.debug( "Ignoring peer Ack (h={}), as it is lower than an earlier one: {}", h, oldH );
            return;
        }

        if ( h < oldH ) {
            // OF-3433: This acknowledges new stanzas, so a smaller value means that the count rolled over.
            Log.debug( "Peer Ack (h={}) is lower than the earlier one ({}) and acknowledges new stanzas: the acknowledged count rolled over.", h, oldH );
            rolledOver = true;
        }
        acknowledged = h;

        // Remove stanzas from temporary storage as now acknowledged
        Log.trace( "Before processing peer Ack (h={}): {} unacknowledged stanzas.", h, entries.size() );

        // Pop all acknowledged stanzas.
        while( !entries.isEmpty() && isInRange( entries.getFirst().getNumber(), oldH, h ) )
        {
            entries.removeFirst();
        }

        Log.trace( "After processing peer Ack (h={}): {} unacknowledged stanzas.", h, entries.size());
    }

    /**
     * Prepares the stanzas in the queue to be sent again on a resumed stream, after the acknowledgement that the peer
     * reported when the stream was resumed was processed. A stanza is sent again at most once: the stanzas that were
     * sent again before (see {@link Entry#isSentAgain()}) are removed from the queue and returned. The stanzas that
     * remain keep their order and the time at which they were first sent, and are not marked as sent again: the caller
     * marks each as it sends it again (see {@link Entry#markSentAgain()}). They are numbered again, directly after the
     * number of acknowledged stanzas, as the peer counts them when they are sent again in that order.
     *
     * @return the stanzas that were removed, in the order in which they were sent.
     */
    @Nonnull
    synchronized List<Entry> removeSentAgainAndRenumber()
    {
        final List<Entry> removed = new LinkedList<>();
        final List<Entry> remaining = new LinkedList<>();
        for (final Entry entry : entries) {
            if (entry.isSentAgain()) {
                removed.add(entry);
            } else {
                remaining.add(entry);
            }
        }
        entries.clear();
        long number = acknowledged;
        for (final Entry entry : remaining) {
            number = (number + 1) & StreamManager.MASK;
            entries.addLast(new Entry(number, entry.getPacket(), entry.isSentAgain(), entry.getTimestamp()));
        }
        return removed;
    }

    /**
     * The number of the stanza that was sent last, whether or not the peer acknowledged it. When no stanzas are
     * unacknowledged, this is the number of stanzas that the peer acknowledged.
     *
     * @return the number of the stanza that was sent last.
     */
    synchronized long getLastSentNumber()
    {
        return entries.isEmpty() ? acknowledged : entries.getLast().getNumber();
    }

    /**
     * The number of the stanza that was added last.
     *
     * @return the number of the last stanza, or null if the queue is empty.
     */
    @Nullable
    synchronized Long getLastNumber()
    {
        return entries.isEmpty() ? null : entries.getLast().getNumber();
    }

    /**
     * Describes the stanza that was added last, for use in messages.
     *
     * @return the number of the last stanza, or "(none)" if the queue is empty.
     */
    @Nonnull
    synchronized String describeLast()
    {
        return entries.isEmpty() ? "(none)" : String.valueOf(entries.getLast().getNumber());
    }

    /**
     * The number of stanzas in the queue.
     *
     * @return the number of stanzas in the queue.
     */
    synchronized int size()
    {
        return entries.size();
    }

    /**
     * Checks whether the queue holds no stanzas.
     *
     * @return true if the queue holds no stanzas.
     */
    synchronized boolean isEmpty()
    {
        return entries.isEmpty();
    }

    /**
     * The stanzas in the queue, in the order in which they were sent. This is an unmodifiable copy, which does not
     * reflect later changes of the queue.
     *
     * @return the stanzas in the queue.
     */
    @Nonnull
    synchronized Collection<Entry> getEntries()
    {
        return List.copyOf(entries);
    }
}
