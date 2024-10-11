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

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A specialized {@link ResolvedServiceAddress} that can be ordered.
 *
 * This class is (only) intended to be used by {@link HappyEyeballsResolver} which uses the ordering to generate results
 * in the order defined by SRV's 'priority' and 'weight' attributes.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@Immutable
class IndexedResolvedServiceAddress extends ResolvedServiceAddress implements Comparable<IndexedResolvedServiceAddress>
{
    final int index;

    public IndexedResolvedServiceAddress(final int index, final InetAddress inetAddress, final int port, final boolean isDirectTLS)
    {
        super(inetAddress, port, isDirectTLS);
        this.index = index;
    }

    public static Set<IndexedResolvedServiceAddress> from(final int index, final InetAddress[] addresses, final int port, final boolean isDirectTLS)
    {
        final Set<IndexedResolvedServiceAddress> result = new HashSet<>();
        for (InetAddress address : addresses) {
            result.add(new IndexedResolvedServiceAddress(index, address, port, isDirectTLS));
        }
        return result;
    }

    public int getIndex()
    {
        return index;
    }

    @Override
    public int compareTo(IndexedResolvedServiceAddress o) {
        return Integer.compare(index, o.index);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        IndexedResolvedServiceAddress that = (IndexedResolvedServiceAddress) o;
        return index == that.index;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(super.hashCode(), index);
    }

    @Override
    public String toString()
    {
        return "IndexedResolvedServiceAddress{" +
            "index=" + index +
            ", inetAddress=" + inetAddress +
            ", port=" + port +
            ", isDirectTLS=" + isDirectTLS +
            '}';
    }
}
