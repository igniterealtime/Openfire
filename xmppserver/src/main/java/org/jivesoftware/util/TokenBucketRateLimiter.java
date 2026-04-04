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

import com.google.common.annotations.VisibleForTesting;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

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
    private final LongSupplier nanoTimeSupplier;

    private long availableTokens;
    private long lastRefillTimeNanos;
    // Leftover refill value in scaled units (1 token = 1_000_000_000 units).
    private long refillRemainder;

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
    private TokenBucketRateLimiter(final LongSupplier nanoTimeSupplier)
    {
        this.nanoTimeSupplier = Objects.requireNonNull(nanoTimeSupplier, "nanoTimeSupplier must not be null");
        this.unlimited = true;
        this.refillTokensPerSecond = 0;
        this.capacity = 0;
        this.availableTokens = 0;
        this.lastRefillTimeNanos = 0;
        this.refillRemainder = 0;
        this.startTimeNanos = nanoTime();
    }

    /**
     * Creates a new rate limiter.
     *
     * @param permitsPerSecond sustained rate of permits
     * @param maxBurst maximum number of permits that can accumulate
     */
    public TokenBucketRateLimiter(final long permitsPerSecond, final long maxBurst)
    {
        this(permitsPerSecond, maxBurst, System::nanoTime);
    }

    /**
     * Creates a new rate limiter with a custom clock.
     *
     * Normally, the system clock is used. This constructor mainly exists for tests.
     *
     * @param permitsPerSecond sustained rate of permits
     * @param maxBurst         maximum number of permits that can accumulate
     * @param nanoTimeSupplier custom clock
     */
    @VisibleForTesting
    TokenBucketRateLimiter(final long permitsPerSecond, final long maxBurst, final LongSupplier nanoTimeSupplier)
    {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond must be > 0");
        }
        if (maxBurst <= 0) {
            throw new IllegalArgumentException("maxBurst must be > 0");
        }

        this.nanoTimeSupplier = Objects.requireNonNull(nanoTimeSupplier, "nanoTimeSupplier must not be null");
        this.unlimited = false;
        this.refillTokensPerSecond = permitsPerSecond;
        this.capacity = maxBurst;
        this.availableTokens = maxBurst;
        this.lastRefillTimeNanos = nanoTime();
        this.refillRemainder = 0;
        this.startTimeNanos = this.lastRefillTimeNanos;
    }

    /**
     * Generates a limiter that allows unlimited events.
     *
     * @return an unlimited rate limiter.
     */
    public static TokenBucketRateLimiter unlimited()
    {
        return new TokenBucketRateLimiter(System::nanoTime);
    }

    /**
     * Returns the current time in nanoseconds from this instance clock.
     *
     * Usually this is the system clock, but tests can provide a custom clock.
     *
     * @return The time in nanoseconds.
     */
    private long nanoTime()
    {
        return nanoTimeSupplier.getAsLong();
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
     * Adds tokens based on elapsed time.
     */
    private void refillIfNeeded()
    {
        final long now = nanoTime();
        final long elapsed = now - lastRefillTimeNanos;
        // With very small intervals, no time may have passed yet.
        if (elapsed <= 0) {
            return;
        }

        if (availableTokens >= capacity) {
            // When already full, do not store extra time as hidden credit.
            lastRefillTimeNanos = now;
            refillRemainder = 0;
            return;
        }

        final long remainingCapacity = capacity - availableTokens;

        // Refill is calculated in scaled integer units: (elapsed * rate) + previous leftover.
        // If this overflows, elapsed time is so large that the bucket must be full.
        if (elapsed > (Long.MAX_VALUE - refillRemainder) / refillTokensPerSecond) {
            availableTokens = capacity;
            lastRefillTimeNanos = now;
            refillRemainder = 0;
            return;
        }

        // Convert elapsed time to scaled units (1_000_000_000 units = 1 token).
        final long refillUnits = elapsed * refillTokensPerSecond + refillRemainder;
        final long tokensToGenerate = refillUnits / 1_000_000_000L;
        // Keep leftover units until they become at least one full token.
        if (tokensToGenerate <= 0) {
            return;
        }

        final long tokensToAdd = Math.min(remainingCapacity, tokensToGenerate);
        availableTokens += tokensToAdd;

        if (availableTokens >= capacity) {
            // Once capacity is reached, drop any extra accrued value.
            lastRefillTimeNanos = now;
            refillRemainder = 0;
        } else {
            // Keep the leftover fraction so refill speed stays accurate over time.
            lastRefillTimeNanos = now;
            refillRemainder = refillUnits % 1_000_000_000L;
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
        return Duration.ofNanos(nanoTime() - startTimeNanos);
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
