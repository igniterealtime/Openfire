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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Iterator;

/**
 * Protection proxy for the UserManager class. It restricts access to
 * protected methods by throwing UnauthorizedExceptions when necessary.
 *
 * @author Iain Shigeoka
 * @see org.jivesoftware.messenger.user.UserManager
 */
public class UserManagerProxy implements UserManager {

    private UserManager userManager;
    private AuthToken auth;
    private Permissions permissions;

    /**
     * Creates a new proxy.
     */
    public UserManagerProxy(UserManager userManager, AuthToken auth, Permissions permissions) {
        this.userManager = userManager;
        this.auth = auth;
        this.permissions = permissions;
    }

    public User createUser(String username, String password, String email)
            throws UserAlreadyExistsException, UnauthorizedException {
        return userManager.createUser(username, password, email);
    }

    public User getUser(long userID) throws UserNotFoundException {
        User user = userManager.getUser(userID);
        Permissions userPermissions = user.getPermissions(auth);
        Permissions newPermissions = new Permissions(permissions, userPermissions);

        return new UserProxy(user, auth, newPermissions);
    }

    public User getUser(String username) throws UserNotFoundException {
        User user = userManager.getUser(username.toLowerCase());
        Permissions userPermissions = user.getPermissions(auth);
        Permissions newPermissions = new Permissions(permissions, userPermissions);

        return new UserProxy(user, auth, newPermissions);
    }

    public long getUserID(String username) throws UserNotFoundException {
        return userManager.getUserID(username);
    }

    public void deleteUser(User user) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            userManager.deleteUser(user);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void deleteUser(long userID) throws UnauthorizedException, UserNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            userManager.deleteUser(userID);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getUserCount() {
        return userManager.getUserCount();
    }

    public Iterator users() throws UnauthorizedException {
        Iterator iterator = userManager.users();
        return new UserGroupIteratorProxy(JiveConstants.USER, iterator, auth, permissions);
    }

    public Iterator users(int startIndex, int numResults) throws UnauthorizedException {
        Iterator iterator = userManager.users(startIndex, numResults);
        return new UserGroupIteratorProxy(JiveConstants.USER, iterator, auth, permissions);
    }

    public String getName() {
        return null;
    }

    public void initialize(Container container) {
        userManager.initialize(container);
    }

    public void start() {
        userManager.start();
    }

    public void stop() {
        userManager.stop();
    }

    public void destroy() {
        userManager.destroy();
    }
}
