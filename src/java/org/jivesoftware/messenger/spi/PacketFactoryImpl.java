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

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.spi.IQRosterImpl;
import java.io.StringReader;
//import java.util.HashMap;
//import java.util.Iterator;
import java.util.LinkedList;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

public class PacketFactoryImpl extends BasicModule implements PacketFactory {

    public LinkedList iqHandlers = new LinkedList();
    public UserManager userManager;
    private XMLInputFactory xppFactory;

    public PacketFactoryImpl() {
        super("Packet factory");
        xppFactory = XMLInputFactory.newInstance();
        xppFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
    }

    public Message getMessage() {
        return new MessageImpl();
    }

    public Message getMessage(XMLStreamReader xpp) throws XMLStreamException {
        Message msg = new MessageImpl();
        parse(msg, xpp);
        return msg;
    }

    public Message getMessage(String msgText) throws XMLStreamException {
        Message msg = null;
        XMLStreamReader xpp = null;
        xpp = xppFactory.createXMLStreamReader(new StringReader(msgText));
        xpp.next(); // move parser to start tag
        msg = new MessageImpl();
        parse(msg, xpp);
        return msg;
    }

    public IQ getIQ() {
        return new IQImpl();
    }

    public IQ getIQ(XMLStreamReader xpp) throws XMLStreamException {

        IQ iq = null;

        try {
            Document doc = XPPReader.parseDocument(xpp);
            Element query = doc.getRootElement().element("query");
            if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
                iq = new IQRosterImpl();
                ((IQImpl)iq).parse(doc);
            }
            else {
                iq = new IQImpl();
                ((IQImpl)iq).parse(doc);
            }
        }
        catch (DocumentException e) {
            throw new XMLStreamException(e.getMessage());
        }
        return iq;
    }

    public Presence getPresence() {
        return new PresenceImpl();
    }

    public Presence getPresence(XMLStreamReader xpp) throws XMLStreamException {
        Presence presence = new PresenceImpl();
        parse(presence, xpp);
        return presence;
    }

    private void parse(XMPPPacket packet, XMLStreamReader xpp) throws
            XMLStreamException {
        packet.parse(xpp);
    }

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        return trackInfo;
    }
}