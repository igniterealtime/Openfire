/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.roster;

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
     * Notifies listeners that a contact is about to be added to a roster. New contacts
     * may be persisted to the database or not. Listeners may indicate that contact about
     * to be persisted should not be persisted. Only one listener is needed to return
     * <tt>false</tt> so that the contact is not persisted.
     *
     * @param roster the roster that was updated.
     * @param item the new roster item.
     * @param persistent true if the new contact is going to be saved to the database.
     * @return false if the contact should not be persisted to the database.
     */
    public static boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
        boolean answer = persistent;
        if (!listeners.isEmpty()) {
            for (RosterEventListener listener : listeners) {
                if (!listener.addingContact(roster, item, persistent)) {
                    answer = false;
                }
            }
        }
        return answer;
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
