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

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

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
        final IoBuffer buffer = IoBuffer.allocate(input.length(), false);
        buffer.putString(input, StandardCharsets.UTF_8.newEncoder());
        buffer.flip();
        final XMLLightweightParser parser = new XMLLightweightParser(StandardCharsets.UTF_8);

        // Execute system under test.
        parser.read(buffer);
        final String[] result = parser.getMsgs();

        // Verify results.
        assertNotNull( result );
        assertEquals(1, result.length );
    }

    @Test
    public void testHasIllegalCharacterReferencesFindsIllegalDecimalChars() throws Exception
    {
        final String input = "test &#65; test";
        assertFalse(XMLLightweightParser.hasIllegalCharacterReferences(input));
    }

    @Test
    public void testHasIllegalCharacterReferencesFindsLegalDecimalChars() throws Exception
    {
        final String input = "test &#7; test";
        assertTrue(XMLLightweightParser.hasIllegalCharacterReferences(input));
    }

    @Test
    public void testHasIllegalCharacterReferencesFindsIllegalHexChars() throws Exception
    {
        final String input = "test &#x41; test";
        assertFalse(XMLLightweightParser.hasIllegalCharacterReferences(input));
    }

    @Test
    public void testHasIllegalCharacterReferencesFindsLegalHexChars() throws Exception
    {
        final String input = "test &#x7; test";
        assertTrue(XMLLightweightParser.hasIllegalCharacterReferences(input));
    }
}
