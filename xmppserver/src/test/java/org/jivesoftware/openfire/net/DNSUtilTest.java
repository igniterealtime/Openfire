/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
     * Runs {@link DNSUtil#prioritize(org.jivesoftware.openfire.net.DNSUtil.WeightedHostAddress[])} on a copy of the
     * DNS SRV xmpp-server records for jabber.org (as they were last 2012).
     */
    @Test
    public void testJabberDotOrgMock() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress fallback = new DNSUtil.WeightedHostAddress("fallback.jabber.org", 5269, false, 31, 31);
        final DNSUtil.WeightedHostAddress hermes6  = new DNSUtil.WeightedHostAddress("hermes6.jabber.org",  5269, false, 30, 30);
        final DNSUtil.WeightedHostAddress hermes   = new DNSUtil.WeightedHostAddress("hermes.jabber.org",   5269, false, 30, 30);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{fallback, hermes6, hermes});

        // verify
        assertEquals(3, result.size(), "There were three records in the input, the output should have contained the same amount.");
        assertTrue(result.contains(hermes), "The 'hermes' host should have been included somewhere in the output.");
        assertTrue(result.contains(hermes6), "The 'hermes6' host should have been included somewhere in the output.");
        assertTrue(result.contains(fallback), "The 'fallback' host should have been included somewhere in the output.");
        assertEquals(fallback, result.get(2), "The 'fallback' host should have been the last record in the result.");
    }

    /**
     * A basic check that verifies that when one hosts exists, it gets returned in the output.
     */
    @Test
    public void testOneHost() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, false, 1, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        assertEquals( 1, result.size() );
        assertEquals(host, result.get(0));
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) priority value of zero.
     */
    @Test
    public void testOneHostZeroPriority() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, false, 0, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        assertEquals(1, result.size());
        assertEquals(host, result.get(0));
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) weight value of zero.
     */
    @Test
    public void testOneHostZeroWeight() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, false, 1, 0);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        assertEquals(1, result.size());
        assertEquals(host, result.get(0));
    }

    /**
     * Verifies that when a couple of records exist that all have a particular priority, those records are all included
     * in the result, ordered (ascending) by their priority.
     */
    @Test
    public void testDifferentPriorities() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, false, 1, 1);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, false, 3, 1);
        final DNSUtil.WeightedHostAddress hostC = new DNSUtil.WeightedHostAddress("hostC", 5222, false, 2, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{hostA, hostB, hostC});

        // verify
        assertEquals(3, result.size());
        assertEquals(hostA, result.get(0));
        assertEquals(hostC, result.get( 1 ));
        assertEquals(hostB, result.get(2));
    }

    /**
     * A test equal to {@link #testDifferentPriorities()}, but with one of the priorities set to zero.
     */
    @Test
    public void testZeroPriority() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, false, 0, 1);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, false, 2, 1);
        final DNSUtil.WeightedHostAddress hostC = new DNSUtil.WeightedHostAddress("hostC", 5222, false, 1, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{hostA, hostB, hostC});

        // verify
        assertEquals(3, result.size());
        assertEquals(hostA, result.get(0));
        assertEquals(hostC, result.get(1));
        assertEquals(hostB, result.get(2));
    }

    /**
     * A test that verifies that hosts with equal weight are alternately first in the resulting list.
     *
     * The test that is done here re-executes until each of the input records was included in the output as the first
     * record. This indicates that the returning list is at the very least not always ordered in the exact same way.
     */
    @Test
    public void testSameWeights() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, false, 1, 10);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, false, 1, 10);
        final DNSUtil.WeightedHostAddress[] hosts = new DNSUtil.WeightedHostAddress[] { hostA, hostB };

        // do magic
        boolean hostAWasFirst = false;
        boolean hostBWasFirst = false;
        final int maxTries = Integer.MAX_VALUE;
        for (int i=0; i<maxTries; i++) {
            final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(hosts);
            if (hostA.equals(result.get(0))) {
                hostAWasFirst = true;
            }

            if (hostB.equals(result.get(0))) {
                hostBWasFirst = true;
            }

            if (hostAWasFirst && hostBWasFirst) {
                break;
            }

            if (i%1000000==0 && i>0) {
                System.err.println("The last " + i + " iterations of this test all had the same result, which is very unlikely to occur (there should be an even distribution between two possible outcomes). We'll iterate up to "+ maxTries +" times, but you might want to abort the unit test at this point...");
            }
        }

        // verify
        assertTrue( hostAWasFirst );
        assertTrue( hostBWasFirst );
    }

    /**
     * A test equal to {@link #testSameWeights()}, but using records with a weight of zero.
     */
    @Test
    public void testZeroWeights() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, false, 1, 0);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, false, 1, 0);
        final DNSUtil.WeightedHostAddress[] hosts = new DNSUtil.WeightedHostAddress[] { hostA, hostB };

        // do magic
        boolean hostAWasFirst = false;
        boolean hostBWasFirst = false;
        final int maxTries = Integer.MAX_VALUE;
        for (int i=0; i<maxTries; i++) {
            final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(hosts);
            if (hostA.equals(result.get(0))) {
                hostAWasFirst = true;
            }

            if (hostB.equals(result.get(0))) {
                hostBWasFirst = true;
            }

            if (hostAWasFirst && hostBWasFirst) {
                break;
            }

            if (i%1000000==0 && i>0) {
                System.err.println("The last " + i + " iterations of this test all had the same result, which is very unlikely to occur (there should be an even distribution between two possible outcomes). We'll iterate up to "+ maxTries +" times, but you might want to abort the unit test at this point...");
            }
        }

        // verify
        assertTrue(hostAWasFirst);
        assertTrue(hostBWasFirst);
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
