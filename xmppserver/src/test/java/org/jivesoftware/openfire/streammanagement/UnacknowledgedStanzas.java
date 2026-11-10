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

import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Gives tests in other packages a view of the stanzas that a stream manager tracks as unacknowledged.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public final class UnacknowledgedStanzas
{
    private UnacknowledgedStanzas() {}

    /**
     * Returns the IDs of the stanzas that the peer has not acknowledged, in the order in which they were sent. Unlike
     * observing them through {@link StreamManager#onClose}, this leaves the stream manager as it is, and does not depend
     * on how the end of the stream handles them.
     *
     * @param streamManager the stream manager to inspect.
     * @return the IDs of the unacknowledged stanzas.
     */
    @Nonnull
    public static List<String> ids(@Nonnull final StreamManager streamManager)
    {
        return streamManager.getUnacknowledgedEntries().stream().map(UnacknowledgedStanzaQueue.Entry::getPacket).map(Packet::getID).toList();
    }
}
