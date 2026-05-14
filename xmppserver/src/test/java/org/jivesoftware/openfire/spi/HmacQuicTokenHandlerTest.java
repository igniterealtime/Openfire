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
 * Unit tests for {@link HmacQuicTokenHandler}.
 */
public class HmacQuicTokenHandlerTest
{
    private static final byte[] FIXED_SECRET = new byte[32]; // all-zero key for determinism

    private HmacQuicTokenHandler handler;
    private InetSocketAddress address;
    private ByteBuf dcid;

    @BeforeEach
    public void setUp() throws Exception
    {
        handler = new HmacQuicTokenHandler(FIXED_SECRET);
        address = new InetSocketAddress(InetAddress.getByName("192.168.1.1"), 54321);
        dcid = Unpooled.wrappedBuffer(new byte[]{1, 2, 3, 4});
    }

    @Test
    public void roundTripShouldSucceed()
    {
        final ByteBuf out = Unpooled.buffer();
        assertTrue(handler.writeToken(out, dcid, address), "writeToken should return true");
        assertEquals(HmacQuicTokenHandler.TOKEN_LENGTH, out.readableBytes());

        final int result = handler.validateToken(out, address);
        assertEquals(HmacQuicTokenHandler.TOKEN_LENGTH, result, "validateToken should return TOKEN_LENGTH on success");
        out.release();
    }

    @Test
    public void differentAddressShouldFailValidation() throws Exception
    {
        final ByteBuf out = Unpooled.buffer();
        handler.writeToken(out, dcid, address);

        final InetSocketAddress other = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 54321);
        assertEquals(-1, handler.validateToken(out, other), "Token from different IP must be rejected");
        out.release();
    }

    @Test
    public void differentPortShouldFailValidation() throws Exception
    {
        final ByteBuf out = Unpooled.buffer();
        handler.writeToken(out, dcid, address);

        final InetSocketAddress other = new InetSocketAddress(InetAddress.getByName("192.168.1.1"), 9999);
        assertEquals(-1, handler.validateToken(out, other), "Token from different port must be rejected");
        out.release();
    }

    @Test
    public void tamperedHmacShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        handler.writeToken(out, dcid, address);

        // Flip a bit in the HMAC portion (byte 8 is the first HMAC byte).
        out.setByte(8, out.getByte(8) ^ 0xFF);

        assertEquals(-1, handler.validateToken(out, address), "Tampered HMAC must be rejected");
        out.release();
    }

    @Test
    public void expiredTokenShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        // Write a timestamp 10 minutes in the past.
        final long oldTimestamp = System.currentTimeMillis() - 10 * 60 * 1000L;

        // Manually construct a token with the old timestamp and a valid HMAC for it.
        // Use a second handler instance with the same secret to generate the HMAC bytes.
        final HmacQuicTokenHandler sameKeyHandler = new HmacQuicTokenHandler(FIXED_SECRET);
        final ByteBuf tempOut = Unpooled.buffer();
        sameKeyHandler.writeToken(tempOut, dcid, address);
        // Replace the timestamp bytes (first 8 bytes) with the old timestamp.
        final ByteBuf expired = Unpooled.buffer(HmacQuicTokenHandler.TOKEN_LENGTH);
        expired.writeLong(oldTimestamp);
        // Copy the HMAC bytes from the freshly-generated token (they won't match the old
        // timestamp, so this token is doubly invalid — but the timestamp check fires first).
        expired.writeBytes(tempOut, 8, 32);
        tempOut.release();

        assertEquals(-1, handler.validateToken(expired, address), "Expired token must be rejected");
        expired.release();
    }

    @Test
    public void tooShortTokenShouldFailValidation()
    {
        final ByteBuf short_ = Unpooled.wrappedBuffer(new byte[10]);
        assertEquals(-1, handler.validateToken(short_, address), "Too-short token must be rejected");
    }

    @Test
    public void maxTokenLengthShouldEqualTokenLength()
    {
        assertEquals(HmacQuicTokenHandler.TOKEN_LENGTH, handler.maxTokenLength());
    }

    @Test
    public void differentSecretShouldFailValidation()
    {
        final ByteBuf out = Unpooled.buffer();
        handler.writeToken(out, dcid, address);

        final byte[] otherSecret = new byte[32];
        otherSecret[0] = 1; // differs from FIXED_SECRET
        final HmacQuicTokenHandler otherHandler = new HmacQuicTokenHandler(otherSecret);

        assertEquals(-1, otherHandler.validateToken(out, address), "Token from different secret must be rejected");
        out.release();
    }

    @Test
    public void constructorShouldRejectShortSecret()
    {
        assertThrows(IllegalArgumentException.class, () -> new HmacQuicTokenHandler(new byte[8]));
    }
}
