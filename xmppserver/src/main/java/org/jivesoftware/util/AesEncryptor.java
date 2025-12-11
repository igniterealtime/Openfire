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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.Security;
import java.util.Base64;

/**
 * Utility class providing symmetric AES encryption/decryption. To strengthen
 * the encrypted result, use the {@link #setKey} method to provide a custom
 * key prior to invoking the {@link #encrypt} or {@link #decrypt} methods.
 *
 * @author Tom Evans
 */
public class AesEncryptor implements Encryptor {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptor.class);
    private static final String ALGORITHM_CBC = "AES/CBC/PKCS7Padding";
    private static final String ALGORITHM_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static boolean isInitialized = false;

    private byte[] cipherKey = null;

    /** Default constructor */
    public AesEncryptor() { initialize(); }

    /**
     * Custom key constructor
     *
     * @param key the custom key
     */
    public AesEncryptor(String key) { 
        initialize();
        setKey(key);
    }

    /**
     * Encrypts a string value using AES with hardcoded IV.
     *
     * @deprecated This method uses a hardcoded IV which makes encryption deterministic
     *             (same plaintext always produces same ciphertext). This is a security
     *             vulnerability as it enables pattern analysis attacks. Use
     *             {@link #encrypt(String, byte[])} with a randomly generated IV instead.
     *             This method is only kept for backward compatibility with existing
     *             encrypted values in configuration files.
     * @param value the value to encrypt
     * @return the Base64-encoded encrypted value, or null if input is null
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
     */
    @Deprecated
    @Override
    public String encrypt(String value) {
        return encrypt(value, null);
    }

    /**
     * Encrypts a plaintext string using AES-GCM with the provided IV.
     *
     * IMPORTANT: Never reuse an IV with the same key. GCM mode is catastrophically
     * weak if the same IV is used twice with the same key - it allows an attacker
     * to recover the authentication key and forge messages. Always generate a new
     * random IV for each encryption operation using SecureRandom.
     *
     * If iv is null, falls back to legacy CBC mode with hardcoded IV for backward
     * compatibility (deprecated behaviour).
     *
     * @param value the plaintext value to encrypt
     * @param iv a unique 16-byte initialisation vector (must never be reused with the same key)
     * @return the Base64-encoded encrypted value, or null if input is null
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3077">OF-3077: Potential padding oracle CBC-mode encryption</a>
     * @see <a href="https://csrc.nist.gov/pubs/sp/800/38/d/final">NIST SP 800-38D: GCM Mode</a>
     */
    @Override
    public String encrypt(String value, byte[] iv) {
        if (value == null) { return null; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted;
        if (iv == null) {
            // Legacy mode: use CBC with hardcoded IV for backward compatibility
            encrypted = cipherCbc(bytes, getKey(), LegacyEncryptionConstants.LEGACY_IV, Cipher.ENCRYPT_MODE);
        } else {
            // Modern mode: use GCM with provided IV (OF-3077)
            encrypted = cipherGcm(bytes, getKey(), iv, Cipher.ENCRYPT_MODE);
        }

        return encrypted == null ? null : Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypts a Base64-encoded encrypted string using AES with hardcoded IV.
     * This method is kept for backward compatibility with values encrypted by older
     * versions of Openfire that used a hardcoded IV. For new encryption operations,
     * use {@link #encrypt(String, byte[])} with a randomly generated IV and
     * {@link #decrypt(String, byte[])} with the same IV for decryption.
     *
     * @param value the Base64-encoded encrypted value to decrypt
     * @return the decrypted plaintext value, or null if input is null
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2883">OF-2883: Base64 decoding issue preventing startup (after upgrade to 4.9.0)</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
     */
    @Override
    public String decrypt(String value) {
        return decrypt(value, null);
    }

    @Override
    public String decrypt(String value, byte[] iv) {
        if (value == null) { return null; }

        // OF-2883: In Openfire 4.9.0, the base64 encoder was changed. This causes a compatibility issue with security.xml files that
        // were generated by older values, that use the 'MIME' type of decoding (with linebreaks) that's not used in later versions.
        // While persisting data in 'security.xml', linebreaks are replaced by white space.
        final String val = value.trim().replaceAll("\\s",""); // OF-3112: Ignore all whitespace in Base64 encoded data.
        final byte[] decoded = Base64.getDecoder().decode(val);

        byte[] bytes;
        if (iv == null) {
            // Legacy mode: use CBC with hardcoded IV for backward compatibility
            bytes = cipherCbc(decoded, getKey(), LegacyEncryptionConstants.LEGACY_IV, Cipher.DECRYPT_MODE);
        } else {
            // Modern mode: try GCM first (OF-3077), fall back to CBC for OF-1533 era data
            bytes = cipherGcm(decoded, getKey(), iv, Cipher.DECRYPT_MODE);
            if (bytes == null) {
                // GCM failed - try CBC for backward compatibility with data encrypted
                // since OF-1533 (2018) which used CBC with random IV
                log.debug("GCM decryption failed, attempting CBC fallback for backward compatibility");
                bytes = cipherCbc(decoded, getKey(), iv, Cipher.DECRYPT_MODE);
            }
        }

        if (bytes == null) { return null; }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Symmetric encrypt/decrypt routine using AES-GCM (authenticated encryption).
     * GCM mode provides both confidentiality and integrity protection, and is not
     * susceptible to padding oracle attacks.
     *
     * @param attribute The value to be converted
     * @param key The encryption key
     * @param iv The initialisation vector
     * @param mode The cipher mode (encrypt or decrypt)
     * @return The converted attribute, or null if conversion fails
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3077">OF-3077: Potential padding oracle CBC-mode encryption</a>
     */
    private byte[] cipherGcm(byte[] attribute, byte[] key, byte[] iv, int mode)
    {
        byte[] result = null;
        try
        {
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance(ALGORITHM_GCM);
            aesCipher.init(mode, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            result = aesCipher.doFinal(attribute);
        }
        catch (Exception e)
        {
            // Don't log at error level - this may be expected during fallback decryption
            log.debug("AES-GCM cipher failed", e);
        }
        return result;
    }

    /**
     * Symmetric encrypt/decrypt routine using AES-CBC (legacy mode).
     * This method is kept for backward compatibility with data encrypted before OF-3077.
     *
     * @param attribute The value to be converted
     * @param key The encryption key
     * @param iv The initialisation vector
     * @param mode The cipher mode (encrypt or decrypt)
     * @return The converted attribute, or null if conversion fails
     */
    private byte[] cipherCbc(byte[] attribute, byte[] key, byte[] iv, int mode)
    {
        byte[] result = null;
        try
        {
            Key aesKey = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance(ALGORITHM_CBC);
            aesCipher.init(mode, aesKey, new IvParameterSpec(iv));
            result = aesCipher.doFinal(attribute);
        }
        catch (Exception e)
        {
            log.error("AES-CBC cipher failed", e);
        }
        return result;
    }

    /**
     * Return the encryption key. This will return the user-defined
     * key (if available) or a default encryption key.
     *
     * @return The encryption key
     */
    private byte [] getKey()
    {
        return cipherKey == null ? LegacyEncryptionConstants.LEGACY_KEY : cipherKey;
    }

    /**
     * Set the encryption key. This will apply the user-defined key,
     * truncated or filled (via the default key) as needed  to meet
     * the key length specifications.
     *
     * @param key The encryption key
     */
    private void setKey(byte [] key)
    {
        cipherKey = editKey(key);
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.util.Encryptor#setKey(java.lang.String)
     */
    @Override
    public void setKey(String key)
    {
        if (key == null) { 
            cipherKey = null; 
            return;
        }
        byte [] bytes = key.getBytes(StandardCharsets.UTF_8);
        setKey(editKey(bytes));
    }

    /**
     * Validates an optional user-defined encryption key. Only the
     * first sixteen bytes of the input array will be used for the key.
     * It will be filled (if necessary) to a minimum length of sixteen.
     *
     * @param key The user-defined encryption key
     * @return A valid encryption key, or null
     */
    private byte [] editKey(byte [] key)
    {
        if (key == null) { return null; }
        byte [] result = new byte [LegacyEncryptionConstants.LEGACY_KEY.length];
        for (int x=0; x<LegacyEncryptionConstants.LEGACY_KEY.length; x++)
        {
            result[x] = x < key.length ? key[x] : LegacyEncryptionConstants.LEGACY_KEY[x];
        }
        return result;
    }

    /** Installs the required security provider(s) */
    private synchronized void initialize()
    {
        if (!isInitialized)
        {
            try
            {
                Security.addProvider(new BouncyCastleProvider());
                isInitialized = true;
            }
            catch (Throwable t)
            {
                log.warn("JCE provider failure; unable to load BC", t);
            }
        }
    }

/* */
    
}
