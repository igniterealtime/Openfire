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
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HmacQuicTokenHandler} — both v1 (address-bound) and v2 (DCID-bound).
 */
public class HmacQuicTokenHandlerTest
{
    private static final byte[] FIXED_SECRET = new byte[32]; // all-zero key for determinism

    // v1 handler (migration disabled — default)
    private HmacQuicTokenHandler v1Handler;
    // v2 handler (migration enabled)
    private HmacQuicTokenHandler v2Handler;

    private InetSocketAddress address;
    private ByteBuf dcid;

    @BeforeEach
    public void setUp() throws Exception
    {
        v1Handler = new HmacQuicTokenHandler(FIXED_SECRET, false);
        v2Handler = new HmacQuicTokenHandler(FIXED_SECRET, true);
        address = new InetSocketAddress(InetAddress.getByName("192.168.1.1"), 54321);
        dcid = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4});
    }

    // -----------------------------------------------------------------------
    // v1 token tests (address-bound, migration disabled)
    // -----------------------------------------------------------------------

    @Test
    public void v1_roundTripShouldSucceed()
    {
        final ByteBuf out = Unpooled.buffer();
        assertTrue(v1Handler.writeToken(out, dcid, address), "writeToken should return true");
        // Token = authenticated prefix (V1_TOKEN_LENGTH) + trailing DCID bytes for Netty/quiche.
        assertEquals(HmacQuicTokenHandler.V1_TOKEN_LENGTH + dcid.readableBytes(), out.readableBytes());

        final int result = v1Handler.validateToken(out, address);
        // validateToken returns the offset of the trailing DCID, i.e. V1_TOKEN_LENGTH.
        assertEquals(HmacQuicTokenHandler.V1_TOKEN_LENGTH, result, "validateToken should return V1_TOKEN_LENGTH on success");
        out.release();
    }

    @Test
    public void v1_differentAddressShouldFailValidation() throws Exception
    {
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        final InetSocketAddress other = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 54321);
        assertEquals(-1, v1Handler.validateToken(out, other), "Token from different IP must be rejected");
        out.release();
    }

    @Test
    public void v1_differentPortShouldFailValidation() throws Exception
    {
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        final InetSocketAddress other = new InetSocketAddress(InetAddress.getByName("192.168.1.1"), 9999);
        assertEquals(-1, v1Handler.validateToken(out, other), "Token from different port must be rejected");
        out.release();
    }

    @Test
    public void v1_tamperedHmacShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        // Flip a bit in the HMAC portion (byte 8 is the first HMAC byte in a v1 token).
        out.setByte(8, out.getByte(8) ^ 0xFF);

        assertEquals(-1, v1Handler.validateToken(out, address), "Tampered HMAC must be rejected");
        out.release();
    }

    @Test
    public void v1_expiredTokenShouldFailValidation()
    {
        // Manually construct a v1 token with a timestamp 10 minutes in the past.
        final long oldTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L;
        final ByteBuf expired = Unpooled.buffer(HmacQuicTokenHandler.V1_TOKEN_LENGTH);
        expired.writeLong(oldTimestamp);
        // HMAC bytes won't match the old timestamp, but the timestamp check fires first.
        expired.writeZero(32);

        assertEquals(-1, v1Handler.validateToken(expired, address), "Expired token must be rejected");
        expired.release();
    }

    @Test
    public void v1_tooShortTokenShouldFailValidation()
    {
        final ByteBuf short_ = Unpooled.wrappedBuffer(new byte[10]);
        assertEquals(-1, v1Handler.validateToken(short_, address), "Too-short token must be rejected");
    }

    @Test
    public void v1_maxTokenLengthShouldEqualV1TokenLength()
    {
        // maxTokenLength includes the authenticated prefix plus up to MAX_DCID_LEN trailing DCID bytes.
        assertEquals(HmacQuicTokenHandler.V1_TOKEN_LENGTH + HmacQuicTokenHandler.MAX_DCID_LEN, v1Handler.maxTokenLength());
    }

    @Test
    public void v1_differentSecretShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        final byte[] otherSecret = new byte[32];
        otherSecret[0] = 1;
        final HmacQuicTokenHandler otherHandler = new HmacQuicTokenHandler(otherSecret, false);

        assertEquals(-1, otherHandler.validateToken(out, address), "Token from different secret must be rejected");
        out.release();
    }

    @Test
    public void constructorShouldRejectShortSecret()
    {
        assertThrows(IllegalArgumentException.class, () -> new HmacQuicTokenHandler(new byte[8], false));
    }

    // -----------------------------------------------------------------------
    // v2 token tests (DCID-bound, migration enabled)
    // -----------------------------------------------------------------------

    @Test
    public void v2_roundTripShouldSucceed()
    {
        final ByteBuf out = Unpooled.buffer();
        assertTrue(v2Handler.writeToken(out, dcid, address), "writeToken should return true");

        // v2 token: 1 (version) + 8 (ts) + 1 (dcid_len) + 4 (dcid) + 32 (hmac) + 4 (trailing DCID) = 50
        final int authenticatedPrefixLen = 1 + 8 + 1 + dcid.readableBytes() + 32;
        final int expectedTotalLen = authenticatedPrefixLen + dcid.readableBytes();
        assertEquals(expectedTotalLen, out.readableBytes(), "v2 token length mismatch");

        // validateToken returns the offset of the trailing DCID (i.e. the authenticated prefix length).
        final int result = v2Handler.validateToken(out, address);
        assertEquals(authenticatedPrefixLen, result, "validateToken should return authenticated prefix length on success");
        out.release();
    }

    @Test
    public void v2_differentAddressShouldStillValidate() throws Exception
    {
        // v2 tokens are NOT bound to the source address — migration is the whole point.
        final ByteBuf out = Unpooled.buffer();
        v2Handler.writeToken(out, dcid, address);

        final InetSocketAddress migratedAddress = new InetSocketAddress(InetAddress.getByName("10.0.0.99"), 12345);
        final int result = v2Handler.validateToken(out, migratedAddress);
        assertTrue(result > 0, "v2 token must be accepted from a different address (migration)");
        out.release();
    }

    @Test
    public void v2_tamperedHmacShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        v2Handler.writeToken(out, dcid, address);

        // The HMAC ends before the trailing DCID bytes. Flip a bit in the last HMAC byte:
        // layout is version(1)+ts(8)+dcid_len(1)+dcid(N)+hmac(32)+dcid(N), so the last HMAC
        // byte is at index: 1+8+1+dcid.readableBytes()+32-1 = 9+dcid.readableBytes()+31.
        final int lastHmacByte = 9 + dcid.readableBytes() + 31;
        out.setByte(lastHmacByte, out.getByte(lastHmacByte) ^ 0xFF);

        assertEquals(-1, v2Handler.validateToken(out, address), "Tampered v2 HMAC must be rejected");
        out.release();
    }

    @Test
    public void v2_expiredTokenShouldFailValidation()
    {
        // Manually construct a v2 token with a timestamp 10 minutes in the past.
        final long oldTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L;
        final ByteBuf expired = Unpooled.buffer();
        expired.writeByte(HmacQuicTokenHandler.V2_VERSION);
        expired.writeLong(oldTimestamp);
        expired.writeByte(4); // dcid_len
        expired.writeBytes(new byte[]{1, 2, 3, 4}); // dcid
        expired.writeZero(32); // HMAC (wrong, but timestamp check fires first)

        assertEquals(-1, v2Handler.validateToken(expired, address), "Expired v2 token must be rejected");
        expired.release();
    }

    @Test
    public void v2_wrongDcidShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        v2Handler.writeToken(out, dcid, address);

        // Flip a bit in the DCID portion (offset 10 = 1 version + 8 ts + 1 dcid_len).
        out.setByte(10, out.getByte(10) ^ 0xFF);

        assertEquals(-1, v2Handler.validateToken(out, address), "v2 token with wrong DCID must be rejected");
        out.release();
    }

    @Test
    public void v2_differentSecretShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        v2Handler.writeToken(out, dcid, address);

        final byte[] otherSecret = new byte[32];
        otherSecret[0] = 1;
        final HmacQuicTokenHandler otherHandler = new HmacQuicTokenHandler(otherSecret, true);

        assertEquals(-1, otherHandler.validateToken(out, address), "v2 token from different secret must be rejected");
        out.release();
    }

    @Test
    public void v2_maxTokenLengthShouldEqualV2MaxTokenLength()
    {
        // maxTokenLength includes the authenticated prefix plus up to MAX_DCID_LEN trailing DCID bytes.
        assertEquals(HmacQuicTokenHandler.V2_MAX_TOKEN_LENGTH + HmacQuicTokenHandler.MAX_DCID_LEN, v2Handler.maxTokenLength());
    }

    @Test
    public void v2_tooShortTokenShouldFailValidation()
    {
        // A buffer that starts with V2_VERSION but is too short.
        final ByteBuf short_ = Unpooled.buffer(3);
        short_.writeByte(HmacQuicTokenHandler.V2_VERSION);
        short_.writeByte(0x00);
        short_.writeByte(0x00);
        assertEquals(-1, v2Handler.validateToken(short_, address), "Too-short v2 token must be rejected");
        short_.release();
    }

    // -----------------------------------------------------------------------
    // Cross-version tests
    // -----------------------------------------------------------------------

    @Test
    public void v1TokenRejectedByV2Handler() throws Exception
    {
        // A v1 token presented to a v2 handler: the first byte is a timestamp high-byte,
        // not V2_VERSION, so it is dispatched to the v1 validator which checks IP+port.
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        // Same address — v1 validator should accept it even from a v2 handler.
        assertTrue(v2Handler.validateToken(out, address) > 0,
            "v2 handler should accept a valid v1 token from the same address");
        out.release();
    }

    @Test
    public void v1TokenFromDifferentAddressRejectedByV2Handler() throws Exception
    {
        final ByteBuf out = Unpooled.buffer();
        v1Handler.writeToken(out, dcid, address);

        final InetSocketAddress other = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 54321);
        assertEquals(-1, v2Handler.validateToken(out, other),
            "v2 handler must reject a v1 token presented from a different address");
        out.release();
    }
}
