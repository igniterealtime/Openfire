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

package org.jivesoftware.openfire.roster;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

/**
 * <p>Represents a single roster item for a User's Roster.</p>
 * <p>The server doesn't need to know anything about roster groups so they are
 * not stored with easy retrieval or manipulation in mind. The important data
 * elements of a roster item (beyond the jid adddress of the roster entry) includes:</p>
 * <ul>
 * <li>nick   - A nickname for the user when used in this roster</li>
 * <li>sub    - A subscription type: to, from, none, both</li>
 * <li>ask    - An optional subscription ask status: subscribe, unsubscribe</li>
 * <li>groups - A list of groups to organize roster entries under (e.g. friends, co-workers, etc)</li>
 * </ul>
 *
 * @author Gaston Dombiak
 */
public class RosterItem implements Cacheable, Externalizable {

    public enum SubType {

        /**
         * Indicates the roster item should be removed.
         */
        REMOVE(-1),
        /**
         * No subscription is established.
         */
        NONE(0),
        /**
         * The roster owner has a subscription to the roster item's presence.
         */
        TO(1),
        /**
         * The roster item has a subscription to the roster owner's presence.
         */
        FROM(2),
        /**
         * The roster item and owner have a mutual subscription.
         */
        BOTH(3);

        private final int value;

        SubType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public String getName() {
            return name().toLowerCase();
        }

        public static SubType getTypeFromInt(int value) {
            for (SubType subType : values()) {
                if (subType.value == value) {
                    return subType;
                }
            }
            return null;
        }
    }

    public enum AskType {

        /**
         * The roster item has no pending subscription requests.
         */
        NONE(-1),
        /**
         * The roster item has been asked for permission to subscribe to their presence
         * but no response has been received.
         */
        SUBSCRIBE(0),
        /**
         * The roster owner has asked to the roster item to unsubscribe from it's
         * presence but has not received confirmation.
         */
        UNSUBSCRIBE(1);

        private final int value;

        AskType(int value) {
            this.value = value;

        }

        public int getValue() {
            return value;
        }

        public static AskType getTypeFromInt(int value) {
            for (AskType askType : values()) {
                if (askType.value == value) {
                    return askType;
                }
            }
            return null;
        }
    }

    public enum RecvType {

        /**
         * There are no subscriptions that have been received but not presented to the user.
         */
        NONE(-1),
        /**
         * The server has received a subscribe request, but has not forwarded it to the user.
         */
        SUBSCRIBE(1),
        /**
         * The server has received an unsubscribe request, but has not forwarded it to the user.
         */
        UNSUBSCRIBE(2);

        private final int value;

        RecvType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static RecvType getTypeFromInt(int value) {
            for (RecvType recvType : values()) {
                if (recvType.value == value) {
                    return recvType;
                }
            }
            return null;
        }
    }

    /**
     * <p>Indicates the roster item should be removed.</p>
     */
    public static final SubType SUB_REMOVE = SubType.REMOVE;
    /**
     * <p>No subscription is established.</p>
     */
    public static final SubType SUB_NONE = SubType.NONE;
    /**
     * <p>The roster owner has a subscription to the roster item's presence.</p>
     */
    public static final SubType SUB_TO = SubType.TO;
    /**
     * <p>The roster item has a subscription to the roster owner's presence.</p>
     */
    public static final SubType SUB_FROM = SubType.FROM;
    /**
     * <p>The roster item and owner have a mutual subscription.</p>
     */
    public static final SubType SUB_BOTH = SubType.BOTH;

    /**
     * <p>The roster item has no pending subscription requests.</p>
     */
    public static final AskType ASK_NONE = AskType.NONE;
    /**
     * <p>The roster item has been asked for permission to subscribe to their presence
     * but no response has been received.</p>
     */
    public static final AskType ASK_SUBSCRIBE = AskType.SUBSCRIBE;
    /**
     * <p>The roster owner has asked to the roster item to unsubscribe from it's
     * presence but has not received confirmation.</p>
     */
    public static final AskType ASK_UNSUBSCRIBE = AskType.UNSUBSCRIBE;

    /**
     * <p>There are no subscriptions that have been received but not presented to the user.</p>
     */
    public static final RecvType RECV_NONE = RecvType.NONE;
    /**
     * <p>The server has received a subscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_SUBSCRIBE = RecvType.SUBSCRIBE;
    /**
     * <p>The server has received an unsubscribe request, but has not forwarded it to the user.</p>
     */
    public static final RecvType RECV_UNSUBSCRIBE = RecvType.UNSUBSCRIBE;

    protected RecvType recvStatus;
    protected JID jid;
    protected String nickname;
    protected List<String> groups;
    protected Set<String> sharedGroups = new HashSet<>();
    protected Set<String> invisibleSharedGroups = new HashSet<>();
    protected SubType subStatus;
    protected AskType askStatus;
    /**
     * Holds the ID that uniquely identifies the roster in the backend store. A value of
     * zero means that the roster item is not persistent.
     */
    private long rosterID;

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public RosterItem() {
    }

    public RosterItem(long id,
                                JID jid,
                                SubType subStatus,
                                AskType askStatus,
                                RecvType recvStatus,
                                String nickname,
                                List<String> groups) {
        this(jid, subStatus, askStatus, recvStatus, nickname, groups);
        this.rosterID = id;
    }

    public RosterItem(JID jid,
                           SubType subStatus,
                           AskType askStatus,
                           RecvType recvStatus,
                           String nickname,
                           List<String> groups) {
        this.jid = jid;
        this.subStatus = subStatus;
        this.askStatus = askStatus;
        this.recvStatus = recvStatus;
        this.nickname = nickname;
        this.groups = new LinkedList<>();
        if (groups != null) {
            for (String group : groups) {
                this.groups.add(group);
            }
        }
    }

    /**
     * Create a roster item from the data in another one.
     *
     * @param item Item that contains the info of the roster item.
     */
    public RosterItem(org.xmpp.packet.Roster.Item item) {
        this(item.getJID(),
                getSubType(item),
                getAskStatus(item),
                RosterItem.RECV_NONE,
                item.getName(),
                new LinkedList<>(item.getGroups()));
    }

    public static RosterItem.AskType getAskStatus(org.xmpp.packet.Roster.Item item) {
        if (item.getAsk() == org.xmpp.packet.Roster.Ask.subscribe) {
            return RosterItem.ASK_SUBSCRIBE;
        }
        else if (item.getAsk() == org.xmpp.packet.Roster.Ask.unsubscribe) {
            return RosterItem.ASK_UNSUBSCRIBE;
        }
        else {
            return RosterItem.ASK_NONE;
        }
    }

    public static RosterItem.SubType getSubType(org.xmpp.packet.Roster.Item item) {
        if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.to) {
            return RosterItem.SUB_TO;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.from) {
            return RosterItem.SUB_FROM;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.both) {
            return RosterItem.SUB_BOTH;
        }
        else if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
            return RosterItem.SUB_REMOVE;
        }
        else {
            return RosterItem.SUB_NONE;
        }
    }

    /**
     * <p>Obtain the current subscription status of the item.</p>
     *
     * @return The subscription status of the item
     */
    public SubType getSubStatus() {
        return subStatus;
    }

    /**
     * <p>Set the current subscription status of the item.</p>
     *
     * @param subStatus The subscription status of the item
     */
    public void setSubStatus(SubType subStatus) {
        // Optimization: Load user only if we need to set the nickname of the roster item
        if ("".equals(nickname) && (subStatus == SUB_BOTH || subStatus == SUB_TO)) {
            try {
                nickname = UserNameManager.getUserName(jid);
            }
            catch (UserNotFoundException e) {
                // Do nothing
            }
        }
        this.subStatus = subStatus;
    }

    /**
     * <p>Obtain the current ask status of the item.</p>
     *
     * @return The ask status of the item
     */
    public AskType getAskStatus() {
        if (isShared()) {
            // Redefine the ask status since the item belongs to a shared group
            return ASK_NONE;
        }
        else {
            return askStatus;
        }
    }

    /**
     * <p>Set the current ask status of the item.</p>
     *
     * @param askStatus The ask status of the item
     */
    public void setAskStatus(AskType askStatus) {
        this.askStatus = askStatus;
    }

    /**
     * <p>Obtain the current recv status of the item.</p>
     *
     * @return The recv status of the item
     */
    public RecvType getRecvStatus() {
        return recvStatus;
    }

    /**
     * <p>Set the current recv status of the item.</p>
     *
     * @param recvStatus The recv status of the item
     */
    public void setRecvStatus(RecvType recvStatus) {
        this.recvStatus = recvStatus;
    }

    /**
     * <p>Obtain the address of the item.</p>
     *
     * @return The address of the item
     */
    public JID getJid() {
        return jid;
    }

    /**
     * <p>Obtain the current nickname for the item.</p>
     *
     * @return The subscription status of the item
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * <p>Set the current nickname for the item.</p>
     *
     * @param nickname The subscription status of the item
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * Returns the groups for the item. Shared groups won't be included in the answer.
     *
     * @return The groups for the item.
     */
    public List<String> getGroups() {
        return groups;
    }

    /**
     * Set the current groups for the item.
     *
     * @param groups The new lists of groups the item belongs to.
     * @throws org.jivesoftware.openfire.SharedGroupException if trying to remove shared group.
     */
    public void setGroups(List<String> groups) throws SharedGroupException {
        if (groups == null) {
            this.groups = new LinkedList<>();
        }
        else {
            // Raise an error if the user is trying to remove the item from a shared group
            for (Group group: getSharedGroups()) {
                // Get the display name of the group
                String groupName = group.getProperties().get("sharedRoster.displayName");
                // Check if the group has been removed from the new groups list
                if (!groups.contains(groupName)) {
                    throw new SharedGroupException("Cannot remove item from shared group");
                }
            }

            // Remove shared groups from the param
            for (Iterator<String> it=groups.iterator(); it.hasNext();) {
                String groupName = it.next();
                try {
                    Group group = GroupManager.getInstance().getGroup(groupName);
                    if (RosterManager.isSharedGroup(group)) {
                        it.remove();
                    }
                } catch (GroupNotFoundException e) {
                    // Check now if there is a group whose display name matches the requested group
                    Collection<Group> groupsWithProp = GroupManager
                            .getInstance()
                            .search("sharedRoster.displayName", groupName);
                    Iterator<Group> itr = groupsWithProp.iterator();
                    while(itr.hasNext()) {
                        Group group = itr.next();
                        if (RosterManager.isSharedGroup(group)) {
                            it.remove();
                        }
                    }
                }
            }
            this.groups = groups;
        }
    }

    /**
     * Returns the shared groups for the item.
     *
     * @return The shared groups this item belongs to.
     */
    public Collection<Group> getSharedGroups() {
        Collection<Group> groups = new ArrayList<>(sharedGroups.size());
        for (String groupName : sharedGroups) {
            try {
                groups.add(GroupManager.getInstance().getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                // Do nothing
            }
        }
        return groups;
    }

    /**
     * Returns the invisible shared groups for the item. These groups are for internal use
     * and help track the reason why a roster item has a presence subscription of type FROM
     * when using shared groups.
     *
     * @return The shared groups this item belongs to.
     */
    public Collection<Group> getInvisibleSharedGroups() {
        Collection<Group> groups = new ArrayList<>(invisibleSharedGroups.size());
        for (String groupName : invisibleSharedGroups) {
            try {
                groups.add(GroupManager.getInstance().getGroup(groupName));
            }
            catch (GroupNotFoundException e) {
                // Do nothing
            }
        }
        return groups;
    }

    Set<String> getInvisibleSharedGroupsNames() {
        return invisibleSharedGroups;
    }

    void setInvisibleSharedGroupsNames(Set<String> groupsNames) {
        invisibleSharedGroups = groupsNames;
    }

    /**
     * Adds a new group to the shared groups list.
     *
     * @param sharedGroup The shared group to add to the list of shared groups.
     */
    public void addSharedGroup(Group sharedGroup) {
        sharedGroups.add(sharedGroup.getName());
        invisibleSharedGroups.remove(sharedGroup.getName());
    }

    /**
     * Adds a new group to the list shared groups that won't be sent to the user. These groups
     * are for internal use and help track the reason why a roster item has a presence
     * subscription of type FROM when using shared groups.
     *
     * @param sharedGroup The shared group to add to the list of shared groups.
     */
    public void addInvisibleSharedGroup(Group sharedGroup) {
        invisibleSharedGroups.add(sharedGroup.getName());
    }

    /**
     * Removes a group from the shared groups list.
     *
     * @param sharedGroup The shared group to remove from the list of shared groups.
     */
    public void removeSharedGroup(Group sharedGroup) {
        sharedGroups.remove(sharedGroup.getName());
        invisibleSharedGroups.remove(sharedGroup.getName());
    }

    /**
     * Returns true if this item belongs to a shared group. Return true even if the item belongs
     * to a personal group and a shared group.
     *
     * @return true if this item belongs to a shared group.
     */
    public boolean isShared() {
        return !sharedGroups.isEmpty() || !invisibleSharedGroups.isEmpty();
    }

    /**
     * Returns true if this item belongs ONLY to shared groups. This means that the the item is
     * considered to be "only shared" if it doesn't belong to a personal group but only to shared
     * groups.
     *
     * @return true if this item belongs ONLY to shared groups.
     */
    public boolean isOnlyShared() {
        return isShared() && groups.isEmpty();
    }

    /**
     * Returns the roster ID associated with this particular roster item. A value of zero
     * means that the roster item is not being persisted in the backend store.<p>
     *
     * Databases can use the roster ID as the key in locating roster items.
     *
     * @return The roster ID
     */
    public long getID() {
        return rosterID;
    }

    /**
     * Sets the roster ID associated with this particular roster item. A value of zero
     * means that the roster item is not being persisted in the backend store.<p>
     *
     * Databases can use the roster ID as the key in locating roster items.
     *
     * @param rosterID The roster ID.
     */
    public void setID(long rosterID) {
        this.rosterID = rosterID;
    }

    /**
     * <p>Update the cached item as a copy of the given item.</p>
     * <p>A convenience for getting the item and setting each attribute.</p>
     *
     * @param item The item who's settings will be copied into the cached copy
     * @throws org.jivesoftware.openfire.SharedGroupException if trying to remove shared group.
     */
    public void setAsCopyOf(org.xmpp.packet.Roster.Item item) throws SharedGroupException {
        setGroups(new LinkedList<>(item.getGroups()));
        setNickname(item.getName());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.util.cache.Cacheable#getCachedSize()
     */
    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        int size = jid.toBareJID().length();
        size += CacheSizes.sizeOfString(nickname);
        size += CacheSizes.sizeOfCollection(groups);
        size += CacheSizes.sizeOfCollection(invisibleSharedGroups);
        size += CacheSizes.sizeOfCollection(sharedGroups);
        size += CacheSizes.sizeOfInt(); // subStatus
        size += CacheSizes.sizeOfInt(); // askStatus
        size += CacheSizes.sizeOfInt(); // recvStatus
        size += CacheSizes.sizeOfLong(); // id
        return size;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, jid);
        ExternalizableUtil.getInstance().writeBoolean(out, nickname != null);
        if (nickname != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        }
        ExternalizableUtil.getInstance().writeStrings(out, groups);
        ExternalizableUtil.getInstance().writeStrings(out, sharedGroups);
        ExternalizableUtil.getInstance().writeStrings(out, invisibleSharedGroups);
        ExternalizableUtil.getInstance().writeInt(out, recvStatus.getValue());
        ExternalizableUtil.getInstance().writeInt(out, subStatus.getValue());
        ExternalizableUtil.getInstance().writeInt(out, askStatus.getValue());
        ExternalizableUtil.getInstance().writeLong(out, rosterID);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        jid = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        this.groups = new LinkedList<>();
        ExternalizableUtil.getInstance().readStrings(in, groups);
        ExternalizableUtil.getInstance().readStrings(in, sharedGroups);
        ExternalizableUtil.getInstance().readStrings(in, invisibleSharedGroups);
        recvStatus = RecvType.getTypeFromInt(ExternalizableUtil.getInstance().readInt(in));
        subStatus = SubType.getTypeFromInt(ExternalizableUtil.getInstance().readInt(in));
        askStatus = AskType.getTypeFromInt(ExternalizableUtil.getInstance().readInt(in));
        rosterID = ExternalizableUtil.getInstance().readLong(in);
    }
}
