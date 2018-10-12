package org.jivesoftware.openfire.forward;

import org.junit.Test;
import org.xmpp.forms.DataForm;
import org.xmpp.packet.Message;

import static org.junit.Assert.assertEquals;

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
