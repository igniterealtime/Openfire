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

import java.time.Duration;

/**
 * A thread-safe, synchronized token-bucket rate limiter with metrics.
 *
 * This class limits the rate of arbitrary events. It is not tied to any specific domain such as networking or
 * connections.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public final class TokenBucketRateLimiter
{
    private final long capacity;
    private final long refillTokensPerSecond;

    private long availableTokens;
    private long lastRefillTimeNanos;

    private long acceptedEvents;
    private long rejectedEvents;

    private final long startTimeNanos;

    /**
     * When {@code true}, {@link #tryAcquire()} always succeeds and token bucket accounting is bypassed.
     */
    private final boolean unlimited;

    /**
     * Creates an unlimited rate limiter. Use {@link #unlimited()} to obtain an instance.
     */
    private TokenBucketRateLimiter()
    {
        this.unlimited = true;
        this.refillTokensPerSecond = 0;
        this.capacity = 0;
        this.availableTokens = 0;
        this.lastRefillTimeNanos = 0;
        this.startTimeNanos = System.nanoTime();
    }

    /**
     * Creates a new rate limiter.
     *
     * @param permitsPerSecond sustained rate of permits
     * @param maxBurst maximum number of permits that can accumulate
     */
    public TokenBucketRateLimiter(final long permitsPerSecond, final long maxBurst)
    {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        if (maxBurst <= 0) {
            throw new IllegalArgumentException("maxBurst must be > 0");
        }

        this.unlimited = false;
        this.refillTokensPerSecond = permitsPerSecond;
        this.capacity = maxBurst;
        this.availableTokens = maxBurst;
        this.lastRefillTimeNanos = System.nanoTime();
        this.startTimeNanos = this.lastRefillTimeNanos;
    }

    /**
     * Generates a limiter that allows unlimited events.
     *
     * @return an unlimited rate limiter.
     */
    public static TokenBucketRateLimiter unlimited()
    {
        return new TokenBucketRateLimiter();
    }

    /**
     * Attempts to acquire a single permit.
     *
     * @return {@code true} if the event is allowed, {@code false} otherwise
     */
    public synchronized boolean tryAcquire()
    {
        if (unlimited) {
            acceptedEvents++;
            return true;
        }

        refillIfNeeded();
        if (availableTokens > 0) {
            availableTokens--;
            acceptedEvents++;
            return true;
        }
        rejectedEvents++;
        return false;
    }

    /**
     * Refills tokens based on elapsed time.
     */
    private void refillIfNeeded()
    {
        final long now = System.nanoTime();
        final long elapsed = now - lastRefillTimeNanos;
        if (elapsed <= 0) {
            return;
        }

        // If the multiplication would overflow, elapsed time is so large that the bucket would be completely refilled
        // regardless, so cap directly at capacity.
        final long tokensToAdd;
        if (elapsed > Long.MAX_VALUE / refillTokensPerSecond) {
            tokensToAdd = capacity;
        } else {
            tokensToAdd = Math.min(capacity, (elapsed * refillTokensPerSecond) / 1_000_000_000L);
        }

        if (tokensToAdd > 0) {
            availableTokens = tokensToAdd >= capacity - availableTokens ? capacity : availableTokens + tokensToAdd;
            // Only advance the timestamp when tokens are actually added, so that sub-token elapsed time is preserved
            // and contributes to the next refill rather than being discarded.
            lastRefillTimeNanos = now;
        }
    }

    /**
     * Returns the sustained rate of permits.
     *
     * @return permits per second
     */
    public long getPermitsPerSecond() {
        return unlimited ? Long.MAX_VALUE : refillTokensPerSecond;
    }

    /**
     * Returns the maximum burst size.
     *
     * @return maximum number of permits that can accumulate
     */
    public long getMaxBurst() {
        return unlimited ? Long.MAX_VALUE : capacity;
    }

    /**
     * Returns the currently available number of permits.
     *
     * @return available permits
     */
    public synchronized long getAvailableTokens() {
        if (unlimited) {
            return Long.MAX_VALUE;
        }
        refillIfNeeded();
        return availableTokens;
    }

    /**
     * Returns the total number of events that were allowed by this rate limiter since creation.
     *
     * @return number of accepted events
     */
    public synchronized long getAcceptedEvents() {
        return acceptedEvents;
    }

    /**
     * Returns the total number of events that were rejected by this rate limiter since creation.
     *
     * @return number of rejected events
     */
    public synchronized long getRejectedEvents() {
        return rejectedEvents;
    }

    /**
     * Returns the total number of events evaluated by this limiter.
     *
     * This is the sum of accepted and rejected events.
     *
     * @return total number of events
     */
    public synchronized long getTotalEvents() {
        return acceptedEvents + rejectedEvents;
    }

    /**
     * Returns the ratio of accepted events to total events.
     *
     * A value of {@code 1.0} indicates that no events have been rejected. If no events have been observed yet, this
     * method returns {@code 1.0}.
     *
     * @return acceptance ratio in the range {@code [0.0, 1.0]}
     */
    public synchronized double getAcceptanceRatio() {
        final long total = acceptedEvents + rejectedEvents;
        return total == 0 ? 1.0 : (double) acceptedEvents / total;
    }

    /**
     * Returns the duration for which this rate limiter has been active.
     *
     * The returned duration is based on monotonic time and is not affected by system clock changes.
     *
     * @return uptime duration
     */
    public Duration getUptime() {
        return Duration.ofNanos(System.nanoTime() - startTimeNanos);
    }

    /**
     * Returns the average rate at which events have been accepted since creation.
     *
     * This value represents a coarse-grained average and is intended for diagnostics and reporting only.
     *
     * @return average accepted events per second
     */
    public synchronized double getAverageAcceptedRatePerSecond() {
        final long seconds = Math.max(1L, getUptime().getSeconds());
        return (double) acceptedEvents / seconds;
    }
}
