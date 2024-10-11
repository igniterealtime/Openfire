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
import java.io.Serializable;
import java.util.*;

/**
 * A (partial) representation of an SRV record, containing an (unresolved) hostname, port, priority and weight attributes.
 * It is expected to be used primarily to represent the result of an SRV query.
 *
 * This representation does not include other attributes of an SRV record, such as the service name, transport protocol
 * and time-to-live.
 *
 * An indicator is included that signals if the address is to be used with DirectTLS (as opposed to STARTTLS) encryption.
 * This value can be thought of as being a derivative of the 'service' that was looked up, as for example, a lookup
 * result for 'xmpp-server' would not be DirectTLS, as opposed to a lookup result for 'xmpps-server', that would be
 * DirectTLS
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@Immutable
public class SrvRecord implements Serializable
{
    private final String hostname;
    private final int port;
    private final boolean isDirectTLS;
    private final int priority;
    private final int weight;

    public static SrvRecord from(final @Nonnull String[] srvRecordEntries, final boolean directTLS)
    {
        final String hostname = srvRecordEntries[srvRecordEntries.length - 1];
        final int port = Integer.parseInt(srvRecordEntries[srvRecordEntries.length - 2]);
        final int weight = Integer.parseInt(srvRecordEntries[srvRecordEntries.length - 3]);
        final int priority = Integer.parseInt(srvRecordEntries[srvRecordEntries.length - 4]);
        return new SrvRecord(hostname.endsWith(".") ? hostname.substring(0, hostname.length()-1) : hostname, port, directTLS, priority, weight);
    }

    public SrvRecord(final @Nonnull String hostname, final int port, final boolean isDirectTLS)
    {
        this(hostname, port, isDirectTLS, 0, 0);
    }

    public SrvRecord(final @Nonnull String hostname, final int port, final boolean isDirectTLS, final int priority, final int weight)
    {
        this.hostname = hostname;
        this.port = port;
        this.isDirectTLS = isDirectTLS;
        this.priority = priority;
        this.weight = weight;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public boolean isDirectTLS()
    {
        return isDirectTLS;
    }

    public int getPriority()
    {
        return priority;
    }

    public int getWeight()
    {
        return weight;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SrvRecord srvRecord = (SrvRecord) o;
        return port == srvRecord.port && isDirectTLS == srvRecord.isDirectTLS && priority == srvRecord.priority && weight == srvRecord.weight && Objects.equals(hostname, srvRecord.hostname);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(hostname, port, isDirectTLS, priority, weight);
    }

    @Override
    public String toString()
    {
        return "SrvRecord{" +
            "hostname='" + hostname + '\'' +
            ", port=" + port +
            ", isDirectTLS=" + isDirectTLS +
            ", priority=" + priority +
            ", weight=" + weight +
            '}';
    }

    public static List<Set<SrvRecord>> prioritize(SrvRecord[] records) {
        return prioritize(Arrays.asList(records));
    }

    public static List<Set<SrvRecord>> prioritize(final Collection<SrvRecord> records)
    {
        final List<Set<SrvRecord>> result = new LinkedList<>();

        // sort by priority (ascending)
        SortedMap<Integer, Set<SrvRecord>> byPriority = new TreeMap<>();
        for(final SrvRecord record : records) {
            if (byPriority.containsKey(record.getPriority())) {
                byPriority.get(record.getPriority()).add(record);
            } else {
                final Set<SrvRecord> set = new HashSet<>();
                set.add(record);
                byPriority.put(record.getPriority(), set);
            }
        }

        // now, randomize each priority set by weight.
        for(Map.Entry<Integer, Set<SrvRecord>> weights : byPriority.entrySet()) {

            final List<SrvRecord> zeroWeights = new LinkedList<>();
            final Set<SrvRecord> priorityGroupResults = new LinkedHashSet<>(); // A set that retains order (which we'll randomize)

            int totalWeight = 0;
            final Iterator<SrvRecord> i = weights.getValue().iterator();
            while (i.hasNext()) {
                final SrvRecord next = i.next();
                if (next.getWeight() == 0) {
                    // set aside, as these should be considered last according to the RFC.
                    zeroWeights.add(next);
                    i.remove();
                    continue;
                }

                totalWeight += next.getWeight();
            }

            int iterationWeight = totalWeight;
            Iterator<SrvRecord> iter = weights.getValue().iterator();
            while (iter.hasNext()) {
                int needle = new Random().nextInt(iterationWeight);

                while (true) {
                    final SrvRecord record = iter.next();
                    needle -= record.getWeight();
                    if (needle <= 0) {
                        priorityGroupResults.add(record);
                        iter.remove();
                        iterationWeight -= record.getWeight();
                        break;
                    }
                }
                iter = weights.getValue().iterator();
            }

            // Append the hosts with zero priority (shuffled)
            Collections.shuffle(zeroWeights);
            priorityGroupResults.addAll(zeroWeights);

            // Finally, add the entire priority group to the larger result.
            result.add(priorityGroupResults);
        }

        return result;
    }
}
