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

package org.jivesoftware.messenger.muc;

import java.util.List;
import java.util.Collection;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.*;

/**
 * Manages groupchat conversations, chatrooms, and users. This class is designed to operate
 * independently from the rest of the Jive server infrastruture. This theoretically allows
 * deployment of the groupchat on a separate server from the main IM server.
 * 
 * @author Gaston Dombiak
 */
public interface MultiUserChatServer {

    /**
     * Obtain the name of this chat service.
     * 
     * @return The chat server name (host name).
     */
    String getServiceName();

    /**
     * Set the name of this chat service. The new name won't go into effect until the server is
     * restarted.
     * 
     * @param name The chat service name (host name).
     */
    void setServiceName(String name);

    /**
     * Returns the collection of JIDs that are system administrators of the MUC service. A sysadmin has
     * the same permissions as a room owner. 
     * 
     * @return a list of bare JIDs.
     */
    Collection<String> getSysadmins();

    /**
     * Adds a new system administrator of the MUC service. A sysadmin has the same permissions as 
     * a room owner. 
     * 
     * @param userJID the bare JID of the new user to add as a system administrator.
     */
    void addSysadmin(String userJID);

    /**
     * Removes a system administrator of the MUC service.
     * 
     * @param userJID the bare JID of the user to remove from the list.
     */
    void removeSysadmin(String userJID);

    /**
     * Returns false if anyone can create rooms or true if only the returned JIDs in
     * <code>getUsersAllowedToCreate</code> are allowed to create rooms.
     *
     * @return true if only some JIDs are allowed to create rooms.
     */
    boolean isRoomCreationRestricted();

    /**
     * Sets if anyone can create rooms or if only the returned JIDs in
     * <code>getUsersAllowedToCreate</code> are allowed to create rooms.
     *
     * @param roomCreationRestricted whether anyone can create rooms or not.
     */
    void setRoomCreationRestricted(boolean roomCreationRestricted);

    /**
     * Returns the collection of JIDs that are allowed to create MUC rooms. An empty list means that
     * anyone can create a room. 
     * 
     * @return a list of bare JIDs.
     */
    Collection<String> getUsersAllowedToCreate();

    /**
     * Adds a new user to the list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the bare JID of the new user to add to list.
     */
    void addUserAllowedToCreate(String userJID);

    /**
     * Removes a user from list of JIDs that are allowed to create MUC rooms.
     * 
     * @param userJID the bare JID of the user to remove from the list.
     */
    void removeUserAllowedToCreate(String userJID);

    /**
     * Obtain the server-wide default message history settings.
     * 
     * @return The message history strategy defaults for the server.
     */
    HistoryStrategy getHistoryStrategy();

    /**
     * Obtains a chatroom by name. A chatroom is created for that name if none exists and the user
     * has permission. The user that asked for the chatroom will be the room's owner if the chatroom
     * was created.
     * 
     * @param roomName Name of the room to get.
     * @param userjid The user's normal jid, not the chat nickname jid.
     * @return The chatroom for the given name.
     * @throws UnauthorizedException If the caller doesn't have permission to access this room.
     */
    MUCRoom getChatRoom(String roomName, XMPPAddress userjid) throws UnauthorizedException;

    /**
     * Obtains a chatroom by name. If the chatroom does not exists then null will be returned.
     * 
     * @param roomName Name of the room to get.
     * @return The chatroom for the given name or null if the room does not exists.
     */
    MUCRoom getChatRoom(String roomName);

    /**
     * Returns true if the server includes a chatroom with the requested name.
     * 
     * @param roomName the name of the chatroom to check.
     * @return true if the server includes a chatroom with the requested name.
     */
    boolean hasChatRoom(String roomName);

    /**
     * Removes the room associated with the given name.
     * 
     * @param roomName The room to remove.
     * @throws UnauthorizedException If the caller doesn't have permission.
     */
    void removeChatRoom(String roomName) throws UnauthorizedException;

    /**
     * Removes a user from all chat rooms.
     * 
     * @param jabberID The user's normal jid, not the chat nickname jid.
     * @throws UnauthorizedException If the caller doesn't have permission.
     */
    void removeUser(XMPPAddress jabberID) throws UnauthorizedException;

    /**
     * Obtain a chat user by XMPPAddress.
     * 
     * @param userjid The XMPPAddress of the user.
     * @return The chatuser corresponding to that XMPPAddress.
     * @throws UnauthorizedException If the caller doesn't have permission.
     * @throws UserNotFoundException If the user is not found and can't be auto-created.
     */
    MUCUser getChatUser(XMPPAddress userjid) throws UnauthorizedException, UserNotFoundException;

    /**
     * Broadcast a given message to all members of this chat room. The sender is always set to be
     * the chatroom.
     * 
     * @param msg The message to broadcast.
     */
    void serverBroadcast(String msg) throws UnauthorizedException;

    /**
     * Returns the total chat time of all rooms combined.
     * 
     * @return total chat time in milliseconds.
     */
    public long getTotalChatTime();

    /**
     * Logs that a given message was sent to a room as part of a conversation. Every message sent
     * to the room that is allowed to be broadcasted and that was sent either from the room itself 
     * or from an occupant will be logged.<p>
     * 
     * Note: For performane reasons, the logged message won't be immediately saved. Instead we keep
     * the logged messages in memory until the logging process saves them to the database. It's 
     * possible to configure the logging process to run every X milliseconds and also the number 
     * of messages to log on each execution. 
     * @see org.jivesoftware.messenger.muc.spi.MultiUserChatServerImpl#initialize(org.jivesoftware.messenger.container.Container)
     * 
     * @param room the room that received the message.
     * @param message the message to log as part of the conversation in the room.
     * @param sender the real XMPPAddress of the sender (e.g. john@example.org). 
     */
    void logConversation(MUCRoom room, Message message, XMPPAddress sender);
}