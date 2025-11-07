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
    private static final String ALGORITHM = "AES/CBC/PKCS7Padding";

    /**
     * Hardcoded initialization vector for deterministic obfuscation.
     * WARNING: Using a fixed IV is not cryptographically secure.
     */
    private static final byte[] INIT_PARM = {
        (byte)0xcd, (byte)0x91, (byte)0xa7, (byte)0xc5,
        (byte)0x27, (byte)0x8b, (byte)0x39, (byte)0xe0,
        (byte)0xfa, (byte)0x72, (byte)0xd0, (byte)0x29,
        (byte)0x83, (byte)0x65, (byte)0x9d, (byte)0x74
    };

    /**
     * Hardcoded encryption key for deterministic obfuscation.
     * WARNING: Using a fixed key is not cryptographically secure.
     */
    private static final byte[] DEFAULT_KEY = {
        (byte)0xf2, (byte)0x46, (byte)0x5d, (byte)0x2a,
        (byte)0xd1, (byte)0x73, (byte)0x0b, (byte)0x18,
        (byte)0xcb, (byte)0x86, (byte)0x95, (byte)0xa3,
        (byte)0xb1, (byte)0xe5, (byte)0x89, (byte)0x27
    };

    private static boolean isInitialized = false;

    /** Default constructor */
    public Obfuscator() {
        initialize();
    }

    /**
     * Obfuscates a string value using hardcoded constants.
     * The same input will always produce the same output (deterministic).
     *
     * @param value the value to obfuscate
     * @return the Base64-encoded obfuscated value, or null if input is null
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
     * @param value the Base64-encoded obfuscated value
     * @return the original plaintext value, or null if input is null
     */
    public String deobfuscate(String value) {
        if (value == null) {
            return null;
        }

        // OF-2883: Handle whitespace in Base64 encoded data (same as AesEncryptor)
        // OF-3112: Ignore all whitespace in Base64 encoded data
        final String val = value.trim().replaceAll("\\s", "");
        final byte[] decoded = Base64.getDecoder().decode(val);
        final byte[] bytes = cipher(decoded, Cipher.DECRYPT_MODE);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Symmetric obfuscate/deobfuscate routine using hardcoded key and IV.
     *
     * @param data The data to be converted
     * @param mode The cipher mode (encrypt or decrypt)
     * @return The converted data, or null if conversion fails
     */
    private byte[] cipher(byte[] data, int mode) {
        byte[] result = null;
        try {
            // Create AES encryption key
            Key aesKey = new SecretKeySpec(DEFAULT_KEY, "AES");

            // Create AES Cipher
            Cipher aesCipher = Cipher.getInstance(ALGORITHM);

            // Initialize AES Cipher and convert using hardcoded IV
            aesCipher.init(mode, aesKey, new IvParameterSpec(INIT_PARM));
            result = aesCipher.doFinal(data);
        } catch (Exception e) {
            log.error("Obfuscation cipher failed", e);
        }
        return result;
    }

    /** Installs the required security provider(s) */
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