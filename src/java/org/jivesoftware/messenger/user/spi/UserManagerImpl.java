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
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.NodePrep;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.*;
import java.util.Iterator;

/**
 * Database implementation of the UserManager interface.
 * It uses the DbUser class along with the jiveUser database
 * table to store and manipulate user information.<p>
 * <p/>
 * This UserManager implementation uses two caches to vastly improve speed:
 * <ul>
 * <li> id2userCache
 * <li> name2idCache
 * </ul><p>
 * <p/>
 * If making your own UserManager implementation, it's
 * highly recommended that you also use these caches.
 *
 * @author Iain Shigeoka
 */
public class UserManagerImpl extends BasicModule implements UserManager {

    private Cache id2userCache;
    private Cache name2idCache;

    private UserIDProvider userIDProvider = UserProviderFactory.getUserIDProvider();
    private UserAccountProvider userAccountProvider =
            UserProviderFactory.getUserAccountProvider();

    public UserManagerImpl() {
        super("User Manager");
    }

    private void initializeCaches() {
        CacheManager.initializeCache("username2userid", 128 * 1024); // 1/8 MB
        CacheManager.initializeCache("userid2user", 521 * 1024); // 1/2 MB
        CacheManager.initializeCache("userid2roster", 521 * 1024); // 1/2 MB

        id2userCache = CacheManager.getCache("userid2user");
        name2idCache = CacheManager.getCache("username2userid");
    }

    public User createUser(String username, String password, String email)
            throws UserAlreadyExistsException, UnauthorizedException {
        User newUser = null;
        // Strip extra or invisible characters from the username so that existing
        // usernames can't be forged.
        username = NodePrep.prep(username);
        username = StringUtils.replace(username, "&nbsp;", "");
        try {
            getUser(username);

            // The user already exists since no exception, so:
            throw new UserAlreadyExistsException();
        }
        catch (UserNotFoundException unfe) {
            // The user doesn't already exist so we can create a new user
            try {
                newUser = getUser(userAccountProvider.createUser(username, password, email));
            }
            catch (UserNotFoundException e) {
                throw new UnauthorizedException("Created an account but could not load it. username: "
                        + username + " pass: " + password + " email: " + email);
            }
        }
        return newUser;
    }

    public User getUser(long userID) throws UserNotFoundException {

        User user = (User)id2userCache.get(new Long(userID));
        if (user == null) {
            String username = userIDProvider.getUsername(userID);
            user = loadUser(userID, username);
        }

        return user;
    }

    public User getUser(String username) throws UserNotFoundException {
        if (username == null) {
            throw new UserNotFoundException("Username with null value is not valid.");
        }
        username = NodePrep.prep(username);
        User user = null;
        Long userIDLong = (Long)name2idCache.get(username);
        if (userIDLong == null) {
            long userID = userIDProvider.getUserID(username);
            user = loadUser(userID, username);
        }
        else {
            user = (User)id2userCache.get(userIDLong);
        }
        if (user == null) {
            throw new UserNotFoundException();
        }

        return user;
    }

    public long getUserID(String username) throws UserNotFoundException {
        username = NodePrep.prep(username);
        Long userIDLong = (Long)name2idCache.get(username);
        // If ID wan't found in cache, load it up and put it there.
        if (userIDLong == null) {
            long userID = userIDProvider.getUserID(username);
            User user = loadUser(userID, username);
            return user.getID();
        }

        return userIDLong.longValue();
    }

    private User loadUser(long userID, String username) {
        User user = new UserImpl(userID, username);
        Long userIDLong = new Long(userID);
        id2userCache.put(userIDLong, user);
        name2idCache.put(username, userIDLong);
        return user;
    }

    public void deleteUser(User user) throws UnauthorizedException {
        userAccountProvider.deleteUser(user.getID());
        // Expire user caches.
        id2userCache.remove(new Long(user.getID()));
        name2idCache.remove(user.getUsername());
    }

    public void deleteUser(long userID)
            throws UnauthorizedException, UserNotFoundException {
        deleteUser((User)id2userCache.get(new Long(userID)));
    }

    public int getUserCount() {
        return userIDProvider.getUserCount();
    }

    public Iterator users() throws UnauthorizedException {
        return new UserIterator(userIDProvider.getUserIDs().toArray());
    }

    public Iterator users(int startIndex, int numResults) throws UnauthorizedException {
        return new UserIterator(userIDProvider.getUserIDs(startIndex, numResults).toArray());
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(Container container) {
        super.initialize(container);
        initializeCaches();
    }
}