/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * <p>Defines the provider methods required for creating and deleting user accounts.</p>
 * <p/>
 * <p>Creating and deleting user accounts can be complex and highly dependent on a provider's
 * unique mix of custom and Jive providers. Implementing this interfaces provides the
 * opportunity to customize the behavior of Messenger when creating and deleting user
 * accounts.</p>
 *
 * @author Iain Shigeoka
 */
public interface UserAccountProvider {
    /**
     * <p>Called to create a new user account (optional operation).</p>
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
     * @param password The plain-text password for the user
     * @param email    The email address of the new user
     * @return The user ID for the new user
     * @throws UnauthorizedException         If users can't be created because of caller permissions (no new account created)
     * @throws UserAlreadyExistsException    If the user already exists (no new account created)
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    long createUser(String username, String password, String email)
            throws UnauthorizedException, UserAlreadyExistsException, UnsupportedOperationException;

    /**
     * <p>Called to delete an existing user account (optional operation).</p>
     * <p/>
     * <p>Use this method to remove a user from the system and clean up any resources
     * devoted to that user. If a user account for the id doesn't exist, the method should return
     * without any exceptions.</p>
     *
     * @param id The user ID for the user to delete
     * @throws UnauthorizedException         If users can't be deleted with this provider (no accounts affected)
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    void deleteUser(long id) throws UnauthorizedException, UnsupportedOperationException;
}
