/*
 * Copyright (C) 2023-2024 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.forward;

import org.dom4j.DocumentHelper;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link Forwarded}
 *
 * @author Christian Schudt
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class ForwardedTest
{
    @BeforeEach
    public void setUp() throws Exception {
        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(xmppServer);
    }

    @Test
    public void testForwarded() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setBody("Tests");
        message.addExtension(new DataForm(DataForm.Type.submit));

        Forwarded forwarded = new Forwarded(message);
        Forwarded forwarded2 = new Forwarded(message);
        String xml1 = forwarded.getElement().asXML();
        String xml2 = forwarded2.getElement().asXML();
        assertEquals("<forwarded xmlns=\"urn:xmpp:forward:0\"><message xmlns=\"jabber:client\" type=\"chat\"><body>Tests</body><x xmlns=\"jabber:x:data\" type=\"submit\"/></message></forwarded>", xml1);
        assertEquals("<forwarded xmlns=\"urn:xmpp:forward:0\"><message xmlns=\"jabber:client\" type=\"chat\"><body>Tests</body><x xmlns=\"jabber:x:data\" type=\"submit\"/></message></forwarded>", xml2);
    }

    /**
     * Asserts that a message with a 'private' extension is not eligible for Carbons delivery.
     */
    @Test
    public void testPrivate() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.getElement().addElement("private", "urn:xmpp:carbons:2");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that a message of type 'chat' is eligible for Carbons delivery.
     */
    @Test
    public void testChat() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.chat);

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that a message of type 'chat' but with a 'private' extension is not eligible for Carbons delivery.
     */
    @Test
    public void testChatPrivate() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.chat);
        input.getElement().addElement("private", "urn:xmpp:carbons:2");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that a message of type 'normal' with a body is eligible for Carbons delivery.
     */
    @Test
    public void testNormalWithBody() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.normal);
        input.setBody("This message is part of unit test " + getClass());

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Asserts that a message of type 'normal' with a body but with a 'private' extension is not eligible for Carbons delivery.
     */
    @Test
    public void testNormalWithBodyPrivate() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.normal);
        input.setBody("This message is part of unit test " + getClass());
        input.getElement().addElement("private", "urn:xmpp:carbons:2");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that a message of type 'groupchat' is not eligible for Carbons delivery.
     */
    @Test
    public void testMucGroupChat() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.groupchat);

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Asserts that a message of type 'groupchat' is not eligible for Carbons delivery.
     *
     * The stanza that's the input of the text is taken from a real-world stanza. This intends to make test coverage
     * more conform real-world scenario's than the basic test in {@link #testMucGroupChat()}
     */
    @Test
    public void testMucGroupChatRawData() throws Exception
    {
        // Setup test fixture.
        final String raw = "<message xmlns=\"jabber:client\" to=\"john@example.org/barfoo\" type=\"groupchat\" id=\"7cb29947-fda2-4a44-b349-ec83fbbf062f\" from=\"room1@muc.example.org/Johnny\">\n" +
            "        <active xmlns=\"http://jabber.org/protocol/chatstates\" />\n" +
            "        <markable xmlns=\"urn:xmpp:chat-markers:0\" />\n" +
            "        <origin-id xmlns=\"urn:xmpp:sid:0\" id=\"7cb29947-fda2-4a44-b349-ec83fbbf062f\" />\n" +
            "        <encrypted xmlns=\"eu.siacs.conversations.axolotl\">\n" +
            "          <header sid=\"12121212\">\n" +
            "            <key rid=\"2334343434\">MOCK-TESTDATA</key>\n" +
            "            <iv>TESTTEST</iv>\n" +
            "</header>\n" +
            "          <payload>TEST</payload>\n" +
            "</encrypted>\n" +
            "        <encryption xmlns=\"urn:xmpp:eme:0\" name=\"OMEMO\" namespace=\"eu.siacs.conversations.axolotl\" />\n" +
            "        <body>You received a message encrypted with OMEMO but your client doesn't support OMEMO.</body>\n" +
            "        <store xmlns=\"urn:xmpp:hints\" />\n" +
            "        <stanza-id xmlns=\"urn:xmpp:sid:0\" id=\"d9c123d0-8738-40be-a1a3-497435e0761d\" by=\"room1@muc.example.org\" />\n" +
            "        <addresses xmlns=\"http://jabber.org/protocol/address\">\n" +
            "          <address type=\"ofrom\" jid=\"john@example.org\" />\n" +
            "</addresses>\n" +
            "</message>\n";
        final Message input = new Message(DocumentHelper.parseText(raw).getRootElement());

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }

    /**
     * A private <message/> from a local user to a MUC participant (sent to a full JID) SHOULD be carbon-copied.
     */
    @Test
    public void testMucPrivateMessageSent() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setTo("room@domain/nick");
        input.setFrom(new JID("user", Fixtures.XMPP_DOMAIN, "resource"));
        input.setType(Message.Type.chat);
        input.setBody("test");
        input.getElement().addElement("x", "http://jabber.org/protocol/muc#user");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertTrue(result);
    }

    /**
     * A private <message/> from a MUC participant (received from a full JID) to a local user SHOULD NOT be
     * carbon-copied (these messages are already replicated by the MUC service to all joined client instances).
     */
    @Test
    public void testMucPrivateMessageReceived() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setTo(new JID("user", Fixtures.XMPP_DOMAIN, "resource"));
        input.setFrom("room@domain/nick");
        input.setType(Message.Type.chat);
        input.setBody("test");
        input.getElement().addElement("x", "http://jabber.org/protocol/muc#user");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }
}
