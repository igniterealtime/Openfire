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

package org.jivesoftware.openfire.forward;

import org.junit.jupiter.api.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.Message;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the implementation of {@link Forwarded}
 *
 * @author Christian Schudt
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class ForwardedTest
{
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
     * Asserts that a message exchanged with a MUC of type 'chat' is not eligible for Carbons delivery.
     */
    @Test
    public void testMucGroupChat() throws Exception
    {
        // Setup test fixture.
        final Message input = new Message();
        input.setType(Message.Type.groupchat);
        input.getElement().addElement("x", "http://jabber.org/protocol/muc#user");

        // Execute system under test.
        final boolean result = Forwarded.isEligibleForCarbonsDelivery(input);

        // Verify results.
        assertFalse(result);
    }
}
