/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQResultListener;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.stringprep.Stringprep;
import org.jivesoftware.stringprep.StringprepException;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheManager;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * Manages users, including loading, creating and deleting.
 *
 * @author Matt Tucker
 * @see User
 */
public class UserManager implements IQResultListener {

    /**
     * Cache of local users.
     */
    private static Cache<String, User> userCache;
    /**
     * Cache if a local or remote user exists.
     */
    private static Cache<String, Boolean> remoteUsersCache;
    private static UserProvider provider;
    private static UserManager instance = new UserManager();

    static {
        // Initialize caches.
        userCache = CacheManager.initializeCache("User", "userCache", 512 * 1024,
                JiveConstants.MINUTE*30);
        remoteUsersCache = CacheManager.initializeCache("Remote Users Existence", "remoteUsersCache",
                512 * 1024, JiveConstants.MINUTE*30);
        CacheManager.initializeCache("Roster", "username2roster", 512 * 1024);
        // Load a user provider.
        initProvider();

        // Detect when a new auth provider class is set
        PropertyEventListener propListener = new PropertyEventListener() {
            public void propertySet(String property, Map params) {
                //Ignore
            }

            public void propertyDeleted(String property, Map params) {
                //Ignore
            }

            public void xmlPropertySet(String property, Map params) {
                if ("provider.user.className".equals(property)) {
                    initProvider();
                }
            }

            public void xmlPropertyDeleted(String property, Map params) {
                //Ignore
            }
        };
        PropertyEventDispatcher.addListener(propListener);

        UserEventListener userListener = new UserEventListener() {
            public void userCreated(User user, Map<String, Object> params) {
                // Do nothing
            }

            public void userDeleting(User user, Map<String, Object> params) {
                // Do nothing
            }

            public void userModified(User user, Map<String, Object> params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the user
                userCache.put(user.getUsername(), user);
            }
        };
        UserEventDispatcher.addListener(userListener);
    }

    /**
     * Returns the currently-installed UserProvider. <b>Warning:</b> in virtually all
     * cases the user provider should not be used directly. Instead, the appropriate
     * methods in UserManager should be called. Direct access to the user provider is
     * only provided for special-case logic.
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
     * @param name the name of the user.
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

        // Fire event.
        Map<String,Object> params = Collections.emptyMap();
        UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_created, params);

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

        // Fire event.
        Map<String,Object> params = Collections.emptyMap();
        UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_deleting, params);

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
        if (username == null) {
            throw new UserNotFoundException("Username cannot be null");
        }
        // Make sure that the username is valid.
        username = username.trim().toLowerCase();
        User user = userCache.get(username);
        if (user == null) {
            synchronized (username.intern()) {
                user = userCache.get(username);
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
     * Returns an unmodifiable Collection of usernames of all users in the system.
     *
     * @return an unmodifiable Collection of all usernames in the system.
     */
    public Collection<String> getUsernames() {
        return provider.getUsernames();
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
     * Searches for users based on a set of fields and a query string. The fields must
     * be taken from the values returned by {@link #getSearchFields()}. The query can
     * include wildcards. For example, a search on the field "Name" with a query of "Ma*"
     * might return user's with the name "Matt", "Martha" and "Madeline".<p>
     *
     * This method throws an UnsupportedOperationException if this operation is
     * not supported by the user provider.
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

    /**
     * Returns true if the specified local username belongs to a registered local user.
     *
     * @param username to username of the user to check it it's a registered user.
     * @return true if the specified JID belongs to a local registered user.
     */
    public boolean isRegisteredUser(String username) {
        if (username == null || "".equals(username)) {
            return false;
        }
        try {
            getUser(username);
            return true;
        }
        catch (UserNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns true if the specified JID belongs to a local or remote registered user. For
     * remote users (i.e. domain does not match local domain) a disco#info request is going
     * to be sent to the bare JID of the user.
     *
     * @param user to JID of the user to check it it's a registered user.
     * @return true if the specified JID belongs to a local or remote registered user.
     */
    public boolean isRegisteredUser(JID user) {
        XMPPServer server = XMPPServer.getInstance();
        if (server.isLocal(user)) {
            try {
                getUser(user.getNode());
                return true;
            }
            catch (UserNotFoundException e) {
                return false;
            }
        }
        else {
            // Look up in the cache using the full JID
            Boolean isRegistered = remoteUsersCache.get(user.toString());
            if (isRegistered == null) {
                // Check if the bare JID of the user is cached
                isRegistered = remoteUsersCache.get(user.toBareJID());
                if (isRegistered == null) {
                    // No information is cached so check user identity and cache it
                    // A disco#info is going to be sent to the bare JID of the user. This packet
                    // is going to be handled by the remote server.
                    IQ iq = new IQ(IQ.Type.get);
                    iq.setFrom(server.getServerInfo().getName());
                    iq.setTo(user.toBareJID());
                    iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
                    // Send the disco#info request to the remote server. The reply will be
                    // processed by the IQResultListener (interface that this class implements)
                    server.getIQRouter().addIQResultListener(iq.getID(), this);
                    synchronized (user.toBareJID().intern()) {
                        server.getIQRouter().route(iq);
                        // Wait for the reply to be processed. Time out in 10 minutes.
                        try {
                            user.toBareJID().intern().wait(600000);
                        }
                        catch (InterruptedException e) {
                            // Do nothing
                        }
                    }
                    // Get the discovered result
                    isRegistered = remoteUsersCache.get(user.toBareJID());
                    if (isRegistered == null) {
                        // Disco failed for some reason (i.e. we timed out before getting a result)
                        // so assume that user is not anonymous and cache result
                        isRegistered = Boolean.FALSE;
                        remoteUsersCache.put(user.toString(), isRegistered);
                    }
                }
            }
            return isRegistered;
        }
    }

    public void receivedAnswer(IQ packet) {
        JID from = packet.getFrom();
        // Assume that the user is not a registered user
        Boolean isRegistered = Boolean.FALSE;
        // Analyze the disco result packet
        if (IQ.Type.result == packet.getType()) {
            Element child = packet.getChildElement();
            for (Iterator it=child.elementIterator("identity"); it.hasNext();) {
                Element identity = (Element) it.next();
                String accountType = identity.attributeValue("type");
                if ("registered".equals(accountType) || "admin".equals(accountType)) {
                    isRegistered = Boolean.TRUE;
                    break;
                }
            }
        }
        // Update cache of remote registered users
        remoteUsersCache.put(from.toBareJID(), isRegistered);

        // Wake up waiting thread
        synchronized (from.toBareJID().intern()) {
            from.toBareJID().intern().notifyAll();
        }
    }

    private static void initProvider() {
        String className = JiveGlobals.getXMLProperty("provider.user.className",
                "org.jivesoftware.openfire.user.DefaultUserProvider");
        // Check if we need to reset the provider class
        if (provider == null || !className.equals(provider.getClass().getName())) {
            try {
                Class c = ClassUtils.forName(className);
                provider = (UserProvider) c.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading user provider: " + className, e);
                provider = new DefaultUserProvider();
            }
        }
    }
}