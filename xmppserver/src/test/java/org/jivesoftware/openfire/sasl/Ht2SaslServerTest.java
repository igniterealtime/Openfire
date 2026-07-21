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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastToken;
import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link Ht2SaslServer}.
 */
public class Ht2SaslServerTest {

    private static final String USERNAME = "testuser";
    private static final byte[] HMAC_BYTES_32 = new byte[32]; // placeholder initiator-hashed-token

    /**
     * Builds an HT2-* initiator message: {@code authcid NUL extra-initiator-values NUL initiator-hashed-token}.
     */
    private static byte[] buildInitiatorMessage(final String authcid, final String extraValues, final byte[] hashedToken) {
        final byte[] authcidBytes = authcid.getBytes(StandardCharsets.UTF_8);
        final byte[] extraBytes = extraValues.getBytes(StandardCharsets.UTF_8);
        // Layout: authcid 0x00 extraValues 0x00 hashedToken
        final byte[] msg = new byte[authcidBytes.length + 1 + extraBytes.length + 1 + hashedToken.length];
        int pos = 0;
        System.arraycopy(authcidBytes, 0, msg, pos, authcidBytes.length);
        pos += authcidBytes.length;
        msg[pos++] = 0x00;
        System.arraycopy(extraBytes, 0, msg, pos, extraBytes.length);
        pos += extraBytes.length;
        msg[pos++] = 0x00;
        System.arraycopy(hashedToken, 0, msg, pos, hashedToken.length);
        return msg;
    }

    /**
     * Returns a mock Ht2ValidationResult with a 32-byte responder token.
     */
    private static Ht2ValidationResult mockResult(final String mechanism) {
        final FastToken rotated = new FastToken(USERNAME, mechanism, new byte[32], Instant.now().plusSeconds(3600));
        return new Ht2ValidationResult(rotated, new byte[32]);
    }

    // -------------------------------------------------------------------------
    // Mechanism name
    // -------------------------------------------------------------------------

    /**
     * Verifies that the mechanism name is reported correctly for HT2-SHA-256-NONE.
     */
    @Test
    public void mechanismNameShouldBeHt2Sha256None() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertEquals(FastTokenManager.HT2_SHA_256_NONE, server.getMechanismName(),
            "Expected mechanism name to be HT2-SHA-256-NONE.");
    }

    /**
     * Verifies that the mechanism name is reported correctly for HT2-SHA-512-NONE.
     */
    @Test
    public void mechanismNameShouldBeHt2Sha512None() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_512_NONE, Collections.emptyMap());
        assertEquals(FastTokenManager.HT2_SHA_512_NONE, server.getMechanismName(),
            "Expected mechanism name to be HT2-SHA-512-NONE.");
    }

    // -------------------------------------------------------------------------
    // Successful authentication
    // -------------------------------------------------------------------------

    /**
     * Verifies that a valid HT2-SHA-256-NONE initiator message results in successful authentication.
     */
    @Test
    public void evaluateResponseShouldSucceedForValidHt2Sha256NoneToken() throws SaslException {
        final Ht2ValidationResult validationResult = mockResult(FastTokenManager.HT2_SHA_256_NONE);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(
                    eq(USERNAME), eq(FastTokenManager.HT2_SHA_256_NONE), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
            final byte[] initiatorMsg = buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32);
            final byte[] result = server.evaluateResponse(initiatorMsg);

            assertTrue(server.isComplete(), "Expected authentication to be complete after valid HT2 token.");
            assertNotNull(result, "Expected a non-null success message.");
            assertEquals(USERNAME, server.getAuthorizationID(), "Expected authorization ID to match authcid.");
            assertNotNull(server.getRotatedToken(), "Expected a rotated token after successful authentication.");
        }
    }

    /**
     * Verifies that a valid HT2-SHA-512-NONE initiator message results in successful authentication.
     */
    @Test
    public void evaluateResponseShouldSucceedForValidHt2Sha512NoneToken() throws SaslException {
        final Ht2ValidationResult validationResult = mockResult(FastTokenManager.HT2_SHA_512_NONE);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(
                    eq(USERNAME), eq(FastTokenManager.HT2_SHA_512_NONE), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_512_NONE, Collections.emptyMap());
            final byte[] result = server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32));

            assertTrue(server.isComplete(), "Expected authentication to be complete after valid HT2-SHA-512 token.");
            assertNotNull(result, "Expected a non-null success message.");
        }
    }

    /**
     * Verifies that the success message has the correct format: NUL extra-responder-values NUL responder-hashed-token.
     * With no extra values, the format is: 0x00 0x00 <responderToken>.
     */
    @Test
    public void evaluateResponseShouldReturnCorrectSuccessMessageFormat() throws SaslException {
        final byte[] responderToken = {10, 20, 30, 40};
        final FastToken rotated = new FastToken(USERNAME, FastTokenManager.HT2_SHA_256_NONE, new byte[32], Instant.now().plusSeconds(3600));
        final Ht2ValidationResult validationResult = new Ht2ValidationResult(rotated, responderToken);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(any(), any(), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
            final byte[] result = server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32));

            // Expected: 0x00 0x00 <responderToken>
            assertEquals(2 + responderToken.length, result.length,
                "Expected success message length to be 2 + responderToken.length.");
            assertEquals(0x00, result[0] & 0xFF, "Expected first byte to be NUL.");
            assertEquals(0x00, result[1] & 0xFF, "Expected second byte to be NUL (end of empty extra-responder-values).");
            assertArrayEquals(responderToken, java.util.Arrays.copyOfRange(result, 2, result.length),
                "Expected the responder token to be appended after the two NUL bytes.");
        }
    }

    /**
     * Verifies that the rotated token is set from the validation result.
     */
    @Test
    public void evaluateResponseShouldSetRotatedTokenFromValidationResult() throws SaslException {
        final FastToken rotated = new FastToken(USERNAME, FastTokenManager.HT2_SHA_256_NONE, new byte[32], Instant.now().plusSeconds(3600));
        final Ht2ValidationResult validationResult = new Ht2ValidationResult(rotated, new byte[32]);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(any(), any(), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
            server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32));

            assertSame(rotated, server.getRotatedToken(), "Expected rotatedToken to be the one from the validation result.");
        }
    }

    // -------------------------------------------------------------------------
    // Authentication failures
    // -------------------------------------------------------------------------

    /**
     * Verifies that a failed validation (validateTokenHt2 returns null) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForInvalidToken() {
        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
            final byte[] msg = buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32);

            assertThrows(SaslException.class, () -> server.evaluateResponse(msg),
                "Expected SaslException for a failed HT2 token validation.");
        }
    }

    /**
     * Verifies that a malformed initiator message (no NUL bytes) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForMalformedMessageWithNoNul() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("nonulbytes".getBytes(StandardCharsets.UTF_8)),
            "Expected SaslException for a malformed HT2 message with no NUL bytes.");
    }

    /**
     * Verifies that a message with only one NUL byte (missing initiator-hashed-token) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForMessageWithOnlyOneNul() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        final byte[] msg = "username\0extras".getBytes(StandardCharsets.UTF_8);
        assertThrows(SaslException.class, () -> server.evaluateResponse(msg),
            "Expected SaslException for an HT2 message with only one NUL byte.");
    }

    /**
     * Verifies that an empty response results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForEmptyResponse() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class, () -> server.evaluateResponse(new byte[0]),
            "Expected SaslException for an empty response.");
    }

    /**
     * Verifies that a null response results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForNullResponse() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class, () -> server.evaluateResponse(null),
            "Expected SaslException for a null response.");
    }

    // -------------------------------------------------------------------------
    // Double-call guard
    // -------------------------------------------------------------------------

    /**
     * Verifies that calling evaluateResponse after successful authentication throws SaslException.
     */
    @Test
    public void evaluateResponseShouldThrowWhenAlreadyComplete() throws SaslException {
        final Ht2ValidationResult validationResult = mockResult(FastTokenManager.HT2_SHA_256_NONE);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(any(), any(), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
            server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32));

            // Second call should fail.
            assertThrows(SaslException.class,
                () -> server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32)),
                "Expected SaslException when evaluateResponse is called a second time after completion.");
        }
    }

    // -------------------------------------------------------------------------
    // State checks
    // -------------------------------------------------------------------------

    /**
     * Verifies that getAuthorizationID throws when authentication is not yet complete.
     */
    @Test
    public void getAuthorizationIdShouldThrowWhenNotComplete() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "Expected IllegalStateException when authentication is not complete.");
    }

    /**
     * Verifies that getRotatedToken returns null before authentication.
     */
    @Test
    public void getRotatedTokenShouldReturnNullBeforeAuthentication() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertNull(server.getRotatedToken(), "Expected getRotatedToken() to return null before authentication.");
    }

    /**
     * Verifies that isComplete returns false before authentication.
     */
    @Test
    public void isCompleteShouldReturnFalseBeforeAuthentication() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        assertFalse(server.isComplete(), "Expected isComplete() to return false before authentication.");
    }

    // -------------------------------------------------------------------------
    // dispose
    // -------------------------------------------------------------------------

    /**
     * Verifies that dispose() resets the server state.
     */
    @Test
    public void disposeShouldResetState() throws SaslException {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        server.dispose();
        assertFalse(server.isComplete(), "Expected isComplete() to be false after dispose.");
        assertNull(server.getRotatedToken(), "Expected rotatedToken to be null after dispose.");
    }

    // -------------------------------------------------------------------------
    // Parameterized — all NONE variants
    // -------------------------------------------------------------------------

    /**
     * Verifies that all HT2-*-NONE mechanism variants report the correct mechanism name and succeed
     * when given a valid mocked validation result.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "HT2-SHA-256-NONE",
        "HT2-SHA-512-NONE",
    })
    public void evaluateResponseShouldSucceedForAllHt2NoneVariants(final String mechanism) throws SaslException {
        final Ht2ValidationResult validationResult = mockResult(mechanism);

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateTokenHt2(
                    eq(USERNAME), eq(mechanism), any(), any(), any(), any()))
                .thenReturn(validationResult);

            final Ht2SaslServer server = new Ht2SaslServer(mechanism, Collections.emptyMap());
            assertEquals(mechanism, server.getMechanismName(), "Expected mechanism name to match.");

            final byte[] result = server.evaluateResponse(buildInitiatorMessage(USERNAME, "", HMAC_BYTES_32));

            assertTrue(server.isComplete(), "Expected authentication to be complete for " + mechanism + ".");
            assertNotNull(result, "Expected a non-null success message for " + mechanism + ".");
            assertTrue(result.length >= 2, "Expected at least 2 bytes in success message for " + mechanism + ".");
            assertEquals(USERNAME, server.getAuthorizationID(), "Expected authorization ID for " + mechanism + ".");
        }
    }
}
