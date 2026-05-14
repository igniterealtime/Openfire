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
package org.jivesoftware.openfire.nio;

import io.netty.channel.ChannelId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link QuicSessionRegistry} and {@link QuicConnectionId}.
 */
class QuicSessionRegistryTest
{
    private QuicSessionRegistry registry;

    @BeforeEach
    void setUp()
    {
        registry = new QuicSessionRegistry();
    }

    // -----------------------------------------------------------------------
    // QuicConnectionId value-type tests
    // -----------------------------------------------------------------------

    @Test
    void connectionId_equalityBasedOnChannelId()
    {
        final ChannelId id = mockChannelId("abc");
        final QuicConnectionId cid1 = new QuicConnectionId(id);
        final QuicConnectionId cid2 = new QuicConnectionId(id);
        assertEquals(cid1, cid2);
        assertEquals(cid1.hashCode(), cid2.hashCode());
    }

    @Test
    void connectionId_differentChannelIdsAreNotEqual()
    {
        final QuicConnectionId cid1 = new QuicConnectionId(mockChannelId("aaa"));
        final QuicConnectionId cid2 = new QuicConnectionId(mockChannelId("bbb"));
        assertNotEquals(cid1, cid2);
    }

    @Test
    void connectionId_nullChannelIdThrows()
    {
        assertThrows(NullPointerException.class, () -> new QuicConnectionId(null));
    }

    @Test
    void connectionId_toStringContainsShortText()
    {
        final ChannelId id = mockChannelId("short42");
        final QuicConnectionId cid = new QuicConnectionId(id);
        assertTrue(cid.toString().contains("short42"), "toString should contain the short text");
    }

    // -----------------------------------------------------------------------
    // QuicSessionRegistry basic tests
    // -----------------------------------------------------------------------

    @Test
    void registry_findReturnsNullWhenEmpty()
    {
        assertNull(registry.find(new QuicConnectionId(mockChannelId("x"))));
    }

    @Test
    void registry_registerAndFind()
    {
        final QuicConnectionId cid = new QuicConnectionId(mockChannelId("conn1"));
        final QuicSessionStreamRouter router = mock(QuicSessionStreamRouter.class);

        registry.register(cid, router);

        assertSame(router, registry.find(cid));
        assertEquals(1, registry.size());
    }

    @Test
    void registry_unregisterRemovesEntry()
    {
        final QuicConnectionId cid = new QuicConnectionId(mockChannelId("conn2"));
        final QuicSessionStreamRouter router = mock(QuicSessionStreamRouter.class);

        registry.register(cid, router);
        registry.unregister(cid);

        assertNull(registry.find(cid));
        assertEquals(0, registry.size());
    }

    @Test
    void registry_unregisterNonExistentIsNoOp()
    {
        assertDoesNotThrow(() -> registry.unregister(new QuicConnectionId(mockChannelId("ghost"))));
    }

    @Test
    void registry_multipleConnectionsAreIndependent()
    {
        final QuicConnectionId cid1 = new QuicConnectionId(mockChannelId("c1"));
        final QuicConnectionId cid2 = new QuicConnectionId(mockChannelId("c2"));
        final QuicSessionStreamRouter r1 = mock(QuicSessionStreamRouter.class);
        final QuicSessionStreamRouter r2 = mock(QuicSessionStreamRouter.class);

        registry.register(cid1, r1);
        registry.register(cid2, r2);

        assertSame(r1, registry.find(cid1));
        assertSame(r2, registry.find(cid2));
        assertEquals(2, registry.size());

        registry.unregister(cid1);
        assertNull(registry.find(cid1));
        assertSame(r2, registry.find(cid2));
        assertEquals(1, registry.size());
    }

    @Test
    void registry_reRegisterSameRouterIsIdempotent()
    {
        final QuicConnectionId cid = new QuicConnectionId(mockChannelId("c3"));
        final QuicSessionStreamRouter router = mock(QuicSessionStreamRouter.class);

        registry.register(cid, router);
        registry.register(cid, router); // same router — should not warn or throw

        assertSame(router, registry.find(cid));
        assertEquals(1, registry.size());
    }

    // -----------------------------------------------------------------------
    // Concurrent access test
    // -----------------------------------------------------------------------

    @Test
    void registry_concurrentRegisterAndUnregister() throws InterruptedException
    {
        final int threadCount = 8;
        final int opsPerThread = 200;
        final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger errors = new AtomicInteger(0);

        final List<QuicConnectionId> cids = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            cids.add(new QuicConnectionId(mockChannelId("t" + i)));
        }

        for (int t = 0; t < threadCount; t++) {
            final QuicConnectionId cid = cids.get(t);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        final QuicSessionStreamRouter router = mock(QuicSessionStreamRouter.class);
                        registry.register(cid, router);
                        registry.find(cid);
                        registry.unregister(cid);
                    }
                } catch (final Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(0, errors.get(), "No errors expected during concurrent access");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Simple ChannelId stub whose equals/hashCode are based on the supplied text.
     * We cannot use Mockito to stub equals()/hashCode() so we use a hand-written stub.
     */
    private static ChannelId mockChannelId(final String shortText)
    {
        return new ChannelId()
        {
            @Override public String asShortText() { return shortText; }
            @Override public String asLongText()  { return shortText + "-long"; }
            @Override public int compareTo(final ChannelId o) { return shortText.compareTo(o.asShortText()); }
            @Override public int hashCode()  { return shortText.hashCode(); }
            @Override public boolean equals(final Object o)
            {
                if (this == o) return true;
                if (!(o instanceof ChannelId)) return false;
                return shortText.equals(((ChannelId) o).asShortText());
            }
            @Override public String toString() { return shortText; }
        };
    }
}
