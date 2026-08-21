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

import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha512SaslServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SASLAuthentication#isScramMechanism(String)}, which recognizes members of the SCRAM family by the
 * naming convention that RFC 5802 § 4 defines for them.
 *
 * The predicate governs which mechanisms have their advertisement gated on credential availability, so a mechanism
 * that it fails to recognize is offered without that check, and one that it recognizes in error is suppressed.
 */
public class SASLAuthenticationScramMechanismTest
{
    /**
     * Verifies that the SCRAM mechanisms that Openfire implements are recognized.
     */
    @Test
    void isScramMechanism_recognizesImplementedMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertTrue(SASLAuthentication.isScramMechanism(ScramSha1SaslServer.MECHANISM_NAME), "SCRAM-SHA-1 must be recognized as a SCRAM mechanism.");
        assertTrue(SASLAuthentication.isScramMechanism(ScramSha256SaslServer.MECHANISM_NAME), "SCRAM-SHA-256 must be recognized as a SCRAM mechanism.");
        assertTrue(SASLAuthentication.isScramMechanism(ScramSha512SaslServer.MECHANISM_NAME), "SCRAM-SHA-512 must be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that the channel binding variant of a SCRAM mechanism is recognized.
     */
    @Test
    void isScramMechanism_recognizesChannelBindingVariant()
    {
        // Setup test fixture.
        final String mechanismName = ScramSha1SaslServer.MECHANISM_NAME + "-PLUS";

        // Execute system under test.
        final boolean result = SASLAuthentication.isScramMechanism(mechanismName);

        // Verify result.
        assertTrue(result, "The channel binding variant of a SCRAM mechanism must be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that a SCRAM mechanism that Openfire does not implement itself is recognized as a SCRAM mechanism. Such
     * a mechanism can be contributed by a component that supplies its own AuthProvider, and must be subjected to the
     * same credential-availability check as the mechanisms that Openfire implements.
     */
    @Test
    void isScramMechanism_recognizesUnimplementedMechanism()
    {
        // Setup test fixture.
        final String mechanismName = "SCRAM-SHA3-512";

        // Execute system under test.
        final boolean result = SASLAuthentication.isScramMechanism(mechanismName);

        // Verify result.
        assertTrue(result, "A SCRAM mechanism that Openfire does not implement itself must still be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that mechanisms outside the SCRAM family are not recognized. Their advertisement must not be gated on
     * the availability of SCRAM credentials.
     */
    @Test
    void isScramMechanism_rejectsNonScramMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertFalse(SASLAuthentication.isScramMechanism("PLAIN"), "PLAIN must not be recognized as a SCRAM mechanism.");
        assertFalse(SASLAuthentication.isScramMechanism("EXTERNAL"), "EXTERNAL must not be recognized as a SCRAM mechanism.");
        assertFalse(SASLAuthentication.isScramMechanism("ANONYMOUS"), "ANONYMOUS must not be recognized as a SCRAM mechanism.");
        assertFalse(SASLAuthentication.isScramMechanism("DIGEST-MD5"), "DIGEST-MD5 must not be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that a name that merely starts with the letters of the family name, but that does not follow the
     * convention of RFC 5802 § 4, is not recognized.
     */
    @Test
    void isScramMechanism_rejectsNameWithoutSeparator()
    {
        // Setup test fixture.
        final String mechanismName = "SCRAMBLED";

        // Execute system under test.
        final boolean result = SASLAuthentication.isScramMechanism(mechanismName);

        // Verify result.
        assertFalse(result, "A name that does not follow the 'SCRAM-' convention must not be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that recognition is case-sensitive. Mechanism names are uppercased before they reach this predicate, so
     * a lowercase name is not a mechanism name that this server uses.
     */
    @Test
    void isScramMechanism_isCaseSensitive()
    {
        // Setup test fixture.
        final String mechanismName = "scram-sha-1";

        // Execute system under test.
        final boolean result = SASLAuthentication.isScramMechanism(mechanismName);

        // Verify result.
        assertFalse(result, "Recognition must be case-sensitive, as mechanism names are uppercased before they reach this predicate.");
    }
}
