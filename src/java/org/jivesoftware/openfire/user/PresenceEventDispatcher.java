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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches presence events. The following events are supported:
 * <ul>
 * <li><b>availableSession</b> --> A session is now available to receive communication.</li>
 * <li><b>unavailableSession</b> --> A session is now longer available.</li>
 * <li><b>presencePriorityChanged</b> --> The priority of a resource has changed.</li>
 * <li><b>presenceChanged</b> --> The show or status value of an available session has changed.</li>
 * </ul>
 * Use {@link #addListener(PresenceEventListener)} and
 * {@link #removeListener(PresenceEventListener)} to add or remove {@link PresenceEventListener}.
 *
 * @author Gaston Dombiak
 */
public class PresenceEventDispatcher {

    private static List<PresenceEventListener> listeners =
            new CopyOnWriteArrayList<PresenceEventListener>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(PresenceEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(PresenceEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notification message indicating that a session that was not available is now
     * available. A session becomes available when an available presence is received.
     * Sessions that are available will have a route in the routing table thus becoming
     * eligible for receiving messages (in particular messages sent to the user bare JID).
     *
     * @param session the session that is now available.
     * @param presence the received available presence.
     */
    public static void availableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.availableSession(session, presence);
            }
        }
    }

    /**
     * Notification message indicating that a session that was available is no longer
     * available. A session becomes unavailable when an unavailable presence is received.
     * The entity may still be connected to the server and may send an available presence
     * later to indicate that communication can proceed.
     *
     * @param session the session that is no longer available.
     * @param presence the received unavailable presence.
     */
    public static void unavailableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.unavailableSession(session, presence);
            }
        }
    }


    /**
     * Notification message indicating that the presence priority of a session has
     * been modified. Presence priorities are used when deciding which session of
     * the same user should receive a message that was sent to the user bare's JID.
     *
     * @param session the affected session.
     * @param presence the presence that changed the priority.
     */
    public static void presencePriorityChanged(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.presencePriorityChanged(session, presence);
            }
        }
    }


    /**
     * Notification message indicating that an available session has changed its
     * presence. This is the case when the user presence changed the show value
     * (e.g. away, dnd, etc.) or the presence status message.
     *
     * @param session the affected session.
     * @param presence the received available presence with the new information.
     */
    public static void presenceChanged(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.presenceChanged(session, presence);
            }
        }
    }
}
