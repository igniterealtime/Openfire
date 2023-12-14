/*
 * Copyright (C) 2004-2008 Jive Software, 2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.component.InternalComponentManager;

import org.jivesoftware.openfire.spi.RoutingTableImpl;

import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests that verify the configuration of caches in the {@link CacheFactory}
 *
 * @author Alex Gidman
 */
public class CacheFactoryConfigTest {

    /**
     * For now this checks that the cache is unlimited and never expires
     */
    @ParameterizedTest
    @MethodSource("arguments")
    public void testCacheConfig(final String cacheName)
    {
        // Instantiate cache
        Cache<String, Serializable> cache = CacheFactory.createCache(cacheName);

        // Assert that there is only the cache we created for this test
        assertEquals(1, CacheFactory.getAllCaches().length);
        // Assert that the cache exists
        assertNotNull(cache);
        // Assert that the cache is unlimited in size (-1)
        assertEquals(-1, CacheFactory.getMaxCacheSize(cacheName));
        // Assert that the cache never expires
        assertEquals(-1, CacheFactory.getMaxCacheLifetime(cacheName));

    }

    /**
     * Clear the CacheFactory before and after each test to prevent leaky tests
     */
    @BeforeEach
    @AfterEach
    public void clearCacheFactory() {
        Cache[] caches = CacheFactory.getAllCaches();
        for (Cache cache: caches) {
            CacheFactory.destroyCache(cache.getName());
        }
    }

    /**
     * Returns a collection of cache names that Openfire expects to be unlimited in size and never expire.
     */
    private static Iterable<Arguments> arguments() {
        final Collection<Arguments> result = new LinkedList<>();

        result.add(Arguments.arguments(SessionManager.ISS_CACHE_NAME));
        result.add(Arguments.arguments(SessionManager.C2S_INFO_CACHE_NAME));
        result.add(Arguments.arguments(SessionManager.DOMAIN_SESSIONS_CACHE_NAME));
        result.add(Arguments.arguments(SessionManager.COMPONENT_SESSION_CACHE_NAME));
        result.add(Arguments.arguments(SessionManager.CM_CACHE_NAME));
        result.add(Arguments.arguments(RoutingTableImpl.C2S_CACHE_NAME));
        result.add(Arguments.arguments(RoutingTableImpl.ANONYMOUS_C2S_CACHE_NAME));
        result.add(Arguments.arguments(RoutingTableImpl.S2S_CACHE_NAME));
        result.add(Arguments.arguments(RoutingTableImpl.COMPONENT_CACHE_NAME));
        result.add(Arguments.arguments(RoutingTableImpl.C2S_SESSION_NAME));
        result.add(Arguments.arguments(InternalComponentManager.COMPONENT_CACHE_NAME));
        result.add(Arguments.arguments("Secret Keys Cache")); // Used in ServerDialback

        return result;
    }


}
