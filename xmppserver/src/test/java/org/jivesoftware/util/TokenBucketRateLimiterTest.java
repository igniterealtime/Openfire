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
    void testBurstIsCapped() throws InterruptedException
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 3);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire();
        limiter.tryAcquire(); // Exhaust and reject

        Thread.sleep(2_000); // Allow more than enough time for refill

        // Verify result.
        assertEquals(3, limiter.getAvailableTokens(), "Available tokens must not exceed max burst");
    }

    /**
     * Verifies that permits become available again after sufficient time has elapsed according to the configured
     * sustained rate.
     */
    @Test
    void testRateRefillAllowsNewPermits() throws InterruptedException
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);

        // Execute system under test.
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(1_100);

        final boolean acquiredAfterRefill = limiter.tryAcquire();

        // Verify result.
        assertTrue(acquiredAfterRefill, "Limiter should allow one permit after refill interval");
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
    void testUptimeIncreases() throws InterruptedException
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1);

        // Execute system under test.
        final Duration initialUptime = limiter.getUptime();
        Thread.sleep(50);
        final Duration laterUptime = limiter.getUptime();

        // Verify result.
        assertTrue(laterUptime.compareTo(initialUptime) > 0, "Uptime should increase over time");
    }

    /**
     * Verifies that the average accepted rate is non-negative after events have been processed.
     */
    @Test
    void testAverageAcceptedRateIsNonNegative() throws InterruptedException
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 5);

        // Execute system under test.
        limiter.tryAcquire();
        limiter.tryAcquire();
        Thread.sleep(200);

        final double averageRate = limiter.getAverageAcceptedRatePerSecond();

        // Verify result.
        assertTrue(averageRate >= 0.0, "Average accepted rate must not be negative");
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
     * Verifies that the {@link TokenBucketRateLimiter#unlimited()} instance never rejects events.
     */
    @Test
    void testUnlimitedLimiterNeverRejects()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter unlimitedLimiter = TokenBucketRateLimiter.unlimited();

        // Execute system under test.
        boolean allAcquired = true;
        for (int i = 0; i < 1000; i++) {
            allAcquired &= unlimitedLimiter.tryAcquire();
        }

        // Verify result.
        assertTrue(allAcquired, "Unlimited limiter should never reject any permits");
    }

    /**
     * Verifies that multiple calls to {@link TokenBucketRateLimiter#unlimited()} produce independent instances.
     */
    @Test
    void testUnlimitedLimiterInstancesAreIndependent()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter limiter1 = TokenBucketRateLimiter.unlimited();
        final TokenBucketRateLimiter limiter2 = TokenBucketRateLimiter.unlimited();

        // Execute system under test.
        limiter1.tryAcquire();
        limiter2.tryAcquire();

        // Verify result.
        assertNotSame(limiter1, limiter2, "Each unlimited limiter instance should be independent");
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
}
