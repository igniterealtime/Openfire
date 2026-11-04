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
package org.jivesoftware.openfire.server.s2stest;

/**
 * The verdict on the domain an administrator asked to test, made before any connection is attempted.
 *
 * @author Matthew Vivian
 */
public enum DomainCheck
{
    /**
     * A valid remote domain that the server-to-server policy allows.
     */
    VALID,

    /**
     * Not a valid XMPP domain.
     */
    INVALID,

    /**
     * The domain is served by this server.
     */
    LOCAL,

    /**
     * The domain is a component of this server.
     */
    COMPONENT,

    /**
     * The server-to-server policy does not allow connections to the domain.
     */
    BLOCKED_BY_POLICY
}
