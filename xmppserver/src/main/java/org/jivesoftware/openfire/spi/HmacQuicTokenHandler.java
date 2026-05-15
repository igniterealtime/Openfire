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
package org.jivesoftware.openfire.spi;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.quic.QuicTokenHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;

/**
 * A stateless address-validation token handler for QUIC that uses HMAC-SHA256.
 *
 * <h2>Token formats</h2>
 *
 * <p>Netty's {@link QuicTokenHandler} contract requires that the raw original DCID bytes are
 * appended <em>after</em> the authenticated token data. The {@link #validateToken} return value
 * is the byte offset at which the DCID starts; Netty reads {@code token[offset:]} and passes
 * those bytes to quiche as the {@code odcid} (original destination connection ID) for the
 * {@code retry_source_connection_id} transport-parameter check (RFC 9000 §7.3 / §8.1).
 * Omitting the trailing DCID causes quiche to receive an empty odcid and close the connection
 * with TRANSPORT_PARAMETER_ERROR / "CID authentication failure".</p>
 *
 * <h3>v1 — address-bound (default, migration disabled)</h3>
 * <pre>
 *   [ timestamp : 8 bytes (big-endian ms since epoch) ]
 *   [ HMAC-SHA256(secret, ip | port(2 BE) | timestamp) : 32 bytes ]
 *   [ original DCID : 0–20 bytes  (appended for Netty/quiche; not authenticated separately) ]
 * </pre>
 * Authenticated prefix length: {@value #V1_TOKEN_LENGTH} bytes.
 * The token is bound to the client's IP address and port. A migrated client will receive a
 * Retry and must complete a fresh handshake from its new address.
 *
 * <h3>v2 — DCID-bound (migration enabled)</h3>
 * <pre>
 *   [ version  : 1 byte  = 0x02 ]
 *   [ timestamp: 8 bytes (big-endian ms since epoch) ]
 *   [ dcid_len : 1 byte  (0–20) ]
 *   [ dcid     : dcid_len bytes ]
 *   [ HMAC-SHA256(secret, dcid | timestamp) : 32 bytes ]
 *   [ original DCID : 0–20 bytes  (appended for Netty/quiche) ]
 * </pre>
 * Authenticated prefix length: 10 + dcid_len + 32 bytes (max {@value #V2_MAX_TOKEN_LENGTH}).
 * The token is bound to the Destination Connection ID but NOT to the source address, so a
 * migrated client can present it from a new UDP 4-tuple. v2 tokens are only issued when
 * {@code migrationEnabled} is {@code true}.
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li>v1 tokens are bound to IP+port, preventing replay from a different address.</li>
 *   <li>v2 tokens are bound to the DCID; they must only be issued <em>after</em> the initial
 *       address validation has already succeeded (i.e. as part of the Retry flow for a
 *       connection that has already been validated). Issuing v2 tokens as the first Retry
 *       would weaken amplification protection.</li>
 *   <li>Both token types expire after {@link #TOKEN_VALIDITY} to limit the replay window.</li>
 *   <li>The HMAC key is generated fresh at server startup; tokens issued before a restart
 *       are automatically invalidated.</li>
 * </ul>
 */
public final class HmacQuicTokenHandler implements QuicTokenHandler
{
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HMAC_LENGTH = 32;       // SHA-256 output
    private static final int TIMESTAMP_LENGTH = 8;   // long, big-endian

    /** Version byte stored at offset 0 of a v2 token. */
    static final byte V2_VERSION = 0x02;

    /** Length of the authenticated prefix of a v1 token (timestamp + HMAC, no version byte). */
    static final int V1_TOKEN_LENGTH = TIMESTAMP_LENGTH + HMAC_LENGTH; // 40

    /** Maximum DCID length per RFC 9000 §17.2. */
    static final int MAX_DCID_LEN = 20;

    /**
     * Maximum length of the authenticated prefix of a v2 token:
     * 1 (version) + 8 (timestamp) + 1 (dcid_len) + 20 (dcid) + 32 (HMAC).
     */
    static final int V2_MAX_TOKEN_LENGTH = 1 + TIMESTAMP_LENGTH + 1 + MAX_DCID_LEN + HMAC_LENGTH; // 62

    /** Tokens older than this are rejected. */
    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(5);

    private final byte[] secret;
    private final boolean migrationEnabled;

    /**
     * Creates a new handler with a freshly-generated random secret and migration disabled.
     * Tokens issued by this instance are invalidated when the instance is discarded (e.g. on server restart).
     */
    public HmacQuicTokenHandler()
    {
        this(randomSecret(), false);
    }

    /**
     * Creates a new handler with a freshly-generated random secret.
     *
     * @param migrationEnabled  when {@code true} the handler issues v2 (DCID-bound) tokens that
     *                          allow a client to migrate to a new UDP 4-tuple without re-handshaking.
     */
    public HmacQuicTokenHandler(final boolean migrationEnabled)
    {
        this(randomSecret(), migrationEnabled);
    }

    /**
     * Creates a new handler with the supplied secret. Useful for testing or for sharing tokens
     * across a cluster (all nodes must use the same secret).
     *
     * @param secret            32-byte HMAC key material; copied defensively.
     * @param migrationEnabled  when {@code true} the handler issues v2 (DCID-bound) tokens.
     */
    public HmacQuicTokenHandler(final byte[] secret, final boolean migrationEnabled)
    {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("HMAC secret must be at least 16 bytes");
        }
        this.secret = Arrays.copyOf(secret, secret.length);
        this.migrationEnabled = migrationEnabled;
    }

    // ------------------------------------------------------------------
    // QuicTokenHandler implementation
    // ------------------------------------------------------------------

    @Override
    public boolean writeToken(final ByteBuf out, final ByteBuf dcid, final InetSocketAddress address)
    {
        final long now = System.currentTimeMillis();
        final boolean written;
        if (migrationEnabled) {
            written = writeV2Token(out, dcid, now);
        } else {
            written = writeV1Token(out, address, now);
        }
        // Netty requires the raw original DCID bytes to be appended after the authenticated
        // token data. It reads token[validateToken(...):]  and passes those bytes to quiche
        // as the odcid for the retry_source_connection_id transport-parameter check
        // (RFC 9000 §7.3). Without this, quiche receives an empty odcid and closes the
        // connection with TRANSPORT_PARAMETER_ERROR / "CID authentication failure".
        if (written) {
            out.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
        }
        return written;
    }

    @Override
    public int validateToken(final ByteBuf token, final InetSocketAddress address)
    {
        if (token.readableBytes() < 1) {
            return -1;
        }
        // Peek at the first byte to determine the token version.
        // v2 tokens start with V2_VERSION (0x02); v1 tokens start with the high byte of a
        // millisecond timestamp, which is never 0x02 for any reasonable system clock value
        // (0x02 would correspond to a timestamp in January 1970, well outside TOKEN_VALIDITY).
        final byte firstByte = token.getByte(token.readerIndex());
        if (firstByte == V2_VERSION) {
            return validateV2Token(token);
        } else {
            return validateV1Token(token, address);
        }
    }

    @Override
    public int maxTokenLength()
    {
        // Each token format appends up to MAX_DCID_LEN raw DCID bytes after the authenticated
        // prefix so that Netty can extract and pass them to quiche as the odcid.
        return (migrationEnabled ? V2_MAX_TOKEN_LENGTH : V1_TOKEN_LENGTH) + MAX_DCID_LEN;
    }

    // ------------------------------------------------------------------
    // v1 token (address-bound)
    // ------------------------------------------------------------------

    private boolean writeV1Token(final ByteBuf out, final InetSocketAddress address, final long now)
    {
        final byte[] hmac = computeV1Hmac(address, now);
        if (hmac == null) {
            return false;
        }
        out.writeLong(now);
        out.writeBytes(hmac);
        return true;
    }

    private int validateV1Token(final ByteBuf token, final InetSocketAddress address)
    {
        if (token.readableBytes() < V1_TOKEN_LENGTH) {
            return -1;
        }
        final long timestamp = token.getLong(token.readerIndex());
        if (!isTimestampValid(timestamp)) {
            return -1;
        }
        final byte[] expected = computeV1Hmac(address, timestamp);
        if (expected == null) {
            return -1;
        }
        final byte[] actual = new byte[HMAC_LENGTH];
        token.getBytes(token.readerIndex() + TIMESTAMP_LENGTH, actual);
        if (!constantTimeEquals(expected, actual)) {
            return -1;
        }
        return V1_TOKEN_LENGTH;
    }

    private byte[] computeV1Hmac(final InetSocketAddress address, final long timestamp)
    {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(address.getAddress().getAddress()); // 4 or 16 bytes
            final int port = address.getPort();
            mac.update((byte) (port >> 8));
            mac.update((byte) (port & 0xFF));
            mac.update(longToBytes(timestamp));
            return mac.doFinal();
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    // ------------------------------------------------------------------
    // v2 token (DCID-bound, migration-capable)
    // ------------------------------------------------------------------

    private boolean writeV2Token(final ByteBuf out, final ByteBuf dcid, final long now)
    {
        final int dcidLen = Math.min(dcid.readableBytes(), MAX_DCID_LEN);
        final byte[] dcidBytes = new byte[dcidLen];
        dcid.getBytes(dcid.readerIndex(), dcidBytes);

        final byte[] hmac = computeV2Hmac(dcidBytes, now);
        if (hmac == null) {
            return false;
        }
        out.writeByte(V2_VERSION);
        out.writeLong(now);
        out.writeByte(dcidLen);
        out.writeBytes(dcidBytes);
        out.writeBytes(hmac);
        return true;
    }

    private int validateV2Token(final ByteBuf token)
    {
        // Layout: version(1) + timestamp(8) + dcid_len(1) + dcid(N) + hmac(32)
        final int minLen = 1 + TIMESTAMP_LENGTH + 1 + HMAC_LENGTH;
        if (token.readableBytes() < minLen) {
            return -1;
        }
        int offset = token.readerIndex();
        offset += 1; // skip version byte (already checked by caller)

        final long timestamp = token.getLong(offset);
        offset += TIMESTAMP_LENGTH;
        if (!isTimestampValid(timestamp)) {
            return -1;
        }

        final int dcidLen = token.getUnsignedByte(offset);
        offset += 1;
        if (dcidLen > MAX_DCID_LEN) {
            return -1;
        }
        if (token.readableBytes() < 1 + TIMESTAMP_LENGTH + 1 + dcidLen + HMAC_LENGTH) {
            return -1;
        }

        final byte[] dcidBytes = new byte[dcidLen];
        token.getBytes(offset, dcidBytes);
        offset += dcidLen;

        final byte[] expected = computeV2Hmac(dcidBytes, timestamp);
        if (expected == null) {
            return -1;
        }
        final byte[] actual = new byte[HMAC_LENGTH];
        token.getBytes(offset, actual);

        if (!constantTimeEquals(expected, actual)) {
            return -1;
        }
        return 1 + TIMESTAMP_LENGTH + 1 + dcidLen + HMAC_LENGTH;
    }

    private byte[] computeV2Hmac(final byte[] dcidBytes, final long timestamp)
    {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(dcidBytes);
            mac.update(longToBytes(timestamp));
            return mac.doFinal();
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private boolean isTimestampValid(final long timestamp)
    {
        final long age = System.currentTimeMillis() - timestamp;
        return age >= 0 && age <= TOKEN_VALIDITY.toMillis();
    }

    private static byte[] longToBytes(final long value)
    {
        final byte[] b = new byte[TIMESTAMP_LENGTH];
        long v = value;
        for (int i = 7; i >= 0; i--) {
            b[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        return b;
    }

    /** Constant-time byte-array comparison to prevent timing side-channels. */
    static boolean constantTimeEquals(final byte[] a, final byte[] b)
    {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private static byte[] randomSecret()
    {
        final byte[] s = new byte[32];
        new SecureRandom().nextBytes(s);
        return s;
    }
}
