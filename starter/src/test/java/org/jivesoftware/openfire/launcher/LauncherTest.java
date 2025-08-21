/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.launcher;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link Launcher}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LauncherTest
{
    /**
     * Checks that parsing an XML file that contains a reference to an external entity fails.
     */
    @Test
    public void testXmlParsingXXE() throws Exception
    {
        // Setup test fixture.
        final File input = new File(URLDecoder.decode(getClass().getClassLoader().getResource("xxe.xml").getFile(), StandardCharsets.UTF_8));

        // Execute system under test / verify results.
        assertThrows(SAXException.class, () -> Launcher.parse(input));
    }

    /**
     * Happy flow: checks that parsing an XML file succeeds.
     */
    @Test
    public void testXmlParsingClean() throws Exception
    {
        // Setup test fixture.
        final File input = new File(URLDecoder.decode(getClass().getClassLoader().getResource("clean.xml").getFile(), StandardCharsets.UTF_8));

        // Execute system under test.
        final Document result = Launcher.parse(input);

        // Verify results.
        assertNotNull(result);
    }
}
