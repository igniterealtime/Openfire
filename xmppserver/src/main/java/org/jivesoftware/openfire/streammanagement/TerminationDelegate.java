/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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

import javax.annotation.Nonnull;
import java.time.Duration;

/**
 * Determines if a session that is detached (in context of Stream Management) can be terminated.
 *
 * An instance is associated to a session through registration with {@link StreamManager#addTerminationDelegate(TerminationDelegate)}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public interface TerminationDelegate
{
    /**
     * Invoked to lear if the implementation allows the associated session to be terminated.
     *
     * A server-configured allowable inactivity duration is provided as an argument. Implementations can, but need not
     * adhere to this value.
     *
     * Implementations can assume that the associated session is in detached state when this method is invoked.
     *
     * @param allowableInactivity The configured allowable inactivity duration for sessions in a detached state.
     * @return true if the session is to be terminated, otherwise false.
     */
    boolean shouldTerminate(@Nonnull final Duration allowableInactivity);
}
