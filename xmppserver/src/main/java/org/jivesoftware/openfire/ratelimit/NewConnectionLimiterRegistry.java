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
package org.jivesoftware.openfire.ratelimit;

import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TokenBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry for shared {@link TokenBucketRateLimiter} instances for tracking new connection attempts per
 * {@link ConnectionType}.
 *
 * This registry ensures that all client-to-server connections (C2S: SOCKET_C2S, BOSH_C2S) share the same limiter,
 * while server-to-server connections (S2S: SOCKET_S2S) use a separate limiter. Other connection types return an
 * unlimited limiter that never blocks connections.
 *
 * Rate limiters are per-cluster node and track metrics such as accepted/rejected events. They do not coordinate
 * across cluster nodes. They are automatically updated whenever the associated system properties change. As this
 * replaces the TokenBucketRateLimiter instance, statistics that are collected before the change will be lost.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public final class NewConnectionLimiterRegistry
{
    private static final Logger Log = LoggerFactory.getLogger(NewConnectionLimiterRegistry.class);

    /**
     * Enables or disables rate limiting for new client-to-server (C2S) connections.
     */
    public static final SystemProperty<Boolean> C2S_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.ratelimit.newconnections.c2s.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(newValue -> updateC2SLimiter())
        .build();

    /**
     * The sustained rate of new client-to-server connection attempts allowed per second. This applies to all C2S types (TCP, BOSH, WebSocket) combined.
     */
    public static final SystemProperty<Integer> C2S_PERMITS_PER_SECOND = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.ratelimit.newconnections.c2s.permits_per_second")
        .setDefaultValue(20)
        .setDynamic(true)
        .addListener(newValue -> updateC2SLimiter())
        .build();

    /**
     * The maximum number of new client-to-server connection attempts that can be accepted in a short burst. Helps absorb spikes without exceeding the sustained rate.
     */
    public static final SystemProperty<Integer> C2S_MAX_BURST = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.ratelimit.newconnections.c2s.max_burst")
        .setDefaultValue(50)
        .setDynamic(true)
        .addListener(newValue -> updateC2SLimiter())
        .build();

    /**
     * Enables or disables rate limiting for new server-to-server (S2S) connections.
     */
    public static final SystemProperty<Boolean> S2S_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.ratelimit.newconnections.s2s.enabled")
        .setDefaultValue(false)
        .setDynamic(true)
        .addListener(newValue -> updateS2SLimiter())
        .build();

    /**
     * The sustained rate of new server-to-server connection attempts allowed per second. Applies to all S2S (federation) connection types, which currently is just TCP.
     */
    public static final SystemProperty<Integer> S2S_PERMITS_PER_SECOND = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.ratelimit.newconnections.s2s.permits_per_second")
        .setDefaultValue(20)
        .setDynamic(true)
        .addListener(newValue -> updateS2SLimiter())
        .build();

    /**
     * The maximum number of new server-to-server connection attempts that can be accepted in a short burst. Allows temporary bursts without violating the sustained rate.
     */
    public static final SystemProperty<Integer> S2S_MAX_BURST = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.ratelimit.newconnections.s2s.max_burst")
        .setDefaultValue(50)
        .setDynamic(true)
        .addListener(newValue -> updateS2SLimiter())
        .build();

    /**
     * The minimum time to suppress repeated log messages for rejected new connection attempts of the same connection type; a value of zero or less disables log suppression.
     */
    public static final SystemProperty<Duration> RATE_LIMIT_LOG_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.ratelimit.newconnections.logging.suppress")
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofMinutes(5))
        .setDynamic(true)
        .build();

    /**
     * Tracks the last time a rate-limit rejection was logged per connection type.
     */
    private static final ConcurrentHashMap<ConnectionType, AtomicLong> RATE_LIMIT_LAST_LOG_TIMES = new ConcurrentHashMap<>();

    /**
     * Shared limiter for all client-to-server connections (C2S).
     */
    private static final AtomicReference<TokenBucketRateLimiter> C2S_LIMITER_REF = new AtomicReference<>();

    /**
     * Shared limiter for server-to-server connections (S2S).
     */
    private static final AtomicReference<TokenBucketRateLimiter> S2S_LIMITER_REF = new AtomicReference<>();

    /**
     * Per-unsupported-type unlimited limiters (lazy creation)
     */
    private static final ConcurrentHashMap<ConnectionType, TokenBucketRateLimiter> UNSUPPORTED_LIMITERS = new ConcurrentHashMap<>();

    static {
        updateC2SLimiter();
        updateS2SLimiter();
    }

    /**
     * Retrieves the {@link TokenBucketRateLimiter} associated with the specified {@link ConnectionType}.
     *
     * All client-to-server connection types share the same limiter. Server-to-server connections
     * share a separate limiter. This allows combined rate limiting for logical groups of connections.
     * <p>
     * Unsupported connection types will cause an unlimited limiter that never blocks connections to be returned.
     *
     * @param type the connection type
     * @return the {@link TokenBucketRateLimiter} associated with the connection type.
     */
    @Nonnull
    public static TokenBucketRateLimiter getLimiter(final ConnectionType type)
    {
        if (type == null) {
            throw new IllegalArgumentException("ConnectionType cannot be null");
        }

        if (type == ConnectionType.SOCKET_C2S || type == ConnectionType.BOSH_C2S) {
            return C2S_LIMITER_REF.get();
        }

        if (type == ConnectionType.SOCKET_S2S) {
            return S2S_LIMITER_REF.get();
        }

        return UNSUPPORTED_LIMITERS.computeIfAbsent(type, t -> TokenBucketRateLimiter.unlimited());
    }

    /**
     * Updates the client-to-server (C2S) limiter to use the current property values.
     */
    private static void updateC2SLimiter()
    {
        if (C2S_ENABLED.getValue()) {
            C2S_LIMITER_REF.set(new TokenBucketRateLimiter(C2S_PERMITS_PER_SECOND.getValue(), C2S_MAX_BURST.getValue()));
        } else {
            C2S_LIMITER_REF.set(TokenBucketRateLimiter.unlimited());
        }
    }

    /**
     * Updates the server-to-server (S2S) limiter to use the current property values.
     */
    private static void updateS2SLimiter()
    {
        if (S2S_ENABLED.getValue()) {
            S2S_LIMITER_REF.set(new TokenBucketRateLimiter(S2S_PERMITS_PER_SECOND.getValue(), S2S_MAX_BURST.getValue()));
        } else {
            S2S_LIMITER_REF.set(TokenBucketRateLimiter.unlimited());
        }
    }

    /**
     * Used to log the rejection of a new connection due to rate limiting. Logging will only occur once per connection
     * type within the configured suppression interval. If the suppression interval is zero or negative, every rejection
     * will be logged.
     *
     * @param type The type of connection that was rejected.
     */
    public static void maybeLogRejection(final ConnectionType type)
    {
        final Duration interval = RATE_LIMIT_LOG_INTERVAL.getValue();

        // Suppression disabled: log every rejection.
        if (interval.isZero() || interval.isNegative()) {
            Log.warn("New connection of type {} rejected due to rate limiting.", type);
            return;
        }

        final long now = Instant.now().toEpochMilli();
        final AtomicLong lastLogTime = RATE_LIMIT_LAST_LOG_TIMES.computeIfAbsent(type, t -> new AtomicLong(0));
        final long previous = lastLogTime.get();

        if (now - previous > interval.toMillis()) {
            if (lastLogTime.compareAndSet(previous, now)) {
                Log.warn("New {} connection rejected due to rate limiting. This message will not be logged again for {} to prevent excessive log output.", type, interval);
            }
        }
    }

    private NewConnectionLimiterRegistry()
    {
        // Prevent instantiation
    }
}
