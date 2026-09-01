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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link MechanismName}, which classifies SASL mechanisms by the conventions encoded in their names.
 */
class MechanismNameTest
{
    // -------------------------------------------------------------------------
    // isScram
    // -------------------------------------------------------------------------

    /**
     * Verifies that the SCRAM mechanisms that Openfire implements are recognized.
     */
    @Test
    void isScram_recognizesImplementedMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertTrue(MechanismName.isScram(ScramSha1SaslServer.MECHANISM_NAME), "SCRAM-SHA-1 must be recognized as a SCRAM mechanism.");
        assertTrue(MechanismName.isScram(ScramSha256SaslServer.MECHANISM_NAME), "SCRAM-SHA-256 must be recognized as a SCRAM mechanism.");
        assertTrue(MechanismName.isScram(ScramSha512SaslServer.MECHANISM_NAME), "SCRAM-SHA-512 must be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that the channel binding variant of a SCRAM mechanism is recognized.
     */
    @Test
    void isScram_recognizesChannelBindingVariant()
    {
        // Setup test fixture.
        final String mechanismName = ScramSha1SaslServer.MECHANISM_NAME + "-PLUS";

        // Execute system under test.
        final boolean result = MechanismName.isScram(mechanismName);

        // Verify result.
        assertTrue(result, "The channel binding variant of a SCRAM mechanism must be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that a SCRAM mechanism that Openfire does not implement itself is recognized as a SCRAM mechanism. Such
     * a mechanism can be contributed by a component that supplies its own AuthProvider, and must be subjected to the
     * same credential-availability check as the mechanisms that Openfire implements.
     */
    @Test
    void isScram_recognizesUnimplementedMechanism()
    {
        // Setup test fixture.
        final String mechanismName = "SCRAM-SHA3-512";

        // Execute system under test.
        final boolean result = MechanismName.isScram(mechanismName);

        // Verify result.
        assertTrue(result, "A SCRAM mechanism that Openfire does not implement itself must still be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that mechanisms outside the SCRAM family are not recognized. Their advertisement must not be gated on
     * the availability of SCRAM credentials.
     */
    @Test
    void isScram_rejectsNonScramMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertFalse(MechanismName.isScram("PLAIN"), "PLAIN must not be recognized as a SCRAM mechanism.");
        assertFalse(MechanismName.isScram("EXTERNAL"), "EXTERNAL must not be recognized as a SCRAM mechanism.");
        assertFalse(MechanismName.isScram("ANONYMOUS"), "ANONYMOUS must not be recognized as a SCRAM mechanism.");
        assertFalse(MechanismName.isScram("DIGEST-MD5"), "DIGEST-MD5 must not be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that a name that merely starts with the letters of the family name, but that does not follow the
     * convention of RFC 5802 § 4, is not recognized.
     */
    @Test
    void isScram_rejectsNameWithoutSeparator()
    {
        // Setup test fixture.
        final String mechanismName = "SCRAMBLED";

        // Execute system under test.
        final boolean result = MechanismName.isScram(mechanismName);

        // Verify result.
        assertFalse(result, "A name that does not follow the 'SCRAM-' convention must not be recognized as a SCRAM mechanism.");
    }

    /**
     * Verifies that recognition is case-sensitive. Mechanism names are uppercased before they reach this predicate, so
     * a lowercase name is not a mechanism name that this server uses.
     */
    @Test
    void isScram_isCaseSensitive()
    {
        // Setup test fixture.
        final String mechanismName = "scram-sha-1";

        // Execute system under test.
        final boolean result = MechanismName.isScram(mechanismName);

        // Verify result.
        assertFalse(result, "Recognition must be case-sensitive, as mechanism names are uppercased before they reach this predicate.");
    }

    // -------------------------------------------------------------------------
    // isFast
    // -------------------------------------------------------------------------

    /**
     * Verifies that the HT-* mechanisms of the original HT draft are recognized as FAST mechanisms.
     */
    @Test
    void isFast_recognizesHtMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertTrue(MechanismName.isFast("HT-SHA-256-NONE"), "HT-SHA-256-NONE must be recognized as a FAST mechanism.");
        assertTrue(MechanismName.isFast("HT-SHA-256-UNIQ"), "HT-SHA-256-UNIQ must be recognized as a FAST mechanism.");
        assertTrue(MechanismName.isFast("HT-SHA-512-EXPR"), "HT-SHA-512-EXPR must be recognized as a FAST mechanism.");
    }

    /**
     * Verifies that the HT2-* mechanisms of the working group draft are recognized as FAST mechanisms.
     */
    @Test
    void isFast_recognizesHt2Mechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertTrue(MechanismName.isFast("HT2-SHA-256-NONE"), "HT2-SHA-256-NONE must be recognized as a FAST mechanism.");
        assertTrue(MechanismName.isFast("HT2-SHA-512-ENDP"), "HT2-SHA-512-ENDP must be recognized as a FAST mechanism.");
    }

    /**
     * Verifies that mechanisms outside the HT families are not recognized. A mechanism recognized in error would be
     * omitted from the SASL mechanism list and advertised in the XEP-0484 inline feature instead, where no client
     * would find it.
     */
    @Test
    void isFast_rejectsNonFastMechanisms()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertFalse(MechanismName.isFast("PLAIN"), "PLAIN must not be recognized as a FAST mechanism.");
        assertFalse(MechanismName.isFast("EXTERNAL"), "EXTERNAL must not be recognized as a FAST mechanism.");
        assertFalse(MechanismName.isFast("SCRAM-SHA-1-PLUS"), "SCRAM-SHA-1-PLUS must not be recognized as a FAST mechanism.");
        assertFalse(MechanismName.isFast("ANONYMOUS"), "ANONYMOUS must not be recognized as a FAST mechanism.");
    }

    /**
     * Verifies that recognition of FAST mechanism names is case-insensitive, unlike {@code isScram}.
     *
     * This asymmetry is load-bearing: the mechanism named in the 'mechanism' attribute of a XEP-0484
     * {@code <request-token/>} element is validated against this predicate before it is uppercased, so a client that
     * sends a lowercase name is accepted today. Aligning this predicate with {@code isScram} would silently start
     * rejecting those clients.
     */
    @Test
    void isFast_isCaseInsensitive()
    {
        // Setup test fixture.
        final String mechanismName = "ht-sha-256-none";

        // Execute system under test.
        final boolean result = MechanismName.isFast(mechanismName);

        // Verify result.
        assertTrue(result, "FAST mechanism names must be recognized irrespective of case, as the mechanism named in a " +
            "<request-token/> element is validated before it is normalized.");
    }

    // -------------------------------------------------------------------------
    // requiresChannelBinding / requiredChannelBindingType
    // -------------------------------------------------------------------------

    /**
     * Verifies that the -UNIQ suffix names the tls-unique channel-binding type, per the HT draft, Table 1.
     *
     * @param mechanismName a FAST mechanism that binds to tls-unique
     */
    @ParameterizedTest
    @ValueSource(strings = {"HT-SHA-256-UNIQ", "HT-SHA3-512-UNIQ", "HT2-SHA-512-UNIQ"})
    void requiredChannelBindingType_mapsUniqToTlsUnique(final String mechanismName)
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final String result = MechanismName.requiredChannelBindingType(mechanismName);

        // Verify result.
        assertEquals("tls-unique", result, "The -UNIQ suffix must name the tls-unique channel-binding type.");
    }

    /**
     * Verifies that the -ENDP suffix names the tls-server-end-point channel-binding type, per the HT draft, Table 1.
     *
     * @param mechanismName a FAST mechanism that binds to tls-server-end-point
     */
    @ParameterizedTest
    @ValueSource(strings = {"HT-SHA-256-ENDP", "HT-SHA3-512-ENDP", "HT2-SHA-512-ENDP"})
    void requiredChannelBindingType_mapsEndpToTlsServerEndPoint(final String mechanismName)
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final String result = MechanismName.requiredChannelBindingType(mechanismName);

        // Verify result.
        assertEquals("tls-server-end-point", result, "The -ENDP suffix must name the tls-server-end-point channel-binding type.");
    }

    /**
     * Verifies that the -EXPR suffix names the tls-exporter channel-binding type, per the HT draft, Table 1.
     *
     * @param mechanismName a FAST mechanism that binds to tls-exporter
     */
    @ParameterizedTest
    @ValueSource(strings = {"HT-SHA-256-EXPR", "HT-SHA3-512-EXPR", "HT2-SHA-512-EXPR"})
    void requiredChannelBindingType_mapsExprToTlsExporter(final String mechanismName)
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final String result = MechanismName.requiredChannelBindingType(mechanismName);

        // Verify result.
        assertEquals("tls-exporter", result, "The -EXPR suffix must name the tls-exporter channel-binding type.");
    }

    /**
     * Verifies that a SCRAM channel-binding variant requires channel binding but names no particular type.
     *
     * SCRAM negotiates the type at runtime, so the two predicates deliberately disagree here. A caller that read the
     * null return as "no channel binding needed" would offer the mechanism on a connection that can supply no binding
     * at all.
     */
    @Test
    void scramPlusRequiresChannelBindingWithoutNamingAType()
    {
        // Setup test fixture.
        final String mechanismName = ScramSha1SaslServer.MECHANISM_NAME + "-PLUS";

        // Execute system under test & verify result.
        assertTrue(MechanismName.requiresChannelBinding(mechanismName),
            "A SCRAM -PLUS mechanism must be reported as requiring channel binding.");
        assertNull(MechanismName.requiredChannelBindingType(mechanismName),
            "A SCRAM -PLUS mechanism negotiates its channel-binding type at runtime, so no specific type may be named.");
    }

    /**
     * Verifies that mechanisms outside both naming conventions need no channel binding, including the base variant of
     * a mechanism whose channel-binding variant does.
     */
    @Test
    void mechanismsOutsideBothConventionsRequireNoChannelBinding()
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertFalse(MechanismName.requiresChannelBinding("PLAIN"), "PLAIN must not be reported as requiring channel binding.");
        assertFalse(MechanismName.requiresChannelBinding("EXTERNAL"), "EXTERNAL must not be reported as requiring channel binding.");
        assertFalse(MechanismName.requiresChannelBinding(ScramSha1SaslServer.MECHANISM_NAME), "SCRAM-SHA-1 must not be reported as requiring channel binding.");
        assertNull(MechanismName.requiredChannelBindingType("PLAIN"), "PLAIN must not name a channel-binding type.");
        assertNull(MechanismName.requiredChannelBindingType(ScramSha1SaslServer.MECHANISM_NAME), "SCRAM-SHA-1 must not name a channel-binding type.");
    }

    /**
     * Verifies that the -NONE variant of a FAST mechanism needs no channel binding. These are the variants that a
     * client without TLS channel-binding support, such as one running in a browser, depends on.
     *
     * @param mechanismName a FAST mechanism without channel binding
     */
    @ParameterizedTest
    @ValueSource(strings = {"HT-SHA-256-NONE", "HT-SHA-512-NONE", "HT-SHA3-512-NONE", "HT2-SHA-256-NONE", "HT2-SHA3-512-NONE"})
    void fastNoneVariantsRequireNoChannelBinding(final String mechanismName)
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test & verify result.
        assertFalse(MechanismName.requiresChannelBinding(mechanismName),
            "The -NONE variant of a FAST mechanism must not be reported as requiring channel binding.");
        assertNull(MechanismName.requiredChannelBindingType(mechanismName),
            "The -NONE variant of a FAST mechanism must not name a channel-binding type.");
    }

    /**
     * Verifies that the two channel-binding predicates agree across every FAST mechanism that this server supports.
     *
     * Each predicate holds its own copy of the suffix list, so this guards against the two drifting apart as
     * mechanisms are added. The consequences are silent either way: a mechanism that requires channel binding but
     * names no type is offered on the strength of any binding type being available rather than the one it needs, and
     * a mechanism that names a type but is not reported as requiring binding is offered without any check at all.
     *
     * @param mechanismName a FAST mechanism supported by this server
     */
    @ParameterizedTest
    @MethodSource("supportedFastMechanisms")
    void channelBindingPredicatesAgreeForEveryFastMechanism(final String mechanismName)
    {
        // Setup test fixture.
        final boolean bindsToChannel = !mechanismName.endsWith("-NONE");

        // Execute system under test & verify result.
        assertEquals(bindsToChannel, MechanismName.requiresChannelBinding(mechanismName),
            "Only the -NONE variants of a FAST mechanism are free of channel binding.");
        assertEquals(bindsToChannel, MechanismName.requiredChannelBindingType(mechanismName) != null,
            "A FAST mechanism that requires channel binding must name exactly which type, and one that does not must name none.");
    }

    /**
     * Verifies that every FAST mechanism this server supports is recognized as one. The advertised set is derived from
     * the same list, so a mechanism missing here would be advertised in the SASL mechanism list rather than the
     * XEP-0484 inline feature, and then rejected when a client selected it.
     *
     * @param mechanismName a FAST mechanism supported by this server
     */
    @ParameterizedTest
    @MethodSource("supportedFastMechanisms")
    void isFast_recognizesEverySupportedFastMechanism(final String mechanismName)
    {
        // Setup test fixture.
        // (none required)

        // Execute system under test.
        final boolean result = MechanismName.isFast(mechanismName);

        // Verify result.
        assertTrue(result, "Every FAST mechanism that this server supports must be recognized as a FAST mechanism.");
    }

    /**
     * Returns the FAST mechanism names that this server supports.
     *
     * @return FAST mechanism names (never null, never empty)
     */
    static Stream<String> supportedFastMechanisms()
    {
        return FastTokenManager.MECHANISMS.stream();
    }
}
