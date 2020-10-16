/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.user;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.property.DefaultUserPropertyProvider;
import org.jivesoftware.openfire.user.property.UserPropertyProvider;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import gnu.inet.encoding.Stringprep;
import gnu.inet.encoding.StringprepException;

import javax.annotation.Nonnull;

/**
 * Manages users, including loading, creating and deleting.
 *
 * @author Matt Tucker
 * @see User
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class UserManager {

    private static final Interner<String> userBaseMutex = Interners.newWeakInterner();

    public static final SystemProperty<Class> USER_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.user.className")
        .setBaseClass(UserProvider.class)
        .setDefaultValue(DefaultUserProvider.class)
        .addListener(UserManager::initProvider)
        .setDynamic(true)
        .build();
    private static final SystemProperty<Duration> REMOTE_DISCO_INFO_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("usermanager.remote-disco-info-timeout-seconds")
        .setDefaultValue(Duration.ofMinutes(1))
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDynamic(true)
        .build();
    public static final SystemProperty<Class> USER_PROPERTY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.userproperty.className")
        .setBaseClass(UserPropertyProvider.class)
        .setDefaultValue(DefaultUserPropertyProvider.class)
        .addListener(UserManager::initPropertyProvider)
        .setDynamic(true)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(UserManager.class);

    private static final String MUTEX_SUFFIX = " usr";
    
    // Wrap this guy up so we can mock out the UserManager class.
    private static final class UserManagerContainer {
        private static final UserManager instance = new UserManager();
    }

    /**
     * Returns the currently-installed UserProvider. <b>Warning:</b> in virtually all
     * cases the user provider should not be used directly. Instead, the appropriate
     * methods in UserManager should be called. Direct access to the user provider is
     * only provided for special-case logic.
     *
     * @return the current UserProvider.
     */
    @SuppressWarnings("AccessStaticViaInstance")
    public static UserProvider getUserProvider() {
        return UserManagerContainer.instance.provider;
    }

    /**
     * Returns the currently-installed UserPropertyProvider.
     *
     * <b>Warning:</b> in virtually all cases the user property provider should not be used directly. Instead, use the
     * Map returned by {@link User#getProperties()} to create, read, update or delete user properties. Failure to do so
     * is likely to result in inconsistent data behavior and race conditions. Direct access to the user property
     * provider is only provided for special-case logic.
     *
     * @return the current UserPropertyProvider.
     * @see User#getProperties
     */
    @SuppressWarnings("AccessStaticViaInstance")
    public static UserPropertyProvider getUserPropertyProvider() {
        return UserManagerContainer.instance.propertyProvider;
    }

    /**
     * Returns a singleton UserManager instance.
     *
     * @return a UserManager instance.
     */
    public static UserManager getInstance() {
        return UserManagerContainer.instance;
    }

    private final XMPPServer xmppServer;
    /** Cache of local users. */
    private final Cache<String, User> userCache;
    /** Cache if a local or remote user exists. */
    private final Cache<String, Boolean> remoteUsersCache;
    private static UserProvider provider;
    private static UserPropertyProvider propertyProvider;

    private UserManager() {
        this(XMPPServer.getInstance());
    }

    /* Exposed for test use only */
    UserManager(final XMPPServer xmppServer) {
        this.xmppServer = xmppServer;
        // Initialize caches.
        userCache = CacheFactory.createCache("User");
        remoteUsersCache = CacheFactory.createCache("Remote Users Existence");

        // Load a user & property provider.
        initProvider(USER_PROVIDER.getValue());
        initPropertyProvider(USER_PROPERTY_PROVIDER.getValue());

        final UserEventListener userListener = new UserEventListener() {
            @Override
            public void userCreated(final User user, final Map<String, Object> params) {
                // Since the user could be created by the provider, add it possible again
                userCache.put(user.getUsername(), user);
            }

            @Override
            public void userDeleting(final User user, final Map<String, Object> params) {
                // Since the user could be deleted by the provider, remove it possible again
                userCache.remove(user.getUsername());
            }

            @Override
            public void userModified(final User user, final Map<String, Object> params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the user
                userCache.put(user.getUsername(), user);
            }
        };
        UserEventDispatcher.addListener(userListener);
    }

    /**
     * Creates a new User. Required values are username and password. The email address
     * and name can optionally be {@code null}, unless the UserProvider deems that
     * either of them are required.
     *
     * @param username the new and unique username for the account.
     * @param password the password for the account (plain text).
     * @param name the name of the user, which can be {@code null} unless the UserProvider
     *      deems that it's required.
     * @param email the email address to associate with the new account, which can
     *      be {@code null}, unless the UserProvider deems that it's required.
     * @return a new User.
     * @throws UserAlreadyExistsException if the username already exists in the system.
     * @throws UnsupportedOperationException if the provider does not support the
     *      operation.
     */
    public User createUser(String username, final String password, final String name, final String email)
            throws UserAlreadyExistsException
    {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Null or empty username.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Null or empty password.");
        }
        // Make sure that the username is valid.
        try {
            username = Stringprep.nodeprep(username);
        }
        catch (final StringprepException se) {
            throw new IllegalArgumentException("Invalid username: " + username,  se);
        }
        if (provider.isNameRequired() && (name == null || name.matches("\\s*"))) {
            throw new IllegalArgumentException("Invalid or empty name specified with provider that requires name. User: "
                                                + username + " Name: " + name);
        }
        if (provider.isEmailRequired() && !StringUtils.isValidEmailAddress(email)) {
            throw new IllegalArgumentException("Invalid or empty email address specified with provider that requires email address. User: "
                                                + username + " Email: " + email);
        }
        final User user = provider.createUser(username, password, name, email);
        userCache.put(username, user);

        // Fire event.
        final Map<String,Object> params = Collections.emptyMap();
        UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_created, params);

        return user;
    }

    /**
     * Deletes a user (optional operation).
     *
     * @param user the user to delete.
     */
    public void deleteUser(final User user) {
        if (provider.isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        final String username = user.getUsername();
        // Make sure that the username is valid.
        try {
            /*username =*/ Stringprep.nodeprep(username);
        }
        catch (final StringprepException se) {
            throw new IllegalArgumentException("Invalid username: " + username,  se);
        }

        // Fire event.
        final Map<String,Object> params = Collections.emptyMap();
        UserEventDispatcher.dispatchEvent(user, UserEventDispatcher.EventType.user_deleting, params);

        provider.deleteUser(user.getUsername());
        // Remove the user from cache.
        userCache.remove(user.getUsername());
    }

    /**
     * Returns the User specified by username.
     *
     * @param username the username of the user.
     * @return the User that matches {@code username}.
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
            synchronized (userBaseMutex.intern(username)) {
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
     * Returns the User specified by jid node.
     *
     * @param user the username of the user.
     * @return the User that matches {@code username}.
     * @throws UserNotFoundException if the user does not exist.
     */
    public User getUser(final JID user) throws UserNotFoundException {
        if (user == null) {
            throw new UserNotFoundException("user cannot be null");
        }

        if (!xmppServer.isLocal(user)) {
            throw new UserNotFoundException("Cannot get remote user");
        }

        return getUser(user.getNode());
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
     * Returns an unmodifiable Collection of all users starting at {@code startIndex}
     * with the given number of results. This is useful to support pagination in a GUI
     * where you may only want to display a certain number of results per page. It is
     * possible that the number of results returned will be less than that specified
     * by {@code numResults} if {@code numResults} is greater than the number of
     * records left to display.
     *
     * @param startIndex the beginning index to start the results at.
     * @param numResults the total number of results to return.
     * @return a Collection of users in the specified range.
     */
    public Collection<User> getUsers(final int startIndex, final int numResults) {
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
    public Collection<User> findUsers(final Set<String> fields, final String query)
            throws UnsupportedOperationException
    {
        return provider.findUsers(fields, query);
    }

    /**
     * Searches for users based on a set of fields and a query string. The fields must
     * be taken from the values returned by {@link #getSearchFields()}. The query can
     * include wildcards. For example, a search on the field "Name" with a query of "Ma*"
     * might return user's with the name "Matt", "Martha" and "Madeline".<p>
     *
     * The startIndex and numResults parameters are used to page through search
     * results. For example, if the startIndex is 0 and numResults is 10, the first
     * 10 search results will be returned. Note that numResults is a request for the
     * number of results to return and that the actual number of results returned
     * may be fewer.<p>
     *
     * This method should throw an UnsupportedOperationException if this
     * operation is not supported by the backend user store.
     *
     * @param fields the fields to search on.
     * @param query the query string.
     * @param startIndex the starting index in the search result to return.
     * @param numResults the number of users to return in the search result.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public Collection<User> findUsers(final Set<String> fields, final String query, final int startIndex,
                                      final int numResults)
            throws UnsupportedOperationException
    {
        return provider.findUsers(fields, query, startIndex, numResults);
    }

    /**
     * Returns true if the specified local username belongs to a registered local user.
     *
     * @param username to username of the user to check it it's a registered user.
     * @return true if the specified JID belongs to a local registered user.
     * @deprecated Replaced by {@link #isRegisteredUser(JID, boolean)}, which prevents assumptions about the domain of the user. (OF-2106)
     */
    @Deprecated
    public boolean isRegisteredUser(final String username) {
        if (username == null || "".equals(username)) {
            return false;
        }
        try {
            getUser(username);
            return true;
        }
        catch (final UserNotFoundException e) {
            return false;
        }
    }

    /**
     * Returns true if the specified JID belongs to a local or remote registered user. For
     * remote users (i.e. domain does not match local domain) a disco#info request is going
     * to be sent to the bare JID of the user.
     *
     * <p>WARNING: If the supplied JID could be a remote user and the disco#info result packet comes back on the same
     * thread as the one the calls this method then it will not be processed, and this method will block for 60 seconds
     * by default. To change the timeout, update the system property <code>usermanager.remote-disco-info-timeout-seconds</code>
     *
     * @param user to JID of the user to check it it's a registered user.
     * @return true if the specified JID belongs to a local or remote registered user.
     * @deprecated Replaced by {@link #isRegisteredUser(JID, boolean)}, of which the signature is clear on performing potentially costly remote lookups. (OF-2106)
     */
    @Deprecated
    public boolean isRegisteredUser(final JID user) {
        return isRegisteredUser(user, true);
    }

    /**
     * Returns true if the specified JID belongs to a local or remote registered user. If allowed by the 'checkRemoteDomains',
     * argument, for remote users (i.e. domain does not match local domain) a disco#info request is going to be sent to
     * the bare JID of the user. If 'checkRemoteDomains' is false, this method will return 'false' for all JIDs of which the
     * domain-part does not match the local domain.
     *
     * <p>WARNING: If the supplied JID could be a remote user and the disco#info result packet comes back on the same
     * thread as the one the calls this method then it will not be processed, and this method will block for 60 seconds
     * by default. To change the timeout, update the system property <code>usermanager.remote-disco-info-timeout-seconds</code>
     *
     * @param user to JID of the user to check it it's a registered user.
     * @param checkRemoteDomains false the lookup is allowed to include calls to remote XMPP domains.
     * @return true if the specified JID belongs to a registered user.
     */
    public boolean isRegisteredUser(@Nonnull final JID user, final boolean checkRemoteDomains) {
        if (xmppServer.isLocal(user)) {
            try {
                getUser(user.getNode());
                return true;
            }
            catch (final UserNotFoundException e) {
                return false;
            }
        }
        else if (!checkRemoteDomains) {
            return false;
        } else {
            // Look up in the cache using the full JID
            Boolean isRegistered = remoteUsersCache.get(user.toString());
            if (isRegistered == null) {
                // Check if the bare JID of the user is cached
                isRegistered = remoteUsersCache.get(user.toBareJID());
                if (isRegistered == null) {
                    // No information is cached so check user identity and cache it
                    // A disco#info is going to be sent to the bare JID of the user. This packet
                    // is going to be handled by the remote server.
                    final IQ iq = new IQ(IQ.Type.get);
                    iq.setFrom(xmppServer.getServerInfo().getXMPPDomain());
                    iq.setTo(user.toBareJID());
                    iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
                    final Semaphore completionSemaphore = new Semaphore(0);
                    // Send the disco#info request to the remote server.
                    final IQRouter iqRouter = xmppServer.getIQRouter();
                    final long timeoutInMillis = REMOTE_DISCO_INFO_TIMEOUT.getValue().toMillis();
                    iqRouter.addIQResultListener(iq.getID(), new IQResultListener() {
                        @Override
                        public void receivedAnswer(final IQ packet) {
                            final JID from = packet.getFrom();
                            // Assume that the user is not a registered user
                            Boolean isRegistered = Boolean.FALSE;
                            // Analyze the disco result packet
                            if (IQ.Type.result == packet.getType()) {
                                final Element child = packet.getChildElement();
                                if (child != null) {
                                    for (final Iterator it = child.elementIterator("identity"); it.hasNext();) {
                                        final Element identity = (Element) it.next();
                                        final String accountType = identity.attributeValue("type");
                                        if ("registered".equals(accountType) || "admin".equals(accountType)) {
                                            isRegistered = Boolean.TRUE;
                                            break;
                                        }
                                    }
                                }
                            }
                            // Update cache of remote registered users
                            remoteUsersCache.put(from.toBareJID(), isRegistered);
                            completionSemaphore.release();
                        }

                        @Override
                        public void answerTimeout(final String packetId) {
                            Log.warn("The result from the disco#info request was never received. request: {}", iq);
                            completionSemaphore.release();
                        }
                    }, timeoutInMillis);
                    // Send the request
                    iqRouter.route(iq);
                    // Wait for the response
                    try {
                        completionSemaphore.tryAcquire(timeoutInMillis, TimeUnit.MILLISECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.warn("Interrupted whilst waiting for response from remote server", e);
                    }
                    isRegistered = remoteUsersCache.computeIfAbsent(user.toBareJID(), ignored -> Boolean.FALSE);
                }
            }
            return isRegistered;
        }
    }

    private static void initProvider(final Class clazz) {
        if (provider == null || !clazz.equals(provider.getClass())) {
            try {
                provider = (UserProvider) clazz.newInstance();
            }
            catch (final Exception e) {
                Log.error("Error loading user provider: " + clazz.getName(), e);
                provider = new DefaultUserProvider();
            }
        }
    }

    private static void initPropertyProvider(final Class clazz) {
        // Check if we need to reset the provider class
        if (propertyProvider == null || !clazz.equals(propertyProvider.getClass())) {
            try {
                propertyProvider = (UserPropertyProvider) clazz.newInstance();
            }
            catch (final Exception e) {
                Log.error("Error loading user property provider: " + clazz.getName(), e);
                propertyProvider = new DefaultUserPropertyProvider();
            }
        }
    }

    /** Exposed for test use only */
    public static void setProvider(UserProvider provider) {
        USER_PROVIDER.setValue(provider.getClass());
        UserManager.provider = provider;
    }

    /** Exposed for test use only */
    public void clearCaches()
    {
        userCache.clear();
        remoteUsersCache.clear();
    }
}
