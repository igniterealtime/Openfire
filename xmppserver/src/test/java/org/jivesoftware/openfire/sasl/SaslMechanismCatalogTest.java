/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.Fixtures;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SaslMechanismCatalog}, which holds the SASL mechanisms that this server can offer.
 *
 * The catalog distinguishes three sets that narrow in order: the mechanisms that an administrator has enabled, those
 * for which an implementation is registered with the JVM, and those that are both and whose further requirements this
 * deployment meets. Conflating any two of them would cause a mechanism to be advertised that no peer can complete, or
 * withheld from one that could, so several of these tests exist to keep that distinction honest.
 *
 * The fixture configuration names BLURDYBLOOP, which nothing implements, alongside TEST-MECHANISM, which is
 * implemented by a provider registered for the duration of each test. Between them they make every pairing of the
 * three sets observable.
 *
 * The catalog's state is static and several of the methods under test mutate it, so the registry is rebuilt from the
 * fixture before every test rather than once for the class.
 */
public class SaslMechanismCatalogTest
{
    private static final List<String> FIXTURE_MECHANISMS = Arrays.asList("BLURDYBLOOP", "TEST-MECHANISM");

    private static List<String> originalEnabledMechanisms;

    @BeforeAll
    public static void setupClass() throws Exception
    {
        CacheFactory.initialize();
        JiveGlobals.setXMLProperty("setup", "true");

        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        originalEnabledMechanisms = new ArrayList<>(SaslMechanismCatalog.getEnabledMechanisms());
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
        SaslMechanismCatalog.setEnabledMechanisms(originalEnabledMechanisms);
    }

    @BeforeEach
    public void setup()
    {
        // Rebuilds the registry from the property, so that a test which adds or removes a mechanism cannot affect the
        // next one.
        SaslMechanismCatalog.setEnabledMechanisms(FIXTURE_MECHANISMS);
        TestSaslMechanism.registerTestMechanism(null);
    }

    @AfterEach
    public void tearDown()
    {
        // The provider is registered with the JVM's security context, so leaving it in place would make
        // TEST-MECHANISM visible to every test that runs afterwards.
        TestSaslMechanism.unregisterTestMechanism();
    }

    /**
     * A mechanism supplied by a registered provider must be reported as implemented, and, being enabled as well, as
     * supported.
     */
    @Test
    public void testRegisteredSaslProvider() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        Set<String> implemented = SaslMechanismCatalog.getImplementedMechanisms();
        Set<String> enabled = SaslMechanismCatalog.getSupportedMechanisms();

        // Verify result.
        assertNotNull(implemented);
        assertFalse(implemented.isEmpty());
        assertTrue(implemented.contains("TEST-MECHANISM"));
        assertNotNull(enabled);
        assertFalse(enabled.isEmpty());
        assertTrue(enabled.contains("TEST-MECHANISM"));
    }

    /**
     * Mechanisms can be added at runtime. Their names are normalized to upper case, which is the form that inbound
     * mechanism names are compared in.
     */
    @Test
    public void testAddSupportedMechanism() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        SaslMechanismCatalog.addSupportedMechanism("PLAIN");
        SaslMechanismCatalog.addSupportedMechanism("digest-md5");

        // Verify result.
        assertTrue(SaslMechanismCatalog.getSupportedMechanisms().contains("PLAIN"));
        assertTrue(SaslMechanismCatalog.getSupportedMechanisms().contains("DIGEST-MD5"));
        assertThrows(IllegalArgumentException.class, () -> {
            SaslMechanismCatalog.addSupportedMechanism(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            SaslMechanismCatalog.addSupportedMechanism("");
        });
    }

    /**
     * Mechanisms can be removed at runtime, matched irrespective of case. Removing one that was never added is not an
     * error, but removing nothing at all is.
     */
    @Test
    public void testRemoveSupportedMechanism() {
        // Setup test fixture.
        SaslMechanismCatalog.addSupportedMechanism("PLAIN");
        SaslMechanismCatalog.addSupportedMechanism("DIGEST-MD5");

        // Execute system under test.
        SaslMechanismCatalog.removeSupportedMechanism("PLAIN");
        SaslMechanismCatalog.removeSupportedMechanism("digest-md5");

        // Verify result.
        assertFalse(SaslMechanismCatalog.getSupportedMechanisms().contains("PLAIN"), "Unsupported PLAIN mechanism should be removed");
        assertFalse(SaslMechanismCatalog.getSupportedMechanisms().contains("DIGEST-MD5"), "Unsupported DIGEST-MD5 mechanism should be removed");
        SaslMechanismCatalog.removeSupportedMechanism("NONEXISTENT"); // Should not throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            SaslMechanismCatalog.removeSupportedMechanism(null);
        }, "Null mechanism should not be allowed");
    }

    /**
     * The supported set reflects mechanisms that have been added at runtime.
     */
    @Test
    public void testGetSupportedMechanisms() {
        // Setup test fixture.
        SaslMechanismCatalog.addSupportedMechanism("PLAIN");
        SaslMechanismCatalog.addSupportedMechanism("DIGEST-MD5");

        // Execute system under test.
        Set<String> mechanisms = SaslMechanismCatalog.getSupportedMechanisms();

        // Verify result.
        assertNotNull(mechanisms, "Supported mechanisms should not be null");
        assertTrue(mechanisms.contains("PLAIN"), "PLAIN mechanism should be supported");
        assertTrue(mechanisms.contains("DIGEST-MD5"), "DIGEST-MD5 mechanism should be supported");
    }

    /**
     * The enabled set is whatever configuration names, verbatim, without regard to whether anything implements it.
     */
    @Test
    public void testGetEnabledMechanisms() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        List<String> enabled = SaslMechanismCatalog.getEnabledMechanisms();

        // Verify result.
        assertNotNull(enabled, "Enabled mechanisms should not be null");
        assertFalse(enabled.isEmpty(), "Enabled mechanisms should not be empty");
        assertTrue(enabled.contains("BLURDYBLOOP"), "BLURDYBLOOP mechanism should be enabled");
        assertTrue(enabled.contains("TEST-MECHANISM"), "TEST-MECHANISM mechanism should be enabled");
    }

    /**
     * The implemented set is whatever the JVM's registered providers supply, without regard to configuration.
     */
    @Test
    public void testGetImplementedMechanisms() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        Set<String> implemented = SaslMechanismCatalog.getImplementedMechanisms();

        // Verify result.
        assertNotNull(implemented, "Implemented mechanisms should not be null");
        assertFalse(implemented.isEmpty(), "Implemented mechanisms should not be empty");
        assertTrue(implemented.contains("PLAIN"), "PLAIN mechanism should be implemented");
        assertTrue(implemented.contains("DIGEST-MD5"), "DIGEST-MD5 mechanism should be implemented");
        assertFalse(implemented.contains("BLURDYBLOOP"), "BLURDYBLOOP mechanism should not be implemented");
        assertTrue(implemented.contains("TEST-MECHANISM"), "TEST-MECHANISM mechanism should be implemented");
    }

    /**
     * A mechanism that configuration enables but that nothing implements must not be offered.
     *
     * Advertising a mechanism for which no {@link javax.security.sasl.SaslServer} can be created would leave a peer
     * that selects it unable to authenticate at all.
     */
    @Test
    public void testEnabledButUnimplementedMechanismIsNotSupported() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        boolean enabled = SaslMechanismCatalog.isEnabled("BLURDYBLOOP");
        Set<String> implemented = SaslMechanismCatalog.getImplementedMechanisms();
        Set<String> supported = SaslMechanismCatalog.getSupportedMechanisms();

        // Verify result.
        assertTrue(enabled, "BLURDYBLOOP is named by configuration, so it must be reported as enabled.");
        assertFalse(implemented.contains("BLURDYBLOOP"), "Test setup issue: BLURDYBLOOP must not be implemented for this test to prove anything.");
        assertFalse(supported.contains("BLURDYBLOOP"), "A mechanism that nothing implements must not be reported as supported, or it would be advertised to peers that cannot complete it.");
    }

    /**
     * A mechanism that nothing enables must not be offered, however widely the JVM implements it.
     */
    @Test
    public void testImplementedButUnenabledMechanismIsNotSupported() {
        // Setup test fixture: PLAIN is implemented by the JVM, but the fixture configuration does not name it.
        // (no additional setup required)

        // Execute system under test.
        boolean enabled = SaslMechanismCatalog.isEnabled("PLAIN");
        Set<String> implemented = SaslMechanismCatalog.getImplementedMechanisms();
        Set<String> supported = SaslMechanismCatalog.getSupportedMechanisms();

        // Verify result.
        assertTrue(implemented.contains("PLAIN"), "Test setup issue: PLAIN must be implemented for this test to prove anything.");
        assertFalse(enabled, "PLAIN is not named by the fixture configuration, so it must not be reported as enabled.");
        assertFalse(supported.contains("PLAIN"), "A mechanism that configuration does not allow must not be reported as supported.");
    }

    /**
     * The enabled check compares against the normalized names that the registry holds, so it expects the upper-cased
     * form that inbound mechanism names are converted to before they reach it.
     */
    @Test
    public void testIsEnabledExpectsNormalizedNames() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test & verify result.
        assertTrue(SaslMechanismCatalog.isEnabled("TEST-MECHANISM"), "An enabled mechanism must be recognized by its normalized name.");
        assertFalse(SaslMechanismCatalog.isEnabled("test-mechanism"), "The check is against normalized names; callers upper-case before reaching it.");
        assertFalse(SaslMechanismCatalog.isEnabled("NOT-A-MECHANISM"), "A mechanism that configuration does not name must not be reported as enabled.");
    }

    /**
     * Changing the configured mechanisms replaces the registry rather than adding to it, so that a mechanism an
     * administrator has removed stops being offered.
     */
    @Test
    public void testSettingEnabledMechanismsReplacesTheRegistry() {
        // Setup test fixture.
        assertTrue(SaslMechanismCatalog.isEnabled("TEST-MECHANISM"), "Test setup issue: expected the fixture mechanism to be enabled.");

        // Execute system under test.
        SaslMechanismCatalog.setEnabledMechanisms(List.of("PLAIN"));

        // Verify result.
        assertTrue(SaslMechanismCatalog.isEnabled("PLAIN"), "The newly configured mechanism must be enabled.");
        assertFalse(SaslMechanismCatalog.isEnabled("TEST-MECHANISM"), "A mechanism that is no longer configured must stop being enabled, rather than lingering in the registry.");
    }
}
