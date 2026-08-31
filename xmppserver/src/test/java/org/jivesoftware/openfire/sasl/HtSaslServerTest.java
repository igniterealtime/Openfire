/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HtSaslServerTest {
    private static final byte[] PROOF = new byte[32];
    private static final byte[] RESPONDER = new byte[] {1, 2, 3};

    @ParameterizedTest
    @ValueSource(strings = {"HT-SHA-256-NONE", "HT-SHA-512-NONE"})
    void authenticatesHt09AndReturnsMutualAuthenticationProof(final String mechanism) throws Exception {
        final HtSaslServer server = new HtSaslServer(mechanism, Collections.emptyMap(),
            (u, m, p, cb, i, r) -> new Ht2ValidationResult(null, RESPONDER));
        assertArrayEquals(RESPONDER, server.evaluateResponse(message("user", PROOF)));
        assertTrue(server.isComplete());
        assertEquals("user", server.getAuthorizationID());
    }

    @Test
    void passesTheExactInitiatorProofToTheValidator() throws Exception {
        final boolean[] called = {false};
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> {
                assertEquals("user", u);
                assertArrayEquals(PROOF, p);
                assertArrayEquals(new byte[0], cb);
                assertEquals("", i);
                called[0] = true;
                return new Ht2ValidationResult(null, RESPONDER);
            });
        server.evaluateResponse(message("user", PROOF));
        assertTrue(called[0]);
    }

    @Test
    void mapsExpiredTokensToCredentialsExpired() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> Ht2ValidationResult.expired());
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> server.evaluateResponse(message("user", PROOF)));
        assertEquals(Failure.CREDENTIALS_EXPIRED, error.getFailure());
    }

    @Test
    void invalidTokenDoesNotMarkExchangeComplete() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> null);

        assertThrows(SaslException.class, () -> server.evaluateResponse(message("user", PROOF)));
        assertFalse(server.isComplete());
    }

    @Test
    void disabledAccountIsRejectedBeforeTokenValidation() {
        final LockOutManager lockOutManager = mock(LockOutManager.class);
        when(lockOutManager.isAccountDisabled("user")).thenReturn(true);
        try (final var lockOut = mockStatic(LockOutManager.class);
             final var tokens = mockStatic(FastTokenManager.class)) {
            lockOut.when(LockOutManager::getInstance).thenReturn(lockOutManager);
            final HtSaslServer server = new HtSaslServer(
                FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());

            final SaslFailureException error = assertThrows(SaslFailureException.class,
                () -> server.evaluateResponse(message("user", PROOF)));
            assertEquals(Failure.NOT_AUTHORIZED, error.getFailure());
            assertArrayEquals(Ht2FailureResponse.encode(Ht2FailureResponse.INVALID_TOKEN), error.getAdditionalData());
            verify(lockOutManager).recordFailedLogin("user");
            tokens.verifyNoInteractions();
        }
    }

    @Test
    void rejectsMalformedAndOversizeMessages() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> null);
        assertThrows(SaslException.class, () -> server.evaluateResponse("user,token".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SaslException.class, () -> server.evaluateResponse(message("a".repeat(256), PROOF)));
        assertThrows(SaslException.class, () -> server.evaluateResponse(new byte[0]));
    }

    private static byte[] message(final String authcid, final byte[] proof) {
        final byte[] id = authcid.getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[id.length + 1 + proof.length];
        System.arraycopy(id, 0, result, 0, id.length);
        System.arraycopy(proof, 0, result, id.length + 1, proof.length);
        return result;
    }
}
