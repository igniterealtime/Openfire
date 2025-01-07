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

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Implement and register with a connection to receive notification
 * of the connection closing.
 *
 * @author Iain Shigeoka
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface ConnectionCloseListener
{
    /**
     * Called when a connection is being closed.
     *
     * @param handback The handback object associated with the connection listener during Connection.registerCloseListener()
     * @deprecated replaced by {@link #onConnectionClosing(Object)}
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.1.0
    default void onConnectionClose( Object handback ) {};

    /**
     * Called when a connection is being closed.
     *
     * This method is intended to be used to start asynchronous processes. The Future that is returned is to be used
     * to status of such an asynchronous process.
     *
     * @param handback The handback object associated with the connection listener during Connection.registerCloseListener()
     * @return a Future representing pending completion of the event listener invocation.
     */
    default CompletableFuture<Void> onConnectionClosing(@Nullable final Object handback)
    {
        // The default implementation is a blocking invocation of the method that is being replaced: onConnectionClose()
        // This is designed to facilitate a graceful migration, where pre-existing implementations of this interface
        // will continue to work without change. When the deprecated method is deleted, this default implementation
        // should also be removed.
        try {
            onConnectionClose(handback);
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
        return CompletableFuture.completedFuture(null);
    }
}
