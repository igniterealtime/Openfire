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
