package org.jivesoftware.openfire.forward;

import junit.framework.Assert;
import org.junit.Test;
import org.xmpp.packet.Message;

/**
 * @author Christian Schudt
 */
public class ForwardTest {

    @Test
    public void testForwarded() {
        Message message = new Message();
        message.setType(Message.Type.chat);
        message.setBody("Tests");

        Forwarded forwarded = new Forwarded(message);
        String xml = forwarded.getElement().asXML();
        Assert.assertEquals("<forwarded xmlns=\"urn:xmpp:forward:0\"><message xmlns=\"jabber:client\" type=\"chat\"><body>Tests</body></message></forwarded>", xml);
    }
}
