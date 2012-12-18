/**
 * $RCSfile: RosterManager.java,v $
 * $Revision: 3138 $
 * $Date: 2005-12-01 02:13:26 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
public class RosterManager extends BasicModule implements GroupEventListener, UserEventListener {

    private static final Logger Log = LoggerFactory.getLogger(RosterManager.class);

    private Cache<String, Roster> rosterCache = null;
    private XMPPServer server;
    private RoutingTable routingTable;
    private RosterItemProvider provider;

    /**
     * Returns true if the roster service is enabled. When disabled it is not possible to
     * retrieve users rosters or broadcast presence packets to roster contacts.
     *
     * @return true if the roster service is enabled.
     */
    public static boolean isRosterServiceEnabled() {
        return JiveGlobals.getBooleanProperty("xmpp.client.roster.active", true);
    }

    public RosterManager() {
        super("Roster Manager");
        rosterCache = CacheFactory.createCache("Roster");

        initProvider();

        PropertyEventDispatcher.addListener(new PropertyEventListener() {
            public void propertySet(String property, Map params) {
                if (property.equals("provider.roster.className")) {
                    initProvider();
                }
            }
            public void propertyDeleted(String property, Map params) {}
            public void xmlPropertySet(String property, Map params) {}
            public void xmlPropertyDeleted(String property, Map params) {}
        });

    }

    /**
     * Returns the roster for the given username.
     *
     * @param username the username to search for.
     * @return the roster associated with the ID.
     * @throws org.jivesoftware.openfire.user.UserNotFoundException if the ID does not correspond
     *         to a known entity on the server.
     */
    public Roster getRoster(String username) throws UserNotFoundException {
        Roster roster = rosterCache.get(username);
        if (roster == null) {
            // Synchronize using a unique key so that other threads loading the User
            // and not the Roster cannot produce a deadlock
            synchronized ((username + " ro").intern()) {
                roster = rosterCache.get(username);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new Roster(username);
                    rosterCache.put(username, roster);
                }
            }
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
        if (!server.isLocal(user)) {
            // Ignore request if user is not a local user
            return;
        }
        try {
            String username = user.getNode();
            // Get the roster of the deleted user
            Roster roster = getRoster(username);
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
            rosterCache.remove(username);

            // Get the rosters that have a reference to the deleted user
            Iterator<String> usernames = provider.getUsernames(user.toBareJID());
            while (usernames.hasNext()) {
                username = usernames.next();
                try {
                    // Get the roster that has a reference to the deleted user
                    roster = getRoster(username);
                    // Remove the deleted user reference from this roster
                    roster.deleteRosterItem(user, false);
                }
                catch (SharedGroupException e) {
                    // Do nothing. We shouldn't have this exception since we disabled the checkings
                }
                catch (UserNotFoundException e) {
                    // Do nothing.
                }
            }
        }
        catch (UnsupportedOperationException e) {
            // Do nothing
        }
        catch (UserNotFoundException e) {
            // Do nothing.
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
     * @param username the username of the user to return his shared groups.
     * @return a collection with all the groups that the user may include in his roster.
     */
    public Collection<Group> getSharedGroups(String username) {
        Collection<Group> answer = new HashSet<Group>();
        Collection<Group> groups = GroupManager.getInstance().getSharedGroups();
        for (Group group : groups) {
            String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
            if ("onlyGroup".equals(showInRoster)) {
                if (group.isUser(username)) {
                    // The user belongs to the group so add the group to the answer
                    answer.add(group);
                }
                else {
                    // Check if the user belongs to a group that may see this group
                    Collection<Group> groupList = parseGroups(group.getProperties().get("sharedRoster.groupList"));
                    for (Group groupInList : groupList) {
                        if (groupInList.isUser(username)) {
                            answer.add(group);
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
     * Returns the list of shared groups whose visibility is public.
     *
     * @return the list of shared groups whose visibility is public.
     */
    public Collection<Group> getPublicSharedGroups() {
        Collection<Group> answer = new HashSet<Group>();
        Collection<Group> groups = GroupManager.getInstance().getSharedGroups();
        for (Group group : groups) {
            String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
            if ("everybody".equals(showInRoster)) {
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
        for (String groupName : parseGroupNames(groupNames)) {
            try {
                answer.add(GroupManager.getInstance().getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                // Do nothing. Silently ignore the invalid reference to the group
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
    private static Collection<String> parseGroupNames(String groupNames) {
        Collection<String> answer = new HashSet<String>();
        if (groupNames != null) {
            StringTokenizer tokenizer = new StringTokenizer(groupNames, ",");
            while (tokenizer.hasMoreTokens()) {
                answer.add(tokenizer.nextToken());
            }
        }
        return answer;
    }

    public void groupCreated(Group group, Map params) {
        //Do nothing
    }

    public void groupDeleting(Group group, Map params) {
        // Get group members
        Collection<JID> users = new HashSet<JID>(group.getMembers());
        users.addAll(group.getAdmins());
        // Get users whose roster will be updated
        Collection<JID> affectedUsers = getAffectedUsers(group);
        // Iterate on group members and update rosters of affected users
        for (JID deletedUser : users) {
            groupUserDeleted(group, affectedUsers, deletedUser);
        }
    }

    public void groupModified(Group group, Map params) {
        // Do nothing if no group property has been modified
        if ("propertyDeleted".equals(params.get("type"))) {
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
            // Get the users of the group
            Collection<JID> users = new HashSet<JID>(group.getMembers());
            users.addAll(group.getAdmins());
            // Get the users whose roster will be affected
            Collection<JID> affectedUsers = getAffectedUsers(group, originalValue,
                    group.getProperties().get("sharedRoster.groupList"));
            // Remove the group members from the affected rosters
            for (JID deletedUser : users) {
                groupUserDeleted(group, affectedUsers, deletedUser);
            }

            // Simulate that the group users has been added to the group. This will cause to push
            // roster items to the "affected" users for the group users
            for (JID user : users) {
                groupUserAdded(group, user);
            }
        }
        else if ("sharedRoster.groupList".equals(keyChanged)) {
            String currentValue = group.getProperties().get("sharedRoster.groupList");
            // Nothing has changed so do nothing.
            if (currentValue.equals(originalValue)) {
                return;
            }
            // Get the users of the group
            Collection<JID> users = new HashSet<JID>(group.getMembers());
            users.addAll(group.getAdmins());
            // Get the users whose roster will be affected
            Collection<JID> affectedUsers = getAffectedUsers(group,
                    group.getProperties().get("sharedRoster.showInRoster"), originalValue);
            // Remove the group members from the affected rosters
            for (JID deletedUser : users) {
                groupUserDeleted(group, affectedUsers, deletedUser);
            }

            // Simulate that the group users has been added to the group. This will cause to push
            // roster items to the "affected" users for the group users
            for (JID user : users) {
                groupUserAdded(group, user);
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
            Collection<JID> users = getAffectedUsers(group);
            // Iterate on all the affected users and update their rosters
            for (JID updatedUser : users) {
                // Get the roster to update.
                Roster roster = null;
                if (server.isLocal(updatedUser)) {
                    roster = rosterCache.get(updatedUser.getNode());
                }
                if (roster != null) {
                    // Update the roster with the new group display name
                    roster.shareGroupRenamed(users);
                }
            }
        }
    }

    @Override
	public void initialize(XMPPServer server) {
        super.initialize(server);
        this.server = server;
        this.routingTable = server.getRoutingTable();

        RosterEventDispatcher.addListener(new RosterEventListener() {
            public void rosterLoaded(Roster roster) {
                // Do nothing
            }

            public boolean addingContact(Roster roster, RosterItem item, boolean persistent) {
                // Do nothing
                return true;
            }

            public void contactAdded(Roster roster, RosterItem item) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                rosterCache.put(roster.getUsername(), roster);
            }

            public void contactUpdated(Roster roster, RosterItem item) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                rosterCache.put(roster.getUsername(), roster);
            }

            public void contactDeleted(Roster roster, RosterItem item) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                rosterCache.put(roster.getUsername(), roster);
            }
        });
    }

    /**
     * Returns true if the specified Group may be included in a user roster. The decision is made
     * based on the group properties that are configurable through the Admin Console.
     *
     * @param group the group to check if it may be considered a shared group.
     * @return true if the specified Group may be included in a user roster.
     */
    public static boolean isSharedGroup(Group group) {
        String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
        if ("onlyGroup".equals(showInRoster) || "everybody".equals(showInRoster)) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the specified Group may be seen by all users in the system. The decision
     * is made based on the group properties that are configurable through the Admin Console.
     *
     * @param group the group to check if it may be seen by all users in the system.
     * @return true if the specified Group may be seen by all users in the system.
     */
    public static boolean isPublicSharedGroup(Group group) {
        String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
        if ("everybody".equals(showInRoster)) {
            return true;
        }
        return false;
    }

    public void memberAdded(Group group, Map params) {
        JID addedUser = new JID((String) params.get("member"));
        // Do nothing if the user was an admin that became a member
        if (group.getAdmins().contains(addedUser)) {
            return;
        }
        if (!isSharedGroup(group)) {
            for (Group visibleGroup : getVisibleGroups(group)) {
                // Get the list of affected users
                Collection<JID> users = new HashSet<JID>(visibleGroup.getMembers());
                users.addAll(visibleGroup.getAdmins());
                groupUserAdded(visibleGroup, users, addedUser);
            }
        }
        else {
            groupUserAdded(group, addedUser);
        }
    }

    public void memberRemoved(Group group, Map params) {
        String member = (String) params.get("member");
        if (member == null) {
            return;
        }
        JID deletedUser = new JID(member);
        // Do nothing if the user is still an admin
        if (group.getAdmins().contains(deletedUser)) {
            return;
        }
        if (!isSharedGroup(group)) {
            for (Group visibleGroup : getVisibleGroups(group)) {
                // Get the list of affected users
                Collection<JID> users = new HashSet<JID>(visibleGroup.getMembers());
                users.addAll(visibleGroup.getAdmins());
                groupUserDeleted(visibleGroup, users, deletedUser);
            }
        }
        else {
            groupUserDeleted(group, deletedUser);
        }
    }

    public void adminAdded(Group group, Map params) {
        JID addedUser = new JID((String) params.get("admin"));
        // Do nothing if the user was a member that became an admin
        if (group.getMembers().contains(addedUser)) {
            return;
        }
        if (!isSharedGroup(group)) {
            for (Group visibleGroup : getVisibleGroups(group)) {
                // Get the list of affected users
                Collection<JID> users = new HashSet<JID>(visibleGroup.getMembers());
                users.addAll(visibleGroup.getAdmins());
                groupUserAdded(visibleGroup, users, addedUser);
            }
        }
        else {
            groupUserAdded(group, addedUser);
        }
    }

    public void adminRemoved(Group group, Map params) {
        JID deletedUser = new JID((String) params.get("admin"));
        // Do nothing if the user is still a member
        if (group.getMembers().contains(deletedUser)) {
            return;
        }
        // Do nothing if the group is not being shown in group members' rosters
        if (!isSharedGroup(group)) {
            for (Group visibleGroup : getVisibleGroups(group)) {
                // Get the list of affected users
                Collection<JID> users = new HashSet<JID>(visibleGroup.getMembers());
                users.addAll(visibleGroup.getAdmins());
                groupUserDeleted(visibleGroup, users, deletedUser);
            }
        }
        else {
            groupUserDeleted(group, deletedUser);
        }
    }

    /**
     * A new user has been created so members of public shared groups need to have
     * their rosters updated. Members of public shared groups need to have a roster
     * item with subscription FROM for the new user since the new user can see them.
     *
     * @param newUser the newly created user.
     * @param params event parameters.
     */
    public void userCreated(User newUser, Map<String,Object> params) {
        JID newUserJID = server.createJID(newUser.getUsername(), null);
        // Shared public groups that are public should have a presence subscription
        // of type FROM for the new user
        for (Group group : getPublicSharedGroups()) {
            // Get group members of public group
            Collection<JID> users = new HashSet<JID>(group.getMembers());
            users.addAll(group.getAdmins());
            // Update the roster of each group member to include a subscription of type FROM
            for (JID userToUpdate : users) {
                // Get the roster to update
                Roster roster = null;
                if (server.isLocal(userToUpdate)) {
                    // Check that the user exists, if not then continue with the next user
                    try {
                        UserManager.getInstance().getUser(userToUpdate.getNode());
                    }
                    catch (UserNotFoundException e) {
                        continue;
                    }
                    roster = rosterCache.get(userToUpdate.getNode());
                }
                // Only update rosters in memory
                if (roster != null) {
                    roster.addSharedUser(group, newUserJID);
                }
                if (!server.isLocal(userToUpdate)) {
                    // Susbcribe to the presence of the remote user. This is only necessary for
                    // remote users and may only work with remote users that **automatically**
                    // accept presence subscription requests
                    sendSubscribeRequest(newUserJID, userToUpdate, true);
                }
            }
        }
    }

    public void userDeleting(User user, Map<String,Object> params) {
        // Shared public groups that have a presence subscription of type FROM
        // for the deleted user should no longer have a reference to the deleted user
        JID userJID = server.createJID(user.getUsername(), null);
        // Shared public groups that are public should have a presence subscription
        // of type FROM for the new user
        for (Group group : getPublicSharedGroups()) {
            // Get group members of public group
            Collection<JID> users = new HashSet<JID>(group.getMembers());
            users.addAll(group.getAdmins());
            // Update the roster of each group member to include a subscription of type FROM
            for (JID userToUpdate : users) {
                // Get the roster to update
                Roster roster = null;
                if (server.isLocal(userToUpdate)) {
                    // Check that the user exists, if not then continue with the next user
                    try {
                        UserManager.getInstance().getUser(userToUpdate.getNode());
                    }
                    catch (UserNotFoundException e) {
                        continue;
                    }
                    roster = rosterCache.get(userToUpdate.getNode());
                }
                // Only update rosters in memory
                if (roster != null) {
                    roster.deleteSharedUser(group, userJID);
                }
                if (!server.isLocal(userToUpdate)) {
                    // Unsusbcribe from the presence of the remote user. This is only necessary for
                    // remote users and may only work with remote users that **automatically**
                    // accept presence subscription requests
                    sendSubscribeRequest(userJID, userToUpdate, false);
                }
            }
        }

        deleteRoster(userJID);
    }

    public void userModified(User user, Map<String,Object> params) {
        //Do nothing
    }

    /**
     * Notification that a Group user has been added. Update the group users' roster accordingly.
     *
     * @param group the group where the user was added.
     * @param addedUser the username of the user that has been added to the group.
     */
    private void groupUserAdded(Group group, JID addedUser) {
        groupUserAdded(group, getAffectedUsers(group), addedUser);
    }

    /**
     * Notification that a Group user has been added. Update the group users' roster accordingly.
     *
     * @param group the group where the user was added.
     * @param users the users to update their rosters
     * @param addedUser the username of the user that has been added to the group.
     */
    private void groupUserAdded(Group group, Collection<JID> users, JID addedUser) {
        // Get the roster of the added user.
        Roster addedUserRoster = null;
        if (server.isLocal(addedUser)) {
            addedUserRoster = rosterCache.get(addedUser.getNode());
        }

        // Iterate on all the affected users and update their rosters
        for (JID userToUpdate : users) {
            if (!addedUser.equals(userToUpdate)) {
                // Get the roster to update
                Roster roster = null;
                if (server.isLocal(userToUpdate)) {
                    // Check that the user exists, if not then continue with the next user
                    try {
                        UserManager.getInstance().getUser(userToUpdate.getNode());
                    }
                    catch (UserNotFoundException e) {
                        continue;
                    }
                    roster = rosterCache.get(userToUpdate.getNode());
                }
                // Only update rosters in memory
                if (roster != null) {
                    roster.addSharedUser(group, addedUser);
                }
                // Check if the roster is still not in memory
                if (addedUserRoster == null && server.isLocal(addedUser)) {
                    addedUserRoster =
                            rosterCache.get(addedUser.getNode());
                }
                // Update the roster of the newly added group user.
                if (addedUserRoster != null) {
                    Collection<Group> groups = GroupManager.getInstance().getGroups(userToUpdate);
                    addedUserRoster.addSharedUser(userToUpdate, groups, group);
                }
                if (!server.isLocal(addedUser)) {
                    // Susbcribe to the presence of the remote user. This is only necessary for
                    // remote users and may only work with remote users that **automatically**
                    // accept presence subscription requests
                    sendSubscribeRequest(userToUpdate, addedUser, true);
                }
                if (!server.isLocal(userToUpdate)) {
                    // Susbcribe to the presence of the remote user. This is only necessary for
                    // remote users and may only work with remote users that **automatically**
                    // accept presence subscription requests
                    sendSubscribeRequest(addedUser, userToUpdate, true);
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
    private void groupUserDeleted(Group group, JID deletedUser) {
        groupUserDeleted(group, getAffectedUsers(group), deletedUser);
    }

    /**
     * Notification that a Group user has been deleted. Update the group users' roster accordingly.
     *
     * @param group the group from where the user was deleted.
     * @param users the users to update their rosters
     * @param deletedUser the username of the user that has been deleted from the group.
     */
    private void groupUserDeleted(Group group, Collection<JID> users, JID deletedUser) {
        // Get the roster of the deleted user.
        Roster deletedUserRoster = null;
        if (server.isLocal(deletedUser)) {
            deletedUserRoster = rosterCache.get(deletedUser.getNode());
        }

        // Iterate on all the affected users and update their rosters
        for (JID userToUpdate : users) {
            // Get the roster to update
            Roster roster = null;
            if (server.isLocal(userToUpdate)) {
                // Check that the user exists, if not then continue with the next user
                try {
                    UserManager.getInstance().getUser(userToUpdate.getNode());
                }
                catch (UserNotFoundException e) {
                    continue;
                }
                roster = rosterCache.get(userToUpdate.getNode());
            }
            // Only update rosters in memory
            if (roster != null) {
                roster.deleteSharedUser(group, deletedUser);
            }
            // Check if the roster is still not in memory
            if (deletedUserRoster == null && server.isLocal(deletedUser)) {
                deletedUserRoster =
                        rosterCache.get(deletedUser.getNode());
            }
            // Update the roster of the newly deleted group user.
            if (deletedUserRoster != null) {
                deletedUserRoster.deleteSharedUser(userToUpdate, group);
            }
            if (!server.isLocal(deletedUser)) {
                // Unsusbcribe from the presence of the remote user. This is only necessary for
                // remote users and may only work with remote users that **automatically**
                // accept presence subscription requests
                sendSubscribeRequest(userToUpdate, deletedUser, false);
            }
            if (!server.isLocal(userToUpdate)) {
                // Unsusbcribe from the presence of the remote user. This is only necessary for
                // remote users and may only work with remote users that **automatically**
                // accept presence subscription requests
                sendSubscribeRequest(deletedUser, userToUpdate, false);
            }
        }
    }

    private void sendSubscribeRequest(JID sender, JID recipient, boolean isSubscribe) {
        Presence presence = new Presence();
        presence.setFrom(sender);
        presence.setTo(recipient);
        if (isSubscribe) {
            presence.setType(Presence.Type.subscribe);
        }
        else {
            presence.setType(Presence.Type.unsubscribe);
        }
        routingTable.routePacket(recipient, presence, false);
    }

    private Collection<Group> getVisibleGroups(Group groupToCheck) {
        Collection<Group> answer = new HashSet<Group>();
        Collection<Group> groups = GroupManager.getInstance().getSharedGroups();
        for (Group group : groups) {
            if (group.equals(groupToCheck)) {
                continue;
            }
            String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
            if ("onlyGroup".equals(showInRoster)) {
                // Check if the user belongs to a group that may see this group
                Collection<String> groupList =
                        parseGroupNames(group.getProperties().get("sharedRoster.groupList"));
                if (groupList.contains(groupToCheck.getName())) {
                    answer.add(group);
                }
            }
            else if ("everybody".equals(showInRoster)) {
                answer.add(group);
            }
        }
        return answer;
    }

    /**
     * Returns true if a given group is visible to a given user. That means, if the user can
     * see the group in his roster.
     *
     * @param group the group to check if the user can see.
     * @param user the JID of the user to check if he may see the group.
     * @return true if a given group is visible to a given user.
     */
    public boolean isGroupVisible(Group group, JID user) {
        String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
        if ("everybody".equals(showInRoster)) {
            return true;
        }
        else if ("onlyGroup".equals(showInRoster)) {
            if (group.isUser(user)) {
                 return true;
            }
            // Check if the user belongs to a group that may see this group
            Collection<Group> groupList = parseGroups(group.getProperties().get(
                    "sharedRoster.groupList"));
            for (Group groupInList : groupList) {
                if (groupInList.isUser(user)) {
                    return true;
                }
            }
        }
        return false;
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
    private Collection<JID> getAffectedUsers(Group group) {
        return getAffectedUsers(group, group.getProperties().get("sharedRoster.showInRoster"),
                group.getProperties().get("sharedRoster.groupList"));
    }

    /**
     * This method is similar to {@link #getAffectedUsers(Group)} except that it receives
     * some group properties. The group properties are passed as parameters since the called of this
     * method may want to obtain the related users of the group based in some properties values.
     *
     * This is useful when the group is being edited and some properties has changed and we need to
     * obtain the related users of the group based on the previous group state.
     */
    private Collection<JID> getAffectedUsers(Group group, String showInRoster, String groupNames) {
        // Answer an empty collection if the group is not being shown in users' rosters
        if (!"onlyGroup".equals(showInRoster) && !"everybody".equals(showInRoster)) {
            return new ArrayList<JID>();
        }
        // Add the users of the group
        Collection<JID> users = new HashSet<JID>(group.getMembers());
        users.addAll(group.getAdmins());
        // Check if anyone can see this shared group
        if ("everybody".equals(showInRoster)) {
            // Add all users in the system
            for (String username : UserManager.getInstance().getUsernames()) {
                users.add(server.createJID(username, null, true));
            }
            // Add all logged users. We don't need to add all users in the system since only the
            // logged ones will be affected.
            //users.addAll(SessionManager.getInstance().getSessionUsers());
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

    Collection<JID> getSharedUsersForRoster(Group group, Roster roster) {
        String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
        String groupNames = group.getProperties().get("sharedRoster.groupList");

        // Answer an empty collection if the group is not being shown in users' rosters
        if (!"onlyGroup".equals(showInRoster) && !"everybody".equals(showInRoster)) {
            return new ArrayList<JID>();
        }

        // Add the users of the group
        Collection<JID> users = new HashSet<JID>(group.getMembers());
        users.addAll(group.getAdmins());

        // If the user of the roster belongs to the shared group then we should return
        // users that need to be in the roster with subscription "from"
        if (group.isUser(roster.getUsername())) {
            // Check if anyone can see this shared group
            if ("everybody".equals(showInRoster)) {
                // Add all users in the system
                for (String username : UserManager.getInstance().getUsernames()) {
                    users.add(server.createJID(username, null, true));
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
        }
        return users;
    }

    /**
     * Returns true if a group in the first collection may mutually see a group of the
     * second collection. More precisely, return true if both collections contain a public
     * group (i.e. anybody can see the group) or if both collection have a group that may see
     * each other and the users are members of those groups or if one group is public and the
     * other group allowed the public group to see it.
     *
     * @param user the name of the user associated to the first collection of groups. This is always a local user.
     * @param groups a collection of groups to check against the other collection of groups.
     * @param otherUser the JID of the user associated to the second collection of groups.
     * @param otherGroups the other collection of groups to check against the first collection.
     * @return true if a group in the first collection may mutually see a group of the
     *         second collection.
     */
    boolean hasMutualVisibility(String user, Collection<Group> groups, JID otherUser,
            Collection<Group> otherGroups) {
        for (Group group : groups) {
            for (Group otherGroup : otherGroups) {
                // Skip this groups if the users are not group users of the groups
                if (!group.isUser(user) || !otherGroup.isUser(otherUser)) {
                    continue;
                }
                if (group.equals(otherGroup)) {
                     return true;
                }
                String showInRoster = group.getProperties().get("sharedRoster.showInRoster");
                String otherShowInRoster = otherGroup.getProperties().get("sharedRoster.showInRoster");
                // Return true if both groups are public groups (i.e. anybody can see them)
                if ("everybody".equals(showInRoster) && "everybody".equals(otherShowInRoster)) {
                    return true;
                }
                else if ("onlyGroup".equals(showInRoster) && "onlyGroup".equals(otherShowInRoster)) {
                    String groupNames = group.getProperties().get("sharedRoster.groupList");
                    String otherGroupNames = otherGroup.getProperties().get("sharedRoster.groupList");
                    // Return true if each group may see the other group
                    if (groupNames != null && otherGroupNames != null) {
                        if (groupNames.contains(otherGroup.getName()) &&
                                otherGroupNames.contains(group.getName())) {
                            return true;
                        }
                        // Check if each shared group can be seen by a group where each user belongs
                        Collection<Group> groupList = parseGroups(groupNames);
                        Collection<Group> otherGroupList = parseGroups(otherGroupNames);
                        for (Group groupName : groupList) {
                            if (groupName.isUser(otherUser)) {
                                for (Group otherGroupName : otherGroupList) {
                                    if (otherGroupName.isUser(user)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
                else if ("everybody".equals(showInRoster) && "onlyGroup".equals(otherShowInRoster)) {
                    // Return true if one group is public and the other group allowed the public
                    // group to see him
                    String otherGroupNames = otherGroup.getProperties().get("sharedRoster.groupList");
                    if (otherGroupNames != null && otherGroupNames.contains(group.getName())) {
                            return true;
                    }
                }
                else if ("onlyGroup".equals(showInRoster) && "everybody".equals(otherShowInRoster)) {
                    // Return true if one group is public and the other group allowed the public
                    // group to see him
                    String groupNames = group.getProperties().get("sharedRoster.groupList");
                    // Return true if each group may see the other group
                    if (groupNames != null && groupNames.contains(otherGroup.getName())) {
                            return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
	public void start() throws IllegalStateException {
        super.start();
        // Add this module as a user event listener so we can update
        // rosters when users are created or deleted
        UserEventDispatcher.addListener(this);
        // Add the new instance as a listener of group events
        GroupEventDispatcher.addListener(this);
    }

    @Override
	public void stop() {
        super.stop();
        // Remove this module as a user event listener
        UserEventDispatcher.removeListener(this);
        // Remove this module as a listener of group events
        GroupEventDispatcher.removeListener(this);
    }

    public static RosterItemProvider getRosterItemProvider() {
        return XMPPServer.getInstance().getRosterManager().provider;
    }

    private void initProvider() {
        JiveGlobals.migrateProperty("provider.roster.className");
        String className = JiveGlobals.getProperty("provider.roster.className",
                "org.jivesoftware.openfire.roster.DefaultRosterItemProvider");

        if (provider == null || !className.equals(provider.getClass().getName())) {
            try {
                Class c = ClassUtils.forName(className);
                provider = (RosterItemProvider) c.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading roster provider: " + className, e);
                provider = new DefaultRosterItemProvider();
            }
        }

    }

}
