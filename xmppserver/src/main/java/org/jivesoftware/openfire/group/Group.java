/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.PersistableMap;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Groups organize users into a single entity for easier management.
 *
 * As grouped entities are thought to represent end-user entities, a group can be thought of as a collection of
 * <em>bare</em> (not full) JIDs. The method of the Group class and its associated API will accept both bare and full
 * JIDs, but are expected to 'cast down' a full JID to a bare JID prior to processing it.
 *
 * The actual group implementation is controlled by the {@link GroupProvider}, which
 * includes things like the group name, the members, and administrators. Each group
 * also has properties, which are always stored in the Openfire database.
 *
 * @see GroupManager#createGroup(String)
 *
 * @author Matt Tucker
 */
public class Group implements Cacheable, Externalizable {

    private static final Logger Log = LoggerFactory.getLogger(Group.class);

    public static final String SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY = "sharedRoster.showInRoster";
    public static final String SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY = "sharedRoster.displayName";
    public static final String SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY = "sharedRoster.groupList";

    private transient GroupProvider provider;
    private transient GroupManager groupManager;
    private transient PersistableMap<String, String> properties;
    private transient GroupJID jid;

    private String name;
    private String description;
    private MemberCollection members;
    private MemberCollection administrators;

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
        this.members = new MemberCollection(members, false);
        this.administrators = new MemberCollection(administrators, true);
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
        this(name, description, members, administrators);

        this.properties = provider.loadProperties(this);

        // Apply the given properties to the group
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!property.getValue().equals(this.properties.get(property.getKey()))) {
                this.properties.put(property.getKey(), property.getValue());
            }
        }
        // Remove obsolete properties
        this.properties.keySet().removeIf(s -> !properties.containsKey(s));
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
        if (Objects.equals(name, this.name) || provider.isReadOnly())
        {
            // Do nothing
            return;
        }
        try {
            String originalName = this.name;
            provider.setName(originalName, name);
            this.name = name;
            this.jid = null; // rebuilt when needed

            groupManager.renameGroupPostProcess(this, originalName);
        }
        catch (GroupAlreadyExistsException e) {
            Log.error("Failed to change group name; group already exists");
        } catch (GroupNameInvalidException e) {
            Log.error("Failed to change group name; new group name is not valid");
        } catch (GroupNotFoundException e) {
            Log.error("Failed to change group name; the provided group name cannot be found");
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
        if (Objects.equals(description, this.description) || provider.isReadOnly()) {
            // Do nothing
            return;
        }
        try {
            String originalDescription = this.description;
            provider.setDescription(name, description);
            this.description = description;

            groupManager.redescribeGroupPostProcess(this, originalDescription);
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
     * Disable contact list group sharing for this group.
     */
    public void shareWithNobody() {
        final PersistableMap<String, String> properties = getProperties();
        properties.put(SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.nobody.getDbValue());
        properties.put(SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, "");
        properties.put(SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, "");
    }

    /**
     * Enable contact list group sharing for this group. The group is shared on the contact list of all users defined in
     * Openfire: every user in this group is added to the contact list of every user in Openfire, grouped by the
     * provided display name.
     *
     * @param displayName The name of the group that as shown in the contact lists of users.
     */
    public void shareWithEverybody(@Nonnull final String displayName) {
        final PersistableMap<String, String> properties = getProperties();
        properties.put(SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.everybody.getDbValue());
        properties.put(SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, displayName);
        properties.remove(SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY);
    }

    /**
     * Enable contact list group sharing for this group. The group is shared on the contact list of all users in the
     * same group: every user in this group is added to the contact list of every other user in the group, grouped by
     * the provided display name.
     *
     * @param displayName The name of the group that as shown in the contact lists of users.
     */
    public void shareWithUsersInSameGroup(@Nonnull final String displayName) {
        final PersistableMap<String, String> properties = getProperties();
        properties.put(SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.usersOfGroups.getDbValue());
        properties.put(SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, displayName);
        properties.put(SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, "");
    }

    /**
     * Enable contact list group sharing for this group. The group is shared on the contact list of all users in each of
     * the provided group: every user in this group is added to the contact list of every user in each of the provided
     * other groups, grouped by the provided display name.
     *
     * @param groupNames Name of groups for which all users should
     * @param displayName The name of the group that as shown in the contact lists of users.
     */
    public void shareWithUsersInGroups(@Nonnull final List<String> groupNames, @Nonnull final String displayName) {
        final PersistableMap<String, String> properties = getProperties();
        properties.put(SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY, SharedGroupVisibility.usersOfGroups.getDbValue());
        properties.put(SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY, displayName);
        properties.put(SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, String.join(",", groupNames));
    }

    /**
     * When contact list group sharing has been enabled, the users in his group will be added to the contact list of
     * every entity that the group is shared with. On their contact lists, the users from this group will be added
     * to a group by a name that is equal to the return value of this method.
     *
     * This method may return a null value when contact list sharing has not yet been configured, or an empty string
     * when it has explicitly been configured to be disabled ("share with nobody").
     *
     * @return A name to be used for a contact list group when sharing is enabled.
     */
    @Nullable
    public String getSharedDisplayName() {
        return getProperties().get(SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY);
    }

    /**
     * Defines to which entities this group is shared, using the contact list group sharing. This method can return
     * 'nobody', which differs from a 'null' value, which is returned when contact list sharing has not yet been
     * configured.
     *
     * When 'usersOfGroups' is returned, {@link #getSharedWithUsersInGroupNames()} should be used to determine which to
     * which groups this group is shared.
     *
     * @return To which users this group is shared with.
     */
    @Nullable
    public SharedGroupVisibility getSharedWith() {
        final String value = getProperties().get(SHARED_ROSTER_SHOW_IN_ROSTER_PROPERTY_KEY);
        if (value == null) {
            return null;
        }
        switch (value) {
            case "nobody": return SharedGroupVisibility.nobody;
            case "everybody": return SharedGroupVisibility.everybody;
            case "onlyGroup": return SharedGroupVisibility.usersOfGroups;
            default: return SharedGroupVisibility.nobody;
        }
    }

    /**
     * Defines the groups of users with which this group is shared if contact list group sharing is enabled and this
     * group is shared with (other) groups (as defined by {@link #getSharedWith()}).
     *
     * @return A collection of group names, possibly empty.
     */
    @Nonnull
    public List<String> getSharedWithUsersInGroupNames() {
        final List<String> result = new ArrayList<>();
        if (getSharedWith() != SharedGroupVisibility.usersOfGroups) {
            return result;
        }

        final String value = properties.get(SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY);
        if (value == null) {
            // When in this getSharedWith, I don't think that the value can be null. I'm not confident enough to add this as an 'assert' though.
            Log.warn("Unable to get shared-with-users-in-group-names for group, as property '{}' unexpectedly has a null value for group: {}", SHARED_ROSTER_GROUP_LIST_PROPERTY_KEY, this);
            return result;
        }

        // Value is a comma-seperated list of group-names
        for (final String groupName : value.split(",")) {
            if (!groupName.trim().isEmpty()) {
                result.add(groupName.trim());
            }
        }
        // A group is always shared with itself when it's shared with other groups.
        if (!result.contains(this.name)) {
            result.add(this.name);
        }
        return result;
    }

    /**
     * Returns a read-only, unmodifiable collection of everyone in the group.
     *
     * @return a read-only Collection of the group administrators + members.
     */
    public Collection<JID> getAll() {
        Set<JID> everybody = new HashSet<>(administrators);
        everybody.addAll(members);
        return Collections.unmodifiableSet(everybody);
    }

    /**
     * Returns a Collection of the group administrators. Changes made the collection will be applied and persisted via
     * the GroupProvider.
     *
     * @return a Collection of the group administrators.
     */
    public Collection<JID> getAdmins() {
        return administrators;
    }

    /**
     * Returns a Collection of the group members. Changes made the collection will be applied and persisted via the
     * GroupProvider.
     *
     * @return a Collection of the group members.
     */
    public Collection<JID> getMembers() {
        return members;
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
        if (username != null) {
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

        size += CacheSizes.sizeOfCollection(members);
        size += CacheSizes.sizeOfCollection(administrators);

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
        if (object instanceof Group) {
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
    private class MemberCollection extends AbstractCollection<JID>
    {
        /**
         * A representation of the addresses of all members.
         */
        private final Set<JID> users; // Must contain only _bare_ JIDs!

        /**
         * True if the users in this group are administrators, otherwise false.
         */
        private final boolean adminCollection;

        public MemberCollection(final Collection<JID> users, final boolean adminCollection)
        {
            this.users = users.stream()
                .map(JID::asBareJID)
                .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet)); // use thread-safe collection type!
            this.adminCollection = adminCollection;
        }

        /**
         * Returns an iterator over the users in this collection.
         *
         * The returned iterator is <i>weakly consistent</i>.
         *
         * @return an iterator over the users in this collection
         */
        @Nonnull
        @Override
        public Iterator<JID> iterator() {
            return new Iterator<>() {

                final Iterator<JID> iter = users.iterator();
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

                    // Perform post-processing (cache updates and event notifications).
                    if (adminCollection) {
                        groupManager.adminRemovedPostProcess(Group.this, user);
                    } else {
                        groupManager.memberRemovedPostProcess(Group.this, user);
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

            user = user.asBareJID();

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
                    try {
                        // Update the group user privileges in the backend store.
                        provider.updateMember(name, user, adminCollection);
                    } catch (Exception e) {
                        Log.error("Failed to update group member", e);
                    }
                }
                else {
                    try {
                        // Add the group user to the backend store.
                        provider.addMember(name, user, adminCollection);
                    } catch (Exception e) {
                        Log.error("Failed to add group member", e);
                    }
                }

                // Perform post-processing (cache updates and event notifications).
                if (adminCollection) {
                    groupManager.adminAddedPostProcess(Group.this, user, alreadyGroupUser);
                } else {
                    groupManager.memberAddedPostProcess(Group.this, user, alreadyGroupUser);
                }

                // If the user was a member that became an admin or vice versa then remove the
                // user from the other collection
                if (alreadyGroupUser) {
                    if (adminCollection) {
                        members.remove(user);
                    }
                    else {
                        administrators.remove(user);
                    }
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean contains(Object o)
        {
            if (o instanceof JID) {
                return users.contains(((JID) o).asBareJID());
            } else {
                return false;
            }
        }

        @Override
        public boolean remove(Object o)
        {
            if (o instanceof JID) {
                // Do not short-circuit this to users.remove(), as we need the handling that happens in the iterator's 'remove' implementation!
                return super.remove(((JID) o).asBareJID());
            } else {
                return false;
            }
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, name);
        ExternalizableUtil.getInstance().writeBoolean(out, description != null);
        if (description != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, description);
        }

        ExternalizableUtil.getInstance().writeInt(out, members.size());
        for (final JID member : members) {
            ExternalizableUtil.getInstance().writeSerializable(out, member);
        }

        ExternalizableUtil.getInstance().writeInt(out, administrators.size());
        for (final JID administrator : administrators) {
            ExternalizableUtil.getInstance().writeSerializable(out, administrator);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        groupManager = GroupManager.getInstance();
        provider = groupManager.getProvider();

        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            description = ExternalizableUtil.getInstance().readSafeUTF(in);
        }

        final int memberSize = ExternalizableUtil.getInstance().readInt(in);
        final List<JID> tmpMembers = new ArrayList<>(memberSize);
        for (int i = 0; i < memberSize; i++) {
            final JID member = (JID) ExternalizableUtil.getInstance().readSerializable(in);
            tmpMembers.add(member);
        }
        members = new MemberCollection(tmpMembers, false);

        final int administratorSize = ExternalizableUtil.getInstance().readInt(in);
        final List<JID> tmpAdministrators = new ArrayList<>(administratorSize);
        for (int i = 0; i < administratorSize; i++) {
            final JID administrator = (JID) ExternalizableUtil.getInstance().readSerializable(in);
            tmpAdministrators.add(administrator);
        }
        administrators = new MemberCollection(tmpAdministrators, true);
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
