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
    //private HashMap namespace2Handlers = new HashMap();
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
        /*
        IQ iq = null;
        XMPPAddress recipient = XMPPAddress.parseJID(xpp.getAttributeValue("","to"));
        String type = xpp.getAttributeValue("","type");
        String id = xpp.getAttributeValue("","id");

        try {
            if (xpp.next() == XmlPullParser.END_TAG){
                // empty iq
                iq = new IQImpl();
            } else {
                IQHandler handler = getHandler(xpp.getNamespace());
                iq = handler.getInfo().parse(xpp);
            }
            iq.setRecipient(recipient);
            iq.setType(iq.typeFromString(type));
            iq.setID(id);
        } catch (XmlPullParserException e) {
            throw new IOException(e.getMessage());
        }
        return iq;
        */
    }

    /*private IQHandler getHandler(String namespace) {
        IQHandler handler = null;

        handler = (IQHandler)namespace2Handlers.get(namespace);
        if (handler == null) {
            Iterator handlerIter = iqHandlers.iterator();
            while (handlerIter.hasNext() && handler == null) {
                IQHandler handlerCandidate = (IQHandler)handlerIter.next();
                IQHandlerInfo handlerInfo = handlerCandidate.getInfo();
                if (handlerInfo != null && namespace.equalsIgnoreCase(handlerInfo.getNamespace())) {
                    handler = handlerCandidate;
                }
            }
            if (handler != null) {
                namespace2Handlers.put(namespace, handler);
            }
        }
        return handler;
    }*/

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
        //trackInfo.getTrackerClasses().put(IQHandler.class, "iqHandlers");
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        return trackInfo;
    }
}