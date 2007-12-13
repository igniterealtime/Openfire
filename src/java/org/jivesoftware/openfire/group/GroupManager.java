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

package org.jivesoftware.openfire.group;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Manages groups.
 *
 * @see Group
 * @author Matt Tucker
 */
public class GroupManager {

    private static final class GroupManagerContainer {
        private static final GroupManager instance = new GroupManager();
    }

    private static final String GROUP_COUNT_KEY = "GROUP_COUNT";
    private static final String SHARED_GROUPS_KEY = "SHARED_GROUPS";
    private static final String GROUP_NAMES_KEY = "GROUP_NAMES";

    /**
     * Returns a singleton instance of GroupManager.
     *
     * @return a GroupManager instance.
     */
    public static GroupManager getInstance() {
        return GroupManagerContainer.instance;
    }

    Cache<String, Group> groupCache;
    Cache<String, Object> groupMetaCache;
    private GroupProvider provider;

    private GroupManager() {
        // Initialize caches.
        groupCache = CacheFactory.createCache("Group");

        // A cache for meta-data around groups: count, group names, groups associated with
        // a particular user
        groupMetaCache = CacheFactory.createCache("Group Metadata Cache");

        // Load a group provider.
        String className = JiveGlobals.getXMLProperty("provider.group.className",
                "org.jivesoftware.openfire.group.DefaultGroupProvider");
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
                groupMetaCache.clear();
            }

            public void groupDeleting(Group group, Map params) {
                groupMetaCache.clear();
            }

            public void groupModified(Group group, Map params) {
                String type = (String)params.get("type");
                // If shared group settings changed, expire the cache.
                if (type != null && (type.equals("propertyModified") ||
                        type.equals("propertyDeleted") || type.equals("propertyAdded")))
                {
                    if (params.get("propertyKey") != null &&
                            params.get("propertyKey").equals("sharedRoster.showInRoster"))
                    {
                        groupMetaCache.clear();
                    }
                }
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }

            public void memberAdded(Group group, Map params) {
                groupMetaCache.clear();
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }

            public void memberRemoved(Group group, Map params) {
                groupMetaCache.clear();
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }

            public void adminAdded(Group group, Map params) {
                groupMetaCache.clear();
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }

            public void adminRemoved(Group group, Map params) {
                groupMetaCache.clear();
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }
        });

        // Pre-load shared groups. This will provide a faster response
        // time to the first client that logs in.
        Runnable task = new Runnable() {
            public void run() {
                Collection<Group> groups = getSharedGroups();
                // Load each group into cache.
                for (Group group : groups) {
                    // Load each user in the group into cache.
                    for (JID jid : group.getMembers()) {
                        try {
                            if (XMPPServer.getInstance().isLocal(jid)) {
                                UserManager.getInstance().getUser(jid.getNode());
                            }
                        }
                        catch (UserNotFoundException unfe) {
                            // Ignore.
                        }
                    }
                }
            }
        };
        TaskEngine.getInstance().submit(task);
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
                // Update caches.
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
                // If group wan't found in cache, load it up and put it there.
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

        // Expire cache.
        groupCache.remove(group.getName());
    }

    /**
     * Deletes a user from all the groups where he/she belongs. The most probable cause
     * for this request is that the user has been deleted from the system.
     *
     * TODO: remove this method and use events instead.
     *
     * @param user the deleted user from the system.
     */
    public void deleteUser(User user) {
        JID userJID = XMPPServer.getInstance().createJID(user.getUsername(), null);
        for (Group group : getGroups(userJID)) {
            if (group.getAdmins().contains(userJID)) {
                if (group.getAdmins().remove(userJID)) {
                    // Remove the group from cache.
                    groupCache.remove(group.getName());
                }
            }
            else {
                if (group.getMembers().remove(userJID)) {
                    // Remove the group from cache.
                    groupCache.remove(group.getName());
                }
            }
        }
    }

    /**
     * Returns the total number of groups in the system.
     *
     * @return the total number of groups.
     */
    public int getGroupCount() {
        Integer count = (Integer)groupMetaCache.get(GROUP_COUNT_KEY);
        if (count == null) {
            synchronized(GROUP_COUNT_KEY.intern()) {
                count = (Integer)groupMetaCache.get(GROUP_COUNT_KEY);
                if (count == null) {
                    count = provider.getGroupCount();
                    groupMetaCache.put(GROUP_COUNT_KEY, count);
                }
            }
        }
        return count;
    }

    /**
     * Returns an unmodifiable Collection of all groups in the system.
     *
     * @return an unmodifiable Collection of all groups.
     */
    public Collection<Group> getGroups() {
        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(GROUP_NAMES_KEY);
        if (groupNames == null) {
            synchronized(GROUP_NAMES_KEY.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(GROUP_NAMES_KEY);
                if (groupNames == null) {
                    groupNames = provider.getGroupNames();
                    groupMetaCache.put(GROUP_NAMES_KEY, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }

    /**
     * Returns an unmodifiable Collection of all shared groups in the system.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getSharedGroups() {
        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(SHARED_GROUPS_KEY);
        if (groupNames == null) {
            synchronized(SHARED_GROUPS_KEY.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(SHARED_GROUPS_KEY);
                if (groupNames == null) {
                    groupNames = Group.getSharedGroupsNames();
                    groupMetaCache.put(SHARED_GROUPS_KEY, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }

    /**
     * Returns all groups given a start index and desired number of results. This is
     * useful to support pagination in a GUI where you may only want to display a certain
     * number of results per page. It is possible that the number of results returned will
     * be less than that specified by numResults if numResults is greater than the number
     * of records left in the system to display.
     *
     * @param startIndex start index in results.
     * @param numResults number of results to return.
     * @return an Iterator for all groups in the specified range.
     */
    public Collection<Group> getGroups(int startIndex, int numResults) {
        String key = GROUP_NAMES_KEY + startIndex + "," + numResults;

        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(key);
        if (groupNames == null) {
            synchronized(key.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(key);
                if (groupNames == null) {
                    groupNames = provider.getGroupNames(startIndex, numResults);
                    groupMetaCache.put(key, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }

    /**
     * Returns an iterator for all groups that the User is a member of.
     *
     * @param user the user.
     * @return all groups the user belongs to.
     */
    public Collection<Group> getGroups(User user) {
        return getGroups(XMPPServer.getInstance().createJID(user.getUsername(), null, true));
    }

    /**
     * Returns an iterator for all groups that the entity with the specified JID is a member of.
     *
     * @param user the JID of the entity to get a list of groups for.
     * @return all groups that an entity belongs to.
     */
    public Collection<Group> getGroups(JID user) {
        String key = user.toBareJID();

        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(key);
        if (groupNames == null) {
            synchronized(key.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(key);
                if (groupNames == null) {
                    groupNames = provider.getGroupNames(user);
                    groupMetaCache.put(key, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
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
        Collection<String> groupNames = provider.search(query);
        return new GroupCollection(groupNames);
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
        Collection<String> groupNames = provider.search(query, startIndex, numResults);
        return new GroupCollection(groupNames);
    }

    /**
     * Returns the configured group provider. Note that this method has special access
     * privileges since only a few certain classes need to access the provider directly.
     *
     * @return the group provider.
     */
    public GroupProvider getProvider() {
        return provider;
    }
}