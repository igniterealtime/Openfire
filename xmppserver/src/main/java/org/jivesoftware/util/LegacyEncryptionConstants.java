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

/**
 * Legacy encryption constants used for backward compatibility with older
 * Openfire versions that used hardcoded initialisation vectors and keys.
 *
 * These constants are used by:
 * - {@link Obfuscator} for deterministic obfuscation of configuration values
 * - {@link AesEncryptor} for decrypting values encrypted with older versions
 *
 * WARNING: These values must NEVER be changed as they are required to decrypt
 * existing encrypted values in security.xml and openfire.xml files from older
 * Openfire installations.
 *
 * @author Matthew Vivian
 */
final class LegacyEncryptionConstants {

    /**
     * Legacy hardcoded initialisation vector.
     * Used for backward compatibility with values encrypted prior to fixing OF-3074.
     */
    static final byte[] LEGACY_IV = {
        (byte)0xcd, (byte)0x91, (byte)0xa7, (byte)0xc5,
        (byte)0x27, (byte)0x8b, (byte)0x39, (byte)0xe0,
        (byte)0xfa, (byte)0x72, (byte)0xd0, (byte)0x29,
        (byte)0x83, (byte)0x65, (byte)0x9d, (byte)0x74
    };

    /**
     * Legacy hardcoded default key.
     * Used for backward compatibility with values encrypted prior to fixing OF-3074.
     */
    static final byte[] LEGACY_KEY = {
        (byte)0xf2, (byte)0x46, (byte)0x5d, (byte)0x2a,
        (byte)0xd1, (byte)0x73, (byte)0x0b, (byte)0x18,
        (byte)0xcb, (byte)0x86, (byte)0x95, (byte)0xa3,
        (byte)0xb1, (byte)0xe5, (byte)0x89, (byte)0x27
    };

    /**
     * Private constructor to prevent instantiation.
     */
    private LegacyEncryptionConstants() {
        throw new AssertionError("LegacyEncryptionConstants should not be instantiated");
    }
}