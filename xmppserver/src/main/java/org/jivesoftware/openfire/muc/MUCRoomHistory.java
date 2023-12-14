/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.SAXReaderUtil;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

/**
 * Represent the data model for one <code>MUCRoom</code> history. Including chat transcript,
 * joining and leaving times.
 * 
 * @author Gaston Dombiak
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public final class MUCRoomHistory implements Externalizable {
    private static final Logger Log = LoggerFactory.getLogger(MUCRoomHistory.class);

    private JID roomAddress;

    private HistoryStrategy historyStrategy;

    private boolean isNonAnonymousRoom;

    private transient MUCRoom room; // Lazily initialized by #getRoom()

    /**
     * This constructor is provided to comply with the Externalizable interface contract. It should not be used directly.
     */
    public MUCRoomHistory()
    {}

    public MUCRoomHistory(MUCRoom room, HistoryStrategy historyStrategy) {
        this.roomAddress = room.getJID();
        this.room = room;
        this.isNonAnonymousRoom = getRoom().canAnyoneDiscoverJID();
        this.historyStrategy = historyStrategy;
    }

    public void addMessage(Message packet) {
        boolean isSubjectChangeRequest = isSubjectChangeRequest(packet);
        JID fromJID = packet.getFrom();
        // Don't keep messages whose sender is the room itself (thus address without resource)
        // unless the message is changing the room's subject
        if (!isSubjectChangeRequest &&
            (fromJID == null || fromJID.toString().length() == 0 ||
             fromJID.equals(getRoom().getRole().getRoleAddress()))) {
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
        if (isNonAnonymousRoom != getRoom().canAnyoneDiscoverJID()) {
            isNonAnonymousRoom = getRoom().canAnyoneDiscoverJID();
            // Update the "from" attribute of the delay information in the history
            // TODO Make this update in a separate thread
            for (Iterator<Message> it = getMessageHistory(); it.hasNext();) {
                Message message = it.next();
                Element delayElement = message.getChildElement("delay", "urn:xmpp:delay");
                if (getRoom().canAnyoneDiscoverJID()) {
                    // Set the Full JID as the "from" attribute // TODO: This is pretty dodgy, as it depends on the user still being in the room. JIDs _should_ have been stored with the message.
                    try {
                        List<MUCRole> role = getRoom().getOccupantsByNickname(message.getFrom().getResource());
                        if (!role.isEmpty()) {
                            delayElement.addAttribute("from", role.get(0).getUserAddress().toString());
                        }
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
        if (getRoom().canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute // TODO: This is pretty dodgy, as it depends on the user still being in the room. JIDs _should_ have been stored with the message.
            try {
                List<MUCRole> role = getRoom().getOccupantsByNickname(packet.getFrom().getResource());
                if (!role.isEmpty()) {
                    delayInformation.addAttribute("from", role.get(0).getUserAddress().toString());
                }
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
     * Add message(s) to the history of the chat room.
     *
     * The messages will likely come from the database when loading the room history from the database.
     * @param oldMessages The messages to add to the history
     */
    public void addOldMessages(@Nonnull final List<Message> oldMessages) {
        addOldMessages(oldMessages.toArray(new Message[0]));
    }

    /**
     * Add message(s) to the history of the chat room.
     *
     * The messages will likely come from the database when loading the room history from the database.
     * @param oldMessages The messages to add to the history
     */
    public void addOldMessages(@Nonnull final Message... oldMessages) {
        historyStrategy.addMessage(oldMessages);
    }

    /**
     * Creates a new message, representing a message that was exchanged in a chat room in the past, based on the
     * provided information.
     *
     * This information will likely come from the database when loading the room history from the database.
     *
     * @param senderJID the sender's JID of the message.
     * @param nickname the sender's nickname of the message.
     * @param sentDate the date when the message was sent to the room.
     * @param subject the subject included in the message.
     * @param body the body of the message.
     * @param stanza the stanza to add
     * @return A historic chat message.
     */
    public Message parseHistoricMessage(String senderJID, String nickname, Date sentDate, String subject,
                                        String body, String stanza)
    {
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        if (stanza != null) {
            // payload initialized as XML string from DB
            try {
                Element element = SAXReaderUtil.readRootElement(stanza);
                for (Element child : element.elements()) {
                    Namespace ns = child.getNamespace();
                    if (ns == null || ns.getURI().equals("jabber:client") || ns.getURI().equals("jabber:server")) {
                        continue;
                    }
                    Element added = message.addChildElement(child.getName(), child.getNamespaceURI());
                    if (!child.getText().isEmpty()) {
                        added.setText(child.getText());
                    }
                    for (Attribute attr : child.attributes()) {
                        added.addAttribute(attr.getQName(), attr.getValue());
                    }
                    for (Element el : child.elements()) {
                        added.add(el.createCopy());
                    }
                }
                if (element.attribute("id") != null) {
                    message.setID(element.attributeValue("id"));
                }
            } catch (Exception ex) {
                Log.error("Failed to parse payload XML", ex);
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        message.setSubject(subject);
        message.setBody(body);
        // Set the sender of the message
        if (nickname != null && nickname.trim().length() > 0) {
            JID roomJID = getRoom().getRole().getRoleAddress();
            // Recreate the sender address based on the nickname and room's JID
            message.setFrom(new JID(roomJID.getNode(), roomJID.getDomain(), nickname, true));
        }
        else {
            // Set the room as the sender of the message
            message.setFrom(getRoom().getRole().getRoleAddress());
        }

        // Add the delay information to the message
        Element delayInformation = message.addChildElement("delay", "urn:xmpp:delay");
        delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(sentDate));
        if (getRoom().canAnyoneDiscoverJID()) {
            // Set the Full JID as the "from" attribute
            delayInformation.addAttribute("from", senderJID);
        }
        else {
            // Set the Room JID as the "from" attribute
            delayInformation.addAttribute("from", getRoom().getRole().getRoleAddress().toString());
        }
        return message;
    }

    /**
     * Removes all history that is maintained for this instance.
     */
    public void purge()
    {
        historyStrategy.purge();
    }

    /**
     * Returns the room for which this instance is operating.
     *
     * @return A room.
     */
    protected synchronized MUCRoom getRoom() {
        if (room == null) {
            final MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomAddress);
            if (service == null) {
                throw new IllegalStateException("Deserializing history for non-existing service of room named " + roomAddress);
            }
            room = service.getChatRoom(roomAddress.getNode());
            if (room == null) {
                throw new IllegalStateException("Deserializing history for non-existing room named " + roomAddress);
            }
        }
        return room;
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
    @Nullable
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

    /**
     * Returns the maximum number of messages that is kept in history for this room, or -1 when there is no such limit.
     *
     * @return The maximum amount of historic messages to keep for this room, or -1.
     */
    public int getMaxMessages() {
        return historyStrategy.getType() == HistoryStrategy.Type.number ? historyStrategy.getMaxNumber() : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MUCRoomHistory that = (MUCRoomHistory) o;
        return isNonAnonymousRoom == that.isNonAnonymousRoom && roomAddress.equals(that.roomAddress) && historyStrategy.equals(that.historyStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomAddress, historyStrategy, isNonAnonymousRoom);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, roomAddress);
        ExternalizableUtil.getInstance().writeBoolean(out, isNonAnonymousRoom);
        ExternalizableUtil.getInstance().writeSerializable(out, historyStrategy);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        roomAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        isNonAnonymousRoom = ExternalizableUtil.getInstance().readBoolean(in);
        historyStrategy = (HistoryStrategy) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
