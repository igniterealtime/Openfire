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

package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.PresenceManager;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import java.util.Collection;

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

    public int getOnlineGuestCount() {
        return manager.getOnlineGuestCount();
    }

    public Collection<User> getOnlineUsers() {
        return manager.getOnlineUsers();
    }

    public Collection<User> getOnlineUsers(boolean ascending, int sortField) {
        return manager.getOnlineUsers(ascending, sortField);
    }

    public Collection<User> getOnlineUsers(boolean ascending, int sortField, int numResults) {
        return manager.getOnlineUsers(ascending, sortField, numResults);
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
        if (presence.getUsername().equals(authToken.getUsername()) ||
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
