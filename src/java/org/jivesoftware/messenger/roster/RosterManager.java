/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.roster;

import org.xmpp.packet.JID;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.SharedGroupException;
import org.jivesoftware.messenger.group.Group;

import java.util.Iterator;

/**
 * A simple service that allows components to retrieve a roster based solely on the ID
 * of the owner. Users have convenience methods for obtaining a roster associated with
 * the owner. However there are many components that need to retrieve the roster
 * based solely on the generic ID owner key. This interface defines a service that can
 * do that. This allows classes that generically manage resource for resource owners
 * (such as presence updates) to generically offer their services without knowing or
 * caring if the roster owner is a user, chatbot, etc.
 *
 * @author Iain Shigeoka
 */
public class RosterManager extends BasicModule {

    private Cache rosterCache = null;

    public RosterManager() {
        super("Roster Manager");
    }

    /**
     * Returns the roster for the given username.
     *
     * @param username the username to search for.
     * @return the roster associated with the ID.
     * @throws org.jivesoftware.messenger.user.UserNotFoundException if the ID does not correspond to a known
     *      entity on the server.
     */
    public Roster getRoster(String username) throws UserNotFoundException {
        if (rosterCache == null) {
            rosterCache = CacheManager.getCache("username2roster");
        }
        if (rosterCache == null) {
            throw new UserNotFoundException("Could not load caches");
        }
        Roster roster = (Roster)rosterCache.get(username);
        if (roster == null) {
            // Not in cache so load a new one:
            roster = new Roster(username);
            rosterCache.put(username, roster);
        }
        if (roster == null) {
            throw new UserNotFoundException(username);
        }
        return roster;
    }

    /**
     * Removes the entire roster of a given user. This is necessary when a user
     * account is being deleted from the server.
     *
     * @param user the user.
     */
    public void deleteRoster(JID user) {
        try {
            String username = user.getNode();
            // Get the roster of the deleted user
            Roster roster = (Roster)CacheManager.getCache("username2roster").get(username);
            if (roster == null) {
                // Not in cache so load a new one:
                roster = new Roster(username);
            }
            // Remove each roster item from the user's roster
            for (RosterItem item : roster.getRosterItems()) {
                try {
                    roster.deleteRosterItem(item.getJid(), false);
                }
                catch (SharedGroupException e) {
                    // Do nothing. We shouldn't have this exception since we disabled the checkings
                }
            }
            // Remove the cached roster from memory
            CacheManager.getCache("username2roster").remove(username);

            // Get the rosters that have a reference to the deleted user
            RosterItemProvider rosteItemProvider = RosterItemProvider.getInstance();
            Iterator<String> usernames = rosteItemProvider.getUsernames(user.toBareJID());
            while (usernames.hasNext()) {
                username = usernames.next();
                // Get the roster that has a reference to the deleted user
                roster = (Roster)CacheManager.getCache("username2roster").get(username);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new Roster(username);
                }
                // Remove the deleted user reference from this roster
                try {
                    roster.deleteRosterItem(user, false);
                }
                catch (SharedGroupException e) {
                    // Do nothing. We shouldn't have this exception since we disabled the checkings
                }
            }
        }
        catch (UnsupportedOperationException e) {
            // Do nothing
        }
    }

    /**
     * Notification that a Group has been deleted. Update the group users' roster accordingly.
     *
     * @param group the group that has been deleted.
     */
    public void groupDeleted(Group group) {
        // Iterate on all the group users and update their rosters
        for (String deletedUser : group.getUsers()) {
            groupUserDeleted(group, deletedUser);
        }
    }

    /**
     * Notification that a Group user has been added. Update the group users' roster accordingly.
     *
     * @param group the group where the user was added.
     * @param addedUser the username of the user that has been added to the group.
     */
    public void groupUserAdded(Group group, String addedUser) {
        // Iterate on all the group users and update their rosters
        for (String userToUpdate : group.getUsers()) {
            if (!addedUser.equals(userToUpdate)) {
                // Get the roster to update
                Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
                // Only update rosters in memory
                if (roster != null) {
                    roster.addSharedUser(group.getName(), addedUser);
                }
            }
        }
    }

    /**
     * Notification that a Group user has been deleted. Update the group users' roster accordingly.
     *
     * @param group the group from where the user was deleted.
     * @param deletedUser the username of the user that has been deleted from the group.
     */
    public void groupUserDeleted(Group group, String deletedUser) {
        // Iterate on all the group users and update their rosters
        for (String userToUpdate : group.getUsers()) {
            if (!deletedUser.equals(userToUpdate)) {
                // Get the roster to update
                Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
                // Only update rosters in memory
                if (roster != null) {
                    roster.deleteSharedUser(group.getName(), deletedUser);
                }
            }
        }
    }

}