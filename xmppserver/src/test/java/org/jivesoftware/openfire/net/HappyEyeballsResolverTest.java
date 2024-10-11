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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link org.jivesoftware.openfire.net.DNSUtil}.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class HappyEyeballsResolverTest
{
    static final Duration RESOLUTION_DELAY = Duration.ofMillis(50); // If this test is giving flappy results, try increasing this value. If it needs to increase over 50ms, then the implementation is not efficient enough on the server to comply with the specification.

    // Host 'prio0' configuration.
    static final SrvRecord HOST_PRIO0 = new SrvRecord("prio0.example.org", 5269, false);
    static final InetAddress HOST_PRIO0_IPV4_ADDRESS;
    static final InetAddress HOST_PRIO0_IPV6_ADDRESS;

    static {
        try {
            HOST_PRIO0_IPV4_ADDRESS = InetAddress.getByAddress(HOST_PRIO0.getHostname(), new byte[] {(byte) 198, (byte) 51, (byte) 100, (byte) 1});
            HOST_PRIO0_IPV6_ADDRESS = InetAddress.getByAddress(HOST_PRIO0.getHostname(), new byte[] {(byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xB8, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 1 });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV4ONLY = () -> IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV6ONLY = () -> IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_BOTHFAMS = () -> IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS, HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV4ONLY_DELAYED_SHORT = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.dividedBy(2).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV4ONLY_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV6ONLY_DELAYED_SHORT = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.dividedBy(2).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_IPV6ONLY_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_BOTHFAMS_DELAYED_SHORT = () -> {
        try { Thread.sleep((RESOLUTION_DELAY.dividedBy(2)).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS, HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO0_BOTHFAMS_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(0, new InetAddress[]{HOST_PRIO0_IPV4_ADDRESS, HOST_PRIO0_IPV6_ADDRESS}, 5269, false);
    };

    // Host 'prio1' configuration.
    static final SrvRecord HOST_PRIO1 = new SrvRecord("prio1.example.org", 5269, false);
    static final InetAddress HOST_PRIO1_IPV4_ADDRESS;
    static final InetAddress HOST_PRIO1_IPV6_ADDRESS;

    static {
        try {
            HOST_PRIO1_IPV4_ADDRESS = InetAddress.getByAddress(HOST_PRIO1.getHostname(), new byte[] {(byte) 198, (byte) 51, (byte) 100, (byte) 2});
            HOST_PRIO1_IPV6_ADDRESS = InetAddress.getByAddress(HOST_PRIO1.getHostname(), new byte[] {(byte) 0x20, (byte) 0x01, (byte) 0x0d, (byte) 0xB8, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 2 });
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV4ONLY = () -> IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV6ONLY = () -> IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_BOTHFAMS = () -> IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS, HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV4ONLY_DELAYED_SHORT = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.dividedBy(2).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV4ONLY_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV6ONLY_DELAYED_SHORT = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.dividedBy(2).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_IPV6ONLY_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_BOTHFAMS_DELAYED_SHORT = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.dividedBy(2).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS, HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    };
    static final Supplier<Set<IndexedResolvedServiceAddress>> SOLVER_PRIO1_BOTHFAMS_DELAYED_LONG = () -> {
        try { Thread.sleep(RESOLUTION_DELAY.multipliedBy(3).toMillis()); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return IndexedResolvedServiceAddress.from(1, new InetAddress[]{HOST_PRIO1_IPV4_ADDRESS, HOST_PRIO1_IPV6_ADDRESS}, 5269, false);
    };

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenSingleHostTwoRecords(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS, 0);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
            final InetAddress expected = preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS;
            assertEquals(expected, result.getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenNoRecords(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0);
        final Supplier<Set<IndexedResolvedServiceAddress>> mockSolver = () -> { throw new RuntimeException("Exception thrown as part of unit testing.\"", new UnknownHostException("Nested exception thrown as part of unit testing.")); };

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(mockSolver, 0);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNull(result);
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenRecordsProvidedAfterResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS_DELAYED_LONG, 0);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNull(result);
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenOnlyIpv4Record(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_IPV4ONLY, 0);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenOnlyIpv6Record(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_IPV6ONLY, 0);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenTwoHostsFourRecords(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS, 0);
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS, 1);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
            final InetAddress expected = preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS;
            assertEquals(expected, result.generateSocketAddress().getAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenPreferredHostProvidedFirst(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS, 0);
            systemUnderTest.solve(SOLVER_PRIO1_BOTHFAMS_DELAYED_SHORT, 1);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
            final InetAddress expected = preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS;
            assertEquals(expected, result.generateSocketAddress().getAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenPreferredHostProvidedSecondButWithinResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS_DELAYED_SHORT, 0);
            systemUnderTest.solve(SOLVER_PRIO1_BOTHFAMS, 1);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
            final InetAddress expected = preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS;
            assertEquals(expected, result.generateSocketAddress().getAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testFirstResultWhenPreferredHostProvidedSecondAfterResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS_DELAYED_LONG, 0);
            systemUnderTest.solve(SOLVER_PRIO1_BOTHFAMS, 1);
            final ResolvedServiceAddress result = systemUnderTest.getNext();

            // Verify results.
            assertNotNull(result);
            final InetAddress expected = preferIpv4 ? HOST_PRIO1_IPV4_ADDRESS : HOST_PRIO1_IPV6_ADDRESS;
            assertEquals(expected, result.generateSocketAddress().getAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAllResults(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS, 0);
            systemUnderTest.solve(SOLVER_PRIO1_BOTHFAMS, 1);

            final List<ResolvedServiceAddress> results = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                final ResolvedServiceAddress nextResult = systemUnderTest.getNext();
                if (nextResult != null) {
                    results.add(nextResult);
                }
                if (results.size() == 4) {
                    break;
                }
            }

            // Verify results.
            assertEquals(4, results.size());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS, results.get(0).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV6_ADDRESS : HOST_PRIO0_IPV4_ADDRESS, results.get(1).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV4_ADDRESS : HOST_PRIO1_IPV6_ADDRESS, results.get(2).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV6_ADDRESS : HOST_PRIO1_IPV4_ADDRESS, results.get(3).getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAllResultsWhenPreferredHostProvidedSecondButWithinResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS_DELAYED_SHORT, 0);
            systemUnderTest.solve(SOLVER_PRIO1_BOTHFAMS, 1);

            final List<ResolvedServiceAddress> results = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                final ResolvedServiceAddress nextResult = systemUnderTest.getNext();
                if (nextResult != null) {
                    results.add(nextResult);
                }
                if (results.size() == 4) {
                    break;
                }
            }

            // Verify results.
            assertEquals(4, results.size());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS, results.get(0).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV6_ADDRESS : HOST_PRIO0_IPV4_ADDRESS, results.get(1).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV4_ADDRESS : HOST_PRIO1_IPV6_ADDRESS, results.get(2).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV6_ADDRESS : HOST_PRIO1_IPV4_ADDRESS, results.get(3).getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAllResultsWhenPreferredFamilyProvidedSecondButWithinResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(preferIpv4 ? SOLVER_PRIO0_IPV4ONLY_DELAYED_SHORT : SOLVER_PRIO0_IPV6ONLY_DELAYED_SHORT, 0);
            systemUnderTest.solve(preferIpv4 ? SOLVER_PRIO1_IPV6ONLY : SOLVER_PRIO1_IPV4ONLY, 1);

            final List<ResolvedServiceAddress> results = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                final ResolvedServiceAddress nextResult = systemUnderTest.getNext();
                if (nextResult != null) {
                    results.add(nextResult);
                }
                if (results.size() == 2) {
                    break;
                }
            }

            // Verify results.
            assertEquals(2, results.size());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS, results.get(0).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV6_ADDRESS : HOST_PRIO1_IPV4_ADDRESS, results.get(1).getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAllResultsWhenPreferredFamilyProvidedSecondAfterResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(preferIpv4 ? SOLVER_PRIO0_IPV4ONLY_DELAYED_LONG : SOLVER_PRIO0_IPV6ONLY_DELAYED_LONG, 0);
            systemUnderTest.solve(preferIpv4 ? SOLVER_PRIO1_IPV6ONLY : SOLVER_PRIO1_IPV4ONLY, 1);

            final List<ResolvedServiceAddress> results = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                final ResolvedServiceAddress nextResult = systemUnderTest.getNext();
                if (nextResult != null) {
                    results.add(nextResult);
                }
                if (results.size() == 2) {
                    break;
                }
            }

            // Verify results.
            assertEquals(2, results.size());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV6_ADDRESS : HOST_PRIO1_IPV4_ADDRESS, results.get(0).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS, results.get(1).getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAllResultsFamilyInterleavedWhenPreferredFamilyProvidedSecondAfterResolutionDelay(final boolean preferIpv4) throws Exception
    {
        // Setup test fixture.
        final List<SrvRecord> input = List.of(HOST_PRIO0, HOST_PRIO1);

        // Execute system under test.
        final HappyEyeballsResolver systemUnderTest = new HappyEyeballsResolver(input, preferIpv4, RESOLUTION_DELAY);
        try {
            systemUnderTest.solve(SOLVER_PRIO0_BOTHFAMS_DELAYED_LONG, 0);
            systemUnderTest.solve(preferIpv4 ? SOLVER_PRIO1_IPV6ONLY : SOLVER_PRIO1_IPV4ONLY, 1);

            final List<ResolvedServiceAddress> results = new LinkedList<>();
            for (int i = 0; i < 10; i++) {
                final ResolvedServiceAddress nextResult = systemUnderTest.getNext();
                if (nextResult != null) {
                    results.add(nextResult);
                }
                if (results.size() == 3) {
                    break;
                }
            }

            // Verify results.
            assertEquals(3, results.size());
            assertEquals(preferIpv4 ? HOST_PRIO1_IPV6_ADDRESS : HOST_PRIO1_IPV4_ADDRESS, results.get(0).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV4_ADDRESS : HOST_PRIO0_IPV6_ADDRESS, results.get(1).getInetAddress());
            assertEquals(preferIpv4 ? HOST_PRIO0_IPV6_ADDRESS : HOST_PRIO0_IPV4_ADDRESS, results.get(2).getInetAddress());
        } finally {
            // Clean up test fixture.
            systemUnderTest.shutdownNow();
        }
    }
}
