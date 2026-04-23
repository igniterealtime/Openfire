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
import org.junit.jupiter.api.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for fake key generation in {@link ScramSha1SaslServer}.
 */
public class ScramSha1SaslServerFakeKeyTest
{
    private static final String USERNAME1 = "fakeuser1";
    private static final String USERNAME2 = "fakeuser2";
    private static final String SERVER_SECRET_1 = "test-secret-1234567890";
    private static final String SERVER_SECRET_2 = "another-secret-0987654321";

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
     * The fake key for a given username and secret should be deterministic.
     */
    @Test
    void fakeKeyIsDeterministicForSameInput()
    {
        // Setup test fixture
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_1);
        final ScramSha1SaslServer server = new ScramSha1SaslServer();

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
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_1);
        final ScramSha1SaslServer server = new ScramSha1SaslServer();

        // Execute system under test
        final byte[] key1 = server.getOrFakeStoredKey(USERNAME1);
        final byte[] key2 = server.getOrFakeStoredKey(USERNAME2);

        // Verify result
        assertFalse(Arrays.equals(key1, key2), "Fake keys should differ for different usernames");
    }

    /**
     * Fake key should change if the server secret changes.
     */
    @Test
    void fakeKeyChangesWhenServerSecretChanges()
    {
        // Setup test fixture
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_1);
        final ScramSha1SaslServer server = new ScramSha1SaslServer();

        // Execute system under test
        final byte[] key1 = server.getOrFakeStoredKey(USERNAME1);
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_2);
        final byte[] key2 = server.getOrFakeStoredKey(USERNAME1);

        // Verify result
        assertFalse(Arrays.equals(key1, key2), "Fake key should change when server secret changes");
    }

    /**
     * Fake key should have a reasonable length (e.g., 20 bytes for HMAC-SHA1 output).
     */
    @Test
    void fakeKeyHasExpectedLength()
    {
        // Setup test fixture
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_1);
        final ScramSha1SaslServer server = new ScramSha1SaslServer();

        // Execute system under test
        final byte[] key = server.getOrFakeStoredKey(USERNAME1);

        // Verify result (HMAC-SHA1 output is 20 bytes)
        assertEquals(20, key.length, "Fake key should be 20 bytes (SHA-1 hash length)");
    }

    /**
     * Stored and server keys should differ for the same username.
     */
    @Test
    void storedAndServerKeysDifferForSameUsername()
    {
        // Setup test fixture
        ScramSha1SaslServer.SERVER_SECRET_NONEXISTING_USERS.setValue(SERVER_SECRET_1);
        final ScramSha1SaslServer server = new ScramSha1SaslServer();

        // Execute system under test
        final byte[] stored = server.getOrFakeStoredKey(USERNAME1);
        final byte[] serverKey = server.getOrFakeServerKey(USERNAME1);

        assertFalse(Arrays.equals(stored, serverKey), "Stored key and server key should differ for same username");
    }
}

