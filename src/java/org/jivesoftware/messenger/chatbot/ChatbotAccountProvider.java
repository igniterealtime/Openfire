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

package org.jivesoftware.messenger.chatbot;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;

/**
 * <p>Defines the provider methods required for creating and deleting chatbot username reservations.</p>
 * <p/>
 * <p>Creating and deleting chatbot username reservations can be complex and highly dependent on a provider's
 * unique mix of custom and Jive providers. Implementing this interfaces provides the
 * opportunity to customize the behavior of Messenger when creating and deleting
 * chatbot username reservations.</p>
 *
 * @author Iain Shigeoka
 */
public interface ChatbotAccountProvider {
    
    /**
     * <p>Called to create a new chatbot username reservations (optional operation).</p>
     * <p/>
     * <p>After a call to this method is made, all other provider methods should be valid.
     * For example, if your user info provider only uses JDBC updates, then this method
     * must insert the initial user info so the updates will occur normally. See the
     * documentation for the providers you are using to determine what needs to be done
     * to satisfy their setup needs. In particular, if you are using Jive default providers,
     * you should examine the jive default UserAccountProvider implementation to make sure
     * you have setup the jive tables you will be using properly.</p>
     *
     * @param username The username for the user to be created
     * @return The user ID for the new user
     * @throws org.jivesoftware.messenger.auth.UnauthorizedException
     *                                       If users can't be created because of caller permissions (no new account created)
     * @throws org.jivesoftware.messenger.user.UserAlreadyExistsException
     *                                       If the user already exists (no new account created)
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    long createChatbot(String username)
            throws UnauthorizedException, UserAlreadyExistsException, UnsupportedOperationException;

    /**
     * <p>Called to delete an existing chatbot username reservations (optional operation).</p>
     * <p/>
     * <p>Use this method to remove a user from the system and clean up any resources
     * devoted to that user. If a user account for the id doesn't exist, the method should return
     * without any exceptions.</p>
     *
     * @param id The user ID for the user to delete
     * @throws UnauthorizedException         If users can't be deleted with this provider (no accounts affected)
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void deleteChatbot(long id) throws UnauthorizedException, UnsupportedOperationException;
}