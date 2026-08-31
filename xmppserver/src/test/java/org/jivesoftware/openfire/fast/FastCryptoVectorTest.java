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
package org.jivesoftware.openfire.fast;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** Deterministic cross-tool vectors for the HT-09/HT2-02 HMAC construction. */
class FastCryptoVectorTest {
    private static final byte[] TOKEN = "dGVzdC10b2tlbi0xMjM0".getBytes(StandardCharsets.UTF_8);

    @Test
    void sha256InitiatorAndResponderVectorsUseUtf8TokenString() {
        assertArrayEquals(hex("d5de6f075b21d5d685a877405140945c388ce94023496a6e592a8ab4ecd14924"),
            FastTokenManager.hmac(TOKEN, "Initiator".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertArrayEquals(hex("65ae53a63a69ad1c84aa4081ac0e604d5ff49a5699f29c96ed5ae8c2faee6d2a"),
            FastTokenManager.hmac(TOKEN, "Responder".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    }

    @Test
    void sha256ExtraValuesVector() {
        assertArrayEquals(hex("f6bbbe2d394f7eb987f2f81c6e90f0cd06974b38a8ad799bb7b55e63c23252f3"),
            FastTokenManager.hmac(TOKEN, "Initiatorcounter=7".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    }

    @Test
    void sha512ResponderVector() {
        assertArrayEquals(hex("cb8c5cbff59e895a208218098bc453eeb13e0085d043f2442baa794369b74372a"
                + "dd7fc832672bb0139859ed5c123bc72b5bafa15f09b830983c5afb13b31e301"),
            FastTokenManager.hmac(TOKEN, "Responder".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    }

    private static byte[] hex(final String value) {
        final byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
