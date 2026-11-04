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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that authenticating to a remote domain that is missing or not a domain name fails without an attempt to
 * connect, as it did before each attempt was recorded.
 */
public class LocalOutgoingServerSessionInvalidDomainTest
{
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"remote example.org"})
    public void testInvalidRemoteDomainFails(final String remoteDomain)
    {
        // Execute system under test.
        final DomainAuthResult result = LocalOutgoingServerSession.authenticateDomain(new DomainPair("example.org", remoteDomain));

        // Verify results.
        assertFalse(result.isSuccess(), "Authentication to the invalid remote domain '" + remoteDomain + "' must fail, rather than throw.");
    }
}
