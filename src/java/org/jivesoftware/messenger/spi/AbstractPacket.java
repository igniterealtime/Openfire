/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.*;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

abstract public class AbstractPacket extends AbstractFragment implements XMPPPacket {

    XMPPPacket.RoutePriority routePriority = XMPPPacket.RoutePriority.normal;
    XMPPError error;
    String id;
    Session session;
    XMPPAddress sender;
    XMPPAddress recipient;
    XMPPPacket.Type type;
    protected boolean sending;

    public boolean isSending() {
        return sending;
    }

    public void setSending(boolean isSending) {
        this.sending = isSending;
    }

    public XMPPPacket.RoutePriority getRoutePriority() {
        return routePriority;
    }

    public void setRoutePriority(XMPPPacket.RoutePriority priority) {
        routePriority = priority;
    }

    public void setError(XMPPError.Code errorCode) {
        this.error = new XMPPError(errorCode);
        type = ERROR;
    }

    public XMPPError getError() {
        return error;
    }

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public void setOriginatingSession(Session session) {
        this.session = session;
    }

    public Session getOriginatingSession() {
        return session;
    }

    public void setSender(XMPPAddress sender) {
        this.sender = sender;
    }

    public XMPPAddress getSender() {
        return sender;
    }

    public void setRecipient(XMPPAddress recipient) {
        this.recipient = recipient;
    }

    public XMPPAddress getRecipient() {
        return recipient;
    }

    public XMPPPacket.Type typeFromString(String type) {
        if (ERROR.toString().equals(type)) {
            return ERROR;
        }
        return null;
    }

    public void setType(XMPPPacket.Type type) {
        this.type = type;
    }

    public XMPPPacket.Type getType() {
        return type;
    }

    protected void copyAttributes(AbstractPacket packet) {
        packet.routePriority = routePriority;
        packet.error = error;
        packet.id = id;
        packet.session = session;
        packet.sender = sender;
        packet.recipient = recipient;
        packet.type = type;
    }

    protected void deepCopy(AbstractPacket packet) {
        copyAttributes(packet);
        Iterator frags = getFragments();
        while (frags.hasNext()) {
            packet.addFragment(((XMPPFragment)frags.next()).createDeepCopy());
        }
    }

    /**
     * <p>Sends the opening tag and the error sub-packet (if applicable).</p>
     * <p>The serializer is left ready to send the next subpacket.</p>
     *
     * @param xmlSerializer the serializer to use.
     * @param version the XMPP version to follow.
     * @param elementName the element name of the packet (iq,message,presence).
     * @param ignoreType the type (if any) to ignore (Message.NORMAL,Presence.AVAILABLE) or
     *          null to ignore
     */
    public void sendRoot(XMLStreamWriter xmlSerializer, int version, String elementName,
            XMPPPacket.Type ignoreType) throws XMLStreamException
    {
        xmlSerializer.writeStartElement("jabber:client", elementName);
        if (sender != null && sender.getHost() != null) {
            xmlSerializer.writeAttribute("from", sender.toString());
        }
        else {
            if (session != null
                    && session.getAddress() != null
                    && session.getAddress().getHost() != null) {
                xmlSerializer.writeAttribute("from", session.getAddress().toString());
            }
        }
        if (recipient != null && recipient.getHost() != null) {
            xmlSerializer.writeAttribute("to", recipient.toString());
        }
        if (id != null && !"".equals(id)) {
            xmlSerializer.writeAttribute("id", id);
        }
        if (type != null && type != ignoreType) {
            xmlSerializer.writeAttribute("type", type.toString());
            if (type.equals(ERROR) && error != null) {
                error.send(xmlSerializer, version);
            }
        }
    }

    public int getSize() {
        // No sense even trying to calculate it, just provide a rough average packet size
        return 50;
    }

    /**
     * <p>Parses the standard root element attributes without moving
     * the xpp position and sets the current packet up for the given
     * packet type.</p>
     *
     * @param xpp The XML pull parser to obtain the root attributes
     */
    protected void parseRootAttributes(XMLStreamReader xpp) {
        setSender(XMPPAddress.parseJID(xpp.getAttributeValue("", "from")));
        setRecipient(XMPPAddress.parseJID(xpp.getAttributeValue("", "to")));
        setType(typeFromString(xpp.getAttributeValue("", "type")));
        setID(xpp.getAttributeValue("", "id"));
    }
}