/*
 * Copyright (C) 2021 Ignite Realtime Community. All rights reserved.
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
package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MUCEventListener;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedTask;
import org.jivesoftware.openfire.muc.cluster.OccupantKickedForNicknameTask;
import org.jivesoftware.openfire.muc.cluster.OccupantRemovedTask;
import org.jivesoftware.openfire.muc.cluster.OccupantUpdatedTask;
import org.jivesoftware.openfire.muc.cluster.SyncLocalOccupantsAndSendJoinPresenceTask;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Maintains an in-memory inventory of what XMPP entity (user) is in what chatroom, across the entire XMPP cluster.
 *
 * Each instance of this class takes responsibility of maintaining the in-memory representation of occupants of rooms
 * for exactly one instance of {@link org.jivesoftware.openfire.muc.MultiUserChatService}.
 *
 * Some data duplication exists between the data managed by this class, and the data that is managed by the collection
 * of MUCRoom instances that are part of the same MUC service. The MUCRoom managed data is shared across the cluster
 * using a clustered data structure (a clustered {@link org.jivesoftware.util.cache.Cache}). The content of such caches
 * cannot survive certain events related to changes in the composition of the cluster (the local server joining or
 * leaving the cluster). This introduces problems, as, for example, the occupants that are connected to the local server
 * should see occupants connected to a cluster node that is now unavailable 'leave' the chatroom. This requires the
 * local cluster node to retain knowledge, even after the clustered cache has been reset. This implementation therefore
 * by design makes no use of such clustered caches to exchange data with other cluster nodes. Instead, this
 * implementation relies on cluster tasks to share data between cluster nodes. To minimize data transfer and data
 * duplication, the data managed by this implementation is kept to the bare minimum needed to perform post-cluster event
 * maintenance.
 *
 * Apart from the responsibility of maintaining data for post-cluster event maintenance, this implementation adds some
 * conveniences, that include:
 * <ul>
 * <li>A method (that operates on the maintained data) to determine what room names a particular user is in. As this
 *     implementation already maintains this data, obtaining it from this class is more convenient and more performant
 *     than obtaining it from the data that is maintained in the clustered data structure (as that is mapped 'by room')</li>
 * <li>A 'last activity' timestamp for users on the local node (to be used to detect idle users</li>
 * </ul>
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2224">OF-2224</a>
 */
public class OccupantManager implements MUCEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(OccupantManager.class);

    public static final SystemProperty<Boolean> PROPERTY_USE_NONBLOCKING_CLUSTERTASKS = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.occupants.clustertask.non-blocking")
        .setDynamic(true)
        .setDefaultValue(false)
        .build();

    /**`
     * Name of the MUC service that this instance is operating for.
     */
    @Nonnull
    private final String serviceName;

    /**`
     * The (domain-part of the) JID of the MUC service that this instance is operating for.
     */
    @Nonnull
    private final String serviceDomain;

    /**
     * Lookup table for finding occupants by node.
     */
    @Nonnull
    private final ConcurrentMap<NodeID, Set<Occupant>> occupantsByNode = new ConcurrentHashMap<>();

    /**
     * Lookup table for finding nodes by occupant.
     */
    @Nonnull
    private final ConcurrentMap<Occupant, Set<NodeID>> nodesByOccupant = new ConcurrentHashMap<>();

    /**
     * Creates a new instance, specific for the provided MUC service.
     *
     * @param service The service for which the new instance will be operating.
     */
    OccupantManager(@Nonnull final MultiUserChatService service)
    {
        this.serviceName = service.getServiceName();
        this.serviceDomain = service.getServiceDomain();
        Log.debug("Instantiating for service '{}'", serviceName);
    }

    /**
     * Registers disappearance of an existing occupant, and/or appearance of a new occupant, on a specific node.
     *
     * This method maintains the three different occupant lookup tables, and keeps them in sync.
     *
     * @param oldOccupant An occupant that is to be removed from the registration of the referred node (nullable)
     * @param newOccupant An occupant that is to be added to the registration of the referred node (nullable)
     * @param nodeIDToReplaceOccupantFor The id of the node that the old/new occupant need to be (de)registered under. If null then the occupant is (de)registered for each node.
     */
    private void replaceOccupant(Occupant oldOccupant, Occupant newOccupant, NodeID nodeIDToReplaceOccupantFor) {

        Set<NodeID> nodeIDsToReplaceOccupantFor = new HashSet<>();
        if (nodeIDToReplaceOccupantFor == null) {
            nodeIDsToReplaceOccupantFor = occupantsByNode.keySet(); // all node ids
        } else {
            nodeIDsToReplaceOccupantFor.add(nodeIDToReplaceOccupantFor); // just the one
        }

        for (NodeID nodeID : nodeIDsToReplaceOccupantFor) {
            synchronized (nodeID) {
                // Step 1: remove old occupant, if there is any
                deleteOccupantFromNode(oldOccupant, nodeID);

                // Step 2: add new occupant, if there is any
                if (newOccupant != null) {
                    occupantsByNode.computeIfAbsent(nodeID, (n) -> new HashSet<>()).add(newOccupant);
                    nodesByOccupant.computeIfAbsent(newOccupant, (n) -> new HashSet<>()).add(nodeID);
                }

                Log.debug("Replaced occupant {} with {} for node {}", oldOccupant, newOccupant, nodeID);
            }
        }

        Log.debug("Occupants remaining after replace: {}", nodesByOccupant);
    }

    private void deleteOccupantFromNode(Occupant oldOccupant, NodeID nodeID) {
        if (oldOccupant != null) {
            if (occupantsByNode.containsKey(nodeID)) {
                occupantsByNode.get(nodeID).remove(oldOccupant);
                if (occupantsByNode.get(nodeID).isEmpty()) {
                    // Clean up, don't leave behind empty set
                    occupantsByNode.remove(nodeID);
                }
            }
            if (nodesByOccupant.containsKey(oldOccupant)) {
                nodesByOccupant.get(oldOccupant).remove(nodeID);
                if (nodesByOccupant.get(oldOccupant).isEmpty()) {
                    // Clean up, don't leave behind empty set
                    nodesByOccupant.remove(oldOccupant);
                }
            }
        }
    }

    /**
     * Verifies that a JID relates to the service for which this instance is operating, by comparing its domain part.
     *
     * @param jid The JID to check
     * @return True if the JID relates to the service, otherwise false.
     */
    public boolean isForThisService(@Nonnull final JID jid) {
        return jid.getDomain().equals(serviceDomain);
    }

    /**
     * When an XMPP entity / user is registered as a new occupant of a room, this event handler will ensure that this
     * instance of OccupantManager, as well as all instances for the same server on every other cluster node, registers
     * the relevant data that is needed to perform post-cluster event maintenance.
     *
     * @param roomJID the JID of the room where the occupant has joined.
     * @param userJID The 'real' JID (not room-at-service-slash-nickname) of the XMPP user that joined.
     * @param nickname nickname of the user in the room.
     */
    @Override
    public void occupantJoined(JID roomJID, JID userJID, String nickname)
    {
        if (!isForThisService(roomJID)) {
            return;
        }

        final OccupantAddedTask task = registerOccupantJoinedLocally(roomJID, userJID, nickname);

        // On all other cluster nodes
        if (PROPERTY_USE_NONBLOCKING_CLUSTERTASKS.getValue()) {
            CacheFactory.doClusterTask(task);
        } else {
            CacheFactory.doSynchronousClusterTask(task, false);
        }
    }

    public OccupantAddedTask registerOccupantJoinedLocally(JID roomJID, JID userJID, String nickname) {
        Log.debug("New local occupancy in room '{}' of service '{}': entity '{}' using nickname '{}'", roomJID.getNode(), serviceName, userJID, nickname);

        final OccupantAddedTask task = new OccupantAddedTask(serviceName, roomJID.getNode(), nickname, userJID, XMPPServer.getInstance().getNodeID());
        process(task); // On this cluster node.

        return task;
    }

    /**
     * When an XMPP entity / user that is an occupant of a room changes its nickname, this event handler will ensure that
     * the relevant data that is maintained in this instance of OccupantManager, as well as all instances for the same
     * server on every other cluster node, is updated.
     *
     * @param roomJID the JID of the room where the occupant has joined.
     * @param userJID The 'real' JID (not room-at-service-slash-nickname) of the XMPP user that joined.
     * @param oldNickname nickname of the user in the room prior to the change.
     * @param newNickname nickname of the user in the room after the change.
     */
    @Override
    public void nicknameChanged(JID roomJID, JID userJID, String oldNickname, String newNickname)
    {
        if (!isForThisService(roomJID)) {
            return;
        }

        Log.debug("Updated local occupancy in room '{}' of service '{}': entity '{}' now nickname '{}' (was: '{}')", roomJID.getNode(), serviceName, newNickname, oldNickname, userJID);
        final OccupantUpdatedTask task = new OccupantUpdatedTask(serviceName, roomJID.getNode(), oldNickname, newNickname, userJID, XMPPServer.getInstance().getNodeID());
        process(task); // On this cluster node.

        // On all other cluster nodes
        if (PROPERTY_USE_NONBLOCKING_CLUSTERTASKS.getValue()) {
            CacheFactory.doClusterTask(task);
        } else {
            CacheFactory.doSynchronousClusterTask(task, false);
        }
    }

    /**
     * When an XMPP entity / user is removed as an occupant of a room, this event handler will ensure that this
     * instance of OccupantManager, as well as all instances for the same server on every other cluster node, updates
     * the relevant data that it maintains to perform post-cluster event maintenance.
     *
     * @param roomJID the JID of the room where the occupant has left.
     * @param userJID The 'real' JID (not room-at-service-slash-nickname) of the XMPP user that left.
     * @param nickname nickname that the user used in the room.
     */
    @Override
    public void occupantLeft(JID roomJID, JID userJID, String nickname)
    {
        if (!isForThisService(roomJID)) {
            return;
        }

        Log.debug("Removed local occupancy in room '{}' of service '{}': entity '{}' using nickname '{}'", roomJID.getNode(), serviceName, userJID, nickname);
        final OccupantRemovedTask task = new OccupantRemovedTask(serviceName, roomJID.getNode(), nickname, userJID, XMPPServer.getInstance().getNodeID());
        process(task); // On this cluster node.

        // On all other cluster nodes
        if (PROPERTY_USE_NONBLOCKING_CLUSTERTASKS.getValue()) {
            CacheFactory.doClusterTask(task);
        } else {
            CacheFactory.doSynchronousClusterTask(task, false);
        }
    }

    /**
     * When an XMPP entity / user is kicked out of a room because of nickname collision, this event handler will ensure
     * that this instance of OccupantManager, as well as all instances for the same server on every other cluster node,
     * updates the relevant data that it maintains to perform post-cluster event maintenance.
     *
     * @param roomJID the JID of the room where the occupant is kicked out.
     * @param nickname nickname that the user used in the room.
     */
    @Override
    public void occupantNickKicked(JID roomJID, String nickname) {
        Log.debug("Informing nodes about kicking occupant with nick {} from room {} of service {}", nickname, roomJID.getNode(), serviceName);
        final OccupantKickedForNicknameTask task = new OccupantKickedForNicknameTask(serviceName, roomJID.getNode(), nickname, XMPPServer.getInstance().getNodeID());
        process(task); // On this cluster node.

        // On all other cluster nodes
        if (PROPERTY_USE_NONBLOCKING_CLUSTERTASKS.getValue()) {
            CacheFactory.doClusterTask(task);
        } else {
            CacheFactory.doSynchronousClusterTask(task, false);
        }
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of a new occupant
     */
    public void process(@Nonnull final OccupantAddedTask task)
    {
//        LocalTime start = LocalTime.now();
        final Occupant newOccupant = new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID());
        replaceOccupant(null, newOccupant, task.getOriginator());

//        logOccupantData("A new occupant was added on node " + task.getOriginator(), start, null);
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of an update for an existing occupant
     */
    public void process(@Nonnull final OccupantUpdatedTask task)
    {
//        LocalTime start = LocalTime.now();
        final Occupant oldOccupant = new Occupant(task.getRoomName(), task.getOldNickname(), task.getRealJID());
        final Occupant newOccupant = new Occupant(task.getRoomName(), task.getNewNickname(), task.getRealJID());
        replaceOccupant(oldOccupant, newOccupant, task.getOriginator());

//        logOccupantData("An occupant was updated on node " + task.getOriginator(), start, null);
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of a removed occupant
     */
    public void process(@Nonnull final OccupantRemovedTask task)
    {
        Log.debug("Processing task to remove occupant {} - {}", task.getRealJID(), task.getNickname());

//        LocalTime start = LocalTime.now();
        final Occupant oldOccupant = new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID());
        replaceOccupant(oldOccupant, null, task.getOriginator());

        Log.debug("Done processing task to remove occupant {} - {}", task.getRealJID(), task.getNickname());
//        logOccupantData("An occupant was removed on node " + task.getOriginator(), start, null);
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of an occupant nickname that has been kicked out of a room
     */
    public void process(@Nonnull final OccupantKickedForNicknameTask task)
    {
        Log.debug("Processing task to remove everyone with nick {} from room {}", task.getNickname(), task.getRoomName());

//        logOccupantData("Almost processing task to remove everyone with nick " + task.getNickname(), LocalTime.now(), null);
        final Set<Occupant> occupantsToKick = occupantsByNode.values().stream()
            .flatMap(Collection::stream)
            .filter(o -> o.getNickname().equals(task.getNickname()))
            .filter(o -> o.getRoomName().equals(task.getRoomName()))
            .collect(Collectors.toSet());

        occupantsToKick.forEach(o -> replaceOccupant(o, null, null));

        Log.debug("Done processing task to remove everyone with nick {}", task.getNickname());
    }

    /**
     * Used by other nodes telling us about all of their occupants.
     *
     * @param task Cluster task that informs of occupants on a remote node.
     */
    public void process(@Nonnull final SyncLocalOccupantsAndSendJoinPresenceTask task) {

        Set<Occupant> oldOccupants = occupantsByNode.get(task.getOriginator());

        Log.debug("We received a copy of {} local MUC occupants from node {}. We already had {} occupants in local registration for that node.", task.getOccupants().size(), task.getOriginator(), oldOccupants == null ? 0 : oldOccupants.size());

        if (oldOccupants != null) {
            // Use defensive copy to prevent concurrent modification exceptions.
            oldOccupants = new HashSet<>(oldOccupants);
        }
        if (oldOccupants != null) {
            Log.debug("Removing {} old occupants", oldOccupants.size());
            oldOccupants.forEach(oldOccupant -> replaceOccupant(oldOccupant, null, task.getOriginator()));
        }
        if (task.getOccupants() != null) {
            Log.debug("Adding {} new occupants", task.getOccupants().size());
            task.getOccupants().forEach(newOccupant -> replaceOccupant(null, newOccupant, task.getOriginator()));
        }
        if (oldOccupants != null) {
            if (oldOccupants.equals(task.getOccupants())) {
                Log.info("We received a copy of local MUC occupants from node {}, but we already had this information. This hints at a possible inefficient sharing of data across the cluster.", task.getOriginator());
            } else {
                Log.warn("We received a copy of local MUC occupants from node {}, but we already had occupants for this node. However, the new data is different from the old data!", task.getOriginator());
            }
        }

        final Set<Occupant> occupantsAfterSync = occupantsByNode.get(task.getOriginator());
        Log.debug("After processing the sync occupants from node {}, we now have {} occupants in local registration for that node.", task.getOriginator(), occupantsAfterSync == null ? 0 : occupantsAfterSync.size());
    }

    /**
     * Returns the name of all the rooms that a particular XMPP entity (user) is currently an occupant of.
     *
     * @param realJID The XMPP address of a user
     * @return All room names that have the user as an occupant.
     */
    @Nonnull
    public Set<String> roomNamesForAddress(@Nonnull final JID realJID) {
        return nodesByOccupant.keySet().stream()
            .filter(occupant -> realJID.equals(occupant.getRealJID()))
            .map(occupant -> occupant.roomName)
            .collect(Collectors.toSet());
    }

    /**
     * Returns data that is maintained for occupants of the local cluster node.
     *
     * @return all data maintained for the local cluster node.
     */
    @Nonnull
    public Set<Occupant> getLocalOccupants()
    {
        return occupantsByNode.getOrDefault(XMPPServer.getInstance().getNodeID(), Collections.emptySet());
    }

    /**
     * Registers activity for a particular user that is assumed to be connected to the local cluster node. This records
     * a timestamp, that can be used to detect idle-ness.
     *
     * @param userJid The address of the user for which to record activity.
     */
    public void registerActivity(@Nonnull final JID userJid) {

        // Only tracking it for the local cluster node, as those are the only users for which this node will monitor activity anyway
        getLocalOccupants().stream()
            .filter(occupant -> userJid.equals(occupant.getRealJID()))
            .forEach(o -> o.setLastActive(Instant.now()));
    }

    /**
     * Counts all users that are in at least one room.
     *
     * @return a user count
     */
    public int numberOfUniqueUsers() {
        return nodesByOccupant.size();
    }

    /**
     * Checks whether the occupant exists, optionally excluding a specific node from evaluation.
     * @param occupant
     * @param exclude
     * @return
     */
    public boolean exists(@Nonnull final Occupant occupant, @Nullable final NodeID exclude) {
        return nodesByOccupant.getOrDefault(occupant, new HashSet<>()).stream()
            .anyMatch(nodeID -> !nodeID.equals(exclude));
    }

    public boolean exists(@Nonnull final Occupant occupant) {
        return nodesByOccupant.containsKey(occupant);
    }

    @Nonnull
    public Set<Occupant> occupantsForRoomByNode(@Nonnull final String roomName, @Nonnull final NodeID nodeID) {
        return occupantsByNode.getOrDefault(nodeID, Collections.emptySet()).stream()
            .filter(occupant -> occupant.getRoomName().equals(roomName))
            .collect(Collectors.toSet());
    }

    @Nonnull
    public Set<Occupant> occupantsForRoomExceptForNode(@Nonnull final String roomName, @Nonnull final NodeID nodeID) {
        return occupantsByNode.entrySet().stream().filter(e -> !e.getKey().equals(nodeID))
            .map(Map.Entry::getValue)
            .flatMap(Collection::stream)
            .filter(occupant -> occupant.getRoomName().equals(roomName))
            .collect(Collectors.toSet());
    }

    /**
     * Removes and returns all data that was maintained for a particular cluster node. It is assumed that this method
     * is used in reaction to that cluster node having left the cluster.
     *
     * @param nodeID Identifier of the cluster node that left.
     * @return All data that this instance maintained for the cluster node.
     */
    @Nonnull
    public Set<Occupant> leftCluster(@Nonnull final NodeID nodeID) {
        Set<Occupant> occupantsBeingRemoved = occupantsByNode.getOrDefault(nodeID, new HashSet<>());

        // Defensive copy to prevent modifying the returned set
        Set<Occupant> returnValue = new HashSet<>(occupantsBeingRemoved);

        returnValue.forEach(o -> replaceOccupant(o, null, nodeID));

        return returnValue;
    }

    /**
     * Removes and returns all data that was maintained for cluster nodes other than the local node. It is assumed that
     * this method is used in reaction to the local cluster node having left the cluster.
     *
     * @return All data that this instance maintained for all cluster nodes except the local one.
     */
    @Nullable
    public Set<Occupant> leftCluster() {

        final NodeID ownNodeID = XMPPServer.getInstance().getNodeID();

        synchronized (ownNodeID) {
            final Set<Occupant> occupantsLeftOnThisNode = occupantsByNode.getOrDefault(ownNodeID, new HashSet<>());


            final Set<Occupant> occupantsRemoved = occupantsByNode.entrySet().stream()
                .filter(e -> !e.getKey().equals(ownNodeID))
                .flatMap(e -> e.getValue().stream())
                .filter(o -> !occupantsLeftOnThisNode.contains(o))
                .collect(Collectors.toSet());

            // Now actually remove what needs to be removed
            // TODO Somehow perform a lock or synchronize so no other thread can access the lookup tables while we are reorganising
            occupantsByNode.entrySet().removeIf(e -> !e.getKey().equals(ownNodeID));


            nodesByOccupant.clear();
            occupantsLeftOnThisNode.forEach(o -> nodesByOccupant.computeIfAbsent(o, (n) -> new HashSet<>()).add(ownNodeID));

            Log.debug("Reset occupants because we left the cluster");

            return occupantsRemoved;
        }
    }

    @Nonnull
    public Map<NodeID, Set<Occupant>> getOccupantsByNode() {
        return Collections.unmodifiableMap(occupantsByNode);
    }

    @Nonnull
    public Map<Occupant, Set<NodeID>> getNodesByOccupant() {
        return Collections.unmodifiableMap(nodesByOccupant);
    }

    @Override
    public void roomCreated(JID roomJID) {
        // Not used.
    }

    @Override
    public void roomDestroyed(JID roomJID) {
        // When a room is destroyed, remove all registered occupants for that room.
        getNodesByOccupant().entrySet().stream()
            .filter(entry -> entry.getKey().getRoomName().equals(roomJID.getNode()))
            .forEach(entry -> entry.getValue().forEach(nodeID -> replaceOccupant(entry.getKey(), null, nodeID)));
    }

    @Override
    public void messageReceived(JID roomJID, JID user, String nickname, Message message) {
        // Not used.
    }

    @Override
    public void privateMessageRecieved(JID toJID, JID fromJID, Message message) {
        // Not used.
    }

    @Override
    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {
        // Not used.
    }

    /**
     * Representation of a user that is an occupant of a chatroom.
     */
    public static class Occupant {
        String roomName;
        String nickname;
        JID realJID;
        Instant lastActive; // Only used on the local cluster node.

        public Occupant(String roomName, String nickname, JID realJID) {
            this.roomName = roomName;
            this.nickname = nickname;
            this.realJID = realJID;
            this.lastActive = Instant.now();
        }

        public String getRoomName() {
            return roomName;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public JID getRealJID() {
            return realJID;
        }

        public void setRealJID(JID realJID) {
            this.realJID = realJID;
        }

        public Instant getLastActive() {
            return lastActive;
        }

        public void setLastActive(Instant lastActive) {
            this.lastActive = lastActive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Occupant that = (Occupant) o;
            return Objects.equals(roomName, that.roomName) && Objects.equals(nickname, that.nickname) && Objects.equals(realJID, that.realJID);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomName, nickname, realJID);
        }

        @Override
        public String toString() {
            return "Occupant{" +
                "roomName='" + roomName + '\'' +
                ", nickname='" + nickname + '\'' +
                ", realJID=" + realJID +
                ", lastActive=" + lastActive +
                '}';
        }
    }

    /**
     * Logs key data about rooms and occupants.
     * Use only when needed, it clouds up the log.
     * @param reasonForLogging Will be logged to provide context for why this is being logged.
     * @param start Start time of the operation for which this log is created.
     * @param roomCache The room cache.
     */
    public void logOccupantData(String reasonForLogging, LocalTime start, Map<String, MUCRoom> roomCache) {
        LocalTime end = LocalTime.now();
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        List<String> callStack = Arrays.stream(elements).skip(2L).limit(10L).map(StackTraceElement::toString).collect(Collectors.toList());
        Log.debug("================== OCCUPANT MANAGER DATA ==================");
        Log.debug("===                         TIME                        ===");
        Log.debug(
            "=                   {} - {}                   =",
            start.withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME),
            end.withNano(0).format(DateTimeFormatter.ISO_LOCAL_TIME)
        );
        Log.debug("===                        REASON                       ===");
        Log.debug("= {}", reasonForLogging);
        Log.debug("===                  OCCUPANTS BY NODE                  ===");
        Log.debug("=                        COUNT: {}", occupantsByNode.size());
        occupantsByNode.forEach((key, value) -> {
            Log.debug("= {}", key);
            value.forEach(occupant -> Log.debug("= - {}", occupant));
        });
        Log.debug("===                  NODES BY OCCUPANT                  ===");
        Log.debug("=                        COUNT: {}", nodesByOccupant.size());
        nodesByOccupant.forEach((key, value) -> {
            Log.debug("= {}", key);
            value.forEach(nodeID -> Log.debug("= - {}", nodeID));
        });
        Log.debug("===                  ROOM CACHE CONTENTS                ===");
        if (roomCache == null) {
            Log.debug("===                     NOT SPECIFIED                   ===");
        } else {
            roomCache
                .values()
                .forEach(room -> Log.debug("= " + room.getName() + " ---> " + room
                    .getOccupants()
                    .stream()
                    .map(MUCRole::getNickname)
                    .collect(Collectors.joining("/"))
                ));
        }
        Log.debug("===                  TOP OF CALL STACK                  ===");
        callStack.forEach(se -> Log.debug("= {}", se));
        Log.debug("================ END OCCUPANT MANAGER DATA ================");
    }
}
