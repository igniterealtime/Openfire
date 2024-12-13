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
package org.jivesoftware.openfire.user;

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link MappedUserProvider}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class MappedUserProviderTest
{
    @BeforeAll
    public static void setup() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    @AfterEach
    public void unsetProperties() {
        JiveGlobals.deleteProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME);
    }

    /**
     * Verifies that the user count is based on data from all providers.
     */
    @Test
    public void testCountsUsersFromAllProviders() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test.
        final int result = provider.getUserCount();

        // Verify results.
        assertEquals(2, result);
    }

    /**
     * Verifies that users are returned from all providers.
     */
    @Test
    public void testGetUserNamesFromAllProviders() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test.
        final Collection<String> result = provider.getUsernames();

        // Verify results.
        assertEquals(2, result.size());
        assertTrue(result.contains("jane"));
        assertTrue(result.contains("john"));
    }

    /**
     * Verifies that a user named 'jane' is returned.
     */
    @Test
    public void testGetJane() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test.
        final User result = provider.loadUser("jane");

        // Verify results.
        assertNotNull(result);
        assertEquals("jane", result.getUsername());
    }

    /**
     * Verifies that a user named 'john' is returned.
     */
    @Test
    public void testGetJohn() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test.
        final User result = provider.loadUser("john");

        // Verify results.
        assertNotNull(result);
        assertEquals("john", result.getUsername());
    }

    /**
     * Verifies that an exception is thrown when a non-existing user is being requested.
     */
    @Test()
    public void testGetNonExistingUser() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test & Verify results.
        assertThrows(UserNotFoundException.class, () -> provider.loadUser("non-existing-user"));
    }

    /**
     * Verifies a new user can be created.
     */
    @Test()
    public void testCreateUser() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test.
        provider.createUser("jack", "secret", "Jack Doe", "jack@example.org");
        final User result = provider.loadUser("jack");

        // Verify results
        assertNotNull(result);
        assertEquals("jack", result.getUsername());
    }

    /**
     * Verifies that an exception is thrown when a new user is being created, using a username that is not unique.
     */
    @Test()
    public void testCreateDuplicateUser() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserProvider.PROPERTY_MAPPER_CLASSNAME, TestUserProviderMapper.class.getName());
        final MappedUserProvider provider = new MappedUserProvider();

        // Execute system under test & Verify results.
        assertThrows(UserAlreadyExistsException.class, () -> provider.createUser("jane", "test", "Jane But Not Doe", "jane@example.com"));
    }
}
