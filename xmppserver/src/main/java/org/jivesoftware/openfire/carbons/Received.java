package org.jivesoftware.openfire.carbons;

import org.dom4j.Element;
import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;

/**
 * The implementation of the {@code <received xmlns="urn:xmpp:carbons:2"/>} extension.
 * It indicates, that a message has been received by another resource.
 *
 * @author Christian Schudt
 */
public final class Received extends PacketExtension {

    public static final String NAME = "received";
    public static final String NAMESPACE = "urn:xmpp:carbons:2";

    public Received(@Nonnull final Forwarded forwarded) {
        super(NAME, NAMESPACE);
        element.add(forwarded.getElement());
    }

    public Packet getForwardedStanza() {
        if (element.element("forwarded") == null) {
            return null;
        }
        if (element.element("forwarded").elements() == null) {
            return null;
        }
        final Element originalStanza = element.element("forwarded").elements().get(0);
        switch (originalStanza.getName()) {
            case "message":
                return new Message(originalStanza, true);
            case "iq":
                return new IQ(originalStanza, true);
            case "presence":
                return new Presence(originalStanza, true);
            default:
                throw new IllegalArgumentException("A 'forwarded' stanza must by of type 'message', 'iq' or 'presence', not: " + originalStanza.getName());
        }
    }
}
