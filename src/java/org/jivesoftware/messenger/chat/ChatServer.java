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

import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * Manages groupchat conversations, chatrooms, and users.
 * This class is designed to operate independently from the
 * rest of the Jive server infrastruture. This theoretically
 * allows deployment of the groupchat on a separate server from
 * the main IM server.
 * <p/>
 * TODO Enforce chat security and authorization using proxies
 *
 * @author Iain Shigeoka
 */
public interface ChatServer {

    /**
     * Obtain the name of this chat server.
     *
     * @return The chat server name (host name)
     */
    String getChatServerName();

    /**
     * Indicate if the server supports anonymous rooms (default is true).
     * Non-anonymous rooms is a JEP-0045 Multi-User Chat (MUC) feature
     * that causes the server to send the real JID of all participants in
     * room presence updates. 
     */
    boolean isUseAnonymousRooms();

    /**
     * Set the name of this chat server. The new name won't go
     * into effect until the server is restarted.
     *
     * @param name The chat server name (host name)
     */
    void setChatServerName(String name);

    /**
     * <p>Obtain the server-wide default message history settings.</p>
     *
     * @return The message history strategy defaults for the server
     */
    HistoryStrategy getHistoryStrategy();

    /**
     * Obtains a chatroom by name. A chatroom is created for
     * that name if none exists and the user has permission.
     *
     * @param roomName Name of the room to get
     * @return The chatroom for the given name
     * @throws UnauthorizedException If the caller doesn't have permission to
     *                               access this room
     */
    ChatRoom getChatRoom(String roomName) throws UnauthorizedException;

    /**
     * Removes the room associated with the given name.
     *
     * @param roomName The room to remove
     * @throws UnauthorizedException If the caller doesn't have permission
     */
    void removeChatRoom(String roomName) throws UnauthorizedException;

    /**
     * Removes a user from all chat rooms.
     *
     * @param jabberID The user's normal jid, not the chat nickname jid
     * @throws UnauthorizedException If the caller doesn't have permission
     */
    void removeUser(XMPPAddress jabberID) throws UnauthorizedException;

    /**
     * Obtain a chat user by XMPPAddress
     *
     * @param userjid The XMPPAddress of the user
     * @return The chatuser corresponding to that XMPPAddress
     * @throws UnauthorizedException If the caller doesn't have permission
     * @throws UserNotFoundException If the user is not found and can't be
     *                               auto-created
     */
    ChatUser getChatUser(XMPPAddress userjid)
            throws UnauthorizedException, UserNotFoundException;

    /**
     * Broadcast a given message to all members of this chat room.
     * The sender is always set to be the chatroom.
     *
     * @param msg The message to broadcast
     */
    void serverBroadcast(String msg) throws UnauthorizedException;

    /**
     * Returns the total chat time of all rooms combined.
     *
     * @return total chat time in milliseconds
     */
    public long getTotalChatTime();
}
