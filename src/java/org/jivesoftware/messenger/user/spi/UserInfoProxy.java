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

    public long getId() {
        return info.getId();
    }

    public String getName() {
        if (isNameVisible() || permissions.hasPermission(Permissions.SYSTEM_ADMIN |
                Permissions.USER_ADMIN)) {
            return info.getName();
        }
        else {
            return null;
        }
    }

    public void setName(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setName(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public boolean isNameVisible() {
        return info.isNameVisible();
    }

    public void setNameVisible(boolean visible) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setNameVisible(visible);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public String getEmail() {
        if (isEmailVisible() || permissions.hasPermission(Permissions.SYSTEM_ADMIN |
                Permissions.USER_ADMIN)) {
            return info.getEmail();
        }
        else {
            return null;
        }
    }

    public void setEmail(String email) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setEmail(email);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public boolean isEmailVisible() {
        return info.isEmailVisible();
    }

    public void setEmailVisible(boolean visible) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            info.setEmailVisible(visible);
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
