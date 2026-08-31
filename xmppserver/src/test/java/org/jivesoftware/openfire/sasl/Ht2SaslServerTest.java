/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed Under The Apache License, Version 2.0 (the "License");
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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class Ht2SaslServerTest {
    private static final byte[] PROOF = new byte[32];
    private static final byte[] RESPONDER = {10, 20, 30, 40};

    @ParameterizedTest
    @ValueSource(strings = {"HT2-SHA-256-NONE", "HT2-SHA-512-NONE"})
    void authenticatesAndFramesMutualProof(final String mechanism) throws Exception {
        final Ht2SaslServer server = new Ht2SaslServer(mechanism, Collections.emptyMap(),
            (u, m, p, cb, i, r) -> new Ht2ValidationResult(null, RESPONDER));
        final byte[] result = server.evaluateResponse(message("user", "", PROOF));
        assertArrayEquals(new byte[] {0, 0}, Arrays.copyOf(result, 2));
        assertArrayEquals(RESPONDER, Arrays.copyOfRange(result, 2, result.length));
        assertEquals("user", server.getAuthorizationID());
    }

    @Test
    void passesAuthenticatedExtraValuesToValidator() throws Exception {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> {
                assertEquals("counter=7,flag=yes", i);
                assertArrayEquals(PROOF, p);
                return new Ht2ValidationResult(null, RESPONDER);
            });
        server.evaluateResponse(message("user", "counter=7,flag=yes", PROOF));
    }

    @Test
    void rejectsInvalidGrammarUtf8AndLimits() {
        final Ht2SaslServer server = server();
        assertThrows(SaslException.class, () -> server.evaluateResponse(message("user", "invalid", PROOF)));
        assertThrows(SaslException.class, () -> server().evaluateResponse(message("a".repeat(256), "", PROOF)));
        assertThrows(SaslException.class, () -> server().evaluateResponse(new byte[] {(byte) 0xc3, 0, 0, 1}));
    }

    @Test
    void mapsExpiredTokensToCredentialsExpired() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> Ht2ValidationResult.expired());
        final SaslFailureException error = assertThrows(SaslFailureException.class,
            () -> server.evaluateResponse(message("user", "", PROOF)));
        assertEquals(Failure.CREDENTIALS_EXPIRED, error.getFailure());
    }

    @Test
    void invalidTokenDoesNotMarkExchangeComplete() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, cb, i, r) -> null);

        final SaslFailureException failure = assertThrows(SaslFailureException.class,
            () -> server.evaluateResponse(message("user", "", PROOF)));
        assertArrayEquals(Ht2FailureResponse.encode(Ht2FailureResponse.INVALID_TOKEN), failure.getAdditionalData());
        assertFalse(server.isComplete());
    }

    @Test
    void malformedMessagesUseOtherErrorFailureFrame() {
        final SaslFailureException failure = assertThrows(SaslFailureException.class,
            () -> server().evaluateResponse(new byte[] {1, 2, 3}));
        assertArrayEquals(Ht2FailureResponse.encode(Ht2FailureResponse.OTHER_ERROR), failure.getAdditionalData());
    }

    private static Ht2SaslServer server() {
        return new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap(),
            (u, m, p, cb, i, r) -> new Ht2ValidationResult(null, RESPONDER));
    }

    private static byte[] message(final String authcid, final String extras, final byte[] proof) {
        final byte[] id = authcid.getBytes(StandardCharsets.UTF_8);
        final byte[] extra = extras.getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[id.length + extra.length + proof.length + 2];
        System.arraycopy(id, 0, result, 0, id.length);
        System.arraycopy(extra, 0, result, id.length + 1, extra.length);
        System.arraycopy(proof, 0, result, id.length + extra.length + 2, proof.length);
        return result;
    }
}
