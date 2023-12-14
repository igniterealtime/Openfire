/*
 * Copyright (C) 2004-2008 Jive Software. 2016-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.group;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.CacheableOptional;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages groups.
 *
 * @see Group
 * @author Matt Tucker
 */
public class GroupManager {

    public static final SystemProperty<Class> GROUP_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.group.className")
        .setBaseClass(GroupProvider.class)
        .setDefaultValue(DefaultGroupProvider.class)
        .addListener(GroupManager::initProvider)
        .setDynamic(true)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(GroupManager.class);

    private static GroupManager INSTANCE;

    private static final Interner<JID> userBasedMutex = Interners.newWeakInterner();
    private static final Interner<PagedGroupNameKey> pagedGroupNameKeyInterner = Interners.newWeakInterner();

    private static final String GROUP_COUNT_KEY = "GROUP_COUNT";
    private static final String GROUP_NAMES_KEY = "GROUP_NAMES";
    private static final String USER_GROUPS_KEY = "USER_GROUPS";

    private static final Object GROUP_COUNT_LOCK = new Object();
    private static final Object GROUP_NAMES_LOCK = new Object();
    private static final Object USER_GROUPS_LOCK = new Object();

    /**
     * Returns a singleton instance of GroupManager.
     *
     * @return a GroupManager instance.
     */
    public static synchronized GroupManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new GroupManager();
        }
        return INSTANCE;
    }

    private final Cache<String, CacheableOptional<Group>> groupCache;
    private final Cache<String, Serializable> groupMetaCache;
    private static GroupProvider provider;

    private GroupManager() {
        // Initialize caches.
        groupCache = CacheFactory.createCache("Group"); // TODO determine if this works in a cluster (should this be a local cache?)

        // A cache for meta-data around groups: count, group names, groups associated with
        // a particular user
        groupMetaCache = CacheFactory.createCache("Group Metadata Cache"); // TODO determine if this works in a cluster (should this be a local cache?)

        initProvider(GROUP_PROVIDER.getValue());

        UserEventDispatcher.addListener(new UserEventListener() {
            @Override
            public void userCreated(User user, Map<String, Object> params) {
                // ignore
            }

            @Override
            public void userDeleting(User user, Map<String, Object> params) {
                deleteUser(user);
            }

            @Override
            public void userModified(User user, Map<String, Object> params) {
                // ignore
            }
        });
    }

    private static void initProvider(final Class<? extends GroupProvider> clazz) {
        if (provider == null || !clazz.equals(provider.getClass())) {
            try {
                provider = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                Log.error("Error loading group provider: {}", clazz.getName(), e);
                provider = new DefaultGroupProvider();
            }
        }
    }

    /**
     * Factory method for creating a new Group. A unique name is the only required field.
     *
     * @param name the new and unique name for the group.
     * @return a new Group.
     * @throws GroupAlreadyExistsException if the group name already exists in the system.
     */
    public Group createGroup(String name) throws GroupAlreadyExistsException, GroupNameInvalidException {
        final Lock lock = groupCache.getLock(name);
        lock.lock();
        try {
            Group newGroup;
            try {
                getGroup(name);
                // The group already exists since now exception, so:
                throw new GroupAlreadyExistsException();
            }
            catch (GroupNotFoundException unfe) {
                // The group doesn't already exist, so we can create a new group
                newGroup = provider.createGroup(name);

                createGroupPostProcess(newGroup);
            }
            return newGroup;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the corresponding group if the given JID represents a group. 
     *
     * @param jid The JID for the group to retrieve
     * @return The group corresponding to the JID, or null if the JID does not represent a group
     * @throws GroupNotFoundException if the JID represents a group that does not exist
     */
    public Group getGroup(JID jid) throws GroupNotFoundException {
        JID groupJID = GroupJID.fromJID(jid);
        return (groupJID instanceof GroupJID) ? getGroup(((GroupJID)groupJID).getGroupName()) : null;
    }

    /**
     * Returns a Group by name.
     *
     * @param name The name of the group to retrieve
     * @return The group corresponding to that name
     * @throws GroupNotFoundException if the group does not exist.
     */
    public Group getGroup(String name) throws GroupNotFoundException {
        return getGroup(name, false);
    }

    /**
     * Returns a Group by name.
     *
     * @param name The name of the group to retrieve
     * @param forceLookup Invalidate the group cache for this group
     * @return The group corresponding to that name
     * @throws GroupNotFoundException if the group does not exist.
     */
    public Group getGroup(String name, boolean forceLookup) throws GroupNotFoundException {

        final CacheableOptional<Group> firstCachedGroup;
        if (forceLookup) {
            firstCachedGroup = null;
            groupCache.remove(name);
        } else {
            firstCachedGroup = groupCache.get(name);
        }

        if (firstCachedGroup != null) {
            return toGroup(name, firstCachedGroup);
        }

        final Lock lock = groupCache.getLock(name);
        lock.lock();
        try {
            final CacheableOptional<Group> secondCachedGroup = groupCache.get(name);
            if (secondCachedGroup != null) {
                return toGroup(name, secondCachedGroup);
            }

            try {
                final Group group = provider.getGroup(name);
                groupCache.put(name, CacheableOptional.of(group));
                return group;
            } catch (final GroupNotFoundException e) {
                groupCache.put(name, CacheableOptional.of(null));
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    private Group toGroup(final String name, final CacheableOptional<Group> coGroup) throws GroupNotFoundException {
        return coGroup
            .toOptional()
            .orElseThrow(() -> new GroupNotFoundException("Group with name " + name + " not found (cached)."));
    }

    /**
     * Deletes a group from the system.
     *
     * @param group the group to delete.
     */
    public void deleteGroup(Group group) throws GroupNotFoundException {
        // Fire the event that announces that a group will be deleted (prior to the fact).
        deleteGroupPreProcess(group);

        // Delete the group.
        provider.deleteGroup(group.getName());

        // Update caches.
        deleteGroupPostProcess(group);
    }

    /**
     * Deletes a user from all the groups where he/she belongs. The most probable cause
     * for this request is that the user has been deleted from the system.
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
        evictCachedUserForGroup(userJID.asBareJID());
    }

    /**
     * Returns the total number of groups in the system.
     *
     * @return the total number of groups.
     */
    public int getGroupCount() {
            Integer count = getGroupCountFromCache();
        if (count == null) {
            synchronized(GROUP_COUNT_LOCK) {
                count = getGroupCountFromCache();
                if (count == null) {
                    count = provider.getGroupCount();
                    saveGroupCountInCache(count);
                }
            }
        }
        return count;
    }

    /**
     * Returns an unmodifiable Collection of all groups in the system.
     * 
     * NOTE: Iterating through the resulting collection has the effect of loading
     * every group into memory. This may be an issue for large deployments. You
     * may call the size() method on the resulting collection to determine the best
     * approach to take before iterating over (and thus instantiating) the groups.
     *
     * @return an unmodifiable Collection of all groups.
     */
    public Collection<Group> getGroups() {
        HashSet<String> groupNames = getGroupNamesFromCache();
        if (groupNames == null) {
            synchronized(GROUP_NAMES_LOCK) {
                groupNames = getGroupNamesFromCache();
                if (groupNames == null) {
                    groupNames = new HashSet<>(provider.getGroupNames());
                    saveGroupNamesInCache(groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }

    /**
     * Returns an unmodifiable Collection of all shared groups in the system.
     * 
     * NOTE: Iterating through the resulting collection has the effect of loading all
     * shared groups into memory. This may be an issue for large deployments. You
     * may call the size() method on the resulting collection to determine the best
     * approach to take before iterating over (and thus instantiating) the groups.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getSharedGroups() {
        HashSet<String> groupNames;
        if (!provider.isSharingSupported()) {
            groupNames = new HashSet<>();
        } else {
            groupNames = new HashSet<>(provider.getSharedGroupNames());
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given userName.
     *
     * @param userName the user to check
     * @return an unmodifiable Collection of all shared groups for the given userName.
     */
    public Collection<Group> getSharedGroups(String userName) {
        HashSet<String> groupNames;
        if (!provider.isSharingSupported()) {
            groupNames = new HashSet<>();
        } else {
            // assume this is a local user
            groupNames = new HashSet<>(provider.getSharedGroupNames(new JID(userName,
                XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null)));
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given group name.
     *
     * @param groupToCheck The group to check
     * @return an unmodifiable Collection of all shared groups for the given group name.
     */
    public Collection<Group> getVisibleGroups(Group groupToCheck) {
        HashSet<String> groupNames;
        if (!provider.isSharingSupported()) {
            groupNames = new HashSet<>();
        } else {
            // Get all the public shared groups.
            groupNames = new HashSet<>(provider.getPublicSharedGroupNames());

            // Now get all visible groups to the given group.
            groupNames.addAll(provider.getVisibleGroupNames(groupToCheck.getName()));
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all public shared groups in the system.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getPublicSharedGroups() {
        HashSet<String> groupNames;
        if (!provider.isSharingSupported()) {
            groupNames = new HashSet<>();
        } else {
            groupNames = new HashSet<>(provider.getPublicSharedGroupNames());
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all groups in the system that
     * match given propValue for the specified propName.
     *
     * @param propName the property name to search for
     * @param propValue the property value to search for
     * @return an unmodifiable Collection of all matching groups.
     */
    public Collection<Group> search(String propName, String propValue) {
        Collection<String> groupsWithProps = provider.search(propName, propValue);
        return new GroupCollection(groupsWithProps);
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
        HashSet<String> groupNames = getPagedGroupNamesFromCache(startIndex, numResults);
        if (groupNames == null) {
            // synchronizing on interned string isn't great, but this value is deemed sufficiently unique for this to be safe here.
            synchronized (pagedGroupNameKeyInterner.intern(getPagedGroupNameKey(startIndex, numResults))) {
                groupNames = getPagedGroupNamesFromCache(startIndex, numResults);
                if (groupNames == null) {
                    groupNames = new HashSet<>(provider.getGroupNames(startIndex, numResults));
                    savePagedGroupNamesFromCache(groupNames, startIndex, numResults);
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
        HashSet<String> groupNames = getUserGroupsFromCache(user);
        if (groupNames == null) {
            synchronized (userBasedMutex.intern(user)) {
                groupNames = getUserGroupsFromCache(user);
                if (groupNames == null) {
                    groupNames = new HashSet<>(provider.getGroupNames(user));
                    saveUserGroupsInCache(user, groupNames);
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
     * @param startIndex the start index to retrieve the group list from
     * @param numResults the maximum number of results to return
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

    private void evictCachedUserForGroup(JID user) {
        if (user != null) {

            // remove cache for getGroups
            synchronized (USER_GROUPS_LOCK) {
                clearUserGroupsCache(user);
            }
        }
    }

    /**
     * Evict from cache all cached user entries that relate to the provided group.
     *
     * This method ignores group names for which a group cannot be found.
     * 
     * @param groupName The name of a group for which to evict cached user entries (cannot be null).
     */
    private void evictCachedUsersForGroup(String groupName)
    {
        try {
            evictCachedUsersForGroup( getGroup(groupName) );
        } catch ( GroupNotFoundException e ) {
            Log.debug("Unable to evict cached users for group '{}': this group does not exist.", groupName, e);
        }
    }

    /**
     * Evict from cache all cached user entries that relate to the provided group.
     *
     * @param group The group for which to evict cached user entries (cannot be null).
     */
    private void evictCachedUsersForGroup(Group group)
    {
        // Get all nested groups, removing any cyclic dependency.
        final Set<Group> groups = getSharedGroups( group );

        // Evict cached information for affected users.
        groups.forEach( g -> {
            g.getAdmins().forEach( jid -> evictCachedUserForGroup( jid.asBareJID()) );
            g.getMembers().forEach( jid -> evictCachedUserForGroup( jid.asBareJID()) );
        });

        // If any of the groups is shared with everybody, evict all cached groups.
        if ( groups.stream().anyMatch( g -> g.getSharedWith() == SharedGroupVisibility.everybody)) {
            evictCachedUserSharedGroups();
        }
    }

    /**
     * Find the unique set of groups with which the provided group is shared,
     * directly or indirectly. An indirect share is defined as a scenario where
     * the group is shared by a group that's shared with another group.
     *
     * @param group The group for which to return all groups that it is shared with (cannot be null).
     * @return A set of groups with which all members of 'group' are shared with (never null).
     */
    @Nonnull
    private Set<Group> getSharedGroups(@Nonnull final Group group) {
        final HashSet<Group> result = new HashSet<>();
        if (provider.isSharingSupported()) {
            if (group.getSharedWith() == SharedGroupVisibility.usersOfGroups) {
                final Set<String> groupNames = new HashSet<>(group.getSharedWithUsersInGroupNames());

                for ( String groupName : groupNames )
                {
                    try {
                        result.add(getGroup(groupName));
                    }
                    catch ( GroupNotFoundException e )
                    {
                        Log.debug("While iterating over subgroups of group '{}', an unrecognized spefgroup was found: '{}'", group.getName(), groupName, e);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Splits a comma-separated string of group name in a set of group names.
     * @param csv The comma-separated list. Cannot be null.
     * @return A set of group names.
     */
    protected static Set<String> splitGroupList( String csv )
    {
        final Set<String> result = new HashSet<>();
        final StringTokenizer tokenizer = new StringTokenizer(csv, ",\t\n\r\f");
        while ( tokenizer.hasMoreTokens() )
        {
            result.add(tokenizer.nextToken().trim());
        }

        return result;
    }

    private void evictCachedPaginatedGroupNames() {
        groupMetaCache.keySet().stream()
            .filter(key -> key.startsWith(GROUP_NAMES_KEY))
            .forEach(groupMetaCache::remove);
    }

    private void evictCachedUserSharedGroups() {
        groupMetaCache.keySet().stream()
            .filter(key -> key.startsWith(GROUP_NAMES_KEY))
            .forEach(groupMetaCache::remove);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a new group having been created.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * created. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that has been created.
     */
    public void createGroupPostProcess(@Nonnull final Group group)
    {
        // Adds default properties if they don't exist.
        if (group.getSharedWith() == null) {
            group.shareWithNobody();
        }

        // Update caches.
        clearGroupNameCache();
        clearGroupCountCache();
        evictCachedUsersForGroup(group);
        evictCachedPaginatedGroupNames();

        groupCache.put(group.getName(), CacheableOptional.of(group));

        // Fire event.
        GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.group_created, Collections.emptyMap());
    }

    /**
     * Dispatches events that reflect an existing group having been deleted.
     *
     * This method is intended to be invoked to update the internal state of GroupManager just before a group will be
     * deleted. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that is about to be removed.
     */
    public void deleteGroupPreProcess(@Nonnull final Group group)
    {
        // Fire event.
        GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.group_deleting, Collections.emptyMap());
    }

    /**
     * Updates the caches maintained by this manager to reflect an existing group having been deleted.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * deleted. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that has been deleted. Care should be taken to not apply changes to this instance, as it
     *              no longer refers to a valid, existing group.
     */
    public void deleteGroupPostProcess(@Nonnull final Group group)
    {
        // Add a no-hit to the cache.
        groupCache.put(group.getName(), CacheableOptional.of(null));
        clearGroupNameCache();
        clearGroupCountCache();
        evictCachedUsersForGroup(group);
        evictCachedPaginatedGroupNames();
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect an admin user having been added
     * to a group.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param admin The address of the admin that was added to the group.
     * @param wasMember Whether the admin was a member of the group (which are mutually exclusive roles) before the update.
     */
    public void adminAddedPostProcess(@Nonnull final Group group, @Nonnull final JID admin, final boolean wasMember)
    {
        evictCachedUserForGroup(admin);
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after an admin was added to it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Fire event.
        final Map<String, String> params = new HashMap<>();
        params.put("admin", admin.toString());
        if (wasMember) {
            params.put("member", admin.toString());
            GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.member_removed, params);
        }
        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.admin_added, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect an admin user having been
     * removed from a group.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param admin The address of the admin that was removed from the group.
     */
    public void adminRemovedPostProcess(@Nonnull final Group group, @Nonnull final JID admin)
    {
        evictCachedUserForGroup(admin);
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after an admin was removed from it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Fire event.
        final Map<String, String> params = new HashMap<>();
        params.put("admin", admin.toString());

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.admin_removed, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a member user having been added
     * to a group.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param member The address of the member that was added to the group.
     * @param wasAdmin Whether the member was an admin of the group (which are mutually exclusive roles) before the update.
     */
    public void memberAddedPostProcess(@Nonnull final Group group, @Nonnull final JID member, final boolean wasAdmin)
    {
        evictCachedUserForGroup(member);
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after a member was added to it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Fire event.
        final Map<String, String> params = new HashMap<>();
        params.put("member", member.toString());
        if (wasAdmin) {
            params.put("admin", member.toString());
            GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.admin_removed, params);
        }
        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.member_added, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a member user having been
     * removed from a group.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param member The address of the member that was removed from the group.
     */
    public void memberRemovedPostProcess(@Nonnull final Group group, @Nonnull final JID member)
    {
        evictCachedUserForGroup(member);
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after a member was removed from it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Fire event.
        final Map<String, String> params = new HashMap<>();
        params.put("member", member.toString());

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.member_removed, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having received a new
     * name.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param originalName The name of the group prior to the change.
     */
    public void renameGroupPostProcess(@Nonnull final Group group, @Nullable final String originalName)
    {
        // Clean up caches
        if (originalName != null) {
            groupCache.remove(originalName);
        }
        clearGroupNameCache();
        evictCachedUsersForGroup(group);
        evictCachedPaginatedGroupNames();

        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after it was renamed (original name: '{}'). This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), originalName, e);
            return;
        }

        // Fire event.
        final Map<String, Object> params = new HashMap<>();
        params.put("type", "nameModified");
        params.put("originalValue", originalName);
        params.put("originalJID", new GroupJID(originalName));

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having received a new
     * description.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param originalDescription The description of the group prior to the change.
     */
    public void redescribeGroupPostProcess(@Nonnull final Group group, @Nullable final String originalDescription)
    {
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after its description was changed. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Fire event.
        final Map<String, Object> params = new HashMap<>();
        params.put("type", "descriptionModified");
        params.put("originalValue", originalDescription);

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having received a new
     * property.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param key The key of the property that was added.
     */
    public void propertyAddedPostProcess(@Nonnull final Group group, @Nonnull final String key)
    {
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after a property (key: '{}') was added to it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), key, e);
            return;
        }
        propertyChangePostProcess(updatedGroup, key, null);

        // Fire event.
        final Map<String, Object> params = new HashMap<>();
        params.put("propertyKey", key);
        params.put("type", "propertyAdded");

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having received a
     * modification to one of its properties.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param key The key of the property that was modified.
     * @param originalValue The value of the property prior to the modification.
     */
    public void propertyModifiedPostProcess(@Nonnull final Group group, @Nonnull final String key, @Nonnull final String originalValue)
    {
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after a property (key: '{}') was modified. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), key, e);
            return;
        }
        propertyChangePostProcess(updatedGroup, key, originalValue);

        // Fire event.
        final Map<String, Object> params = new HashMap<>();
        params.put("propertyKey", key);
        params.put("type", "propertyModified");
        params.put("originalValue", originalValue);

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having one of its
     * properties removed.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param key The key of the property that was removed.
     * @param originalValue The value of the property prior to the removal.
     */
    public void propertyDeletedPostProcess(@Nonnull final Group group, @Nonnull final String key, @Nonnull final String originalValue)
    {
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after a property (key: '{}') was removed from it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), key, e);
            return;
        }
        propertyChangePostProcess(updatedGroup, key, originalValue);

        // Fire event.
        final Map<String, Object> params = new HashMap<>();
        params.put("propertyKey", key);
        params.put("type", "propertyDeleted");
        params.put("originalValue", originalValue);

        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, params);
    }

    /**
     * Updates the caches maintained by this manager and dispatches events that reflect a group having all of its
     * properties removed.
     *
     * This method is intended to be invoked to update the internal state of GroupManager after a group has been
     * updated. It should rarely be invoked by code that is not part of the GroupManager implementation.
     *
     * @param group The group that was modified.
     * @param originalProperties The properties of the group prior to the removal.
     */
    public void propertiesDeletedPostProcess(@Nonnull final Group group, @Nonnull final Map<String, String> originalProperties)
    {
        final Group updatedGroup;
        try {
            // By forcing the lookup, the cache will automatically receive the required refresh.
            updatedGroup = getGroup(group.getName(), true);
        } catch (GroupNotFoundException e) {
            Log.error("Group '{}' was not found after all its properties were removed from it. This is indicative of a bug in Openfire. Please consider reporting it.", group.getName(), e);
            return;
        }

        // Make sure that 'shared roster' changes are processed.
        for (final Map.Entry<String, String> entry : originalProperties.entrySet()) {
            GroupManager.getInstance().propertyChangePostProcess(group, entry.getKey(), entry.getValue());
        }

        // Fire event.
        final Map<String, Object> event = new HashMap<>();
        event.put("type", "propertyDeleted");
        event.put("propertyKey", "*");
        GroupEventDispatcher.dispatchEvent(updatedGroup, GroupEventDispatcher.EventType.group_modified, event);
    }

    // A generic method to process the effect of property changes to contact list sharing. Re-used by all property change post-processing methods.
    private void propertyChangePostProcess(final Group group, final String key, final String originalValue)
    {
        switch (key) {
            case Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY: {
                clearGroupNameCache();

                // Check to see if the definition of people to which the shared group is shared has changed
                final String newValue = group.getProperties().get(Group.SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY);
                if (!StringUtils.equals(originalValue, newValue)) {
                    if ("everybody".equals(originalValue) || "everybody".equals(newValue)) {
                        evictCachedUserSharedGroups();
                    }
                }
                break;
            }

            case Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY: {
                // Check to see if the list of groups to which the shared group is shared has changed
                final String newValue = group.getProperties().get(Group.SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY);

                if (!StringUtils.equals(originalValue, newValue)) {
                    evictCachedUsersForGroup(group);

                    // Also clear the cache for groups that have been removed from the shared list.
                    if (originalValue != null) {
                        final Set<String> newGroupNames = newValue == null ? new HashSet<>() : splitGroupList(newValue);
                        final Set<String> oldGroupNames = splitGroupList(originalValue);

                        // The 'new' group names are already handled by the evictCachedUserForGroup call above. No need to do that twice.
                        oldGroupNames.removeAll(newGroupNames);
                        oldGroupNames.forEach(this::evictCachedUsersForGroup);
                    }
                }
                break;
            }
        }
    }

    /*
        For reasons currently unclear, this class stores a number of different objects in the groupMetaCache. To
        better encapsulate this, all access to the groupMetaCache is via these methods
     */
    @SuppressWarnings("unchecked")
    private HashSet<String> getGroupNamesFromCache() {
        return (HashSet<String>)groupMetaCache.get(GROUP_NAMES_KEY);
    }

    private void clearGroupNameCache() {
        groupMetaCache.remove(GROUP_NAMES_KEY);
    }

    private void saveGroupNamesInCache(final HashSet<String> groupNames) {
        groupMetaCache.put(GROUP_NAMES_KEY, groupNames);
    }

    private PagedGroupNameKey getPagedGroupNameKey(final int startIndex, final int numResults) {
        return new PagedGroupNameKey(startIndex, numResults);
    }

    private static final class PagedGroupNameKey {

        public final int startIndex;

        public final int numResults;

        public PagedGroupNameKey( int startIndex, int numResults){
            this.startIndex = startIndex;
            this.numResults = numResults;
        }

        @Override
        public String toString() {
            return GROUP_NAMES_KEY + startIndex + "," + numResults;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PagedGroupNameKey that = (PagedGroupNameKey) o;
            return startIndex == that.startIndex && numResults == that.numResults;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startIndex, numResults);
        }
    }

    @SuppressWarnings("unchecked")
    private HashSet<String> getPagedGroupNamesFromCache(final int startIndex, final int numResults) {
        return (HashSet<String>)groupMetaCache.get(getPagedGroupNameKey(startIndex, numResults).toString());
    }

    private void savePagedGroupNamesFromCache(final HashSet<String> groupNames, final int startIndex, final int numResults) {
        groupMetaCache.put(getPagedGroupNameKey(startIndex, numResults).toString(), groupNames);

    }

    private Integer getGroupCountFromCache() {
        return (Integer)groupMetaCache.get(GROUP_COUNT_KEY);
    }

    private void saveGroupCountInCache(final int count) {
        groupMetaCache.put(GROUP_COUNT_KEY, count);
    }

    private void clearGroupCountCache() {
        groupMetaCache.remove(GROUP_COUNT_KEY);
    }

    @SuppressWarnings("unchecked")
    private HashSet<String> getUserGroupsFromCache(final JID user) {
        return (HashSet<String>)groupMetaCache.get(getUserGroupsKey(user));
    }

    private void clearUserGroupsCache(final JID user) {
        groupMetaCache.remove(getUserGroupsKey(user));
    }

    private void saveUserGroupsInCache(final JID user, final HashSet<String> groupNames) {
        groupMetaCache.put(getUserGroupsKey(user), groupNames);
    }

    private String getUserGroupsKey(final JID user) {
        return USER_GROUPS_KEY + user.toBareJID();
    }

}
