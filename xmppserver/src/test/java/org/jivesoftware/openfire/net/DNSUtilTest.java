/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link org.jivesoftware.openfire.net.DNSUtil}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DNSUtilTest {

    private Map<String, SrvRecord> originalDnsOverride;

    @BeforeEach
    public void storeOriginalDnsOverride()
    {
        final Map<String, SrvRecord> current = DNSUtil.getDnsOverride();
        originalDnsOverride = current == null ? null : new HashMap<>(current);
    }

    @AfterEach
    public void restoreOriginalDnsOverride()
    {
        DNSUtil.setDnsOverride(originalDnsOverride == null ? null : new HashMap<>(originalDnsOverride));
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} finds a match when both
     * arguments have the same value.
     */
    @Test
    public void testNameCoverageExactMatch() throws Exception
    {
        // setup
        final String name = "xmpp.example.invalid";
        final String pattern = name;

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertTrue(result, "Expected exact name match to be covered by pattern, but it was not.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} does not find a match when both
     * arguments have different values.
     */
    @Test
    public void testNameCoverageUnequal() throws Exception
    {
        // setup
        final String name = "xmpp.example.invalid";
        final String pattern = "something.completely.different";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertFalse(result, "Expected different names to not match, but they were considered covered.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} does not find a match when the
     * needle/name is a subdomain of the DNS pattern, without the DNS pattern including a wildcard.
     */
    @Test
    public void testNameCoverageSubdomainNoWildcard() throws Exception
    {
        // setup
        final String name = "xmpp.example.invalid";
        final String pattern = "example.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertFalse(result, "Expected subdomain to not match non-wildcard parent pattern, but it did.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} does not find a match when the
     * last part of the needle/name equals the pattern.
     */
    @Test
    public void testNameCoveragePartialMatchButNoSubdomain() throws Exception
    {
        // setup
        final String name = "xmppexample.invalid";
        final String pattern = "example.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertFalse(result, "Expected partial suffix match without dot-boundary to fail, but it matched.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} finds a match when the
     * needle/name is a subdomain of the DNS pattern, while the DNS pattern includes a wildcard.
     */
    @Test
    public void testNameCoverageSubdomainWithWildcard() throws Exception
    {
        // setup
        final String name = "xmpp.example.invalid";
        final String pattern = "*.example.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertTrue(result, "Expected wildcard pattern to match subdomain, but it did not.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} finds a match when the
     * needle/name is a subdomain of a subdomain of the DNS pattern, while the DNS pattern includes a wildcard.
     */
    @Test
    public void testNameCoverageSubSubdomainWithWildcard() throws Exception
    {
        // setup
        final String name = "deeper.xmpp.example.invalid";
        final String pattern = "*.example.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertTrue(result, "Expected wildcard pattern to match deeper subdomain, but it did not.");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} finds a match when the
     * needle/name equals the domain part of the DNS pattern, while the DNS pattern includes a wildcard.
     *
     * Although somewhat shady, the certificate management in Openfire depends on this to hold true.
     */
    @Test
    public void testNameCoverageSubdomainWithWildcardOfSameDomain() throws Exception
    {
        // setup
        final String name = "xmpp.example.invalid";
        final String pattern = "*.xmpp.example.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertTrue(result, "Expected wildcard pattern to cover same-domain value used by certificate logic, but it did not.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects a null name.
     */
    @Test
    public void testNameCoverageRejectsNullName() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern(null, "*.example.invalid"), "Expected null name to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects an empty name.
     */
    @Test
    public void testNameCoverageRejectsEmptyName() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("", "*.example.invalid"), "Expected empty name to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects a null pattern.
     */
    @Test
    public void testNameCoverageRejectsNullPattern() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("xmpp.example.invalid", null), "Expected null pattern to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects an empty pattern.
     */
    @Test
    public void testNameCoverageRejectsEmptyPattern() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("xmpp.example.invalid", ""), "Expected empty pattern to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#constructLookup(String, String, String)} successfully constructs a query part.
     */
    @Test
    public void testConstructLookup() throws Exception
    {
        // Setup test fixture.
        final String service = "xmpp-client";
        final String protocol = "tcp";
        final String name = "igniterealtime.invalid";

        // Execute system under test.
        final String result = DNSUtil.constructLookup(service, protocol, name);

        // Verify results.
        assertEquals("_xmpp-client._tcp.igniterealtime.invalid.", result, "Expected DNS lookup query to be constructed in '_service._proto.name.' format.");
    }

    /**
     * Verifies that {@link DNSUtil#constructLookup(String, String, String)} lowercases independently of default JVM locale.
     */
    @Test
    public void testConstructLookupWithTurkishDefaultLocale() throws Exception
    {
        // Setup test fixture.
        final Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            final String service = "XmPP-CLIENT";
            final String protocol = "TCP";
            final String name = "IGniterealtime.Invalid";

            // Execute system under test.
            final String result = DNSUtil.constructLookup(service, protocol, name);

            // Verify results.
            assertEquals(
                "_xmpp-client._tcp.igniterealtime.invalid.",
                result,
                "Expected constructLookup to produce locale-independent lowercase output."
            );
        } finally {
            // Tear down test fixture.
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Verifies that exact DNS override entries are used before wildcard and global wildcard overrides.
     */
    @Test
    public void testResolveXMPPDomainUsesExactOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord exact = new SrvRecord("chat1.external.invalid", 5269, false);
        final SrvRecord wildcard = new SrvRecord("domain-wildcard.external.invalid", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("chat1.example.invalid", exact, "*.example.invalid", wildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.example.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(exact)), result, "Expected exact domain override to take precedence over wildcard and global '*' overrides.");
    }

    /**
     * Verifies that a global '*' DNS override entry is used when no exact domain override exists.
     */
    @Test
    public void testResolveXMPPDomainUsesGlobalWildcardOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord global = new SrvRecord("fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat2.example.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected global '*' override to be used when no exact override exists.");
    }

    /**
     * Verifies that the most-specific wildcard domain override is preferred when multiple wildcards match.
     */
    @Test
    public void testResolveXMPPDomainUsesMostSpecificWildcardOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord broadWildcard = new SrvRecord("broad-wildcard.external.invalid", 5269, false);
        final SrvRecord specificWildcard = new SrvRecord("specific-wildcard.external.invalid", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.external.invalid", broadWildcard, "*.location2.external.invalid", specificWildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat2.location2.external.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(specificWildcard)), result, "Expected most-specific wildcard override to be used when multiple wildcard patterns match.");
    }

    /**
     * Verifies that DNS override entries are returned ordered by resolution precedence.
     */
    @Test
    public void testGetDnsOverrideEntriesByPrecedence() throws Exception
    {
        // Setup test fixture.
        DNSUtil.setDnsOverride(Map.of(
            "chat.example.invalid", new SrvRecord("chat.example.invalid", 5269, false),
            "alpha.example.invalid", new SrvRecord("alpha.example.invalid", 5269, false),
            "*.example.invalid", new SrvRecord("wildcard-broad.example.invalid", 5269, false),
            "*.a.example.invalid", new SrvRecord("wildcard-specific-a.example.invalid", 5269, false),
            "*.b.example.invalid", new SrvRecord("wildcard-specific-b.example.invalid", 5269, false),
            "*", new SrvRecord("fallback.example.invalid", 5269, false)
        ));

        // Execute system under test.
        final List<String> sortedKeys = DNSUtil.getDnsOverrideEntriesByPrecedence().stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        // Verify results.
        assertEquals(
            List.of(
                "alpha.example.invalid",
                "chat.example.invalid",
                "*.a.example.invalid",
                "*.b.example.invalid",
                "*.example.invalid",
                "*"
            ),
            sortedKeys,
            "Expected DNS override keys to be ordered as exact, wildcard-most-specific-first, then global fallback."
        );
    }

    /**
     * Verifies that no configured DNS overrides produce an empty ordered entry list.
     */
    @Test
    public void testGetDnsOverrideEntriesByPrecedenceWithoutConfiguration() throws Exception
    {
        // Setup test fixture.
        DNSUtil.setDnsOverride(null);

        // Execute system under test.
        final List<Map.Entry<String, SrvRecord>> result = DNSUtil.getDnsOverrideEntriesByPrecedence();

        // Verify results.
        assertTrue(result.isEmpty(), "Expected no ordered entries when no DNS override configuration exists.");
    }

    /**
     * Verifies that wildcard domain overrides are matched case-insensitively.
     */
    @Test
    public void testResolveXMPPDomainUsesWildcardOverrideCaseInsensitively() throws Exception
    {
        // Setup test fixture.
        final SrvRecord wildcard = new SrvRecord("case-insensitive-wildcard.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.EXTERNAL.INVALID", wildcard));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("ChAt1.ExTeRnAl.InVaLiD", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(wildcard)), result, "Expected wildcard override matching to be case-insensitive.");
    }

    /**
     * Verifies that exact domain overrides are matched case-insensitively.
     * This test ensures that an exact override configured as "Example.org" will
     * match a lookup for "example.org", maintaining consistency with DNS standards.
     */
    @Test
    public void testResolveXMPPDomainUsesExactOverrideCaseInsensitively() throws Exception
    {
        // Setup test fixture.
        final SrvRecord exact = new SrvRecord("case-insensitive-exact.external.invalid", 5269, false);
        final SrvRecord wildcard = new SrvRecord("fallback-wildcard.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("Chat1.Example.Invalid", exact, "*.example.invalid", wildcard));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.example.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(exact)), result, "Expected exact override matching to be case-insensitive.");
    }

    /**
     * Verifies that exact DNS override matching does not depend on the default JVM locale.
     */
    @Test
    public void testResolveXMPPDomainUsesExactOverrideWithTurkishDefaultLocale() throws Exception
    {
        // Setup test fixture.
        final Locale originalLocale = Locale.getDefault();
        final SrvRecord exact = new SrvRecord("locale-safe-exact.external.invalid", 5269, false);
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DNSUtil.setDnsOverride(Map.of("CHAT.EXAMPLE.INVALID", exact));

            // Execute system under test.
            final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat.example.invalid", 5269);

            // Verify results.
            assertEquals(
                List.of(Set.of(exact)),
                result,
                "Expected exact override matching to remain stable when default locale is Turkish."
            );
        } finally {
            // Tear down test fixture.
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Verifies that wildcard pattern matching does not depend on the default JVM locale.
     */
    @Test
    public void testNameCoverageWildcardMatchWithTurkishDefaultLocale() throws Exception
    {
        // Setup test fixture.
        final Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            // Execute system under test.
            final boolean result = DNSUtil.isNameCoveredByPattern("chat.external.invalid", "*.EXTERNAL.INVALID");

            // Verify results.
            assertTrue(result, "Expected wildcard match to remain stable when default locale is Turkish.");
        } finally {
            // Tear down test fixture.
            Locale.setDefault(originalLocale);
        }
    }

    /**
     * Verifies that exact domain overrides take precedence even when configured in different cases.
     * This test ensures mixed-case exact overrides work correctly and take precedence over wildcards.
     */
    @Test
    public void testResolveXMPPDomainExactOverridePrecedenceWithMixedCase() throws Exception
    {
        // Setup test fixture.
        final SrvRecord exact = new SrvRecord("mixed-case-exact.external.invalid", 5269, false);
        final SrvRecord wildcard = new SrvRecord("wildcard-fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("CHAT.EXAMPLE.INVALID", exact, "*.EXAMPLE.INVALID", wildcard));

        // Execute system under test with different casing
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat.example.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(exact)), result, "Expected exact override to take precedence over wildcard even with mixed case configuration.");
    }

    /**
     * Verifies that malformed wildcard keys are ignored and do not match as wildcard patterns.
     */
    @Test
    public void testResolveXMPPDomainIgnoresMalformedWildcardKeys() throws Exception
    {
        // Setup test fixture.
        final SrvRecord malformedWildcard = new SrvRecord("malformed-wildcard.external.invalid", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*external.invalid", malformedWildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.external.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected malformed wildcard key to be ignored and global '*' fallback to be used.");
    }

    /**
     * Verifies that a global '*' DNS override entry is used when neither exact nor wildcard overrides match.
     */
    @Test
    public void testResolveXMPPDomainUsesGlobalWildcardWhenNoWildcardMatches() throws Exception
    {
        // Setup test fixture.
        final SrvRecord wildcard = new SrvRecord("wildcard.external.invalid", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.example.invalid", wildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.external.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected global '*' override to be used when no exact or wildcard override matches.");
    }

    /**
     * Verifies that a broader wildcard DNS override is used when it is the only wildcard match.
     */
    @Test
    public void testResolveXMPPDomainUsesBroaderWildcardWhenOnlyWildcardMatch() throws Exception
    {
        // Setup test fixture.
        final SrvRecord broadWildcard = new SrvRecord("broad.external.invalid", 5269, false);
        final SrvRecord global = new SrvRecord("global.external.invalid", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.external.invalid", broadWildcard, "*.location2.external.invalid", new SrvRecord("specific.external.invalid", 5269, false), "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat3.external.invalid", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(broadWildcard)), result, "Expected broader wildcard override to be used when it is the only matching wildcard.");
    }

    /**
     * Verifies that a global '*' DNS override is returned when no exact or wildcard domain matches exist.
     * This test validates the DNS override precedence logic, not the DNS fallback behavior.
     */
    @Test
    public void testResolveXMPPDomainUsesGlobalOverrideWhenConfigured() throws Exception
    {
        // Setup test fixture.
        final String domain = "test-override.example";
        final int defaultPort = 5269;

        // Use a global wildcard override to test the override path
        final SrvRecord globalOverride = new SrvRecord("override-server.external.invalid", 5555, false, Integer.MAX_VALUE, Integer.MAX_VALUE);
        DNSUtil.setDnsOverride(Map.of("*", globalOverride));

        // Execute system under test
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain(domain, defaultPort);

        // Verify results - should contain only the global override
        assertFalse(result.isEmpty(), "Expected override result to be returned.");
        assertEquals(1, result.size(), "Expected exactly one priority group.");
        assertEquals(1, result.get(0).size(), "Expected exactly one record in the priority group.");

        final SrvRecord actualRecord = result.get(0).iterator().next();
        assertEquals(globalOverride, actualRecord, "Expected the global wildcard override to be returned.");
    }

    /**
     * Verifies the DNS fallback logic that appends domain+defaultPort when DNS lookups return no results.
     * This test clears DNS overrides to exercise the actual DNS resolution and fallback append behavior.
     * Uses .invalid TLD per RFC 6761 to ensure DNS lookups fail quickly and deterministically.
     */
    @Test
    public void testResolveXMPPDomainAppendsFallbackWhenDnsEmpty() throws Exception
    {
        // Setup test fixture - use .invalid TLD that should fail DNS lookups quickly
        final String domain = "no-srv-records.invalid";
        final int defaultPort = 5269;

        // Clear all overrides to force actual DNS resolution path
        DNSUtil.setDnsOverride(Map.of());

        // Execute system under test - will perform real DNS lookups
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain(domain, defaultPort);

        // Verify fallback behavior - should contain the domain+port fallback
        assertFalse(result.isEmpty(), "Expected at least the fallback record when DNS returns no SRV records.");

        // Find the fallback record (domain+defaultPort with priority = Integer.MAX_VALUE)
        boolean foundFallback = result.stream()
            .flatMap(Set::stream)
            .anyMatch(record -> 
                record.getHostname().equals(domain) && 
                record.getPort() == defaultPort && 
                !record.isDirectTLS() &&
                record.getPriority() == Integer.MAX_VALUE &&
                record.getWeight() == 0);
                
        assertTrue(foundFallback, 
            "Expected fallback record (domain+defaultPort) to be appended when DNS lookups return no SRV records for domain: " + domain);
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} does not find a match when the
     * last part of the needle/name equals the pattern suffix but is not a proper subdomain.
     * This test catches the vulnerability where "notexternal.invalid" would incorrectly match "*.external.invalid".
     * This is a critical security boundary test to prevent overly-broad DNS override routing.
     */
    @Test
    public void testNameCoverageWildcardPartialMatchButNoSubdomain() throws Exception
    {
        // setup
        final String name = "notexternal.invalid";
        final String pattern = "*.external.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertFalse(result, "Expected partial suffix match with wildcard but no dot-boundary to fail, but it matched.");
    }

    /**
     * Security boundary test: Verifies that wildcard patterns do not match direct suffix concatenation.
     * Prevents DNS override misrouting where "evilexternal.com" might incorrectly match "*.external.com".
     */
    @Test
    public void testNameCoverageWildcardRejectsDirectSuffixConcatenation() throws Exception
    {
        // setup
        final String name = "evilexternal.com";
        final String pattern = "*.external.com";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern(name, pattern);

        // verify
        assertFalse(result, "Wildcard should not match direct suffix concatenation without dot boundary");
    }

    /**
     * Security boundary test: Verifies that wildcard patterns do not match partial domain name overlap.
     * Prevents DNS override misrouting where "maliciousexample.org" might incorrectly match "*.example.org".
     */
    @Test
    public void testNameCoverageWildcardRejectsPartialDomainOverlap() throws Exception
    {
        // setup
        final String name = "maliciousexample.org";
        final String pattern = "*.example.org";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern(name, pattern);

        // verify
        assertFalse(result, "Wildcard should not match when domain name is concatenated without dot");
    }

    /**
     * Security boundary test: Verifies that wildcard patterns do not match TLD boundary violations.
     * Prevents DNS override misrouting where "example.orgmalicious" might incorrectly match "*.example.org".
     */
    @Test
    public void testNameCoverageWildcardRejectsTldBoundaryViolation() throws Exception
    {
        // setup
        final String name = "example.orgmalicious";
        final String pattern = "*.example.org";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern(name, pattern);

        // verify
        assertFalse(result, "Wildcard should not match when TLD is concatenated");
    }

    /**
     * Positive control test: Verifies that legitimate subdomains still match wildcard patterns correctly.
     * Ensures that security boundary fixes don't break normal wildcard functionality.
     */
    @Test
    public void testNameCoverageWildcardMatchesLegitimateSubdomain() throws Exception
    {
        // setup
        final String name = "legitimate.external.com";
        final String pattern = "*.external.com";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern(name, pattern);

        // verify
        assertTrue(result, "Wildcard should match legitimate subdomain with proper dot boundary");
    }

    /**
     * A test that verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} finds a match when the
     * name exactly equals the domain part of a wildcard pattern (certificate edge case).
     */
    @Test
    public void testNameCoverageWildcardExactDomainMatch() throws Exception
    {
        // setup
        final String name = "external.invalid";
        final String pattern = "*.external.invalid";

        // do magic
        final boolean result = DNSUtil.isNameCoveredByPattern( name, pattern );

        // verify
        assertTrue(result, "Expected exact domain match against wildcard pattern to succeed for certificate compatibility, but it failed.");
    }
}
