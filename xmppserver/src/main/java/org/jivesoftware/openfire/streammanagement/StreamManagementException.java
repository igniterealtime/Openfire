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

import org.xmpp.packet.PacketError;

import javax.annotation.Nonnull;

/**
 * Thrown when stream management could not be enabled for a session, carrying the error condition that XEP-0198 § 6
 * requires to be reported to the peer.
 *
 * The condition travels with the exception so that a caller which embeds the outcome in a larger response — such as a
 * Bind2 inline request — can report the same reason that a standalone request would have received.
 */
public class StreamManagementException extends RuntimeException
{
    private final PacketError.Condition condition;

    public StreamManagementException(@Nonnull final PacketError.Condition condition, @Nonnull final String message) {
        super(message);
        this.condition = condition;
    }

    @Nonnull
    public PacketError.Condition getCondition() {
        return condition;
    }
}
