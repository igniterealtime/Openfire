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
import org.jivesoftware.messenger.event.GroupEventListener;
import org.jivesoftware.messenger.event.GroupEventDispatcher;
import org.jivesoftware.messenger.group.Group;

import java.util.Iterator;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Map;

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
public class RosterManager extends BasicModule implements GroupEventListener {

    private Cache rosterCache = null;

    public RosterManager() {
        super("Roster Manager");
        // Add the new instance as a listener of group events
        GroupEventDispatcher.addListener(this);
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

    public void groupCreated(Group group, Map params) {
        //Do nothing
    }

    public void groupDeleting(Group group, Map params) {
        // Get all the group users
        Collection<String> users = new ArrayList<String>(group.getMembers());
        users.addAll(group.getAdmins());
        // Iterate on all the group users and update their rosters
        for (String deletedUser : users) {
            groupUserDeleted(group, deletedUser);
        }
    }

    public void groupModified(Group group, Map params) {
        // Do nothing if no group property has been modified
        if (!"propertyModified".equals(params.get("type"))) {
             return;
        }
        String originalValue = (String) params.get("originalValue");

        if ("showInRoster".equals(params.get("propertyKey"))) {
            String currentValue = group.getProperties().get("showInRoster");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Get all the group users
            Collection<String> users = new ArrayList<String>(group.getMembers());
            users.addAll(group.getAdmins());
            if ("true".equals(currentValue)) {
                // We must show group in group members' rosters
                // Iterate on all the group users and update their rosters
                for (String addedUser : users) {
                    groupUserAdded(group, addedUser);
                }
            }
            else {
                // We must remove group from group members' rosters
                // Iterate on all the group users and update their rosters
                for (String deletedUser : users) {
                    groupUserDeleted(group, deletedUser);
                }
            }
        }
        else if ("displayName".equals(params.get("propertyKey"))) {
            String currentValue = group.getProperties().get("displayName");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Do nothing if the group is not being shown in group members' rosters
            if (!"true".equals(group.getProperties().get("showInRoster"))) {
                return;
            }
            // Get all the group users
            Collection<String> users = new ArrayList<String>(group.getMembers());
            users.addAll(group.getAdmins());
            // Iterate on all the group users and update their rosters
            for (String updatedUser : users) {
                // Get the roster to update.
                Roster roster = (Roster) CacheManager.getCache("username2roster").get(updatedUser);
                if (roster != null) {
                    // Update the roster with the new group display name
                    roster.shareGroupRenamed(originalValue, currentValue, users);
                }
            }
        }
    }

    public void memberAdded(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!"true".equals(group.getProperties().get("showInRoster"))) {
            return;
        }
        String addedUser = (String) params.get("member");
        groupUserAdded(group, addedUser);
    }

    public void memberRemoved(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!"true".equals(group.getProperties().get("showInRoster"))) {
            return;
        }
        String addedUser = (String) params.get("member");
        groupUserDeleted(group, addedUser);
    }

    public void adminAdded(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!"true".equals(group.getProperties().get("showInRoster"))) {
            return;
        }
        String addedUser = (String) params.get("admin");
        groupUserAdded(group, addedUser);
    }

    public void adminRemoved(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!"true".equals(group.getProperties().get("showInRoster"))) {
            return;
        }
        String addedUser = (String) params.get("admin");
        groupUserDeleted(group, addedUser);
    }

    /**
     * Notification that a Group user has been added. Update the group users' roster accordingly.
     *
     * @param group the group where the user was added.
     * @param addedUser the username of the user that has been added to the group.
     */
    private void groupUserAdded(Group group, String addedUser) {
        // Get all the group users
        Collection<String> users = new ArrayList<String>(group.getMembers());
        users.addAll(group.getAdmins());
        // Get the roster of the added user.
        Roster addedUserRoster = (Roster) CacheManager.getCache("username2roster").get(addedUser);
        // Get the display name of the group
        String groupName = group.getProperties().get("displayName");

        // Iterate on all the group users and update their rosters
        for (String userToUpdate : users) {
            if (!addedUser.equals(userToUpdate)) {
                // Get the roster to update
                Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
                // Only update rosters in memory
                if (roster != null) {
                    roster.addSharedUser(groupName, addedUser);
                }
                // Update the roster of the newly added group user
                if (addedUserRoster != null) {
                    addedUserRoster.addSharedUser(groupName, userToUpdate);
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
    private void groupUserDeleted(Group group, String deletedUser) {
        // Get all the group users
        Collection<String> users = new ArrayList<String>(group.getMembers());
        users.addAll(group.getAdmins());
        // Get the roster of the deleted user.
        Roster deletedUserRoster = (Roster) CacheManager.getCache("username2roster").get(deletedUser);
        // Get the display name of the group
        String groupName = group.getProperties().get("displayName");

        // Iterate on all the group users and update their rosters
        for (String userToUpdate : users) {
            // Get the roster to update
            Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
            // Only update rosters in memory
            if (roster != null) {
                roster.deleteSharedUser(groupName, deletedUser);
            }
            // Update the roster of the newly deleted group user
            if (deletedUserRoster != null) {
                deletedUserRoster.deleteSharedUser(groupName, userToUpdate);
            }
        }
    }
}