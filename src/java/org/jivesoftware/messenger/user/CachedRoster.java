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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.util.Cacheable;
import org.xmpp.packet.Presence;

/**
 * <p>A Roster that is cached in memory and persisted to some backend storage system.</p>
 * <p/>
 * <p>Cached Rosters are the permanent roster attached to a user/chatbot account. This interface
 * is primarily a marker interface for implementations.</p>
 *
 * @author Iain Shigeoka
 *         <p/>
 *
 */
public interface CachedRoster extends Roster, Cacheable {

    /**
     * <p>Return the username of the user or chatbot that owns this roster.</p>
     *
     * @return the username of the user or chatbot that owns this roster
     */
    String getUsername();

    /**
     * <p>Obtain a 'roster reset', a snapshot of the full cached roster as an IQRoster.</p>
     *
     * @return The roster reset (snapshot) as an IQRoster
     */
    IQRoster getReset() throws UnauthorizedException;

    /**
     * <p>Broadcast the presence update to all subscribers of the roter.</p>
     * <p/>
     * <p>Any presence change typically results in a broadcast to the roster members.</p>
     *
     * @param packet The presence packet to broadcast
     */
    void broadcastPresence(Presence packet);
}
