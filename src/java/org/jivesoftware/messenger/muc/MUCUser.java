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

import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.ChannelHandler;
import org.jivesoftware.messenger.auth.UnauthorizedException;

import java.util.Iterator;

/**
 * The chat user is a separate user abstraction for interacting with
 * the chat server. Centralizing chat users to the Jabber entity that
 * sends and receives the chat messages allows us to create quality of
 * service, authorization, and resource decisions on a real-user basis.
 * <p/>
 * Most chat users in a typical s2s scenario will not be local users.
 * </p><p>
 * MUCUsers play one or more roles in one or more chat rooms on the
 * server.
 *
 * @author Gaston Dombiak
 */
public interface MUCUser extends ChannelHandler {

    /**
     * Obtain a user ID (useful for database indexing).
     *
     * @return The user's id number if any (-1 indicates the implementation doesn't support ids)
     * @throws UnauthorizedException If the caller doesn't have appropriate permissions
     */
    long getID() throws UnauthorizedException;

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
     public XMPPAddress getAddress();

    /**
     * Obtain the role of the user in a particular room.
     *
     * @param roomName The name of the room we're interested in
     * @return The role the user plays in that room
     * @throws UnauthorizedException If the caller doesn't have appropriate permissions
     * @throws NotFoundException     if the user does not have a role in the given room
     */
    MUCRole getRole(String roomName)
            throws UnauthorizedException, NotFoundException;

    /**
     * Get all roles for this user.
     *
     * @return Iterator over all roles for this user
     * @throws UnauthorizedException If the caller doesn't have permission
     */
    Iterator<MUCRole> getRoles() throws UnauthorizedException;

    /**
     * Removes the role of the use in a particular room.<p>
     * <p/>
     * Note: PREREQUISITE: A lock on this object has already been obtained.
     *
     * @param roomName The name of the room we're being removed
     */
    void removeRole(String roomName);

    /**
     * Get time (in milliseconds from System currentTimeMillis()) since last packet.
     *
     * @return The time when the last packet was sent from this user
     */
    long getLastPacketTime();
}