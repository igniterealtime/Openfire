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
package org.jivesoftware.admin;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies how the read-only section of the admin console's Certificate Stores page saves the cert.stores.readonly
 * setting: it sets the setting to what the checkbox shows, and reports a change only when the value changed, so that
 * the page writes an admin event only then.
 *
 * @author Matthew Vivian
 */
public class CertificateStoresReadOnlySettingTest
{
    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that saving a ticked checkbox while the setting is off turns the setting on, and reports a change.
     */
    @Test
    public void testSaveTickedWhenOffTurnsSettingOn()
    {
        // Execute system under test.
        final boolean changed = CertificateStoresReadOnlySetting.save( true );

        // Verify result.
        assertTrue( changed, "Saving a ticked checkbox while the setting is off must report a change." );
        assertTrue( CertificateStoreManager.READ_ONLY.getValue(), "Saving a ticked checkbox must turn the setting on." );
    }

    /**
     * Verifies that saving an unticked checkbox while the setting is on turns the setting off, and reports a change.
     */
    @Test
    public void testSaveUntickedWhenOnTurnsSettingOff()
    {
        // Setup test fixture.
        CertificateStoreManager.READ_ONLY.setValue( true );

        // Execute system under test.
        final boolean changed = CertificateStoresReadOnlySetting.save( false );

        // Verify result.
        assertTrue( changed, "Saving an unticked checkbox while the setting is on must report a change." );
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "Saving an unticked checkbox must turn the setting off." );
    }

    /**
     * Verifies that saving a ticked checkbox while the setting is already on leaves it on, and reports no change, so
     * that no admin event is written.
     */
    @Test
    public void testSaveTickedWhenOnReportsNoChange()
    {
        // Setup test fixture.
        CertificateStoreManager.READ_ONLY.setValue( true );

        // Execute system under test.
        final boolean changed = CertificateStoresReadOnlySetting.save( true );

        // Verify result.
        assertFalse( changed, "Saving the value the setting already has must not report a change, so that no admin event is written." );
        assertTrue( CertificateStoreManager.READ_ONLY.getValue(), "Saving a ticked checkbox must leave the setting on." );
    }

    /**
     * Verifies that saving an unticked checkbox while the setting is off (its default) leaves it off, and reports no
     * change, so that no admin event is written.
     */
    @Test
    public void testSaveUntickedWhenOffReportsNoChange()
    {
        // Execute system under test.
        final boolean changed = CertificateStoresReadOnlySetting.save( false );

        // Verify result.
        assertFalse( changed, "Saving the default value must not report a change, so that no admin event is written." );
        assertFalse( CertificateStoreManager.READ_ONLY.getValue(), "Saving an unticked checkbox must leave the setting off." );
    }
}
