package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

/**
 * @author Christian Schudt
 */
public class Sent extends PacketExtension {
    public Sent(Forwarded forwarded) {
        super("sent", "urn:xmpp:carbons:2");
        element.add(forwarded.getElement());
    }
}
