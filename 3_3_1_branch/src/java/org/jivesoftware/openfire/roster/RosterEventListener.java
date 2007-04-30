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
