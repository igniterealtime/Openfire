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

import org.jivesoftware.openfire.session.ClientSession;
import org.xmpp.packet.Presence;

/**
 * Interface to listen for presence events. Use the
 * {@link PresenceEventDispatcher#addListener(PresenceEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface PresenceEventListener {

    /**
     * Notification message indicating that a session that was not available is now
     * available. A session becomes available when an available presence is received.
     * Sessions that are available will have a route in the routing table thus becoming
     * eligible for receiving messages (in particular messages sent to the user bare JID).
     *
     * @param session the session that is now available.
     * @param presence the received available presence.
     */
    public void availableSession(ClientSession session, Presence presence);

    /**
     * Notification message indicating that a session that was available is no longer
     * available. A session becomes unavailable when an unavailable presence is received.
     * The entity may still be connected to the server and may send an available presence
     * later to indicate that communication can proceed.
     *
     * @param session the session that is no longer available.
     * @param presence the received unavailable presence.
     */
    public void unavailableSession(ClientSession session, Presence presence);

    /**
     * Notification message indicating that the presence priority of a session has
     * been modified. Presence priorities are used when deciding which session of
     * the same user should receive a message that was sent to the user bare's JID.
     *
     * @param session the affected session.
     * @param presence the presence that changed the priority.
     */
    public void presencePriorityChanged(ClientSession session, Presence presence);

    /**
     * Notification message indicating that an available session has changed its
     * presence. This is the case when the user presence changed the show value
     * (e.g. away, dnd, etc.) or the presence status message.
     *
     * @param session the affected session.
     * @param presence the received available presence with the new information.
     */
    public void presenceChanged(ClientSession session, Presence presence);
}
