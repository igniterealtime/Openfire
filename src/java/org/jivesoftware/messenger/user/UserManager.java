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

import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.stringprep.Stringprep;
import org.jivesoftware.stringprep.StringprepException;

import java.util.Collection;
import java.util.Set;

/**
 * Manages users, including loading, creating and deleting.
 *
 * @author Matt Tucker
 * @see User
 */
public class UserManager {

    private static Cache userCache;
    private static UserProvider provider;
    private static UserManager instance = new UserManager();

    static {
        // Initialize caches.
        CacheManager.initializeCache("userCache", 512 * 1024);
        CacheManager.initializeCache("username2roster", 512 * 1024);
        userCache = CacheManager.getCache("userCache");
        // Load a user provider.
        String className = JiveGlobals.getXMLProperty("provider.user.className",
                "org.jivesoftware.messenger.user.DefaultUserProvider");
        try {
            Class c = ClassUtils.forName(className);
            provider = (UserProvider)c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading user provider: " + className, e);
            provider = new DefaultUserProvider();
        }
    }

    /**
     * Returns the currently-installed UserProvider. Note, in most cases the user
     * provider should not be used directly. Instead, the appropriate methods
     * in UserManager should be called.
     *
     * @return the current UserProvider.
     */
    public static UserProvider getUserProvider() {
        return provider;
    }

    /**
     * Returns a singleton UserManager instance.
     *
     * @return a UserManager instance.
     */
    public static UserManager getInstance() {
        return instance;
    }

    private UserManager() {

    }

    /**
     * Creates a new User. Required values are username and password. The email address
     * can optionally be <tt>null</tt>.
     *
     * @param username the new and unique username for the account.
     * @param password the password for the account (plain text).
     * @param email the email address to associate with the new account, which can
     *      be <tt>null</tt>.
     * @return a new User.
     * @throws UserAlreadyExistsException if the username already exists in the system.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     */
    public User createUser(String username, String password, String name, String email)
            throws UserAlreadyExistsException
    {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }
        // Make sure that the username is valid.
        try {
            username = Stringprep.nodeprep(username);
        }
        catch (StringprepException se) {
            throw new IllegalArgumentException("Invalid username: " + username,  se);
        }
        User user = provider.createUser(username, password, name, email);
        userCache.put(username, user);
        return user;
    }

    /**
     * Deletes a user (optional operation).
     *
     * @param user the user to delete.
     */
    public void deleteUser(User user) {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        String username = user.getUsername();
        // Make sure that the username is valid.
        try {
            username = Stringprep.nodeprep(username);
        }
        catch (StringprepException se) {
            throw new IllegalArgumentException("Invalid username: " + username,  se);
        }
        provider.deleteUser(user.getUsername());
        // Remove the user from cache.
        userCache.remove(user.getUsername());
    }

    /**
     * Returns the User specified by username.
     *
     * @param username the username of the user.
     * @return the User that matches <tt>username</tt>.
     * @throws UserNotFoundException if the user does not exist.
     */
    public User getUser(String username) throws UserNotFoundException {
        // Make sure that the username is valid.
        username = username.trim().toLowerCase();
        User user = (User) userCache.get(username);
        if (user == null) {
            synchronized(username.intern()) {
                user = (User) userCache.get(username);
                if (user == null) {
                    user = provider.loadUser(username);
                    userCache.put(username, user);
                }
            }
        }
        return user;
    }

    /**
     * Returns the total number of users in the system.
     *
     * @return the total number of users.
     */
    public int getUserCount() {
        return provider.getUserCount();
    }

    /**
     * Returns an unmodifiable Collection of all users in the system.
     *
     * @return an unmodifiable Collection of all users.
     */
    public Collection<User> getUsers() {
        return provider.getUsers();
    }

    /**
     * Returns an unmodifiable Collection of all users starting at <tt>startIndex</tt>
     * with the given number of results. This is useful to support pagination in a GUI
     * where you may only want to display a certain number of results per page. It is
     * possible that the number of results returned will be less than that specified
     * by <tt>numResults</tt> if <tt>numResults</tt> is greater than the number of
     * records left to display.
     *
     * @param startIndex the beginning index to start the results at.
     * @param numResults the total number of results to return.
     * @return a Collection of users in the specified range.
     */
    public Collection<User> getUsers(int startIndex, int numResults) {
        return provider.getUsers(startIndex, numResults);
    }

    /**
     * Returns the set of fields that can be used for searching for users. Each field
     * returned must support wild-card and keyword searching. For example, an
     * implementation might send back the set {"Username", "Name", "Email"}. Any of
     * those three fields can then be used in a search with the
     * {@link #findUsers(Set,String)} method.<p>
     *
     * This method should throw an UnsupportedOperationException if this
     * operation is not supported by the backend user store.
     *
     * @return the valid search fields.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public Set<String> getSearchFields() throws UnsupportedOperationException {
        return provider.getSearchFields();
    }

    /**
     * earches for users based on a set of fields and a query string. The fields must
     * be taken from the values returned by {@link #getSearchFields()}. The query can
     * include wildcards. For example, a search on the field "Name" with a query of "Ma*"
     * might return user's with the name "Matt", "Martha" and "Madeline".<p>
     *
     * This method should throw an UnsupportedOperationException if this
     * operation is not supported by the backend user store.
     *
     * @param fields the fields to search on.
     * @param query the query string.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public Collection<User> findUsers(Set<String> fields, String query)
            throws UnsupportedOperationException
    {
        return provider.findUsers(fields, query);
    }
}