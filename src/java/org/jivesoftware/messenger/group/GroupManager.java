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

package org.jivesoftware.messenger.group;

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.JiveGlobals;

import java.util.Collection;
import java.util.ArrayList;

/**
 * Manages groups.
 *
 * @see Group
 * @author Matt Tucker
 */
public class GroupManager {

    Cache groupCache;
    Cache groupMemberCache;
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
        CacheManager.initializeCache("group", 128 * 1024);
        CacheManager.initializeCache("group member", 32 * 1024);
        groupCache = CacheManager.getCache("group");
        groupMemberCache = CacheManager.getCache("group member");
        // Load a group provider.
        String className = JiveGlobals.getXMLProperty("provider.group.className",
                "org.jivesoftware.messenger.group.DefaultGroupProvider");
        try {
            Class c = ClassUtils.forName(className);
            provider = (GroupProvider)c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading group provider: " + className, e);
            provider = new DefaultGroupProvider();
        }
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
            Group newGroup = null;
            try {
                getGroup(name);
                // The group already exists since now exception, so:
                throw new GroupAlreadyExistsException();
            }
            catch (GroupNotFoundException unfe) {
                // The group doesn't already exist so we can create a new group
                newGroup = provider.createGroup(name);
                groupCache.put(name, newGroup);
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
        Group group = (Group)groupCache.get(name);
        // If ID wan't found in cache, load it up and put it there.
        if (group == null) {
            group = provider.getGroup(name);
            groupCache.put(name, group);
        }
        return group;
    }

    /**
     * Deletes a group from the system.
     *
     * @param group the group to delete.
     */
    public void deleteGroup(Group group) {
        // Make a copy of the group members.
        Collection<String> members = new ArrayList<String>(group.getMembers());

        // Delete the group.
        provider.deleteGroup(group.getName());

        // Expire all relevant caches.
        groupCache.remove(group.getName());
        // Remove entries in the group membership cache for all members of the group.
        for (String username : members) {
            groupMemberCache.remove("userGroups-" + username);
        }
    }

    /**
     * Returns the total number of groups in the system.
     *
     * @return the total number of groups.
     */
    public int getGroupCount() {
        return provider.getGroupCount();
    }

    /**
     * Returns an unmodifiable Collection of all groups in the system.
     *
     * @return an unmodifiable Collection of all groups.
     */
    public Collection<Group> getGroups() {
        return provider.getGroups();
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
        return provider.getGroups(startIndex, numResults);
    }

    /**
     * Returns an iterator for all groups that a user is a member of.
     *
     * @param user the user to get a list of groups for.
     * @return all groups that a user belongs to.
     */
    public Collection<Group> getGroups(User user) {
        return provider.getGroups(user);
    }
}