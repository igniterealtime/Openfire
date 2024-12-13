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
package org.jivesoftware.openfire.user.property;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link MappedUserPropertyProvider}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class MappedUserPropertyProviderTest
{
    @BeforeAll
    public static void setup() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    @AfterEach
    public void unsetProperties() {
        JiveGlobals.deleteProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME);
    }

    /**
     * Verifies that properties from a user named 'jane' is returned (from the secondary provider).
     */
    @Test
    public void testGetJane() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();

        // Execute system under test.
        final Map<String, String> result = provider.loadProperties("jane");

        // Verify results.
        assertNotNull(result);
    }

    /**
     * Verifies that properties from a user named 'john' is returned (from the tertiary provider).
     */
    @Test
    public void testGetJohn() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();

        // Execute system under test.
        final Map<String, String> result = provider.loadProperties("john");

        // Verify results.
        assertNotNull(result);

    }

    /**
     * Verifies that an exception is thrown when properties for non-existing user are being requested.
     */
    @Test()
    public void testGetNonExistingUser() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();

        // Execute system under test & Verify results.
        assertThrows(UserNotFoundException.class, () -> provider.loadProperties("non-existing-user"));
    }

    /**
     * Verifies a new property can be created.
     */
    @Test()
    public void testCreateProperty() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();

        // Execute system under test.
        provider.insertProperty("jane", "new property", "new property value");
        final String result = provider.loadProperty("jane", "new property");

        // Verify results
        assertEquals("new property value", result);
    }

    /**
     * Verifies a property can be deleted.
     */
    @Test()
    public void testDeleteProperty() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();
        provider.insertProperty("jane", "to delete property", "delete me");

        // Execute system under test.
        provider.deleteProperty("jane", "to delete property");

        // Verify results
        final String result = provider.loadProperty("jane", "new property");
        assertNull(result);
    }

    /**
     * Verifies a property can be deleted.
     */
    @Test()
    public void testUpdateProperty() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty(MappedUserPropertyProvider.PROPERTY_MAPPER_CLASSNAME, TestUserPropertyProviderMapper.class.getName());
        final MappedUserPropertyProvider provider = new MappedUserPropertyProvider();
        provider.insertProperty("jane", "to update property", "update me");

        // Execute system under test.
        provider.updateProperty("jane", "to update property", "updated");

        // Verify results
        final String result = provider.loadProperty("jane", "to update property");
        assertEquals("updated", result);
    }
}
