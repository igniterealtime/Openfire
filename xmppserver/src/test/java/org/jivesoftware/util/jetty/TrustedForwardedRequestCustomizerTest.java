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
package org.jivesoftware.util.jetty;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.Test;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrustedForwardedRequestCustomizer}.
 */
public class TrustedForwardedRequestCustomizerTest
{
    /**
     * Verifies that null remote addresses are never trusted.
     */
    @Test
    public void isTrustedReturnsFalseForNullAddress()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("192.0.2.10"));

        // Execute system under test
        final boolean result = customizer.isTrusted(null);

        // Verify result
        assertFalse(result, "A null remote address must be treated as untrusted to prevent accidental trust.");
    }

    /**
     * Verifies that matching trusted proxies are recognized.
     */
    @Test
    public void isTrustedReturnsTrueForMatchingProxy()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("192.0.2.10"));
        final SocketAddress remoteAddress = new InetSocketAddress("192.0.2.10", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "A proxy IP in the trusted list must be accepted as trusted.");
    }

    /**
     * Verifies that IPv4 CIDR ranges are accepted in trusted proxy configuration.
     */
    @Test
    public void isTrustedReturnsTrueForIpv4CidrRange()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("198.51.100.0/24"));
        final SocketAddress remoteAddress = new InetSocketAddress("198.51.100.42", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "An IPv4 address inside a trusted CIDR range must be treated as trusted.");
    }

    /**
     * Verifies that IPv4 dash ranges are accepted in trusted proxy configuration.
     */
    @Test
    public void isTrustedReturnsTrueForIpv4DashRange()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("198.51.100.10-198.51.100.20"));
        final SocketAddress remoteAddress = new InetSocketAddress("198.51.100.15", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "An IPv4 address inside a trusted dash range must be treated as trusted.");
    }

    /**
     * Verifies that IPv4 wildcard ranges are accepted in trusted proxy configuration.
     */
    @Test
    public void isTrustedReturnsTrueForIpv4WildcardRange()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("198.51.100.*"));
        final SocketAddress remoteAddress = new InetSocketAddress("198.51.100.200", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "An IPv4 address inside a trusted wildcard range must be treated as trusted.");
    }

    /**
     * Verifies that IPv6 abbreviated and expanded notations are treated as equivalent.
     */
    @Test
    public void isTrustedReturnsTrueForIpv6NotationVariant()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("2001:db8:0:0:0:0:0:1"));
        final SocketAddress remoteAddress = new InetSocketAddress("2001:db8::1", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "Equivalent expanded and abbreviated IPv6 addresses must match trusted proxy entries.");
    }

    /**
     * Verifies that IPv6 CIDR ranges are accepted in trusted proxy configuration.
     */
    @Test
    public void isTrustedReturnsTrueForIpv6CidrRange()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("2001:db8::/64"));
        final SocketAddress remoteAddress = new InetSocketAddress("2001:db8::beef", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "An IPv6 address inside a trusted CIDR range must be treated as trusted.");
    }

    /**
     * Verifies that IPv6 dash ranges are accepted in trusted proxy configuration.
     */
    @Test
    public void isTrustedReturnsTrueForIpv6DashRange()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("2001:db8::10-2001:db8::20"));
        final SocketAddress remoteAddress = new InetSocketAddress("2001:db8::18", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "An IPv6 address inside a trusted dash range must be treated as trusted.");
    }

    /**
     * Verifies that addresses outside a configured range are not treated as trusted.
     */
    @Test
    public void isTrustedReturnsFalseWhenOutsideConfiguredRanges()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(
            new ForwardedRequestCustomizer(),
            Set.of("198.51.100.0/24", "2001:db8::/64")
        );

        // Execute system under test
        final boolean ipv4Result = customizer.isTrusted(new InetSocketAddress("203.0.113.1", 5222));
        final boolean ipv6Result = customizer.isTrusted(new InetSocketAddress("2001:db8:1::1", 5222));

        // Verify result
        assertFalse(ipv4Result, "An IPv4 address outside all configured ranges must not be trusted.");
        assertFalse(ipv6Result, "An IPv6 address outside all configured ranges must not be trusted.");
    }

    /**
     * Verifies that unresolved socket addresses are rejected.
     */
    @Test
    public void isTrustedReturnsFalseForUnresolvedAddress()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("192.0.2.10"));
        final SocketAddress remoteAddress = InetSocketAddress.createUnresolved("proxy.example.org", 5222);

        // Execute system under test
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertFalse(result, "An unresolved remote address must be untrusted because no IP can be validated.");
    }

    /**
     * Verifies that forwarded headers are detected when present.
     */
    @Test
    public void hasForwardedHeadersReturnsTrueWhenHeaderPresent()
    {
        // Setup test fixture
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("192.0.2.10"));
        final Request request = mock(Request.class);
        final HttpFields headers = mock(HttpFields.class);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.contains(HttpHeader.X_FORWARDED_FOR)).thenReturn(true);

        // Execute system under test
        final boolean result = customizer.hasForwardedHeaders(request);

        // Verify result
        assertTrue(result, "Requests with an X-Forwarded-For header must be detected as forwarded.");
    }

    /**
     * Verifies that customization delegates when the source is trusted.
     */
    @Test
    public void customizeDelegatesForTrustedSource()
    {
        // Setup test fixture
        final ForwardedRequestCustomizer delegate = mock(ForwardedRequestCustomizer.class);
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(delegate, Set.of("192.0.2.10"));
        final Request request = mock(Request.class);
        final Request wrappedRequest = mock(Request.class);
        final ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
        final HttpFields headers = mock(HttpFields.class);
        final HttpFields.Mutable responseHeaders = mock(HttpFields.Mutable.class);
        when(request.getConnectionMetaData()).thenReturn(connectionMetaData);
        when(connectionMetaData.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("192.0.2.10", 5222));
        when(request.getHeaders()).thenReturn(headers);
        when(delegate.customize(request, responseHeaders)).thenReturn(wrappedRequest);

        // Execute system under test
        final Request result = customizer.customize(request, responseHeaders);

        // Verify result
        assertSame(wrappedRequest, result, "Customizer must propagate the wrapped request returned by the trusted delegate.");
        verify(delegate).customize(request, responseHeaders);
    }

    /**
     * Verifies that a link-local IPv6 address carrying a zone/scope ID (e.g. "fe80::1%eth0") does not cause an
     * exception and is still evaluated correctly against the trusted proxy list.
     *
     * {@link java.net.InetAddress#getHostAddress()} appends the scope ID, so any code path that converts an
     * {@link java.net.Inet6Address} to a String before matching must strip it first.
     */
    @Test
    public void isTrustedHandlesScopedIpv6AddressWithoutException() throws Exception
    {
        // Setup test fixture: build an Inet6Address that carries a numeric scope ID.
        // byte[] for 2001:db8::1
        final byte[] addr = new byte[]{
            0x20, 0x01, 0x0d, (byte)0xb8, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1
        };
        // scope_id 1 – produces getHostAddress() == "2001:db8::1%1" on most JVMs
        final Inet6Address scopedAddress = Inet6Address.getByAddress(null, addr, 1);
        final InetSocketAddress remoteAddress = new InetSocketAddress(scopedAddress, 5222);
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(new ForwardedRequestCustomizer(), Set.of("2001:db8::/32"));

        // Execute system under test – must not throw IllegalArgumentException
        final boolean result = customizer.isTrusted(remoteAddress);

        // Verify result
        assertTrue(result, "A scoped IPv6 address whose bare address falls inside a trusted range must be trusted.");
    }

    /**
     * Verifies that customization does not delegate for untrusted sources.
     */
    @Test
    public void customizeSkipsDelegateForUntrustedSource()
    {
        // Setup test fixture
        final AtomicBoolean delegateInvoked = new AtomicBoolean(false);
        final Request wrappedRequest = mock(Request.class);
        final ForwardedRequestCustomizer delegate = new ForwardedRequestCustomizer()
        {
            @Override
            public Request customize(final Request request, final HttpFields.Mutable responseHeaders)
            {
                delegateInvoked.set(true);
                return wrappedRequest;
            }
        };
        final TrustedForwardedRequestCustomizer customizer = new TrustedForwardedRequestCustomizer(delegate, Set.of("192.0.2.10"));
        final Request request = mock(Request.class);
        final ConnectionMetaData connectionMetaData = mock(ConnectionMetaData.class);
        final HttpFields headers = mock(HttpFields.class);
        final HttpFields.Mutable responseHeaders = mock(HttpFields.Mutable.class);
        when(request.getConnectionMetaData()).thenReturn(connectionMetaData);
        when(connectionMetaData.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("198.51.100.11", 5222));
        when(request.getHeaders()).thenReturn(headers);
        when(headers.contains(HttpHeader.FORWARDED)).thenReturn(true);

        // Execute system under test
        final Request result = customizer.customize(request, responseHeaders);

        // Verify result
        assertSame(request, result, "Customizer must still return the original request even when delegation is skipped.");
        assertFalse(delegateInvoked.get(), "Forwarded customization must be skipped when the source is not trusted.");
    }
}

