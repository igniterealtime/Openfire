/**
 * $RCSfile$
 * $Revision: 3117 $
 * $Date: 2005-11-25 22:57:29 -0300 (Fri, 25 Nov 2005) $
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

package org.jivesoftware.openfire.group;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.clearspace.ClearspaceManager;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Manages groups.
 *
 * @see Group
 * @author Matt Tucker
 */
public class GroupManager {

	private static final Logger Log = LoggerFactory.getLogger(GroupManager.class);

    private static final class GroupManagerContainer {
        private static final GroupManager instance = new GroupManager();
    }

    private static final String GROUP_COUNT_KEY = "GROUP_COUNT";
    private static final String SHARED_GROUPS_KEY = "SHARED_GROUPS";
    private static final String GROUP_NAMES_KEY = "GROUP_NAMES";
    private static final String PUBLIC_GROUPS = "PUBLIC_GROUPS";

    /**
     * Returns a singleton instance of GroupManager.
     *
     * @return a GroupManager instance.
     */
    public static GroupManager getInstance() {
        return GroupManagerContainer.instance;
    }

    private Cache<String, Group> groupCache;
    private Cache<String, Object> groupMetaCache;
    private GroupProvider provider;

    private GroupManager() {
        // Initialize caches.
        groupCache = CacheFactory.createCache("Group");

        // A cache for meta-data around groups: count, group names, groups associated with
        // a particular user
        groupMetaCache = CacheFactory.createCache("Group Metadata Cache");

        initProvider();

        GroupEventDispatcher.addListener(new GroupEventListener() {
            public void groupCreated(Group group, Map params) {

                // Adds default properties if they don't exists, since the creator of
                // the group could set them.
                if (group.getProperties().get("sharedRoster.showInRoster") == null) {
                    group.getProperties().put("sharedRoster.showInRoster", "nobody");
                    group.getProperties().put("sharedRoster.displayName", "");
                    group.getProperties().put("sharedRoster.groupList", "");
                }
                
                // Since the group could be created by the provider, add it possible again
                groupCache.put(group.getName(), group);

                // Evict only the information related to Groups.
                // Do not evict groups with 'user' as keys.
                groupMetaCache.remove(GROUP_COUNT_KEY);
                groupMetaCache.remove(GROUP_NAMES_KEY);
                groupMetaCache.remove(SHARED_GROUPS_KEY);
                
                // Evict cached information for affected users
                evictCachedUsersForGroup(group);
            }

            public void groupDeleting(Group group, Map params) {
                // Since the group could be deleted by the provider, remove it possible again
                groupCache.remove(group.getName());
                
                // Evict only the information related to Groups.
                // Do not evict groups with 'user' as keys.
                groupMetaCache.remove(GROUP_COUNT_KEY);
                groupMetaCache.remove(GROUP_NAMES_KEY);
                groupMetaCache.remove(SHARED_GROUPS_KEY);
                
                // Evict cached information for affected users
                evictCachedUsersForGroup(group);
            }

            public void groupModified(Group group, Map params) {
                String type = (String)params.get("type");
                // If shared group settings changed, expire the cache.
                if (type != null) {
                	if (type.equals("propertyModified") ||
                        type.equals("propertyDeleted") || type.equals("propertyAdded"))
	                {
                		Object key = params.get("propertyKey");
	                    if (key instanceof String && (key.equals("sharedRoster.showInRoster") || key.equals("*")))
	                    {
	                    	groupMetaCache.remove(GROUP_NAMES_KEY);
	                        groupMetaCache.remove(SHARED_GROUPS_KEY);
	                    }
	                }	
                	// clean up cache for old group name
                	if (type.equals("nameModified")) {
                		String originalName = (String) params.get("originalValue");
                		if (originalName != null) {
                			groupMetaCache.remove(originalName);
                		}
                        // Evict cached information for affected users
                        evictCachedUsersForGroup(group);
                	}
                }
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
            }

            public void memberAdded(Group group, Map params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
                
                // Remove only the collection of groups the member belongs to.
                String member = (String) params.get("member");
                if(member != null) {
	                groupMetaCache.remove(member);
                }
            }

            public void memberRemoved(Group group, Map params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
                
                // Remove only the collection of groups the member belongs to.
                String member = (String) params.get("member");
                if(member != null) {
	                groupMetaCache.remove(member);
                }
            }

            public void adminAdded(Group group, Map params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
                
                // Remove only the collection of groups the member belongs to.
                String member = (String) params.get("admin");
                if(member != null) {
	                groupMetaCache.remove(member);
                }
            }

            public void adminRemoved(Group group, Map params) {
                // Set object again in cache. This is done so that other cluster nodes
                // get refreshed with latest version of the object
                groupCache.put(group.getName(), group);
                
                // Remove only the collection of groups the member belongs to.
                String member = (String) params.get("admin");
                if(member != null) {
	                groupMetaCache.remove(member);
                }
            }

        });

        UserEventDispatcher.addListener(new UserEventListener() {
            public void userCreated(User user, Map<String, Object> params) {
                // ignore
            }

            public void userDeleting(User user, Map<String, Object> params) {
                deleteUser(user);
            }

            public void userModified(User user, Map<String, Object> params) {
                // ignore
            }
        });

        // Detect when a new auth provider class is set
        PropertyEventListener propListener = new PropertyEventListener() {
            public void propertySet(String property, Map params) {
                if ("provider.group.className".equals(property)) {
                    initProvider();
                }
            }

            public void propertyDeleted(String property, Map params) {
                //Ignore
            }

            public void xmlPropertySet(String property, Map params) {
                //Ignore
            }

            public void xmlPropertyDeleted(String property, Map params) {
                //Ignore
            }
        };
        PropertyEventDispatcher.addListener(propListener);
    }

    private void initProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("provider.group.className");

        // Load a group provider.
        String className = JiveGlobals.getProperty("provider.group.className",
                "org.jivesoftware.openfire.group.DefaultGroupProvider");
        try {
            Class c = ClassUtils.forName(className);
            provider = (GroupProvider) c.newInstance();
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
        return getGroup(name, false);
    }

    /**
     * Returns a Group by name.
     *
     * @param name The name of the group to retrieve
     * @return The group corresponding to that name
     * @throws GroupNotFoundException if the group does not exist.
     */
    public Group getGroup(String name, boolean forceLookup) throws GroupNotFoundException {
        Group group = null;
        if (!forceLookup) {
            group = groupCache.get(name);
        }
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
     * NOTE: Iterating through the resulting collection has the effect of loading
     * every group into memory. This may be an issue for large deployments. You
     * may call the size() method on the resulting collection to determine the best
     * approach to take before iterating over (and thus instantiating) the groups.
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
     * NOTE: Iterating through the resulting collection has the effect of loading all
     * shared groups into memory. This may be an issue for large deployments. You
     * may call the size() method on the resulting collection to determine the best
     * approach to take before iterating over (and thus instantiating) the groups.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getSharedGroups() {
        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(SHARED_GROUPS_KEY);
        if (groupNames == null) {
            synchronized(SHARED_GROUPS_KEY.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(SHARED_GROUPS_KEY);
                if (groupNames == null) {
                    groupNames = provider.getSharedGroupNames();
                    groupMetaCache.put(SHARED_GROUPS_KEY, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given userName.
     *
     * @return an unmodifiable Collection of all shared groups for the given userName.
     */
    public Collection<Group> getSharedGroups(String userName) {
        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(userName);
        if (groupNames == null) {
            synchronized(userName.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(userName);
                if (groupNames == null) {
                	// assume this is a local user
                    groupNames = provider.getSharedGroupNames(new JID(userName, 
                    		XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null));
                    groupMetaCache.put(userName, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all shared groups in the system for a given userName.
     *
     * @return an unmodifiable Collection of all shared groups for the given userName.
     */
    public Collection<Group> getVisibleGroups(Group groupToCheck) {
    	// Get all the public shared groups.
    	Collection<String> groupNames = (Collection<String>)groupMetaCache.get(PUBLIC_GROUPS);
        if (groupNames == null) {
            synchronized(PUBLIC_GROUPS.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(PUBLIC_GROUPS);
                if (groupNames == null) {
                    groupNames = provider.getPublicSharedGroupNames();
                    groupMetaCache.put(PUBLIC_GROUPS, groupNames);
                }
            }
        }
        // Now get all visible groups to the given group.
        groupNames.addAll(provider.getVisibleGroupNames(groupToCheck.getName()));
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all public shared groups in the system.
     *
     * @return an unmodifiable Collection of all shared groups.
     */
    public Collection<Group> getPublicSharedGroups() {
        Collection<String> groupNames = (Collection<String>)groupMetaCache.get(PUBLIC_GROUPS);
        if (groupNames == null) {
            synchronized(PUBLIC_GROUPS.intern()) {
                groupNames = (Collection<String>)groupMetaCache.get(PUBLIC_GROUPS);
                if (groupNames == null) {
                    groupNames = provider.getPublicSharedGroupNames();
                    groupMetaCache.put(PUBLIC_GROUPS, groupNames);
                }
            }
        }
        return new GroupCollection(groupNames);
    }
    
    /**
     * Returns an unmodifiable Collection of all groups in the system that
     * match given propValue for the specified propName.
     *
     * @return an unmodifiable Collection of all shared groups.
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
     * Returns true if properties of groups are read only.
     * They are read only if Clearspace is the group provider.
     *
     * @return true if properties of groups are read only.
     */
    public boolean isPropertyReadOnly() {
        return ClearspaceManager.isEnabled();
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
    
    private void evictCachedUsersForGroup(Group group) {
        // Evict cached information for affected users
        for (JID user : group.getAdmins()) {
        	groupMetaCache.remove(user.getNode());
        }
        for (JID user : group.getMembers()) {
        	groupMetaCache.remove(user.getNode());
        }
    }
}