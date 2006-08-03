/**
 * $RCSfile$
 * $Revision: 3117 $
 * $Date: 2005-11-25 22:57:29 -0300 (Fri, 25 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.group;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.event.GroupEventDispatcher;
import org.jivesoftware.wildfire.event.GroupEventListener;
import org.jivesoftware.wildfire.user.User;
import org.xmpp.packet.JID;

import java.util.*;

/**
 * Manages groups.
 *
 * @see Group
 * @author Matt Tucker
 */
public class GroupManager {

    Cache<String, Group> groupCache;
    Cache<String, Collection<Group>> userGroupCache;
    // Holds the cache key for the global group in the users groups cache
    private final String globalGroupKey = "ALL GROUPS";
    // Holds the cache key for the shared groups in the users groups cache
    private final String sharedGroupsKey = "SHARED GROUPS";
    private GroupProvider provider;

    private static GroupManager instance = new GroupManager();

    /**
     * Returns a singleton instance of GroupManager.
     *
     * @return a GroupManager instance.
     */
    public static GroupManager getInstance() {
        return instance;
    }

    private GroupManager() {
        // Initialize caches.
        groupCache = CacheManager.initializeCache("Group", "group", 512 * 1024);

        // A cache for all groups and groups related to a particular user
        String cacheName = "User Group Cache";
        CacheManager.initializeCache(cacheName, "userGroup", 512 * 1024, 1000 * 60 * 60 * 3);
        userGroupCache = CacheManager.getCache(cacheName);

        // Load a group provider.
        String className = JiveGlobals.getXMLProperty("provider.group.className",
                "org.jivesoftware.wildfire.group.DefaultGroupProvider");
        try {
            Class c = ClassUtils.forName(className);
            provider = (GroupProvider) c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading group provider: " + className, e);
            provider = new DefaultGroupProvider();
        }

        GroupEventDispatcher.addListener(new GroupEventListener() {
            public void groupCreated(Group group, Map params) {
                userGroupCache.clear();
            }

            public void groupDeleting(Group group, Map params) {
                userGroupCache.clear();
            }

            public void groupModified(Group group, Map params) {
                /* Ignore */
            }

            public void memberAdded(Group group, Map params) {
                userGroupCache.clear();
            }

            public void memberRemoved(Group group, Map params) {
                userGroupCache.clear();
            }

            public void adminAdded(Group group, Map params) {
                userGroupCache.clear();
            }

            public void adminRemoved(Group group, Map params) {
                userGroupCache.clear();
            }
        });
    }

    /**
     * Factory method for creating a new Group. A unique name is the only required field.
     *
     * @param name the new and unique name for the group.
     * @return a new Group.
     * @throws GroupAlreadyExistsException if the group name already exists in the system.
     */
    public Group createGroup(String name) throws GroupAlreadyExistsException {
        synchronized (name.intern()) {
            Group newGroup;
            try {
                getGroup(name);
                // The group already exists since now exception, so:
                throw new GroupAlreadyExistsException();
            }
            catch (GroupNotFoundException unfe) {
                // The group doesn't already exist so we can create a new group
                newGroup = provider.createGroup(name);
                groupCache.put(name, newGroup);

                // Fire event.
                GroupEventDispatcher.dispatchEvent(newGroup,
                        GroupEventDispatcher.EventType.group_created, Collections.emptyMap());
            }
            return newGroup;
        }
    }

    /**
     * Returns a Group by name.
     *
     * @param name The name of the group to retrieve
     * @return The group corresponding to that name
     * @throws GroupNotFoundException if the group does not exist.
     */
    public Group getGroup(String name) throws GroupNotFoundException {
        Group group = groupCache.get(name);
        // If ID wan't found in cache, load it up and put it there.
        if (group == null) {
            synchronized (name.intern()) {
                group = groupCache.get(name);
                // If ID wan't found in cache, load it up and put it there.
                if (group == null) {
                    group = provider.getGroup(name);
                    groupCache.put(name, group);
                }
            }
        }
        return group;
    }

    /**
     * Deletes a group from the system.
     *
     * @param group the group to delete.
     */
    public void deleteGroup(Group group) {
        // Fire event.
        GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.group_deleting,
                Collections.emptyMap());

        // Delete the group.
        provider.deleteGroup(group.getName());

        // Expire all relevant caches.
        groupCache.remove(group.getName());
    }

    /**
     * Deletes a user from all the groups where he/she belongs. The most probable cause
     * for this request is that the user has been deleted from the system.
     *
     * TODO: remove this method and use events isntead.
     *
     * @param user the deleted user from the system.
     */
    public void deleteUser(User user) {
        JID userJID = XMPPServer.getInstance().createJID(user.getUsername(), null);
        for (Group group : getGroups(userJID)) {
            if (group.getAdmins().contains(userJID)) {
                group.getAdmins().remove(userJID);
            }
            else {
                group.getMembers().remove(userJID);
            }
        }
        userGroupCache.clear();
    }

    /**
     * Returns the total number of groups in the system.
     *
     * @return the total number of groups.
     */
    public int getGroupCount() {
        Collection<Group> groups = userGroupCache.get(globalGroupKey);
        if(groups == null) {
            return provider.getGroupCount();
        }
        return groups.size();
    }

    /**
     * Returns an unmodifiable Collection of all groups in the system.
     *
     * @return an unmodifiable Collection of all groups.
     */
    public Collection<Group> getGroups() {
        synchronized (globalGroupKey) {
            Collection<Group> groups = userGroupCache.get(globalGroupKey);
            if (groups == null) {
                groups = provider.getGroups();
                // Add to cache and ensure correct identity
                groups = cacheAndEnsureIdentity(groups);
                userGroupCache.put(globalGroupKey, groups);
            }
            return groups;
        }
    }

    /**
     * Returns an unmodifiable Collection of all shared groups in the system.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getSharedGroups() {
        synchronized (sharedGroupsKey) {
            Collection<Group> groups = userGroupCache.get(sharedGroupsKey);
            if (groups == null) {
                Set<String> groupsNames = Group.getSharedGroupsNames();
                groups = provider.getGroups(groupsNames);
                // Add to cache and ensure correct identity
                groups = cacheAndEnsureIdentity(groups);
                userGroupCache.put(sharedGroupsKey, groups);
            }
            return groups;
        }
    }

    /**
     * Returns an iterator for all groups according to a filter.
     * <p/>
     * This is useful to support
     * pagination in a GUI where you may only want to display a certain
     * number of results per page. It is possible that the
     * number of results returned will be less than that specified by
     * numResults if numResults is greater than the number of records left in
     * the system to display.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return an Iterator for all groups in the specified range.
     */
    public Collection<Group> getGroups(int startIndex, int numResults) {
        Collection<Group> groups = provider.getGroups(startIndex, numResults);
        // Add to cache and ensure correct identity
        groups = cacheAndEnsureIdentity(groups);
        return groups;
    }

    /**
     * Returns an iterator for all groups that the User is a member of.
     *
     * @param user the user.
     * @return all groups the user belongs to.
     */
    public Collection<Group> getGroups(User user) {
        return getGroups(XMPPServer.getInstance().createJID(user.getUsername(), null));
    }

    /**
     * Returns an iterator for all groups that the entity with the specified JID is a member of.
     *
     * @param user the JID of the entity to get a list of groups for.
     * @return all groups that an entity belongs to.
     */
    public Collection<Group> getGroups(JID user) {
        String userID = user.toString();
        synchronized (userID.intern()) {
            Collection<Group> groups = userGroupCache.get(userID);
            if (groups == null) {
                groups = provider.getGroups(user);
                // Add to cache and ensure correct identity
                groups = cacheAndEnsureIdentity(groups);
                userGroupCache.put(userID, groups);
            }
            return groups;
        }
    }

    /**
     * Returns true if groups are read-only.
     *
     * @return true if groups are read-only.
     */
    public boolean isReadOnly() {
        return provider.isReadOnly();
    }

    /**
     * Returns true if searching for groups is supported.
     *
     * @return true if searching for groups are supported.
     */
    public boolean isSearchSupported() {
        return provider.isSearchSupported();
    }

    /**
     * Returns the groups that match the search. The search is over group names and
     * implicitly uses wildcard matching (although the exact search semantics are left
     * up to each provider implementation). For example, a search for "HR" should match
     * the groups "HR", "HR Department", and "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @return all groups that match the search.
     */
    public Collection<Group> search(String query) {
        return provider.search(query);
    }

    /**
     * Returns the groups that match the search given a start index and desired number
     * of results. The search is over group names and implicitly uses wildcard matching
     * (although the exact search semantics are left up to each provider implementation).
     * For example, a search for "HR" should match the groups "HR", "HR Department", and
     * "The HR People".<p>
     *
     * Before searching or showing a search UI, use the {@link #isSearchSupported} method
     * to ensure that searching is supported.
     *
     * @param query the search string for group names.
     * @return all groups that match the search.
     */
    public Collection<Group> search(String query, int startIndex, int numResults) {
        return provider.search(query, startIndex, numResults);
    }

    /**
     * Returns the configured group provider. Note that this method has special access
     * privileges since only a few certain classes need to access the provider directly.
     *
     * @return the group provider.
     */
    GroupProvider getProvider() {
        return provider;
    }

    /**
     * Caches groups present in the specified collection that are not already cached and
     * ensures correct identity of already cached groups.
     *
     * @param groups loaded groups from the backend store.
     * @return a list containing the correct and valid groups (i.e. ensuring identity).
     */
    private Collection<Group> cacheAndEnsureIdentity(Collection<Group> groups) {
        Collection<Group> answer = new ArrayList<Group>(groups.size());
        for (Group group : groups) {
            synchronized (group.getName().intern()) {
                Group existingGroup = groupCache.get(group.getName());
                if (existingGroup == null) {
                    // Add loaded group to the cache
                    groupCache.put(group.getName(), group);
                    answer.add(group);
                }
                else {
                    // Replace loaded group with the cached one to ensure correct identity
                    answer.add(existingGroup);
                }
            }
        }
        return answer;
    }
}