package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

/**
 * The implementation of the {@code <sent xmlns="urn:xmpp:carbons:2"/>} extension.
 * It indicates, that a message has been sent by the same user from another resource.
 *
 * @author Christian Schudt
 */
public final class Sent extends PacketExtension {
    public Sent(Forwarded forwarded) {
        super("sent", "urn:xmpp:carbons:2");
        element.add(forwarded.getElement());
    }
}
