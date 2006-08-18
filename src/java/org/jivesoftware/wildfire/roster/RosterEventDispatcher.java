/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.roster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches roster events. The following events are supported:
 * <ul>
 * <li><b>rosterLoaded</b> --> A roster has just been loaded.</li>
 * <li><b>contactAdded</b> --> A contact has been added to a roster.</li>
 * <li><b>contactUpdated</b> --> A contact has been updated of a roster.</li>
 * <li><b>contactDeleted</b> --> A contact has been deleted from a roster.</li>
 * </ul>
 * Use {@link #addListener(RosterEventListener)} and {@link #removeListener(RosterEventListener)}
 * to add or remove {@link RosterEventListener}.
 *
 * @author Gaston Dombiak
 */
public class RosterEventDispatcher {

    private static List<RosterEventListener> listeners =
            new CopyOnWriteArrayList<RosterEventListener>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(RosterEventListener listener) {
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
    public static void removeListener(RosterEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies the listeners that a roster has just been loaded.
     *
     * @param roster the loaded roster.
     */
    public static void rosterLoaded(Roster roster) {
        if (!listeners.isEmpty()) {
            for (RosterEventListener listener : listeners) {
                listener.rosterLoaded(roster);
            }
        }
    }

    /**
     * Notifies the listeners that a contact has been added to a roster.
     *
     * @param roster the roster that was updated.
     * @param item   the new roster item.
     */
    public static void contactAdded(Roster roster, RosterItem item) {
        if (!listeners.isEmpty()) {
            for (RosterEventListener listener : listeners) {
                listener.contactAdded(roster, item);
            }
        }
    }

    /**
     * Notifies the listeners that a contact has been updated.
     *
     * @param roster the roster that was updated.
     * @param item   the updated roster item.
     */
    public static void contactUpdated(Roster roster, RosterItem item) {
        if (!listeners.isEmpty()) {
            for (RosterEventListener listener : listeners) {
                listener.contactUpdated(roster, item);
            }
        }
    }

    /**
     * Notifies the listeners that a contact has been deleted from a roster.
     *
     * @param roster the roster that was updated.
     * @param item   the roster item that was deleted.
     */
    public static void contactDeleted(Roster roster, RosterItem item) {
        if (!listeners.isEmpty()) {
            for (RosterEventListener listener : listeners) {
                listener.contactDeleted(roster, item);
            }
        }
    }
}
