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

package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.messenger.Entity;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Group;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.spi.UserIteratorProxy;
import java.util.Date;
import java.util.Iterator;

/**
 * Protection proxy for the Group interface. It restricts access of certain
 * methods to those that have the proper permissions to administer this object.
 *
 * @author Iain Shigeoka
 */
public class GroupProxy implements Group {

    private Group group;
    private AuthToken authToken;
    private Permissions permissions;

    public GroupProxy(Group group, AuthToken authToken, Permissions permissions) {
        this.group = group;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public long getID() {
        return group.getID();
    }

    public String getName() {
        return group.getName();
    }

    public void setName(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.setName(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public String getDescription() {
        return group.getDescription();
    }

    public void setDescription(String description) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.setDescription(description);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Date getCreationDate() {
        return group.getCreationDate();
    }

    public void setCreationDate(Date creationDate)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN)) {
            group.setCreationDate(creationDate);
        }
        else
            throw new UnauthorizedException();
    }

    public Date getModificationDate() {
        return group.getModificationDate();
    }

    public void setModificationDate(Date modificationDate)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN)) {
            group.setModificationDate(modificationDate);
        }
        else
            throw new UnauthorizedException();
    }

    public String getProperty(String name) {
        return group.getProperty(name);
    }

    public void setProperty(String name, String value)
            throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.setProperty(name, value);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.deleteProperty(name);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public Iterator getPropertyNames() {
        return group.getPropertyNames();
    }

    public void addAdministrator(Entity entity)
            throws UnauthorizedException, UserAlreadyExistsException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.addAdministrator(entity);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void removeAdministrator(Entity entity) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.removeAdministrator(entity);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void addMember(Entity entity)
            throws UnauthorizedException, UserAlreadyExistsException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.addMember(entity);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void removeMember(Entity entity) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN
                | Permissions.GROUP_ADMIN)) {
            group.removeMember(entity);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public boolean isAdministrator(Entity entity) {
        return group.isAdministrator(entity);
    }

    public boolean isMember(Entity entity) {
        return group.isMember(entity);
    }

    public int getAdministratorCount() {
        return group.getAdministratorCount();
    }

    public int getMemberCount() {
        return group.getMemberCount();
    }

    public Iterator members() {
        return new UserIteratorProxy(group.members(), authToken, permissions);
    }

    public Iterator administrators() {
        return new UserIteratorProxy(group.administrators(), authToken, permissions);
    }

    public Permissions getPermissions(AuthToken authToken) {
        return group.getPermissions(authToken);
    }

    public boolean isAuthorized(long type) {
        return permissions.hasPermission(type);
    }
}
