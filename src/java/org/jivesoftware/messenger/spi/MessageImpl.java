/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.spi;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.XMPPDOMFragment;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.XMPPPacket;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.Document;
import org.dom4j.Element;

public class MessageImpl extends AbstractPacket implements Message {

    private String body;
    private String subject;
    private String thread;

    public MessageImpl() {
        type = Message.NORMAL;
    }

    public XMPPPacket.Type typeFromString(String type) {
        XMPPPacket.Type typeObject = super.typeFromString(type);
        if (typeObject == null) {
            if (type == null || type.length() == 0) {
                typeObject = Message.NORMAL;
            }
            else if (Message.GROUP_CHAT.toString().equals(type)) {
                typeObject = Message.GROUP_CHAT;
            }
            else if (Message.CHAT.toString().equals(type)) {
                typeObject = Message.CHAT;
            }
            else if (Message.HEADLINE.toString().equals(type)) {
                typeObject = Message.HEADLINE;
            }
            else {
                typeObject = Message.NORMAL;
            }
        }
        return typeObject;
    }

    public int getSize() {
        int size = super.getSize();
        if (subject != null) {
            size += subject.length();
        }
        if (body != null) {
            size += body.length();
        }
        if (thread != null) {
            size += thread.length();
        }
        return size;
    }

    public String toString() {
        return "M " + hashCode() + " " + type + " S " + subject + " B " + body;
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        super.sendRoot(xmlSerializer, version, "message", Message.NORMAL);

        if (subject != null && subject.length() > 0) {
            xmlSerializer.writeStartElement("jabber:client", "subject");
            xmlSerializer.writeCharacters(subject);
            xmlSerializer.writeEndElement();
        }
        if (thread != null && thread.length() > 0) {
            xmlSerializer.writeStartElement("jabber:client", "thread");
            xmlSerializer.writeCharacters(thread);
            xmlSerializer.writeEndElement();
        }
        if (body != null && body.length() > 0) {
            xmlSerializer.writeStartElement("jabber:client", "body");
            xmlSerializer.writeCharacters(body);
            xmlSerializer.writeEndElement();
        }

        Iterator frags = getFragments();
        while (frags.hasNext()) {
            ((XMPPFragment)frags.next()).send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {
        MessageImpl msg = new MessageImpl();
        deepCopy(msg);
        msg.body = body;
        msg.subject = subject;
        msg.thread = thread;
        return msg;
    }

    public void parse(XMLStreamReader xpp) throws XMLStreamException {
        // Super class AbstractPacket will not move the parser from it's start position.
        parseRootAttributes(xpp);
        Document doc = null;
        try {
            doc = XPPReader.parseDocument(xpp);
            Element root = doc.getRootElement();

            Iterator frags = root.elementIterator();
            while (frags.hasNext()) {
                Element element = (Element)frags.next();
                String name = element.getName();
                if ("body".equals(name)) {
                    body = element.getText();
                }
                else if ("thread".equals(name)) {
                    thread = element.getTextTrim();
                }
                else if ("subject".equals(name)) {
                    subject = element.getTextTrim();
                }
                else if ("error".equals(name)) {
                    setType(ERROR);
                }
                else {
                    addFragment(new XMPPDOMFragment(element));
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }
}
