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

import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserInfo;
import java.util.Date;

/**
 * Protection proxy for User objects.
 *
 * @author Iain Shigeoka
 * @see User
 */
public class UserInfoProxy implements UserInfo {

    private UserInfo info;
    private AuthToken authToken;
    private Permissions permissions;

    /**
     * Create a new UserProxy.
     */
    public UserInfoProxy(UserInfo info, AuthToken authToken, Permissions permissions) {
        this.info = info;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public String getName() {
        return info.getName();
    }

    public void setName(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setName(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public String getEmail() {
        return info.getEmail();
    }

    public void setEmail(String email) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setEmail(email);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Date getCreationDate() {
        return info.getCreationDate();
    }

    public void setCreationDate(Date creationDate)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setCreationDate(creationDate);
        }
        else
            throw new UnauthorizedException();
    }

    public Date getModificationDate() {
        return info.getModificationDate();
    }

    public void setModificationDate(Date modifiedDate) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setModificationDate(modifiedDate);
        }
        else
            throw new UnauthorizedException();
    }

    public int getCachedSize() {
        return info.getCachedSize();
    }

}
