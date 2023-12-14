/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.session;

import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests that verify the implementation of {@link Session}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class SessionTest
{
    @Test
    public void testDecodeVersion() throws Exception
    {
        // Setup test fixture.

        // Execute system under test.
        final int[] result = Session.decodeVersion("987.65");

        // Verify results.
        assertEquals(987, result[0]);
        assertEquals(65, result[1]);
    }

    @Test
    public void testDetectVersion() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream version='0.9'/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final int[] result = Session.detectVersion(parser);

        // Verify results.
        assertEquals(0, result[0]);
        assertEquals(9, result[1]);
    }

    @Test
    public void testDetectVersionHigh() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream version='" + (Session.MAJOR_VERSION+10) + "." + (Session.MINOR_VERSION+11) + "'/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final int[] result = Session.detectVersion(parser);

        // Verify results.
        assertEquals(Session.MAJOR_VERSION, result[0]);
        assertEquals(Session.MINOR_VERSION, result[1]);
    }

    @Test
    public void testDetectVersionInvalidValue() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream version='error'/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final int[] result = Session.detectVersion(parser);

        // Verify results.
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void testDetectVersionNoAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final int[] result = Session.detectVersion(parser);

        // Verify results.
        assertEquals(0, result[0]);
        assertEquals(0, result[1]);
    }

    @Test
    public void testDetectLanguage() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream lang='it'/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final Locale result = Session.detectLanguage(parser);

        // Verify results.
        assertEquals("it", result.getLanguage());
    }

    @Test
    public void testDetectLanguageInvalidValue() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream lang='345345'/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final Locale result = Session.detectLanguage(parser);

        // Verify results.
        assertNotNull(result);
    }

    @Test
    public void testDetectLanguageNoAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader("<stream/>"));
        int v;
        do {
            v = parser.next();
        } while (v != XmlPullParser.START_TAG);

        // Execute system under test.
        final Locale result = Session.detectLanguage(parser);

        // Verify results.
        assertEquals("en", result.getLanguage());
    }
}
