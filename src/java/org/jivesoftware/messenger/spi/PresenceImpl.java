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

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.MetaDataFragment;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.XMPPFragment;
import org.jivesoftware.messenger.XMPPPacket;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import java.util.Date;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.Document;
import org.dom4j.Element;

/**
 * Database implementation of the Presence interface.
 *
 * @author Iain Shigeoka
 */
public class PresenceImpl extends AbstractPacket implements Presence, Cacheable {

    private String username;
    private String uid = "";
    private Date loginTime;
    private Date updateTime;
    private int show;
    private int priority;
    private boolean visible;
    private String status;

    public PresenceImpl(User user, String uid) {
        this();
        if (user != null) {
            this.username = user.getUsername();
        }

        this.uid = uid;
    }

    public PresenceImpl() {
        show = Presence.SHOW_NONE;
        type = Presence.AVAILABLE;
        visible = false;
        loginTime = new Date();
        updateTime = loginTime;
        priority = NO_PRIORITY;
    }

    public boolean isAvailable() {
        return type != Presence.UNAVAILABLE;
    }

    public void setAvailable(boolean online) throws UnauthorizedException {
        if (online) {
            type = Presence.AVAILABLE;
        }
        else {
            type = Presence.UNAVAILABLE;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) throws UnauthorizedException {
        this.visible = visible;
    }

    public void parse(XMLStreamReader xpp) throws XMLStreamException {
        // Super class AbstractPacket will not move the parser from it's start position.
        parseRootAttributes(xpp);
        Document doc = null;
        try {
            // Extremely inefficient to parse it into a DOM then toss it about like this
            // but we'll optimize when we see it's a bottleneck
            doc = XPPReader.parseDocument(xpp);
            Element root = doc.getRootElement();

            show = Presence.SHOW_NONE;
            // Default priority is zero unless a value is provided
            priority = 0;

            Iterator subElements = root.elementIterator();
            while (subElements.hasNext()) {
                Element element = (Element)subElements.next();
                String name = element.getName();
                if ("show".equals(name)) {
                    String showText = root.element("show").getText();
                    if ("".equals(showText)) {
                        show = Presence.SHOW_NONE;
                    }
                    else if ("chat".equals(showText)) {
                        show = Presence.SHOW_CHAT;
                    }
                    else if ("dnd".equals(showText)) {
                        show = Presence.SHOW_DND;
                    }
                    else if ("away".equals(showText)) {
                        show = Presence.SHOW_AWAY;
                    }
                    else if ("xa".equals(showText)) {
                        show = Presence.SHOW_XA;
                    }
                }
                else if ("status".equals(name)) {
                    status = root.element("status").getTextTrim();
                }
                else if ("error".equals(name)) {
                    setType(ERROR);
                }
                else if ("priority".equals(name)) {
                    String priorityText = root.element("priority").getTextTrim();
                    if (priorityText != null && priorityText.length() > 0) {
                        priority = Integer.parseInt(priorityText);
                    }
                }
                else {
                    addFragment(new MetaDataFragment(element));
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public String getUsername() {
        return username;
    }

    public Date getLoginTime() {
        return (Date)loginTime.clone();
    }

    public Date getLastUpdateTime() {
        return (Date)updateTime.clone();
    }

    public void setLastUpdateTime(Date time) throws UnauthorizedException {
        updateTime.setTime(time.getTime());
    }

    public int getShow() {
        return show;
    }

    public void setShow(int show) throws UnauthorizedException {
        this.show = show;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) throws UnauthorizedException {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(username);      // username
        size += CacheSizes.sizeOfString(uid);           // uid
        size += CacheSizes.sizeOfDate();                // login date
        size += CacheSizes.sizeOfDate();                // last update date
        size += CacheSizes.sizeOfInt();                 // show

        return size;
    }

    public String toString() {
        return super.toString() + " " + type.toString() + " S: " + sender + " R: " + recipient;
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        super.sendRoot(xmlSerializer, version, "presence", Presence.AVAILABLE);

        // if (show != Presence.SHOW_NONE)
        switch (show) {
            case Presence.SHOW_XA:
                xmlSerializer.writeStartElement("jabber:client", "show");
                xmlSerializer.writeCharacters("xa");
                xmlSerializer.writeEndElement();
                break;
            case Presence.SHOW_AWAY:
                xmlSerializer.writeStartElement("jabber:client", "show");
                xmlSerializer.writeCharacters("away");
                xmlSerializer.writeEndElement();
                break;
            case Presence.SHOW_CHAT:
                xmlSerializer.writeStartElement("jabber:client", "show");
                xmlSerializer.writeCharacters("chat");
                xmlSerializer.writeEndElement();
                break;
            case Presence.SHOW_DND:
                xmlSerializer.writeStartElement("jabber:client", "show");
                xmlSerializer.writeCharacters("dnd");
                xmlSerializer.writeEndElement();
                break;
        }
        if (status != null && status.length() > 0) {
            xmlSerializer.writeStartElement("jabber:client", "status");
            xmlSerializer.writeCharacters(status);
            xmlSerializer.writeEndElement();
        }
        if (priority != NO_PRIORITY) {
            xmlSerializer.writeStartElement("jabber:client", "priority");
            xmlSerializer.writeCharacters(Integer.toString(priority));
            xmlSerializer.writeEndElement();
        }

        Iterator frags = getFragments();
        while (frags.hasNext()) {
            ((XMPPFragment)frags.next()).send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {
        PresenceImpl presence = new PresenceImpl();
        deepCopy(presence);
        presence.username = username;
        presence.uid = uid;
        presence.loginTime = loginTime;
        presence.updateTime = updateTime;
        presence.show = show;
        presence.priority = priority;
        presence.visible = visible;
        presence.status = status;

        return presence;
    }

    public XMPPPacket.Type typeFromString(String type) {
        XMPPPacket.Type typeObject = super.typeFromString(type);
        if (typeObject == null) {
            if (type == null || type.length() == 0) {
                typeObject = Presence.AVAILABLE;
            }
            else if (Presence.UNAVAILABLE.toString().equals(type)) {
                typeObject = Presence.UNAVAILABLE;
            }
            else if (Presence.SUBSCRIBE.toString().equals(type)) {
                typeObject = Presence.SUBSCRIBE;
            }
            else if (Presence.SUBSCRIBED.toString().equals(type)) {
                typeObject = Presence.SUBSCRIBED;
            }
            else if (Presence.UNSUBSCRIBE.toString().equals(type)) {
                typeObject = Presence.UNSUBSCRIBE;
            }
            else if (Presence.INVISIBLE.toString().equals(type)) {
                typeObject = Presence.INVISIBLE;
            }
        }
        return typeObject;
    }
}