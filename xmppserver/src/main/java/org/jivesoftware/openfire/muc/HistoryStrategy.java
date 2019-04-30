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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jivesoftware.openfire.muc.cluster.UpdateHistoryStrategy;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;

/**
 * <p>Multi-User Chat rooms may cache history of the conversations in the room in order to
 * play them back to newly arriving members.</p>
 * 
 * <p>This class is an internal component of MUCRoomHistory that describes the strategy that can 
 * be used, and provides a method of administering the history behavior.</p>
 *
 * @author Gaston Dombiak
 * @author Derek DeMoro
 */
public class HistoryStrategy {

    private static final Logger Log = LoggerFactory.getLogger(HistoryStrategy.class);

    /**
     * The type of strategy being used.
     */
    private Type type = Type.number;

    /**
     * List containing the history of messages.
     */
    private ConcurrentLinkedQueue<Message> history = new ConcurrentLinkedQueue<>();
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
     * Create a history strategy with the given parent strategy (for defaults) or null if no 
     * parent exists.
     *
     * @param parentStrategy The parent strategy of this strategy or null if none exists.
     */
    public HistoryStrategy(HistoryStrategy parentStrategy) {
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
        if (parent == null) {
            // Update the history strategy of the MUC service
            CacheFactory.doClusterTask(new UpdateHistoryStrategy(contextSubdomain, this));
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
        if (parent == null) {
            // Update the history strategy of the MUC service
            CacheFactory.doClusterTask(new UpdateHistoryStrategy(contextSubdomain, this));
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
     * Add a message to the current chat history. The strategy type will determine what 
     * actually happens to the message.
     *
     * @param packet The packet to add to the chatroom's history.
     */
    public void addMessage(Message packet){
        // get the conditions based on default or not
        Type strategyType;
        int strategyMaxNumber;
        if (type == Type.defaulType && parent != null) {
            strategyType = parent.getType();
            strategyMaxNumber = parent.getMaxNumber();
        }
        else {
            strategyType = type;
            strategyMaxNumber = maxNumber;
        }

        // Room subject change messages are special
        boolean subjectChange = isSubjectChangeRequest(packet);
        if (subjectChange) {
            roomSubject = packet;
            return;
        }

        // store message according to active strategy
        if (strategyType == Type.all) {
            history.add(packet);
        }
        else if (strategyType == Type.number) {
            if (history.size() >= strategyMaxNumber) {
                // We have to remove messages so the new message won't exceed
                // the max history size
                // This is complicated somewhat because we must skip over the
                // last room subject
                // message because we want to preserve the room subject if
                // possible.
                Iterator<Message> historyIter = history.iterator();
                while (historyIter.hasNext() && history.size() >= strategyMaxNumber) {
                    if (historyIter.next() != roomSubject) {
                        historyIter.remove();
                    }
                }
            }
            history.add(packet);
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
     * Obtain the current history as an iterator of messages to play back to a new room member.
     * 
     * @return An iterator of Message objects to be sent to the new room member.
     */
    public Iterator<Message> getMessageHistory(){
        LinkedList<Message> list = new LinkedList<>(history);
        // Sort messages. Messages may be out of order when running inside of a cluster
        Collections.sort(list, new MessageComparator());
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
        LinkedList<Message> list = new LinkedList<>(history);
        // Sort messages. Messages may be out of order when running inside of a cluster
        Collections.sort(list, new MessageComparator());
        return list.listIterator(list.size());
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
}
