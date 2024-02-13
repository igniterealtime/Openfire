/*
 * Copyright (C) 2016-2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.forward;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketExtension;

import java.util.Date;
import java.util.Set;

/**
 * @author Christian Schudt
 * @author Guus der Kinderen, guus@goodbytes.nl
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

    /**
     * Checks if a message stanza is eligible for carbons delivery, based on the recommended rules defined in section
     * 6.1 "Recommended Rules" of XEP-0280 "Message Carbons"
     *
     * @param stanza The message stanza to check
     * @return true if the stanza is eligible for Carbons delivery.
     * @see <a href="https://xmpp.org/extensions/xep-0280.html#recommended-rules">XEP-0280 "Message Carbons" Section 6.1 "Recommended Rules"</a>
     */
    public static boolean isEligibleForCarbonsDelivery(final Message stanza)
    {
        // To properly handle messages exchanged with a MUC (or similar service), the server must be able to identify MUC-related messages.
        // This can be accomplished by tracking the clients' presence in MUCs, or by checking for the <x xmlns="http://jabber.org/protocol/muc#user">
        // element in messages. The following rules apply to MUC-related messages:
        if (stanza.getChildElement("x", "http://jabber.org/protocol/muc#user") != null)
        {
            // A <message/> containing a Direct MUC Invitations (XEP-0249) SHOULD be carbon-copied.
            if (containsChildElement(stanza, Set.of("x"), "jabber:x:conference")) {
                return true;
            }

            // A <message/> containing a Mediated Invitation SHOULD be carbon-copied.
            if (stanza.getChildElement("x", "http://jabber.org/protocol/muc#user") != null
                && stanza.getChildElement("x", "http://jabber.org/protocol/muc#user").element("invite") != null) {
                return true;
            }

            // A private <message/> from a local user to a MUC participant (sent to a full JID) SHOULD be carbon-copied
            // The server SHOULD limit carbon-copying to the clients sharing a Multi-Session Nick in that MUC, and MAY
            // inject the <x/> element into such carbon copies. Clients can not respond to carbon-copies of MUC-PMs
            // related to a MUC they are not joined to. Therefore, they SHOULD either ignore such carbon copies, or
            // provide a way for the user to join the MUC before answering.
            if (stanza.getTo() != null && stanza.getTo().getResource() != null
                && stanza.getFrom() != null && stanza.getFrom().getNode() != null && XMPPServer.getInstance().isLocal(stanza.getFrom()))
            {
                return true;
                // TODO The server SHOULD limit carbon-copying to the clients sharing a Multi-Session Nick in that MUC (OF-2780).
            }

            // A private <message/> from a MUC participant (received from a full JID) to a local user SHOULD NOT be
            // carbon-copied (these messages are already replicated by the MUC service to all joined client instances).
            if (stanza.getFrom() != null && stanza.getFrom().getResource() != null
                && stanza.getTo() != null && stanza.getTo().getNode() != null && XMPPServer.getInstance().isLocal(stanza.getTo()))
            {
                return false;
            }
        }

        // A <message/> of type "groupchat" SHOULD NOT be carbon-copied.
        if (stanza.getType() == Message.Type.groupchat) {
            return false;
        }

        // A <message/> is eligible for carbons delivery if it does not contain a <private/> child element...
        if (containsChildElement(stanza, Set.of("private", "received"), "urn:xmpp:carbons"))
        {
            return false;
        }

        // and if at least one of the following is true:

        // ... it is of type "chat".
        if (stanza.getType() == Message.Type.chat) {
            return true;
        }

        // ... it is of type "normal" and contains a <body> element.
        if ((stanza.getType() == null || stanza.getType() == Message.Type.normal) && stanza.getBody() != null) {
            return true;
        }

        // ... it contains payload elements typically used in IM
        if (containsChildElement(stanza, Set.of("request", "received"), "urn:xmpp:receipts") // Message Delivery Receipts (XEP-0184)
         || containsChildElement(stanza, Set.of("active", "inactive", "gone", "composing", "paused"), "http://jabber.org/protocol/chatstates") // Chat State Notifications (XEP-0085)
         || (containsChildElement(stanza, Set.of("markable", "received", "displayed", "acknowledged"), "urn:xmpp:chat-markers"))  // Chat Markers (XEP-0333)).
        ) {
            return true;
        }

        // ... it is of type "error" and it was sent in response to a <message/> that was eligible for carbons delivery.
        // TODO implement me (OF-2779)

        return false;
    }

    /**
     * Checks if the provided stanza has a direct child element that matches any of the provided element names, escaped
     * by a namespace that <em>starts with</em> the provided prefix.
     *
     * This method is intended to identify stanzas that have extensions for any version of a particular XEP.
     *
     * @param stanza The stanza to check
     * @param elementNames The element names for which to search
     * @param namespaceUriPrefix Part of the namespace that identifies the element name.
     * @return true if matching child element is found, otherwise false.
     */
    static boolean containsChildElement(final Packet stanza, final Set<String> elementNames, final String namespaceUriPrefix) {
        for (final String elementName : elementNames) {
            if (stanza.getElement().element(elementName) != null
             && stanza.getElement().element(elementName).getNamespaceURI() != null
             && stanza.getElement().element(elementName).getNamespaceURI().startsWith(namespaceUriPrefix)) {
                return true;
            }
        }
        return false;
    }
}
