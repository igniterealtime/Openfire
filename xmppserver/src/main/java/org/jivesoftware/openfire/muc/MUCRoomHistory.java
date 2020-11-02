/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.muc;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.StringReader;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Represent the data model for one <code>MUCRoom</code> history. Including chat transcript,
 * joining and leaving times.
 * 
 * @author Gaston Dombiak
 */
public final class MUCRoomHistory {
    private static final Logger Log = LoggerFactory.getLogger(MUCRoomHistory.class);

    private MUCRoom room;

    private HistoryStrategy historyStrategy;

    private boolean isNonAnonymousRoom;

    public MUCRoomHistory(MUCRoom mucRoom, HistoryStrategy historyStrategy) {
        this.room = mucRoom;
        this.isNonAnonymousRoom = mucRoom.canAnyoneDiscoverJID();
        this.historyStrategy = historyStrategy;
    }

    public void addMessage(Message packet) {
        boolean isSubjectChangeRequest = isSubjectChangeRequest(packet);
        JID fromJID = packet.getFrom();
        // Don't keep messages whose sender is the room itself (thus address without resource)
        // unless the message is changing the room's subject
        if (!isSubjectChangeRequest &&
            (fromJID == null || fromJID.toString().length() == 0 ||
             fromJID.equals(room.getRole().getRoleAddress()))) {
            return;
        }
        // Do not store regular messages if there is no message strategy (keep subject change requests)
        if (!isSubjectChangeRequest && !historyStrategy.isHistoryEnabled()) {
            return;
        }

        // Ignore empty messages (no subject AND no body)
        if (!isSubjectChangeRequest &&
            (packet.getBody() == null || packet.getBody().trim().length() == 0)) {
            return;
        }

        Message packetToAdd = packet.createCopy();

        // Check if the room has changed its configuration
        if (isNonAnonymousRoom != room.canAnyoneDiscoverJID()) {
            isNonAnonymousRoom = room.canAnyoneDiscoverJID();
            // Update the "from" attribute of the delay information in the history
            // TODO Make this update in a separate thread
            for (Iterator<Message> it = getMessageHistory(); it.hasNext();) {
                Message message = it.next();
                Element delayElement = message.getChildElement("delay", "urn:xmpp:delay");
                if (room.canAnyoneDiscoverJID()) {
                    // Set the Full JID as the "from" attribute
                    try {
                        MUCRole role = room.getOccupant(message.getFrom().getResource());
                        delayElement.addAttribute("from", role.getUserAddress().toString());
                    }
                    catch (UserNotFoundException e) {
                        // Ignore.
                    }
                }
                else {
                    // Set the Room JID as the "from" attribute
                    delayElement.addAttribute("from", message.getFrom().toString());
                }
            }

        }

        // Add the delay information to the message
        Element delayInformation = packetToAdd.addChildElement("delay", "urn:xmpp:delay");
        Date current = new Date();
        delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(current));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            try {
                MUCRole role = room.getOccupant(packet.getFrom().getResource());
                delayInformation.addAttribute("from", role.getUserAddress().toString());
            }
            catch (UserNotFoundException e) {
                // Ignore.
            }
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.addAttribute("from", packet.getFrom().toString());
        }
        historyStrategy.addMessage(packetToAdd);
    }

    public Iterator<Message> getMessageHistory() {
        return historyStrategy.getMessageHistory();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list
     * iterator will be positioned at the end of the history so senders of this message must
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator<Message> getReverseMessageHistory() {
        return historyStrategy.getReverseMessageHistory();
    }

    /**
     * Creates a new message and adds it to the history. The new message will be created based on
     * the provided information. This information will likely come from the database when loading
     * the room history from the database.
     *
     * @param senderJID the sender's JID of the message to add to the history.
     * @param nickname the sender's nickname of the message to add to the history.
     * @param sentDate the date when the message was sent to the room.
     * @param subject the subject included in the message.
     * @param body the body of the message.
     * @param stanza the stanza to add
     */
    public void addOldMessage(String senderJID, String nickname, Date sentDate, String subject,
            String body, String stanza)
    {
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        if (stanza != null) {
            // payload initialized as XML string from DB
            try {
                SAXReader xmlReader = setupSAXReader();
                Element element = xmlReader.read(new StringReader(stanza)).getRootElement();
                for (Element child : (List<Element>)element.elements()) {
                    Namespace ns = child.getNamespace();
                    if (ns == null || ns.getURI().equals("jabber:client") || ns.getURI().equals("jabber:server")) {
                        continue;
                    }
                    Element added = message.addChildElement(child.getName(), child.getNamespaceURI());
                    if (!child.getText().isEmpty()) {
                        added.setText(child.getText());
                    }
                    for (Attribute attr : (List<Attribute>)child.attributes()) {
                        added.addAttribute(attr.getQName(), attr.getValue());
                    }
                    for (Element el : (List<Element>)child.elements()) {
                        added.add(el.createCopy());
                    }
                }
                if (element.attribute("id") != null) {
                    message.setID(element.attributeValue("id"));
                }
            } catch (Exception ex) {
                Log.error("Failed to parse payload XML", ex);
            }
        }
        message.setSubject(subject);
        message.setBody(body);
        // Set the sender of the message
        if (nickname != null && nickname.trim().length() > 0) {
            JID roomJID = room.getRole().getRoleAddress();
            // Recreate the sender address based on the nickname and room's JID
            message.setFrom(new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true));
        }
        else {
            // Set the room as the sender of the message
            message.setFrom(room.getRole().getRoleAddress());
        }

        // Add the delay information to the message
        Element delayInformation = message.addChildElement("delay", "urn:xmpp:delay");
        delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(sentDate));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            delayInformation.addAttribute("from", senderJID);
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.addAttribute("from", room.getRole().getRoleAddress().toString());
        }
        historyStrategy.addMessage(message);
    }

    private SAXReader setupSAXReader() throws SAXException {
        SAXReader xmlReader = new SAXReader();
        xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        xmlReader.setEncoding("UTF-8");
        return xmlReader;
    }

    /**
     * Returns true if there is a message within the history of the room that has changed the
     * room's subject.
     *
     * @return true if there is a message within the history of the room that has changed the
     *         room's subject.
     */
    public boolean hasChangedSubject() {
        return historyStrategy.hasChangedSubject();
    }

    /**
     * Returns the message within the history of the room that has changed the
     * room's subject.
     * 
     * @return the latest room subject change or null if none exists yet.
     */
    public Message getChangedSubject() {
        return historyStrategy.getChangedSubject();
    }

    /**
     * Returns true if the given message qualifies as a subject change request, per XEP-0045.
     *
     * @param message the message to check
     * @return true if the given packet is a subject change request
     */
    public boolean isSubjectChangeRequest(Message message) {
        return historyStrategy.isSubjectChangeRequest(message);
    }
}
