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
package org.jivesoftware.openfire.net;

import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sasl2SuccessTest
{
    @Test
    void usesResumedFullJidAsAuthorizationIdentifier()
    {
        final JID resumedAddress = new JID("romeo", "example.org", "balcony");

        final String result = SASLAuthentication.authorizationIdentityForSasl2Success("romeo@example.org", resumedAddress);

        assertEquals("romeo@example.org/balcony", result);
    }

    @Test
    void retainsAuthenticatedIdentityWithoutResumption()
    {
        final String result = SASLAuthentication.authorizationIdentityForSasl2Success("romeo@example.org", null);

        assertEquals("romeo@example.org", result);
    }
}
