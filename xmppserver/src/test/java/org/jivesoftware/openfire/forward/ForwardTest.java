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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christian Schudt
 */
public class ForwardTest {

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
}
