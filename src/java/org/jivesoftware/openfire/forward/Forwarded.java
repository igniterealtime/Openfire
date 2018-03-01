package org.jivesoftware.openfire.forward;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketExtension;

import java.util.Date;

/**
 * @author Christian Schudt
 */
public class Forwarded extends PacketExtension {
    public Forwarded(Element copy, Date delay, JID delayFrom) {
        super("forwarded", "urn:xmpp:forward:0");
        populate(copy, delay, delayFrom);
    }
    public Forwarded(Message message, Date delay, JID delayFrom) {
        super("forwarded", "urn:xmpp:forward:0");

        Message copy = message.createCopy();
        populate(copy.getElement(), delay, delayFrom);
    }
    public Forwarded(Element copy) {
        super("forwarded", "urn:xmpp:forward:0");
        populate(copy, null, null);
    }
    public Forwarded(Message message) {
        super("forwarded", "urn:xmpp:forward:0");

        Message copy = message.createCopy();
        populate(copy.getElement(), null, null);
    }
    private void populate(Element copy, Date delay, JID delayFrom) {

        copy.setQName(QName.get("message", "jabber:client"));

        for (Object element : copy.elements()) {
            if (element instanceof Element) {
                Element el = (Element) element;
                // Only set the "jabber:client" namespace if the namespace is empty (otherwise the resulting xml would look like <body xmlns=""/>)
                if ("".equals(el.getNamespace().getStringValue())) {
                    el.setQName(QName.get(el.getName(), "jabber:client"));
                }
            }
        }
        if (delay != null) {
            Element delayInfo = element.addElement("delay", "urn:xmpp:delay");
            delayInfo.addAttribute("stamp", XMPPDateTimeFormat.format(delay));
            if (delayFrom != null) {
                // Set the Full JID as the "from" attribute
                delayInfo.addAttribute("from", delayFrom.toString());
            }
        }
        element.add(copy);
    }
}
