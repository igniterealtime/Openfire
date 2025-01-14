/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire;

import org.xmpp.packet.Packet;
import org.jivesoftware.openfire.auth.UnauthorizedException;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Delivers packets to locally connected streams. This is the opposite
 * of the packet transporter.
 *
 * @author Iain Shigeoka
 */
public interface PacketDeliverer {

    /**
     * Delivers the given stanza based on its recipient and sender.
     *
     * Invocation of this method blocks until the deliverer finishes processing the stanza.
     *
     * The deliverer defers actual routing decisions to other classes.
     *
     * @param stanza the stanza to route
     * @throws PacketException if the packet is null or the packet could not be routed.
     * @throws UnauthorizedException if the user is not authorised
     */
    void deliver(@Nonnull final Packet stanza) throws UnauthorizedException, PacketException;

    /**
     /**
     * Delivers the given stanza based on its recipient and sender.
     *
     * Invocation of this method blocks until the deliverer finishes processing the stanza.
     *
     * The deliverer defers actual routing decisions to other classes.
     *
     * @param stanza the stanza to route
     * @return A future from which any exception thrown while processing the stanza can be obtained.
     */
    default CompletableFuture<Void> deliverAsync(@Nonnull final Packet stanza) {
        try {
            return CompletableFuture.runAsync(() -> {
                try {
                    deliver(stanza);
                } catch (UnauthorizedException e) {
                    throw new CompletionException(e);
                }
            });
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }
}
