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
package org.jivesoftware.openfire.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link ServerSession.AuthenticationMethod}.
 */
public class ServerSessionTest
{
    /**
     * Verifies that EXTERNAL maps to SASL_EXTERNAL.
     */
    @Test
    public void shouldMapExternalToSaslExternal()
    {
        // Setup test fixture.
        final String mechanismName = "EXTERNAL";

        // Execute system under test.
        final ServerSession.AuthenticationMethod result = ServerSession.AuthenticationMethod.fromSaslMechanismName(mechanismName);

        // Verify result.
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result, "Expected EXTERNAL to map to SASL_EXTERNAL.");
    }

    /**
     * Verifies that EXTERNAL mapping is case-insensitive.
     */
    @Test
    public void shouldMapExternalCaseInsensitiveToSaslExternal()
    {
        // Setup test fixture.
        final String mechanismName = "external";

        // Execute system under test.
        final ServerSession.AuthenticationMethod result = ServerSession.AuthenticationMethod.fromSaslMechanismName(mechanismName);

        // Verify result.
        assertEquals(ServerSession.AuthenticationMethod.SASL_EXTERNAL, result, "Expected lowercase external to map to SASL_EXTERNAL.");
    }

    /**
     * Verifies that non-EXTERNAL mechanism names map to OTHER.
     */
    @Test
    public void shouldMapNonExternalToOther()
    {
        // Setup test fixture.
        final String mechanismName = "PLAIN";

        // Execute system under test.
        final ServerSession.AuthenticationMethod result = ServerSession.AuthenticationMethod.fromSaslMechanismName(mechanismName);

        // Verify result.
        assertEquals(ServerSession.AuthenticationMethod.OTHER, result, "Expected non-EXTERNAL mechanism names to map to OTHER.");
    }
}

