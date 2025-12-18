/*
 * Copyright (C) 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link org.jivesoftware.openfire.net.DNSUtil}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DNSUtilTest {

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
        assertTrue( result );
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
        assertFalse( result );
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
        assertFalse( result );
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
        assertFalse( result );
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
        assertTrue( result );
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
        assertTrue( result );
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
        assertTrue( result );
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
        assertEquals("_xmpp-client._tcp.igniterealtime.org.", result);
    }
}
