/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BlowfishEncryptorTest {

    @Test
    public void testEncryptionUsingDefaultKey() {
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new Blowfish();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertNotEquals(test, b64Encrypted);
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionUsingCustomKey() {
        
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new Blowfish(UUID.randomUUID().toString());
        
        String b64Encrypted = encryptor.encrypt(test);
        assertNotEquals(test, b64Encrypted);
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionForEmptyString() {
        
        String test = "";
        
        Encryptor encryptor = new Blowfish();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertNotEquals(test, b64Encrypted);
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }


    @Test
    public void testEncryptionForNullString() {
        Encryptor encryptor = new Blowfish();

        String b64Encrypted = encryptor.encrypt(null);

        assertNull(b64Encrypted);
    }

    /**
     * Test PBKDF2 key derivation produces deterministic results.
     * Same password + same salt should produce the same derived key.
     */
    @Test
    public void testPBKDF2KeyDerivationIsDeterministic() throws Exception {
        byte[] salt = new byte[32];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(salt);

        byte[] key1 = Blowfish.deriveKeyPBKDF2("testPassword", salt);
        byte[] key2 = Blowfish.deriveKeyPBKDF2("testPassword", salt);

        assertArrayEquals(key1, key2, "Same password and salt should produce identical keys");
    }

    /**
     * Test PBKDF2 key derivation produces different keys for different salts.
     * This prevents rainbow table attacks.
     */
    @Test
    public void testPBKDF2KeyDerivationUsesSalt() throws Exception {
        byte[] salt1 = new byte[32];
        byte[] salt2 = new byte[32];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(salt1);
        random.nextBytes(salt2);

        byte[] key1 = Blowfish.deriveKeyPBKDF2("testPassword", salt1);
        byte[] key2 = Blowfish.deriveKeyPBKDF2("testPassword", salt2);

        assertFalse(java.util.Arrays.equals(key1, key2),
            "Different salts should produce different keys even with same password");
    }

    /**
     * Test backward compatibility: default configuration uses SHA1 KDF.
     * This ensures existing Openfire installations continue to work without
     * requiring migration of encrypted properties.
     */
    @Test
    public void testDefaultKdfIsSHA1ForBackwardCompatibility() {
        String kdf = JiveGlobals.getBlowfishKdf();
        assertEquals("sha1", kdf, "Default KDF should be SHA1 for backward compatibility");
    }

    /**
     * Test backward compatibility: encryption and decryption works with SHA1 KDF.
     * This verifies that the legacy key derivation method continues to function,
     * allowing existing encrypted properties to be read.
     */
    @Test
    public void testBackwardCompatibilityWithSHA1Encryption() {
        // Verify we're using SHA1 (the legacy/default mode)
        String kdf = JiveGlobals.getBlowfishKdf();
        assertEquals("sha1", kdf, "Test assumes SHA1 as default KDF");

        // Test encrypt/decrypt cycle with SHA1 (legacy behaviour)
        String testValue = "backward-compatibility-test-" + UUID.randomUUID();
        Encryptor encryptor = new Blowfish();

        String encrypted = encryptor.encrypt(testValue);
        assertNotNull(encrypted, "Encryption should succeed with SHA1 KDF");
        assertNotEquals(testValue, encrypted, "Encrypted value should differ from plaintext");

        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(testValue, decrypted, "Decryption should recover original value with SHA1 KDF");
    }

    /**
     * Test end-to-end encryption and decryption using PBKDF2 KDF.
     * This verifies the full integration path: salt generation, KDF configuration,
     * and the complete encrypt/decrypt cycle with PBKDF2-HMAC-SHA512.
     */
    @Test
    public void testEncryptionWithPBKDF2KDF() {
        // Store original KDF setting to restore later
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Configure Openfire to use PBKDF2 key derivation
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);

            // Ensure a salt is generated (getBlowfishSalt auto-generates if not present)
            String salt = JiveGlobals.getBlowfishSalt();
            assertNotNull(salt, "Salt should be generated");
            assertFalse(salt.trim().isEmpty(), "Salt should not be empty");

            // Test encrypt/decrypt cycle with PBKDF2
            String testValue = "pbkdf2-test-" + UUID.randomUUID();
            Encryptor encryptor = new Blowfish();

            String encrypted = encryptor.encrypt(testValue);
            assertNotNull(encrypted, "Encryption should succeed with PBKDF2 KDF");
            assertNotEquals(testValue, encrypted, "Encrypted value should differ from plaintext");

            String decrypted = encryptor.decrypt(encrypted);
            assertEquals(testValue, decrypted, "Decryption should recover original value with PBKDF2 KDF");

        } finally {
            // Restore original KDF setting to avoid affecting other tests
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }
}
