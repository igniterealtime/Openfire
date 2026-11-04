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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link LatestAttempts}, which keeps the most recent connection attempt per pair of domains.
 *
 * @author Matthew Vivian
 */
public class LatestAttemptsTest
{
    @Test
    public void testLatestAttemptIsReturnedForItsDomainPair()
    {
        // Setup test fixture.
        final LatestAttempts attempts = new LatestAttempts(10);
        final AttemptRecorder first = new AttemptRecorder("example.com", "remote.example.org");
        final AttemptRecorder second = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        attempts.put(first);
        attempts.put(second);

        // Verify results.
        assertSame(second, attempts.get("example.com", "remote.example.org").orElseThrow(), "a newer attempt must replace the older one for the same pair");
    }

    @Test
    public void testAttemptsAreKeptPerPair()
    {
        // Setup test fixture.
        final LatestAttempts attempts = new LatestAttempts(10);
        final AttemptRecorder toRemote = new AttemptRecorder("example.com", "remote.example.org");
        final AttemptRecorder fromSubdomain = new AttemptRecorder("conference.example.com", "remote.example.org");

        // Execute system under test.
        attempts.put(toRemote);
        attempts.put(fromSubdomain);

        // Verify results.
        assertSame(toRemote, attempts.get("example.com", "remote.example.org").orElseThrow(), "each domain pair must keep its own attempt");
        assertSame(fromSubdomain, attempts.get("conference.example.com", "remote.example.org").orElseThrow(), "each domain pair must keep its own attempt");
        assertTrue(attempts.get("example.com", "other.example.org").isEmpty(), "a pair with no attempt must have nothing");
    }

    @Test
    public void testOldestPairIsEvictedWhenFull()
    {
        // Setup test fixture.
        final LatestAttempts attempts = new LatestAttempts(2);
        attempts.put(new AttemptRecorder("example.com", "a.example.org"));
        attempts.put(new AttemptRecorder("example.com", "b.example.org"));

        // Execute system under test.
        attempts.put(new AttemptRecorder("example.com", "c.example.org"));

        // Verify results.
        assertTrue(attempts.get("example.com", "a.example.org").isEmpty(), "the least recently stored pair must be evicted, so memory stays bounded");
        assertTrue(attempts.get("example.com", "c.example.org").isPresent(), "the newest attempt must be kept");
        assertEquals(2, attempts.size(), "the store must never hold more than its capacity");
    }

    @Test
    public void testCapacityMustBePositive()
    {
        assertThrows(IllegalArgumentException.class, () -> new LatestAttempts(0), "a store that can hold nothing is a configuration error");
    }
}
