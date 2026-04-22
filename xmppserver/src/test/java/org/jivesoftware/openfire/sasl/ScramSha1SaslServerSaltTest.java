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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.channelbinding.ChannelBindingProviderManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for salt handling and user enumeration protection in {@link ScramSha1SaslServer}.
 */
public class ScramSha1SaslServerSaltTest
{
    private static final String NON_EXISTENT_USER = "nonexistentuser";
    private static final String EXISTENT_USER = "existentuser";
    private static final String EXISTENT_SALT_BASE64 = "QSXCR+Q6sek8bf92";
    private MockedStatic<AuthFactory> authFactoryMock;

    /**
     * Prepare the Openfire environment for unit tests.
     */
    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterEach
    public void resetProperty() throws Exception
    {
        // Prevent order-dependent failures by resetting the property after each test.
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTENT_USERS.setValue(null);
    }

    /**
     * Set up mocks for each test.
     */
    @BeforeEach
    void setUp()
    {
        // Now mock AuthFactory
        authFactoryMock = Mockito.mockStatic(AuthFactory.class);

        // Simulate existent user
        authFactoryMock.when(() -> AuthFactory.getSalt(EXISTENT_USER)).thenReturn(EXISTENT_SALT_BASE64);

        // Simulate non-existent user
        authFactoryMock.when(() -> AuthFactory.getSalt(NON_EXISTENT_USER)).thenThrow(new UserNotFoundException(NON_EXISTENT_USER));
    }

    /**
     * Clean up mocks after each test.
     */
    @AfterEach
    void tearDown()
    {
        if (authFactoryMock != null) {
            authFactoryMock.close();
        }
    }

    /**
     * The fake salt for a non-existent user should be deterministic (the same across calls).
     */
    @Test
    void fakeSaltIsDeterministicForNonExistentUser()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final byte[] salt1 = server.getOrCreateSalt(NON_EXISTENT_USER);
        final byte[] salt2 = server.getOrCreateSalt(NON_EXISTENT_USER);

        // Verify result
        assertArrayEquals(salt1, salt2, "Fake salt for non-existent user should be deterministic");
    }

    /**
     * The fake salt for a non-existent user should not match the salt for a real user.
     */
    @Test
    void fakeSaltIsDifferentFromRealUserSalt()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final byte[] fakeSalt = server.getOrCreateSalt(NON_EXISTENT_USER);
        final byte[] realSalt = server.getOrCreateSalt(EXISTENT_USER);

        // Verify result
        assertFalse(Arrays.equals(fakeSalt, realSalt), "Fake salt for non-existent user should not match real user salt");
    }

    /**
     * The fake salt for a non-existent user should change if the server secret changes.
     */
    @Test
    void fakeSaltChangesWhenServerSecretChanges()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final byte[] salt1 = server.getOrCreateSalt(NON_EXISTENT_USER);
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTENT_USERS.setValue("modified" + ScramSha1SaslServer.SERVER_SECRET_NONEXISTENT_USERS.getValue());
        final byte[] salt2 = server.getOrCreateSalt(NON_EXISTENT_USER);

        // Verify result
        assertFalse(Arrays.equals(salt1, salt2), "Fake salt should change when server secret changes");
    }

    /**
     * The salt for a real user should be consistent across calls.
     */
    @Test
    void realUserSaltIsConsistent()
    {
        // Setup test fixture
        final ScramSha1SaslServer server = new ScramSha1SaslServer(false, new HashMap<>(), new ChannelBindingProviderManager(), Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Execute system under test
        final byte[] salt1 = server.getOrCreateSalt(EXISTENT_USER);
        final byte[] salt2 = server.getOrCreateSalt(EXISTENT_USER);

        // Verify result
        assertArrayEquals(salt1, salt2, "Salt for real user should be consistent");
    }
}
