package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.junit.Test;
import org.xmpp.packet.Message;

import static org.junit.Assert.assertEquals;

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
