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

import io.netty.channel.ChannelId;

import java.util.Objects;

/**
 * Immutable value type wrapping a Netty {@link ChannelId} that identifies a QUIC connection.
 *
 * <p>We use the Netty {@code ChannelId} of the {@code QuicChannel} as the connection identifier
 * because:</p>
 * <ul>
 *   <li>It is unique per channel instance within the JVM.</li>
 *   <li>The {@code QuicChannel} object is <em>reused</em> across path migrations (quiche fires
 *       {@code QuicPathEvent.PeerMigrated} on the same channel), so the {@code ChannelId} remains
 *       stable across a migration event.</li>
 *   <li>It does not require access to Netty-internal APIs (unlike the raw QUIC DCID bytes which
 *       are only accessible via package-private methods on {@code QuicheQuicChannel}).</li>
 * </ul>
 */
public final class QuicConnectionId
{
    private final ChannelId channelId;

    public QuicConnectionId(final ChannelId channelId)
    {
        this.channelId = Objects.requireNonNull(channelId, "channelId");
    }

    public ChannelId getChannelId()
    {
        return channelId;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuicConnectionId other)) {
            return false;
        }
        return channelId.equals(other.channelId);
    }

    @Override
    public int hashCode()
    {
        return channelId.hashCode();
    }

    @Override
    public String toString()
    {
        return "QuicConnectionId{" + channelId.asShortText() + "}";
    }
}
