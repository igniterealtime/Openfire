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

import net.jcip.annotations.Immutable;

import javax.annotation.Nonnull;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * A representation of an Internet Protocol-based socket address (an IP address and port) for a (presumably XMPP) service.
 *
 * Instances of this class are intended to represent results of DNS resolution, intended to be used when setting up a
 * network connection to a remote XMPP service.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@Immutable
public class ResolvedServiceAddress
{
    final InetAddress inetAddress;
    final int port;
    final boolean isDirectTLS;

    public ResolvedServiceAddress(final @Nonnull InetAddress inetAddress, int port, boolean isDirectTLS)
    {
        this.inetAddress = inetAddress;
        this.port = port;
        this.isDirectTLS = isDirectTLS;
    }

    public InetAddress getInetAddress()
    {
        return inetAddress;
    }

    public int getPort()
    {
        return port;
    }

    public boolean isDirectTLS()
    {
        return isDirectTLS;
    }

    public boolean isIPv6()
    {
        return inetAddress instanceof Inet6Address;
    }

    public InetSocketAddress generateSocketAddress()
    {
        return new InetSocketAddress(inetAddress, port);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResolvedServiceAddress that = (ResolvedServiceAddress) o;
        return port == that.port && isDirectTLS == that.isDirectTLS && Objects.equals(inetAddress, that.inetAddress);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(inetAddress, port, isDirectTLS);
    }

    @Override
    public String toString()
    {
        return "ResolvedServiceAddress{" +
            "inetAddress=" + inetAddress +
            ", port=" + port +
            ", isDirectTLS=" + isDirectTLS +
            '}';
    }
}
