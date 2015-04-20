package org.jivesoftware.openfire.forward;

import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketExtension;

/**
 * @author Christian Schudt
 */
public class Forwarded extends PacketExtension {
    public Forwarded(Message message) {
        super("forwarded", "urn:xmpp:forward:0");

        Message copy = message.createCopy();

        copy.getElement().setQName(QName.get("message", "jabber:client"));

        for (Object element : copy.getElement().elements()) {
            if (element instanceof Element) {
                Element el = (Element) element;
                // Only set the "jabber:client" namespace if the namespace is empty (otherwise the resulting xml would look like <body xmlns=""/>)
                if ("".equals(el.getNamespace().getStringValue())) {
                    el.setQName(QName.get(el.getName(), "jabber:client"));
                }
            }
        }
        element.add(copy.getElement());
    }
}
