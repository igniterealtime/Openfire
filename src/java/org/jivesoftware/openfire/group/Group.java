/*
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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Groups organize users into a single entity for easier management.<p>
 *
 * The actual group implementation is controlled by the {@link GroupProvider}, which
 * includes things like the group name, the members, and adminstrators. Each group
 * also has properties, which are always stored in the Openfire database.
 *
 * @see GroupManager#createGroup(String)
 *
 * @author Matt Tucker
 */
public class Group implements Cacheable, Externalizable {

    private static final Logger Log = LoggerFactory.getLogger(Group.class);

    private transient GroupProvider provider;
    private transient GroupManager groupManager;
    private transient PersistableMap<String, String> properties;
    private transient GroupJID jid;

    private String name;
    private String description;
    private ConcurrentSkipListSet<JID> members;
    private ConcurrentSkipListSet<JID> administrators;

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public Group() {
    }

    /**
     * Constructs a new group. Note: this constructor is intended for implementors of the
     * {@link GroupProvider} interface. To create a new group, use the
     * {@link GroupManager#createGroup(String)} method.
     *
     * @param name the name.
     * @param description the description.
     * @param members a Collection of the group members.
     * @param administrators a Collection of the group administrators.
     */
    public Group(String name, String description, Collection<JID> members,
            Collection<JID> administrators)
    {
        this.groupManager = GroupManager.getInstance();
        this.provider = groupManager.getProvider();
        this.name = name;
        this.description = description;
        this.members = new ConcurrentSkipListSet<>(members);
        this.administrators = new ConcurrentSkipListSet<>(administrators);
    }

    /**
     * Constructs a new group. Note: this constructor is intended for implementors of the
     * {@link GroupProvider} interface. To create a new group, use the
     * {@link GroupManager#createGroup(String)} method.
     *
     * @param name the name.
     * @param description the description.
     * @param members a Collection of the group members.
     * @param administrators a Collection of the group administrators.
     * @param properties a Map of properties with names and its values.
     */
    public Group(String name, String description, Collection<JID> members,
            Collection<JID> administrators, Map<String, String> properties)
    {
        this.groupManager = GroupManager.getInstance();
        this.provider = groupManager.getProvider();
        this.name = name;
        this.description = description;
        this.members = new ConcurrentSkipListSet<>(members);
        this.administrators = new ConcurrentSkipListSet<>(administrators);

        this.properties = provider.loadProperties(this);

        // Apply the given properties to the group
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!property.getValue().equals(this.properties.get(property.getKey()))) {
                this.properties.put(property.getKey(), property.getValue());
            }
        }
        // Remove obsolete properties
        Iterator<String> oldProps = this.properties.keySet().iterator();
        while (oldProps.hasNext()) {
            if (!properties.containsKey(oldProps.next())) {
                oldProps.remove();
            }
        }
    }

    /**
     * Returns a JID for the group based on the group name. This
     * instance will be of class GroupJID to distinguish it from
     * other types of JIDs in the system.
     *
     * This method is synchronized to ensure each group has only
     * a single JID instance created via lazy instantiation.
     *
     * @return A JID for the group.
     */
    public synchronized GroupJID getJID() {
        if (jid == null) {
            jid = new GroupJID(getName());
        }
        return jid;
    }

    /**
     * Returns the name of the group. For example, 'XYZ Admins'.
     *
     * @return the name of the group.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the group. For example, 'XYZ Admins'. This
     * method is restricted to those with group administration permission.
     *
     * @param name the name for the group.
     */
    public void setName(String name) {
        if (name == this.name || (name != null && name.equals(this.name)) || provider.isReadOnly())
        {
            // Do nothing
            return;
        }
        try {
            String originalName = this.name;
            GroupJID originalJID = getJID();
            provider.setName(originalName, name);
            this.name = name;
            this.jid = null; // rebuilt when needed

            // Fire event.
            Map<String, Object> params = new HashMap<>();
            params.put("type", "nameModified");
            params.put("originalValue", originalName);
            params.put("originalJID", originalJID);
            GroupEventDispatcher.dispatchEvent(this, GroupEventDispatcher.EventType.group_modified,
                    params);
        }
        catch (GroupAlreadyExistsException e) {
            Log.error("Failed to change group name; group already exists");
        }
    }

    /**
     * Returns the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of the XYZ forum'.
     *
     * @return the description of the group.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of
     * the XYZ forum'. This method is restricted to those with group
     * administration permission.
     *
     * @param description the description of the group.
     */
    public void setDescription(String description) {
        if (description == this.description || (description != null && description.equals(this.description)) ||
                provider.isReadOnly()) {
            // Do nothing
            return;
        }
        try {
            String originalDescription = this.description;
            provider.setDescription(name, description);
            this.description = description;
            // Fire event.
            Map<String, Object> params = new HashMap<>();
            params.put("type", "descriptionModified");
            params.put("originalValue", originalDescription);
            GroupEventDispatcher.dispatchEvent(this,
                    GroupEventDispatcher.EventType.group_modified, params);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Returns all extended properties of the group. Groups have an arbitrary
     * number of extended properties. The returned collection can be modified
     * to add new properties or remove existing ones.
     *
     * @return the extended properties.
     */
    public PersistableMap<String,String> getProperties() {
        synchronized (this) {
            if (properties == null) {
                properties = provider.loadProperties(this);
            }
        }
        // Return a wrapper that will intercept add and remove commands.
        return properties;
    }

    /**
     * Returns a Collection of everyone in the group.
     *
     * @return a read-only Collection of the group administrators + members.
     */
    public Collection<JID> getAll() {
        Set<JID> everybody = new HashSet<>(administrators);
        everybody.addAll(members);
        return Collections.unmodifiableSet(everybody);
    }

    /**
     * Returns a Collection of the group administrators.
     *
     * @return a Collection of the group administrators.
     */
    public Collection<JID> getAdmins() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(administrators, true);
    }

    /**
     * Returns a Collection of the group members.
     *
     * @return a Collection of the group members.
     */
    public Collection<JID> getMembers() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(members, false);
    }

    /**
     * Returns true if the provided JID belongs to a user that is part of the group.
     *
     * @param user the JID address of the user to check.
     * @return true if the specified user is a group user.
     */
    public boolean isUser(JID user) {
        // Make sure that we are always checking bare JIDs
        if (user != null && user.getResource() != null) {
            user = user.asBareJID();
        }
        return user != null && (members.contains(user) || administrators.contains(user));
    }

    /**
     * Returns true if the provided username belongs to a user of the group.
     *
     * @param username the username to check.
     * @return true if the provided username belongs to a user of the group.
     */
    public boolean isUser(String username) {
        if  (username != null) {
            return isUser(XMPPServer.getInstance().createJID(username, null, true));
        }
        else {
            return false;
        }
    }

    @Override
    public int getCachedSize()
        throws CannotCalculateSizeException {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(name);
        size += CacheSizes.sizeOfString(description);
        size += CacheSizes.sizeOfMap(properties);

        for (JID member: members) {
            size += CacheSizes.sizeOfString(member.toString());
        }
        for (JID admin: administrators) {
            size += CacheSizes.sizeOfString(admin.toString());
        }

        return size;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof Group) {
            return name.equals(((Group)object).getName());
        }
        else {
            return false;
        }
    }
    /**
     * Collection implementation that notifies the GroupProvider of any
     * changes to the collection.
     */
    private class MemberCollection extends AbstractCollection<JID> {

        private Collection<JID> users;
        private boolean adminCollection;

        public MemberCollection(Collection<JID> users, boolean adminCollection) {
            this.users = users;
            this.adminCollection = adminCollection;
        }

        @Override
        public Iterator<JID> iterator() {
            return new Iterator<JID>() {

                Iterator<JID> iter = users.iterator();
                JID current = null;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public JID next() {
                    current = iter.next();
                    return current;
                }

                @Override
                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    // Do nothing if the provider is read-only.
                    if (provider.isReadOnly()) {
                        return;
                    }
                    JID user = current;
                    // Remove the user from the collection in memory.
                    iter.remove();
                    // Remove the group user from the backend store.
                    provider.deleteMember(name, user);
                    // Fire event.
                    if (adminCollection) {
                        Map<String, String> params = new HashMap<>();
                        params.put("admin", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.admin_removed, params);
                    }
                    else {
                        Map<String, String> params = new HashMap<>();
                        params.put("member", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.member_removed, params);
                    }
                }
            };
        }

        @Override
        public int size() {
            return users.size();
        }

        @Override
        public boolean add(JID user) {
            // Do nothing if the provider is read-only.
            if (provider.isReadOnly()) {
                return false;
            }
            // Find out if the user was already a group user.
            boolean alreadyGroupUser;
            if (adminCollection) {
                alreadyGroupUser = members.contains(user);
            }
            else {
                alreadyGroupUser = administrators.contains(user);
            }
            if (users.add(user)) {
                if (alreadyGroupUser) {
                    // Update the group user privileges in the backend store.
                    provider.updateMember(name, user, adminCollection);
                }
                else {
                    // Add the group user to the backend store.
                    provider.addMember(name, user, adminCollection);
                }

                // Fire event.
                Map<String, String> params = new HashMap<>();
                if (adminCollection) {
                    params.put("admin", user.toString());
                    if (alreadyGroupUser) {
                        params.put("member", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                    GroupEventDispatcher.EventType.member_removed, params);
                    }
                    GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.admin_added, params);
                }
                else {
                    params.put("member", user.toString());
                    if (alreadyGroupUser) {
                        params.put("admin", user.toString());
                        GroupEventDispatcher.dispatchEvent(Group.this,
                                    GroupEventDispatcher.EventType.admin_removed, params);
                    }
                    GroupEventDispatcher.dispatchEvent(Group.this,
                                GroupEventDispatcher.EventType.member_added, params);
                }
                // If the user was a member that became an admin or vice versa then remove the
                // user from the other collection
                if (alreadyGroupUser) {
                    if (adminCollection) {
                        if (members.contains(user)) {
                            members.remove(user);
                        }
                    }
                    else {
                        if (administrators.contains(user)) {
                            administrators.remove(user);
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, name);
        ExternalizableUtil.getInstance().writeBoolean(out, description != null);
        if (description != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, description);
        }
        ExternalizableUtil.getInstance().writeSerializableCollection(out, members);
        ExternalizableUtil.getInstance().writeSerializableCollection(out, administrators);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        groupManager = GroupManager.getInstance();
        provider = groupManager.getProvider();

        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            description = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        members= new ConcurrentSkipListSet<>();
        administrators = new ConcurrentSkipListSet<>();
        ExternalizableUtil.getInstance().readSerializableCollection(in, members, getClass().getClassLoader());
        ExternalizableUtil.getInstance().readSerializableCollection(in, administrators, getClass().getClassLoader());
    }

    /**
     * Search for a JID within a group. If the given haystack is not resolvable
     * to a group, this method returns false.
     *
     * @param needle A JID, possibly a member/admin of the given group
     * @param haystack Presumably a Group, a Group name, or a JID that represents a Group
     * @return true if the JID (needle) is found in the group (haystack)
     */
    public static boolean search(JID needle, Object haystack) {
        Group group = resolveFrom(haystack);
        return (group != null && group.isUser(needle));
    }

    /**
     * Attempt to resolve the given object into a Group.
     *
     * @param proxy Presumably a Group, a Group name, or a JID that represents a Group
     * @return The corresponding group, or null if the proxy cannot be resolved as a group
     */
    public static Group resolveFrom(Object proxy) {
        Group result = null;
        try {
            GroupManager groupManger = GroupManager.getInstance();
            if (proxy instanceof JID) {
                result = groupManger.getGroup((JID)proxy);
            } else if (proxy instanceof String) {
                result = groupManger.getGroup((String)proxy);
            } else if (proxy instanceof Group) {
                result = (Group) proxy;
            }
        } catch (GroupNotFoundException gnfe) {
            // ignore
        }
        return result;
    }

}
