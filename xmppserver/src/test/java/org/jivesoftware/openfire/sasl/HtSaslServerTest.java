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
 * Unit tests for {@link HtSaslServer}.
 */
public class HtSaslServerTest {

    private static final String USERNAME = "testuser";
    private static final byte[] VALID_TOKEN_BYTES = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    /**
     * Builds an HT-* initial response: {@code cb-name,username,token-bytes}.
     */
    private static byte[] buildResponse(final String cbName, final String username, final byte[] tokenBytes) {
        final byte[] prefix = (cbName + "," + username + ",").getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[prefix.length + tokenBytes.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(tokenBytes, 0, result, prefix.length, tokenBytes.length);
        return result;
    }

    // -------------------------------------------------------------------------
    // Mechanism name
    // -------------------------------------------------------------------------

    /**
     * Verifies that the mechanism name is reported correctly for HT-SHA-256-NONE.
     */
    @Test
    public void mechanismNameShouldBeHtSha256None() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertEquals(FastTokenManager.HT_SHA_256_NONE, server.getMechanismName(),
            "Expected mechanism name to be HT-SHA-256-NONE.");
    }

    /**
     * Verifies that the mechanism name is reported correctly for HT-SHA-512-NONE.
     */
    @Test
    public void mechanismNameShouldBeHtSha512None() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_512_NONE, Collections.emptyMap());
        assertEquals(FastTokenManager.HT_SHA_512_NONE, server.getMechanismName(),
            "Expected mechanism name to be HT-SHA-512-NONE.");
    }

    // -------------------------------------------------------------------------
    // Successful authentication
    // -------------------------------------------------------------------------

    /**
     * Verifies that a valid HT-SHA-256-NONE token results in successful authentication.
     */
    @Test
    public void evaluateResponseShouldSucceedForValidHtSha256NoneToken() throws SaslException {
        final FastToken rotatedToken = new FastToken(USERNAME, FastTokenManager.HT_SHA_256_NONE,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_256_NONE), any()))
                .thenReturn(rotatedToken);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
            final byte[] response = buildResponse("none", USERNAME, VALID_TOKEN_BYTES);
            final byte[] result = server.evaluateResponse(response);

            assertTrue(server.isComplete(), "Expected authentication to be complete after a valid token.");
            assertNotNull(result, "Expected a non-null result.");
            assertEquals(0, result.length, "Expected an empty byte array on success (HT-* has no server challenge).");
            assertEquals(USERNAME, server.getAuthorizationID(), "Expected authorization ID to match username.");
            assertNotNull(server.getRotatedToken(), "Expected a rotated token after successful authentication.");
        }
    }

    /**
     * Verifies that a valid HT-SHA-512-NONE token results in successful authentication.
     */
    @Test
    public void evaluateResponseShouldSucceedForValidHtSha512NoneToken() throws SaslException {
        final FastToken rotatedToken = new FastToken(USERNAME, FastTokenManager.HT_SHA_512_NONE,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_512_NONE), any()))
                .thenReturn(rotatedToken);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_512_NONE, Collections.emptyMap());
            final byte[] response = buildResponse("none", USERNAME, VALID_TOKEN_BYTES);
            final byte[] result = server.evaluateResponse(response);

            assertTrue(server.isComplete(), "Expected authentication to be complete after a valid SHA-512 token.");
            assertEquals(0, result.length, "Expected an empty byte array on success for HT-SHA-512-NONE.");
        }
    }

    /**
     * Verifies that the rotated token returned by evaluateResponse matches the one from FastTokenManager.
     */
    @Test
    public void evaluateResponseShouldSetRotatedToken() throws SaslException {
        final FastToken rotatedToken = new FastToken(USERNAME, FastTokenManager.HT_SHA_256_NONE,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_256_NONE), any()))
                .thenReturn(rotatedToken);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
            server.evaluateResponse(buildResponse("none", USERNAME, VALID_TOKEN_BYTES));

            assertSame(rotatedToken, server.getRotatedToken(), "Expected rotatedToken to be the one returned by validateToken.");
        }
    }

    // -------------------------------------------------------------------------
    // Authentication failures
    // -------------------------------------------------------------------------

    /**
     * Verifies that an invalid token (validateToken returns null) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForInvalidToken() {
        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_256_NONE), any()))
                .thenReturn(null);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
            final byte[] response = buildResponse("none", USERNAME, VALID_TOKEN_BYTES);

            assertThrows(SaslException.class, () -> server.evaluateResponse(response),
                "Expected SaslException for an invalid token.");
        }
    }

    /**
     * Verifies that an expired token (validateToken returns null) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForExpiredToken() {
        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(any(), any(), any()))
                .thenReturn(null);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
            final byte[] response = buildResponse("none", USERNAME, VALID_TOKEN_BYTES);

            assertThrows(SaslException.class, () -> server.evaluateResponse(response),
                "Expected SaslException for an expired token.");
        }
    }

    /**
     * Verifies that a malformed response (no commas) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForMalformedResponseWithNoCommas() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("nocommashere".getBytes(StandardCharsets.UTF_8)),
            "Expected SaslException for a malformed response with no commas.");
    }

    /**
     * Verifies that a response with only one comma (missing token) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForResponseWithOnlyOneComma() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class,
            () -> server.evaluateResponse("none,username".getBytes(StandardCharsets.UTF_8)),
            "Expected SaslException for a response with only one comma.");
    }

    /**
     * Verifies that an empty response results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForEmptyResponse() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class, () -> server.evaluateResponse(new byte[0]),
            "Expected SaslException for an empty response.");
    }

    /**
     * Verifies that a null response results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForNullResponse() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
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
        final FastToken rotatedToken = new FastToken(USERNAME, FastTokenManager.HT_SHA_256_NONE,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(any(), any(), any()))
                .thenReturn(rotatedToken);

            final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
            server.evaluateResponse(buildResponse("none", USERNAME, VALID_TOKEN_BYTES));

            // Second call should fail.
            assertThrows(SaslException.class,
                () -> server.evaluateResponse(buildResponse("none", USERNAME, VALID_TOKEN_BYTES)),
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
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "Expected IllegalStateException when authentication is not complete.");
    }

    /**
     * Verifies that getRotatedToken returns null before authentication.
     */
    @Test
    public void getRotatedTokenShouldReturnNullBeforeAuthentication() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertNull(server.getRotatedToken(), "Expected getRotatedToken() to return null before authentication.");
    }

    /**
     * Verifies that isComplete returns false before authentication.
     */
    @Test
    public void isCompleteShouldReturnFalseBeforeAuthentication() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
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
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        server.dispose();
        assertFalse(server.isComplete(), "Expected isComplete() to be false after dispose.");
        assertNull(server.getRotatedToken(), "Expected rotatedToken to be null after dispose.");
    }

    // -------------------------------------------------------------------------
    // unwrap / wrap — not supported
    // -------------------------------------------------------------------------

    /**
     * Verifies that unwrap() throws SaslException because HT-* does not support integrity/confidentiality.
     */
    @Test
    public void unwrapShouldThrowSaslException() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class,
            () -> server.unwrap(new byte[]{1, 2, 3}, 0, 3),
            "Expected SaslException from unwrap() because HT-* does not support integrity/confidentiality.");
    }

    /**
     * Verifies that wrap() throws SaslException because HT-* does not support integrity/confidentiality.
     */
    @Test
    public void wrapShouldThrowSaslException() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class,
            () -> server.wrap(new byte[]{1, 2, 3}, 0, 3),
            "Expected SaslException from wrap() because HT-* does not support integrity/confidentiality.");
    }

    // -------------------------------------------------------------------------
    // Parameterized — all NONE variants work via the same code path
    // -------------------------------------------------------------------------

    /**
     * Verifies that all HT-*-NONE mechanism variants report the correct mechanism name and succeed
     * when given a valid token (mocked FastTokenManager).
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "HT-SHA-256-NONE",
        "HT-SHA-512-NONE",
    })
    public void evaluateResponseShouldSucceedForAllHtNoneVariants(final String mechanism) throws SaslException {
        final FastToken rotatedToken = new FastToken(USERNAME, mechanism,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(mechanism), any()))
                .thenReturn(rotatedToken);

            final HtSaslServer server = new HtSaslServer(mechanism, Collections.emptyMap());
            assertEquals(mechanism, server.getMechanismName(), "Expected mechanism name to match.");

            final byte[] result = server.evaluateResponse(buildResponse("none", USERNAME, VALID_TOKEN_BYTES));

            assertTrue(server.isComplete(), "Expected authentication to be complete for " + mechanism + ".");
            assertEquals(0, result.length, "Expected empty success result for " + mechanism + ".");
            assertEquals(USERNAME, server.getAuthorizationID(), "Expected authorization ID for " + mechanism + ".");
        }
    }
}
