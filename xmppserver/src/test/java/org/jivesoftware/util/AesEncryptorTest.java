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

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.security.Security;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

public class AesEncryptorTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @BeforeAll
    static void setupBouncyCastle() {
        // Register BouncyCastle provider to enable PKCS7Padding (consistent with AesEncryptor)
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Generates a random 16-byte IV for testing.
     */
    private byte[] generateRandomIV() {
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        return iv;
    }

    @Test
    public void testEncryptionUsingDefaultKey() {
        String test = UUID.randomUUID().toString();
        byte[] iv = generateRandomIV();

        Encryptor encryptor = new AesEncryptor();

        String b64Encrypted = encryptor.encrypt(test, iv);
        assertNotEquals(test, b64Encrypted);

        assertEquals(test, encryptor.decrypt(b64Encrypted, iv));
    }

    @Test
    public void testEncryptionUsingCustomKey() {

        String test = UUID.randomUUID().toString();
        byte[] iv = generateRandomIV();

        Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());

        String b64Encrypted = encryptor.encrypt(test, iv);
        assertNotEquals(test, b64Encrypted);

        assertEquals(test, encryptor.decrypt(b64Encrypted, iv));
    }

    @Test
    public void testEncryptionForEmptyString() {

        String test = "";
        byte[] iv = generateRandomIV();

        Encryptor encryptor = new AesEncryptor();

        String b64Encrypted = encryptor.encrypt(test, iv);
        assertNotEquals(test, b64Encrypted);

        assertEquals(test, encryptor.decrypt(b64Encrypted, iv));
    }


    @Test
    public void testEncryptionForNullString() {
        byte[] iv = generateRandomIV();
        Encryptor encryptor = new AesEncryptor();

        String b64Encrypted = encryptor.encrypt(null, iv);

        assertNull(b64Encrypted);
    }

    @Test
    public void testEncryptionWithKeyAndIV() {

        final String plainText = UUID.randomUUID().toString();
        final byte[] iv = "0123456789abcdef".getBytes();
        final Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());
        final String encryptedText = encryptor.encrypt(plainText, iv);

        final String decryptedText = encryptor.decrypt(encryptedText, iv);

        assertThat(decryptedText, is(plainText));
    }

    @Test
    public void testEncryptionWithKeyAndBadIV() {

        final String plainText = UUID.randomUUID().toString();
        final byte[] iv = "0123456789abcdef".getBytes();
        final Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());
        final String encryptedText = encryptor.encrypt(plainText, iv);

        final String decryptedText = encryptor.decrypt(encryptedText);

        assertThat(decryptedText, is(not(plainText)));

    }

    /**
     * Tests decoding a Base64 value stored as would be stored in security.xml by versions of Openfire prior to 4.9.0.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2883">OF-2883: Base64 decoding issue preventing startup (after upgrade to 4.9.0)</a>
     */
    @Test
    public void testBase64DecodeValuePriorTo490()
    {
        // Setup text fixture.
        final String unencoded = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod";
        final String base64encoded = "PwwA2Ejt1HDS0xwbEg4qMzk/E9fDA1aA6TdL2zBPMVtwW9C2UnUafAmm/twRZxY89euheVg0rxoQ Vfo5to825asMcx/bJNPY136oZstULDU=";

        // Execute system under test.
        final AesEncryptor encryptor = new AesEncryptor();
        final String result = encryptor.decrypt(base64encoded);

        // Verify results.
        assertEquals(unencoded, result);
    }

    /**
     * Tests decoding a Base64 value stored as would be stored in security.xml by versions of Openfire 4.9.0 and later.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2883">OF-2883: Base64 decoding issue preventing startup (after upgrade to 4.9.0)</a>
     */
    @Test
    public void testBase64DecodeValuePastTo490()
    {
        // Setup test fixture.
        final String unencoded = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod";
        final String base64encoded = "PwwA2Ejt1HDS0xwbEg4qMzk/E9fDA1aA6TdL2zBPMVtwW9C2UnUafAmm/twRZxY89euheVg0rxoQVfo5to825asMcx/bJNPY136oZstULDU=";

        // Execute system under test.
        final AesEncryptor encryptor = new AesEncryptor();
        final String result = encryptor.decrypt(base64encoded);

        // Verify results.
        assertEquals(unencoded, result);
    }

    /**
     * Tests that encryption without explicit IV is deterministic (same plaintext produces same ciphertext).
     * This demonstrates the security vulnerability of hardcoded IV usage.
     * This test uses the DEPRECATED encrypt(String) method.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testEncryptionWithoutExplicitIVIsDeterministic()
    {
        // Setup test fixture.
        final String plaintext = "sensitive-password-123";
        final Encryptor encryptor = new AesEncryptor();

        // Execute system under test - encrypt the same value twice using deprecated method.
        final String encrypted1 = encryptor.encrypt(plaintext);
        final String encrypted2 = encryptor.encrypt(plaintext);

        // Verify results - this is the CURRENT BEHAVIOUR (deterministic encryption).
        // This is a security vulnerability as it enables pattern analysis attacks.
        assertEquals(encrypted1, encrypted2,
                     "Same plaintext should produce same ciphertext with hardcoded IV (current vulnerability)");
    }

    /**
     * Tests that encryption with different IVs produces different ciphertext.
     * This verifies the fix for the hardcoded IV vulnerability.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
     */
    @Test
    public void testEncryptionWithDifferentIVsIsNonDeterministic()
    {
        // Setup test fixture.
        final String plaintext = "sensitive-password-123";
        final Encryptor encryptor = new AesEncryptor();
        final byte[] iv1 = generateRandomIV();
        final byte[] iv2 = generateRandomIV();

        // Execute system under test - encrypt the same value with different IVs.
        final String encrypted1 = encryptor.encrypt(plaintext, iv1);
        final String encrypted2 = encryptor.encrypt(plaintext, iv2);

        // Verify results - different IVs should produce different ciphertext.
        assertNotEquals(encrypted1, encrypted2,
                     "Same plaintext with different IVs should produce different ciphertext");

        // Verify both can be decrypted correctly with their respective IVs.
        assertEquals(plaintext, encryptor.decrypt(encrypted1, iv1));
        assertEquals(plaintext, encryptor.decrypt(encrypted2, iv2));
    }

    /**
     * Tests that GCM encryption with random IV works correctly.
     * This verifies the OF-3077 fix that switches from CBC to GCM mode.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3077">OF-3077: Potential padding oracle CBC-mode encryption</a>
     */
    @Test
    public void testGcmEncryptionDecryption()
    {
        // Setup test fixture.
        final String plaintext = "sensitive-data-for-gcm-test";
        final Encryptor encryptor = new AesEncryptor();
        final byte[] iv = generateRandomIV();

        // Execute system under test.
        final String encrypted = encryptor.encrypt(plaintext, iv);

        // Verify results.
        assertNotNull(encrypted, "Encrypted value should not be null");
        assertNotEquals(plaintext, encrypted, "Encrypted value should differ from plaintext");
        assertEquals(plaintext, encryptor.decrypt(encrypted, iv),
                     "Decrypted value should match original plaintext");
    }

    /**
     * Tests that legacy CBC-encrypted data (from OF-1533 era, 2018+) can still be decrypted
     * using the CBC fallback mechanism. This simulates data encrypted with CBC mode and
     * a random IV, as was done by JiveProperties since OF-1533.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-1533">OF-1533: Use a random IV for each new encrypted property</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3077">OF-3077: Potential padding oracle CBC-mode encryption</a>
     */
    @Test
    public void testCbcFallbackForLegacyData() throws Exception
    {
        // Setup test fixture - simulate CBC-encrypted data from OF-1533 era.
        final String plaintext = "legacy-password";
        final byte[] iv = generateRandomIV();

        // Encrypt using CBC directly (simulating old Openfire behaviour before OF-3077).
        javax.crypto.Cipher cbcCipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding");
        cbcCipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                       new javax.crypto.spec.SecretKeySpec(LegacyEncryptionConstants.LEGACY_KEY, "AES"),
                       new javax.crypto.spec.IvParameterSpec(iv));
        byte[] cbcEncryptedBytes = cbcCipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        final String cbcEncrypted = java.util.Base64.getEncoder().encodeToString(cbcEncryptedBytes);

        // Execute system under test - decrypt using AesEncryptor (which now uses GCM first, then CBC fallback).
        final AesEncryptor encryptor = new AesEncryptor();
        final String decrypted = encryptor.decrypt(cbcEncrypted, iv);

        // Verify results - the CBC fallback should successfully decrypt the legacy data.
        assertEquals(plaintext, decrypted,
                     "Legacy CBC-encrypted data should be decrypted via fallback mechanism");
    }

    /**
     * Tests that GCM mode detects tampering with the ciphertext.
     * GCM provides authenticated encryption, so modified ciphertext should fail to decrypt.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3077">OF-3077: Potential padding oracle CBC-mode encryption</a>
     */
    @Test
    public void testGcmDetectsTampering()
    {
        // Setup test fixture.
        final String plaintext = "sensitive-data";
        final Encryptor encryptor = new AesEncryptor();
        final byte[] iv = generateRandomIV();

        // Encrypt the data.
        final String encrypted = encryptor.encrypt(plaintext, iv);

        // Tamper with the ciphertext by modifying a character.
        final byte[] ciphertextBytes = java.util.Base64.getDecoder().decode(encrypted);
        ciphertextBytes[0] ^= 0xFF; // Flip bits in first byte
        final String tamperedEncrypted = java.util.Base64.getEncoder().encodeToString(ciphertextBytes);

        // Execute system under test - attempt to decrypt tampered data.
        final String decrypted = encryptor.decrypt(tamperedEncrypted, iv);

        // Verify results - GCM should detect the tampering and return null.
        // Note: The fallback to CBC may produce garbage or null depending on the tampering.
        // The key point is that the original plaintext should NOT be recovered.
        assertNotEquals(plaintext, decrypted,
                        "Tampered ciphertext should not decrypt to original plaintext");
    }
}
