/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.xmpp.packet.Presence;

/**
 * Interface to listen for remote presence events. Use the
 * {@link RemotePresenceEventDispatcher#addListener(RemotePresenceEventListener)}
 * method to register for events.
 *
 * @author Armando Jagucki
 */
public interface RemotePresenceEventListener {

    /**
     * Notification message indicating that a remote user that was not available is now
     * available. A remote user becomes available when an available presence is received
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received available presence.
     */
    public void availableRemoteUser(Presence presence);

    /**
     * Notification message indicating that a remote user that was available is no longer
     * available. A remote user becomes unavailable when an unavailable presence is received.
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received unavailable presence.
     */
    public void unavailableRemoteUser(Presence presence);

}
