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
package org.jivesoftware.openfire.session;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that verify the functionality of {@link LocalClientSession}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LocalClientSessionTest
{
    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4NoRange() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Collections.emptySet();

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4NoMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.2.2");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4NoMatchWithWildcard() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("198.51.100.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4NoMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.2.2", "192.0.2.34");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4ExactMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.2.254");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4ExactMatchMultipleAddresses() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.2.2", "192.0.2.254");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4WildcardRangeFourthByteMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.2.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPvWildcardRangeFourthByteMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("198.51.100.2", "192.0.2.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4WildcardRangeThirdByteMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.*.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4WildcardRangeThirdByteMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.0.*.*", "198.*.*.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4WildcardRangeSecondByteMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.254").getAddress();
        final Set<String> ranges = Set.of("192.*.*.*");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4CidrRangeNoMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("198.51.100.2").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0/24");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4CidrRangeFirstMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.0").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0/24");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4CidrRangeMiddleMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.128").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0/24");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4CidrRangeMiddleMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.128").getAddress();
        final Set<String> ranges = Set.of("198.51.100.0/24", "192.0.2.0/24");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4CidrRangeLastMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.255").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0/24");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4DashRangeNoMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("198.51.100.2").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0-192.0.2.255");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4DashRangeFirstMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.0").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0-192.0.2.255");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4DashRangeMiddleMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.128").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0-192.0.2.255");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4DashRangeMiddleMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.128").getAddress();
        final Set<String> ranges = Set.of("198.51.100.0-198.51.100.255", "192.0.2.0-192.0.2.255");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv4DashRangeLastMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("192.0.2.255").getAddress();
        final Set<String> ranges = Set.of("192.0.2.0-192.0.2.255");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6NoRange() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Collections.emptySet();

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6NoMatchMultipleAddresses() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Set.of("3fff:400::1", "3fff:400::2");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6ExactMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Set.of("2001:db8::4");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6ExactMatchDifferentNotation() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8:0:0:0:0:0:ffff").getAddress();
        final Set<String> ranges = Set.of("2001:db8::ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6ExactMatchMultipleAddresses() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Set.of("3fff:400::1", "2001:db8::4");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6CidrRangeNoMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Set.of("3fff:400::/22");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6CidrRangeFirstMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff::").getAddress();
        final Set<String> ranges = Set.of("3fff::/22");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6CidrRangeMiddleMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff::").getAddress();
        final Set<String> ranges = Set.of("3fff::/22");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6CidrRangeMiddleMatchMultipleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff::").getAddress();
        final Set<String> ranges = Set.of("3fff:800::/22", "3fff::/22");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6CidrRangeLastMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff").getAddress();
        final Set<String> ranges = Set.of("3fff::/22");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6DashRangeNoMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("2001:db8::4").getAddress();
        final Set<String> ranges = Set.of("3fff:400::-3fff:7ff:ffff:ffff:ffff:ffff:ffff:ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertFalse(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6DashRangeFirstMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff::").getAddress();
        final Set<String> ranges = Set.of("3fff::-3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6DashRangeMiddleMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff::").getAddress();
        final Set<String> ranges = Set.of("3fff::-3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6DashRangeMiddleMatchMulitpleRanges() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff::").getAddress();
        final Set<String> ranges = Set.of("3fff:c00::-3fff:fff:ffff:ffff::", "3fff::-3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }

    @Test
    @Deprecated(since = "4.10.0", forRemoval = true) // Remove when the system under test gets removed.
    public void testIPv6DashRangeLastMatch() throws Exception
    {
        // Setup test fixture.
        final byte[] address = InetAddress.getByName("3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff").getAddress();
        final Set<String> ranges = Set.of("3fff::-3fff:3ff:ffff:ffff:ffff:ffff:ffff:ffff");

        // Execute system under test.
        final boolean result = LocalClientSession.isAddressInRange(address, ranges);

        // Verify results.
        assertTrue(result);
    }
}
