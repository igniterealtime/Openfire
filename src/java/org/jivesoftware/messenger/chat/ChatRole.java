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
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Defines the permissions and actions that a ChatUser may use in
 * a particular room. Each ChatRole defines the relationship between
 * a ChatRoom and a ChatUser.
 * <p/>
 * ChatUsers can play different roles in different chatrooms.
 *
 * @author Iain Shigeoka
 */
public interface ChatRole extends ChatDeliverer {

    /**
     * A silent observer of the room (can't speak in the room)
     */
    int OBSERVER = 0;
    /**
     * A normal occupant of the room
     */
    int OCCUPANT = 1;
    /**
     * Administrator of the room
     */
    int OWNER = 10;
    /**
     * Runs moderated discussions
     */
    int MODERATOR = 20;
    /**
     * The guest speaker in a moderated discussion
     */
    int GUEST_SPEAKER = 30;

    /**
     * Obtain the current presence status of a user in a chatroom.
     *
     * @return The presence of the user in the room
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    Presence getPresence() throws UnauthorizedException;

    /**
     * Set the current presence status of a user in a chatroom.
     *
     * @param presence The presence of the user in the room
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    void setPresence(Presence presence) throws UnauthorizedException;

    /**
     * Call this method to promote or demote a user's role in a chatroom.
     * It is common for the chatroom or other chat room members to change
     * the role of users (a moderator promoting another user to moderator
     * status for example).
     * <p/>
     * Owning ChatUsers should have their membership roles updated.
     *
     * @param newRole The new role that the user will play
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    void setRole(int newRole) throws UnauthorizedException;

    /**
     * Obtain the role state of the user.
     *
     * @return The role status of this user
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    int getRole() throws UnauthorizedException;

    /**
     * Obtain the nickname for the user in the chatroom.
     *
     * @return The user's nickname in the room or null if invisible
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    String getNickname() throws UnauthorizedException;

    /**
     * An event callback for kicks (being removed from a room). This
     * provides the user an opportunity to react to the kick (although the
     * chat user has already been kicked when this method is called). Remove
     * users from a chatroom by calling ChatRoom.leaveRoom().
     *
     * @throws UnauthorizedException Thrown if the caller doesn't have
     *                               permission to know this user's presence
     */
    void kick() throws UnauthorizedException;

    /**
     * Obtain the chat user that plays this role.
     *
     * @return The chatuser playing this role
     */
    ChatUser getChatUser();

    /**
     * Obtain the chat room that hosts this user's role.
     *
     * @return The chatroom hosting this role.
     */
    ChatRoom getChatRoom();

    /**
     * Obtain the XMPPAddress representing this role in a room:
     * room@server/nickname
     *
     * @return The Jabber ID that represents this role in the room
     */
    XMPPAddress getRoleAddress();
}
