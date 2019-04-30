/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.roster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches roster events. The following events are supported:
 * <ul>
 * <li><b>rosterLoaded</b> --&gt; A roster has just been loaded.</li>
 * <li><b>contactAdded</b> --&gt; A contact has been added to a roster.</li>
 * <li><b>contactUpdated</b> --&gt; A contact has been updated of a roster.</li>
 * <li><b>contactDeleted</b> --&gt; A contact has been deleted from a roster.</li>
 * </ul>
 * Use {@link #addListener(RosterEventListener)} and {@link #removeListener(RosterEventListener)}
 * to add or remove {@link RosterEventListener}.
 *
 * @author Gaston Dombiak
 */
public class RosterEventDispatcher {
    private static final Logger Log = LoggerFactory.getLogger(RosterEventDispatcher.class);
    
    private static List<RosterEventListener> listeners =
            new CopyOnWriteArrayList<>();

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
                try {
                    listener.rosterLoaded(roster);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'rosterLoaded' event!", e);
                }   
            }
        }
    }

    /**
     * Notifies listeners that a contact is about to be added to a roster. New contacts
     * may be persisted to the database or not. Listeners may indicate that contact about
     * to be persisted should not be persisted. Only one listener is needed to return
     * {@code false} so that the contact is not persisted.
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
                try {
                    if (!listener.addingContact(roster, item, persistent)) {
                        answer = false;
                    }
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'addingContact' event!", e);
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
                try {
                    listener.contactAdded(roster, item);  
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'contactAdded' event!", e);
                }
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
                try {
                    listener.contactUpdated(roster, item);
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'contactUpdated' event!", e);
                }
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
                try {
                    listener.contactDeleted(roster, item);   
                } catch (Exception e) {
                    Log.warn("An exception occurred while dispatching a 'contactDeleted' event!", e);
                }
            }
        }
    }
}
