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

/**
 * Used to receive event notifications from <code>ChatRooms</code>.
 *
 * @author Derek DeMoro
 * @see ChatAuditManager
 */
public interface ChatRoomListener {

    /**
     * Called whenever a <code>ChatRoom</code> has been opened.
     *
     * @param room the <code>ChatRoom</code> that was opened.
     */
    void roomOpened(ChatRoom room);

    /**
     * Called whenever a <code>ChatRoom</code> has been closed.
     *
     * @param room the <code>ChatRoom</code> that was closed.
     */
    void roomClosed(ChatRoom room);

    /**
     * Called when a user has left a <code>ChatRoom</code>
     *
     * @param room the <code>ChatRoom</code> that was left.
     * @param user the <code>ChatUser</code> who left the room.
     */
    void memberLeftRoom(ChatRoom room, ChatUser user);

    /**
     * Called when a user has left a <code>ChatRoom</code>
     *
     * @param room the <code>ChatRoom</code> that was just joined.
     * @param user the <code>ChatUser</code> that has joined the room.
     */
    void memberJoinedRoom(ChatRoom room, ChatUser user);
}

