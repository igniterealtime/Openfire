package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

/**
 * @author Christian Schudt
 */
public class Received extends PacketExtension {
    public Received(Forwarded forwarded) {
        super("received", "urn:xmpp:carbons:2");
        element.add(forwarded.getElement());
    }
}
