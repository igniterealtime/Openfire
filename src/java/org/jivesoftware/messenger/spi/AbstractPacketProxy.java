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

import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.XMPPError;
import org.jivesoftware.messenger.XMPPPacket;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

abstract public class AbstractPacketProxy extends FragmentProxy implements XMPPPacket {

    protected XMPPPacket packet;

    public AbstractPacketProxy(XMPPPacket packet, AuthToken token, Permissions permissions) {
        super(packet, token, permissions);
        this.packet = packet;
    }

    public boolean isSending() {
        return packet.isSending();
    }

    public void setSending(boolean isSending) {
        packet.setSending(isSending);
    }

    public XMPPPacket.RoutePriority getRoutePriority() {
        return packet.getRoutePriority();
    }

    public void setRoutePriority(XMPPPacket.RoutePriority priority) {
        packet.setRoutePriority(priority);
    }

    public void setError(XMPPError.Code errorCode) {
        packet.setError(errorCode);
    }

    public XMPPError getError() {
        return packet.getError();
    }

    public String getID() {
        return packet.getID();
    }

    public void setID(String id) {
        packet.setID(id);
    }

    public void setOriginatingSession(Session session) {
        packet.setOriginatingSession(session);
    }

    public XMPPAddress getRecipient() {
        return packet.getRecipient();
    }

    public void setRecipient(XMPPAddress recipient) {
        packet.setRecipient(recipient);
    }

    public void setSender(XMPPAddress sender) {
        packet.setSender(sender);
    }

    public XMPPAddress getSender() {
        return packet.getSender();
    }

    public Session getOriginatingSession() {
        return packet.getOriginatingSession();
    }

    public void parse(XMLStreamReader xpp) throws XMLStreamException {
        packet.parse(xpp);
    }

    public XMPPPacket.Type typeFromString(String type) {
        return packet.typeFromString(type);
    }

    public void setType(XMPPPacket.Type type) {
        packet.setType(type);
    }

    public XMPPPacket.Type getType() {
        return packet.getType();
    }
}
