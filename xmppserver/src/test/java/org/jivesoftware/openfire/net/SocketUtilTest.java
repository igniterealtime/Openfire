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

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns.Target;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies what {@link SocketUtil#createSocketToXmppDomain(String, int, AttemptRecorder)} records about DNS and the
 * TCP connection, which decides checks 2 and 3 of the S2S connection test.
 *
 * @author Matthew Vivian
 */
public class SocketUtilTest
{
    private static final String DOMAIN = "remote.example.invalid";

    private Map<String, SrvRecord> originalDnsOverride;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

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

    // DNS classification

    @Test
    public void testSrvRecordsAreReportedWithoutTheFallback()
    {
        // Setup test fixture: SRV records, plus the fallback that DNSUtil always appends.
        final List<Set<SrvRecord>> records = List.of(
            Set.of(new SrvRecord("xmpp.remote.example.invalid", 5269, false, 5, 0)),
            Set.of(new SrvRecord(DOMAIN, 5269, false, Integer.MAX_VALUE, 0)));

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(records, false);

        // Verify results.
        assertEquals(Dns.Outcome.SRV_RECORDS, dns.outcome(), "published SRV records must be reported as such");
        assertEquals(List.of(Target.plain("xmpp.remote.example.invalid:5269")), dns.targets().values(), "the fallback that is always appended must not be listed as an SRV record");
    }

    @Test
    public void testOnlyTheFallbackIsReportedAsSuch()
    {
        // Setup test fixture.
        final List<Set<SrvRecord>> records = List.of(Set.of(new SrvRecord(DOMAIN, 5269, false, Integer.MAX_VALUE, 0)));

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(records, false);

        // Verify results.
        assertEquals(Dns.Outcome.FALLBACK_ONLY, dns.outcome(), "with no SRV records, only the domain name itself was used");
        assertEquals(List.of(Target.plain(DOMAIN + ":5269")), dns.targets().values(),"the fallback host and port must be listed");
    }

    @Test
    public void testDirectTlsRecordsAreMarked()
    {
        // Setup test fixture.
        final List<Set<SrvRecord>> records = List.of(Set.of(new SrvRecord("xmpps.remote.example.invalid", 5270, true, 5, 0)));

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(records, false);

        // Verify results.
        assertEquals(List.of(Target.directTls("xmpps.remote.example.invalid:5270")), dns.targets().values(), "a direct TLS record must be distinguishable from a plain one");
    }

    @Test
    public void testOverrideIsReportedAsSuch()
    {
        // Setup test fixture.
        final List<Set<SrvRecord>> records = List.of(Set.of(new SrvRecord("xmpp2", 5269, false)));

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(records, true);

        // Verify results.
        assertEquals(Dns.Outcome.OVERRIDE, dns.outcome(), "an address configured as a DNS override must not be reported as an SRV record");
        assertEquals(List.of(Target.plain("xmpp2:5269")), dns.targets().values(),"the overriding address must be listed");
    }

    // Connection attempt classification

    @Test
    public void testRefusedConnectionIsClassified()
    {
        final SocketUtil.ConnectionAttempt attempt = SocketUtil.classifyFailure("192.0.2.10:5269", new ConnectException("Connection refused"));
        assertEquals(Tcp.Outcome.REFUSED, attempt.outcome(), "a refused connection must be told apart from other failures");
    }

    @Test
    public void testTimedOutConnectionIsClassified()
    {
        final SocketUtil.ConnectionAttempt attempt = SocketUtil.classifyFailure("192.0.2.10:5269", new SocketTimeoutException("Connect timed out"));
        assertEquals(Tcp.Outcome.TIMED_OUT, attempt.outcome(), "a connection that timed out must be told apart from a refused one");
    }

    @Test
    public void testConnectionTimedOutByTheOperatingSystemIsClassifiedAsTimedOut()
    {
        // Setup test fixture: when the operating system gives up on an unanswered connection before the socket timeout,
        // Java reports it as a ConnectException, the same type as a refusal, with a message that says it timed out.
        final ConnectException linux = new ConnectException("Connection timed out");
        final ConnectException macOs = new ConnectException("Operation timed out");

        // Execute system under test and verify results.
        assertEquals(Tcp.Outcome.TIMED_OUT, SocketUtil.classifyFailure("192.0.2.10:5269", linux).outcome(), "a connection the operating system timed out must not be reported as refused");
        assertEquals(Tcp.Outcome.TIMED_OUT, SocketUtil.classifyFailure("192.0.2.10:5269", macOs).outcome(), "a connection the operating system timed out must not be reported as refused, whatever the platform's wording");
    }

    @Test
    public void testOtherFailureKeepsItsReason()
    {
        // Execute system under test.
        final SocketUtil.ConnectionAttempt attempt = SocketUtil.classifyFailure("192.0.2.10:5269", new NoRouteToHostException("No route to host"));

        // Verify results.
        assertEquals(Tcp.Outcome.FAILED, attempt.outcome(), "another failure must be reported as such");
        assertEquals("No route to host", attempt.reason(), "the failure's own message must be kept");
    }

    @Test
    public void testAttemptInterruptedBecauseAnotherSucceededIsIgnored()
    {
        assertNull(SocketUtil.classifyFailure("192.0.2.10:5269", new ClosedByInterruptException()), "an attempt cancelled because another address connected first is not a failure");
    }

    @Test
    public void testAllAttemptsRefusedSummariseAsRefused()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(List.of(
            new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.REFUSED, null),
            new SocketUtil.ConnectionAttempt("[2001:db8::10]:5269", Tcp.Outcome.REFUSED, null)));

        // Verify results.
        assertEquals(Tcp.Outcome.REFUSED, tcp.outcome(), "when every address refused, the result is refused");
        assertEquals(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.refused("[2001:db8::10]:5269")), tcp.failures().values(), "every address tried must be listed");
    }

    @Test
    public void testAllAttemptsTimedOutSummariseAsTimedOut()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(List.of(new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.TIMED_OUT, null)));

        // Verify results.
        assertEquals(Tcp.allFailed(List.of(AddressFailure.timedOut("192.0.2.10:5269"))), tcp, "when every address timed out, the result is timed out");
        assertEquals(Tcp.Outcome.TIMED_OUT, tcp.outcome(), "when every address timed out, the result is timed out");
    }

    @Test
    public void testMixedFailuresSummariseWithEachReason()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(List.of(
            new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.REFUSED, null),
            new SocketUtil.ConnectionAttempt("192.0.2.11:5269", Tcp.Outcome.TIMED_OUT, null)));

        // Verify results.
        assertEquals(Tcp.Outcome.FAILED, tcp.outcome(), "different failures at different addresses cannot be summarised as one kind");
        assertEquals(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.timedOut("192.0.2.11:5269")), tcp.failures().values(), "every address tried must be listed, with what happened at it");
    }

    @Test
    public void testManyDnsTargetsAreCapped()
    {
        // Setup test fixture: the remote's DNS answer, which it controls, lists more targets than are kept.
        final int extra = 5;
        final Set<SrvRecord> prioritySet = new HashSet<>();
        for (int i = 0; i < AttemptMilestones.MAX_LISTED_VALUES + extra; i++) {
            prioritySet.add(new SrvRecord("xmpp" + i + ".remote.example.invalid", 5269, false, 5, 0));
        }

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(List.of(prioritySet), false);

        // Verify results.
        assertEquals(AttemptMilestones.MAX_LISTED_VALUES, dns.targets().values().size(), "only the first targets must be kept");
        assertEquals(extra, dns.targets().leftOut(), "the number of targets left out must be kept");
    }

    @Test
    public void testFewDnsTargetsAreNotCapped()
    {
        // Setup test fixture.
        final Set<SrvRecord> prioritySet = new HashSet<>();
        for (int i = 0; i < AttemptMilestones.MAX_LISTED_VALUES; i++) {
            prioritySet.add(new SrvRecord("xmpp" + i + ".remote.example.invalid", 5269, false, 5, 0));
        }

        // Execute system under test.
        final Dns dns = SocketUtil.classifyDns(List.of(prioritySet), false);

        // Verify results.
        assertEquals(AttemptMilestones.MAX_LISTED_VALUES, dns.targets().values().size(), "targets up to the limit must all be kept");
        assertEquals(0, dns.targets().leftOut(), "nothing must be counted as left out");
    }

    @Test
    public void testManyRefusedAttemptsAreCapped()
    {
        // Setup test fixture.
        final int extra = 5;
        final List<SocketUtil.ConnectionAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < AttemptMilestones.MAX_LISTED_VALUES + extra; i++) {
            attempts.add(new SocketUtil.ConnectionAttempt("192.0.2." + i + ":5269", Tcp.Outcome.REFUSED, null));
        }

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(attempts);

        // Verify results.
        assertEquals(Tcp.Outcome.REFUSED, tcp.outcome(), "the outcome must still cover every attempt");
        assertEquals(AttemptMilestones.MAX_LISTED_VALUES, tcp.failures().values().size(), "only the first addresses tried must be kept");
        assertEquals("192.0.2.0:5269", tcp.failures().values().get(0).endpoint(), "the addresses kept must be the first ones tried");
        assertEquals(extra, tcp.failures().leftOut(), "the number of addresses left out must be kept");
    }

    @Test
    public void testManyMixedFailuresAreCapped()
    {
        // Setup test fixture.
        final int extra = 5;
        final List<SocketUtil.ConnectionAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < AttemptMilestones.MAX_LISTED_VALUES + extra; i++) {
            attempts.add(new SocketUtil.ConnectionAttempt("192.0.2." + i + ":5269", i % 2 == 0 ? Tcp.Outcome.REFUSED : Tcp.Outcome.TIMED_OUT, null));
        }

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(attempts);

        // Verify results.
        assertEquals(Tcp.Outcome.FAILED, tcp.outcome(), "different failures at different addresses cannot be summarised as one kind");
        assertEquals(AttemptMilestones.MAX_LISTED_VALUES, tcp.failures().values().size(), "failures at addresses beyond the limit must be left out");
        assertEquals(extra, tcp.failures().leftOut(), "the number of addresses left out must be kept");
    }

    // Classification of a connection that was made

    @Test
    public void testConnectionAtTheFirstAddressIsConnected()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.10:5269", 0, false, List.of());

        // Verify results.
        assertEquals(Tcp.connected("192.0.2.10:5269"), tcp, "a connection with no failed address before it must be recorded as connected");
    }

    @Test
    public void testConnectionAfterFailedAddressesRecordsTheFailures()
    {
        // Setup test fixture: a higher-priority address timed out before another one connected.
        final List<SocketUtil.ConnectionAttempt> failed = List.of(
            new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.TIMED_OUT, null),
            new SocketUtil.ConnectionAttempt("[2001:db8::10]:5269", Tcp.Outcome.REFUSED, null));

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.11:5269", 1, false, failed);

        // Verify results.
        assertEquals(Tcp.connectedAfterFailures("192.0.2.11:5269", List.of(AddressFailure.timedOut("192.0.2.10:5269"), AddressFailure.refused("[2001:db8::10]:5269"))), tcp,
            "the addresses that failed before the connection was made must be recorded with it, so that a dead address is not hidden by a working one");
    }

    @Test
    public void testFailuresBeforeAConnectionAreCapped()
    {
        // Setup test fixture.
        final int extra = 5;
        final List<SocketUtil.ConnectionAttempt> failed = new ArrayList<>();
        for (int i = 0; i < AttemptMilestones.MAX_LISTED_VALUES + extra; i++) {
            failed.add(new SocketUtil.ConnectionAttempt("192.0.2." + i + ":5269", Tcp.Outcome.REFUSED, null));
        }

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.100:5269", 1, false, failed);

        // Verify results.
        assertEquals(AttemptMilestones.MAX_LISTED_VALUES, tcp.failures().values().size(), "the failures must be capped");
        assertEquals(extra, tcp.failures().leftOut(), "the number of failures left out must be kept");
    }

    @Test
    public void testConnectionToTheFallbackDespiteSrvRecordsRecordsTheFailures()
    {
        // Setup test fixture: the server that the SRV record names refused the connection.
        final List<SocketUtil.ConnectionAttempt> failed = List.of(new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.REFUSED, null));

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.20:5269", 1, true, failed);

        // Verify results.
        assertEquals(Tcp.connectedToFallback("192.0.2.20:5269", List.of(AddressFailure.refused("192.0.2.10:5269"))), tcp,
            "a connection to the domain name itself, made because no server named in the SRV records could be reached, must be recorded as such");
    }

    @Test
    public void testConnectionToTheFallbackWithoutAnyFailureMeansTheSrvTargetsHadNoAddress()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.20:5269", 1, true, List.of());

        // Verify results.
        assertEquals(Tcp.connectedToFallback("192.0.2.20:5269", List.of()), tcp, "a connection to the domain name itself with no failed address before it must be recorded without failures");
    }

    @Test
    public void testFailureBesideTheConnectionInItsPrioritySetIsNotRecorded()
    {
        // Setup test fixture: the IPv6 address of the server could not be reached (as from a host without IPv6), while
        // its IPv4 address, in the same priority set and tried alongside it, connected.
        final List<SocketUtil.ConnectionAttempt> failed = List.of(
            new SocketUtil.ConnectionAttempt("[2001:db8::10]:5269", Tcp.Outcome.FAILED, "Network is unreachable", 0));

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.10:5269", 0, false, failed);

        // Verify results.
        assertEquals(Tcp.connected("192.0.2.10:5269"), tcp,
            "an address that failed while another address of the same priority set connected did not delay the connection, so it must not turn the connection into a warning");
    }

    @Test
    public void testOnlyFailuresInEarlierPrioritySetsAreRecordedWithTheConnection()
    {
        // Setup test fixture: the higher-priority server timed out; then, in the next priority set, the IPv6 address
        // could not be reached while the IPv4 address connected.
        final List<SocketUtil.ConnectionAttempt> failed = List.of(
            new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.TIMED_OUT, null, 0),
            new SocketUtil.ConnectionAttempt("[2001:db8::11]:5269", Tcp.Outcome.FAILED, "Network is unreachable", 1));

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.11:5269", 1, false, failed);

        // Verify results.
        assertEquals(Tcp.connectedAfterFailures("192.0.2.11:5269", List.of(AddressFailure.timedOut("192.0.2.10:5269"))), tcp,
            "only the failures of priority sets tried before the one that connected must be recorded with the connection");
    }

    @Test
    public void testFallbackRecordsOnlyFailuresOfTheSrvTargets()
    {
        // Setup test fixture: the server that the SRV record names refused the connection; then, for the domain name
        // itself, the IPv6 address could not be reached while the IPv4 address connected.
        final List<SocketUtil.ConnectionAttempt> failed = List.of(
            new SocketUtil.ConnectionAttempt("192.0.2.10:5269", Tcp.Outcome.REFUSED, null, 0),
            new SocketUtil.ConnectionAttempt("[2001:db8::20]:5269", Tcp.Outcome.FAILED, "Network is unreachable", 1));

        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnection("192.0.2.20:5269", 1, true, failed);

        // Verify results.
        assertEquals(Tcp.connectedToFallback("192.0.2.20:5269", List.of(AddressFailure.refused("192.0.2.10:5269"))), tcp,
            "a failure beside the connection to the domain name itself must not be listed as a failure of the servers the SRV records name");
    }

    @Test
    public void testFallbackIsRecognisedOnlyWhenSrvRecordsWerePublished()
    {
        // Setup test fixture.
        final Set<SrvRecord> fallback = Set.of(new SrvRecord(DOMAIN, 5269, false, Integer.MAX_VALUE, 0));
        final Set<SrvRecord> srv = Set.of(new SrvRecord("xmpp.remote.example.invalid", 5269, false, 5, 0));

        // Execute system under test, and verify results.
        assertTrue(SocketUtil.isFallbackDespiteSrvRecords(List.of(srv, fallback), false, fallback), "the domain name itself, tried after published SRV records, must be recognised as the fallback");
        assertFalse(SocketUtil.isFallbackDespiteSrvRecords(List.of(srv, fallback), false, srv), "a priority set of published SRV records is not the fallback");
        assertFalse(SocketUtil.isFallbackDespiteSrvRecords(List.of(fallback), false, fallback), "without SRV records, the domain name itself is the only address, which RFC 6120 allows");
        assertFalse(SocketUtil.isFallbackDespiteSrvRecords(List.of(fallback), true, fallback), "an address from a DNS override is not the fallback");
    }

    @Test
    public void testNoAttemptAtAllIsAFailure()
    {
        // Execute system under test.
        final Tcp tcp = SocketUtil.summariseConnectionAttempts(List.of());

        // Verify results.
        assertEquals(Tcp.Outcome.NONE_ATTEMPTED, tcp.outcome(), "resolving addresses but attempting none must fail with its own outcome, which the page explains in translatable text");
    }

    @Test
    public void testEndpointsAreFormattedForReading()
    {
        assertEquals("127.0.0.1:5269", SocketUtil.endpoint(new InetSocketAddress(InetAddress.getLoopbackAddress(), 5269)), "an IPv4 endpoint must read as address and port");
        assertEquals("[0:0:0:0:0:0:0:1]:5269", SocketUtil.endpoint(new InetSocketAddress("::1", 5269)), "an IPv6 address must be bracketed so its port is readable");
    }

    // Recording during a real attempt

    @Test
    public void testRefusedPortIsRecorded() throws Exception
    {
        // Setup test fixture: a local port with nothing listening.
        final int closedPort;
        try (final ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = probe.getLocalPort();
        }
        DNSUtil.setDnsOverride(Map.of(DOMAIN, new SrvRecord("127.0.0.1", closedPort, false)));
        final AttemptRecorder recorder = new AttemptRecorder("example.com", DOMAIN);

        // Execute system under test.
        final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToXmppDomain(DOMAIN, 5269, recorder);

        // Verify results.
        assertNull(result, "no connection can be made to a closed port");
        assertEquals(Dns.Outcome.OVERRIDE, recorder.snapshot().getDns().orElseThrow().outcome(), "the override must be recorded as what DNS returned");
        assertEquals(Tcp.allFailed(List.of(AddressFailure.refused("127.0.0.1:" + closedPort))), recorder.snapshot().getTcp().orElseThrow(), "the refused connection must be recorded with its address");
    }

    @Test
    public void testConnectedAddressIsRecorded() throws Exception
    {
        // Setup test fixture: a local port that accepts connections.
        try (final ServerSocket listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            DNSUtil.setDnsOverride(Map.of(DOMAIN, new SrvRecord("127.0.0.1", listening.getLocalPort(), false)));
            final AttemptRecorder recorder = new AttemptRecorder("example.com", DOMAIN);

            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToXmppDomain(DOMAIN, 5269, recorder);

            // Verify results.
            try {
                assertNotNull(result, "a connection must be made to a listening port");
                assertEquals(Tcp.connected("127.0.0.1:" + listening.getLocalPort()), recorder.snapshot().getTcp().orElseThrow(), "the address finally used must be recorded");
            } finally {
                if (result != null) {
                    result.getKey().close();
                }
            }
        }
    }

    @Test
    public void testUnresolvableHostIsRecordedAsNoAddress() throws IOException
    {
        // Setup test fixture: a host name that cannot resolve (RFC 6761 reserves .invalid).
        DNSUtil.setDnsOverride(Map.of(DOMAIN, new SrvRecord("nonexistent.invalid", 5269, false)));
        final AttemptRecorder recorder = new AttemptRecorder("example.com", DOMAIN);

        // Execute system under test.
        final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToXmppDomain(DOMAIN, 5269, recorder);

        // Verify results.
        assertNull(result, "no connection can be made without an address");
        assertEquals(Dns.noAddress(), recorder.snapshot().getDns().orElseThrow(), "a domain whose hosts resolve to no address must be recorded as such");
        assertTrue(recorder.snapshot().getTcp().isEmpty(), "no TCP connection was attempted, so none must be recorded");
    }

    @Test
    public void testRecorderIsOptional()
    {
        // Setup test fixture.
        DNSUtil.setDnsOverride(Map.of(DOMAIN, new SrvRecord("nonexistent.invalid", 5269, false)));

        // Execute system under test, and verify results.
        assertNull(SocketUtil.createSocketToXmppDomain(DOMAIN, 5269), "callers without a recorder must keep working as before");
    }
}
