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

package org.jivesoftware.messenger.user;

import java.util.Date;
import java.util.Collection;

/**
 * Provider interface for the user system.
 *
 * @author Matt Tucker
 */
public interface UserProvider {

    /**
     * Loads the specified user by username.
     *
     * @param username the username
     * @return the User.
     * @throws UserNotFoundException if the User could not be loaded.
     */
    public User loadUser(String username) throws UserNotFoundException;

    /**
     * Creates a new user. This method should throw an
     * UnsupportedOperationException if this operation is not
     * supporte by the backend user store.
     *
     * @param username the username.
     * @param password the plain-text password.
     * @param name the user's name, which can be <tt>null</tt>.
     * @param email the user's email address, which can be <tt>null</tt>.
     * @return a new User.
     * @throws UserAlreadyExistsException if the username is already in use.
     */
    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException;

    /**
     * Delets a user. This method should throw an
     * UnsupportedOperationException if this operation is not
     * supported by the backend user store.
     *
     * @param username the username to delete.
     */
    public void deleteUser(String username);

    /**
     * Returns the number of users in the system.
     *
     * @return the total number of users.
     */
    public int getUserCount();

    /**
     * Returns an unmodifiable Collections of all users in the system. The
     * {@link UserCollection} class can be used to assist in the implementation
     * of this method. It takes a String [] of usernames and presents it as a
     * Collection of User objects (obtained with calls to
     * {@link UserManager#getUser(String)}.
     *
     * @return an unmodifiable Collection of all users.
     */
    public Collection<User> getUsers();

    /**
     * Returns an unmodifiable Collections of users in the system within the
     * specified range. The {@link UserCollection} class can be used to assist
     * in the implementation of this method. It takes a String [] of usernames
     * and presents it as a  Collection of User objects (obtained with calls to
     * {@link UserManager#getUser(String)}.<p>
     *
     * It is possible that the number of results returned will be less than that
     * specified by <tt>numResults</tt> if <tt>numResults</tt> is greater than the
     * number of records left to display.
     *
     * @param startIndex the beginning index to start the results at.
     * @param numResults the total number of results to return.
     * @return an unmodifiable Collection of users within the specified range.
     */
    public Collection<User> getUsers(int startIndex, int numResults);

    /**
     * Returns the user's password. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @return the user's password.
     * @throws UserNotFoundException if the given user could not be loaded.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException;

    /**
     * Sets the users's password. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @param password the new plaintext password for the user.
     * @throws UserNotFoundException if the given user could not be loaded.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public void setPassword(String username, String password)
            throws UserNotFoundException, UnsupportedOperationException;

    /**
     * Sets the user's name. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username.
     * @param name the name.
     * @throws UserNotFoundException if the user could not be found.
     */
    public void setName(String username, String name) throws UserNotFoundException;

    /**
     * Sets the user's email address. This method should throw an
     * UnsupportedOperationException if this operation is not supported
     * by the backend user store.
     *
     * @param username the username.
     * @param email the email address.
     * @throws UserNotFoundException if the user could not be found.
     */
    public void setEmail(String username, String email) throws UserNotFoundException;

    /**
     * Sets the date the user was created. This method should throw an
     * UnsupportedOperationException if this operation is not supported
     * by the backend user store.
     *
     * @param username the username.
     * @param creationDate the date the user was created.
     * @throws UserNotFoundException if the user could not be found.
     */
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException;

    /**
     * Sets the date the user was last modified. This method should throw an
     * UnsupportedOperationException if this operation is not supported
     * by the backend user store.
     *
     * @param username the username.
     * @param modificationDate the date the user was last modified.
     * @throws UserNotFoundException if the user could not be found.
     */
    public void setModificationDate(String username, Date modificationDate)
            throws UserNotFoundException;

    /**
     * Returns the set of fields that can be used for searching for users. Each field
     * returned must support wild-card and keyword searching. For example, an
     * implementation might send back the set {"Username", "Name", "Email"}. Any of
     * those three fields can then be used in a search with the
     * {@link #findUsers(String,String)} method.<p>
     *
     * This method should throw an UnsupportedOperationException if this
     * operation is not supported by the backend user store.
     *
     * @return the valid search fields.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public Collection<String> getSearchFields() throws UnsupportedOperationException;

    /**
     * Searches for users based on a field an query string. The field must be one
     * of the values returned by {@link #getSearchFields()}. The query can include
     * wildcards. For example, a search on the field "Name" with a query of "Ma*"
     * might return user's with the name "Matt", "Martha" and "Madeline".<p>
     *
     * This method should throw an UnsupportedOperationException if this
     * operation is not supported by the backend user store. 
     *
     * @param field the field to search on.
     * @param query the query string.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public Collection<User> findUsers(String field, String query)
            throws UnsupportedOperationException;

}