/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

/**
 * Interface to listen for roster events. Use the
 * {@link RosterEventDispatcher#addListener(RosterEventListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface RosterEventListener {

    /**
     * Notification message indicating that a roster has just been loaded.
     *
     * @param roster the loaded roster.
     */
    public void rosterLoaded(Roster roster);

    /**
     * Notification message indicating that a contact is about to be added to a roster. New
     * contacts may be persisted to the database or not. Listeners may indicate that contact
     * about to be persisted should not be persisted. Only one listener is needed to return
     * <tt>false</tt> so that the contact is not persisted.
     *
     * @param roster the roster that was updated.
     * @param item the new roster item.
     * @param persistent true if the new contact is going to be saved to the database.
     * @return false if the contact should not be persisted to the database.
     */
    public boolean addingContact(Roster roster, RosterItem item, boolean persistent);

    /**
     * Notification message indicating that a contact has been added to a roster.
     *
     * @param roster the roster that was updated.
     * @param item the new roster item.
     */
    public void contactAdded(Roster roster, RosterItem item);

    /**
     * Notification message indicating that a contact has been updated.
     *
     * @param roster the roster that was updated.
     * @param item the updated roster item.
     */
    public void contactUpdated(Roster roster, RosterItem item);

    /**
     * Notification message indicating that a contact has been deleted from a roster.
     *
     * @param roster the roster that was updated.
     * @param item the roster item that was deleted.
     */
    public void contactDeleted(Roster roster, RosterItem item);
}
