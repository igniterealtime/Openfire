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

import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.auth.AuthToken;
import org.jivesoftware.messenger.auth.Permissions;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.RosterProxy;
import org.jivesoftware.messenger.user.CachedRoster;
import org.jivesoftware.messenger.user.IQRoster;

public class CachedRosterProxy extends RosterProxy implements CachedRoster {
    public CachedRosterProxy(CachedRoster roster, AuthToken authToken, Permissions permissions) {
        super(roster, authToken, permissions);
    }

    public String getUsername() {
        return ((CachedRoster)roster).getUsername();
    }

    public IQRoster getReset() throws UnauthorizedException {
        return ((CachedRoster)roster).getReset();
    }

    public void broadcastPresence(Presence packet) {
        ((CachedRoster)roster).broadcastPresence(packet);
    }

    public int getCachedSize() {
        return ((CachedRoster)roster).getCachedSize();
    }
}
