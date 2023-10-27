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
package org.jivesoftware.openfire.websocket;

import org.dom4j.*;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;

/**
 * Verifies the implementation of {@link WebSocketClientStanzaHandler}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class WebSocketClientStanzaHandlerTest
{
    /**
     * It is desired to collapse the 'open' element that's send as part of the websocket data exchange. This test
     * verifies that the {@link WebSocketClientStanzaHandler#withoutDeclaration(Document)} does not return an expanded
     * XML element.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2703">OF-2703</a>
     */
    @Test
    public void ensureOpenElementCollapsedTest() throws Exception
    {
        // Setup test fixture.
        final Element open = DocumentHelper.createElement(QName.get("open", "urn:ietf:params:xml:ns:xmpp-framing"));
        final Document document = DocumentHelper.createDocument(open);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        open.addAttribute("from", "example.org");
        open.addAttribute("id", "unittest123");
        open.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), "en");
        open.addAttribute("version", "1.0");

        // Execute system under test.
        final String result = WebSocketClientStanzaHandler.withoutDeclaration(document);

        // Verify results.
        assertFalse(result.contains("></open>"));
    }
}
