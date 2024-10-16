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
package org.jivesoftware.util;

import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to validate the functionality of @{link {@link IpUtils}}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class IpUtilsTest
{
    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set, Set)} does not find the provided address in empty
     * sets of addresses and ranges.
     */
    @Test
    public void testIsAddressInAnyOfIpv6SplitEmptySets()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:0");
        final Set<Ipv6> addresses = new HashSet<>();
        final Set<Ipv6Range> ranges = new HashSet<>();

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set)} does not find the provided address in empty
     * sets of addresses and ranges.
     */
    @Test
    public void testIsAddressInAnyOfIpv6NonSplitEmptySets()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:0");
        final Set<String> addressesAndRanges = new HashSet<>();

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set, Set)} does not find the provided address in non-empty
     * sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfIpv6SplitNoMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:0");
        final Set<Ipv6> addresses = Set.of(Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1"),Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:2"));
        final Set<Ipv6Range> ranges = Set.of(Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:3").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:4"), Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:5").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set)} does not find the provided address in non-empty
     * sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfIpv6NonSplitNoMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:0");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv6SplitAddressMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:2");
        final Set<Ipv6> addresses = Set.of(Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1"),Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:2"));
        final Set<Ipv6Range> ranges = Set.of(Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:3").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:4"), Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:5").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv6NonSplitAddressMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:2");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv6SplitRangeMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:5");
        final Set<Ipv6> addresses = Set.of(Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1"),Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:2"));
        final Set<Ipv6Range> ranges = Set.of(Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:3").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:4"), Ipv6Range.from("91ed:ffff:8948:c0a3:1:dc9e:bed9:5").to("91ed:ffff:8948:c0a3:1:dc9e:bed9:6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv6, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv6NonSplitRangeMatch()
    {
        // Setup test fixture.
        final Ipv6 needle = Ipv6.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:5");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set, Set)} does not find the provided address in empty
     * sets of addresses and ranges.
     */
    @Test
    public void testIsAddressInAnyOfIpv4SplitEmptySets()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.5");
        final Set<Ipv4> addresses = new HashSet<>();
        final Set<Ipv4Range> ranges = new HashSet<>();

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set)} does not find the provided address in empty
     * sets of addresses and ranges.
     */
    @Test
    public void testIsAddressInAnyOfIpv4NonSplitEmptySets()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.5");
        final Set<String> addressesAndRanges = new HashSet<>();

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set, Set)} does not find the provided address in non-empty
     * sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfIpv4SplitNoMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.0");
        final Set<Ipv4> addresses = Set.of(Ipv4.of("198.51.100.1"),Ipv4.of("198.51.100.2"));
        final Set<Ipv4Range> ranges = Set.of(Ipv4Range.from("198.51.100.3").to("198.51.100.4"), Ipv4Range.from("198.51.100.5").to("198.51.100.6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set)} does not find the provided address in non-empty
     * sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfIpv4NonSplitNoMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.0");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv4SplitAddressMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.2");
        final Set<Ipv4> addresses = Set.of(Ipv4.of("198.51.100.1"),Ipv4.of("198.51.100.2"));
        final Set<Ipv4Range> ranges = Set.of(Ipv4Range.from("198.51.100.3").to("198.51.100.4"), Ipv4Range.from("198.51.100.5").to("198.51.100.6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv4NonSplitAddressMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.2");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv4SplitRangeMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.5");
        final Set<Ipv4> addresses = Set.of(Ipv4.of("198.51.100.1"),Ipv4.of("198.51.100.2"));
        final Set<Ipv4Range> ranges = Set.of(Ipv4Range.from("198.51.100.3").to("198.51.100.4"), Ipv4Range.from("198.51.100.5").to("198.51.100.6"));

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addresses, ranges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfIpv4NonSplitRangeMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.5");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(Ipv4, Set)} correctly find the provided address in non-empty
     * sets of addresses and ranges, when one of the ranges contains the provided address. This test uses the
     * Openfire-proprietary 'wildcard' notation for the matching range.
     */
    @Test
    public void testIsAddressInAnyOfIpv4NonSplitWildcardRangeMatch()
    {
        // Setup test fixture.
        final Ipv4 needle = Ipv4.of("198.51.100.5");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.*");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv6NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "91ed:ffff:8948:c0a3:1:dc9e:bed9:0";
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv6AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "91ed:ffff:8948:c0a3:1:dc9e:bed9:2";
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv6RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "91ed:ffff:8948:c0a3:1:dc9e:bed9:5";
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv4NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "198.51.100.0";
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv4AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "198.51.100.2";
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(String, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfStringIpv4RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final String needle = "198.51.100.5";
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv6NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:0");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv6AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:2");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv6RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:5");
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv4NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("198.51.100.0");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv4AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("198.51.100.2");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(java.net.InetAddress, Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfInetIpv4RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final InetAddress needle = InetAddress.getByName("198.51.100.5");
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv6NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:0").getAddress();
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv6AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:2").getAddress();
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv6RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("91ed:ffff:8948:c0a3:1:dc9e:bed9:5").getAddress();
        final Set<String> addressesAndRanges = Set.of("91ed:ffff:8948:c0a3:1:dc9e:bed9:1", "91ed:ffff:8948:c0a3:1:dc9e:bed9:2", "91ed:ffff:8948:c0a3:1:dc9e:bed9:3-91ed:ffff:8948:c0a3:1:dc9e:bed9:4", "91ed:ffff:8948:c0a3:1:dc9e:bed9:5-91ed:ffff:8948:c0a3:1:dc9e:bed9:6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} does not find the provided address in
     * non-empty sets of addresses and ranges that do not contain a match.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv4NoMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("198.51.100.0").getAddress();
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the addresses is a match for the provided address.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv4AddressMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("198.51.100.2").getAddress();
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isAddressInAnyOf(byte[], Set)} correctly find the provided address in
     * non-empty sets of addresses and ranges, when one of the ranges contains the provided address.
     */
    @Test
    public void testIsAddressInAnyOfByteArrayIpv4RangeMatch() throws UnknownHostException
    {
        // Setup test fixture.
        final byte[] needle = InetAddress.getByName("198.51.100.5").getAddress();
        final Set<String> addressesAndRanges = Set.of("198.51.100.1", "198.51.100.2", "198.51.100.3-198.51.100.4", "198.51.100.5-198.51.100.6");

        // Execute system under test.
        final boolean result = IpUtils.isAddressInAnyOf(needle, addressesAndRanges);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#removeBracketsFromIpv6Address(String)} returns the unmodified input value when that
     * is an empty string.
     */
    @Test
    public void testRemoveBracketsFromIpv6AddressEmpty() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final String result = IpUtils.removeBracketsFromIpv6Address(input);

        // Verify results.
        assertEquals(input, result);
    }

    /**
     * Asserts that {@link IpUtils#removeBracketsFromIpv6Address(String)} returns the unmodified input value when that
     * is an IPv6 input value not surrounded by brackets.
     */
    @Test
    public void testRemoveBracketsFromIpv6AddressNoBrackets() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final String result = IpUtils.removeBracketsFromIpv6Address(input);

        // Verify results.
        assertEquals(input, result);
    }

    /**
     * Asserts that {@link IpUtils#removeBracketsFromIpv6Address(String)} returns the IPv6 input value without brackets.
     */
    @Test
    public void testRemoveBracketsFromIpv6AddressWithBrackets() throws Exception
    {
        // Setup test fixture.
        final String input = "[2001:db8:ffff:ffff:ffff:ffff:ffff:ffff]";

        // Execute system under test.
        final String result = IpUtils.removeBracketsFromIpv6Address(input);

        // Verify results.
        assertEquals("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff", result);
    }

    /**
     * Asserts that {@link IpUtils#removeBracketsFromIpv6Address(String)} returns the unmodified input value when that
     * is surrounded by brackets, but is not an IPv6 value.
     */
    @Test
    public void testRemoveBracketsFromIpv6AddressNoIpv6() throws Exception
    {
        // Setup test fixture.
        final String input = "[123]";

        // Execute system under test.
        final String result = IpUtils.removeBracketsFromIpv6Address(input);

        // Verify results.
        assertEquals(input, result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} does not modify an empty string.
     */
    @Test
    public void testConvertEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals("", result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} does not affect a string that does
     * not contain an IP address range.
     */
    @Test
    public void testConvertNonIpString() throws Exception
    {
        // Setup test fixture.
        final String input = "This is not an IP address range and should be unaffected.";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals("This is not an IP address range and should be unaffected.", result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} does not affect a string that is an
     * IP address that is not a range.
     */
    @Test
    public void testConvertAddressNotRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals("198.51.100.4", result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} does not affect a string that already
     * is a CIDR range.
     */
    @Test
    public void testConvertRangeAlreadyCidr() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals("198.51.100.0/24", result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} replaces a wildcard-based range
     * with a CIDR notation.
     */
    @Test
    public void testConvertRangeWildcard() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals("198.51.100.0/24", result);
    }

    /**
     * Asserts that {@link IpUtils#convertIpv4WildcardRangeToCidrNotation(String)} replaces a wildcard-based range
     * with a CIDR notation, if the input is surrounded by whitespace characters (not trimmed).
     */
    @Test
    public void testConvertRangeWildcardWhitespace() throws Exception
    {
        // Setup test fixture.
        final String input = " 198.51.100.*\n";

        // Execute system under test.
        final String result = IpUtils.convertIpv4WildcardRangeToCidrNotation(input);

        // Verify results.
        assertEquals(" 198.51.100.0/24\n", result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a null value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4Null() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify an empty string value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4EmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a 'foobar' text value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4Foobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify an IPv4 CIDR range value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4CidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify an IPv4 dash range value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4DashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify an IPv4 Wildcard range value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4WildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} correctly identifies a value as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a valid IPv6 address as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4WrongFamily() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a valid IPv6 address (using the abbreviated
     * notations) as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4WrongFamilyAbbreviated() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a valid IPv6 address range (in the CIDR notation) as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4WrongFamilyCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a valid IPv6 address range (in the dash notation) as a valid IPv4 address.
     */
    @Test
    public void testIsValidIpv4WrongFamilyDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify a null value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeNull() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify an empty string value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify a 'foobar' text value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeFoobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} correctly identifies an IPv4 CIDR range value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} correctly identifies an IPv4 dash-range value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} correctly identifies an IPv4 Wildcard range value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeWildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify an IPv4 address value as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeAddress() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify a valid IPv6 address as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeWrongFamily() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4(String)} does not identify a valid IPv6 address (using the abbreviated
     * notations) as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeWrongFamilyAbbreviated() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify a valid IPv6 address range (in the CIDR notation) as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeWrongFamilyCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv4Range(String)} does not identify a valid IPv6 address range (in the dash notation) as a valid IPv4 address range.
     */
    @Test
    public void testIsValidIpv4RangeWrongFamilyDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv4Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify a null value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6Null() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify an empty string value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6EmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify a 'foobar' text value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6Foobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify an IPv6 CIDR range value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6CidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify an IPv6 dash range value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6DashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} correctly identifies a value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} correctly identifies a valid IPv6 address (using the abbreviated
     * notations) as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6WrongFamilyAbbreviated() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify a valid IPv4 address as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6WrongFamily() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify a valid IPv4 address range (in the CIDR notation) as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6WrongFamilyCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify a valid IPv4 address range (in the dash notation) as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6WrongFamilyDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6(String)} does not identify an IPv4 Wildcard range value as a valid IPv6 address.
     */
    @Test
    public void testIsValidIpv6WrongFamilyWildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a null value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeNull() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify an empty string value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a 'foobar' text value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeFoobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} correctly identifies an IPv6 CIDR range value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} correctly identifies an IPv6 dash-range value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify an IPv6 address value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeAddress() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a valid IPv6 address (using the abbreviated
     * notations) as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeAddressAbbreviated() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a valid IPv4 address as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeWrongFamily() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a valid IPv4 address range (in the CIDR notation) as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeWrongFamilyCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} does not identify a valid IPv4 address range (in the dash notation) as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeWrongFamilyDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpv6Range(String)} correctly identifies an IPv4 Wildcard range value as a valid IPv6 address range.
     */
    @Test
    public void testIsValidIpv6RangeWrongFamilyWildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpv6Range(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify a null value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressNull() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an empty string value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify a 'foobar' text value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressFoobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an IPv4 CIDR range value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressIpv4CidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an IPv4 dash range value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressIpv4DashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an IPv4 Wildcard range value as a valid IP address.
     */
    @Test
    public void testIsValidIpAddressIpv4WildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} correctly identifies an IPv4 address value as a valid IP address.
     */
    @Test
    public void testisValidIpAddressIpv4() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an IPv6 CIDR range value as a valid address.
     */
    @Test
    public void testIsValidIpAddressCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} does not identify an IPv6 dash range value as a valid address.
     */
    @Test
    public void testIsValidIpAddressDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddress(String)} correctly identifies an IPv6 address value as a valid address.
     */
    @Test
    public void testIsValidIpAddressIpv6() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddress(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} does not identify a null value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeNull() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} does not identify an empty string value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} does not identify a 'foobar' text value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeFoobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} correctly identifies an IPv4 CIDR range value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeIpv4CidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} correctly identifies an IPv4 dash range value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeIpv4DashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} correctly identifies an IPv4 Wildcard range value as a valid IP address range.
     */
    @Test
    public void testIsValidIpRangeIpv4WildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} does not identify an IPv4 address value as a valid IP address range.
     */
    @Test
    public void testisValidIpRangeIpv4() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} correctly identifies an IPv6 CIDR range value as a valid address range.
     */
    @Test
    public void testIsValidIpRangeCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} correctly identifies an IPv6 dash range value as a valid address range.
     */
    @Test
    public void testIsValidIpRangeDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpRange(String)} does not identifies an IPv6 address value as a valid address range.
     */
    @Test
    public void testIsValidIpRangeIpv6() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} does not identify a null value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeNull() throws Exception
    {
        // Setup test fixture.
        final String input = null;

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} does not identify an empty string value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeEmptyString() throws Exception
    {
        // Setup test fixture.
        final String input = "";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} does not identify a 'foobar' text value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeFoobar() throws Exception
    {
        // Setup test fixture.
        final String input = "foobar";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv4 CIDR range value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeIpv4CidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0/24";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv4 dash range value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeIpv4DashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.0-198.51.100.255";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv4 Wildcard range value as a valid IP address or IP address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeIpv4WildcardRange() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.*";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv4 address value as a valid IP address or IP address range.
     */
    @Test
    public void testisValidIpAddressOrRangeIpv4() throws Exception
    {
        // Setup test fixture.
        final String input = "198.51.100.4";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv6 CIDR range value as a valid address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeCidrRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::/32";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv6 dash range value as a valid address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeDashRange() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8::-2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that {@link IpUtils#isValidIpAddressOrRange(String)} correctly identifies an IPv6 address value as a valid address range.
     */
    @Test
    public void testIsValidIpAddressOrRangeIpv6() throws Exception
    {
        // Setup test fixture.
        final String input = "2001:db8:ffff:ffff:ffff:ffff:ffff:ffff";

        // Execute system under test.
        final boolean result = IpUtils.isValidIpAddressOrRange(input);

        // Verify results.
        assertTrue(result);
    }
}
