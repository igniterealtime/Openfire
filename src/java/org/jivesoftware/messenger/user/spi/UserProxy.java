/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2001 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserInfo;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.Iterator;

/**
 * Protection proxy for User objects.
 *
 * @author Iain Shigeoka
 * @see User
 */
public class UserProxy implements User {

    private User user;
    private AuthToken authToken;
    private Permissions permissions;

    /**
     * Create a new UserProxy.
     */
    public UserProxy(User user, AuthToken authToken, Permissions permissions) {
        this.user = user;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public long getID() {
        return user.getID();
    }

    public String getUsername() {
        return user.getUsername();
    }

    public void setPassword(String password) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            user.setPassword(password);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public UserInfo getInfo() throws UserNotFoundException {
        return user.getInfo();
    }

    public void saveInfo() throws UnauthorizedException {
        user.saveInfo();
    }

    public String getProperty(String name) {
        return user.getProperty(name);
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            user.setProperty(name, value);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            user.deleteProperty(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Iterator getPropertyNames() {
        return user.getPropertyNames();
    }

    public CachedRoster getRoster() throws UnauthorizedException {
        return new CachedRosterProxy(user.getRoster(), authToken, permissions);
    }

    public Permissions getPermissions(AuthToken authToken) {
        return user.getPermissions(authToken);
    }

    public boolean isAuthorized(long permissionType) {
        return permissions.hasPermission(permissionType);
    }

    public void setVCardProperty(String name, String value) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            user.setVCardProperty(name, value);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public String getVCardProperty(String name) {
        return user.getVCardProperty(name);
    }

    public void deleteVCardProperty(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            user.deleteVCardProperty(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Iterator getVCardPropertyNames() {
        return user.getVCardPropertyNames();
    }

}
