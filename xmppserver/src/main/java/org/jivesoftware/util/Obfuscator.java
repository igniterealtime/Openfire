/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.Security;
import java.util.Base64;

/**
 * Utility class providing deterministic obfuscation using hardcoded constants.
 * This is NOT cryptographically secure encryption - it uses fixed IV and key values
 * to provide consistent, reversible obfuscation of data.
 *
 * This class is intended for obfuscating configuration values where deterministic
 * output is required (same input always produces same output), but where the values
 * should not be stored in plain text. For true encryption with security guarantees,
 * use {@link AesEncryptor} with a randomly generated IV for each encryption operation.
 *
 * @author Matthew Vivian
 * @see AesEncryptor
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
 */
public class Obfuscator {

    private static final Logger log = LoggerFactory.getLogger(Obfuscator.class);

    /**
     * Cipher algorithm used for obfuscation.
     * AES/CBC/PKCS7Padding is intentionally used despite being vulnerable to padding oracle attacks.
     * This is acceptable because this class provides obfuscation, not cryptographic security.
     * For more secure encryption, use {@link AesEncryptor} with random IVs.
     */
    private static final String ALGORITHM = "AES/CBC/PKCS7Padding";

    /**
     * Tracks whether the BouncyCastle security provider has been initialised.
     * BouncyCastle is required for PKCS7Padding support.
     */
    private static boolean isInitialized = false;

    /**
     * Default constructor.
     * Initialises the BouncyCastle security provider if not already loaded.
     */
    public Obfuscator() {
        initialize();
    }

    /**
     * Obfuscates a string value using hardcoded constants.
     * The same input will always produce the same output (deterministic).
     *
     * WARNING: This is NOT cryptographically secure encryption. This method uses:
     * - A static initialization vector (IV)
     * - A hardcoded key
     * - CBC mode (vulnerable to padding oracle attacks)
     *
     * This provides only obfuscation (hiding from casual viewing), not security.
     * Anyone with access to the source code can reverse this obfuscation.
     *
     * Use cases:
     * - Storing configuration values that should not be in plaintext
     * - Backward compatibility with legacy AesEncryptor data
     * - Situations requiring deterministic output (same input → same output)
     *
     * For cryptographically secure encryption, use {@link AesEncryptor} instead.
     *
     * @param value the value to obfuscate
     * @return the Base64-encoded obfuscated value, or null if input is null
     * @see AesEncryptor for secure encryption with random IVs
     */
    public String obfuscate(String value) {
        if (value == null) {
            return null;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        byte[] ciphered = cipher(bytes, Cipher.ENCRYPT_MODE);
        return Base64.getEncoder().encodeToString(ciphered);
    }

    /**
     * Deobfuscates a Base64-encoded obfuscated string.
     *
     * WARNING: This method intentionally uses a static initialization vector (IV) and
     * a hardcoded key for backward compatibility with legacy AesEncryptor implementations.
     * This is NOT cryptographically secure and should ONLY be used for obfuscation
     * (hiding values from casual viewing), not for security-critical encryption.
     *
     * The use of a static IV is a known cryptographic weakness that allows pattern analysis
     * and reduces security to "security through obscurity". This is acceptable for this
     * use case because the method is designed for deterministic obfuscation, not secure
     * encryption.
     *
     * Key security limitations:
     * - Static IV enables pattern analysis (identical plaintexts → identical ciphertexts)
     * - Hardcoded key means anyone with source code access can decrypt
     * - CBC mode is vulnerable to padding oracle attacks
     * - No authentication (no AEAD), making tampering undetectable
     *
     * This intentional use of weak cryptography will be flagged by static analysis tools
     * (CodeQL). The warnings are expected and acceptable given the obfuscation-only
     * purpose of this code.
     *
     * For cryptographically secure encryption, use {@link AesEncryptor} which provides:
     * - Random IV generation for each encryption operation
     * - Support for custom encryption keys
     *
     * @param value the Base64-encoded obfuscated value
     * @return the original plaintext value, or null if input is null
     * @see AesEncryptor for cryptographically secure encryption
     * @see LegacyEncryptionConstants for the hardcoded key and IV values
     */
    public String deobfuscate(String value) {
        if (value == null) {
            return null;
        }

        // OF-2883: Handle whitespace in Base64 encoded data (same as AesEncryptor)
        // OF-3112: Ignore all whitespace in Base64 encoded data
        final String val = value.trim().replaceAll("\\s", "");
        final byte[] decoded = Base64.getDecoder().decode(val);

        // SECURITY NOTE: The cipher() method intentionally uses a static IV and hardcoded key.
        // This is required for backward compatibility and deterministic obfuscation.
        // DO NOT modify cipher() to use random IVs - that would break existing installations.
        final byte[] bytes = cipher(decoded, Cipher.DECRYPT_MODE);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Symmetric obfuscate/deobfuscate routine using hardcoded key and IV.
     *
     * WARNING: This method intentionally uses insecure cryptographic practices:
     * 1. AES/CBC/PKCS7Padding - vulnerable to padding oracle attacks
     * 2. Static initialization vector - allows pattern analysis and deterministic output
     * 3. Hardcoded key - provides no real security, only obfuscation
     *
     * These are intentional design decisions for backward compatibility and deterministic
     * obfuscation. This is NOT cryptographically secure encryption. For secure encryption,
     * use {@link AesEncryptor} which supports random IV generation and custom keys.
     *
     * Static analysis warnings from this method are expected and acceptable.
     *
     * @param data The data to be converted
     * @param mode The cipher mode (encrypt or decrypt)
     * @return The converted data, or null if conversion fails
     * @see AesEncryptor for cryptographically secure encryption
     */
    private byte[] cipher(byte[] data, int mode) {
        byte[] result = null;
        try {
            // Create AES encryption key
            Key aesKey = new SecretKeySpec(LegacyEncryptionConstants.LEGACY_KEY, "AES");

            // SECURITY NOTE: This method intentionally uses a static IV and hardcoded key.
            // This is required for backward compatibility and deterministic obfuscation.
            // DO NOT modify cipher() to use random IVs - that would break existing installations.

            // Create AES Cipher - intentional use of CBC mode for backward compatibility
            // codeql[java/insecure-crypto]
            Cipher aesCipher = Cipher.getInstance(ALGORITHM);

            // Initialize AES Cipher with hardcoded IV - intentional for deterministic obfuscation
            // codeql[java/static-initialization-vector]
            aesCipher.init(mode, aesKey, new IvParameterSpec(LegacyEncryptionConstants.LEGACY_IV));
            result = aesCipher.doFinal(data);
        } catch (Exception e) {
            log.error("Obfuscation cipher failed", e);
        }
        return result;
    }

    /**
     * Installs the BouncyCastle security provider if not already initialised.
     *
     * BouncyCastle is required to support PKCS7Padding in the AES/CBC/PKCS7Padding cipher.
     * The standard JCE provider only supports PKCS5Padding, which is functionally identical
     * to PKCS7Padding for AES (block size 128 bits) but uses different naming.
     *
     * This method is thread-safe and will only add the provider once, even if called
     * multiple times concurrently.
     *
     * If BouncyCastle fails to load, a warning is logged but execution continues.
     * The cipher operations will fail later if the provider is required but unavailable.
     */
    private synchronized void initialize() {
        if (!isInitialized) {
            try {
                Security.addProvider(new BouncyCastleProvider());
                isInitialized = true;
            } catch (Throwable t) {
                log.warn("JCE provider failure; unable to load BC", t);
            }
        }
    }
}
