/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chat;

import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.Message;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * <p>Chat rooms may cache history of the conversations in the room in order to
 * play them back to newly arriving members.</p>
 * <p>This class describes the strategy that can be used, and provides a method
 * of administering the history behaviro.</p>
 *
 * @author Iain Shigeoka
 * @author Derek DeMoro
 */
public class HistoryStrategy {

    private Type type = Type.number;

    /**
     * <p>List containing the history of messages.</p>
     */
    private LinkedList history = new LinkedList();
    /**
     * Default max number.
     */
    private static final int DEFAULT_MAX_NUMBER = 25;
    /**
     * <p>The maximum number of chat history messages stored for the room.</p>
     */
    private int maxNumber;
    /**
     * <p>The parent history used for default settings, or null if no parent
     * (chat server defaults).
     */
    private HistoryStrategy parent;
    /**
     * <p>Track the latest room subject change or null if none exists yet.</p>
     */
    private Message roomSubject = null;
    /**
     * <p>If set, indicates settings should be saved to the given context.</p>
     */
    private ModuleContext context = null;
    /**
     * <p>The string prefix to be used on the context property names
     * (do not include trailing dot).</p>
     */
    private String contextPrefix = null;

    /**
     * <p>Create a history strategy with the given parent strategy (for defaults)
     * or null if no parent exists.</p>
     *
     * @param parentStrategy The parent strategy of this strategy or null if none exists
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
     * <p>Obtain the maximum number of messages for strategies using message number
     * limitations.</p>
     *
     * @return The maximum number of messages to store in applicable strategies
     */
    public int getMaxNumber() {
        return maxNumber;
    }

    /**
     * <p>Set the maximum number of messages for strategies using message number
     * limitations.</p>
     *
     * @param max The maximum number of messages to store in applicable strategies
     */
    public void setMaxNumber(int max) {
        this.maxNumber = max;
        if (context != null) {
            context.setProperty(contextPrefix + ".maxNumber",
                    Integer.toString(maxNumber));
        }
    }

    /**
     * <p>Set the type of history strategy being used.</p>
     *
     * @param newType The new type of chat history to use
     */
    public void setType(Type newType) {
        if (newType != null) {
            type = newType;
        }
        if (context != null) {
            context.setProperty(contextPrefix + ".type", type.toString());
        }
    }

    /**
     * <p>Obtain the type of history strategy being used.</p>
     *
     * @return The current type of strategy being used
     */
    public Type getType() {
        return type;
    }

    /**
     * <p>Add a message to the current chat history.</p>
     * <p>The strategy type will determine what actually happens to the message.</p>
     *
     * @param packet The packet to add to the chatroom's history.
     */
    public void addMessage(Message packet) {
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
        boolean subjectChange = false;
        if (packet.getSubject() != null && packet.getSubject().length() > 0) {
            subjectChange = true;
            roomSubject = packet;
        }

        // store message according to active strategy
        if (strategyType == Type.none) {
            if (subjectChange) {
                history.clear();
                history.add(packet);
            }
        }
        else if (strategyType == Type.all) {
            history.addLast(packet);
        }
        else if (strategyType == Type.number) {
            if (history.size() >= strategyMaxNumber) {
                // We have to remove messages so the new message won't exceed
                // the max history size
                // This is complicated somewhat because we must skip over the
                // last room subject
                // message because we want to preserve the room subject if
                // possible.
                ListIterator historyIter = history.listIterator();
                while (historyIter.hasNext()
                        && history.size() > strategyMaxNumber) {
                    if (historyIter.next() != roomSubject) {
                        historyIter.remove();
                    }
                }
            }
            history.addLast(packet);
        }
    }

    /**
     * <p>Obtain the current history as an iterator of messages to play
     * back to a new room member.</p>
     *
     * @return An iterator of Message objects to be sent to the new room member
     */
    public Iterator getMessageHistory() {
        LinkedList list = new LinkedList(history);
        return list.iterator();
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
     * and parent != null DEFAULT is used, otherwise, NUMBER is used.
     *
     * @param typeName The text name of the strategy type
     */
    public void setTypeFromString(String typeName) {
        try {
            setType(Type.valueOf(typeName));
        }
        catch (IllegalArgumentException ie) {
            if (parent != null) {
                setType(Type.defaulType);
            }
            else {
                setType(Type.number);
            }
        }
    }

    /**
     * <p>Sets the context and string prefix to use for retrieving and saving
     * settings (and also triggers an immediate loading of properties).</p>
     *
     * @param newContext The context to use to read/write properties
     * @param prefix     The prefix to use (without trailing dot) on property names
     */
    public void setContext(ModuleContext newContext, String prefix) {
        this.context = newContext;
        this.contextPrefix = prefix;
        setTypeFromString(context.getProperty(prefix + ".type"));
        String maxNumberString = context.getProperty(prefix + ".maxNumber");
        if (maxNumberString != null && maxNumberString.trim().length() > 0) {
            try {
                setMaxNumber(Integer.parseInt(maxNumberString));
            }
            catch (Exception e) {
                Log.info("Jive property "
                        + prefix + ".maxNumber not a valid number.");
            }
        }
    }
}
