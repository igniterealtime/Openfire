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
package org.jivesoftware.openfire.group;

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link HybridGroupProvider}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class HybridGroupProviderTest
{
    @BeforeAll
    public static void setup() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        CacheFactory.initialize();
    }

    @BeforeEach
    @AfterEach
    public void unsetProperties() {
        JiveGlobals.deleteProperty(HybridGroupProvider.PRIMARY_PROVIDER.getKey());
        JiveGlobals.deleteProperty(HybridGroupProvider.SECONDARY_PROVIDER.getKey());
        JiveGlobals.deleteProperty(HybridGroupProvider.TERTIARY_PROVIDER.getKey());
    }

    /**
     * Verifies that the group count is based on data from all providers.
     */
    @Test
    public void testCountsGroupsFromAllProviders() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test.
        final int result = provider.getGroupCount();

        // Verify results.
        assertEquals(2, result);
    }

    /**
     * Verifies that groups are returned from all providers.
     */
    @Test
    public void testGetGroupNamesFromAllProviders() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test.
        final Collection<String> result = provider.getGroupNames();

        // Verify results.
        assertEquals(2, result.size());
        assertTrue(result.contains("little-endians"));
        assertTrue(result.contains("big-endians"));
    }

    /**
     * Verifies that a group named 'little-endians' is returned (from the secondary provider).
     */
    @Test
    public void testGetJane() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test.
        final Group result = provider.getGroup("little-endians");

        // Verify results.
        assertNotNull(result);
        assertEquals("little-endians", result.getName());
    }

    /**
     * Verifies that a group named 'big-endians' is returned (from the tertiary provider).
     */
    @Test
    public void testGetJohn() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test.
        final Group result = provider.getGroup("big-endians");

        // Verify results.
        assertNotNull(result);
        assertEquals("big-endians", result.getName());
    }

    /**
     * Verifies that an exception is thrown when a non-existing group is being requested.
     */
    @Test()
    public void testGetNonExistingGroup() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test & Verify results.
        assertThrows(GroupNotFoundException.class, () -> provider.getGroup("non-existing-group"));
    }

    /**
     * Verifies a new group can be created.
     */
    @Test()
    public void testCreateGroup() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test.
        provider.createGroup("middle-endians");
        final Group result = provider.getGroup("middle-endians");

        // Verify results
        assertNotNull(result);
        assertEquals("middle-endians", result.getName());
    }

    /**
     * Verifies that an exception is thrown when a new group is being created, using a groupname that is not unique.
     */
    @Test()
    public void testCreateDuplicateGroup() throws Exception
    {
        // Setup test fixture.
        HybridGroupProvider.PRIMARY_PROVIDER.setValue(TestGroupProvider.NoGroupProvider.class);
        HybridGroupProvider.SECONDARY_PROVIDER.setValue(TestGroupProvider.LittleEndiansGroupProvider.class);
        HybridGroupProvider.TERTIARY_PROVIDER.setValue(TestGroupProvider.BigEndiansGroupProvider.class);
        final HybridGroupProvider provider = new HybridGroupProvider();

        // Execute system under test & Verify results.
        assertThrows(GroupAlreadyExistsException.class, () -> provider.createGroup("little-endians"));
    }
}
