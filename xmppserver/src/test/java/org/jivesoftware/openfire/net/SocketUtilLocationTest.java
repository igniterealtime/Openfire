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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the connection to a location that the peer of a stream named for reconnecting to it (XEP-0198 § 3),
 * as made by {@link SocketUtil#createSocketToLocation(String, String, int, Duration)}.
 */
public class SocketUtilLocationTest
{
    private Map<String, SrvRecord> originalDnsOverride;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
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

    /**
     * Verifies that a location in the form of RFC 6120 § 4.9.3.19 is parsed into its host and its port, if any: a domain
     * name or IP address, optionally followed by a colon and a port, in which an IPv6 address is enclosed in brackets.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "node2.example.com, node2.example.com, ",
        "node2.example.com:5270, node2.example.com, 5270",
        "' node2.example.com:5270 ', node2.example.com, 5270",
        "192.0.2.1, 192.0.2.1, ",
        "192.0.2.1:5270, 192.0.2.1, 5270",
        "[2001:db8::1], 2001:db8::1, ",
        "[2001:db8::1]:5270, 2001:db8::1, 5270",
    })
    public void validLocationIsParsed(final String location, final String expectedHost, final Integer expectedPort)
    {
        // Setup test fixture.

        // Execute system under test.
        final SocketUtil.Location result = SocketUtil.parseLocation(location);

        // Verify result.
        assertNotNull(result, "Location '" + location + "' must be parsed.");
        assertEquals(expectedHost, result.host(), "The host of location '" + location + "' must be parsed.");
        assertEquals(expectedPort, result.port(), "The port of location '" + location + "' must be parsed.");
    }

    /**
     * Verifies that a location that is not in the form of RFC 6120 § 4.9.3.19 is refused.
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "2001:db8::1", "2001:db8::1:5270", "[2001:db8::1", "[2001:db8::1]5270", "[node2.example.com]:5270", "[192.0.2.1]", ":5270", "node2.example.com:", "node2.example.com:abc", "node2.example.com:0", "node2.example.com:65536", "node 2.example.com", "node2@example.com:5270", "[2001:db8::g]", "[node2:example]"})
    public void invalidLocationIsRefused(final String location)
    {
        // Setup test fixture.

        // Execute system under test.
        final SocketUtil.Location result = SocketUtil.parseLocation(location);

        // Verify result.
        assertNull(result, "Location '" + location + "' must be refused, but was parsed as: " + result);
    }

    /**
     * Verifies that a connection is made to a location that names an IP address and port.
     */
    @Test
    public void connectsToLocation() throws Exception
    {
        // Setup test fixture.
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            final String location = server.getInetAddress().getHostAddress() + ":" + server.getLocalPort();

            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", location, 5269, Duration.ofSeconds(5));

            // Verify result.
            assertNotNull(result, "A connection must be made to location " + location);
            try (final Socket socket = result.getKey()) {
                assertEquals(server.getLocalPort(), socket.getPort(), "The connection must be made to the port of the location.");
                assertFalse(result.getValue(), "A connection to a location must not use direct TLS.");
            }
        }
    }

    /**
     * Verifies that no connection is made to a location when a DNS override applies to the XMPP domain of the peer, as
     * the override then decides where that domain is reached.
     */
    @Test
    public void noConnectionToLocationWhenDnsOverrideApplies() throws Exception
    {
        // Setup test fixture.
        DNSUtil.setDnsOverride(Map.of("example.org", new SrvRecord("xmpp.example.org", 5269, false)));
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            final String location = server.getInetAddress().getHostAddress() + ":" + server.getLocalPort();

            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", location, 5269, Duration.ofSeconds(5));

            // Verify result.
            assertNull(result, "No connection must be made to a location when a DNS override applies to the domain.");
        }
    }

    /**
     * Verifies that a connection to a location that names an IP address without a port is made on the provided default
     * port.
     */
    @Test
    public void connectsToLocationWithoutPortOnDefaultPort() throws Exception
    {
        // Setup test fixture.
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "127.0.0.1", server.getLocalPort(), Duration.ofSeconds(5));

            // Verify result.
            assertNotNull(result, "A connection must be made to a location that names an IP address without a port.");
            try (final Socket socket = result.getKey()) {
                assertEquals(server.getLocalPort(), socket.getPort(), "A location without a port must be connected to on the default port.");
            }
        }
    }

    /**
     * Verifies that a connection to a location that names a domain name and a port is made to that port, rather than to
     * a port that resolving the domain name as an XMPP domain would give.
     */
    @Test
    public void connectsToLocationWithDomainNameAndPort() throws Exception
    {
        // Setup test fixture.
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("localhost")))
        {
            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "localhost:" + server.getLocalPort(), 5269, Duration.ofSeconds(5));

            // Verify result.
            assertNotNull(result, "A connection must be made to a location that names a domain name and a port.");
            try (final Socket socket = result.getKey()) {
                assertEquals(server.getLocalPort(), socket.getPort(), "The connection must be made to the port of the location.");
            }
        }
    }

    /**
     * Verifies that a location that names a domain name without a port is resolved as an XMPP domain (RFC 6120 § 3.2),
     * which, for this test, a DNS override for that domain name answers.
     */
    @Test
    public void locationWithDomainNameWithoutPortIsResolvedAsXmppDomain() throws Exception
    {
        // Setup test fixture.
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            DNSUtil.setDnsOverride(Map.of("node2.example.org", new SrvRecord("127.0.0.1", server.getLocalPort(), false)));

            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "node2.example.org", 5269, Duration.ofSeconds(5));

            // Verify result.
            assertNotNull(result, "A connection must be made to the host that resolving the domain name of the location gives.");
            try (final Socket socket = result.getKey()) {
                assertEquals(server.getLocalPort(), socket.getPort(), "The connection must be made to the port that resolving the domain name of the location gives.");
            }
        }
    }

    /**
     * Verifies that a DNS override for the domain name of a location applies also when the location names a port, as it
     * does when the location names none.
     */
    @Test
    public void dnsOverrideForLocationWithPortIsUsed() throws Exception
    {
        // Setup test fixture.
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            DNSUtil.setDnsOverride(Map.of("node2.example.org", new SrvRecord("127.0.0.1", server.getLocalPort(), false)));

            // Execute system under test.
            final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "node2.example.org:5270", 5269, Duration.ofSeconds(5));

            // Verify result.
            assertNotNull(result, "A connection must be made to the host that the DNS override for the domain name of the location names.");
            try (final Socket socket = result.getKey()) {
                assertEquals(server.getLocalPort(), socket.getPort(), "The connection must be made to the port that the DNS override for the domain name of the location names.");
            }
        }
    }

    /**
     * Verifies that no connection is made to a location at which nothing accepts connections, so that the caller can
     * fall back to the hosts of the XMPP domain.
     */
    @Test
    public void noConnectionToUnreachableLocation() throws Exception
    {
        // Setup test fixture.
        final int closedPort;
        try (final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = server.getLocalPort();
        }

        // Execute system under test.
        final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "127.0.0.1:" + closedPort, 5269, Duration.ofSeconds(5));

        // Verify result.
        assertNull(result, "No connection must be made to a location at which nothing accepts connections.");
    }

    /**
     * Verifies that no connection is made to a location that is not in the form of RFC 6120 § 4.9.3.19.
     */
    @Test
    public void noConnectionToInvalidLocation()
    {
        // Setup test fixture.

        // Execute system under test.
        final Map.Entry<Socket, Boolean> result = SocketUtil.createSocketToLocation("example.org", "2001:db8::1", 5269, Duration.ofSeconds(5));

        // Verify result.
        assertNull(result, "No connection must be made to a location that is not valid.");
    }
}
