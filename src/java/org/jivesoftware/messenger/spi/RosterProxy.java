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

import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.Roster;
import org.jivesoftware.messenger.user.RosterItem;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Protection proxy for the Roster interface. It restricts access of certain
 * methods to those that have the proper permissions to administer this object.
 *
 * @author Iain Shigeoka
 */
public class RosterProxy implements Roster {

    protected Roster roster;
    private AuthToken authToken;
    private Permissions permissions;

    public RosterProxy(Roster roster, AuthToken authToken, Permissions permissions) {
        this.authToken = authToken;
        this.permissions = permissions;
        this.roster = roster;
    }

    public boolean isRosterItem(XMPPAddress user) {
        return roster.isRosterItem(user);
    }

    public Iterator getRosterItems() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            Iterator iter = roster.getRosterItems();
            ArrayList items = new ArrayList();

            while (iter.hasNext()) {
                RosterItem item = (RosterItem)iter.next();
                if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
                    // check for ability to view online status generically
                    // rosteritem user .isAuthorized(Permissions.VIEW_ONLINE_STATUS)) {
                    if (!(item instanceof RosterItemProxy)) {
                        item = new RosterItemProxy(item, authToken, permissions);
                    }
                    items.add(item);
                }
            }

            return items.iterator();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public int getTotalRosterItemCount() throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return roster.getTotalRosterItemCount();
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RosterItem getRosterItem(XMPPAddress user) throws UnauthorizedException, UserNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            RosterItem item = roster.getRosterItem(user);
            if (!(item instanceof RosterItemProxy)) {
                item = new RosterItemProxy(item, authToken, permissions);
            }
            return item;
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RosterItem createRosterItem(XMPPAddress user) throws UnauthorizedException, UserAlreadyExistsException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return roster.createRosterItem(user);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RosterItem createRosterItem(XMPPAddress user, String nickname, List groups) throws UnauthorizedException, UserAlreadyExistsException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return roster.createRosterItem(user, nickname, groups);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RosterItem createRosterItem(RosterItem item) throws UnauthorizedException, UserAlreadyExistsException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            return roster.createRosterItem(item);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public void updateRosterItem(RosterItem item) throws UnauthorizedException, UserNotFoundException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            roster.updateRosterItem(item);
        }
        else {
            throw new UnauthorizedException();
        }
    }

    public RosterItem deleteRosterItem(XMPPAddress user) throws UnauthorizedException {
        if (permissions.hasPermission(Permissions.SYSTEM_ADMIN | Permissions.USER_ADMIN)) {
            RosterItem item = roster.deleteRosterItem(user);
            if (!(item instanceof RosterItemProxy)) {
                item = new RosterItemProxy(item, authToken, permissions);
            }
            return item;
        }
        else {
            throw new UnauthorizedException();
        }
    }
}
