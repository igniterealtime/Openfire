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
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.Entity;
import org.jivesoftware.messenger.auth.*;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.spi.UserIterator;
import java.util.*;

/**
 * Database implementation of the Group interface.
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 * @author Iain Shigeoka
 * @see Group
 */
public class GroupImpl implements Group, Cacheable {

    private GroupProvider provider = AuthProviderFactory.getGroupProvider();

    private long groupID;
    private String name = null;
    private String description = " ";
    private Date creationDate;
    private Date modificationDate;
    private Map properties;
    private long[] memberList;
    private long[] adminList;
    private Cache groupCache;
    private Cache groupMemberCache;

    /**
     * <p>Creates a group with the given settings.</p>
     *
     * @param id               The unique ID of the group
     * @param groupName        The name of the group
     * @param groupDescription The description of the group
     * @param created          The date-time the group was created
     * @param modified         The date-time the group was last modified
     */
    GroupImpl(long id,
              String groupName,
              String groupDescription,
              Date created,
              Date modified) {
        groupID = id;
        name = groupName;
        description = groupDescription;
        creationDate = created;
        modificationDate = modified;
        properties = new Hashtable();
        groupCache = CacheManager.getCache(GroupManager.GROUP_CACHE_NAME);
        groupMemberCache = CacheManager.getCache(GroupManager.GROUP_MEMBER_CACHE_NAME);
    }

    public long getID() {
        return groupID;
    }

    public String getName() {
        return name;
    }

    public void setName(String groupName) throws UnauthorizedException {
        if (groupName == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        this.name = groupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String desc) throws UnauthorizedException {
        if (desc == null) {
            throw new IllegalArgumentException("Description cannot be null");
        }

        this.description = desc;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date created) throws UnauthorizedException {
        if (created == null) {
            throw new IllegalArgumentException("Creation date cannot be null");
        }

        this.creationDate.setTime(created.getTime());
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modified) throws UnauthorizedException {
        if (modified == null) {
            throw new IllegalArgumentException("Modification date cannot be null");
        }

        this.modificationDate.setTime(modified.getTime());
    }

    public String getProperty(String name) {
        return (String)properties.get(name);
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        // Make sure the property name and value aren't null.
        if (name == null || value == null || "".equals(name) || "".equals(value)) {
            throw new NullPointerException("Cannot set property with empty or null value.");
        }
        // See if we need to update a property value or insert a new one.
        if (properties.containsKey(name)) {
            // Only update the value in the database if the property value has changed.
            if (!(value.equals(properties.get(name)))) {
                String original = (String)properties.get(name);
                properties.put(name, value);
                provider.updateProperty(groupID, name, value);

                // Re-add group to cache.
                groupCache.put(new Long(groupID), this);

                // fire off an event
                Map params = new HashMap();
                params.put("Type", "propertyModify");
                params.put("PropertyKey", name);
                params.put("originalValue", original);
            }
        }
        else {
            properties.put(name, value);
            provider.createProperty(groupID, name, value);

            // Re-add group to cache.
            groupCache.put(new Long(groupID), this);

            // fire off an event
            Map params = new HashMap();
            params.put("Type", "propertyAdd");
            params.put("PropertyKey", name);
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        // Only delete the property if it exists.
        if (properties.containsKey(name)) {
            // fire off an event
            Map params = new HashMap();
            params.put("Type", "propertyDelete");
            params.put("PropertyKey", name);

            properties.remove(name);
            provider.deleteProperty(groupID, name);

            // Re-add group to cache.
            groupCache.put(new Long(groupID), this);
        }
    }

    public Iterator getPropertyNames() {
        return Collections.unmodifiableSet(properties.keySet()).iterator();
    }

    public void addAdministrator(Entity entity)
            throws UnauthorizedException, UserAlreadyExistsException {
        // If the user is already an administrator, do nothing.
        if (isAdministrator(entity)) {
            throw new UserAlreadyExistsException("User " + entity.getUsername() + "with ID " + entity.getID() +
                    " already is a member of the group");
        }

        long userID = entity.getID();
        provider.createMember(groupID, userID, true);
        try {
            provider.updateGroup(this);
        }
        catch (GroupNotFoundException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }

        // Changed admins, so reset admin list
        adminList = null;
        groupCache.put(new Long(groupID), this);
        groupMemberCache.put(groupID + ",admin," + entity.getID(), Boolean.TRUE);

        // Now, clear the permissions cache.
        // This is handled via a listener for the GroupEvent.GROUP_ADMINISTRATOR_ADD event

        // fire off an event
        Map params = new HashMap();
        params.put("Administrator", entity);
    }

    public void removeAdministrator(Entity entity) throws UnauthorizedException {
        // If the user is not an administrator, do nothing.
        if (isAdministrator(entity)) {

            long userID = entity.getID();
            provider.deleteMember(groupID, userID);
            try {
                provider.updateGroup(this);
            }
            catch (GroupNotFoundException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }

            // Changed admins, so reset admin list
            adminList = null;
            groupCache.put(new Long(groupID), this);

            // Remove user from admin cache
            groupMemberCache.remove(groupID + ",admin," + userID);

            // Now, clear the permissions cache.
            // This is handled via a listener for the GroupEvent.GROUP_ADMINISTRATOR_DELETE event
        }
    }

    public void addMember(Entity entity)
            throws UnauthorizedException, UserAlreadyExistsException {
        // Don't do anything if the user is already a member of the group.
        if (isMember(entity)) {
            throw new UserAlreadyExistsException("User " + entity.getUsername() + "with ID " + entity.getID() +
                    " already is a member of the group");
        }

        long userID = entity.getID();
        provider.createMember(groupID, userID, false);
        try {
            provider.updateGroup(this);
        }
        catch (GroupNotFoundException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }

        // Changed membership, so reset member list
        memberList = null;
        groupCache.put(new Long(groupID), this);

        // Remove user from member cache
        groupMemberCache.remove(groupID + ",member," + userID);

        // Remove the user's entry for groups they belong in.
        groupMemberCache.remove("userGroups-" + userID);

        // Now, clear the permissions cache.
        // This is handled via a listener for the GroupEvent.GROUP_USER_ADD event
    }

    public void removeMember(Entity entity) throws UnauthorizedException {
        // Don't do anything if the user isn't a member of the group.
        if (!isMember(entity)) {
            return;
        }

        long userID = entity.getID();
        provider.deleteMember(groupID, userID);

        // Changed membership, so reset member list
        memberList = null;
        groupCache.put(new Long(groupID), this);

        // Remove user from member cache
        groupMemberCache.remove(groupID + ",member," + userID);

        // Remove the user's entry for groups they belong in.
        groupMemberCache.remove("userGroups-" + userID);

        // Now, clear the permissions cache.
        // This is handled via a listener for the GroupEvent.GROUP_USER_DELETE event
    }

    public boolean isAdministrator(Entity entity) {
        long userID = entity.getID();
        Boolean bool = null;
        bool = (Boolean)groupMemberCache.get(groupID + ",admin," + userID);

        if (bool == null) {
            bool = new Boolean(provider.isMember(groupID, userID, true));
            // Add to cache
            groupMemberCache.put(groupID + ",admin," + userID, bool);
        }
        return bool.booleanValue();
    }

    public boolean isMember(Entity entity) {
        long userID = entity.getID();
        Boolean bool = null;
        bool = (Boolean)groupMemberCache.get(groupID + ",member," + userID);
        if (bool == null) {
            bool = new Boolean(provider.isMember(groupID, userID, false));
            // Add to cache
            groupMemberCache.put(groupID + ",member," + userID, bool);
        }
        return bool.booleanValue();
    }

    public int getAdministratorCount() {
        // If the admin list is null, load it.
        if (adminList == null) {
            administrators();
        }
        return adminList.length;
    }

    public int getMemberCount() {
        // If the member list is null, load it.
        if (memberList == null) {
            members();
        }
        return memberList.length;
    }

    public Iterator members() {
        if (memberList == null) {
            memberList = provider.getMembers(groupID, false).toArray();
            // Re-add group to cache.
            groupCache.put(new Long(groupID), this);
        }

        try {
            return new UserIterator(memberList);
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return null;
    }

    public Iterator administrators() {
        if (adminList == null) {
            adminList = provider.getMembers(groupID, true).toArray();
            // Re-add group to cache.
            groupCache.put(new Long(groupID), this);
        }

        try {
            return new UserIterator(adminList);
        }
        catch (UnauthorizedException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return null;
    }

    public Permissions getPermissions(AuthToken auth) {
        long userID = auth.getUserID();
        try {
            Boolean bool = null;
            bool = (Boolean)groupMemberCache.get(groupID + ",admin," + userID);

            if (bool == null) {
                bool = new Boolean(provider.isMember(groupID, userID, true));
                // Add to cache
                groupMemberCache.put(groupID + ",admin," + userID, bool);
            }

            if (bool.booleanValue()) {
                return new Permissions(Permissions.GROUP_ADMIN);
            }
        }
        catch (Exception e) {
        }

        return new Permissions(Permissions.NONE);
    }

    public boolean isAuthorized(long permissionType) {
        return true;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // id
        size += CacheSizes.sizeOfString(name);          // name
        size += CacheSizes.sizeOfString(description);   // description
        size += CacheSizes.sizeOfDate();                // creation date
        size += CacheSizes.sizeOfDate();                // modification date
        size += CacheSizes.sizeOfMap(properties);       // properties
        size += CacheSizes.sizeOfObject();              // member list
        size += CacheSizes.sizeOfObject();              // admin list

        return size;
    }

    /**
     * Returns a String representation of the Group object using the group name.
     *
     * @return a String representation of the Group object.
     */
    public String toString() {
        return name;
    }

    public int hashCode() {
        return (int)groupID;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof GroupImpl) {
            return groupID == ((GroupImpl)object).getID();
        }
        else {
            return false;
        }
    }
}