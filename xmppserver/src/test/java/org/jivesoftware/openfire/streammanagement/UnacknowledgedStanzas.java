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

import org.jivesoftware.openfire.session.DomainPair;
import org.xmpp.packet.Packet;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Gives tests in other packages a view of the stanzas that a stream manager tracks as unacknowledged, of those that it
 * queues while the connection of its session is broken, and of those that it holds until their domain pair is verified
 * again.
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

    /**
     * Returns the IDs of the stanzas that the peer has not acknowledged and that are tracked as sent again, in the order
     * in which they were sent.
     *
     * @param streamManager the stream manager to inspect.
     * @return the IDs of the unacknowledged stanzas that are tracked as sent again.
     */
    @Nonnull
    public static List<String> sentAgainIds(@Nonnull final StreamManager streamManager)
    {
        return streamManager.getUnacknowledgedEntries().stream().filter(UnacknowledgedStanzaQueue.Entry::isSentAgain).map(UnacknowledgedStanzaQueue.Entry::getPacket).map(Packet::getID).toList();
    }

    /**
     * Returns the IDs of the stanzas that are queued, and not sent, while the connection of the session is broken, in
     * the order in which they were queued.
     *
     * @param streamManager the stream manager to inspect.
     * @return the IDs of the queued stanzas.
     */
    @Nonnull
    public static List<String> queuedIds(@Nonnull final RequestingStreamManager streamManager)
    {
        return streamManager.getQueuedStanzas().stream().map(Packet::getID).toList();
    }

    /**
     * Returns the IDs of the stanzas of a domain pair that are held until that domain pair is verified again after the
     * stream was resumed: its unacknowledged stanzas, followed by the others, each in order.
     *
     * @param streamManager the stream manager to inspect.
     * @param domainPair the domain pair.
     * @return the IDs of the held stanzas.
     */
    @Nonnull
    public static List<String> heldIds(@Nonnull final RequestingStreamManager streamManager, @Nonnull final DomainPair domainPair)
    {
        return streamManager.getHeldStanzas(domainPair).stream().map(Packet::getID).toList();
    }
}
