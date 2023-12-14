/*
 * Copyright (C) 2018-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.Message;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christian Schudt
 */
public class MessageCarbonsTest {

    @Test
    public void testSent() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setBody("Tests");

        Forwarded forwarded = new Forwarded(message);

        Sent sent = new Sent(forwarded);
        String xml = sent.getElement().asXML();
        assertEquals("<sent xmlns=\"urn:xmpp:carbons:2\"><forwarded xmlns=\"urn:xmpp:forward:0\"><message xmlns=\"jabber:client\" type=\"chat\"><body>Tests</body></message></forwarded></sent>", xml);
    }

    @Test
    public void testReceived() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setBody("Tests");

        Forwarded forwarded = new Forwarded(message);

        Received received = new Received(forwarded);
        String xml = received.getElement().asXML();
        assertEquals("<received xmlns=\"urn:xmpp:carbons:2\"><forwarded xmlns=\"urn:xmpp:forward:0\"><message xmlns=\"jabber:client\" type=\"chat\"><body>Tests</body></message></forwarded></received>", xml);
    }
}
