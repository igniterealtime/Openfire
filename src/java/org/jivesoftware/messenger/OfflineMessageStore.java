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

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Iterator;

/**
 * Represents the user's offline message storage. A message store holds messages that were sent
 * to the user while they were unavailable. The user can retrieve their messages by setting
 * their presence to "available". The messages will then be delivered normally.
 * Offline message storage is optional in which case, a null implementation is returned that
 * always throws UnauthorizedException adding messages to the store.
 * <p/>
 * A future version of the message store will support POP like message storage so that
 * users may download offline messages on demand, inspect headers only, and continue to store
 * offline messages even when available.
 *
 * @author Iain Shigeoka
 */
public interface OfflineMessageStore {
    /**
     * Add a message to the message store. Messages will be stored and made available for
     * later delivery.
     *
     * @param message The message to store (messages are standard XMPP message XML)
     * @throws UnauthorizedException If the user is not allowed to store messages, or they have exceeded their quota
     */
    void addMessage(Message message) throws UnauthorizedException;

    /**
     * <p>Obtain all messages in the store for a user.</p>
     * <p>Remove messages using the iterator.remove() method. Otherwise
     * messages stay in the message store and will be available to other
     * users of getMessages().</p>
     *
     * @param userName The user name of the user who's messages you'd like to receive
     * @return An iterator of packets containing all offline messages
     * @throws UnauthorizedException If the user is not allowed to retrieve messages
     */
    Iterator getMessages(String userName) throws UnauthorizedException, UserNotFoundException;

    /**
     * <p>Obtain all messages in the store for a user.</p>
     * <p>Remove messages using the iterator.remove() method. Otherwise
     * messages stay in the message store and will be available to other
     * users of getMessages().</p>
     *
     * @param userID The user ID of the user who's messages you'd like to receive
     * @return An iterator of packets containing all offline messages
     * @throws UnauthorizedException If the user is not allowed to retrieve messages
     */
    Iterator getMessages(long userID) throws UnauthorizedException;

    /**
     * <p>Obtain the approximate size of the XML messages stored for a particular user.</p>
     *
     * @param userID The user ID of the user who's messages you'd like to receive
     * @return The approximate size of messages stored in bytes
     */
    int getSize(long userID) throws UnauthorizedException;

    /**
     * Obtain the approximate size of the XML messages stored for a particular user.
     *
     * @param userName The user name of the user who's messages you'd like to receive
     * @return The approximate size of messages stored in bytes
     */
    int getSize(String userName) throws UnauthorizedException, UserNotFoundException;
}