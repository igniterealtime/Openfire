/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.auth.spi;

import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.ModuleContext;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.Entity;
import org.jivesoftware.messenger.auth.*;
import org.jivesoftware.messenger.user.User;
import java.util.Iterator;

/**
 * Database implementation of the GroupManager interface.
 *
 * @author Iain Shigeoka
 */
public class GroupManagerImpl extends BasicModule implements GroupManager {

    private GroupProvider provider = AuthProviderFactory.getGroupProvider();

    /**
     * A cache for group object.s
     */
    private Cache groupCache;
    /**
     * A cache that maps group names to ID's.
     */
    private Cache groupIDCache;
    /**
     * A cache for the list members of in each group.
     */
    private Cache groupMemberCache;

    public GroupManagerImpl() {
        super("Group Manager (database-backed)");
    }


    public Group createGroup(String name)
            throws UnauthorizedException, GroupAlreadyExistsException {
        Group newGroup = null;
        try {
            getGroup(name);

            // The group already exists since now exception, so:
            throw new GroupAlreadyExistsException();
        }
        catch (GroupNotFoundException unfe) {
            // The group doesn't already exist so we can create a new group
            newGroup = provider.createGroup(name);
            groupCache.put(new Long(newGroup.getID()), newGroup);
        }
        return newGroup;
    }

    public Group getGroup(long groupID) throws GroupNotFoundException {
        Group group = (Group)groupCache.get(new Long(groupID));
        if (group == null) {
            group = provider.getGroup(groupID);
            groupCache.put(new Long(groupID), group);
        }
        return group;
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        Long groupIDLong = (Long)groupIDCache.get(name);
        // If ID wan't found in cache, load it up and put it there.
        if (groupIDLong == null) {
            Group group = provider.getGroup(name);
            groupIDLong = new Long(group.getID());
            groupIDCache.put(name, groupIDLong);
        }
        return getGroup(groupIDLong.longValue());
    }

    public void deleteGroup(Group group) throws UnauthorizedException {
        long groupID = group.getID();
        long[] members = new long[group.getMemberCount()];
        Iterator iter = group.members();

        for (int i = 0; i < members.length; i++) {
            User user = (User)iter.next();
            members[i] = user.getID();
        }

        provider.deleteGroup(group.getID());

        // Finally, expire all relevant caches
        groupCache.remove(new Long(groupID));
        groupIDCache.remove(group.getName());
        // Remove entries in the group membership cache for all members of the group.
        for (int i = 0; i < members.length; i++) {
            groupMemberCache.remove("userGroups-" + members[i]);
        }
        // Removing a group can change the permissions of all the users in that
        // group. Therefore, expire permissions cache.
    }

    public int getGroupCount() {
        return provider.getGroupCount();
    }

    public Iterator getGroups() {
        LongList groups = provider.getGroups();
        return new GroupIterator(this, groups.toArray());
    }

    public Iterator getGroups(BasicResultFilter filter) {
        LongList groups = provider.getGroups(filter);
        return new GroupIterator(this, groups.toArray());
    }

    public Iterator getGroups(Entity entity) {
        long userID = entity.getID();
        String key = "userGroups-" + userID;
        // Look in the group membership cache for the value.
        long[] groups = (long[])groupMemberCache.get(key);

        if (groups == null) {
            groups = provider.getGroups(entity.getID()).toArray();
        }
        return new GroupIterator(this, groups);
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(ModuleContext context, Container container) {
        super.initialize(context, container);
        CacheManager.initializeCache(GROUP_CACHE_NAME, 128 * 1024, context);
        CacheManager.initializeCache(GROUP_ID_CACHE_NAME, 128 * 1024, context);
        CacheManager.initializeCache(GROUP_MEMBER_CACHE_NAME, 16 * 1024, context);

        groupCache = CacheManager.getCache(GROUP_CACHE_NAME);
        groupIDCache = CacheManager.getCache(GROUP_ID_CACHE_NAME);
        groupMemberCache = CacheManager.getCache(GROUP_MEMBER_CACHE_NAME);
    }
}
