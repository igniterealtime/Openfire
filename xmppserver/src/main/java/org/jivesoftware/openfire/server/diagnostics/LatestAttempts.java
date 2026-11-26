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
package org.jivesoftware.openfire.server.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the most recent outbound connection attempt for each pair of local and remote domain, on this cluster node,
 * whether it succeeded or failed.
 *
 * The number of pairs kept is bounded, evicting the pair stored least recently, so that a server that federates with
 * many domains does not accumulate attempts without limit. Thread-safe.
 *
 * @author Matthew Vivian
 */
public final class LatestAttempts
{
    private final Map<String, AttemptRecorder> attempts;

    /**
     * @param capacity the number of domain pairs to keep; at least one.
     */
    public LatestAttempts(final int capacity)
    {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least one, but was " + capacity);
        }
        this.attempts = new LinkedHashMap<>(16, 0.75f, false)
        {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, AttemptRecorder> eldest)
            {
                return size() > capacity;
            }
        };
    }

    /**
     * Stores an attempt as the latest for its domain pair, replacing any earlier one.
     *
     * @param attempt the attempt.
     */
    public synchronized void put(final AttemptRecorder attempt)
    {
        final String key = key(attempt.getLocalDomain(), attempt.getRemoteDomain());
        // Remove first, so that a replaced pair counts as the most recently stored.
        attempts.remove(key);
        attempts.put(key, attempt);
    }

    /**
     * @param localDomain the domain the connection was made from.
     * @param remoteDomain the domain the connection was made to.
     * @return the latest attempt for the pair, if one is kept.
     */
    public synchronized Optional<AttemptRecorder> get(final String localDomain, final String remoteDomain)
    {
        return Optional.ofNullable(attempts.get(key(localDomain, remoteDomain)));
    }

    public synchronized int size()
    {
        return attempts.size();
    }

    private static String key(final String localDomain, final String remoteDomain)
    {
        // A domain name cannot contain a space, so this cannot be ambiguous.
        return localDomain + ' ' + remoteDomain;
    }
}
