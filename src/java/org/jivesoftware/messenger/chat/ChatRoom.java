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

import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Iterator;

/**
 * A chat room on the chat server manages its users, and
 * enforces it's own security rules.
 *
 * @author Iain Shigeoka
 */
public interface ChatRoom extends ChatDeliverer {

    /**
     * Get the name of this room.
     *
     * @return The name for this room
     */
    String getName();

    /**
     * Obtain a unique numerical id for this room.
     * Useful for storing rooms in databases
     * (TBD: persistent rooms do not exist yet)
     *
     * @return The unique id for this room or -1 if no id has been set.
     */
    long getID();

    /**
     * <p>Obtain the room specific message history settings.</p>
     *
     * @return The message history strategy defaults for the room
     */
    HistoryStrategy getHistoryStrategy();

    /**
     * <p>Obtain the role of the chat server (mainly for addressing
     * messages and presence).</p>
     *
     * @return The role for the chat room itself
     * @throws UnauthorizedException If you don't have permission
     */
    ChatRole getRole() throws UnauthorizedException;

    /**
     * Obtain the role of a given user by nickname.
     *
     * @param nickname The nickname of the user you'd like to obtain
     * @return The user's role in the room
     * @throws UnauthorizedException If you don't have permission to
     *                               access the user
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    ChatRole getMember(String nickname)
            throws UnauthorizedException, UserNotFoundException;

    /**
     * Obtain the roles of all users in the chatroom.
     *
     * @return Iterator over all users in the chatroom
     * @throws UnauthorizedException If you don't have permission to access
     *                               the user
     */
    Iterator getMembers() throws UnauthorizedException;

    /**
     * Determine if a given nickname is taken.
     *
     * @param nickname The nickname of the user you'd like to obtain
     * @return True if a nickname is taken
     * @throws UnauthorizedException If you don't have permission to access
     *                               the user
     */
    boolean hasMember(String nickname) throws UnauthorizedException;

    /**
     * Joins the room using the given nickname.
     *
     * @param nickname The nickname the user wants to use in the chatroom
     * @param user     The user joining
     * @return The role created for the user
     * @throws UnauthorizedException      If the user doesn't have permision
     *                                    to join the room
     * @throws UserAlreadyExistsException If the nickname is already taken
     */
    ChatRole joinRoom(String nickname, ChatUser user)
            throws UnauthorizedException, UserAlreadyExistsException;

    /**
     * Remove a member from the chat room.
     *
     * @param nickname The user to remove
     * @throws UnauthorizedException If the user doesn't have permission
     *                               to leave the room
     * @throws UserNotFoundException If the nickname is not found
     */
    void leaveRoom(String nickname)
            throws UnauthorizedException, UserNotFoundException;

    /**
     * Create a new presence in this room for the given role.
     *
     * @return The new presence
     * @throws UnauthorizedException If the user doesn't have permission to
     *                               leave the room
     */
    Presence createPresence(int presenceStatus) throws UnauthorizedException;

    /**
     * Broadcast a given message to all members of this chat room.
     * The sender is always set to be the chatroom.
     *
     * @param msg The message to broadcast
     */
    void serverBroadcast(String msg) throws UnauthorizedException;

    /**
     * Returns the total length of the chat session.
     *
     * @return length of chat session in milliseconds.
     */
    public long getChatLength();
}
