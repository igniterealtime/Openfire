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

import org.jivesoftware.openfire.fast.FastTokenManager;
import org.jivesoftware.openfire.fast.FastTokenManager.Ht2ValidationResult;
import org.junit.jupiter.api.Test;

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Conformance tests derived directly from HT-09 and HT2-02's ABNF and HMAC definitions. */
class FastSpecificationConformanceTest {

    private static final String AUTHCID = "testuser";
    private static final byte[] PROOF = new byte[32];

    @Test
    void ht09AcceptsAuthcidNulProofAndReturnsResponderProof() throws Exception {
        final byte[] response = join(AUTHCID.getBytes(StandardCharsets.UTF_8), new byte[] {0}, PROOF);
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE,
            Collections.emptyMap(), (u, m, p, c, i, r) -> new Ht2ValidationResult(null, PROOF));
        final byte[] responderProof = assertDoesNotThrow(() -> server.evaluateResponse(response));

        assertArrayEquals(PROOF, responderProof,
            "HT-09 requires the responder HMAC as SASL success data.");
    }

    @Test
    void ht09RejectsLegacyCommaSeparatedBearerToken() {
        final HtSaslServer server = new HtSaslServer(FastTokenManager.HT_SHA_256_NONE, Collections.emptyMap());
        assertThrows(SaslException.class, () -> server.evaluateResponse(
            "none,testuser,bearer-token".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ht2RejectsMalformedExtraInitiatorValues() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        final byte[] response = join(AUTHCID.getBytes(StandardCharsets.UTF_8), new byte[] {0},
            "not-a-key-value".getBytes(StandardCharsets.UTF_8), new byte[] {0}, PROOF);
        assertThrows(SaslException.class, () -> server.evaluateResponse(response));
    }

    @Test
    void ht2RejectsAuthcidLongerThan255Octets() {
        final Ht2SaslServer server = new Ht2SaslServer(FastTokenManager.HT2_SHA_256_NONE, Collections.emptyMap());
        final byte[] response = join("a".repeat(256).getBytes(StandardCharsets.UTF_8), new byte[] {0, 0}, PROOF);
        assertThrows(SaslException.class, () -> server.evaluateResponse(response));
    }

    private static byte[] join(final byte[]... values) {
        int length = 0;
        for (byte[] value : values) length += value.length;
        final byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }
}
