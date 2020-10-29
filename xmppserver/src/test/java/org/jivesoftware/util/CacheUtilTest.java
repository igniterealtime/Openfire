/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheUtil;
import org.jivesoftware.util.cache.DefaultLocalCacheStrategy;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Unit tests that verify the functionality of {@link CacheUtil}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class CacheUtilTest
{
    /**
     * Verifies that {@link CacheUtil#removeValueFromMultiValuedCache(Cache, Serializable, Object)} successfully removes an element from a cache.
     */
    @Test
    public void testRemoveElementFromCollection() throws Exception
    {
        // Setup test fixture.
        final ArrayList<String> collection = new ArrayList<>();
        collection.add( "existing value" );
        final Cache<String, ArrayList<String>> cache = new DefaultLocalCacheStrategy().createCache( "test" );
        cache.put( "existing key", collection );

        // Execute system under test.
        Throwable result = null;
        try
        {
            CacheUtil.removeValueFromMultiValuedCache( cache, "existing key", "existing value" );
        }
        catch ( Throwable t )
        {
            result = t;
        }

        // Verify results.
        assertNull( result );
        assertFalse( collection.contains( "existing value" ) );
    }

    /**
     * Verifies that {@link CacheUtil#removeValueFromMultiValuedCache(Cache, Serializable, Object)} does not change the cache or
     * throw an exception, while an element is being removed from a cached collection that does not contain that entity..
     */
    @Test
    public void testRemoveNoneExistingElementFromCollection() throws Exception
    {
        // Setup test fixture.
        final ArrayList<String> collection = new ArrayList<>();
        collection.add( "existing value" );
        final Cache<String, ArrayList<String>> cache = new DefaultLocalCacheStrategy().createCache( "test" );
        cache.put( "existing key", collection );

        // Execute system under test.
        Throwable result = null;
        try
        {
            CacheUtil.removeValueFromMultiValuedCache( cache, "existing key", "non-existing value" );
        }
        catch ( Throwable t )
        {
            result = t;
        }

        // Verify results.
        assertNull( result );
        assertTrue( collection.contains( "existing value" ) );
    }

    /**
     * Verifies that {@link CacheUtil#removeValueFromMultiValuedCache(Cache, Serializable, Object)} does not change the cache or
     * throw an exception, while an element is being removed from a collection that's not in the cache.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1829">OF-1829</a>
     */
    @Test
    public void testRemoveElementFromNonExistentCollection() throws Exception
    {
        // Setup test fixture.
        final Cache<String, ArrayList<String>> cache = new DefaultLocalCacheStrategy().createCache( "test" );

        // Execute system under test.
        Throwable result = null;
        try
        {
            CacheUtil.removeValueFromMultiValuedCache( cache, "non-existing-key", "a value" );
        }
        catch ( Throwable t )
        {
            result = t;
        }

        // Verify results.
        assertNull( result );
    }
}
