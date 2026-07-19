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
package org.jivesoftware.openfire.fast;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FastTokenManager}.
 */
public class FastTokenManagerTest {

    /**
     * Verifies that sha256Hex produces a 64-character lowercase hex string.
     */
    @Test
    public void sha256HexShouldReturnCorrectLength() {
        final byte[] data = "hello".getBytes();
        final String hex = FastTokenManager.sha256Hex(data);
        assertNotNull(hex, "Expected a non-null hash.");
        assertEquals(64, hex.length(), "Expected a 64-character SHA-256 hex string.");
        assertTrue(hex.matches("[0-9a-f]+"), "Expected lowercase hex characters only.");
    }

    /**
     * Verifies that sha256Hex is deterministic for the same input.
     */
    @Test
    public void sha256HexShouldBeDeterministic() {
        final byte[] data = "test-data".getBytes();
        final String hex1 = FastTokenManager.sha256Hex(data);
        final String hex2 = FastTokenManager.sha256Hex(data);
        assertEquals(hex1, hex2, "Expected the same hash for the same input.");
    }

    /**
     * Verifies that sha256Hex produces different hashes for different inputs.
     */
    @Test
    public void sha256HexShouldProduceDifferentHashesForDifferentInputs() {
        final String hex1 = FastTokenManager.sha256Hex("input1".getBytes());
        final String hex2 = FastTokenManager.sha256Hex("input2".getBytes());
        assertNotEquals(hex1, hex2, "Expected different hashes for different inputs.");
    }

    /**
     * Verifies that FastToken correctly reports expiry.
     */
    @Test
    public void fastTokenShouldReportExpiredWhenPastExpiry() {
        final FastToken token = new FastToken("user", "HT-SHA-256-NONE", new byte[32], Instant.now().minusSeconds(1));
        assertTrue(token.isExpired(), "Expected token to be expired when expiry is in the past.");
    }

    /**
     * Verifies that FastToken correctly reports non-expiry.
     */
    @Test
    public void fastTokenShouldNotReportExpiredWhenBeforeExpiry() {
        final FastToken token = new FastToken("user", "HT-SHA-256-NONE", new byte[32], Instant.now().plusSeconds(3600));
        assertFalse(token.isExpired(), "Expected token not to be expired when expiry is in the future.");
    }

    /**
     * Verifies that FastToken.getToken() returns a defensive copy.
     */
    @Test
    public void fastTokenGetTokenShouldReturnDefensiveCopy() {
        final byte[] original = new byte[]{1, 2, 3};
        final FastToken token = new FastToken("user", "HT-SHA-256-NONE", original, Instant.now().plusSeconds(3600));
        final byte[] retrieved = token.getToken();
        retrieved[0] = 99;
        assertNotEquals(99, token.getToken()[0], "Expected getToken() to return a defensive copy.");
    }

    /**
     * Verifies that featureElement() returns a correctly structured element.
     */
    @Test
    public void featureElementShouldReturnCorrectStructure() {
        final org.dom4j.Element fast = FastTokenManager.featureElement();
        assertNotNull(fast, "Expected a non-null feature element.");
        assertEquals("fast", fast.getName(), "Expected element name to be 'fast'.");
        assertEquals(FastTokenManager.NAMESPACE, fast.getNamespaceURI(), "Expected FAST namespace.");
        final org.dom4j.Element mechanism = fast.element("mechanism");
        assertNotNull(mechanism, "Expected a <mechanism/> child element.");
        assertEquals(FastTokenManager.HT_SHA_256_NONE, mechanism.getText(), "Expected HT-SHA-256-NONE mechanism.");
    }
}
