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
package org.jivesoftware.openfire.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide registry that maps a {@link QuicConnectionId} to the {@link QuicSessionStreamRouter}
 * that owns that QUIC connection.
 *
 * <p>The registry is the authoritative lookup table used during connection migration
 * (RFC 9000 §9). When a client migrates to a new UDP 4-tuple the underlying quiche library
 * fires a {@code QuicPathEvent.PeerMigrated} event on the <em>same</em> {@code QuicChannel}
 * object, so the channel-attribute fast-path in {@link QuicSessionStreamRouter} already works
 * without a registry lookup. The registry provides a secondary lookup path for any future
 * scenario where the channel object is replaced (e.g. a Netty upgrade changes the behaviour)
 * and for administrative introspection.</p>
 *
 * <p>All methods are thread-safe.</p>
 */
public final class QuicSessionRegistry
{
    private static final Logger Log = LoggerFactory.getLogger(QuicSessionRegistry.class);

    private final ConcurrentHashMap<QuicConnectionId, QuicSessionStreamRouter> byConnectionId =
        new ConcurrentHashMap<>();

    /**
     * Registers a router under the given connection ID.
     * If a different router was already registered under the same ID it is replaced and a warning
     * is logged (this should not happen in normal operation).
     *
     * @param cid    the connection ID (must not be {@code null})
     * @param router the router to register (must not be {@code null})
     */
    public void register(@Nonnull final QuicConnectionId cid, @Nonnull final QuicSessionStreamRouter router)
    {
        final QuicSessionStreamRouter previous = byConnectionId.put(cid, router);
        if (previous != null && previous != router) {
            Log.warn("QuicSessionRegistry: replaced existing router for connection {} — this is unexpected", cid);
        } else {
            Log.debug("QuicSessionRegistry: registered router for connection {}", cid);
        }
    }

    /**
     * Returns the router registered under the given connection ID, or {@code null} if none.
     *
     * @param cid the connection ID to look up
     * @return the router, or {@code null}
     */
    @Nullable
    public QuicSessionStreamRouter find(@Nonnull final QuicConnectionId cid)
    {
        return byConnectionId.get(cid);
    }

    /**
     * Removes the registration for the given connection ID.
     * A debug message is logged; if no entry existed the call is a no-op.
     *
     * @param cid the connection ID to unregister
     */
    public void unregister(@Nonnull final QuicConnectionId cid)
    {
        final QuicSessionStreamRouter removed = byConnectionId.remove(cid);
        if (removed != null) {
            Log.debug("QuicSessionRegistry: unregistered router for connection {}", cid);
        }
    }

    /**
     * Returns the number of currently registered connections. Intended for monitoring/testing.
     *
     * @return number of registered connections
     */
    public int size()
    {
        return byConnectionId.size();
    }
}
