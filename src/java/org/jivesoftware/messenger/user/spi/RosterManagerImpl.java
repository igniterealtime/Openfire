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
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.RosterManager;
import org.jivesoftware.messenger.user.UserNotFoundException;

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
}