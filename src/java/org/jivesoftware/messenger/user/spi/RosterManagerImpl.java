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
import org.jivesoftware.messenger.container.ServiceLookupFactory;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.NameIDManager;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.RosterManager;
import org.jivesoftware.messenger.user.UserNotFoundException;

public class RosterManagerImpl extends BasicModule implements RosterManager {

    private Cache rosterCache = null;
    private NameIDManager nameIDManager = null;

    public RosterManagerImpl() {
        super("Roster Manager");
    }

    public CachedRoster getRoster(long id) throws UserNotFoundException {
        if (rosterCache == null) {
            rosterCache = CacheManager.getCache("userid2roster");
        }
        if (rosterCache == null) {
            throw new UserNotFoundException("Could not load caches");
        }
        CachedRoster roster = (CachedRoster)rosterCache.get(new Long(id));
        if (roster == null) {
            if (nameIDManager == null) {
                try {
                    nameIDManager =
                            (NameIDManager)ServiceLookupFactory.getLookup().lookup(NameIDManager.class);
                }
                catch (UnauthorizedException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
            if (nameIDManager != null) {
                // Not in cache so load a new one:
                roster = new CachedRosterImpl(id, nameIDManager.getUsername(id));
                rosterCache.put(new Long(id), roster);
            }
        }
        if (roster == null) {
            throw new UserNotFoundException(Long.toHexString(id));
        }
        return roster;
    }
}
