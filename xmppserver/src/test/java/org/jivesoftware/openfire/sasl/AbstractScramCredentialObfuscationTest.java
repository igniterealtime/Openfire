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
import org.jivesoftware.util.SystemProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Abstract base for unit tests for fake key generation, salt handling and user enumeration protection any SCRAM SASL
 * mechanism.
 */
public abstract class AbstractScramCredentialObfuscationTest
{
    private MockedStatic<AuthFactory> authFactoryMock;

    // for the salt tests
    private static final String NON_EXISTENT_USER = "nonexistentuser";
    private static final String EXISTENT_USER = "existentuser";

    // for the fake key tests
    private static final String USERNAME1 = "fakeuser1";
    private static final String USERNAME2 = "fakeuser2";

    // test server secrets used for deterministic credential obfuscation
    private static final String SERVER_SECRET_1 = "test-secret-1234567890";
    private static final String SERVER_SECRET_2 = "another-secret-0987654321";

    /**
     * Returns the server secret property used to derive deterministic fake credentials.
     *
     * @return the non-existent user server secret property.
     */
    protected abstract SystemProperty<String> serverSecretProperty();

    /**
     * Returns the expected hash output length in bytes.
     *
     * @return the expected hash length.
     */
    protected abstract int expectedHashLengthBytes();

    /**
     * Returns the Base64-encoded salt value associated with an existing user in the SCRAM authentication process.
     *
     * @return the salt for an existing user as a {@link String}.
     */
    protected abstract String configuredUserSalt();

    /**
     * Creates a SCRAM SASL server instance for test execution.
     *
     * @return a configured {@link ScramSaslServer} instance.
     */
    @Nonnull
    protected abstract ScramSaslServer newServer();

    /**
     * Prepare the Openfire environment for unit tests.
     */
    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    /**
     * Set up mocks for each test.
     */
    @BeforeEach
    void setUp()
    {
        authFactoryMock = Mockito.mockStatic(AuthFactory.class);
        authFactoryMock.when(() -> AuthFactory.getSalt(EXISTENT_USER, ScramSha1SaslServer.MECHANISM_NAME)).thenReturn(configuredUserSalt());
        authFactoryMock.when(() -> AuthFactory.getSalt(NON_EXISTENT_USER, ScramSha1SaslServer.MECHANISM_NAME)).thenThrow(new UserNotFoundException(NON_EXISTENT_USER));
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

        // Prevent order-dependent failures by resetting the property after each test.
        serverSecretProperty().setValue(null);
    }

    /**
     * The fake key for a given username and secret should be deterministic.
     */
    @Test
    void fakeKeyIsDeterministicForSameInput()
    {
        // Setup test fixture
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] key1 = server.getOrFakeStoredKey(USERNAME1);
        final byte[] key2 = server.getOrFakeStoredKey(USERNAME1);

        // Verify result
        assertArrayEquals(key1, key2, "Fake key should be deterministic for same input and secret");
    }

    /**
     * Fake keys for different usernames should differ.
     */
    @Test
    void fakeKeysDifferForDifferentUsernames()
    {
        // Setup test fixture
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] key1 = server.getOrFakeStoredKey(USERNAME1);
        final byte[] key2 = server.getOrFakeStoredKey(USERNAME2);

        // Verify result
        assertArrayNotEquals(key1, key2, "Fake keys should differ for different usernames");
    }

    /**
     * Fake key should change if the server secret changes.
     */
    @Test
    void fakeKeyChangesWhenServerSecretChanges()
    {
        // Setup test fixture
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] key1 = server.getOrFakeStoredKey(USERNAME1);
        serverSecretProperty().setValue(SERVER_SECRET_2);
        final byte[] key2 = server.getOrFakeStoredKey(USERNAME1);

        // Verify result
        assertArrayNotEquals(key1, key2, "Fake key should change when server secret changes");
    }

    /**
     * Fake stored key should have a reasonable length (e.g., 20 bytes for HMAC-SHA1 output).
     */
    @Test
    void fakeStoredKeyHasExpectedLength()
    {
        // Setup test fixture
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] key = server.getOrFakeStoredKey(USERNAME1);

        // Verify result
        assertEquals(expectedHashLengthBytes(), key.length, "Fake stored key length should equal the configured hash output length");
    }

    /**
     * Stored and server keys should differ for the same username.
     */
    @Test
    void storedAndServerKeysDifferForSameUsername()
    {
        // Setup test fixture
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] stored = server.getOrFakeStoredKey(USERNAME1);
        final byte[] serverKey = server.getOrFakeServerKey(USERNAME1);

        assertArrayNotEquals(stored, serverKey, "Stored key and server key should differ for same username");
    }

    /**
     * The fake salt for a non-existent user should be deterministic (the same across calls).
     */
    @Test
    void fakeSaltIsDeterministicForNonExistentUser()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer();

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
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] fakeSalt = server.getOrCreateSalt(NON_EXISTENT_USER);
        final byte[] realSalt = server.getOrCreateSalt(EXISTENT_USER);

        // Verify result
        assertArrayNotEquals(fakeSalt, realSalt, "Fake salt for non-existent user should not match real user salt");
    }

    /**
     * The fake salt for a non-existent user should change if the server secret changes.
     */
    @Test
    void fakeSaltChangesWhenServerSecretChanges()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer();

        // Execute system under test
        serverSecretProperty().setValue(SERVER_SECRET_1);
        final byte[] salt1 = server.getOrCreateSalt(NON_EXISTENT_USER);
        serverSecretProperty().setValue(SERVER_SECRET_2);
        final byte[] salt2 = server.getOrCreateSalt(NON_EXISTENT_USER);

        // Verify result
        assertArrayNotEquals(salt1, salt2, "Fake salt should change when server secret changes");
    }

    /**
     * The salt for a real user should be consistent across calls.
     */
    @Test
    void realUserSaltIsConsistent()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer();

        // Execute system under test
        final byte[] salt1 = server.getOrCreateSalt(EXISTENT_USER);
        final byte[] salt2 = server.getOrCreateSalt(EXISTENT_USER);

        // Verify result
        assertArrayEquals(salt1, salt2, "Salt for real user should be consistent");
    }

    /**
     * Asserts that two byte arrays are not equal. The test will fail if the arrays are considered equal.
     *
     * @param expected the first byte array expected to hold a different value
     * @param actual the second byte array to compare with the expected array
     * @param message the message to display if the assertion fails
     */
    protected static void assertArrayNotEquals(byte[] expected, byte[] actual, String message)
    {
        assertFalse(Arrays.equals(expected, actual), message);
    }
}
