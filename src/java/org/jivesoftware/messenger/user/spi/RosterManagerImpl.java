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

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.XMPPAddress;

import java.util.Iterator;

public class RosterManagerImpl extends BasicModule implements RosterManager {

    private Cache rosterCache = null;

    public RosterManagerImpl() {
        super("Roster Manager");
    }

    public CachedRoster getRoster(String username) throws UserNotFoundException {
        if (rosterCache == null) {
            rosterCache = CacheManager.getCache("username2roster");
        }
        if (rosterCache == null) {
            throw new UserNotFoundException("Could not load caches");
        }
        CachedRoster roster = (CachedRoster)rosterCache.get(username);
        if (roster == null) {
            // Not in cache so load a new one:
            roster = new CachedRosterImpl(username);
            rosterCache.put(username, roster);
        }
        if (roster == null) {
            throw new UserNotFoundException(username);
        }
        return roster;
    }

    public void deleteRoster(XMPPAddress user) {
        try {
            String username = user.getNamePrep();
            // Get the roster of the deleted user
            Roster roster = (Roster)CacheManager.getCache("username2roster").get(username);
            if (roster == null) {
                // Not in cache so load a new one:
                roster = new CachedRosterImpl(username);
            }
            // Remove each roster item from the user's roster
            Iterator<RosterItem> items = roster.getRosterItems();
            while (items.hasNext()) {
                roster.deleteRosterItem(items.next().getJid());
            }
            // Remove the cached roster from memory
            CacheManager.getCache("username2roster").remove(username);

            // Get the rosters that have a reference to the deleted user
            RosterItemProvider rosteItemProvider = UserProviderFactory.getRosterItemProvider();
            Iterator<String> usernames = rosteItemProvider.getUsernames(user.toBareStringPrep());
            while (usernames.hasNext()) {
                username = usernames.next();
                // Get the roster that has a reference to the deleted user
                roster = (Roster)CacheManager.getCache("username2roster").get(username);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new CachedRosterImpl(username);
                }
                // Remove the deleted user reference from this roster
                roster.deleteRosterItem(user);
            }
        }
        catch (UnsupportedOperationException e) {
            // Do nothing
        }
        catch (UnauthorizedException e) {
            // Do nothing
        }
    }
}