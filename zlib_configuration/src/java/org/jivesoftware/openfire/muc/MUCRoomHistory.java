/**
 * $RCSfile: MUCRoomHistory.java,v $
 * $Revision: 3157 $
 * $Date: 2005-12-04 22:54:55 -0300 (Sun, 04 Dec 2005) $
 *
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

import org.dom4j.Element;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.util.Date;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.TimeZone;

/**
 * Represent the data model for one <code>MUCRoom</code> history. Including chat transcript,
 * joining and leaving times.
 * 
 * @author Gaston Dombiak
 */
public final class MUCRoomHistory {

    private static final FastDateFormat UTC_FORMAT = FastDateFormat
            .getInstance(JiveConstants.XMPP_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));
    private static final FastDateFormat UTC_FORMAT_OLD = FastDateFormat
            .getInstance(JiveConstants.XMPP_DELAY_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));

    private MUCRoom room;

    private HistoryStrategy historyStrategy;

    private boolean isNonAnonymousRoom;

    public MUCRoomHistory(MUCRoom mucRoom, HistoryStrategy historyStrategy) {
        this.room = mucRoom;
        this.isNonAnonymousRoom = mucRoom.canAnyoneDiscoverJID();
        this.historyStrategy = historyStrategy;
    }

    public void addMessage(Message packet) {
        // Don't keep messages whose sender is the room itself (thus address without resource)
        // unless the message is changing the room's subject
        if ((packet.getFrom() == null || packet.getFrom().toString().length() == 0 ||
                packet.getFrom().equals(room.getRole().getRoleAddress())) &&
                packet.getSubject() == null) {
            return;
        }
        // Do not store messages is strategy is none and message is not changing the room subject
        if (!historyStrategy.isHistoryEnabled()) {
            if (packet.getSubject() == null || packet.getSubject().trim().length() == 0) {
                return;
            }
        }

        // Ignore messages with no subject AND no body
        if ((packet.getSubject() == null || "".equals(packet.getSubject().trim())) &&
                (packet.getBody() == null || "".equals(packet.getBody().trim()))) {
            return;
        }

        Message packetToAdd = packet.createCopy();

        // Check if the room has changed its configuration
        if (isNonAnonymousRoom != room.canAnyoneDiscoverJID()) {
            isNonAnonymousRoom = room.canAnyoneDiscoverJID();
            // Update the "from" attribute of the delay information in the history
            Message message;
            Element delayElement;
            // TODO Make this update in a separate thread
            for (Iterator it = getMessageHistory(); it.hasNext();) {
                message = (Message) it.next();
                delayElement = message.getChildElement("x", "jabber:x:delay");
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
        Element delayInformationOld = packetToAdd.addChildElement("x", "jabber:x:delay");
        Date current = new Date();
        delayInformation.addAttribute("stamp", UTC_FORMAT.format(current));
        delayInformationOld.addAttribute("stamp", UTC_FORMAT_OLD.format(current));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            try {
                MUCRole role = room.getOccupant(packet.getFrom().getResource());
                delayInformation.addAttribute("from", role.getUserAddress().toString());
                delayInformationOld.addAttribute("from", role.getUserAddress().toString());
            }
            catch (UserNotFoundException e) {
                // Ignore.
            }
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.addAttribute("from", packet.getFrom().toString());
            delayInformationOld.addAttribute("from", packet.getFrom().toString());
        }
        historyStrategy.addMessage(packetToAdd);
    }

    public Iterator getMessageHistory() {
        return historyStrategy.getMessageHistory();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list
     * iterator will be positioned at the end of the history so senders of this message must
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator getReverseMessageHistory() {
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
     */
    public void addOldMessage(String senderJID, String nickname, Date sentDate, String subject,
            String body)
    {
        Message message = new Message();
        message.setType(Message.Type.groupchat);
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
        Element delayInformationOld = message.addChildElement("x", "jabber:x:delay");
        delayInformation.addAttribute("stamp", UTC_FORMAT.format(sentDate));
        delayInformationOld.addAttribute("stamp", UTC_FORMAT_OLD.format(sentDate));
        if (room.canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            delayInformation.addAttribute("from", senderJID);
            delayInformationOld.addAttribute("from", senderJID);
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.addAttribute("from", room.getRole().getRoleAddress().toString());
            delayInformationOld.addAttribute("from", room.getRole().getRoleAddress().toString());
        }
        historyStrategy.addMessage(message);
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
}