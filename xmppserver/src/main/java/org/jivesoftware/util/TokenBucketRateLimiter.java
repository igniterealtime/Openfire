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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * A reusable, thread-safe, non-blocking token-bucket rate limiter with metrics.
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

    private final AtomicLong availableTokens;
    private volatile long lastRefillTimeNanos;

    private final LongAdder acceptedEvents = new LongAdder();
    private final LongAdder rejectedEvents = new LongAdder();

    private final long startTimeNanos;

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

        this.refillTokensPerSecond = permitsPerSecond;
        this.capacity = maxBurst;
        this.availableTokens = new AtomicLong(maxBurst);
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
        return new TokenBucketRateLimiter(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Attempts to acquire a single permit.
     *
     * @return {@code true} if the event is allowed, {@code false} otherwise
     */
    public boolean tryAcquire()
    {
        refillIfNeeded();

        final long remaining = availableTokens.getAndUpdate(
            current -> current > 0 ? current - 1 : 0
        );

        if (remaining > 0) {
            acceptedEvents.increment();
            return true;
        }

        rejectedEvents.increment();
        return false;
    }

    /**
     * Refills tokens based on elapsed time.
     */
    private void refillIfNeeded()
    {
        final long now = System.nanoTime();
        final long elapsedNanos = now - lastRefillTimeNanos;

        if (elapsedNanos <= 0) {
            return;
        }

        final long tokensToAdd = (elapsedNanos * refillTokensPerSecond) / 1_000_000_000L;

        if (tokensToAdd <= 0) {
            return;
        }

        synchronized (this) {
            final long currentNow = System.nanoTime();
            final long elapsed = currentNow - lastRefillTimeNanos;

            final long refill = (elapsed * refillTokensPerSecond) / 1_000_000_000L;

            if (refill > 0) {
                final long newValue = Math.min(
                    capacity,
                    availableTokens.get() + refill
                );
                availableTokens.set(newValue);
                lastRefillTimeNanos = currentNow;
            }
        }
    }

    /**
     * Returns the sustained rate of permits.
     *
     * @return permits per second
     */
    public long getPermitsPerSecond() {
        return refillTokensPerSecond;
    }

    /**
     * Returns the maximum burst size.
     *
     * @return maximum number of permits that can accumulate
     */
    public long getMaxBurst() {
        return capacity;
    }

    /**
     * Returns the currently available number of permits.
     *
     * @return available permits
     */
    public long getAvailableTokens() {
        refillIfNeeded();
        return availableTokens.get();
    }

    /**
     * Returns the total number of events that were allowed by this rate limiter since creation.
     *
     * @return number of accepted events
     */
    public long getAcceptedEvents() {
        return acceptedEvents.sum();
    }

    /**
     * Returns the total number of events that were rejected by this rate limiter since creation.
     *
     * @return number of rejected events
     */
    public long getRejectedEvents() {
        return rejectedEvents.sum();
    }

    /**
     * Returns the total number of events evaluated by this limiter.
     *
     * This is the sum of accepted and rejected events.
     *
     * @return total number of events
     */
    public long getTotalEvents() {
        return getAcceptedEvents() + getRejectedEvents();
    }

    /**
     * Returns the ratio of accepted events to total events.
     *
     * A value of {@code 1.0} indicates that no events have been rejected. If no events have been observed yet, this
     * method returns {@code 1.0}.
     *
     * @return acceptance ratio in the range {@code [0.0, 1.0]}
     */
    public double getAcceptanceRatio() {
        final long total = getTotalEvents();
        return total == 0 ? 1.0 : (double) getAcceptedEvents() / total;
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
    public double getAverageAcceptedRatePerSecond() {
        final long seconds = Math.max(1L, getUptime().getSeconds());
        return (double) getAcceptedEvents() / seconds;
    }
}
