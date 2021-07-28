/*
 * Copyright (C) 2016-2021 Ignite Realtime Community. All rights reserved.
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

import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
class LocalMUCRoomManager
{
    private static final Logger Log = LoggerFactory.getLogger(LocalMUCRoomManager.class);

    /**
     * Name of the MUC service that this instance is operating for.
     */
    private final String serviceName;

    /**
     * Chat rooms for this service, mapped by their name.
     */
    private final Cache<String, MUCRoom> ROOM_CACHE;

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
            ROOM_CACHE.put(room.getName(), room);
            localRooms.put(room.getName(), room);
        } finally {
            lock.unlock();
        }

        GroupEventDispatcher.addListener(room); // TODO this event listener is added only in the node where the room is created. Does this mean that events are not propagated in a cluster?
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
            } else {
                ROOM_CACHE.put(room.getName(), room);
                localRooms.put(room.getName(), room);
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
                GroupEventDispatcher.removeListener(room);
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
                    Log.info("Unloading chat room (due to inactivity):" + roomName + "|" + room.getClass().getName());
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
     * in the cache. It is expected to be invoked right after joining
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#joinedCluster()} or leaving
     * ({@link org.jivesoftware.openfire.cluster.ClusterEventListener#leftCluster()} a cluster.
     */
    void restoreCacheContent() {
        Log.trace( "Restoring cache content for cache '{}' by adding all MUC Rooms that are known to the local node.", ROOM_CACHE.getName() );

        for (Map.Entry<String, MUCRoom> entry : localRooms.entrySet()) {
            final Lock lock = ROOM_CACHE.getLock(entry.getKey());
            lock.lock();
            try {
                if (!ROOM_CACHE.containsKey(entry.getKey())) {
                    ROOM_CACHE.put(entry.getKey(), entry.getValue());
                } else {
                    final MUCRoom roomInCluster = ROOM_CACHE.get(entry.getKey());
                    if (!roomInCluster.equals(entry.getValue())) { // TODO: unsure if #equals() is enough to verify equality here.
                        Log.warn("Joined an Openfire cluster on which a room exists that clashes with a room that exists locally. Room name: '{}' on service '{}'", entry.getKey(), serviceName);
                        // FIXME handle collision. Two nodes have different rooms using the same name.
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
