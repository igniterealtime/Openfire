/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
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

    public long getUserID() {
        return ((CachedRoster)roster).getUserID();
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
