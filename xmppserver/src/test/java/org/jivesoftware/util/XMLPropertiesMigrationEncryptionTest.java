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
package org.jivesoftware.util;

import org.jivesoftware.Fixtures;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These tests intentionally exercise {@link JiveGlobals#migrateProperty(String)} with encryption markers coming from
 * security configuration.
 */
public class XMLPropertiesMigrationEncryptionTest
{
    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    @AfterEach
    public void resetProperties() throws Exception
    {
        Fixtures.clearExistingProperties();
        setPrivateStaticField(JiveGlobals.class, "securityProperties", null);
        setPrivateStaticField(JiveGlobals.class, "propertyEncryptor", null);
        setPrivateStaticField(JiveGlobals.class, "propertyEncryptorNew", null);
        setPrivateStaticField(JiveGlobals.class, "currentKey", null);
    }

    /**
     * Asserts that when migrating an XML property, not yet stored in the database, is encrypted, its encryption state
     * is persisted.
     *
     * @see <a href="https://issues.igniterealtime.org/browse/OF-3296">OF-3296: Encrypted XML properties can lose encryption during database migration</a>
     */
    @Test
    public void migrateEncryptedXmlOnlyPropertyPreservesEncryption() throws Exception
    {
        // Setup test fixture.
        final String propertyName = "of3175.password";
        final String propertyValue = "s3cr3t";

        configureEncryptedSecurityProperties(propertyName);
        JiveGlobals.setXMLProperty(propertyName, propertyValue);

        assertTrue(JiveGlobals.isXMLPropertyEncrypted(propertyName), "Unable to prepare test fixture: encrypted XML property must be set.");
        assertNull(JiveGlobals.getProperty(propertyName), "Unable to prepare test fixture: encrypted XML property must not be set in DB.");

        // Execute system under test.
        JiveGlobals.migrateProperty(propertyName);

        // Verify results.
        assertEquals(propertyValue, JiveGlobals.getProperty(propertyName), "Expected the migrated property to have the same value as the original (but it did not).");
        assertTrue(JiveGlobals.isPropertyEncrypted(propertyName), "Regression for OF-3296: migrated encrypted XML property must end up being encrypted in DB.");
    }

    /**
     * Asserts that when migrating an XML property, already stored in the database, is encrypted, its encryption state
     * (as defined by the XML property) is persisted.
     *
     * @see <a href="https://issues.igniterealtime.org/browse/OF-3296">OF-3296: Encrypted XML properties can lose encryption during database migration</a>
     */
    @Test
    public void migrateEncryptedDuplicatePropertyPreservesEncryption() throws Exception
    {
        // Setup test fixture.
        final String propertyName = "of3175.duplicate.password";
        final String propertyValue = "same-value";

        configureEncryptedSecurityProperties(propertyName);
        JiveGlobals.setXMLProperty(propertyName, propertyValue); // duplicate XML value
        JiveGlobals.setProperty(propertyName, propertyValue); // existing DB value

        assertTrue(JiveGlobals.isXMLPropertyEncrypted(propertyName), "Unable to prepare test fixture: encrypted XML property must be set.");
        assertFalse(JiveGlobals.isPropertyEncrypted(propertyName), "Unable to prepare test fixture: property must not (yet) be defined as 'encrypted' in DB.");

        // Execute system under test.
        JiveGlobals.migrateProperty(propertyName);

        // Verify results.
        assertEquals(propertyValue, JiveGlobals.getProperty(propertyName), "Expected the migrated property to have the same value as the original (but it did not).");
        assertTrue(JiveGlobals.isPropertyEncrypted(propertyName), "Regression for OF-3296: migrated encrypted XML property must end up being encrypted in DB.");
    }

    private static void configureEncryptedSecurityProperties(String... encryptedPropertyNames) throws Exception
    {
        final StringBuilder xml = new StringBuilder("<root><encrypt><property>");
        for (final String propertyName : encryptedPropertyNames) {
            xml.append("<name>").append(propertyName).append("</name>");
        }
        xml.append("</property></encrypt></root>");

        final XMLProperties security = XMLProperties.getNonPersistedInstance(
            new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8))
        );
        setPrivateStaticField(JiveGlobals.class, "securityProperties", security);
    }

    private static void setPrivateStaticField(Class<?> clazz, String fieldName, Object value) throws Exception
    {
        final Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}


