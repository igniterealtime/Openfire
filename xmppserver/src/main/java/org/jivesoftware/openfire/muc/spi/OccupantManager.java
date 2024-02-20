/*
 * Copyright (C) 2021-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.muc.cluster.*;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    /**
     * Controls if blocking or non-blocking tasks are used when synchronizing MUC occupant data over an Openfire cluster.
     */
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
     * Lookup table for finding occupants by node and their JID.
     *
     * Users of the local domain have a network connection (TCP, BOSH, websocket) to exactly one cluster node. This
     * collection keeps a mapping of cluster nodes to users (to occupants). It is intended to be used to recompute the
     * occupants of the domain when a cluster change occurs (eg: a server leaves the cluster)
     *
     * Users of other domains are not explicitly linked to a particular cluster node, as any cluster node can
     * (re)establish a server-to-server connection to a remote domain. Federated users are therefor not tracked in this
     * map.
     *
     * Occupants are keyed by their 'real JID' property.
     *
     * @see #federatedOccupants Equivalent that contains occupants that are users of non-local XMPP domains.
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2799">Issue OF-2799</a>
     */
    @Nonnull
    @GuardedBy("mutex")
    private final ConcurrentMap<NodeID, Map<JID, Set<Occupant>>> localOccupantsByNode = new ConcurrentHashMap<>();

    /**
     * Lookup table for finding the cluster node for an occupant.
     *
     * This collection contains only users of the local XMPP domain, for the reason documented in the javadoc of the
     * {@link #localOccupantsByNode} field.
     */
    @Nonnull
    @GuardedBy("mutex")
    private final ConcurrentMap<Occupant, NodeID> nodeByLocalOccupant = new ConcurrentHashMap<>();

    /**
     * Lookup table for finding occupants by their JID.
     *
     * This collection contains only users of remote XMPP domains, for the reason documented in the javadoc of the
     * {@link #localOccupantsByNode} field.
     *
     * Occupants are keyed by their 'real JID' property.
     *
     * @see #localOccupantsByNode Equivalent that contains occupants that are users of the local XMPP domain.
     */
    @Nonnull
    @GuardedBy("mutex")
    private final ConcurrentMap<JID, Set<Occupant>> federatedOccupants = new ConcurrentHashMap<>();

    /**
     * A mutex that guards access to the occupantsByNode and nodeByOccupant collections.
     */
    @Nonnull
    private final ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();

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
     * This method maintains the two different occupant lookup tables, and keeps them in sync.
     *
     * Calls to this method must be mode only under guard of a write lock that been obtained from {@link #mutex}.
     *
     * This method <em>only</em> should be used for non-federated/local users. For federated users, use
     * {@link #replaceFederatedOccupant(Occupant, Occupant)}.
     *
     * @param oldOccupant An occupant that is to be removed from the registration of the referred node
     * @param newOccupant An occupant that is to be added to the registration of the referred node
     * @param nodeIDToReplaceOccupantFor The id of the node that the old/new occupant need to be (de)registered under. If null then the occupant is (de)registered for each node.
     */
    @GuardedBy("mutex")
    private void replaceLocalOccupant(@Nullable final Occupant oldOccupant, @Nullable final Occupant newOccupant, @Nullable final NodeID nodeIDToReplaceOccupantFor)
    {
        if (oldOccupant == null && newOccupant == null) {
            throw new IllegalArgumentException("Arguments 'oldOccupant' and 'newOccupant' cannot both be null (but were).");
        }
        if (oldOccupant != null && oldOccupant.isFederated()) {
            throw new IllegalArgumentException("Argument 'oldOccupant' refers to a federated user '" + oldOccupant + "' (use #replaceFederatedOccupant() instead).");
        }
        if (newOccupant != null && newOccupant.isFederated()) {
            throw new IllegalArgumentException("Argument 'newOccupant' refers to a federated user '" + newOccupant + "' (use #replaceFederatedOccupant() instead).");
        }

        Set<NodeID> nodeIDsToReplaceOccupantFor = new HashSet<>();
        if (nodeIDToReplaceOccupantFor == null) {
            nodeIDsToReplaceOccupantFor = localOccupantsByNode.keySet(); // all node ids
        } else {
            nodeIDsToReplaceOccupantFor.add(nodeIDToReplaceOccupantFor); // just the one
        }

        for (NodeID nodeID : nodeIDsToReplaceOccupantFor) {
            // Step 1: remove old occupant, if there is any
            deleteOccupantFromNode(oldOccupant, nodeID);

            // Step 2: add new occupant, if there is any
            if (newOccupant != null) {
                localOccupantsByNode.computeIfAbsent(nodeID, (n) -> new HashMap<>())
                    .computeIfAbsent(newOccupant.getRealJID(), (s) -> new HashSet<>()).add(newOccupant);
                nodeByLocalOccupant.put(newOccupant, nodeID);
            }

            Log.debug("Replaced non-federated occupant {} with {} for node {}", oldOccupant, newOccupant, nodeID);
        }
        Log.debug("Non-federated occupants remaining after replace: {}", nodeByLocalOccupant);
    }

    /**
     * Registers disappearance of an existing occupant on a specific node.
     *
     * Calls to this method must be mode only under guard of a write lock that been obtained from {@link #mutex}.
     *
     * @param oldOccupant An occupant that is to be removed from the registration of the referred node.
     * @param nodeID The ID of the node that the occupant need to be deregistered from.
     */
    @GuardedBy("mutex")
    private void deleteOccupantFromNode(@Nullable final Occupant oldOccupant, @Nonnull final NodeID nodeID)
    {
        if (oldOccupant != null) {
            final Map<JID, Set<Occupant>> occupantsOnNode = localOccupantsByNode.get(nodeID);
            if (occupantsOnNode != null) {
                final Set<Occupant> occupantsForJID = occupantsOnNode.get(oldOccupant.getRealJID());
                if (occupantsForJID != null) {
                    occupantsForJID.remove(oldOccupant);
                    // Clean up, don't leave behind empty entries.
                    if (occupantsForJID.isEmpty()) {
                        occupantsOnNode.remove(oldOccupant.getRealJID());
                        if (occupantsOnNode.isEmpty()) {
                            localOccupantsByNode.remove(nodeID);
                        }
                    }
                }
            }
            nodeByLocalOccupant.remove(oldOccupant);

            // When an occupant is being pinged, but removed from the node, cancel the ping.
            final TimerTask pendingPingTask = oldOccupant.getPendingPingTask();
            if (pendingPingTask != null) {
                Log.debug("Remove pending ping task for {} that is being deleted.", oldOccupant);
                TaskEngine.getInstance().cancelScheduledTask(pendingPingTask);
                oldOccupant.setPendingPingTask(null);
            }
        }
    }

    /**
     * Registers disappearance of an existing occupant, and/or appearance of a new occupant
     *
     * This method maintains the two different occupant lookup tables, and keeps them in sync.
     *
     * Calls to this method must be mode only under guard of a write lock that been obtained from {@link #mutex}.
     *
     * This method <em>only</em> should be used for federated/non-local users. For local users, use
     * {@link #replaceLocalOccupant(Occupant, Occupant, NodeID)}.
     *
     * @param oldOccupant An occupant that is to be removed from the registration of the referred node
     * @param newOccupant An occupant that is to be added to the registration of the referred node
     */
    @GuardedBy("mutex")
    private void replaceFederatedOccupant(@Nullable final Occupant oldOccupant, @Nullable final Occupant newOccupant)
    {
        if (oldOccupant == null && newOccupant == null) {
            throw new IllegalArgumentException("Arguments 'oldOccupant' and 'newOccupant' cannot both be null (but were).");
        }
        if (oldOccupant != null && !oldOccupant.isFederated()) {
            throw new IllegalArgumentException("Argument 'oldOccupant' refers to a local/non-federated user '" + oldOccupant + "' (use #replaceLocalOccupant() instead).");
        }
        if (newOccupant != null && !newOccupant.isFederated()) {
            throw new IllegalArgumentException("Argument 'newOccupant' refers to a local/non-federated user '" + newOccupant + "' (use #replaceLocalOccupant() instead).");
        }

        if (oldOccupant != null) {
            federatedOccupants.computeIfPresent(oldOccupant.getRealJID(), (jid, occupants) -> {
                occupants.remove(oldOccupant);
                return occupants.isEmpty() ? null : occupants; // Delete if no occupants are left.
            });

            // When an occupant is being pinged, but removed from the node, cancel the ping.
            final TimerTask pendingPingTask = oldOccupant.getPendingPingTask();
            if (pendingPingTask != null) {
                Log.debug("Remove pending ping task for {} that is being deleted.", oldOccupant);
                TaskEngine.getInstance().cancelScheduledTask(pendingPingTask);
                oldOccupant.setPendingPingTask(null);
            }
        }
        if (newOccupant != null) {
            federatedOccupants.computeIfAbsent(newOccupant.getRealJID(), (n) -> new HashSet<>()).add(newOccupant);
        }
    }

    /**
     * Verifies that a JID relates to the service for which this instance is operating, by comparing its domain part.
     *
     * @param jid The JID to check
     * @return True if the JID relates to the service, otherwise false.
     */
    public boolean isForThisService(@Nonnull final JID jid)
    {
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
    public void occupantJoined(@Nonnull final JID roomJID, @Nonnull final JID userJID, @Nonnull final String nickname)
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

    @Nonnull
    public OccupantAddedTask registerOccupantJoinedLocally(@Nonnull final JID roomJID, @Nonnull final JID userJID, @Nonnull final String nickname)
    {
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
    public void nicknameChanged(@Nonnull final JID roomJID, @Nonnull final JID userJID, @Nonnull final String oldNickname, @Nonnull final String newNickname)
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
    public void occupantNickKicked(JID roomJID, String nickname)
    {
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
        Log.debug("Processing task to add occupant {} - {}", task.getRealJID(), task.getNickname());

        final Occupant newOccupant = new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID());
        mutex.writeLock().lock();
        try {
            if (newOccupant.isFederated()) {
                replaceFederatedOccupant(null, newOccupant);
            } else {
                replaceLocalOccupant(null, newOccupant, task.getOriginator());
            }
        } finally {
            mutex.writeLock().unlock();
        }
    }

    /**
     * Updates the data maintained by this instance to perform post-cluster event maintenance, based on the data from
     * the clustered task.
     *
     * @param task Cluster task that informs of an update for an existing occupant
     */
    public void process(@Nonnull final OccupantUpdatedTask task)
    {
        Log.debug("Processing task to update occupant {} - {}", task.getRealJID(), task.getOldNickname());

        final Occupant oldOccupant = new Occupant(task.getRoomName(), task.getOldNickname(), task.getRealJID());
        final Occupant newOccupant = new Occupant(task.getRoomName(), task.getNewNickname(), task.getRealJID());

        mutex.writeLock().lock();
        try {
            if (oldOccupant.isFederated()) { // Old and New occupant will both either be federated or not, as they're using the same realJID value.
                replaceFederatedOccupant(oldOccupant, newOccupant);
            } else {
                replaceLocalOccupant(oldOccupant, newOccupant, task.getOriginator());
            }
        } finally {
            mutex.writeLock().unlock();
        }
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

        final Occupant oldOccupant = new Occupant(task.getRoomName(), task.getNickname(), task.getRealJID());
        mutex.writeLock().lock();
        try {
            if (oldOccupant.isFederated()) {
                replaceFederatedOccupant(oldOccupant, null);
            } else {
                replaceLocalOccupant(oldOccupant, null, task.getOriginator());
            }
        } finally {
            mutex.writeLock().unlock();
        }

        Log.debug("Done processing task to remove occupant {} - {}", task.getRealJID(), task.getNickname());
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

        final Set<Occupant> localOccupantsToKick;
        final Set<Occupant> federatedOccupantsToKick;
        mutex.readLock().lock();
        try {
            localOccupantsToKick = localOccupantsByNode.values().stream().map(Map::values)
                .flatMap(Collection::stream)
                .flatMap(Collection::stream)
                .filter(o -> o.getNickname().equals(task.getNickname()))
                .filter(o -> o.getRoomName().equals(task.getRoomName()))
                .collect(Collectors.toSet());

            federatedOccupantsToKick = federatedOccupants.values().stream()
                .flatMap(Collection::stream)
                .filter(o -> o.getNickname().equals(task.getNickname()))
                .filter(o -> o.getRoomName().equals(task.getRoomName()))
                .collect(Collectors.toSet());
        } finally {
            mutex.readLock().unlock(); // A ReentrantReadWriteLock read lock cannot be upgraded to a write lock. First release the read lock.
        }

        mutex.writeLock().lock();
        try {
            localOccupantsToKick.forEach(o -> replaceLocalOccupant(o, null, null));
            federatedOccupantsToKick.forEach(o -> replaceFederatedOccupant(o, null));
        } finally {
            mutex.writeLock().unlock();
        }

        Log.debug("Done processing task to remove everyone with nick {}", task.getNickname());
    }

    /**
     * Used by other nodes telling us about all of their occupants.
     *
     * @param task Cluster task that informs of occupants on a remote node.
     */
    public void process(@Nonnull final SyncLocalOccupantsAndSendJoinPresenceTask task)
    {
        Set<Occupant> oldLocalOccupants;

        mutex.writeLock().lock();
        try {
            final Map<JID, Set<Occupant>> jidSetMap = localOccupantsByNode.get(task.getOriginator());
            if (jidSetMap == null) {
                oldLocalOccupants = null;
            } else {
                oldLocalOccupants = jidSetMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
            }
            Log.debug("We received a copy of {} local MUC occupants from node {}. We already had {} occupants in local registration for that node.", task.getOccupants().size(), task.getOriginator(), oldLocalOccupants == null ? 0 : oldLocalOccupants.size());

            if (oldLocalOccupants != null) {
                // Use defensive copy to prevent concurrent modification exceptions.
                oldLocalOccupants = new HashSet<>(oldLocalOccupants);
            }
            if (oldLocalOccupants != null) {
                Log.debug("Removing {} old local occupants", oldLocalOccupants.size());
                oldLocalOccupants.forEach(oldOccupant -> replaceLocalOccupant(oldOccupant, null, task.getOriginator()));
            }
            if (task.getOccupants() != null) {
                Log.debug("Adding {} new occupants", task.getOccupants().size());
                task.getOccupants().forEach(newOccupant -> {
                    if (newOccupant.isFederated()) {
                        replaceFederatedOccupant(null, newOccupant);
                    } else {
                        replaceLocalOccupant(null, newOccupant, task.getOriginator());
                    }
                });
            }
        } finally {
            mutex.writeLock().unlock(); // Unlock write, still hold read.
        }

        if (oldLocalOccupants != null) {
            if (oldLocalOccupants.equals(task.getOccupants().stream().filter(occupant -> !occupant.isFederated()).collect(Collectors.toSet()))) {
                Log.info("We received a copy of local MUC occupants from node {}, but we already had this information. This hints at a possible inefficient sharing of data across the cluster.", task.getOriginator());
            } else {
                Log.warn("We received a copy of local MUC occupants from node {}, but we already had occupants for this node. However, the new data is different from the old data!", task.getOriginator());
            }
        }
    }

    /**
     * Returns the name of all the rooms that a particular XMPP entity (user) is currently an occupant of.
     *
     * @param realJID The XMPP address of a user
     * @return All room names that have the user as an occupant.
     */
    @Nonnull
    public Set<String> roomNamesForAddress(@Nonnull final JID realJID)
    {
        mutex.readLock().lock();
        try {
            if (XMPPServer.getInstance().isLocal(realJID)) {
                return nodeByLocalOccupant.keySet().stream()
                    .filter(occupant -> realJID.equals(occupant.getRealJID()))
                    .map(occupant -> occupant.roomName)
                    .collect(Collectors.toSet());
            } else {
                return federatedOccupants.getOrDefault(realJID, new HashSet<>()).stream()
                    .filter(occupant -> realJID.equals(occupant.getRealJID()))
                    .map(occupant -> occupant.roomName)
                    .collect(Collectors.toSet());
            }
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Returns data that is maintained for occupants of the local cluster node.
     *
     * The returned occupants include occupants that are local to the XMPP domain as well as federated users.
     *
     * @return all data maintained for the local cluster node.
     */
    @Nonnull
    public Set<Occupant> getLocalOccupants() // Beware: confusing usage of the word 'local' here. Here, it means 'local to the cluster node' and not 'non-federated'.
    {
        mutex.readLock().lock();
        try {
            final Set<Occupant> local = localOccupantsByNode.getOrDefault(XMPPServer.getInstance().getNodeID(), Collections.emptyMap())
                .values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
            final Set<Occupant> federated = federatedOccupants.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

            final Set<Occupant> result = new HashSet<>();
            result.addAll(local);
            result.addAll(federated);

            return result;
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Registers activity for a particular user that is assumed to be connected to the local cluster node. This records
     * a timestamp, that can be used to detect idle-ness.
     *
     * @param userJid The address of the user for which to record activity.
     */
    public void registerActivity(@Nonnull final JID userJid)
    {
        // Only tracking it for the local cluster node, as those are the only users for which this node will monitor activity anyway
        mutex.writeLock().lock();
        try {
            if (XMPPServer.getInstance().isLocal(userJid)) {
                final Map<JID, Set<Occupant>> localOccupants = localOccupantsByNode.get((XMPPServer.getInstance().getNodeID()));
                if (localOccupants != null) {
                    final Set<Occupant> localOccupantsForUser = localOccupants.get(userJid);
                    if (localOccupantsForUser != null) {
                        localOccupantsForUser.forEach(occupant -> occupant.setLastActive(Instant.now()));
                    }
                }
            } else {
                final Set<Occupant> federatedOccupantsForUser = federatedOccupants.get(userJid);
                if (federatedOccupantsForUser != null) {
                    federatedOccupantsForUser.forEach(occupant -> occupant.setLastActive(Instant.now()));
                }
            }
        } finally {
            mutex.writeLock().unlock();
        }
    }

    /**
     * Returns the most recent activity for a particular user that is assumed to be connected to the local cluster node.
     * This returns a timestamp, that can be used to detect idle-ness.
     *
     * @param userJid The address of the user for which to return a timestamp of last activity.
     * @return A timestamp, or null when there currently is no occupant by that JID on the local node.
     */
    @Nullable
    public Instant lastActivityOnLocalNode(@Nonnull final JID userJid)
    {
        mutex.readLock().lock();
        try {
            if (XMPPServer.getInstance().isLocal(userJid)) {
                final Map<JID, Set<Occupant>> localOccupants = localOccupantsByNode.get((XMPPServer.getInstance().getNodeID()));
                if (localOccupants != null) {
                    final Set<Occupant> localOccupantsForUser = localOccupants.get(userJid);
                    if (localOccupantsForUser != null) {
                        return localOccupantsForUser.stream()
                            .map(Occupant::getLastActive)
                            .max(java.util.Comparator.naturalOrder())
                            .orElse(null);
                    }
                }
            } else {
                final Set<Occupant> federatedOccupantsForUser = federatedOccupants.get(userJid);
                if (federatedOccupantsForUser != null) {
                    return federatedOccupantsForUser.stream()
                        .map(Occupant::getLastActive)
                        .max(java.util.Comparator.naturalOrder())
                        .orElse(null);
                }
            }
            return null;
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Counts all users that are in at least one room.
     *
     * @return a user count
     */
    public int numberOfUniqueUsers()
    {
        mutex.readLock().lock();
        try {
            return nodeByLocalOccupant.size() + federatedOccupants.values().stream().mapToInt(Collection::size).sum();
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Checks whether the occupant exists, optionally excluding a specific node from evaluation.
     *
     * @param occupant The subject of the existence check
     * @param exclude An optional node to exclude from the check
     * @return True if the occupant was found, otherwise false.
     */
    public boolean exists(@Nonnull final Occupant occupant, @Nullable final NodeID exclude)
    {
        mutex.readLock().lock();
        try {
            if (occupant.isFederated()) {
                return federatedOccupants.getOrDefault(occupant.getRealJID(), Collections.emptySet()).contains(occupant);
            } else {
                final NodeID node = nodeByLocalOccupant.get(occupant);
                if (node == null) {
                    return false;
                }
                return !node.equals(exclude);
            }
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Checks whether the occupant exists.
     *
     * @param occupant The subject of the existence check
     * @return True if the occupant was found, otherwise false.
     */
    public boolean exists(@Nonnull final Occupant occupant)
    {
        mutex.readLock().lock();
        try {
            if (occupant.isFederated()) {
                return federatedOccupants.getOrDefault(occupant.getRealJID(), Collections.emptySet()).contains(occupant);
            } else {
                return nodeByLocalOccupant.containsKey(occupant);
            }
        } finally {
            mutex.readLock().unlock();
        }
    }

    @Nonnull
    public Set<Occupant> occupantsForRoomByNode(@Nonnull final String roomName, @Nonnull final NodeID nodeID, final boolean includeFederated)
    {
        mutex.readLock().lock();
        try {
            // TODO optimize this in a way that it does not need to iterate over all occupants.
            final Set<Occupant> local = localOccupantsByNode.getOrDefault(nodeID, Collections.emptyMap()).values().stream().flatMap(Collection::stream)
                .filter(occupant -> occupant.getRoomName().equals(roomName))
                .collect(Collectors.toSet());

            final Set<Occupant> result = new HashSet<>();
            result.addAll(local);

            if (includeFederated) {
                final Set<Occupant> federated = federatedOccupants.values().stream().flatMap(Collection::stream)
                    .filter(occupant -> occupant.getRoomName().equals(roomName))
                    .collect(Collectors.toSet());
                result.addAll(federated);
            }

            return result;
        } finally {
            mutex.readLock().unlock();
        }
    }

    @Nonnull
    public Set<Occupant> occupantsForRoomExceptForNode(@Nonnull final String roomName, @Nonnull final NodeID nodeID, final boolean includeFederated)
    {
        mutex.readLock().lock();
        try {
            // TODO optimize this in a way that it does not need to iterate over all occupants.
            final Set<Occupant> result = new HashSet<>();
            for (Map.Entry<NodeID, Map<JID, Set<Occupant>>> entry : localOccupantsByNode.entrySet()) {
                if (entry.getKey().equals(nodeID)) {
                    continue;
                }
                final Map<JID, Set<Occupant>> occupantsOnRemoteNode = entry.getValue();
                for (final Set<Occupant> occupantsForJID : occupantsOnRemoteNode.values()) {
                    for (final Occupant occupantForJID : occupantsForJID) {
                        if (occupantForJID.getRoomName().equals(roomName)) {
                            result.add(occupantForJID);
                        }
                    }
                }
            }

            if (includeFederated) {
                final Set<Occupant> federated = federatedOccupants.values().stream().flatMap(Collection::stream)
                    .filter(occupant -> occupant.getRoomName().equals(roomName))
                    .collect(Collectors.toSet());
                result.addAll(federated);
            }

            return result;
        } finally {
            mutex.readLock().unlock();
        }
    }

    /**
     * Removes and returns all data that was maintained for a particular cluster node. It is assumed that this method
     * is used in reaction to that cluster node having left the cluster.
     *
     * @param nodeID Identifier of the cluster node that left.
     * @return All data that this instance maintained for the cluster node.
     */
    @Nonnull
    public Set<Occupant> leftCluster(@Nonnull final NodeID nodeID)
    {
        // Note: it is assumed that users on federated domains have _not_ lost connectivity. Federation can be
        // re-established from the local server, if it previously was established at the node that has now left.
        Set<Occupant> returnValue;
        mutex.writeLock().lock();
        try {
            Set<Occupant> occupantsBeingRemoved = localOccupantsByNode.getOrDefault(nodeID, new HashMap<>()).values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

            // Defensive copy to prevent modifying the returned set
            returnValue = new HashSet<>(occupantsBeingRemoved);

            returnValue.forEach(o -> replaceLocalOccupant(o, null, nodeID));
        } finally {
            mutex.writeLock().unlock();
        }
        return returnValue;
    }

    /**
     * Removes and returns all data that was maintained for cluster nodes other than the local node. It is assumed that
     * this method is used in reaction to the local cluster node having left the cluster.
     *
     * @return All data that this instance maintained for all cluster nodes except the local one.
     */
    @Nullable
    public Set<Occupant> leftCluster()
    {
        // Note: it is assumed that users on federated domains have _not_ lost connectivity. Federation can be
        // re-established from the local server, if it previously was established at the node that has now left.

        final NodeID ownNodeID = XMPPServer.getInstance().getNodeID();
        mutex.writeLock().lock();
        try {
            final Map<JID, Set<Occupant>> occupantsLeftOnThisNode = localOccupantsByNode.getOrDefault(ownNodeID, new HashMap<>());

            final Set<Occupant> occupantsRemoved = new HashSet<>();
            for (Map.Entry<NodeID, Map<JID, Set<Occupant>>> entry : localOccupantsByNode.entrySet()) {
                if (entry.getKey().equals(ownNodeID)) {
                    continue;
                }
                for (final Map.Entry<JID, Set<Occupant>> occupantsOnRemoteNode : entry.getValue().entrySet()) {
                    final Set<Occupant> localOccupantsForJID = occupantsLeftOnThisNode.get(occupantsOnRemoteNode.getKey());
                    if (localOccupantsForJID == null) {
                        occupantsRemoved.addAll(occupantsOnRemoteNode.getValue());
                    } else {
                        for (final Occupant occupantOnRemoteNode : occupantsOnRemoteNode.getValue()) {
                            if (!localOccupantsForJID.contains(occupantOnRemoteNode)) {
                                occupantsRemoved.add(occupantOnRemoteNode);
                            }
                        }
                    }
                }
            }

            // Now actually remove what needs to be removed
            localOccupantsByNode.entrySet().removeIf(e -> !e.getKey().equals(ownNodeID));

            nodeByLocalOccupant.clear();
            occupantsLeftOnThisNode.values().stream().flatMap(Collection::stream)
                .forEach(o -> nodeByLocalOccupant.put(o, ownNodeID));

            Log.debug("Reset occupants because we left the cluster");
            return occupantsRemoved;
        } finally {
            mutex.writeLock().unlock();
        }
    }

    @Nonnull
    public Map<NodeID, Set<Occupant>> getLocalOccupantsByNode()
    {
        mutex.readLock().lock();
        try {
            final Map<NodeID, Set<Occupant>> result = new HashMap<>();
            for (final Map.Entry<NodeID, Map<JID, Set<Occupant>>> entry : localOccupantsByNode.entrySet()) {
                final Set<Occupant> occupants = entry.getValue().values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
                result.put(entry.getKey(), occupants);
            }
            return Collections.unmodifiableMap(result);
        } finally {
            mutex.readLock().unlock();
        }
    }

    public Set<Occupant> getFederatedOccupants()
    {
        mutex.readLock().lock();
        try {
            return federatedOccupants.values().stream().flatMap(Collection::stream).collect(Collectors.toUnmodifiableSet());
        } finally {
            mutex.readLock().unlock();
        }
    }

    @Nonnull
    public Map<Occupant, NodeID> getNodeByLocalOccupant()
    {
        mutex.readLock().lock();
        try {
            return Collections.unmodifiableMap(nodeByLocalOccupant);
        } finally {
            mutex.readLock().unlock();
        }
    }

    @Override
    public void roomCreated(JID roomJID) {
        // Not used.
    }

    @Override
    public void roomDestroyed(@Nonnull final JID roomJID)
    {
        // When a room is destroyed, remove all registered occupants for that room.
        mutex.writeLock().lock();
        try {
            nodeByLocalOccupant.entrySet().stream()
                .filter(entry -> entry.getKey().getRoomName().equals(roomJID.getNode()))
                .forEach(entry -> replaceLocalOccupant(entry.getKey(), null, entry.getValue()));

            federatedOccupants.values().stream()
                .flatMap(Collection::stream)
                .filter(occupant -> occupant.getRoomName().equals(roomJID.getNode()))
                .forEach(occupant -> replaceFederatedOccupant(occupant, null));
        } finally {
            mutex.writeLock().unlock();
        }
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
    public static class Occupant
    {
        String roomName;
        String nickname;
        JID realJID;
        Instant lastActive; // Only used on the local cluster node.
        Instant lastPingRequest; // Only used on the local cluster node.
        TimerTask pendingPingTask; // Only used on the local cluster node.

        public Occupant(String roomName, String nickname, JID realJID) {
            this.roomName = roomName;
            this.nickname = nickname;
            this.realJID = realJID;
            this.lastActive = Instant.now();
            this.lastPingRequest = null;
            this.pendingPingTask = null;
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

        @Nullable
        public Instant getLastPingRequest() {
            return lastPingRequest;
        }

        @Nullable
        public TimerTask getPendingPingTask() {
            return pendingPingTask;
        }

        public void setPendingPingTask(@Nullable TimerTask pendingPingTask) {
            this.pendingPingTask = pendingPingTask;
            if (pendingPingTask != null) {
                this.lastPingRequest = Instant.now();
            }
        }

        public boolean isFederated() {
            return !XMPPServer.getInstance().isLocal(realJID);
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
            return "Occupant " +
                "'" + nickname + '\'' +
                " of room '" + roomName + '\'' +
                " (real JID '" + realJID +
                "', last active " + lastActive +
                ")";
        }
    }
}
