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
package org.jivesoftware.openfire.csi;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies the implementation of {@link CsiManager}
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class CsiManagerTest
{
    /**
     * Verifies that a stanza Indicating Intent to Start a Session (Jingle) is <em>not</em> identified as a stanza that
     * can be delayed/queued in context of CSI.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2750">OF-2750: CSI-enabled client does not receive Jingle invitations</a>
     */
    @Test
    public void testJinglePropose() throws Exception
    {
        // Setup test fixture.
        final Packet input = parse("<message type=\"chat\" id=\"jm-propose-LE3clSJQobTiFcrAoSD52\" to=\"user@example.com\">\n" +
            "   <propose xmlns=\"urn:xmpp:jingle-message:0\" id=\"LE3clSJQobTiFcrAoNLR2A\">\n" +
            "      <description xmlns=\"urn:xmpp:jingle:apps:rtp:1\" media=\"audio\" />\n" +
            "      <description xmlns=\"urn:xmpp:jingle:apps:rtp:1\" media=\"video\" />\n" +
            "   </propose>\n" +
            "   <request xmlns=\"urn:xmpp:receipts\" />\n" +
            "   <store xmlns=\"urn:xmpp:hints\" />\n" +
            "</message>");

        // Execute system under test.
        final boolean result = CsiManager.canDelay(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tries to parse a stanza from an input text. This method throws an exception when the input cannot be parsed.
     *
     * @param input The text to be parsed
     * @return the stanza that resulted from parsing
     */
    private static Packet parse(final String input) throws DocumentException, XmlPullParserException, IOException
    {
        final XMPPPacketReader reader = new XMPPPacketReader();
        final Element element = reader.read(new StringReader(input)).getRootElement();
        switch (element.getName()) {
            case "presence":
                return new Presence(element, true);
            case "iq":
                return new IQ(element, true);
            case "message":
                return new Message(element, true);
            default:
                throw new IllegalStateException("Unexpected element name: " + element.asXML());
        }
    }
}
