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

import org.dom4j.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.jivesoftware.util.Encryptor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FastTokenManager}.
 */
public class FastTokenManagerTest {

    @Test
    public void storedTokenEnvelopeDoesNotContainPlaintextAndCanBeDecrypted() {
        final Encryptor encryptor = new Encryptor() {
            @Override public String encrypt(final String value) { return encrypt(value, new byte[0]); }
            @Override public String encrypt(final String value, final byte[] iv) {
                return Base64.getEncoder().encodeToString((value + ":cipher").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            @Override public String decrypt(final String value) { return decrypt(value, new byte[0]); }
            @Override public String decrypt(final String value, final byte[] iv) {
                final String decoded = new String(Base64.getDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8);
                return decoded.substring(0, decoded.length() - ":cipher".length());
            }
            @Override public void setKey(final String key) { }
        };
        final String protectedValue = FastTokenManager.protectToken("secret-token", encryptor, new byte[16]);

        assertTrue(protectedValue.startsWith("v1:"));
        assertFalse(protectedValue.contains("secret-token"));
        assertEquals("secret-token", FastTokenManager.unprotectToken(protectedValue, encryptor));
        assertThrows(IllegalArgumentException.class,
            () -> FastTokenManager.unprotectToken("cleartext-token", encryptor));
        assertThrows(IllegalArgumentException.class,
            () -> FastTokenManager.unprotectToken("v2:unsupported", encryptor));
    }

    // -------------------------------------------------------------------------
    // sha256Hex (deprecated delegate to hashHex)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // hashHex
    // -------------------------------------------------------------------------

    /**
     * Verifies that hashHex with SHA-256 produces a 64-character hex string.
     */
    @Test
    public void hashHexSha256ShouldReturnCorrectLength() {
        final String hex = FastTokenManager.hashHex("hello".getBytes(), "SHA-256");
        assertEquals(64, hex.length(), "Expected a 64-character SHA-256 hex string.");
        assertTrue(hex.matches("[0-9a-f]+"), "Expected lowercase hex characters only.");
    }

    /**
     * Verifies that hashHex with SHA-512 produces a 128-character hex string.
     */
    @Test
    public void hashHexSha512ShouldReturnCorrectLength() {
        final String hex = FastTokenManager.hashHex("hello".getBytes(), "SHA-512");
        assertEquals(128, hex.length(), "Expected a 128-character SHA-512 hex string.");
        assertTrue(hex.matches("[0-9a-f]+"), "Expected lowercase hex characters only.");
    }

    /**
     * Verifies that hashHex SHA-256 and SHA-512 differ for the same input.
     */
    @Test
    public void hashHexShouldDifferBetweenSha256AndSha512() {
        final byte[] data = "same-input".getBytes();
        final String hex256 = FastTokenManager.hashHex(data, "SHA-256");
        final String hex512 = FastTokenManager.hashHex(data, "SHA-512");
        assertNotEquals(hex256, hex512, "Expected SHA-256 and SHA-512 hashes to differ.");
    }

    /**
     * Verifies that hashHex throws IllegalStateException for an unknown algorithm.
     */
    @Test
    public void hashHexShouldThrowForUnknownAlgorithm() {
        assertThrows(IllegalStateException.class,
            () -> FastTokenManager.hashHex("data".getBytes(), "NOT-AN-ALGORITHM"),
            "Expected IllegalStateException for an unknown digest algorithm.");
    }

    // -------------------------------------------------------------------------
    // hmac
    // -------------------------------------------------------------------------

    /**
     * Verifies that hmac with HmacSHA256 is deterministic.
     */
    @Test
    public void hmacSha256ShouldBeDeterministic() {
        final byte[] key = "key".getBytes();
        final byte[] msg = "message".getBytes();
        assertArrayEquals(
            FastTokenManager.hmac(key, msg, "HmacSHA256"),
            FastTokenManager.hmac(key, msg, "HmacSHA256"),
            "Expected the same HMAC for the same key and message.");
    }

    /**
     * Verifies that hmac with HmacSHA256 produces 32 bytes.
     */
    @Test
    public void hmacSha256ShouldProduceThirtyTwoBytes() {
        final byte[] result = FastTokenManager.hmac("key".getBytes(), "msg".getBytes(), "HmacSHA256");
        assertEquals(32, result.length, "Expected HmacSHA256 output to be 32 bytes.");
    }

    /**
     * Verifies that hmac with HmacSHA512 produces 64 bytes.
     */
    @Test
    public void hmacSha512ShouldProduceSixtyFourBytes() {
        final byte[] result = FastTokenManager.hmac("key".getBytes(), "msg".getBytes(), "HmacSHA512");
        assertEquals(64, result.length, "Expected HmacSHA512 output to be 64 bytes.");
    }

    /**
     * Verifies that hmac HmacSHA256 and HmacSHA512 differ for the same inputs.
     */
    @Test
    public void hmacShouldDifferBetweenSha256AndSha512() {
        final byte[] key = "key".getBytes();
        final byte[] msg = "msg".getBytes();
        final byte[] mac256 = FastTokenManager.hmac(key, msg, "HmacSHA256");
        final byte[] mac512 = FastTokenManager.hmac(key, msg, "HmacSHA512");
        assertFalse(java.util.Arrays.equals(mac256, mac512),
            "Expected HmacSHA256 and HmacSHA512 outputs to differ.");
    }

    /**
     * Verifies that hmac throws IllegalStateException for an unknown algorithm.
     */
    @Test
    public void hmacShouldThrowForUnknownAlgorithm() {
        assertThrows(IllegalStateException.class,
            () -> FastTokenManager.hmac("key".getBytes(), "msg".getBytes(), "NotAnAlgorithm"),
            "Expected IllegalStateException for an unknown HMAC algorithm.");
    }

    // -------------------------------------------------------------------------
    // hashAlgorithmForMechanism
    // -------------------------------------------------------------------------

    /**
     * Verifies that hashAlgorithmForMechanism extracts the correct algorithm from all 16 mechanism names.
     */
    @ParameterizedTest
    @CsvSource({
        "HT-SHA-256-NONE,  SHA-256",
        "HT-SHA-256-UNIQ,  SHA-256",
        "HT-SHA-256-ENDP,  SHA-256",
        "HT-SHA-256-EXPR,  SHA-256",
        "HT-SHA-512-NONE,  SHA-512",
        "HT-SHA-512-UNIQ,  SHA-512",
        "HT-SHA-512-ENDP,  SHA-512",
        "HT-SHA-512-EXPR,  SHA-512",
        "HT2-SHA-256-NONE, SHA-256",
        "HT2-SHA-256-UNIQ, SHA-256",
        "HT2-SHA-256-ENDP, SHA-256",
        "HT2-SHA-256-EXPR, SHA-256",
        "HT2-SHA-512-NONE, SHA-512",
        "HT2-SHA-512-UNIQ, SHA-512",
        "HT2-SHA-512-ENDP, SHA-512",
        "HT2-SHA-512-EXPR, SHA-512",
        "HT-SHA3-512-NONE, SHA3-512",
        "HT2-SHA3-512-EXPR, SHA3-512",
    })
    public void hashAlgorithmForMechanismShouldExtractCorrectAlgorithm(final String mechanism, final String expectedAlgorithm) {
        assertEquals(expectedAlgorithm.trim(),
            FastTokenManager.hashAlgorithmForMechanism(mechanism.trim()),
            "Expected hash algorithm '" + expectedAlgorithm.trim() + "' for mechanism '" + mechanism.trim() + "'.");
    }

    /**
     * Verifies that hashAlgorithmForMechanism throws for a malformed mechanism name.
     */
    @Test
    public void hashAlgorithmForMechanismShouldThrowForMalformedName() {
        assertThrows(IllegalArgumentException.class,
            () -> FastTokenManager.hashAlgorithmForMechanism("INVALID"),
            "Expected IllegalArgumentException for a malformed mechanism name.");
    }

    // -------------------------------------------------------------------------
    // hmacAlgorithmForMechanism
    // -------------------------------------------------------------------------

    /**
     * Verifies that hmacAlgorithmForMechanism maps SHA-256 variants to HmacSHA256.
     */
    @ParameterizedTest
    @CsvSource({
        "HT-SHA-256-NONE",
        "HT-SHA-256-UNIQ",
        "HT-SHA-256-ENDP",
        "HT-SHA-256-EXPR",
        "HT2-SHA-256-NONE",
        "HT2-SHA-256-UNIQ",
        "HT2-SHA-256-ENDP",
        "HT2-SHA-256-EXPR",
    })
    public void hmacAlgorithmForSha256MechanismShouldReturnHmacSha256(final String mechanism) {
        assertEquals("HmacSHA256",
            FastTokenManager.hmacAlgorithmForMechanism(mechanism),
            "Expected HmacSHA256 for mechanism '" + mechanism + "'.");
    }

    /**
     * Verifies that hmacAlgorithmForMechanism maps SHA-512 variants to HmacSHA512.
     */
    @ParameterizedTest
    @CsvSource({
        "HT-SHA-512-NONE",
        "HT-SHA-512-UNIQ",
        "HT-SHA-512-ENDP",
        "HT-SHA-512-EXPR",
        "HT2-SHA-512-NONE",
        "HT2-SHA-512-UNIQ",
        "HT2-SHA-512-ENDP",
        "HT2-SHA-512-EXPR",
    })
    public void hmacAlgorithmForSha512MechanismShouldReturnHmacSha512(final String mechanism) {
        assertEquals("HmacSHA512",
            FastTokenManager.hmacAlgorithmForMechanism(mechanism),
            "Expected HmacSHA512 for mechanism '" + mechanism + "'.");
    }

    @Test
    public void hmacAlgorithmForSha3MechanismUsesRegisteredJcaName() {
        assertEquals("HmacSHA3-512", FastTokenManager.hmacAlgorithmForMechanism(FastTokenManager.HT2_SHA3_512_NONE));
        assertEquals(64, FastTokenManager.hmac("key".getBytes(), "message".getBytes(), "HmacSHA3-512").length);
    }

    // -------------------------------------------------------------------------
    // isHt2Mechanism
    // -------------------------------------------------------------------------

    /**
     * Verifies that isHt2Mechanism returns true for HT2-* mechanisms.
     */
    @ParameterizedTest
    @CsvSource({
        "HT2-SHA-256-NONE",
        "HT2-SHA-256-UNIQ",
        "HT2-SHA-512-NONE",
        "HT2-SHA-512-EXPR",
    })
    public void isHt2MechanismShouldReturnTrueForHt2(final String mechanism) {
        assertTrue(FastTokenManager.isHt2Mechanism(mechanism),
            "Expected isHt2Mechanism to return true for '" + mechanism + "'.");
    }

    /**
     * Verifies that isHt2Mechanism returns false for HT-* (non-HT2) mechanisms.
     */
    @ParameterizedTest
    @CsvSource({
        "HT-SHA-256-NONE",
        "HT-SHA-256-UNIQ",
        "HT-SHA-512-NONE",
        "HT-SHA-512-EXPR",
    })
    public void isHt2MechanismShouldReturnFalseForHt(final String mechanism) {
        assertFalse(FastTokenManager.isHt2Mechanism(mechanism),
            "Expected isHt2Mechanism to return false for '" + mechanism + "'.");
    }

    // -------------------------------------------------------------------------
    // featureElement
    // -------------------------------------------------------------------------

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

    /**
     * Verifies that featureElement() advertises every supported FAST mechanism.
     */
    @Test
    public void featureElementShouldAdvertiseAllSupportedMechanisms() {
        final Element fast = FastTokenManager.featureElement();
        @SuppressWarnings("unchecked")
        final List<Element> mechanisms = fast.elements("mechanism");
        final List<String> names = mechanisms.stream().map(Element::getText).collect(Collectors.toList());

        assertEquals(24, names.size(), "Expected exactly 24 mechanism elements.");

        // HT-* (8)
        assertTrue(names.contains(FastTokenManager.HT_SHA_256_NONE), "Expected HT-SHA-256-NONE to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_256_UNIQ), "Expected HT-SHA-256-UNIQ to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_256_ENDP), "Expected HT-SHA-256-ENDP to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_256_EXPR), "Expected HT-SHA-256-EXPR to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_512_NONE), "Expected HT-SHA-512-NONE to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_512_UNIQ), "Expected HT-SHA-512-UNIQ to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_512_ENDP), "Expected HT-SHA-512-ENDP to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA_512_EXPR), "Expected HT-SHA-512-EXPR to be advertised.");
        // HT2-* (8)
        assertTrue(names.contains(FastTokenManager.HT2_SHA_256_NONE), "Expected HT2-SHA-256-NONE to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_256_UNIQ), "Expected HT2-SHA-256-UNIQ to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_256_ENDP), "Expected HT2-SHA-256-ENDP to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_256_EXPR), "Expected HT2-SHA-256-EXPR to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_512_NONE), "Expected HT2-SHA-512-NONE to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_512_UNIQ), "Expected HT2-SHA-512-UNIQ to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_512_ENDP), "Expected HT2-SHA-512-ENDP to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT2_SHA_512_EXPR), "Expected HT2-SHA-512-EXPR to be advertised.");
        assertTrue(names.contains(FastTokenManager.HT_SHA3_512_NONE));
        assertTrue(names.contains(FastTokenManager.HT_SHA3_512_UNIQ));
        assertTrue(names.contains(FastTokenManager.HT_SHA3_512_ENDP));
        assertTrue(names.contains(FastTokenManager.HT_SHA3_512_EXPR));
        assertTrue(names.contains(FastTokenManager.HT2_SHA3_512_NONE));
        assertTrue(names.contains(FastTokenManager.HT2_SHA3_512_UNIQ));
        assertTrue(names.contains(FastTokenManager.HT2_SHA3_512_ENDP));
        assertTrue(names.contains(FastTokenManager.HT2_SHA3_512_EXPR));
    }

    // -------------------------------------------------------------------------
    // FastToken
    // -------------------------------------------------------------------------

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
     * Verifies that FastToken.getUsername() and getMechanism() return the values passed at construction.
     */
    @Test
    public void fastTokenShouldReturnConstructorValues() {
        final Instant expiry = Instant.now().plusSeconds(3600);
        final FastToken token = new FastToken("alice", "HT2-SHA-512-NONE", new byte[32], expiry);
        assertEquals("alice", token.getUsername(), "Expected getUsername() to return the constructed username.");
        assertEquals("HT2-SHA-512-NONE", token.getMechanism(), "Expected getMechanism() to return the constructed mechanism.");
        assertEquals(expiry, token.getExpiry(), "Expected getExpiry() to return the constructed expiry instant.");
    }

    // -------------------------------------------------------------------------
    // Ht2ValidationResult
    // -------------------------------------------------------------------------

    /**
     * Verifies that Ht2ValidationResult.getResponderHashedToken() returns a defensive copy.
     */
    @Test
    public void ht2ValidationResultShouldReturnDefensiveCopyOfResponderToken() {
        final FastToken rotated = new FastToken("user", "HT2-SHA-256-NONE", new byte[32], Instant.now().plusSeconds(3600));
        final byte[] responder = {10, 20, 30};
        final FastTokenManager.Ht2ValidationResult result =
            new FastTokenManager.Ht2ValidationResult(rotated, responder);

        final byte[] retrieved = result.getResponderHashedToken();
        retrieved[0] = 99;
        assertNotEquals(99, result.getResponderHashedToken()[0],
            "Expected getResponderHashedToken() to return a defensive copy.");
    }

    /**
     * Verifies that Ht2ValidationResult.getRotatedToken() returns the token passed at construction.
     */
    @Test
    public void ht2ValidationResultShouldReturnRotatedToken() {
        final FastToken rotated = new FastToken("user", "HT2-SHA-256-NONE", new byte[32], Instant.now().plusSeconds(3600));
        final FastTokenManager.Ht2ValidationResult result =
            new FastTokenManager.Ht2ValidationResult(rotated, new byte[32]);
        assertSame(rotated, result.getRotatedToken(), "Expected getRotatedToken() to return the same FastToken instance.");
    }
}
