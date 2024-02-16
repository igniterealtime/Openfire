/*
 * Copyright (C) 2016-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.spi.RoutingTableImpl;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * Each instance of this class takes responsibility of maintaining the in-memory representation of MUCRooms for exactly
 * one instance of {@link org.jivesoftware.openfire.muc.MultiUserChatService}, which is expected to be the sole invoking
 * entity. This instance that is provided as an argument to the constructor. This class makes extensive use of the
 * 'package' access modifier to reflect this.
 *
 * It is the responsibility of invoking codes that changes applied to instances managed by this class are made available
 * to other users (eg: cluster nodes). To achieve this, the {@link #sync(MUCRoom)} method must be used. Changes to an
 * instance that are not synced will not be reflected in subsequent instances returned by the various getters in this
 * class (behavior can differ based on the deployment model of Openfire: clustered environments are more susceptible to
 * data loss than a single-server Openfire instance.
 *
 * To control (cluster-wide) access to instances, a MUCRoom-based Lock instance can be obtained through {@link #getLock(String)}.
 *
 * @author <a href="mailto:583424568@qq.com">wuchang</a> 2016-1-14
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LocalMUCRoomManager
{
    private static final Logger Log = LoggerFactory.getLogger(LocalMUCRoomManager.class);

    /**
     * Name of the MUC service that this instance is operating for.
     */
    private final String serviceName;

    /**
     * Chat rooms for this service, mapped by their name.
     *
     * @see #getLocalRooms() which holds content that needs to remain accessible to the local cluster node.
     */
    private final Cache<String, MUCRoom> ROOM_CACHE;

    /**
     * Counters for the data that is in ROOM_CACHE. Used to return statistics without having to iterate over the
     * entire content of ROOM_CACHE.
     */
    private final Cache<String, Long> ROOM_CACHE_STATS;

    /**
     * The key used in {@link #ROOM_CACHE_STATS} to keep track of the amount of non-persistent rooms in the cache.
     */
    private static final String STAT_KEY_ROOMCOUNT_NONPERSISTENT = "Amount of MUC rooms (non-persistent)";

    /**
     * A cluster-local copy of rooms, used to (re)populating #ROOM_CACHE upon cluster join or leave.
     */
    private final Map<String, MUCRoom> localRooms = new HashMap<>();

    /**
     * Creates a new instance, specific for the provided MUC service.
     *
     * @param service The service for which the new instance will be operating.
     */
    LocalMUCRoomManager(@Nonnull final MultiUserChatService service)
    {
        this.serviceName = service.getServiceName();
        Log.debug("Instantiating for service '{}'", serviceName);
        ROOM_CACHE = CacheFactory.createCache("MUC Service '" + serviceName + "' Rooms");
        ROOM_CACHE.setMaxLifetime(-1);
        ROOM_CACHE.setMaxCacheSize(-1L);
        ROOM_CACHE_STATS = CacheFactory.createCache("MUC Service '" + serviceName + "' Room Statistics");
        ROOM_CACHE_STATS.setMaxLifetime(-1);
        ROOM_CACHE_STATS.setMaxCacheSize(-1L);
    }

    /**
     * Returns the number of chat rooms that are currently actively loaded in memory.
     *
     * @return a chat room count.
     */
    int size()
    {
        final int result = ROOM_CACHE.size();
        Log.trace("Room count for service '{}': {}", serviceName, result);
        return result;
    }

    /**
     * Generates a mutex object that controls cluster-wide access to a MUCRoom instance that represents the room in this
     * service identified by the provided name.
     *
     * The lock, once returned, is not acquired/set.
     *
     * @param roomName Name of the room for which to return a lock.
     * @return The lock (which has not been set yet).
     */
    @Nonnull
    Lock getLock(@Nonnull final String roomName)
    {
        Log.trace("Obtaining lock for room '{}' of service '{}'", roomName, serviceName);
        return ROOM_CACHE.getLock(roomName);
    }

    /**
     * Adds a room instance to this manager.
     *
     * @param room The room to be added.
     */
    void add(@Nonnull final MUCRoom room)
    {
        final Lock lock = ROOM_CACHE.getLock(room.getName());
        lock.lock();
        try {
            Log.trace("Adding room '{}' of service '{}'", room.getName(), serviceName);
            final MUCRoom oldValue = ROOM_CACHE.put(room.getName(), room);
            localRooms.put(room.getName(), room);
            updateNonPersistentRoomStat(oldValue, room);
        } finally {
            lock.unlock();
        }

        GroupEventDispatcher.addListener(room); // TODO this event listener is added only in the node where the room is created. Does this mean that events are not propagated in a cluster?
        UserEventDispatcher.addListener(room);
    }

    /**
     * Makes available the current state of the provided MUCRoom instance to all nodes in the Openfire cluster (if the
     * local server is part of such a cluster). This method should be used whenever a MUCRoom instance has been changed.
     *
     * @param room The room for which to persist state changes across the Openfire cluster.
     */
    void sync(@Nonnull final MUCRoom room)
    {
        final Lock lock = ROOM_CACHE.getLock(room.getName());
        lock.lock();
        try {
            Log.trace("Syncing room '{}' of service '{}' (destroy: {})", room.getName(), serviceName, room.isDestroyed);
            if (room.isDestroyed) {
                ROOM_CACHE.remove(room.getName());
                localRooms.remove(room.getName());
                updateNonPersistentRoomStat(null, room);
            } else {
                final MUCRoom oldValue = ROOM_CACHE.put(room.getName(), room);
                localRooms.put(room.getName(), room);
                updateNonPersistentRoomStat(oldValue, room);
            }
        } finally {
            lock.unlock();
        }
    }

    // TODO As modifications to rooms won't be persisted in the cache without the room having being explicitly put back in the cache,
    //      this method probably needs work. Documentation should be added and/or this should return an Unmodifiable collection (although
    //      that still does not rule out modifications to individual collection items. Can we replace it completely with a 'getRoomNames()'
    //      method, which would then force usage to acquire a lock before operating on a room.
    Collection<MUCRoom> getAll()
    {
        return ROOM_CACHE.values();
    }

    /**
     * Retrieve a specific room, if one is currently managed by this instance.
     *
     * Note that when obtaining a room instance using this method, the caller should take responsibility to make sure
     * that any changes to the instance will become visible to other cluster nodes (which is done by invoking
     * {@link #sync(MUCRoom)}. Where appropriate, the caller should apply mutex (as returned by {@link #getLock(String)})
     * to control concurrent access to the returned instance.
     *
     * @param roomName The name of the room to retrieve.
     * @return The room
     */
    @Nullable
    MUCRoom get(@Nonnull final String roomName)
    {
        return ROOM_CACHE.get(roomName);
    }

    /**
     * Removes a room instance from this manager.
     *
     * This method will only remove the instance from management (and trigger appropriate event listeners). It will not
     * remove the room from the database, if it's in there.
     *
     * @param roomName The name of the room to be removed.
     */
    @Nullable
    MUCRoom remove(@Nonnull final String roomName)
    {
        //memory leak will happen if we forget remove it from GroupEventDispatcher
        final Lock lock = ROOM_CACHE.getLock(roomName);
        lock.lock();
        try {
            Log.trace("Removing room '{}' of service '{}'", roomName, serviceName);
            final MUCRoom room = ROOM_CACHE.remove(roomName);
            if (room != null) {
                room.getRoomHistory().purge();
                GroupEventDispatcher.removeListener(room);
                UserEventDispatcher.removeListener(room);
                updateNonPersistentRoomStat(room, null);
            }
            localRooms.remove(roomName);
            return room;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes rooms that have only been inactive since a certain date from memory. This does not destroy the room: it
     * can be reloaded from the database on-demand. Note that this method is expected to operate on persistent rooms
     * only, as non-persistent rooms will be destroyed immediately after the last occupant leaves.
     *
     * @param cleanUpDate The cut-off date.
     * @return The total amount of time that the removed rooms had 'chat-time'.
     */
    Duration unloadInactiveRooms(@Nonnull final Date cleanUpDate)
    {
        Duration totalChatTime = Duration.ZERO;
        final Set<String> roomNames = getAll().stream().map(MUCRoom::getName).collect(Collectors.toSet());
        for (final String roomName : roomNames) {
            final Lock lock = ROOM_CACHE.getLock(roomName);
            lock.lock();
            try {
                final MUCRoom room = get(roomName);
                if (room != null && room.getEmptyDate() != null && room.getEmptyDate().before(cleanUpDate)) {
                    Log.debug("Unloading chat room (due to inactivity): {}", roomName);
                    remove(roomName);
                    totalChatTime = totalChatTime.plus(Duration.ofMillis(room.getChatLength()));
                }
            } finally {
                lock.unlock();
            }
        }
        return totalChatTime;
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining the cluster.
     *
     * This method checks whether local occupant nicknames clash with remote ones. If a clash is detected, both
     * occupants are kicked out of the room.
     *
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#joinedCluster()} or leaving
     *
     * @param occupantManager The occupant manager that contains local occupant registration.
     * @return The set of local occupants that is in the room after processing. This is the original set of local occupants of the room minus any occupants that were kicked out.
     */
    public Set<OccupantManager.Occupant> restoreCacheContentAfterJoin(@Nonnull final OccupantManager occupantManager)
    {
        Log.debug( "Restoring cache content for cache '{}' after we joined the cluster, by adding all MUC Rooms that are known to the local node.", ROOM_CACHE.getName() );

        final Set<OccupantManager.Occupant> localOccupants = occupantManager.getLocalOccupants();
        final Set<OccupantManager.Occupant> occupantsToRetain = new HashSet<>(localOccupants);

        final Map<String, List<OccupantManager.Occupant>> localOccupantByRoom = localOccupants.stream().collect(Collectors.groupingBy(OccupantManager.Occupant::getRoomName));

        // The state of the rooms in the clustered cache should be modified to include our local occupants.
        for (Map.Entry<String, MUCRoom> localRoomEntry : localRooms.entrySet())
        {
            final String roomName = localRoomEntry.getKey();
            Log.trace("Re-adding local room '{}' to cluster cache.", roomName);

            final Lock lock = ROOM_CACHE.getLock(roomName);
            lock.lock();
            try {
                final MUCRoom localRoom = localRoomEntry.getValue();
                if (!ROOM_CACHE.containsKey(roomName)) {
                    Log.trace("Room was not known to the cluster. Added our representation.");
                    ROOM_CACHE.put(roomName, localRoom);
                    updateNonPersistentRoomStat(null, localRoom);
                } else {
                    Log.trace("Room was known to the cluster. Merging our local representation with cluster-provided data.");
                    final MUCRoom roomInCluster = ROOM_CACHE.get(roomName);

                    // Get all occupants that were provided by the local node, and add them to the cluster-representation.
                    final List<OccupantManager.Occupant> localOccupantsToRestore = localOccupantByRoom.get(roomName);
                    if (localOccupantsToRestore != null) {
                        Log.trace("These occupants of the room are recognized as living on our cluster node. Adding them from the cluster-based room: {}", localOccupantsToRestore.stream().map(OccupantManager.Occupant::getRealJID).map(JID::toString).collect(Collectors.joining( ", " )));
                        for (OccupantManager.Occupant localOccupantToRestore : localOccupantsToRestore ) {
                            // Get the Role for the local occupant from the local representation of the room, and add that to the cluster room.
                            final MUCRole localOccupantRole = localRoom.getOccupantByFullJID(localOccupantToRestore.getRealJID());

                            if (localOccupantRole == null) {
                                Log.trace("Trying to add occupant '{}' but no role for that occupant exists in the local room. Data inconsistency?", localOccupantToRestore.getRealJID());
                                continue;
                            } else {
                                Log.trace("Found localOccupantRole {} for localOccupantToRestore {}, client route = {}", localOccupantRole, localOccupantToRestore.getRealJID(), XMPPServer.getInstance().getRoutingTable().getClientRoute(localOccupantToRestore.getRealJID()));
                            }

                            // OF-2165
                            // Check if the nickname of this occupant already existed for another user in the room.
                            // If it did, we need to kick the users out. With sincere apologies.
                            String nickBeingAddedToRoom = localOccupantRole.getNickname();
                            boolean occupantWasKicked = false;
                            try {
                                final List<MUCRole> existingOccupantsWithSameNick = roomInCluster.getOccupantsByNickname(nickBeingAddedToRoom);
                                final List<JID> otherUsersWithSameNick = existingOccupantsWithSameNick.stream().map(MUCRole::getUserAddress).filter(bareJid -> !bareJid.equals(localOccupantRole.getUserAddress())).collect(Collectors.toList());
                                if (!otherUsersWithSameNick.isEmpty()) {

                                    // We will be routing presences to several users. The routing table may not have
                                    // finished updating the client routes. However those are needed for routing the
                                    // stanzas, specifically the local client route. So do that first.
                                    RoutingTable routingTable = XMPPServer.getInstance().getRoutingTable();
                                    if (routingTable instanceof RoutingTableImpl) {
                                        RoutingTableImpl.class.cast(routingTable).addLocalClientRoutesToCache();
                                    }

                                    // There is at least one remote occupant, being a different user, with the same nick.
                                    // Kick all.
                                    otherUsersWithSameNick.forEach(jid -> kickOccupantBecauseOfNicknameCollision(roomInCluster, nickBeingAddedToRoom, jid, occupantManager));
                                    final JID localUserToBeKickedFullJid = localOccupantToRestore.getRealJID();

                                    // Now kick the local user. It has to be added to the room for a short instant so that it can actually be kicked out.
                                    // Normally we would do this with:
//                                          roomInCluster.addOccupantRole(localOccupantRole);
                                    // But that notifies other nodes as well about the new occupant. We don't want that, this is
                                    // entirely a local affair. Therefore perform two separate steps instead, without invoking
                                    // occupant joined events.
                                    roomInCluster.occupants.add(localOccupantRole);
                                    occupantManager.registerOccupantJoinedLocally(localOccupantRole.getRoleAddress().asBareJID(), localOccupantRole.getUserAddress(), localOccupantRole.getNickname());

                                    // Just added. Now kick out.
                                    kickOccupantBecauseOfNicknameCollision(roomInCluster, nickBeingAddedToRoom, localUserToBeKickedFullJid, occupantManager);

                                    // Inform other nodes of the kick, so they can remove the occupants from their occupant registration
                                    occupantManager.occupantNickKicked(roomInCluster.getJID(), nickBeingAddedToRoom);

                                    occupantWasKicked = true;
                                }
                            } catch (UserNotFoundException e) {
                                // This is actually the happy path. There is no remote occupant in the room with the same nick. Proceed.
                            }

                            if (!occupantWasKicked) {
                                roomInCluster.addOccupantRole(localOccupantRole);
                            } else {
                                occupantsToRetain.remove(localOccupantToRestore);
                            }
                        }
                    }

                    if (!roomInCluster.equals(localRoom)) {
                        // TODO: unsure if #equals() is enough to verify equality here.
                        Log.warn("Joined an Openfire cluster on which a room exists that clashes with a room that exists locally. Room name: '{}' on service '{}'", roomName, serviceName);
                        // TODO: handle collision. Two nodes have different rooms using the same name.
                    }

                    // Sync room back to make cluster aware of changes.
                    Log.debug("Re-added local room '{}' to cache, with occupants: {}", roomName, roomInCluster.getOccupants().stream().map(MUCRole::getUserAddress).map(JID::toString).collect(Collectors.joining( ", " )));
                    ROOM_CACHE.put(roomName, roomInCluster);
                    // The implementation of this method does not allow configuration to be changed that warrants a update toe ROOM_CACHE_STATS

                    // TODO: update the local copy of the room with occupants, maybe?
                }
            } finally {
                lock.unlock();
            }
        }

        // Add a cluster listener to clean up locally stored data when another cluster node removes it from the cache.
        ROOM_CACHE.addClusteredCacheEntryListener(new ClusteredCacheEntryListener<String, MUCRoom>() {
            @Override
            public void entryAdded(@Nonnull String key, @Nullable MUCRoom newValue, @Nonnull NodeID nodeID) {
            }

            @Override
            public void entryRemoved(@Nonnull String key, @Nullable MUCRoom oldValue, @Nonnull NodeID nodeID) {
                localRooms.remove(key);
                final MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
                if (service != null) {
                    service.getOccupantManager().roomDestroyed(new JID(key, service.getServiceDomain(), null));
                }
            }

            @Override
            public void entryUpdated(@Nonnull String key, @Nullable MUCRoom oldValue, @Nullable MUCRoom newValue, @Nonnull NodeID nodeID) {
            }

            @Override
            public void entryEvicted(@Nonnull String key, @Nullable MUCRoom oldValue, @Nonnull NodeID nodeID) {
                localRooms.remove(key);
                final MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(serviceName);
                if (service != null) {
                    service.getOccupantManager().roomDestroyed(new JID(key, service.getServiceDomain(), null));
                }
            }

            @Override
            public void mapCleared(@Nonnull NodeID nodeID) {
            }

            @Override
            public void mapEvicted(@Nonnull NodeID nodeID) {
            }
        }, false, false);

        return occupantsToRetain;
    }

    /**
     * Kick a user out of a room for reason of nickname collision.
     * @param room The room to kick the user out of.
     * @param nickBeingAddedToRoom The nickname that is the cause of the problem.
     * @param userToBeKicked The full jid of the user to be kicked.
     * @param occupantManager The occupant manager that contains local occupant registration.
     */
    private void kickOccupantBecauseOfNicknameCollision(MUCRoom room, String nickBeingAddedToRoom, JID userToBeKicked, @Nonnull OccupantManager occupantManager) {
        Log.info(
            "Occupant {} of room {} with nickname {} has to be kicked out because the nickname clashes with another user in the same room.",
            userToBeKicked,
            room.getName(),
            nickBeingAddedToRoom
        );

        // Kick the user from all the rooms that he/she had previously joined.
        try {
            final Presence kickedPresence = room.kickOccupant(userToBeKicked, null, null, "Nickname clash with other user in the same room.");

            Log.trace("Kick presence to be sent to room: {}", kickedPresence);

            // Send the updated presence to the room occupants, but only those on this local node.
            room.send(kickedPresence, room.getRole());

            Log.debug("Kicked occupant '{}' out of room '{}'.", userToBeKicked, room.getName());
        } catch (final NotAllowedException e) {
            // Do nothing since we cannot kick owners or admins
            Log.debug("Occupant '{}' not kicked out of room '{}' because of '{}'.", userToBeKicked, room.getName(), e.getMessage());
        }
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after leaving the cluster.
     *
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#leftCluster()} a cluster.
     */
    void restoreCacheContentAfterLeave(@Nullable final Set<OccupantManager.Occupant> occupantsOnRemovedNodes)
    {
        Log.debug( "Restoring cache content for cache '{}' after we left the cluster, by adding all MUC Rooms that are known to the local node.", ROOM_CACHE.getName() );

        final Map<String, List<OccupantManager.Occupant>> occupantsOnRemovedNodesByRoom;
        if (occupantsOnRemovedNodes == null) {
            occupantsOnRemovedNodesByRoom = Collections.emptyMap();
        } else {
            occupantsOnRemovedNodesByRoom = occupantsOnRemovedNodes.stream().collect(Collectors.groupingBy(OccupantManager.Occupant::getRoomName));
        }

        for (Map.Entry<String, MUCRoom> localRoomEntry : localRooms.entrySet()) {
            final String roomName = localRoomEntry.getKey();
            Log.trace("Re-adding local room '{}' to cluster cache.", roomName);
            final Lock lock = ROOM_CACHE.getLock(roomName);
            lock.lock();
            try {
                final MUCRoom room = localRoomEntry.getValue();

                // The state of the rooms in the clustered cache should be modified to remove all but our local occupants.
                final List<OccupantManager.Occupant> occupantsToRemove = occupantsOnRemovedNodesByRoom.get(roomName);
                if (occupantsToRemove != null) {
                    Log.trace("These occupants of the room are recognized as living on another cluster node. Removing them from the room: {}", occupantsToRemove.stream().map(OccupantManager.Occupant::getRealJID).map(JID::toString).collect(Collectors.joining( ", " )));
                    for (OccupantManager.Occupant occupantToRemove : occupantsToRemove) {
                        final MUCRole occupantRole = room.getOccupantByFullJID(occupantToRemove.getRealJID());
                        if (occupantRole == null) {
                            Log.trace("Trying to remove occupant '{}' but no role for that occupant exists in the room. Data inconsistency?", occupantToRemove.getRealJID());
                            continue;
                        }
                        room.removeOccupantRole(occupantRole);
                    }
                }

                // Place room in cluster cache.
                Log.trace("Re-added local room '{}' to cache, with occupants: {}", roomName, room.getOccupants().stream().map(MUCRole::getUserAddress).map(JID::toString).collect(Collectors.joining( ", " )));
                ROOM_CACHE.put(roomName, room);
            } finally {
                lock.unlock();
            }
        }
        recomputeNonPersistentRoomCount();
    }

    /**
     * This method detects rooms that we know of 'locally' (in the data structure that supports the room cache), but which
     * are not (no longer) in the cache.
     *
     * When a cluster node crashes out of the cluster (eg: network interruption), it has been observed that the cache can
     * 'break'. Presumably, the affected cache entry wasn't "physically" stored on the server, and as the network connection
     * is gone, a backup cannot be obtained either.
     *
     * This method attempts to identify, remove and return rooms that are lost in these cases, which is intended to be
     * used (only) when processing a "cluster break" event.
     *
     * @return room names that were known to the local server, but not (any more) in the clustered cache.
     */
    @Nonnull
    public synchronized Set<String> detectAndRemoveLostRooms()
    {
        Log.debug("Looking for rooms that have 'dropped out' of the cache (likely as a result of a network failure).");

        final Set<String> localRoomNames = localRooms.keySet();
        final Set<String> cachedRoomNames = ROOM_CACHE.keySet();
        final Set<String> roomNamesNotInCache = new HashSet<>(localRoomNames);
        roomNamesNotInCache.removeAll(cachedRoomNames);

        if (roomNamesNotInCache.isEmpty()) {
            Log.debug("Found no rooms that are missing from the cache.");
        } else {
            Log.info("Found {} rooms that we know locally, but are not (no longer) in the cache. This can occur when a cluster node fails, but should not occur otherwise. Missing rooms: {}", roomNamesNotInCache.size(), String.join(", ", roomNamesNotInCache));
            localRooms.keySet().removeAll(roomNamesNotInCache);
        }
        return roomNamesNotInCache;
    }

    public Cache<String, MUCRoom> getROOM_CACHE() {
        return ROOM_CACHE;
    }

    public Map<String, MUCRoom> getLocalRooms() {
        return localRooms;
    }

    /**
     * Modifies the statistic in {@link #ROOM_CACHE_STATS} that keeps a count of all non-persisted MUC rooms
     * (key: {@link #STAT_KEY_ROOMCOUNT_NONPERSISTENT}), based on a rooms that are removed from or added to {@link #ROOM_CACHE}
     *
     * @param oldValue a room that was removed from {@link #ROOM_CACHE}
     * @param newValue a room that was added to {@link #ROOM_CACHE}
     */
    private void updateNonPersistentRoomStat(@Nullable final MUCRoom oldValue, @Nullable final MUCRoom newValue)
    {
        int delta = 0;
        if (oldValue != null && !oldValue.isPersistent()) {
            delta--;
        }
        if (newValue != null && !newValue.isPersistent()) {
            delta++;
        }
        if (delta < 0) {
            decrementStatistic(STAT_KEY_ROOMCOUNT_NONPERSISTENT);
        } else if (delta > 0) {
            incrementStatistic(STAT_KEY_ROOMCOUNT_NONPERSISTENT);
        }
    }

    /**
     * Increments (+1) a number-based value of a statistic as maintained in {@link #ROOM_CACHE_STATS}.
     *
     * @param key the key used to store the statistic in the cache.
     */
    private void incrementStatistic(@Nonnull final String key)
    {
        final Lock lock = ROOM_CACHE_STATS.getLock(key);
        lock.lock();
        try {
            Long count = ROOM_CACHE_STATS.getOrDefault(key, 0L);
            count++;
            ROOM_CACHE_STATS.put(key, count);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Decrements (-1) a number-based value of a statistic as maintained in {@link #ROOM_CACHE_STATS}.
     *
     * @param key the key used to store the statistic in the cache.
     */
    private void decrementStatistic(@Nonnull final String key)
    {
        final Lock lock = ROOM_CACHE_STATS.getLock(key);
        lock.lock();
        try {
            Long count = ROOM_CACHE_STATS.getOrDefault(key, 0L);
            count--;
            ROOM_CACHE_STATS.put(key, count);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Iterates over all MUC rooms (in the cache, non-cached rooms are obviously not non-persistent) and counts the
     * number of non-persistent rooms.
     *
     * This method is more resource intensive, but perhaps more accurate, than {@link #getNonPersistentRoomCount()}.
     *
     * @return The count of non-persistent MUC rooms.
     */
    public long recomputeNonPersistentRoomCount()
    {
        final long count = getAll().stream().filter(room -> !room.isPersistent()).count();
        final Lock lock = ROOM_CACHE_STATS.getLock(STAT_KEY_ROOMCOUNT_NONPERSISTENT);
        lock.lock();
        try {
            final Long oldCount = ROOM_CACHE_STATS.put(STAT_KEY_ROOMCOUNT_NONPERSISTENT, count);
            if (oldCount != null && oldCount != count) {
                Log.warn("Recomputed the amount of non persistent MUC rooms. The amount registered was {}, while the new count is {}", oldCount, count);
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a count of all rooms that are non-persistent.
     *
     * The statistic returned by this method is based on a derived value. It is not based on a direct re-evaluation of
     * each room.
     *
     * This method is not as resource intensive, but perhaps less accurate, than {@link #recomputeNonPersistentRoomCount()}.
     *
     * @return The count of non-persistent MUC rooms.
     */
    public long getNonPersistentRoomCount() {
        final Lock lock = ROOM_CACHE_STATS.getLock(STAT_KEY_ROOMCOUNT_NONPERSISTENT);
        lock.lock();
        try {
            return ROOM_CACHE_STATS.getOrDefault(STAT_KEY_ROOMCOUNT_NONPERSISTENT, 0L);
        } finally {
            lock.unlock();
        }
    }
}
