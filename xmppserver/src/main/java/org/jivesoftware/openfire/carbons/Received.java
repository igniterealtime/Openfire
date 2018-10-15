package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

/**
 * The implementation of the {@code <received xmlns="urn:xmpp:carbons:2"/>} extension.
 * It indicates, that a message has been received by another resource.
 *
 * @author Christian Schudt
 */
public final class Received extends PacketExtension {
    public Received(Forwarded forwarded) {
        super("received", "urn:xmpp:carbons:2");
        element.add(forwarded.getElement());
    }
}
