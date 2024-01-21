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
package org.jivesoftware.util.cache;

import org.jivesoftware.util.InitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implemention of {@link CacheFactory}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CacheFactoryTest
{
    @BeforeEach
    public void initCacheFactory() throws InitializationException
    {
        CacheFactory.initialize();
        clearCacheFactory();
    }

    @AfterEach
    public void clearCacheFactory() {
        Cache[] caches = CacheFactory.getAllCaches();
        for (Cache cache: caches) {
            CacheFactory.destroyCache(cache.getName());
        }
    }

    /**
     * Asserts that a cache can be created.
     */
    @Test
    public void testCacheCreation() throws Exception
    {
        // Setup test fixture.

        // Execute system under test.
        final Cache result = CacheFactory.createCache("unittest-cache-creation");

        // Verify results.
        assertNotNull(result);
    }

    /**
     * Asserts that a previously created cache is returned on subsequent invocations of the creation method.
     */
    @Test
    public void testCacheRecreation() throws Exception
    {
        // Setup test fixture.
        final String name = "unittest-cache-recreation";

        // Execute system under test.
        final Cache resultA = CacheFactory.createCache(name);
        final Cache resultB = CacheFactory.createCache(name);

        // Verify results.
        assertNotNull(resultB);
        assertSame(resultA, resultB);
    }

    /**
     * Asserts that a local cache can be created.
     */
    @Test
    public void testLocalCacheCreation() throws Exception
    {
        // Setup test fixture.

        // Execute system under test.
        final Cache result = CacheFactory.createLocalCache("unittest-localcache-creation");

        // Verify results.
        assertNotNull(result);
    }

    /**
     * Asserts that a previously created local cache is returned on subsequent invocations of the creation method.
     */
    @Test
    public void testLocalCacheRecreation() throws Exception
    {
        // Setup test fixture.
        final String name = "unittest-localcache-recreation";

        // Execute system under test.
        final Cache resultA = CacheFactory.createLocalCache(name);
        final Cache resultB = CacheFactory.createLocalCache(name);

        // Verify results.
        assertNotNull(resultB);
        assertSame(resultA, resultB);
    }

    /**
     * Asserts that a serializing cache can be created.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2781">OF-2781</a>
     */
    @Test
    public void testSerializingCacheCreation() throws Exception
    {
        // Setup test fixture.

        // Execute system under test.
        final Cache result = CacheFactory.createSerializingCache("unittest-serializingcache-creation", String.class, String.class);

        // Verify results.
        assertNotNull(result);
        assertInstanceOf(SerializingCache.class, ((CacheWrapper) result).getWrappedCache());
    }

    /**
     * Asserts that a previously created serializing cache is returned on subsequent invocations of the creation method.
     */
    @Test
    public void testSerializingCacheRecreation() throws Exception
    {
        // Setup test fixture.
        final String name = "unittest-serializingcache-recreation";

        // Execute system under test.
        final Cache resultA = CacheFactory.createSerializingCache(name, String.class, String.class);
        final Cache resultB = CacheFactory.createSerializingCache(name, String.class, String.class);

        // Verify results.
        assertNotNull(resultB);
        assertSame(resultA, resultB);
        assertInstanceOf(SerializingCache.class, ((CacheWrapper) resultB).getWrappedCache());
    }

    /**
     * Verifies that after the JVM left the cluster (at which point the cache implementations are swapped) a Serializing
     * Cache remains a Serializing Cache.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2782">OF-2782</a>
     */
    @Test
    public void testSerializingCacheRetainsTypeAfterLeftCluster() throws Exception
    {
        // Setup test fixture.
        final String name = "unittest-serializingcache-leftcluster";
        final CacheWrapper cache = CacheFactory.createSerializingCache(name, String.class, String.class);

        // Execute system under test.
        CacheFactory.leftCluster();

        // Verify results.
        assertInstanceOf(SerializingCache.class, cache.getWrappedCache());
    }
}
