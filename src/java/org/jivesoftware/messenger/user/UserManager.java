/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.container.Module;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import java.util.Iterator;

/**
 * Centralized management of users in the Jive system including creating, retrieving, and deleting
 * User objects.<p>
 * <p/>
 * In some cases, you may wish to plug in your own user system implementation. In that case, you
 * should set the Jive property <tt>UserManager.className</tt> with the name of your UserManager
 * class. Your class must have a public, no-argument constructor. The class must also create and
 * return User object implementations as necessary.
 *
 * @author Iain Shigeoka
 * @see User
 */
public interface UserManager extends Module {

    /**
     * Factory method for creating a new User with all required values: a password, and a unique username.
     *
     * @param username the new and unique username for the account.
     * @param password the password for the account as plain text.
     * @param email    The email address to associate with the new account
     * @return a new User.
     * @throws UserAlreadyExistsException    if the username already exists in the system.
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public User createUser(String username, String password, String email)
            throws UserAlreadyExistsException, UnauthorizedException, UnsupportedOperationException;

    /**
     * Deletes a user (optional operation).
     *
     * @param user the user to delete.
     * @throws UnauthorizedException         If the caller doesn't have permission to take the given action
     * @throws UnsupportedOperationException If the provider does not support the operation (this is an optional operation)
     */
    public void deleteUser(User user) throws UnauthorizedException, UnsupportedOperationException;

    /**
     * Returns a User specified by their ID.
     *
     * @param userID the id of the User to lookup.
     * @return the User specified by <tt>userID</tt>.
     * @throws UserNotFoundException if the user does not exist.
     */
    public User getUser(long userID) throws UserNotFoundException;

    /**
     * Returns the User specified by username.
     *
     * @param username the username of the user.
     * @return the User that matches <tt>username</tt>.
     * @throws UserNotFoundException if the user does not exist.
     */
    public User getUser(String username) throws UserNotFoundException;

    /**
     * Returns the userID specified by the username. This method is only useful in specialized
     * cases, as its generally easier to call <tt>getUser(username).getID()</tt> instead of this
     * method.
     *
     * @param username the username of the user.
     * @return the userID that matches username.
     * @throws UserNotFoundException if the user does not exist.
     */
    public long getUserID(String username) throws UserNotFoundException;

    /**
     * <p>Deletes a user.</p>
     *
     * @param userID the ID of the user to delete.
     * @throws UnauthorizedException
     */
    public void deleteUser(long userID) throws UnauthorizedException, UserNotFoundException;

    /**
     * Returns the number of users in the system.
     *
     * @return the total number of users.
     */
    public int getUserCount();

    /**
     * Returns an iterator for all users in the system.
     *
     * @return an Iterator for all users.
     */
    public Iterator users() throws UnauthorizedException;

    /**
     * Returns an iterator for all users starting at <tt>startIndex</tt> with the given number of
     * results. This is useful to support pagination in a GUI where you may only want to display a
     * certain number of results per page. It is possible that the number of results returned will
     * be less than that specified by numResults if numResults is greater than the number of records
     * left in the system to display.
     *
     * @param startIndex the beginning index to start the results at.
     * @param numResults the total number of results to return.
     * @return an Iterator for all users in the specified range.
     */
    public Iterator users(int startIndex, int numResults) throws UnauthorizedException;
}