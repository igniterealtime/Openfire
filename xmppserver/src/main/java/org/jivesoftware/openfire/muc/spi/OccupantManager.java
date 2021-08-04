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
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedTask;
import org.jivesoftware.openfire.muc.cluster.OccupantRemovedTask;
import org.jivesoftware.openfire.muc.cluster.OccupantUpdatedTask;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;
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
     * Maintains state for all occupants of this service.
     */
    @Nonnull
    // FIXME when joining a cluster, the NodeID of the local user changes. This collection needs to be updated then.
    private final ConcurrentMap<NodeID, Set<Occupant>> occupantsByNode = new ConcurrentHashMap<>();

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

        Log.trace("New local occupancy in room '{}' of service '{}': entity '{}' using nickname '{}'", roomJID.getNode(), serviceName, userJID, nickname);
        final OccupantAddedTask task = new OccupantAddedTask(serviceName, roomJID.getNode(), nickname, userJID, XMPPServer.getInstance().getNodeID());
        process(task); // On this cluster node.

        // On all other cluster nodes
        if (PROPERTY_USE_NONBLOCKING_CLUSTERTASKS.getValue()) {
            CacheFactory.doClusterTask(task);
        } else {
            CacheFactory.doSynchronousClusterTask(task, false);
        }
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

        Log.trace("Updated local occupancy in room '{}' of service '{}': entity '{}' now nickname '{}' (was: '{}')", roomJID.getNode(), serviceName, newNickname, oldNickname, userJID);
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

        Log.trace("Removed local occupancy in room '{}' of service '{}': entity '{}' using nickname '{}'", roomJID.getNode(), serviceName, nickname, userJID);
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
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of a new occupant
     */
    public void process(@Nonnull final OccupantAddedTask task)
    {
        final Set<Occupant> occupants = occupantsByNode.getOrDefault(task.getOriginator(), new HashSet<>());
        occupants.add( new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID()));
        occupantsByNode.put(task.getOriginator(), occupants);
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of an update for an existing occupant
     */
    public void process(@Nonnull final OccupantUpdatedTask task)
    {
        final Set<Occupant> occupants = occupantsByNode.getOrDefault(task.getOriginator(), new HashSet<>());
        occupants.remove( new Occupant(task.getRoomName(), task.getOldNickname(), task.getRealJID()) );
        occupants.add( new Occupant(task.getRoomName(), task.getNewNickname(), task.getRealJID()));
        occupantsByNode.put(task.getOriginator(), occupants);
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of a removed occupant
     */
    public void process(@Nonnull final OccupantRemovedTask task)
    {
        final Set<Occupant> occupants = occupantsByNode.getOrDefault(task.getOriginator(), new HashSet<>());
        occupants.remove( new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID()) );
        occupantsByNode.put(task.getOriginator(), occupants);
    }

    /**
     * Returns the name of all the rooms that a particular XMPP entity (user) is currently an occupant of.
     *
     * @param realJID The XMPP address of a user
     * @return All room names that have the user as an occupant.
     */
    @Nonnull
    public Set<String> roomNamesForAddress(@Nonnull final JID realJID) {
        // TODO optimize this. Iterating over all values is very inefficient.
        return occupantsByNode.values().stream()
            .flatMap( Collection::stream )
            .filter( occupant -> occupant.getRealJID().equals(realJID) )
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
        // TODO optimize this. Iterating over all values is very inefficient.

        // Only tracking it for the local cluster node, as those are the only users for which this node will monitor
        // activity anyway.
        occupantsByNode.getOrDefault(XMPPServer.getInstance().getNodeID(), new HashSet<>()).stream()
            .filter(occupant -> occupant.getRealJID().equals(userJid))
            .forEach(occupant -> occupant.setLastActive(Instant.now()));
    }

    /**
     * Counts all users that are in at least one room.
     *
     * @return a user count
     */
    public int numberOfUniqueUsers() {
        return occupantsByNode.values().stream()
            .flatMap( Collection::stream )
            .map(Occupant::getRealJID)
            .collect(Collectors.toSet())
            .size();
    }

    // cluster leave
    public boolean exists(@Nonnull final Occupant occupant) {
        // TODO optimize this. Iterating over all values is very inefficient.
        return occupantsByNode.values().parallelStream().anyMatch( clusterOccupants -> clusterOccupants.contains(occupant) );
    }

    // cluster leave
    @Nonnull
    public Set<Occupant> occupantsForRoomByNode(@Nonnull final String roomName, @Nonnull final NodeID nodeID) {
        return occupantsByNode.getOrDefault(nodeID, Collections.emptySet()).stream()
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
    @Nullable
    public Set<Occupant> leftCluster(@Nonnull final NodeID nodeID) {
        return occupantsByNode.remove(nodeID);
    }

    /**
     * Removes and returns all data that was maintained for cluster nodes other than the local node. It is assumed that
     * this method is used in reaction to the local cluster node having left the cluster.
     *
     * @return All data that this instance maintained for all cluster nodes except the local one.
     */
    @Nullable
    public Set<Occupant> leftCluster() {
        final Set<Occupant> result = new HashSet<>();
        final Iterator<Map.Entry<NodeID, Set<Occupant>>> iterator = occupantsByNode.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<NodeID, Set<Occupant>> entry = iterator.next();
            if (!entry.getKey().equals(XMPPServer.getInstance().getNodeID()) && !entry.getKey().equals(XMPPServer.getInstance().getDefaultNodeID())) {
                result.addAll(entry.getValue());
                iterator.remove();
            }
        }
        return result;
    }

    // TODO This getter was added for the muc-room-cache.jsp page. Possibly public access is not appropriate here.
    @Nonnull
    public ConcurrentMap<NodeID, Set<Occupant>> getOccupantsByNode() {
        return occupantsByNode;
    }

    @Override
    public void roomCreated(JID roomJID) {
        // Not used.
    }

    @Override
    public void roomDestroyed(JID roomJID) {
        // Not used.
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
    }
}
