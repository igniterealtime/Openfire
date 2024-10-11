/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SrvRecordTest
{
    /**
     * Asserts that {@link SrvRecord#from(String[], boolean)} correctly parses an SRV record.
     */
    @Test
    public void testParseSrvRecord() throws Exception
    {
        // Setup test fixture.
        final String input = "1 0 5269 xmpp.example.org";

        // Execute system under test.
        final SrvRecord result = SrvRecord.from(input.split(" "), true);

        // Verify results.
        assertNotNull(result);
        assertEquals(1, result.getPriority());
        assertEquals(0, result.getWeight());
        assertEquals(5269, result.getPort());
        assertEquals("xmpp.example.org", result.getHostname());
    }

    /**
     * Runs {@link SrvRecord#prioritize(SrvRecord[])} on a copy of the
     * DNS SRV xmpp-server records for jabber.org (as they were last 2012).
     */
    @Test
    public void testJabberDotOrgMock() throws Exception {
        // setup
        final SrvRecord fallback = new SrvRecord("fallback.jabber.org", 5269, false, 31, 31);
        final SrvRecord hermes6  = new SrvRecord("hermes6.jabber.org",  5269, false, 30, 30);
        final SrvRecord hermes   = new SrvRecord("hermes.jabber.org",   5269, false, 30, 30);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{fallback, hermes6, hermes});

        // verify
        assertEquals(2, result.size(), "There are two distinct priority values in the input, so two priority groups should be part of the result.");
        assertEquals(2, result.get(0).size(), "The priority group with the lowest priority value (30) should have two entries in it");
        assertEquals(1, result.get(1).size(), "The priority group with the highest priority value (31) should have one entry in it");
        assertTrue(result.get(0).contains(hermes), "The 'hermes' host should have been included somewhere in the first priority group.");
        assertTrue(result.get(0).contains(hermes6), "The 'hermes6' host should have been included somewhere in the first priority group.");
        assertTrue(result.get(1).contains(fallback), "The 'fallback' host should have been included somewhere in the second priority group.");
    }

    /**
     * A basic check that verifies that when one hosts exists, it gets returned in the output.
     */
    @Test
    public void testOneHost() throws Exception {
        // setup
        final SrvRecord host = new SrvRecord("host", 5222, false, 1, 1);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{host});

        // verify
        assertEquals( 1, result.size() );
        assertEquals(host, result.get(0).iterator().next());
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) priority value of zero.
     */
    @Test
    public void testOneHostZeroPriority() throws Exception {
        // setup
        final SrvRecord host = new SrvRecord("host", 5222, false, 0, 1);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{host});

        // verify
        assertEquals(1, result.size());
        assertEquals(host, result.get(0).iterator().next());
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) weight value of zero.
     */
    @Test
    public void testOneHostZeroWeight() throws Exception {
        // setup
        final SrvRecord host = new SrvRecord("host", 5222, false, 1, 0);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{host});

        // verify
        assertEquals(1, result.size());
        assertEquals(host, result.get(0).iterator().next());
    }

    /**
     * Verifies that when a couple of records exist that all have a particular priority, those records are all included
     * in the result, ordered (ascending) by their priority.
     */
    @Test
    public void testDifferentPriorities() throws Exception {
        // setup
        final SrvRecord hostA = new SrvRecord("hostA", 5222, false, 1, 1);
        final SrvRecord hostB = new SrvRecord("hostB", 5222, false, 3, 1);
        final SrvRecord hostC = new SrvRecord("hostC", 5222, false, 2, 1);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{hostA, hostB, hostC});

        // verify
        assertEquals(3, result.size());
        assertEquals(hostA, result.get(0).iterator().next());
        assertEquals(hostC, result.get(1).iterator().next());
        assertEquals(hostB, result.get(2).iterator().next());
    }

    /**
     * A test equal to {@link #testDifferentPriorities()}, but with one of the priorities set to zero.
     */
    @Test
    public void testZeroPriority() throws Exception {
        // setup
        final SrvRecord hostA = new SrvRecord("hostA", 5222, false, 0, 1);
        final SrvRecord hostB = new SrvRecord("hostB", 5222, false, 2, 1);
        final SrvRecord hostC = new SrvRecord("hostC", 5222, false, 1, 1);

        // do magic
        final List<Set<SrvRecord>> result = SrvRecord.prioritize(new SrvRecord[]{hostA, hostB, hostC});

        // verify
        assertEquals(3, result.size());
        assertEquals(hostA, result.get(0).iterator().next());
        assertEquals(hostC, result.get(1).iterator().next());
        assertEquals(hostB, result.get(2).iterator().next());
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
        final SrvRecord hostA = new SrvRecord("hostA", 5222, false, 1, 10);
        final SrvRecord hostB = new SrvRecord("hostB", 5222, false, 1, 10);
        final SrvRecord[] hosts = new SrvRecord[] { hostA, hostB };

        // do magic
        boolean hostAWasFirst = false;
        boolean hostBWasFirst = false;
        final int maxTries = Integer.MAX_VALUE;
        for (int i=0; i<maxTries; i++) {
            final List<Set<SrvRecord>> result = SrvRecord.prioritize(hosts);
            if (hostA.equals(result.get(0).iterator().next())) {
                hostAWasFirst = true;
            }

            if (hostB.equals(result.get(0).iterator().next())) {
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
        final SrvRecord hostA = new SrvRecord("hostA", 5222, false, 1, 0);
        final SrvRecord hostB = new SrvRecord("hostB", 5222, false, 1, 0);
        final SrvRecord[] hosts = new SrvRecord[] { hostA, hostB };

        // do magic
        boolean hostAWasFirst = false;
        boolean hostBWasFirst = false;
        final int maxTries = Integer.MAX_VALUE;
        for (int i=0; i<maxTries; i++) {
            final List<Set<SrvRecord>> result = SrvRecord.prioritize(hosts);
            if (hostA.equals(result.get(0).iterator().next())) {
                hostAWasFirst = true;
            }

            if (hostB.equals(result.get(0).iterator().next())) {
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
}
