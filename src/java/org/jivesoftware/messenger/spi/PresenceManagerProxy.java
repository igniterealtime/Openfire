/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2002 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.spi.UserIteratorProxy;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Enforces security constraints.
 *
 * @author Iain Shigeoka
 */
public class PresenceManagerProxy implements PresenceManager {

    private PresenceManager manager;
    private AuthToken authToken;
    private Permissions permissions;

    public PresenceManagerProxy(PresenceManager manager, AuthToken authToken,
                                Permissions permissions) {
        this.manager = manager;
        this.authToken = authToken;
        this.permissions = permissions;
    }

    public boolean isAvailable(User user) throws UnauthorizedException {
        return manager.isAvailable(user);
    }

    public int getOnlineUserCount() {
        Iterator iter = getOnlineUsers();
        int count = 0;

        while (iter.hasNext()) {
            iter.next();
            count++;
        }

        return count;
    }

    public int getOnlineGuestCount() {
        return manager.getOnlineGuestCount();
    }

    public Iterator getOnlineUsers() {
        Iterator iter = manager.getOnlineUsers();
        ArrayList users = new ArrayList();

        while (iter.hasNext()) {
            User user = (User)iter.next();
            if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN) ||
                    user.isAuthorized(Permissions.VIEW_ONLINE_STATUS)) {
                users.add(user);
            }
        }

        return new UserIteratorProxy(users.iterator(), authToken, permissions);
    }

    public Iterator getOnlineUsers(boolean ascending, int sortField) {
        Iterator iter = manager.getOnlineUsers(ascending, sortField);
        ArrayList users = new ArrayList();

        while (iter.hasNext()) {
            User user = (User)iter.next();
            if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN) ||
                    user.isAuthorized(Permissions.VIEW_ONLINE_STATUS)) {
                users.add(user);
            }
        }

        return new UserIteratorProxy(users.iterator(), authToken, permissions);
    }

    public Iterator getOnlineUsers(boolean ascending, int sortField, int numResults) {
        Iterator iter = manager.getOnlineUsers(ascending, sortField, numResults);
        ArrayList users = new ArrayList();

        while (iter.hasNext()) {
            User user = (User)iter.next();
            if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN) ||
                    user.isAuthorized(Permissions.VIEW_ONLINE_STATUS)) {
                users.add(user);
            }
        }

        return new UserIteratorProxy(users.iterator(), authToken, permissions);
    }

    public Presence createPresence(User user, String uid) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return manager.createPresence(user, uid);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void setOffline(Presence presence) throws UnauthorizedException {
        if ((presence.getUserID() == authToken.getUserID()) ||
                permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            manager.setOffline(presence);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void setOffline(XMPPAddress jid) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            manager.setOffline(jid);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void probePresence(String prober, XMPPAddress probee) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            manager.probePresence(prober, probee);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void probePresence(XMPPAddress prober, XMPPAddress probee) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            manager.probePresence(prober, probee);
        }
        else {
            throw new UnauthorizedException();
        }
    }
}
