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

package org.jivesoftware.messenger.user;

import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.spi.IQRosterItemImpl;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

/**
 * A roster implemented against a JDBC database
 *
 * @author Iain Shigeoka
 */
public class IQRoster extends IQ implements Roster {

    private BasicIQRoster basicRoster = new BasicIQRoster();

    /**
     * <p>Create an empty iq roster packet.</p>
     */
    public IQRoster() {
    }

    public String getChildNamespace() {
        return "jabber:iq:roster";
    }

    public String getChildName() {
        return "query";
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        try {
            super.sendRoot(xmlSerializer, version, "iq", null);
            xmlSerializer.setPrefix("", "jabber:iq:roster");
            xmlSerializer.writeStartElement("query");
            xmlSerializer.writeDefaultNamespace("jabber:iq:roster");
            Iterator items = basicRoster.getRosterItems();
            while (items.hasNext()) {
                Object item = items.next();
                if (item instanceof IQRosterItem) {
                    ((IQRosterItem)item).send(xmlSerializer, version);
                }
                else {
                    new IQRosterItemImpl((RosterItem)item).send(xmlSerializer, version);
                }
            }
            xmlSerializer.writeEndElement();
            xmlSerializer.writeEndElement();
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public void parse(Document doc) {
        Element root = doc.getRootElement();
        setTo(new JID(root.attributeValue("to")));
        setType(Type.valueOf(root.attributeValue("type")));
        setID(root.attributeValue("id"));

        Iterator elements = root.elements().iterator();
        while (elements.hasNext()) {
            Element element = (Element)elements.next();
            if ("query".equals(element.getName())) {
                Iterator items = element.elementIterator("item");
                while (items.hasNext()) {
                    try {
                        Element item = (Element)items.next();
                        RosterItem rosterItem = basicRoster.createRosterItem(new JID(item.attributeValue("jid")),
                                item.attributeValue("name"),
                                null);
                        rosterItem.setSubStatus("remove".equals(item.attributeValue("subscription")) ?
                                RosterItem.SUB_REMOVE : RosterItem.SUB_NONE);
                        Iterator groupElements = item.elementIterator("group");
                        while (groupElements.hasNext()) {
                            rosterItem.getGroups().add(((Element)groupElements.next()).getTextTrim());
                        }
                    }
                    catch (UnauthorizedException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                    catch (UserAlreadyExistsException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
    }

    public void parse(XMLStreamReader xpp) throws XMLStreamException {
        // We're one past the root iq-element
        int event = xpp.getEventType();
        Document doc = null;
        // The one query element or the error element
        if (event == XMLStreamConstants.START_ELEMENT) {
            if ("query".equals(xpp.getLocalName())) {

            }
            else {
                // error, we'll punt and implement later
                throw new XMLStreamException("Error packets not supported yet");
            }
            try {
                doc = XPPReader.parseDocument(xpp);
                this.setChildElement(doc.getRootElement().getName(), doc.getRootElement().getNamespaceURI());
            }
            catch (DocumentException e) {
                throw new XMLStreamException();
            }
        }
        while (event != XMLStreamConstants.END_ELEMENT) {
            event = xpp.next();
        }
    }

    // ##################################################################################
    // Basic Roster usage - the downside of single inheritance
    // ##################################################################################

    private class BasicIQRoster extends BasicRoster {
        protected RosterItem provideRosterItem(RosterItem item) throws UserAlreadyExistsException, UnauthorizedException {
            return new IQRosterItemImpl(item);
        }

        protected RosterItem provideRosterItem(JID user, String nickname, List groups) throws UserAlreadyExistsException, UnauthorizedException {
            return new IQRosterItemImpl(user, nickname, groups);
        }
    }

    public boolean isRosterItem(JID user) {
        return basicRoster.isRosterItem(user);
    }

    public Iterator getRosterItems() throws UnauthorizedException {
        return basicRoster.getRosterItems();
    }

    public int getTotalRosterItemCount() throws UnauthorizedException {
        return basicRoster.getTotalRosterItemCount();
    }

    public RosterItem getRosterItem(JID user)
            throws UnauthorizedException, UserNotFoundException {
        return basicRoster.getRosterItem(user);
    }

    public RosterItem createRosterItem(JID user)
            throws UnauthorizedException, UserAlreadyExistsException {
        return basicRoster.createRosterItem(user);
    }

    public RosterItem createRosterItem(JID user, String nickname, List groups)
            throws UnauthorizedException, UserAlreadyExistsException {
        return basicRoster.createRosterItem(user, nickname, groups);
    }

    public RosterItem createRosterItem(RosterItem item) throws UnauthorizedException, UserAlreadyExistsException {
        return basicRoster.createRosterItem(item);
    }

    public void updateRosterItem(RosterItem item)
            throws UnauthorizedException, UserNotFoundException {
        basicRoster.updateRosterItem(item);
    }

    public RosterItem deleteRosterItem(JID user) throws UnauthorizedException {
        return basicRoster.deleteRosterItem(user);
    }
}
