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


import java.util.*;

/**
 * Manages event handling with the opening and closing of chat rooms.
 *
 * @author Derek DeMoro
 */
public final class ChatAuditManager {
    private static ChatAuditManager singleton;
    private static final Object LOCK = new Object();

    private final Map roomMap = new HashMap();
    private final List listeners = new LinkedList();


    /**
     * Returns the singleton instance of <CODE>ChatAuditManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>ChatAuditManager</CODE>
     */
    public static ChatAuditManager getInstance() {
        // Synchronize on LOCK to ensure that we don't end up creating
        // two singletons.
        synchronized (LOCK) {
            if (null == singleton) {
                ChatAuditManager controller = new ChatAuditManager();
                singleton = controller;
                return controller;
            }
        }
        return singleton;
    }

    private ChatAuditManager() {
    }

    /**
     * Adds an <code>ChatRoomListener</code> to the manager.
     *
     * @param listener the <code>ChatRoomListener</code> to be added
     */
    public void addChatRoomListener(ChatRoomListener listener) {
        listeners.add(listener);
    }


    /**
     * Removes an <code>ChatRoomListener</code> from the manager.
     *
     * @param listener the <code>ChatRoomListener</code> to be removed
     */
    public void removeChatRoomListener(ChatRoomListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all ChatRoomListeners that a ChatRoom has been opened.
     *
     * @param room the <code>ChatRoom</code> that was opened.
     * @see ChatRoomListener
     */
    public void fireChatRoomOpened(ChatRoom room) {
        Iterator iter = listeners.iterator();
        while (iter.hasNext()) {
            ((ChatRoomListener)iter.next()).roomOpened(room);
        }
    }

    /**
     * Notifies all ChatRoomListeners that a ChatRoom has been closed.
     *
     * @param room the <code>ChatRoom</code> that was closed.
     * @see ChatRoomListener
     */
    public void fireChatRoomClosed(ChatRoom room) {
        Iterator iter = listeners.iterator();
        while (iter.hasNext()) {
            ((ChatRoomListener)iter.next()).roomClosed(room);
        }
    }

    /**
     * Notifies all ChatRoomListeners that a member has joined the room.
     *
     * @param room the <code>ChatRoom</code> that the member just joined.
     * @param user the <code>ChatUser</code> who just joined the room.
     * @see ChatRoomListener
     * @see ChatRoom
     * @see ChatUser
     */
    public void fireMemberJoined(ChatRoom room, ChatUser user) {
        Iterator iter = listeners.iterator();
        while (iter.hasNext()) {
            ((ChatRoomListener)iter.next()).memberJoinedRoom(room, user);
        }
    }

    /**
     * Notifies all ChatRoomListeners that a member has left the room.
     *
     * @param room the <code>ChatRoom</code> that the member just left.
     * @param user the <code>ChatUser</code> who just left the room.
     * @see ChatRoomListener
     * @see ChatRoom
     * @see ChatUser
     */
    public void fireMemberLeftRoom(ChatRoom room, ChatUser user) {
        Iterator iter = listeners.iterator();
        while (iter.hasNext()) {
            ((ChatRoomListener)iter.next()).memberLeftRoom(room, user);
        }
    }

    /**
     * Adds a <code>ChatRoomHistory</code>.
     *
     * @param history the <code>ChatRoomHistory</code> to add.
     */
    public void addChatHistory(ChatRoomHistory history) {
        roomMap.put(history.getRoomname(), history);
    }

    /**
     * Returns a <code>ChatRoomHistory</code> for a given room.
     *
     * @param roomname the name of the <code>ChatRoom</code>
     * @return the <code>ChatRoomHistory</code> for the given room.
     */
    public ChatRoomHistory getChatHistory(String roomname) {
        return (ChatRoomHistory)roomMap.get(roomname);
    }
}

