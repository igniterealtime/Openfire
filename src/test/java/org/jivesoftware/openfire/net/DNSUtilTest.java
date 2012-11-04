package org.jivesoftware.openfire.net;

import org.jivesoftware.openfire.net.DNSUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link org.jivesoftware.openfire.net.DNSUtil}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class DNSUtilTest {

    //@Test
    public void testJabberDotOrg() throws Exception {
        for (int i=0; i<=10; i++) {
        final List<DNSUtil.HostAddress> list = DNSUtil.resolveXMPPDomain("jabber.org", 5222);
        for(DNSUtil.HostAddress address : list) {
            System.out.println("Address: " + address.toString());
        }
            System.out.println("");
        }
    }

    /**
     * Runs {@link DNSUtil#prioritize(org.jivesoftware.openfire.net.DNSUtil.WeightedHostAddress[])} on a copy of the
     * DNS SRV xmpp-server records for jabber.org (as they were last 2012).
     */
    @Test
    public void testJabberDotOrgMock() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress fallback = new DNSUtil.WeightedHostAddress("fallback.jabber.org", 5269, 31, 31);
        final DNSUtil.WeightedHostAddress hermes6  = new DNSUtil.WeightedHostAddress("hermes6.jabber.org",  5269, 30, 30);
        final DNSUtil.WeightedHostAddress hermes   = new DNSUtil.WeightedHostAddress("hermes.jabber.org",   5269, 30, 30);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{fallback, hermes6, hermes});

        // verify
        Assert.assertEquals("There were three records in the input, the output should have contained the same amount.", 3, result.size());
        Assert.assertTrue("The 'hermes' host should have been included somewhere in the output."   , result.contains(hermes));
        Assert.assertTrue("The 'hermes6' host should have been included somewhere in the output."  , result.contains(hermes6));
        Assert.assertTrue("The 'fallback' host should bhave been included somewhere in the output.", result.contains(fallback));
        Assert.assertEquals("The 'fallback' host should have been the last record in the result."  , fallback, result.get(2));
    }

    /**
     * A basic check that verifies that when one hosts exists, it gets returned in the output.
     */
    @Test
    public void testOneHost() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, 1, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(host, result.get(0));
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) priority value of zero.
     */
    @Test
    public void testOneHostZeroPiority() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, 0, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(host, result.get(0));
    }

    /**
     * A check equal to {@link #testOneHost()}, but using (the edge-case) weight value of zero.
     */
    @Test
    public void testOneHostZeroWeight() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress host = new DNSUtil.WeightedHostAddress("host", 5222, 1, 0);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{host});

        // verify
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(host, result.get(0));
    }

    /**
     * Verifies that when a couple of records exist that all have a particular priority, those records are all included
     * in the result, ordered (ascending) by their priority.
     * @throws Exception
     */
    @Test
    public void testDifferentPriorities() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, 1, 1);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, 3, 1);
        final DNSUtil.WeightedHostAddress hostC = new DNSUtil.WeightedHostAddress("hostC", 5222, 2, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{hostA, hostB, hostC});

        // verify
        Assert.assertEquals(3, result.size());
        Assert.assertEquals(hostA, result.get(0));
        Assert.assertEquals(hostC, result.get(1));
        Assert.assertEquals(hostB, result.get(2));
    }

    /**
     * A test equal to {@link #testDifferentPriorities()}, but with one of the priorities set to zero.
     */
    @Test
    public void testZeroPriority() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, 0, 1);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, 2, 1);
        final DNSUtil.WeightedHostAddress hostC = new DNSUtil.WeightedHostAddress("hostC", 5222, 1, 1);

        // do magic
        final List<DNSUtil.WeightedHostAddress> result = DNSUtil.prioritize(new DNSUtil.WeightedHostAddress[]{hostA, hostB, hostC});

        // verify
        Assert.assertEquals(3, result.size());
        Assert.assertEquals(hostA, result.get(0));
        Assert.assertEquals(hostC, result.get(1));
        Assert.assertEquals(hostB, result.get(2));
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
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, 1, 10);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, 1, 10);
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
        Assert.assertTrue(hostAWasFirst);
        Assert.assertTrue(hostBWasFirst);
    }

    /**
     * A test equal to {@link #testSameWeights()}, but using records with a weight of zero.
     */
    @Test
    public void testZeroWeights() throws Exception {
        // setup
        final DNSUtil.WeightedHostAddress hostA = new DNSUtil.WeightedHostAddress("hostA", 5222, 1, 0);
        final DNSUtil.WeightedHostAddress hostB = new DNSUtil.WeightedHostAddress("hostB", 5222, 1, 0);
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
        Assert.assertTrue(hostAWasFirst);
        Assert.assertTrue(hostBWasFirst);
    }

}
