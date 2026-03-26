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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link TokenBucketRateLimiter}.
 *
 * These tests validate the externally observable behavior of the rate limiter, including burst handling, sustained
 * rate enforcement, rejection behavior, unlimited mode, and metric reporting.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
class TokenBucketRateLimiterTest
{
    /**
     * Verifies that constructing a rate limiter with zero permits per second throws an exception.
     */
    @Test
    void testConstructorRejectsZeroPermitsPerSecond()
    {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, 10),
            "Zero permits per second should be rejected");
    }

    /**
     * Verifies that constructing a rate limiter with a negative permits-per-second value throws an exception.
     */
    @Test
    void testConstructorRejectsNegativePermitsPerSecond()
    {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(-1, 10),
            "Negative permits per second should be rejected");
    }

    /**
     * Verifies that constructing a rate limiter with a zero max burst value throws an exception.
     */
    @Test
    void testConstructorRejectsZeroMaxBurst()
    {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, 0),
            "Zero max burst should be rejected");
    }

    /**
     * Verifies that constructing a rate limiter with a negative max burst value throws an exception.
     */
    @Test
    void testConstructorRejectsNegativeMaxBurst()
    {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(5, -1),
            "Negative max burst should be rejected");
    }

    /**
     * Verifies that the initial burst capacity is immediately available after construction and that acquiring more than
     * the burst capacity results in rejection.
     */
    @Test
    void testInitialBurstIsAvailable()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 10);

        // Execute system under test.
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire(), "Initial burst permit should be available");
        }
        final boolean extraAttempt = limiter.tryAcquire();

        // Verify result.
        assertFalse(extraAttempt, "Burst capacity should be exhausted");
    }

    /**
     * Verifies that the number of available permits never exceeds the configured maximum burst size, even after a long
     * idle period.
     */
    @Test
    void testBurstIsCapped()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 3, clock);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire(); // Exhaust and reject

        clock.advanceNanos(2_000_000_000L); // Allow more than enough time for full refill.

        // Verify result.
        assertEquals(3, limiter.getAvailableTokens(), "Available tokens must not exceed max burst");
    }

    /**
     * Verifies that permits become available again after sufficient time has elapsed according to the configured
     * sustained rate.
     */
    @Test
    void testRateRefillAllowsNewPermits()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, clock);

        // Execute system under test.
        assertTrue(limiter.tryAcquire(), "First acquire should succeed on a full bucket");
        assertFalse(limiter.tryAcquire(), "Second acquire should fail on an exhausted bucket");

        clock.advanceNanos(1_100_000_000L);

        final boolean acquiredAfterRefill = limiter.tryAcquire();

        // Verify result.
        assertTrue(acquiredAfterRefill, "Limiter should allow one permit after refill interval");
    }

    /**
     * Verifies that getAvailableTokens reflects a refill after sufficient time has elapsed.
     */
    @Test
    void testGetAvailableTokensReflectsRefill()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 2, clock);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire(); // Exhaust

        clock.advanceNanos(1_100_000_000L);

        // Verify result.
        assertEquals(1, limiter.getAvailableTokens(), "getAvailableTokens should reflect tokens added by refill");
    }

    /**
     * Verifies that sub-token elapsed time is preserved and contributes to a later refill.
     */
    @Test
    void testFractionalElapsedTimeIsPreservedAcrossRefills()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 2, clock);

        // Execute system under test.
        assertTrue(limiter.tryAcquire(), "First acquire should consume one of the initial burst tokens");
        assertTrue(limiter.tryAcquire(), "Second acquire should consume the second initial burst token");
        assertFalse(limiter.tryAcquire(), "Bucket should now be exhausted");

        clock.advanceNanos(1_500_000_000L);
        assertTrue(limiter.tryAcquire(), "1.5 seconds should refill exactly one token");
        assertFalse(limiter.tryAcquire(), "Only one token should have been refilled so far");

        clock.advanceNanos(500_000_000L);
        assertTrue(limiter.tryAcquire(), "Remaining 0.5 second should combine with prior remainder to refill one token");
    }

    /**
     * Verifies that elapsed time while the bucket is full does not become hidden credit.
     */
    @Test
    void testElapsedTimeIsNotBankedWhileBucketIsFull()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 2, clock);

        // Execute system under test.
        clock.advanceNanos(5_000_000_000L);
        assertEquals(2, limiter.getAvailableTokens(), "Bucket should remain capped at burst capacity");

        assertTrue(limiter.tryAcquire(), "One token should be immediately available");
        assertTrue(limiter.tryAcquire(), "Second token should be immediately available");
        assertFalse(limiter.tryAcquire(), "No hidden refill credit should exist after draining a full bucket");
    }

    /**
     * Verifies that the limiter correctly counts accepted and rejected events.
     */
    @Test
    void testRejectionsAreCounted()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();

        // Verify result.
        assertEquals(1, limiter.getAcceptedEvents(), "Accepted event count should match");
        assertEquals(2, limiter.getRejectedEvents(), "Rejected event count should match");
        assertEquals(3, limiter.getTotalEvents(), "Total event count should match");
    }

    /**
     * Verifies that the acceptance ratio is calculated correctly based on accepted and rejected events.
     */
    @Test
    void testAcceptanceRatio()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();

        // Verify result.
        assertEquals(0.5, limiter.getAcceptanceRatio(), 0.0001, "Acceptance ratio should be 0.5");
    }

    /**
     * Verifies that the acceptance ratio is reported as {@code 1.0} when no events have been processed.
     */
    @Test
    void testNoEventsAcceptanceRatioIsOne()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);

        // Execute system under test.
        final double acceptanceRatio = limiter.getAcceptanceRatio();

        // Verify result.
        assertEquals(1.0, acceptanceRatio, "Acceptance ratio should be 1.0 when no events have occurred");
    }

    /**
     * Verifies that the configuration getters return the values provided at construction time.
     */
    @Test
    void testConfigurationGetters()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(7, 13);

        // Execute system under test.
        final long permitsPerSecond = limiter.getPermitsPerSecond();
        final long maxBurst = limiter.getMaxBurst();

        // Verify result.
        assertEquals(7, permitsPerSecond, "Permits-per-second configuration should match");
        assertEquals(13, maxBurst, "Max burst configuration should match");
    }

    /**
     * Verifies that the reported uptime increases monotonically as time passes.
     */
    @Test
    void testUptimeIncreases()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, clock);

        // Execute system under test.
        final Duration initialUptime = limiter.getUptime();
        clock.advanceNanos(50_000_000L);
        final Duration laterUptime = limiter.getUptime();

        // Verify result.
        assertTrue(laterUptime.compareTo(initialUptime) > 0, "Uptime should increase over time");
    }

    /**
     * Verifies that the average accepted rate is non-negative after events have been processed.
     */
    @Test
    void testAverageAcceptedRateIsNonNegative()
    {
        // Setup test fixture.
        final FakeNanoClock clock = new FakeNanoClock();
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5, clock);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();
        clock.advanceNanos(2_000_000_000L);

        final double averageRate = limiter.getAverageAcceptedRatePerSecond();

        // Verify result.
        assertEquals(1.0, averageRate, 0.0001, "Average accepted rate should match accepted events per elapsed second");
    }

    /**
     * Verifies that the {@link TokenBucketRateLimiter#unlimited()} instance never rejects events.
     */
    @Test
    void testUnlimitedLimiterNeverRejects()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = TokenBucketRateLimiter.unlimited();

        // Execute system under test.
        boolean allAcquired = true;
        for (int i = 0; i < 1_000; i++) {
            allAcquired &= limiter.tryAcquire();
        }

        // Verify result.
        assertTrue(allAcquired, "Unlimited limiter should never reject any permits");
    }

    /**
     * Verifies that the unlimited limiter reports configuration getters as {@link Long#MAX_VALUE} to indicate that no
     * limit is in effect.
     */
    @Test
    void testUnlimitedLimiterConfigurationGetters()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = TokenBucketRateLimiter.unlimited();

        // Verify result.
        assertEquals(Long.MAX_VALUE, limiter.getPermitsPerSecond(), "Unlimited limiter should report Long.MAX_VALUE for permits per second");
        assertEquals(Long.MAX_VALUE, limiter.getMaxBurst(), "Unlimited limiter should report Long.MAX_VALUE for max burst");
        assertEquals(Long.MAX_VALUE, limiter.getAvailableTokens(), "Unlimited limiter should report Long.MAX_VALUE for available tokens");
    }

    /**
     * Verifies that the unlimited limiter reports its metrics correctly.
     */
    @Test
    void testUnlimitedLimiterMetrics()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = TokenBucketRateLimiter.unlimited();

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();

        // Verify result.
        assertEquals(2, limiter.getAcceptedEvents(), "Accepted events should be counted correctly for unlimited limiter");
        assertEquals(0, limiter.getRejectedEvents(), "Rejected events should always be zero for unlimited limiter");
        assertEquals(2, limiter.getTotalEvents(), "Total events should match the sum of accepted and rejected events");
        assertEquals(1.0, limiter.getAcceptanceRatio(), 0.0001, "Acceptance ratio should always be 1.0 for unlimited limiter");
    }

    /**
     * Verifies that two rate limiter instances maintain independent state and metrics and do not interfere with each other.
     */
    @Test
    void testLimiterInstancesAreIndependent()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter1 = new TokenBucketRateLimiter(5, 3);
        final TokenBucketRateLimiter limiter2 = new TokenBucketRateLimiter(5, 3);

        // Execute system under test.
        limiter1.tryAcquire();
        limiter1.tryAcquire();
        limiter1.tryAcquire();
        limiter1.tryAcquire(); // One rejection

        // Verify result.
        assertEquals(3, limiter1.getAcceptedEvents(), "Limiter1 should count only its own accepted events");
        assertEquals(1, limiter1.getRejectedEvents(), "Limiter1 should count only its own rejected events");
        assertEquals(0, limiter2.getAcceptedEvents(), "Limiter2 should not be affected by events on limiter1");
        assertEquals(3, limiter2.getAvailableTokens(), "Limiter2 tokens should be unaffected by limiter1 consumption");
    }

    /**
     * Verifies that two unlimited limiter instances maintain independent metrics and do not share state.
     */
    @Test
    void testUnlimitedLimiterInstancesAreIndependent()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter1 = TokenBucketRateLimiter.unlimited();
        final TokenBucketRateLimiter limiter2 = TokenBucketRateLimiter.unlimited();

        // Execute system under test.
        limiter1.tryAcquire();
        limiter1.tryAcquire();
        limiter1.tryAcquire();

        // Verify result.
        assertEquals(3, limiter1.getAcceptedEvents(), "Limiter1 should count only its own accepted events");
        assertEquals(0, limiter2.getAcceptedEvents(), "Limiter2 should not be affected by events on limiter1");
    }

    /**
     * A fake {@link System#nanoTime()} implementation that allows for manual increments of time.
     */
    private static final class FakeNanoClock implements LongSupplier
    {
        private final AtomicLong nowNanos = new AtomicLong();

        @Override
        public long getAsLong()
        {
            return nowNanos.get();
        }

        void advanceNanos(final long nanos)
        {
            nowNanos.addAndGet(nanos);
        }
    }
}
