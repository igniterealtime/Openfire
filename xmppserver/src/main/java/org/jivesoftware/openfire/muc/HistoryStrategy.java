/*
 * Copyright (C) 2004-2008 Jive Software, 2022 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * <p>Multi-User Chat rooms may cache history of the conversations in the room in order to
 * play them back to newly arriving members.</p>
 * 
 * <p>This class is an internal component of MUCRoomHistory that describes the strategy that can 
 * be used, and provides a method of administering the history behavior.</p>
 *
 * @author Gaston Dombiak
 * @author Derek DeMoro
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class HistoryStrategy implements Externalizable {

    private static final Logger Log = LoggerFactory.getLogger(HistoryStrategy.class);

    /**
     * An unlimited cache that records (references to) MUC room messages. The key of the cache is the room JID for which
     * a list of messages is recorded. The value is a wrapper around a set of references for messages. These references
     * are keys in the {@link #MUC_HISTORY_MESSAGES_CACHE} cache.
     */
    private static final Cache<JID, CacheableOptional<ConcurrentLinkedQueue<UUID>>> MUC_HISTORY_META_CACHE = CacheFactory.createCache("MUC History Meta");

    /**
     * An unlimited cache that holds individual messages, expected to be MUC room messages. The key of the cache is the
     * reference used in {@link #MUC_HISTORY_META_CACHE}. The value is the dom4j element that backs the message stanza.
     *
     * This cache should only be referenced while holding a lock on the key of MUC_HISTORY_META_CACHE that represents
     * the address of the room in which a message was exchanged!
     */
    private static final Cache<UUID, DefaultElement> MUC_HISTORY_MESSAGES_CACHE = CacheFactory.createCache("MUC History Messages");

    /**
     * The address of the room (expected to be a bare JID) for which this instance records message history.
     */
    private JID roomJID;

    /**
     * The type of strategy being used.
     */
    private Type type = Type.number;

    /**
     * Default max number.
     */
    private static final int DEFAULT_MAX_NUMBER = 25;

    /**
     * The maximum number of chat history messages stored for the room.
     */
    private int maxNumber;

    /**
     * The parent history used for default settings, or null if no parent
     * (chat server defaults).
     */
    private HistoryStrategy parent;

    /**
     * Track the latest room subject change or null if none exists yet.
     */
    private Message roomSubject = null;

    /**
     * The string prefix to be used on the context property names
     * (do not include trailing dot).
     */
    private String contextPrefix = null;

    /**
     * The subdomain of the service the properties are set on.
     */
    private String contextSubdomain = null;

    /**
     * This constructor is provided to comply with the Externalizable interface contract. It should not be used directly.
     */
    public HistoryStrategy()
    {}

    /**
     * Create a history strategy with the given parent strategy (for defaults) or null if no 
     * parent exists.
     *
     * @param roomJID the unique identifier of the room for which this strategy will store messages.
     * @param parentStrategy The parent strategy of this strategy or null if none exists.
     */
    public HistoryStrategy(JID roomJID, HistoryStrategy parentStrategy) {
        this.roomJID = roomJID;
        this.parent = parentStrategy;
        if (parent == null) {
            maxNumber = DEFAULT_MAX_NUMBER;
        }
        else {
            type = Type.defaulType;
            maxNumber = parent.getMaxNumber();
        }
    }

    /**
     * Obtain the maximum number of messages for strategies using message number limitations.
     *
     * @return The maximum number of messages to store in applicable strategies.
     */
    public int getMaxNumber() {
        return maxNumber;
    }

    /**
     * Set the maximum number of messages for strategies using message number limitations.
     *
     * @param max the maximum number of messages to store in applicable strategies.
     */
    public void setMaxNumber(int max) {
        if (maxNumber == max) {
            // Do nothing since value has not changed
            return;
        }
        this.maxNumber = max;
        if (contextPrefix != null){
            MUCPersistenceManager.setProperty(contextSubdomain, contextPrefix + ".maxNumber", Integer.toString(maxNumber));
        }
    }

    /**
     * Set the type of history strategy being used.
     *
     * @param newType The new type of chat history to use.
     */
    public void setType(Type newType){
        if (type == newType) {
            // Do nothing since value has not changed
            return;
        }
        if (newType != null){
            type = newType;
        }
        if (contextPrefix != null){
            MUCPersistenceManager.setProperty(contextSubdomain, contextPrefix + ".type", type.toString());
        }
    }

    /**
     * Obtain the type of history strategy being used.
     *
     * @return The current type of strategy being used.
     */
    public Type getType(){
        return type;
    }

    /**
     * Add a message(s) to the current chat history. The strategy type will determine what
     * actually happens to the message.
     *
     * @param packets The messages to add to the chatroom's history.
     */
    public void addMessage(@Nonnull final Message... packets)
    {
        // Room subject change messages are special
        for (final Message packet : packets) {
            boolean subjectChange = isSubjectChangeRequest(packet);
            if (subjectChange) {
                roomSubject = packet;
            }
        }

        // Do not process subject changes anymore after this point.
        final List<Message> messages = Arrays.stream(packets).filter(p -> !isSubjectChangeRequest(p)).collect(Collectors.toList());

        if (messages.isEmpty()) {
            return;
        }

        // get the conditions based on default or not
        final Type strategyType;
        final int strategyMaxNumber;
        if (type == Type.defaulType && parent != null) {
            strategyType = parent.getType();
            strategyMaxNumber = parent.getMaxNumber();
        }
        else {
            strategyType = type;
            strategyMaxNumber = maxNumber;
        }

        final Lock lock = MUC_HISTORY_META_CACHE.getLock(roomJID);
        lock.lock();
        try {
            final CacheableOptional<ConcurrentLinkedQueue<UUID>> optional = MUC_HISTORY_META_CACHE.get(roomJID);
            final ConcurrentLinkedQueue<UUID> references;
            if (optional == null || optional.isAbsent()) {
                references = new ConcurrentLinkedQueue<>();
            } else {
                references = optional.get();
            }
            for (final Message message : messages) {
                // store message according to active strategy.
                if (strategyType == Type.number) {
                    if (references.size() >= strategyMaxNumber) {
                        // We have to remove messages so the new message won't exceed the max history size.
                        while (!references.isEmpty() && references.size() >= strategyMaxNumber) {
                            final UUID oldReference = references.poll();
                            MUC_HISTORY_MESSAGES_CACHE.remove(oldReference);
                        }
                    }
                }

                if (strategyType == Type.all || strategyType == Type.number) {
                    final UUID reference = UUID.randomUUID();
                    references.add(reference);
                    MUC_HISTORY_MESSAGES_CACHE.put(reference, (DefaultElement) message.getElement());
                }
            }

            // Explicitly add back to cache (Hazelcast won't update-by-reference).
            MUC_HISTORY_META_CACHE.put(roomJID, CacheableOptional.of(references));
        } finally {
            lock.unlock();
        }
    }

    boolean isHistoryEnabled() {
        Type strategyType = type;
        if (type == Type.defaulType && parent != null) {
            strategyType = parent.getType();
        }
        return strategyType != HistoryStrategy.Type.none;
    }

    /**
     * Obtains the historic messages cached for this particular room from the cache.
     *
     * This method ensures that a room-based lock is acquired, before interacting with the cache.
     *
     * Note that modifications applied to the returned collection are not guaranteed to be applied to the cache.
     * Explicitly put the collection back in the cache to ensure that changes are persisted there.
     *
     * @return The historic messages for this room.
     */
    protected Queue<Message> getHistoryFromCache() {
        // Ensure room history is in cache. Doing this outside of the lock below, to reduce the likelihood of deadlocks occurring.
        if (!MUC_HISTORY_META_CACHE.containsKey(roomJID)) {
            try {
                final MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());
                MUCPersistenceManager.loadHistory(room, getMaxNumber());
            } catch (Exception e) {
                Log.error("Unable to load history for room {} from database.", roomJID, e);
            }
        }

        // Obtain history from cache.
        final Lock lock = MUC_HISTORY_META_CACHE.getLock(roomJID);
        lock.lock();
        try {
            final CacheableOptional<ConcurrentLinkedQueue<UUID>> optional = MUC_HISTORY_META_CACHE.get(roomJID);
            final ConcurrentLinkedQueue<Message> result = new ConcurrentLinkedQueue<>();
            if (optional != null && !optional.isAbsent()) {
                for (final UUID reference : optional.get()) {
                    final DefaultElement messageElement = MUC_HISTORY_MESSAGES_CACHE.get(reference);
                    if (messageElement == null) {
                        Log.warn("Unable to retrieve message of room {} from clustered cache by reference: {}", roomJID, reference);
                    } else {
                        result.add(new Message(messageElement, true));
                    }
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Obtain the current history as an iterator of messages to play back to a new room member.
     * 
     * @return An iterator of Message objects to be sent to the new room member.
     */
    public Iterator<Message> getMessageHistory(){
        final LinkedList<Message> list = new LinkedList<>(getHistoryFromCache());
        // Sort messages. Messages may be out of order when running inside of a cluster
        list.sort(new MessageComparator());
        return list.iterator();
    }

    /**
     * Obtain the current history to be iterated in reverse mode. This means that the returned list 
     * iterator will be positioned at the end of the history so senders of this message must 
     * traverse the list in reverse mode.
     * 
     * @return A list iterator of Message objects positioned at the end of the list.
     */
    public ListIterator<Message> getReverseMessageHistory(){
        final LinkedList<Message> list = new LinkedList<>(getHistoryFromCache());
        // Sort messages. Messages may be out of order when running inside of a cluster
        list.sort(new MessageComparator());
        return list.listIterator(list.size());
    }

    /**
     * Removes all history that is maintained for this instance.
     */
    public void purge()
    {
        final Lock lock = MUC_HISTORY_META_CACHE.getLock(roomJID);
        lock.lock();
        try {
            final CacheableOptional<ConcurrentLinkedQueue<UUID>> oldReferences = MUC_HISTORY_META_CACHE.put(roomJID, CacheableOptional.of(null));
            if (oldReferences != null && oldReferences.isPresent()) {
                for (final UUID oldReference : oldReferences.get()) {
                    MUC_HISTORY_MESSAGES_CACHE.remove(oldReference);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, type);
        ExternalizableUtil.getInstance().writeSafeUTF(out, roomJID.toString());
        ExternalizableUtil.getInstance().writeInt(out, maxNumber);

        ExternalizableUtil.getInstance().writeBoolean(out,parent != null);
        if (parent != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, parent);
        }

        ExternalizableUtil.getInstance().writeBoolean(out, roomSubject != null);
        if (roomSubject != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement)roomSubject.getElement());
        }

        ExternalizableUtil.getInstance().writeBoolean(out, contextPrefix != null);
        if (contextPrefix != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, contextPrefix);
        }

        ExternalizableUtil.getInstance().writeBoolean(out, contextSubdomain != null);
        if (contextSubdomain != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, contextSubdomain);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = (Type) ExternalizableUtil.getInstance().readSerializable(in);
        roomJID = new JID(ExternalizableUtil.getInstance().readSafeUTF(in), false);
        maxNumber = ExternalizableUtil.getInstance().readInt(in);

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            parent = (HistoryStrategy) ExternalizableUtil.getInstance().readSerializable(in);
        } else {
            parent = null;
        }

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            roomSubject = new Message((DefaultElement) ExternalizableUtil.getInstance().readSerializable(in));
        } else {
            roomSubject = null;
        }

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            contextPrefix = ExternalizableUtil.getInstance().readSafeUTF(in);
        } else {
            contextPrefix = null;
        }

        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            contextSubdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        } else {
            contextSubdomain = null;
        }
    }

    /**
     * Strategy type.
     */
    public enum Type {
        defaulType, none, all, number;
    }

    /**
     * Obtain the strategy type from string name. See the Type enumeration name
     * strings for the names strings supported. If nothing matches
     * and parent is not null, then the default strategy is used. Otherwise the number
     * strategy is used.
     *
     * @param typeName the text name of the strategy type.
     */
    public void setTypeFromString(String typeName) {
        try {
            type = Type.valueOf(typeName);
        }
        catch (Exception e) {
            if (parent != null) {
                type = Type.defaulType;
            }
            else {
                type = Type.number;
            }
        }
    }

    /**
     * Sets the prefix to use for retrieving and saving settings (and also
     * triggers an immediate loading of properties).
     *
     * @param subdomain the subdomain of the muc service to pull properties for.
     * @param prefix the prefix to use (without trailing dot) on property names.
     */
    public void setContext(String subdomain, String prefix) {
        this.contextSubdomain = subdomain;
        this.contextPrefix = prefix;
        setTypeFromString(MUCPersistenceManager.getProperty(subdomain, prefix + ".type"));
        String maxNumberString = MUCPersistenceManager.getProperty(subdomain, prefix + ".maxNumber");
        if (maxNumberString != null && maxNumberString.trim().length() > 0){
            try {
                this.maxNumber = Integer.parseInt(maxNumberString);
            }
            catch (Exception e){
                Log.info("Jive property " + prefix + ".maxNumber not a valid number.");
            }
        }
    }

    /**
     * Returns true if there is a message within the history of the room that has changed the
     * room's subject.
     *
     * @return true if there is a message within the history of the room that has changed the
     *         room's subject.
     */
    public boolean hasChangedSubject() {
        return roomSubject != null;
    }

    /**
     * Returns the message within the history of the room that has changed the
     * room's subject.
     * 
     * @return the latest room subject change or null if none exists yet.
     */
    @Nullable
    public Message getChangedSubject() {
        return roomSubject;
    }

    /**
     * Returns true if the given message qualifies as a subject change request for
     * the target MUC room, per XEP-0045. Note that this does not validate whether 
     * the sender has permission to make the change, because subject change requests
     * may be loaded from history or processed "live" during a user's session.
     * 
     * Refer to http://xmpp.org/extensions/xep-0045.html#subject-mod for details.
     *
     * @param message the message to check
     * @return true if the given packet is a subject change request
     */
    public boolean isSubjectChangeRequest(Message message) {
        
        // The subject is changed by sending a message of type "groupchat" to the <room@service>, 
        // where the <message/> MUST contain a <subject/> element that specifies the new subject 
        // but MUST NOT contain a <body/> element (or a <thread/> element).
        // Unfortunately, many clients do not follow these strict guidelines from the specs, so we
        // allow a lenient policy for detecting non-conforming subject change requests. This can be
        // configured by setting the "xmpp.muc.subject.change.strict" property to false (true by default).
        // An empty <subject/> value means that the room subject should be removed.

        return Message.Type.groupchat == message.getType() && 
                message.getSubject() != null && 
                (!isSubjectChangeStrict() || 
                    (message.getBody() == null && 
                     message.getThread() == null));
    }

    private boolean isSubjectChangeStrict() {
        return JiveGlobals.getBooleanProperty("xmpp.muc.subject.change.strict", true);
    }

    private static class MessageComparator implements Comparator<Message> {
        @Override
        public int compare(Message o1, Message o2) {
            String stamp1 = o1.getChildElement("delay", "urn:xmpp:delay").attributeValue("stamp");
            String stamp2 = o2.getChildElement("delay", "urn:xmpp:delay").attributeValue("stamp");
            return stamp1.compareTo(stamp2);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryStrategy that = (HistoryStrategy) o;
        return maxNumber == that.maxNumber && type == that.type
            && Objects.equals(contextPrefix, that.contextPrefix) && Objects.equals(contextSubdomain, that.contextSubdomain)
            && (roomSubject == that.roomSubject || (roomSubject != null && that.roomSubject != null && Objects.equals(roomSubject.toXML(), that.roomSubject.toXML())) )
            && Objects.equals(roomJID, that.roomJID) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, roomJID, maxNumber, parent, roomSubject, contextPrefix, contextSubdomain);
    }
}
