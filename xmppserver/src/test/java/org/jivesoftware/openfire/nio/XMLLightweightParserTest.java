/*
 * Copyright (C) 2021-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests that verify the functionality as implemented in {@link XMLLightweightParser}
 *
 * @author Guus der Kinderen, guus.der.kinderen@goodbytes.nl
 */
public class XMLLightweightParserTest {

    private CharsetDecoder encoder;
    private XMLLightweightParser parser;

    @BeforeEach
    public void setUp() throws Exception {
        parser = new XMLLightweightParser();

        encoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    private char[] BytBufToChars(ByteBuf buffer) {
        CharBuffer charBuffer = CharBuffer.allocate(buffer.capacity());
        encoder.decode(buffer.nioBuffer(), charBuffer, false);
        char[] buf = new char[charBuffer.position()];
        charBuffer.flip();
        charBuffer.get(buf);
        return buf;
    }

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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
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
        ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(input.length());
        buffer.writeBytes(input.getBytes());
        final XMLLightweightParser parser = new XMLLightweightParser();

        // Execute system under test.
        parser.read(BytBufToChars(buffer));
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }
}
