/*
 * Copyright (C) 2024-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality as implemented in {@link NettyXMPPDecoder}.
 *
 * These tests use Netty's {@link EmbeddedChannel} to exercise the decoder in a realistic
 * pipeline context, without requiring a real network connection.
 */
public class NettyXMPPDecoderTest {

    private EmbeddedChannel channel;
    private NettyXMPPDecoder decoder;

    @BeforeEach
    public void setUp() {
        decoder = new NettyXMPPDecoder();
        channel = new EmbeddedChannel(decoder);
        // Set up the XML parser attribute that the decoder requires
        channel.attr(NettyConnectionHandler.XML_PARSER).set(new XMLLightweightParser());
    }

    @AfterEach
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    /**
     * Asserts that a single complete XMPP stanza is decoded from raw bytes and forwarded to the next
     * handler in the pipeline as a String.
     */
    @Test
    public void testDecodesSingleStanza() {
        // Setup test fixture.
        final String stanza = "<message to='user@example.org'><body>Hello</body></message>";
        final ByteBuf input = Unpooled.copiedBuffer(stanza, StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(input);

        // Verify results.
        final String result = channel.readInbound();
        assertNotNull(result);
        assertEquals(stanza, result);
        assertNull(channel.readInbound()); // Only one message expected
    }

    /**
     * Asserts that multiple complete XMPP stanzas written to the channel in a single buffer are
     * each individually decoded and forwarded to the next handler.
     */
    @Test
    public void testDecodesMultipleStanzasInSingleBuffer() {
        // Setup test fixture.
        final String stanza1 = "<presence/>";
        final String stanza2 = "<message to='user@example.org'><body>Hi</body></message>";
        final ByteBuf input = Unpooled.copiedBuffer(stanza1 + stanza2, StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(input);

        // Verify results.
        final String result1 = channel.readInbound();
        final String result2 = channel.readInbound();
        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(stanza1, result1);
        assertEquals(stanza2, result2);
        assertNull(channel.readInbound()); // No more messages expected
    }

    /**
     * Asserts that an XMPP stanza split across two separate network reads is correctly reassembled
     * and decoded as a single complete message.
     */
    @Test
    public void testDecodesStanzaSplitAcrossMultipleReads() {
        // Setup test fixture.
        final String stanza = "<message to='user@example.org'><body>Hello</body></message>";
        final int midpoint = stanza.length() / 2;
        final ByteBuf part1 = Unpooled.copiedBuffer(stanza.substring(0, midpoint), StandardCharsets.UTF_8);
        final ByteBuf part2 = Unpooled.copiedBuffer(stanza.substring(midpoint), StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(part1);
        assertNull(channel.readInbound()); // Should not yet have a complete message

        channel.writeInbound(part2);

        // Verify results.
        final String result = channel.readInbound();
        assertNotNull(result);
        assertEquals(stanza, result);
    }

    /**
     * Asserts that when the channel receives an SSL handshake exception (wrapped in a {@link DecoderException}),
     * the channel is closed and no error is propagated as an additional exception to the next handler.
     */
    @Test
    public void testExceptionCaughtHandlesSslHandshakeError() {
        // Setup test fixture.
        final DecoderException cause = new DecoderException(new SSLHandshakeException("TLS handshake failed"));

        // Execute system under test - fire the exception through the pipeline.
        channel.pipeline().fireExceptionCaught(cause);
        channel.runPendingTasks();

        // Verify results: channel should be closed after an SSL handshake exception.
        assertFalse(channel.isOpen(), "Channel should be closed after an SSL handshake exception.");
    }

    /**
     * Asserts that when the channel receives a non-SSL-record exception (wrapped in a {@link DecoderException}),
     * the channel is closed and no error is propagated as an additional exception to the next handler.
     */
    @Test
    public void testExceptionCaughtHandlesNotSslRecordException() {
        // Setup test fixture.
        final DecoderException cause = new DecoderException(new NotSslRecordException("Not an SSL record"));

        // Execute system under test.
        channel.pipeline().fireExceptionCaught(cause);
        channel.runPendingTasks();

        // Verify results: channel should be closed after a non-SSL record exception.
        assertFalse(channel.isOpen(), "Channel should be closed after a NotSslRecordException.");
    }

    /**
     * Asserts that when the channel receives a connection-reset exception, the channel is closed
     * and no error is propagated as an additional exception to the next handler.
     */
    @Test
    public void testExceptionCaughtHandlesConnectionReset() {
        // Setup test fixture.
        final DecoderException cause = new DecoderException(new SocketException("Connection reset"));

        // Execute system under test.
        channel.pipeline().fireExceptionCaught(cause);
        channel.runPendingTasks();

        // Verify results: channel should be closed after a connection reset.
        assertFalse(channel.isOpen(), "Channel should be closed after a connection reset.");
    }

    /**
     * Asserts that when the channel receives a generic exception, the channel is closed.
     */
    @Test
    public void testExceptionCaughtHandlesGenericException() {
        // Setup test fixture.
        final RuntimeException cause = new RuntimeException("Unexpected error");

        // Execute system under test.
        channel.pipeline().fireExceptionCaught(cause);
        channel.runPendingTasks();

        // Verify results: channel should be closed after a generic exception.
        assertFalse(channel.isOpen(), "Channel should be closed after a generic exception.");
    }

    /**
     * Asserts that the XMPP stream open element is decoded and passed as a message.
     * The stream:stream opening is a special case in XMPP where the element is not self-closing.
     */
    @Test
    public void testDecodesXmppStreamOpen() {
        // Setup test fixture.
        final String streamOpen = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='example.org' version='1.0'>";
        final ByteBuf input = Unpooled.copiedBuffer(streamOpen, StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(input);

        // Verify results.
        final String result = channel.readInbound();
        assertNotNull(result, "Stream open element should be decoded as a message.");
    }

    /**
     * Asserts that a stanza containing CDATA sections is correctly decoded.
     */
    @Test
    public void testDecodesCdataSection() {
        // Setup test fixture.
        final String stanza = "<message to='user@example.org'><body><![CDATA[<not-xml>]]></body></message>";
        final ByteBuf input = Unpooled.copiedBuffer(stanza, StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(input);

        // Verify results.
        final String result = channel.readInbound();
        assertNotNull(result);
        assertEquals(stanza, result);
    }

    /**
     * Asserts that self-closing stanzas (those in the form {@code <tag/>}) are correctly decoded.
     */
    @Test
    public void testDecodesSelfClosingStanza() {
        // Setup test fixture.
        final String stanza = "<presence/>";
        final ByteBuf input = Unpooled.copiedBuffer(stanza, StandardCharsets.UTF_8);

        // Execute system under test.
        channel.writeInbound(input);

        // Verify results.
        final String result = channel.readInbound();
        assertNotNull(result);
        assertEquals(stanza, result);
    }
}
