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

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Obfuscator class, which provides deterministic obfuscation
 * using hardcoded constants (not cryptographically secure).
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3074">OF-3074: Prevent hardcoded IV when encrypting parameters</a>
 */
public class ObfuscatorTest {

    @Test
    public void testObfuscationRoundTrip() {
        String test = UUID.randomUUID().toString();

        Obfuscator obfuscator = new Obfuscator();

        String obfuscated = obfuscator.obfuscate(test);
        assertNotEquals(test, obfuscated, "Obfuscated value should differ from plaintext");

        assertEquals(test, obfuscator.deobfuscate(obfuscated),
                     "Deobfuscated value should match original plaintext");
    }

    @Test
    public void testObfuscationForEmptyString() {
        String test = "";

        Obfuscator obfuscator = new Obfuscator();

        String obfuscated = obfuscator.obfuscate(test);
        assertNotEquals(test, obfuscated, "Obfuscated empty string should not be empty");

        assertEquals(test, obfuscator.deobfuscate(obfuscated),
                     "Deobfuscated value should be empty string");
    }

    @Test
    public void testObfuscationForNullString() {
        Obfuscator obfuscator = new Obfuscator();

        String obfuscated = obfuscator.obfuscate(null);

        assertNull(obfuscated, "Obfuscating null should return null");
    }

    @Test
    public void testObfuscationIsDeterministic() {
        // This test verifies that obfuscation is deterministic (same input = same output).
        // This is expected behaviour for the Obfuscator class, as it uses hardcoded constants.
        // This is NOT cryptographically secure, but that's the point - it's for obfuscation,
        // not encryption.

        String plaintext = "test-value-123";
        Obfuscator obfuscator = new Obfuscator();

        String obfuscated1 = obfuscator.obfuscate(plaintext);
        String obfuscated2 = obfuscator.obfuscate(plaintext);

        assertEquals(obfuscated1, obfuscated2,
                     "Obfuscation should be deterministic with hardcoded constants");
    }

    @Test
    public void testDeobfuscationForNullString() {
        Obfuscator obfuscator = new Obfuscator();

        String deobfuscated = obfuscator.deobfuscate(null);

        assertNull(deobfuscated, "Deobfuscating null should return null");
    }
}