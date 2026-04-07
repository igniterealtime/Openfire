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
import java.util.Map;
import java.util.Set;

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
        final String name = "xmpp.example.org";
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
        final String name = "xmpp.example.org";
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
        final String name = "xmpp.example.org";
        final String pattern = "example.org";

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
        final String name = "xmppexample.org";
        final String pattern = "example.org";

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
        final String name = "xmpp.example.org";
        final String pattern = "*.example.org";

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
        final String name = "deeper.xmpp.example.org";
        final String pattern = "*.example.org";

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
        final String name = "xmpp.example.org";
        final String pattern = "*.xmpp.example.org";

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
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern(null, "*.example.org"), "Expected null name to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects an empty name.
     */
    @Test
    public void testNameCoverageRejectsEmptyName() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("", "*.example.org"), "Expected empty name to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects a null pattern.
     */
    @Test
    public void testNameCoverageRejectsNullPattern() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("xmpp.example.org", null), "Expected null pattern to be rejected.");
    }

    /**
     * Verifies that {@link DNSUtil#isNameCoveredByPattern(String, String)} rejects an empty pattern.
     */
    @Test
    public void testNameCoverageRejectsEmptyPattern() throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> DNSUtil.isNameCoveredByPattern("xmpp.example.org", ""), "Expected empty pattern to be rejected.");
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
        final String name = "igniterealtime.org";

        // Execute system under test.
        final String result = DNSUtil.constructLookup(service, protocol, name);

        // Verify results.
        assertEquals("_xmpp-client._tcp.igniterealtime.org.", result, "Expected DNS lookup query to be constructed in '_service._proto.name.' format.");
    }

    /**
     * Verifies that exact DNS override entries are used before a global wildcard override.
     */
    @Test
    public void testResolveXMPPDomainUsesExactOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord exact = new SrvRecord("chat1.external.com", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.com", 5269, false);
        DNSUtil.setDnsOverride(Map.of("chat1.example.org", exact, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.example.org", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(exact)), result, "Expected exact domain override to take precedence over global '*' override.");
    }

    /**
     * Verifies that a global '*' DNS override entry is used when no exact domain override exists.
     */
    @Test
    public void testResolveXMPPDomainUsesGlobalWildcardOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord global = new SrvRecord("fallback.external.com", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat2.example.org", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected global '*' override to be used when no exact override exists.");
    }

    /**
     * Verifies that wildcard domain entries like '*.example.org' are not interpreted as patterns.
     */
    @Test
    public void testResolveXMPPDomainDoesNotUseDomainWildcardOverride() throws Exception
    {
        // Setup test fixture.
        final SrvRecord domainWildcard = new SrvRecord("chat-wildcard.external.com", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.com", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.external.com", domainWildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat2.location2.external.com", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected '*.domain' override key to be treated as literal and fall back to global '*' override.");
        assertNotEquals(List.of(Set.of(domainWildcard)), result, "Expected '*.domain' override key to not be interpreted as a wildcard pattern.");
    }

    /**
     * Verifies that malformed wildcard keys are ignored and do not match as wildcard patterns.
     */
    @Test
    public void testResolveXMPPDomainIgnoresMalformedWildcardKeys() throws Exception
    {
        // Setup test fixture.
        final SrvRecord malformedWildcard = new SrvRecord("malformed-wildcard.external.com", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.com", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*external.com", malformedWildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.external.com", 5269);

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
        final SrvRecord wildcard = new SrvRecord("wildcard.external.com", 5269, false);
        final SrvRecord global = new SrvRecord("fallback.external.com", 5269, false);
        DNSUtil.setDnsOverride(Map.of("*.example.org", wildcard, "*", global));

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain("chat1.external.com", 5269);

        // Verify results.
        assertEquals(List.of(Set.of(global)), result, "Expected global '*' override to be used when no exact or wildcard override matches.");
    }

    /**
     * Verifies that DNS/default fallback is used when no DNS overrides are configured.
     */
    @Test
    public void testResolveXMPPDomainUsesFallbackWhenNoOverridesConfigured() throws Exception
    {
        // Setup test fixture.
        final String domain = "chat1.invalid";
        final int defaultPort = 5269;
        final SrvRecord expectedFallback = new SrvRecord(domain, defaultPort, false, Integer.MAX_VALUE, 0);
        DNSUtil.setDnsOverride(null);

        // Execute system under test.
        final List<Set<SrvRecord>> result = DNSUtil.resolveXMPPDomain(domain, defaultPort);

        // Verify results.
        assertTrue(result.stream().flatMap(Set::stream).anyMatch(expectedFallback::equals), "Expected fallback record based on requested domain and default port when no overrides are configured.");
    }
}
