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
import org.mockito.MockedStatic;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link HtSha256NoneSaslServer}.
 */
public class HtSha256NoneSaslServerTest {

    private static final String USERNAME = "testuser";
    private static final byte[] VALID_TOKEN_BYTES = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

    /**
     * Builds the HT-SHA-256-NONE initial response: {@code none,<username>,<token-bytes>}
     */
    private static byte[] buildResponse(final String username, final byte[] tokenBytes) {
        final byte[] prefix = ("none," + username + ",").getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[prefix.length + tokenBytes.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(tokenBytes, 0, result, prefix.length, tokenBytes.length);
        return result;
    }

    /**
     * Verifies that the mechanism name is correct.
     */
    @Test
    public void mechanismNameShouldBeHtSha256None() {
        final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
        assertEquals(FastTokenManager.HT_SHA_256_NONE, server.getMechanismName(),
            "Expected mechanism name to be HT-SHA-256-NONE.");
    }

    /**
     * Verifies that a valid token results in successful authentication.
     */
    @Test
    public void evaluateResponseShouldSucceedForValidToken() throws SaslException {
        final FastToken rotatedToken = new FastToken(USERNAME, FastTokenManager.HT_SHA_256_NONE,
            new byte[32], Instant.now().plusSeconds(3600));

        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_256_NONE), any()))
                .thenReturn(rotatedToken);

            final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
            final byte[] response = buildResponse(USERNAME, VALID_TOKEN_BYTES);
            final byte[] result = server.evaluateResponse(response);

            assertTrue(server.isComplete(), "Expected authentication to be complete after valid token.");
            assertNotNull(result, "Expected a non-null result.");
            assertEquals(0, result.length, "Expected an empty byte array on success.");
            assertEquals(USERNAME, server.getAuthorizationID(), "Expected authorization ID to match username.");
            assertNotNull(server.getRotatedToken(), "Expected a rotated token after successful authentication.");
        }
    }

    /**
     * Verifies that an invalid token results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForInvalidToken() {
        try (final MockedStatic<FastTokenManager> mocked = mockStatic(FastTokenManager.class)) {
            mocked.when(() -> FastTokenManager.validateToken(eq(USERNAME), eq(FastTokenManager.HT_SHA_256_NONE), any()))
                .thenReturn(null);

            final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
            final byte[] response = buildResponse(USERNAME, VALID_TOKEN_BYTES);

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
            // validateToken returns null for expired tokens.
            mocked.when(() -> FastTokenManager.validateToken(any(), any(), any()))
                .thenReturn(null);

            final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
            final byte[] response = buildResponse(USERNAME, VALID_TOKEN_BYTES);

            assertThrows(SaslException.class, () -> server.evaluateResponse(response),
                "Expected SaslException for an expired token.");
        }
    }

    /**
     * Verifies that a malformed response (missing commas) results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForMalformedResponse() {
        final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
        final byte[] malformed = "nocommashere".getBytes(StandardCharsets.UTF_8);

        assertThrows(SaslException.class, () -> server.evaluateResponse(malformed),
            "Expected SaslException for a malformed response.");
    }

    /**
     * Verifies that an empty response results in a SaslException.
     */
    @Test
    public void evaluateResponseShouldFailForEmptyResponse() {
        final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();

        assertThrows(SaslException.class, () -> server.evaluateResponse(new byte[0]),
            "Expected SaslException for an empty response.");
    }

    /**
     * Verifies that getAuthorizationID throws when authentication is not yet complete.
     */
    @Test
    public void getAuthorizationIdShouldThrowWhenNotComplete() {
        final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "Expected IllegalStateException when authentication is not complete.");
    }

    /**
     * Verifies that dispose() resets the server state.
     */
    @Test
    public void disposeShouldResetState() throws SaslException {
        final HtSha256NoneSaslServer server = new HtSha256NoneSaslServer();
        server.dispose();
        assertFalse(server.isComplete(), "Expected isComplete() to be false after dispose.");
        assertNull(server.getRotatedToken(), "Expected rotatedToken to be null after dispose.");
    }
}
