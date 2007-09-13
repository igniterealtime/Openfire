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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.xmpp.packet.Presence;

/**
 * Dispatches remote presence events. The following events are supported:
 * <ul>
 * <li><b>availableRemoteUser</b> --> A remote user is now available.</li>
 * <li><b>unavailableRemoteUser</b> --> A remote user is no longer available.</li>
 * </ul>
 * Use {@link #addListener(RemotePresenceEventListener)} and
 * {@link #removeListener(RemotePresenceEventListener)} to add or remove {@link RemotePresenceEventListener}.
 *
 * @author Armando Jagucki
 */
public class RemotePresenceEventDispatcher {

    private static List<RemotePresenceEventListener> listeners =
            new CopyOnWriteArrayList<RemotePresenceEventListener>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(RemotePresenceEventListener listener) {
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
     * Notification message indicating that a remote user that was not available is now
     * available. A remote user becomes available when an available presence is received
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received available presence.
     */
    public static void availableRemoteUser(Presence presence) {
        if (!listeners.isEmpty()) {
            for (RemotePresenceEventListener listener : listeners) {
                listener.availableRemoteUser(presence);
            }
        }
    }

    /**
     * Notification message indicating that a remote user that was available is no longer
     * available. A remote user becomes unavailable when an unavailable presence is received.
     * by <tt>PresenceRouter</tt>.
     *
     * @param presence the received unavailable presence.
     */
    public static void unavailableRemoteUser(Presence presence) {
        if (!listeners.isEmpty()) {
            for (RemotePresenceEventListener listener : listeners) {
                listener.unavailableRemoteUser(presence);
            }
        }
    }

}
