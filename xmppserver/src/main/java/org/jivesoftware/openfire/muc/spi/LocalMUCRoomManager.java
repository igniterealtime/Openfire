package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * This class supports the simple MUCRoom management including remove, add and query. Its sole invoking entity should be
 * the instance of {@link MultiUserChatService} that is provided as an argument to the constructor. Most of the access
 * modifiers of methods of this class are 'package' to reflect this.
 *
 * @author <a href="mailto:583424568@qq.com">wuchang</a> 2016-1-14
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class LocalMUCRoomManager
{
    private static final Logger Log = LoggerFactory.getLogger(LocalMUCRoomManager.class);

    private final String serviceName;

    private final Cache<String, MUCRoom> CACHE_ROOM;

    /**
     * A cluster-local copy of rooms, used to (re)populating #CACHE_ROOM upon cluster join or leave.
     */
    private final Map<String, MUCRoom> rooms = new HashMap<>();

    LocalMUCRoomManager(@Nonnull final MultiUserChatService service) {
        this.serviceName = service.getServiceName();
        Log.debug("Instantiating for service '{}'", serviceName);
        CACHE_ROOM= CacheFactory.createCache("MUC Service '" + serviceName + "' Rooms");
        CACHE_ROOM.setMaxLifetime(-1);
        CACHE_ROOM.setMaxCacheSize(-1L);
    }

    /**
     * Returns the number of chat rooms that are currently actively loaded in memory.
     *
     * @return a chat room count.
     */
    int getNumberChatRooms()
    {
        final int result = CACHE_ROOM.size();
        Log.trace("Room count for service '{}': {}", serviceName, result);
        return result;
    }

    @Nonnull Lock getLock(@Nonnull final String roomName) {
        Log.trace("Obtaining lock for room '{}' of service '{}'", roomName, serviceName);
        return CACHE_ROOM.getLock(roomName);
    }

    void addRoom(@Nonnull final MUCRoom room) {
        final Lock lock = CACHE_ROOM.getLock(room.getName());
        lock.lock();
        try {
            Log.trace("Adding room '{}' of service '{}'", room.getName(), serviceName);
            CACHE_ROOM.put(room.getName(), room);
            rooms.put(room.getName(), room);
        } finally {
            lock.unlock();
        }

        GroupEventDispatcher.addListener(room); // TODO this event listener is added only in the node where the room is created. Does this mean that events are not propagated in a cluster?
    }

    void syncRoom(@Nonnull final MUCRoom room) {
        final Lock lock = CACHE_ROOM.getLock(room.getName());
        lock.lock();
        try {
            Log.trace("Syncing room '{}' of service '{}'", room.getName(), serviceName);
            if (room.isDestroyed) {
                CACHE_ROOM.remove(room.getName());
                rooms.remove(room.getName());
            } else {
                CACHE_ROOM.put(room.getName(), room);
                rooms.put(room.getName(), room);
            }
        } finally {
            lock.unlock();
        }
    }

    // TODO As modifications to rooms won't be persisted in the cache without the room having being explicitly put back in the cache,
    //      this method probably needs work. Documentation should be added and/or this should return an Unmodifiable collection (although
    //      that still does not rule out modifications to individual collection items. Can we replace it completely with a 'getRoomNames()'
    //      method, which would then force usage to acquire a lock before operating on a room.
    Collection<MUCRoom> getRooms(){
        return CACHE_ROOM.values();
    }

    // TODO this should probably not be used without a lock having been acquired and set. Update all usages to do so.
    MUCRoom getRoom(@Nonnull final String roomName){
        return CACHE_ROOM.get(roomName);
    }
    
    MUCRoom removeRoom(@Nonnull final String roomName){
        //memory leak will happen if we forget remove it from GroupEventDispatcher
        final Lock lock = CACHE_ROOM.getLock(roomName);
        lock.lock();
        try {
            Log.trace("Removing room '{}' of service '{}'", roomName, serviceName);
            final MUCRoom room = CACHE_ROOM.remove(roomName);
            if (room != null) {
                GroupEventDispatcher.removeListener(room);
            }
            rooms.remove(roomName);
            return room;
        } finally {
            lock.unlock();
        }
    }
    
    Duration cleanupRooms(@Nonnull final Date cleanUpDate) {
        Duration totalChatTime = Duration.ZERO;
        final Set<String> roomNames = getRooms().stream().map(MUCRoom::getName).collect(Collectors.toSet());
        for (final String roomName : roomNames) {
            final Lock lock = CACHE_ROOM.getLock(roomName);
            lock.lock();
            try {
                final MUCRoom room = getRoom(roomName);
                if (room.getEmptyDate() != null && room.getEmptyDate().before(cleanUpDate)) {
                    Log.info("Unloading chat room (due to inactivity):" + roomName + "|" + room.getClass().getName());
                    removeRoom(roomName);
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
        Log.trace( "Restoring cache content for cache '{}' by adding all MUC Rooms that are known to the local node.", CACHE_ROOM.getName() );

        for (Map.Entry<String, MUCRoom> entry : rooms.entrySet()) {
            final Lock lock = CACHE_ROOM.getLock(entry.getKey());
            lock.lock();
            try {
                if (!CACHE_ROOM.containsKey(entry.getKey())) {
                    CACHE_ROOM.put(entry.getKey(), entry.getValue());
                } else {
                    final MUCRoom roomInCluster = CACHE_ROOM.get(entry.getKey());
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
