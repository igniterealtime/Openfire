/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.XMPPDOMFragment;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.IQImpl;
import org.jivesoftware.messenger.user.*;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

/**
 * A roster implemented against a JDBC database
 *
 * @author Iain Shigeoka
 */
public class IQRosterImpl extends IQImpl implements IQRoster {

    private BasicIQRoster basicRoster = new BasicIQRoster();

    /**
     * <p>Create an empty iq roster packet.</p>
     */
    public IQRosterImpl() {
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
        setRecipient(XMPPAddress.parseJID(root.attributeValue("to")));
        setType(typeFromString(root.attributeValue("type")));
        setID(root.attributeValue("id"));

        Iterator elements = root.elements().iterator();
        while (elements.hasNext()) {
            Element element = (Element)elements.next();
            if ("query".equals(element.getName())) {
                Iterator items = element.elementIterator("item");
                while (items.hasNext()) {
                    try {
                        Element item = (Element)items.next();
                        RosterItem rosterItem = basicRoster.createRosterItem(XMPPAddress.parseJID(item.attributeValue("jid")),
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
                setChildNamespace(doc.getRootElement().getNamespaceURI());
                setChildName(doc.getRootElement().getName());
                XMPPFragment fragment = new XMPPDOMFragment(doc.getRootElement());
                addFragment(fragment);
                setChildFragment(fragment);
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

        protected RosterItem provideRosterItem(XMPPAddress user, String nickname, List groups) throws UserAlreadyExistsException, UnauthorizedException {
            return new IQRosterItemImpl(user, nickname, groups);
        }
    }

    public boolean isRosterItem(XMPPAddress user) {
        return basicRoster.isRosterItem(user);
    }

    public Iterator getRosterItems() throws UnauthorizedException {
        return basicRoster.getRosterItems();
    }

    public int getTotalRosterItemCount() throws UnauthorizedException {
        return basicRoster.getTotalRosterItemCount();
    }

    public RosterItem getRosterItem(XMPPAddress user)
            throws UnauthorizedException, UserNotFoundException {
        return basicRoster.getRosterItem(user);
    }

    public RosterItem createRosterItem(XMPPAddress user)
            throws UnauthorizedException, UserAlreadyExistsException {
        return basicRoster.createRosterItem(user);
    }

    public RosterItem createRosterItem(XMPPAddress user, String nickname, List groups)
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

    public RosterItem deleteRosterItem(XMPPAddress user) throws UnauthorizedException {
        return basicRoster.deleteRosterItem(user);
    }
}
