package org.jivesoftware.openfire.labelling;

import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.PacketExtension;

/**
 * XEP-0258 label.
 * Note that this does not (intentionally) know anything about the label elements
 * themselves.
 */
public class SecurityLabel extends PacketExtension {
    public SecurityLabel(Element el) {
        super(el);
    }

    public SecurityLabel(final String displayMarking, final String fgcolour, final String bgcolour, Element label) {
        super("securitylabel", "urn:xmpp:sec-label:0");
        assert label != null : "Security Label element must not be null";
        if (fgcolour != null || bgcolour != null) {
            assert displayMarking != null : "Colours set without a marking";
        }
        populate(displayMarking, fgcolour, bgcolour, label);
    }

    public void populate(final String displayMarking, final String fgcolour, final String bgcolour, Element labelelement) {
        if (displayMarking != null) {
            Element dm = this.element.addElement("displaymarking");
            dm.setText(displayMarking);
            if (fgcolour != null) {
                dm.addAttribute("fgcolor", fgcolour);
            }
            if (bgcolour != null) {
                dm.addAttribute("bgcolor", bgcolour);
            }
        }
        Element label = this.element.addElement("label");
        label.add(labelelement);
    }

    public Element getLabel() {
        return (Element)this.element.element("label").elements().get(0);
    }

    static {
        registeredExtensions.put(QName.get("securitylabel", "urn:xmpp:sec-label:0"), SecurityLabel.class);
    }

}
