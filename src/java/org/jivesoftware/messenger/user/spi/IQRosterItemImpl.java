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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.ConcurrentHashSet;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.user.BasicRosterItem;
import org.jivesoftware.messenger.user.IQRosterItem;
import org.jivesoftware.messenger.user.RosterItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

public class IQRosterItemImpl extends BasicRosterItem implements IQRosterItem {

    public IQRosterItemImpl(XMPPAddress jid) {
        super(jid);
    }

    public IQRosterItemImpl(XMPPAddress jid, String nickname, List groups) {
        super(jid, nickname, groups);
    }

    /**
     * <p>Create a copy of the given roster item.</p>
     *
     * @param item
     */
    public IQRosterItemImpl(RosterItem item) {
        super(item);
        if (item instanceof IQRosterItemImpl) {
            fragments = (ConcurrentHashSet)((IQRosterItemImpl)item).fragments.clone();
        }
    }

    public Element asXMLElement() {
        Element item = DocumentHelper.createElement("item");
        item.addAttribute("jid", jid.toBareString());
        item.addAttribute("subscription", subStatus.getName());
        if (askStatus != ASK_NONE) {
            item.addAttribute("ask", askStatus.getName());
        }
        if (nickname != null) {
            if (nickname.trim().length() > 0) {
                item.addAttribute("name", nickname.trim());
            }
        }
        if (groups != null) {
            Iterator groupsItr = groups.iterator();
            while (groupsItr.hasNext()) {
                item.addElement("group").addText((String)groupsItr.next());
            }
        }
        return item;
    }

    public String getNamespace() {
        return "jabber:iq:roster";
    }

    public void setNamespace(String namespace) {
        // do nothing
    }

    public String getName() {
        return "item";
    }

    public void setName(String name) {
        // do nothing
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
        xmlSerializer.writeStartElement("jabber:iq:roster", "item");
        xmlSerializer.writeAttribute("jid", jid.toBareString());
        xmlSerializer.writeAttribute("subscription", subStatus.getName());
        if (askStatus != ASK_NONE) {
            xmlSerializer.writeAttribute("ask", askStatus.getName());
        }
        if (nickname != null) {
            if (nickname.trim().length() > 0) {
                xmlSerializer.writeAttribute("name", nickname.trim());
            }
        }
        if (groups != null) {
            Iterator groupsItr = groups.iterator();
            while (groupsItr.hasNext()) {
                xmlSerializer.writeStartElement("jabber:iq:roster", "group");
                xmlSerializer.writeCharacters((String)groupsItr.next());
                xmlSerializer.writeEndElement();
            }
        }
        Iterator frags = fragments.iterator();
        while (frags.hasNext()) {
            XMPPFragment frag = (XMPPFragment)frags.next();
            frag.send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {
        IQRosterItemImpl item = new IQRosterItemImpl(new XMPPAddress(jid.getName(), jid.getHost(),
                jid.getResource()));
        item.subStatus = subStatus;
        item.askStatus = askStatus;
        item.recvStatus = recvStatus;
        item.nickname = nickname;
        if (groups != null) {
            item.groups = new ArrayList(groups.size());
            Collections.copy(item.groups, groups);
        }
        item.fragments = (ConcurrentHashSet)fragments.clone();
        return item;
    }

    private ConcurrentHashSet fragments = new ConcurrentHashSet();

    public void addFragment(XMPPFragment fragment) {
        fragments.add(fragment);
    }

    public Iterator getFragments() {
        return fragments.iterator();
    }

    public XMPPFragment getFragment(String name, String namespace) {
        if (fragments == null) {
            return null;
        }
        XMPPFragment frag;
        for (Iterator frags = fragments.iterator(); frags.hasNext();) {
            frag = (XMPPFragment)frags.next();
            if (name.equals(frag.getName()) && namespace.equals(frag.getNamespace())) {
                return frag;
            }
        }
        return null;
    }

    public void clearFragments() {
        fragments.clear();
    }

    public int getSize() {
        return fragments.size();
    }
}
