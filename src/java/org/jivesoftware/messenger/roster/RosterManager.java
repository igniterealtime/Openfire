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
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.SharedGroupException;
import org.jivesoftware.messenger.SessionManager;
import org.jivesoftware.messenger.event.GroupEventListener;
import org.jivesoftware.messenger.event.GroupEventDispatcher;
import org.jivesoftware.messenger.group.Group;
import org.jivesoftware.messenger.group.GroupManager;
import org.jivesoftware.messenger.group.GroupNotFoundException;

import java.util.*;

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
     * @throws org.jivesoftware.messenger.user.UserNotFoundException if the ID does not correspond
     *         to a known entity on the server.
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
     * Returns a collection with all the groups that the user may include in his roster. The
     * following criteria will be used to select the groups: 1) Groups that are configured so that
     * everybody can include in his roster, 2) Groups that are configured so that its users may
     * include the group in their rosters and the user is a group user of the group and 3) User
     * belongs to a Group that may see a Group that whose members may include the Group in their
     * rosters.
     *
     * @param user the user to return his shared groups.
     * @return a collection with all the groups that the user may include in his roster.
     */
    public Collection<Group> getSharedGroups(User user) {
        Collection<Group> answer = new HashSet<Group>();
        Collection<Group> groups = GroupManager.getInstance().getGroups();
        for (Group group : groups) {
            String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
            if ("onlyGroup".equals(showInRoster)) {
                if (group.isUser(user.getUsername())) {
                    // The user belongs to the group so add the group to the answer
                    answer.add(group);
                }
                else {
                    // Check if the user belongs to a group that may see this group
                    Collection<Group> groupList = parseGroups(group.getProperties().get("sharedRoster.groupList"));
                    for (Group groupInList : groupList) {
                        if (groupInList.isUser(user.getUsername())) {
                            answer.add(groupInList);
                        }
                    }
                }
            }
            else if ("everybody".equals(showInRoster)) {
                // Anyone can see this group so add the group to the answer
                answer.add(group);
            }
        }
        return answer;
    }

    /**
     * Returns a collection of Groups obtained by parsing a comma delimited String with the name
     * of groups.
     *
     * @param groupNames a comma delimited string with group names.
     * @return a collection of Groups obtained by parsing a comma delimited String with the name
     *         of groups.
     */
    private Collection<Group> parseGroups(String groupNames) {
        Collection<Group> answer = new HashSet<Group>();
        if (groupNames != null) {
            StringTokenizer tokenizer = new StringTokenizer(groupNames, ",");
            while (tokenizer.hasMoreTokens()) {
                String groupName = tokenizer.nextToken();
                try {
                    answer.add(GroupManager.getInstance().getGroup(groupName));
                }
                catch (GroupNotFoundException e) {
                    // Do nothing. Silently ignore the invalid reference to the group
                }
            }
        }
        return answer;
    }

    public void groupCreated(Group group, Map params) {
        //Do nothing
    }

    public void groupDeleting(Group group, Map params) {
        // Iterate on all the group users and update their rosters
        for (String deletedUser : getRelatedUsers(group, true)) {
            groupUserDeleted(group, deletedUser);
        }
    }

    public void groupModified(Group group, Map params) {
        // Do nothing if no group property has been modified
        if (!"propertyModified".equals(params.get("type"))) {
             return;
        }
        String keyChanged = (String) params.get("propertyKey");
        String originalValue = (String) params.get("originalValue");


        if ("sharedRoster.showInRoster".equals(keyChanged)) {
            String currentValue = group.getProperties().get("sharedRoster.showInRoster");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Remove the group from all users's rosters (if the group was a shared group)
            Collection<String> users = getRelatedUsers(group, originalValue,
                    group.getProperties().get("sharedRoster.groupList"), true);
            for (String deletedUser : users) {
                groupUserDeleted(group, users, deletedUser);
            }
            // Add the group to all users's rosters (if the group is a shared group)
            for (String addedUser : getRelatedUsers(group, true)) {
                groupUserAdded(group, addedUser);
            }
        }
        else if ("sharedRoster.groupList".equals(keyChanged)) {
            String currentValue = group.getProperties().get("sharedRoster.groupList");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Remove the group from all users's rosters (if the group was a shared group)
            Collection<String> users = getRelatedUsers(group,
                    group.getProperties().get("sharedRoster.showInRoster"), originalValue, true);
            for (String deletedUser : users) {
                groupUserDeleted(group, users, deletedUser);
            }
            // Add the group to all users's rosters (if the group is a shared group)
            for (String addedUser : getRelatedUsers(group, true)) {
                groupUserAdded(group, addedUser);
            }
        }
        else if ("sharedRoster.displayName".equals(keyChanged)) {
            String currentValue = group.getProperties().get("sharedRoster.displayName");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Do nothing if the group is not being shown in users' rosters
            if (!isSharedGroup(group)) {
                return;
            }
            // Get all the affected users
            Collection<String> users = getRelatedUsers(group, true);
            // Iterate on all the affected users and update their rosters
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

    /**
     * Returns true if the specified Group may be included in a user roster. The decision is made
     * based on the group properties that are configurable through the Admin Console.
     *
     * @param group the group to check if it may be considered a shared group.
     * @return true if the specified Group may be included in a user roster.
     */
    public boolean isSharedGroup(Group group) {
        String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
        if ("onlyGroup".equals(showInRoster) || "everybody".equals(showInRoster)) {
            return true;
        }
        return false;
    }

    public void memberAdded(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!isSharedGroup(group)) {
            return;
        }
        String addedUser = (String) params.get("member");
        groupUserAdded(group, addedUser);
    }

    public void memberRemoved(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!isSharedGroup(group)) {
            return;
        }
        String addedUser = (String) params.get("member");
        groupUserDeleted(group, addedUser);
    }

    public void adminAdded(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!isSharedGroup(group)) {
            return;
        }
        String addedUser = (String) params.get("admin");
        groupUserAdded(group, addedUser);
    }

    public void adminRemoved(Group group, Map params) {
        // Do nothing if the group is not being shown in group members' rosters
        if (!isSharedGroup(group)) {
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
        // Get all the affected users
        Collection<String> users = getRelatedUsers(group, true);
        // Get the roster of the added user.
        Roster addedUserRoster = (Roster) CacheManager.getCache("username2roster").get(addedUser);

        // Iterate on all the affected users and update their rosters
        for (String userToUpdate : users) {
            if (!addedUser.equals(userToUpdate)) {
                // Get the roster to update
                Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
                // Only update rosters in memory
                if (roster != null) {
                    roster.addSharedUser(group, addedUser);
                }
                // Update the roster of the newly added group user. Only add users of the group to
                // the roster of the new group user
                if (addedUserRoster != null && group.isUser(userToUpdate)) {
                    addedUserRoster.addSharedUser(group, userToUpdate);
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
        groupUserDeleted(group, getRelatedUsers(group, true), deletedUser);
    }

    /**
     * Notification that a Group user has been deleted. Update the group users' roster accordingly.
     *
     * @param group the group from where the user was deleted.
     * @param users the users to update their rosters
     * @param deletedUser the username of the user that has been deleted from the group.
     */
    private void groupUserDeleted(Group group, Collection<String> users, String deletedUser) {
        // Get the roster of the deleted user.
        Roster deletedUserRoster = (Roster) CacheManager.getCache("username2roster").get(deletedUser);
        // Get the display name of the group
        String groupName = group.getProperties().get("sharedRoster.displayName");

        // Iterate on all the affected users and update their rosters
        for (String userToUpdate : users) {
            // Get the roster to update
            Roster roster = (Roster)CacheManager.getCache("username2roster").get(userToUpdate);
            // Only update rosters in memory
            if (roster != null) {
                roster.deleteSharedUser(groupName, deletedUser);
            }
            // Update the roster of the newly deleted group user. Only remove users of the group
            // from the roster of the deleted group user
            if (deletedUserRoster != null && group.isUser(userToUpdate)) {
                deletedUserRoster.deleteSharedUser(groupName, userToUpdate);
            }
        }
    }

    /**
     * Returns all the users that are related to a shared group. This is the logic that we are
     * using: 1) If the group visiblity is configured as "Everybody" then all users in the system or
     * all logged users in the system will be returned (configurable thorugh the "filterOffline"
     * flag), 2) if the group visiblity is configured as "onlyGroup" then all the group users will
     * be included in the answer and 3) if the group visiblity is configured as "onlyGroup" and
     * the group allows other groups to include the group in the groups users' roster then all
     * the users of the allowed groups will be included in the answer.
     */
    Collection<String> getRelatedUsers(Group group, boolean filterOffline) {
        return getRelatedUsers(group, group.getProperties().get("sharedRoster.showInRoster"),
                group.getProperties().get("sharedRoster.groupList"), filterOffline);
    }

    /**
     * This method is similar to {@link #getRelatedUsers(Group, boolean)} except that it receives
     * some group properties. The group properties are passed as parameters since the called of this
     * method may want to obtain the related users of the group based in some properties values.
     *
     * This is useful when the group is being edited and some properties has changed and we need to
     * obtain the related users of the group based on the previous group state.
     */ 
    private Collection<String> getRelatedUsers(Group group, String showInRoster, String groupNames,
            boolean filterOffline) {
        // Answer an empty collection if the group is not being shown in users' rosters
        if (!"onlyGroup".equals(showInRoster) && !"everybody".equals(showInRoster)) {
            return new ArrayList<String>();
        }
        // Add the users of the group
        Collection<String> users = new HashSet<String>(group.getMembers());
        users.addAll(group.getAdmins());
        // Check if anyone can see this shared group
        if ("everybody".equals(showInRoster)) {
            if (filterOffline) {
                // Add all logged users. We don't need to add all users in the system since only the
                // logged ones will be affected.
                users.addAll(SessionManager.getInstance().getSessionUsers());
            }
            else {
                // Add all users in the system
                for (User user : UserManager.getInstance().getUsers()) {
                    users.add(user.getUsername());
                }
            }
        }
        else {
            // Add the users that may see the group
            Collection<Group> groupList = parseGroups(groupNames);
            for (Group groupInList : groupList) {
                users.addAll(groupInList.getMembers());
                users.addAll(groupInList.getAdmins());
            }
        }
        return users;
    }
}