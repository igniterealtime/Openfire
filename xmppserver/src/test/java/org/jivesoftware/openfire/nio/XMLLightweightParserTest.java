/*
 * Copyright (C) 2021-2026 Ignite Realtime Foundation. All rights reserved.
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
import io.netty.buffer.ByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the functionality as implemented in {@link XMLLightweightParser}
 *
 * @author Guus der Kinderen, guus.der.kinderen@goodbytes.nl
 */
public class XMLLightweightParserTest {

    /**
     * Asserts that a start-tag name can be parsed when it is followed by a space character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329OpenAndCloseWithSpace() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence to='foo@example.org'></presence>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that a start-tag name can be parsed when it is followed by a space and a newline character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329OpenAndCloseWithSpaceAndNewline() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence \n to='foo@example.org'></presence>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that a start-tag name can be parsed when it is followed by a newline character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329OpenAndCloseWithNewline() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence\n to='foo@example.org'></presence>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that a self-terminating start-tag name can be parsed when it is followed by a space character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329SelfTerminatingWithSpace() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence to='foo@example.org'/>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that a self-terminating start-tag name can be parsed when it is followed by a space and a newline character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329SelfTerminatingWithSpaceAndNewline() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence\n to='foo@example.org'/>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that a self-terminating start-tag name can be parsed when it is followed by a newline character.
     *
     * This test checks for a variation of the issue described in OF-2329.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2329">OF-2329: XML parsing bug when tag-name is not followed by space or '>'</a>
     */
    @Test
    public void testOF2329SelfTerminatingWithNewline() throws Exception
    {
        // Setup test fixture.
        final String input = "<presence \n to='foo@example.org'/>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that {@link XMLLightweightParser} can parse a stanza that contains multi-byte characters.
     */
    @Test
    public void testOF2814MultiByteCharacters() throws Exception
    {
        // Setup test fixture.
        final String input = "<message to='foo@example.org'><body>\u3053\u308C\u306F\u30DE\u30EB\u30C1\u30D0\u30A4\u30C8\u6587\u5B57\u3092\u542B\u3080\u30C6\u30B9\u30C8 \u30D9\u30AF\u30C8\u30EB\u3067\u3059\u3002</body></message>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes().length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Asserts that {@link XMLLightweightParser} can parse a stanza that contains multi-byte characters, when the bytes
     * are provided in two arrays, that together make up the full string, even when the arrays are split in such a way
     * that one of the multi-byte characters is on the end of the first array, while its other bytes are at the start of
     * the next array.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-458">issue OF-458: XMPPDecoder has a decode problem for UTF-8</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2814">issue OF-2814: Issue with cyrilic (any multi-byte?) characters in messages</a>
     */
    @Test
    public void testOF2814MultiByteCharactersInTwoPasses() throws Exception
    {
        // Setup test fixture.
        final String input = "<message to='foo@example.org'><body>\u3053\u308C\u306F\u30DE\u30EB\u30C1\u30D0\u30A4\u30C8\u6587\u5B57\u3092\u542B\u3080\u30C6\u30B9\u30C8 \u30D9\u30AF\u30C8\u30EB\u3067\u3059\u3002</body></message>";
        final byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        final int snip = 36 + 3 + 2; // First 36 bytes correspond to the ASCII prefix. Then, various three-byte characters follow. We want to break in the middle of a character for this test.
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(inputBytes.length);
        buffer.writeBytes(Arrays.copyOfRange(inputBytes, 0, snip));
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        buffer.writeBytes(Arrays.copyOfRange(inputBytes, snip, inputBytes.length));
        parser.read(buffer);

        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    /**
     * Verifies that {@link XMLLightweightParser#decode(ByteBuf)} does not read anything from a byte array, and does not
     * progress its reader index, when the provided content is only the first byte of a multi-byte character.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-458">issue OF-458: XMPPDecoder has a decode problem for UTF-8</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2814">issue OF-2814: Issue with cyrilic (any multi-byte?) characters in messages</a>
     */
    @Test
    public void testDecodeFirstByteOfMultibyteChar() throws Exception
    {
        // Setup test fixture.
        final byte[] multibyteCharacter = "\u3053".getBytes(StandardCharsets.UTF_8); // 3-byte character.
        final XMLLightweightParser parser = new XMLLightweightParser();
        final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(3);
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 0, 1));

        // Execute system under test.
        final char[] result = parser.decode(in);

        // Verify results.
        assertEquals(0, result.length);
        assertEquals(0, in.readerIndex());
    }

    /**
     * Verifies that {@link XMLLightweightParser#decode(ByteBuf)} eventually reads an entire multi-byte character, even
     * if it is provided to the method in pieces.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-458">issue OF-458: XMPPDecoder has a decode problem for UTF-8</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2814">issue OF-2814: Issue with cyrilic (any multi-byte?) characters in messages</a>
     */
    @Test
    public void testDecodeAllByteOfMultibyteCharInSteps() throws Exception
    {
        // Setup test fixture.
        final byte[] multibyteCharacter = "\u3053".getBytes(StandardCharsets.UTF_8); // 3-byte character.
        assert multibyteCharacter.length == 3;
        final XMLLightweightParser parser = new XMLLightweightParser();
        final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(3);

        // Execute system under test.
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 0, 1));
        parser.decode(in);
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 1, 2));
        parser.decode(in);
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 2, 3));
        final char[] result = parser.decode(in);

        // Verify results.
        assertEquals(1, result.length);
        assertEquals(3, in.readerIndex());
    }

    /**
     * Verifies that {@link XMLLightweightParser#decode(ByteBuf)} only reads 'enough' bytes from an input buffer that
     * contains multi-byte characters to make up for 'complete' characters, leaving the 'read-index' (position) of that
     * buffer at the position of the next, unread byte.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-458">issue OF-458: XMPPDecoder has a decode problem for UTF-8</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2814">issue OF-2814: Issue with cyrilic (any multi-byte?) characters in messages</a>
     */
    @Test
    public void testDecodePartialMultibyteString() throws Exception
    {
        final byte[] multibyteCharacter = "\u3053\u308C".getBytes(StandardCharsets.UTF_8); // two 3-byte characters.
        assert multibyteCharacter.length == 6;
        final XMLLightweightParser parser = new XMLLightweightParser();
        final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(10);

        // Execute system under test.
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 0, 5));
        final char[] result = parser.decode(in);

        // Verify results.
        assertEquals(1, result.length);
        assertEquals('\u3053', result[0]);
        assertEquals(3, in.readerIndex()); // Even though we tried to read 5 bytes, the reader index should be on the first byte after the character that was successfully read!
    }

    /**
     * Verifies that {@link XMLLightweightParser#decode(ByteBuf)} can read a string of multi-byte characters, even if
     * their bytes are provided to the decoder in chunks that do not correspond with the character boundaries.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-458">issue OF-458: XMPPDecoder has a decode problem for UTF-8</a>
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2814">issue OF-2814: Issue with cyrilic (any multi-byte?) characters in messages</a>
     */
    @Test
    public void testDecodePartialMultibyteStringInSteps() throws Exception
    {
        final byte[] multibyteCharacter = "\u3053\u308C".getBytes(StandardCharsets.UTF_8); // two 3-byte characters.
        assert multibyteCharacter.length == 6;
        final XMLLightweightParser parser = new XMLLightweightParser();
        final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(10);

        // Execute system under test.
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 0, 2));
        parser.decode(in);
        in.writeBytes(Arrays.copyOfRange(multibyteCharacter, 2, 6));
        final char[] result = parser.decode(in);

        // Verify results.
        assertEquals(2, result.length);
        assertEquals('\u3053', result[0]);
        assertEquals('\u308C', result[1]);
        assertEquals(6, in.readerIndex());
    }

    /**
     * Verifies that {@link XMLLightweightParser#decode(ByteBuf)} updates the reader index of the provided buffer,
     * specifically when the provided byte buffer has a reader index that's higher than 0.
     *
     * <a href="https://igniterealtime.atlassian.net/browse/OF-2872">Issue OF-2872</a>
     */
    @Test
    public void testReaderWriterIndices() throws Exception
    {
        // Setup test fixture
        final XMLLightweightParser parser = new XMLLightweightParser();
        final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(10);
        in.writeBytes("foo".getBytes(StandardCharsets.UTF_8));
        in.readerIndex(3); // fake a read of the first three characters.

        // Execute system under test.
        in.writeBytes("bar".getBytes(StandardCharsets.UTF_8));
        parser.decode(in);

        // Verify result.
        assertEquals(6, in.readerIndex());
    }

    /**
     * Asserts that multiple stanzas written into a single buffer are each individually parsed
     * and returned by {@link XMLLightweightParser#getMsgs()}.
     */
    @Test
    public void testMultipleStanzasInSingleBuffer() throws Exception
    {
        // Setup test fixture.
        final String stanza1 = "<presence/>";
        final String stanza2 = "<message to='foo@example.org'><body>Hello</body></message>";
        final String input = stanza1 + stanza2;
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(stanza1, result[0]);
        assertEquals(stanza2, result[1]);
    }

    /**
     * Asserts that a stanza containing a CDATA section is parsed correctly as a single message.
     */
    @Test
    public void testStanzaWithCdataSection() throws Exception
    {
        // Setup test fixture.
        final String input = "<message to='foo@example.org'><body><![CDATA[<not-xml>&amp;]]></body></message>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(input, result[0]);
    }

    /**
     * Asserts that an XMPP stream header ({@code <stream:stream>}) is treated as a self-terminating element
     * and returned as a message by the parser.
     */
    @Test
    public void testStreamOpenIsReturnedAsMessage() throws Exception
    {
        // Setup test fixture.
        final String input = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='example.org' version='1.0'>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull(result);
        assertEquals(1, result.length, "The stream open element should be returned as a message.");
    }

    /**
     * Asserts that a stream close element ({@code </stream:stream>}) is parsed and returned as a message,
     * after first receiving an open stream element.
     */
    @Test
    public void testStreamCloseIsReturnedAsMessage() throws Exception
    {
        // Setup test fixture.
        final String streamOpen = "<stream:stream xmlns='jabber:client' xmlns:stream='http://etherx.jabber.org/streams' to='example.org' version='1.0'>";
        final String streamClose = "</stream:stream>";
        final XMLLightweightParser parser = new XMLLightweightParser();

        ByteBuf openBuffer = ByteBufAllocator.DEFAULT.buffer(streamOpen.getBytes(StandardCharsets.UTF_8).length);
        openBuffer.writeCharSequence(streamOpen, StandardCharsets.UTF_8);
        parser.read(openBuffer);
        parser.getMsgs(); // consume the open message

        ByteBuf closeBuffer = ByteBufAllocator.DEFAULT.buffer(streamClose.getBytes(StandardCharsets.UTF_8).length);
        closeBuffer.writeCharSequence(streamClose, StandardCharsets.UTF_8);

        // Execute system under test.
        parser.read(closeBuffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull(result);
        assertEquals(1, result.length, "The stream close element should be returned as a message.");
        assertEquals(streamClose, result[0]);
    }

    /**
     * Asserts that XML stanzas with nested child elements are parsed correctly as a single message.
     */
    @Test
    public void testNestedElementsAreReturnedAsOneMessage() throws Exception
    {
        // Setup test fixture.
        final String input = "<iq type='get' id='1'><query xmlns='jabber:iq:roster'><item jid='user@example.org'/></query></iq>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(input, result[0]);
    }

    /**
     * Asserts that a stanza containing an illegal XML control character (in the range 0x00-0x08)
     * causes an exception to be thrown by the parser.
     */
    @Test
    public void testIllegalControlCharacterThrowsException() throws Exception
    {
        // Setup test fixture: include a NUL character (0x00) in the stanza body, which is illegal in XML 1.0
        final String input = "<message><body>\u0001</body></message>";
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(input, StandardCharsets.UTF_8);
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        assertThrows(Exception.class, () -> parser.read(buffer),
            "Parsing a stanza with an illegal control character should throw an exception.");
    }

    /**
     * Asserts that after the parser's maximum buffer size is exceeded, the
     * {@link XMLLightweightParser#isMaxBufferSizeExceeded()} flag is set.
     * <p>
     * The buffer-size check in the parser happens at the <em>start</em> of each {@code read()} call,
     * so this test first fills the internal buffer by writing data that exceeds the limit, and then
     * triggers the check by performing a second read.
     * </p>
     */
    @Test
    public void testMaxBufferSizeExceededFlagIsSet() throws Exception
    {
        // Setup test fixture: build a partial stanza whose byte count exceeds the parser's max buffer size.
        final long maxSize = XMLLightweightParser.XMPP_PARSER_BUFFER_SIZE.getValue();
        final StringBuilder body = new StringBuilder("<message><body>");
        while (body.length() <= maxSize) {
            body.append("x".repeat(1024));
        }
        // Do NOT close the stanza - we need the parser to accumulate data without completing a message.
        final byte[] inputBytes = body.toString().getBytes(StandardCharsets.UTF_8);

        final XMLLightweightParser parser = new XMLLightweightParser();

        // First read: fills the internal buffer past the limit.
        ByteBuf firstBuffer = ByteBufAllocator.DEFAULT.buffer(inputBytes.length);
        firstBuffer.writeBytes(inputBytes);
        parser.read(firstBuffer); // no exception yet - check happens at start of read, before appending

        // Second read: the check at the top of read() detects the oversized buffer and throws.
        ByteBuf secondBuffer = ByteBufAllocator.DEFAULT.buffer(1);
        secondBuffer.writeCharSequence("x", StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> parser.read(secondBuffer),
            "A second read after exceeding the buffer limit should throw an exception.");

        // Verify results.
        assertTrue(parser.isMaxBufferSizeExceeded(),
            "The maxBufferSizeExceeded flag should be set after exceeding the parser's buffer limit.");
    }
}
