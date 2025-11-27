/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
     * Test decryption behaviour for structurally invalid ciphertext.
     *
     * IMPORTANT: Blowfish.decryptString() only returns null for STRUCTURAL issues:
     * - Invalid binhex characters
     * - Ciphertext too short (less than 16 hex chars / 8 bytes for the IV)
     *
     * For structurally valid but wrong-key ciphertext, decryption produces garbage,
     * not null. This is a fundamental limitation of Blowfish-CBC without integrity
     * verification (no MAC/HMAC).
     */
    @Test
    public void testDecryptReturnsNullForStructurallyInvalidCiphertext() {
        Encryptor encryptor = new Blowfish();

        // Structurally invalid inputs that should return null
        assertNull(encryptor.decrypt("not-valid-hex!@#$"),
            "Invalid hex characters should return null");
        assertNull(encryptor.decrypt("ABC"),
            "Too short ciphertext should return null");
        assertNull(encryptor.decrypt("0123456789ABCDEF"),
            "Only IV, no ciphertext should return empty or null");
    }

    /**
     * Test that decrypting with wrong key produces GARBAGE, not null.
     *
     * This documents an important limitation of Blowfish-CBC: there's no integrity
     * verification. Decrypting with the wrong key produces mojibake (garbage text)
     * rather than null or an exception.
     *
     * This has implications for migration: we cannot reliably detect wrong-key
     * decryption. The migration code checks for null (which catches structural
     * errors) but cannot detect key mismatches. Users MUST ensure proper backups.
     */
    @Test
    public void testDecryptWithWrongKeyProducesGarbageNotNull() {
        // Encrypt with one key
        Encryptor encryptor1 = new Blowfish("correct-key-12345");
        String plaintext = "sensitive-data-" + UUID.randomUUID();
        String ciphertext = encryptor1.encrypt(plaintext);
        assertNotNull(ciphertext, "Encryption should succeed");

        // Decrypt with a different key
        Encryptor encryptor2 = new Blowfish("wrong-key-67890");
        String decrypted = encryptor2.decrypt(ciphertext);

        // IMPORTANT: Wrong key produces garbage, NOT null
        // This is why backup verification is critical before migration
        assertNotNull(decrypted, "Wrong key produces garbage, not null");
        assertNotEquals(plaintext, decrypted, "Wrong key produces different (garbage) result");
    }

    /**
     * Test that documents the limitation of decryption failure detection.
     *
     * The migration code in JiveGlobals uses null-checking to detect SOME failures:
     * - Invalid binhex format -> returns null (DETECTABLE)
     * - Truncated ciphertext -> returns null (DETECTABLE)
     * - Wrong encryption key -> returns garbage (NOT DETECTABLE)
     * - Corrupted but valid hex -> returns garbage (NOT DETECTABLE)
     *
     * This test documents that proper database and security.xml backups are the
     * ONLY reliable protection against migration failures, as not all decryption
     * failures can be programmatically detected.
     */
    @Test
    public void testMigrationCanOnlyDetectStructuralDecryptionFailures() {
        Encryptor encryptor = new Blowfish("test-key");

        // Case 1: Structural failures ARE detectable (returns null)
        String structurallyInvalid = "GHIJ";  // Not valid hex
        assertNull(encryptor.decrypt(structurallyInvalid),
            "Structural failures should return null and be detectable");

        // Case 2: Wrong-key failures are NOT detectable (returns garbage)
        String plaintext = "test-value";
        String encrypted = encryptor.encrypt(plaintext);

        Encryptor wrongKeyEncryptor = new Blowfish("wrong-key");
        String wrongKeyResult = wrongKeyEncryptor.decrypt(encrypted);
        assertNotNull(wrongKeyResult,
            "Wrong-key decryption returns garbage, not null - NOT detectable");

        // This demonstrates why backups are essential for migration safety
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

    /**
     * Test that setBlowfishKdf() automatically reinitialises the encryptor cache.
     *
     * This test verifies the fix for the bug where newly encrypted properties
     * continued to use SHA1 key derivation after migration until restart.
     * The setBlowfishKdf() method should automatically refresh the cached
     * encryptor to use the updated KDF setting immediately.
     */
    @Test
    public void testSetBlowfishKdfReinitialises() {
        // Store original KDF setting to restore later
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // 1. Start with SHA1 KDF (legacy mode)
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);

            // 2. Encrypt a value using the current encryptor (SHA1)
            String testValue = "reinit-test-" + UUID.randomUUID();
            Encryptor sha1Encryptor = JiveGlobals.getPropertyEncryptor();
            String sha1Encrypted = sha1Encryptor.encrypt(testValue);

            // 3. Switch to PBKDF2 - this should automatically reinitialise the encryptor
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);

            // 4. Get the encryptor again - should now use PBKDF2
            Encryptor pbkdf2Encryptor = JiveGlobals.getPropertyEncryptor();
            String pbkdf2Encrypted = pbkdf2Encryptor.encrypt(testValue);

            // 5. The two encrypted values should be different because they use different KDFs
            // (SHA1 vs PBKDF2 produce different derived keys)
            assertNotEquals(sha1Encrypted, pbkdf2Encrypted,
                "Encrypted values should differ after KDF change because different KDFs are used");

            // 6. Verify the new encryptor can decrypt its own ciphertext
            String decrypted = pbkdf2Encryptor.decrypt(pbkdf2Encrypted);
            assertEquals(testValue, decrypted,
                "Encryptor should be able to decrypt values it encrypted after KDF change");

        } finally {
            // Restore original KDF (setBlowfishKdf automatically reinitialises)
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test that PBKDF2 encryption adds the $v2$ version prefix.
     * This makes encrypted values self-describing, allowing detection of
     * which KDF was used to encrypt them.
     */
    @Test
    public void testPBKDF2EncryptionAddsVersionPrefix() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Configure for PBKDF2
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);

            Blowfish blowfish = new Blowfish();
            String testValue = "prefix-test-" + UUID.randomUUID();

            String encrypted = blowfish.encryptString(testValue);

            // Verify the $v2$ prefix is present
            assertTrue(encrypted.startsWith(Blowfish.VERSION_PREFIX_PBKDF2),
                "PBKDF2-encrypted values should start with " + Blowfish.VERSION_PREFIX_PBKDF2);

            // Verify the prefix value is correct
            assertEquals("$v2$", Blowfish.VERSION_PREFIX_PBKDF2,
                "Version prefix constant should be '$v2$'");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test that SHA1 encryption does NOT add the version prefix.
     * Legacy SHA1-encrypted values remain unprefixed for backward compatibility.
     */
    @Test
    public void testSHA1EncryptionHasNoVersionPrefix() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Configure for SHA1 (legacy)
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);

            Blowfish blowfish = new Blowfish();
            String testValue = "sha1-test-" + UUID.randomUUID();

            String encrypted = blowfish.encryptString(testValue);

            // Verify no $v2$ prefix
            assertFalse(encrypted.startsWith(Blowfish.VERSION_PREFIX_PBKDF2),
                "SHA1-encrypted values should NOT have the $v2$ prefix");

            // SHA1-encrypted values start with hex characters (the CBC IV)
            assertTrue(encrypted.matches("^[0-9a-f]+$"),
                "SHA1-encrypted values should be pure hex");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test that decryption handles the $v2$ prefix correctly by stripping it.
     * Verifies round-trip encrypt/decrypt works when prefix is present.
     */
    @Test
    public void testDecryptionStripsVersionPrefix() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Configure for PBKDF2
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);

            Blowfish blowfish = new Blowfish();
            String testValue = "roundtrip-prefix-test-" + UUID.randomUUID();

            // Encrypt (will add $v2$ prefix)
            String encrypted = blowfish.encryptString(testValue);
            assertTrue(encrypted.startsWith(Blowfish.VERSION_PREFIX_PBKDF2),
                "Sanity check: encrypted value should have prefix");

            // Decrypt (should strip prefix and decrypt correctly)
            String decrypted = blowfish.decryptString(encrypted);
            assertEquals(testValue, decrypted,
                "Decryption should successfully handle $v2$ prefix");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test that decryption works for legacy values without prefix.
     * PBKDF2-configured Blowfish should still decrypt non-prefixed values
     * (though with wrong result if they were SHA1-encrypted - this tests structure only).
     */
    @Test
    public void testDecryptionHandlesLegacyNonPrefixedValues() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Start with SHA1 to create a non-prefixed value
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
            Blowfish sha1Blowfish = new Blowfish();
            String testValue = "legacy-test-" + UUID.randomUUID();
            String sha1Encrypted = sha1Blowfish.encryptString(testValue);

            // Verify no prefix
            assertFalse(sha1Encrypted.startsWith(Blowfish.VERSION_PREFIX_PBKDF2),
                "SHA1-encrypted value should not have prefix");

            // SHA1 Blowfish can decrypt its own output
            String decrypted = sha1Blowfish.decryptString(sha1Encrypted);
            assertEquals(testValue, decrypted,
                "SHA1 Blowfish should decrypt non-prefixed values correctly");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test isUsingPBKDF2() method returns correct value based on KDF setting.
     */
    @Test
    public void testIsUsingPBKDF2ReturnsCorrectValue() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Test with SHA1
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
            Blowfish sha1Blowfish = new Blowfish();
            assertFalse(sha1Blowfish.isUsingPBKDF2(),
                "isUsingPBKDF2() should return false when KDF is SHA1");

            // Test with PBKDF2
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
            Blowfish pbkdf2Blowfish = new Blowfish();
            assertTrue(pbkdf2Blowfish.isUsingPBKDF2(),
                "isUsingPBKDF2() should return true when KDF is PBKDF2");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test that setKey with explicit KDF parameter sets usingPBKDF2 correctly.
     */
    @Test
    public void testSetKeyWithExplicitKdfSetsUsingPBKDF2() {
        String originalKdf = JiveGlobals.getBlowfishKdf();

        try {
            // Ensure salt exists for PBKDF2
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
            JiveGlobals.getBlowfishSalt();

            // Now test explicit KDF parameter
            Blowfish blowfish = new Blowfish();

            blowfish.setKey("test-key", JiveGlobals.BLOWFISH_KDF_SHA1);
            assertFalse(blowfish.isUsingPBKDF2(),
                "setKey with SHA1 KDF should set isUsingPBKDF2() to false");

            blowfish.setKey("test-key", JiveGlobals.BLOWFISH_KDF_PBKDF2);
            assertTrue(blowfish.isUsingPBKDF2(),
                "setKey with PBKDF2 KDF should set isUsingPBKDF2() to true");

        } finally {
            JiveGlobals.setBlowfishKdf(originalKdf);
        }
    }

    /**
     * Test decryption of null returns null (defensive null handling).
     */
    @Test
    public void testDecryptNullReturnsNull() {
        Blowfish blowfish = new Blowfish();
        assertNull(blowfish.decryptString(null),
            "Decrypting null should return null");
    }

    /**
     * Test that version prefix format follows PHC String Format convention.
     * The $v2$ format is inspired by bcrypt, argon2, scrypt etc.
     */
    @Test
    public void testVersionPrefixFollowsPHCConvention() {
        String prefix = Blowfish.VERSION_PREFIX_PBKDF2;

        // PHC format: $<id>$ where id is alphanumeric
        assertTrue(prefix.startsWith("$"), "Prefix should start with $");
        assertTrue(prefix.endsWith("$"), "Prefix should end with $");
        assertEquals(4, prefix.length(), "Prefix '$v2$' should be 4 characters");

        // The '$' character is not valid hex, so prefix is unambiguous
        assertFalse(prefix.matches("^[0-9a-fA-F]+$"),
            "Prefix should NOT be valid hex to avoid confusion with ciphertext");
    }
}
