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
package org.jivesoftware.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link SAXReaderUtil}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class SAXReaderUtilTest
{
    /**
     * Asserts that XML text can be parsed when provided as a String.
     */
    @Test
    public void testString() throws Exception
    {
        // Setup test fixture.
        final String input = "<foo><bar>test</bar></foo>";

        // Execute system under test.
        final Document output = SAXReaderUtil.readDocument(input);

        // Verify result.
        assertNotNull(output);
        assertEquals("foo", output.getRootElement().getName());
        assertNotNull(output.getRootElement().element("bar"));
        assertEquals("test", output.getRootElement().elementText("bar"));
    }

    /**
     * Asserts that XML text can be parsed when provided as a Reader.
     */
    @Test
    public void testReader() throws Exception
    {
        // Setup test fixture.
        final Reader input = new StringReader("<foo><bar>test</bar></foo>");

        // Execute system under test.
        final Document output = SAXReaderUtil.readDocument(input);

        // Verify result.
        assertNotNull(output);
        assertEquals("foo", output.getRootElement().getName());
        assertNotNull(output.getRootElement().element("bar"));
        assertEquals("test", output.getRootElement().elementText("bar"));
    }

    /**
     * Asserts that XML text can be parsed when provided as an InputStream.
     */
    @Test
    public void testInputStream() throws Exception
    {
        // Setup test fixture.
        final InputStream input = new ByteArrayInputStream("<foo><bar>test</bar></foo>".getBytes(StandardCharsets.UTF_8));

        // Execute system under test.
        final Document output = SAXReaderUtil.readDocument(input);

        // Verify result.
        assertNotNull(output);
        assertEquals("foo", output.getRootElement().getName());
        assertNotNull(output.getRootElement().element("bar"));
        assertEquals("test", output.getRootElement().elementText("bar"));
    }

    /**
     * Without a ExecutorService, problems during parsing/reading data would cause a DocumentException. By executing
     * this implementation through a Callable, these Exceptions are wrapped in a ExecutorException. This test verifies
     * that a DocumentException, caused by a problem parsing XML, remains accessible through the ExecutorException that
     * is thrown.
     */
    @Test
    public void testDocumentException() throws Exception
    {
        // Setup test fixture.
        final InputStream input = new ByteArrayInputStream("this is not valid XML".getBytes(StandardCharsets.UTF_8));

        final ExecutionException result;
        try {
            // Execute system under test.
            SAXReaderUtil.readDocument(input);
            fail("An ExecutionException should have been thrown.");
            return;
        } catch (ExecutionException e) {
            result = e;
        }

        // Verify result.
        assertNotNull(result);
        assertNotNull(result.getCause());
        assertEquals(DocumentException.class, result.getCause().getClass());
    }
}
