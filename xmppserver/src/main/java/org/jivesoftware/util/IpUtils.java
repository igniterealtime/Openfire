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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Various utility methods for working with (string representations of) IP-addresses.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class IpUtils
{
    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addressesAndRanges the values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull byte[] address, final @Nonnull Set<String> addressesAndRanges)
    {
        if (address.length == 4) {
            return isAddressInAnyOf(from4(address), addressesAndRanges);
        }
        if (address.length == 16) {
            return isAddressInAnyOf(from16(address), addressesAndRanges);
        }
        throw new IllegalArgumentException("Unrecognized address type: " + Arrays.toString(address));
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addressesAndRanges the values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull String address, final @Nonnull Set<String> addressesAndRanges)
    {
        if (isValidIpv4(address)) {
            return isAddressInAnyOf(Ipv4.of(address), addressesAndRanges);
        }
        if (isValidIpv6(address)) {
            return isAddressInAnyOf(Ipv6.of(address), addressesAndRanges);
        }
        throw new IllegalArgumentException("Unrecognized address type: " + address);
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addressesAndRanges the values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull InetAddress address, final @Nonnull Set<String> addressesAndRanges)
    {
        if (address instanceof Inet4Address) {
            return isAddressInAnyOf(from((Inet4Address) address), addressesAndRanges);
        }
        if (address instanceof Inet6Address) {
            return isAddressInAnyOf(from((Inet6Address) address), addressesAndRanges);
        }
        throw new IllegalArgumentException("Unrecognized address type: " + address);
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addressesAndRanges the values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull Ipv4 address, final @Nonnull Set<String> addressesAndRanges)
    {
        final Set<Ipv4> addresses = addressesAndRanges.stream().filter(IpUtils::isValidIpv4).map(Ipv4::of).collect(Collectors.toSet());
        final Set<Ipv4Range> ranges = addressesAndRanges.stream().filter(IpUtils::isValidIpv4Range).map(IpUtils::convertIpv4WildcardRangeToCidrNotation).map(Ipv4Range::parse).collect(Collectors.toSet());
        return isAddressInAnyOf(address, addresses, ranges);
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addressesAndRanges the values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull Ipv6 address, final @Nonnull Set<String> addressesAndRanges)
    {
        final Set<Ipv6> addresses = addressesAndRanges.stream().filter(IpUtils::isValidIpv6).map(Ipv6::of).collect(Collectors.toSet());
        final Set<Ipv6Range> ranges = addressesAndRanges.stream().filter(IpUtils::isValidIpv6Range).map(Ipv6Range::parse).collect(Collectors.toSet());
        return isAddressInAnyOf(address, addresses, ranges);
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addresses the address-values to compare the provided address with.
     * @param ranges the range-values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull Ipv4 address, final @Nonnull Set<Ipv4> addresses, final @Nonnull Set<Ipv4Range> ranges)
    {
        return addresses.stream().anyMatch(a->a.equals(address)) || ranges.stream().anyMatch(r->r.contains(address));
    }

    /**
     * Checks if the provided address is matched by any of the addresses or address ranges.
     *
     * @param address The address to look up.
     * @param addresses the address-values to compare the provided address with.
     * @param ranges the range-values to compare the provided address with.
     * @return <tt>true</tt> if the address is found in the addresses or ranges, otherwise <tt>false</tt>.
     */
    public static boolean isAddressInAnyOf(final @Nonnull Ipv6 address, final @Nonnull Set<Ipv6> addresses, final @Nonnull Set<Ipv6Range> ranges)
    {
        return addresses.stream().anyMatch(a->a.equals(address)) || ranges.stream().anyMatch(r->r.contains(address));
    }

    // Replace this with API introduced in https://github.com/jgonian/commons-ip-math/pull/32
    static Ipv4 from(final @Nonnull Inet4Address address) {
        return from4(address.getAddress());
    }

    // Replace this with API introduced in https://github.com/jgonian/commons-ip-math/pull/32
    static Ipv4 from4(final @Nonnull byte[] octets) {
        long result = 0;
        for (byte octet : octets) {
            result = (result << 8) | Byte.toUnsignedInt(octet);
        }
        return Ipv4.of(result);
    }

    // Replace this with API introduced in https://github.com/jgonian/commons-ip-math/pull/32
    static Ipv6 from(final @Nonnull Inet6Address address) {
        return from16(address.getAddress());
    }

    // Replace this with API introduced in https://github.com/jgonian/commons-ip-math/pull/32
    static Ipv6 from16(final @Nonnull byte[] octets) {
        BigInteger result = BigInteger.ZERO;
        for (byte octet : octets) {
            result = result.shiftLeft(8).add(BigInteger.valueOf(Byte.toUnsignedInt(octet)));
        }
        return Ipv6.of(result);
    }

    /**
     * When the provided input is an IPv6 literal that is enclosed in brackets (the [] style as expressed in
     * https://tools.ietf.org/html/rfc2732 and https://tools.ietf.org/html/rfc6874), this method returns the value
     * stripped from those brackets (the IPv6 address, instead of the literal). In all other cases, the input value is
     * returned.
     *
     * @param address The value from which to strip brackets.
     * @return the input value, stripped from brackets if applicable.
     */
    @Nonnull
    public static String removeBracketsFromIpv6Address(@Nonnull final String address)
    {
        final String result;
        if (address.startsWith("[") && address.endsWith("]")) {
            result = address.substring(1, address.length()-1);
            try {
                Ipv6.parse(result);
                // The remainder is a valid IPv6 address. Return the original value.
                return result;
            } catch (IllegalArgumentException e) {
                // The remainder isn't a valid IPv6 address. Return the original value.
                return address;
            }
        }
        // Not a bracket-enclosed string. Return the original input.
        return address;
    }

    /**
     * Replaces a representation of an IPv4 network range that is using wildcards (eg: <tt>192.*.*.*</tt>) with a
     * notation that is CIDR-based (eg: <tt>192.0.0.0/8</tt>).
     *
     * When the string cannot be transformed, the original string is returned.
     *
     * @param value The network range to transform
     * @return The transformed range.
     */
    public static String convertIpv4WildcardRangeToCidrNotation(final @Nonnull String value)
    {
        return value.replace(".*.*.*", ".0.0.0/8")
                    .replace(".*.*", ".0.0/16")
                    .replace(".*", ".0/24");
    }

    /**
     * Checks if the provided value is a representation of an IPv4 address.
     *
     * @param value the value to check
     * @return true if the provided value is an IPv4 address, otherwise false.
     */
    public static boolean isValidIpv4(@Nullable final String value)
    {
        if (value == null) {
            return false;
        }
        try {
            Ipv4.parse(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the provided value is a representation of an IPv4 address range.
     *
     * @param value the value to check
     * @return true if the provided value is an IPv4 address range, otherwise false.
     */
    public static boolean isValidIpv4Range(@Nullable final String value)
    {
        if (value == null) {
            return false;
        }
        try {
            Ipv4Range.parse(convertIpv4WildcardRangeToCidrNotation(value));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the provided value is a representation of an IPv6 address.
     *
     * @param value the value to check
     * @return true if the provided value is an IPv6 address, otherwise false.
     */
    public static boolean isValidIpv6(@Nullable final String value)
    {
        if (value == null) {
            return false;
        }
        try {
            Ipv6.parse(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the provided value is a representation of an IPv6 address range.
     *
     * @param value the value to check
     * @return true if the provided value is an IPv6 address range, otherwise false.
     */
    public static boolean isValidIpv6Range(@Nullable final String value)
    {
        if (value == null) {
            return false;
        }
        try {
            Ipv6Range.parse(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    /**
     * Checks if the provided value is a representation of an IP address.
     *
     * @param value the value to check
     * @return true if the provided value is an IP address, otherwise false.
     */
    public static boolean isValidIpAddress(@Nullable final String value)
    {
        return isValidIpv4(value) || isValidIpv6(value);
    }

    /**
     * Checks if the provided value is a representation of an IP address range.
     *
     * @param value the value to check
     * @return true if the provided value is an IP address range, otherwise false.
     */
    public static boolean isValidIpRange(@Nullable final String value)
    {
        return isValidIpv4Range(value) || isValidIpv6Range(value);
    }

    /**
     * Checks if the provided value is a representation of an IP address or an IP address range.
     *
     * @param value the value to check
     * @return true if the provided value is an IP address or IP address range, otherwise false.
     */
    public static boolean isValidIpAddressOrRange(@Nullable final String value)
    {
        return isValidIpAddress(value) || isValidIpRange(value);
    }
}
