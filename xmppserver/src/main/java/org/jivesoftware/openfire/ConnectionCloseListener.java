/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.event.SessionEventListener;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Implement and register this listener with a connection to receive notifications when the connection is closed.
 *
 * <em>Important:</em> A connection closing does <em>not</em> necessarily imply that the associated session has been
 * closed as well. In some cases, a session may outlive its connection - for example, when Stream Management is enabled.
 *
 * Implementations MUST NOT assume that a callback to any method in this interface indicates that the corresponding
 * session has been closed. To reliably detect session closure events, implementations should instead use
 * {@link SessionEventListener}.
 *
 * @author Iain Shigeoka
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface ConnectionCloseListener
{
    /**
     * Called when a connection is being closed.
     *
     * This method is intended to be used to start asynchronous processes. The Future that is returned is to be used
     * to status of such an asynchronous process.
     *
     * @param handback The handback object associated with the connection listener during Connection.registerCloseListener()
     * @return a Future representing pending completion of the event listener invocation.
     */
    CompletableFuture<Void> onConnectionClosing(@Nullable final Object handback);
}
