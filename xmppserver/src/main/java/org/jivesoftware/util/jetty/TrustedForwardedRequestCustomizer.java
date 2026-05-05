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

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;
import org.jivesoftware.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Wraps a ForwardedRequestCustomizer and only applies it to requests from trusted proxies.
 *
 * The set of trusted proxies supports a mix of IPv4 and IPv6 addresses and ranges.
 *
 * This guards against spoofed forwarded headers from untrusted network peers.
 */
public class TrustedForwardedRequestCustomizer implements HttpConfiguration.Customizer
{
    private static final Logger Log = LoggerFactory.getLogger(TrustedForwardedRequestCustomizer.class);

    private final ForwardedRequestCustomizer delegate;
    private final Set<String> trustedProxies;

    /**
     * Applies forwarded-header processing only for requests that originate from a trusted proxy.
     *
     * This wrapper protects against spoofed forwarded headers by ignoring them when the direct peer address is not in
     * the configured trusted proxy ranges.
     *
     * @param delegate the customizer to delegate to when the request source is trusted
     * @param trustedProxies the set of trusted proxy IP ranges
     */
    public TrustedForwardedRequestCustomizer(@Nonnull final ForwardedRequestCustomizer delegate, @Nonnull final Set<String> trustedProxies)
    {
        this.trustedProxies = new HashSet<>();
        for (final String trustedProxy : trustedProxies) {
            if (!IpUtils.isValidIpAddressOrRange(trustedProxy)) {
                Log.warn("Trusted proxy value is not a valid IP address or range: {} (it will be ignored).", trustedProxy);
            } else {
                this.trustedProxies.add(trustedProxy);
            }
        }

        if (this.trustedProxies.isEmpty()) {
            Log.warn("Initializing TrustedForwardedRequestCustomizer with an empty set of trusted proxies. This will reject all forwarded requests.");
        }

        this.delegate = delegate;
    }

    /**
     * Applies the delegated forwarded-header customization only when the request source is trusted.
     *
     * @param request the request to potentially customize
     * @param responseHeaders mutable response headers available to customizers
     * @return the possibly customized request instance
     */
    @Override
    public Request customize(Request request, HttpFields.Mutable responseHeaders)
    {
        final SocketAddress remoteSocketAddress = request.getConnectionMetaData().getRemoteSocketAddress();
        if (isTrusted(remoteSocketAddress)) {
            return delegate.customize(request, responseHeaders);
        } else {
            if (hasForwardedHeaders(request)) {
                Log.debug("Ignoring forwarded headers from untrusted source: {}", remoteSocketAddress);
            }
        }
        return request;
    }

    /**
     * Determines if the remote socket address belongs to a trusted proxy.
     *
     * @param addr the remote socket address, which can be absent or unresolved
     * @return true when the address can be resolved to an IP that matches a trusted proxy range
     */
    @VisibleForTesting
    boolean isTrusted(@Nullable final SocketAddress addr)
    {
        if (addr == null) {
            return false;
        }

        if (addr instanceof InetSocketAddress address) {
            if (address.getAddress() == null) {
                Log.trace("Unable to determine IP address of remote socket (unresolved address): {}", addr);
                return false;
            }
            return IpUtils.isAddressInAnyOf(address.getAddress(), trustedProxies);
        }

        Log.trace("Unable to determine IP address of remote socket (unsupported remote socket type: {}): {}", addr.getClass(), addr);
        return false;
    }

    /**
     * Checks if a request contains any supported forwarded headers.
     *
     * @param request the request to inspect
     * @return true when at least one forwarded header is present
     */
    @VisibleForTesting
    boolean hasForwardedHeaders(@Nonnull final Request request)
    {
        return request.getHeaders().contains(HttpHeader.FORWARDED)
            || request.getHeaders().contains(HttpHeader.X_FORWARDED_FOR)
            || request.getHeaders().contains(HttpHeader.X_FORWARDED_HOST)
            || request.getHeaders().contains(HttpHeader.X_FORWARDED_PORT)
            || request.getHeaders().contains(HttpHeader.X_FORWARDED_PROTO)
            || request.getHeaders().contains(HttpHeader.X_FORWARDED_SERVER);
    }
}
