package org.jivesoftware.openfire.muc.spi;

import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * this class supports the simple MUCRoom management including remove,add and query.
 *
 * Note that this implementation provides a representation of rooms that are currently actively loaded in memory only.
 * More rooms might exist in the database.
 *
 * @author <a href="mailto:583424568@qq.com">wuchang</a>
 * 2016-1-14
 */
public class LocalMUCRoomManager
{
    private final Cache<String, MUCRoom> CACHE_ROOM;

    LocalMUCRoomManager(@Nonnull final MultiUserChatService service) {
        CACHE_ROOM= CacheFactory.createCache("MUC Service '" + service.getServiceName() + "' Rooms");
        CACHE_ROOM.setMaxLifetime(-1);
        CACHE_ROOM.setMaxCacheSize(-1L);
    }

    /**
     * Returns the number of chat rooms that are currently actively loaded in memory.
     *
     * @return a chat room count.
     */
    public int getNumberChatRooms(){
        return CACHE_ROOM.size();
    }

    public Lock getLock(@Nonnull final String roomName) {
        return CACHE_ROOM.getLock(roomName);
    }

    public void addRoom(final String roomname, final MUCRoom room) {
        final Lock lock = CACHE_ROOM.getLock(roomname);
        lock.lock();
        try {
            CACHE_ROOM.put(roomname, room);
        } finally {
            lock.unlock();
        }

        GroupEventDispatcher.addListener(room); // TODO this event listener is added only in the node where the room is created. Does this mean that events are not prop
    }

    // TODO As modifications to rooms won't be persisted in the cache without the room having being explicitly put back in the cache,
    //      this method probably needs work. Documentation should be added and/or this should return an Unmodifiable collection (although
    //      that still does not rule out modifications to individual collection items. Can we replace it completely with a 'getRoomNames()'
    //      method, which would then force usage to acquire a lock before operating on a room.
    public Collection<MUCRoom> getRooms(){
        return CACHE_ROOM.values();
    }

    // TODO this should probably not be used without a lock having been acquired and set. Update all usages to do so.
    public MUCRoom getRoom(final String roomname){
        return CACHE_ROOM.get(roomname);
    }
    
    public MUCRoom removeRoom(final String roomname){
        //memory leak will happen if we forget remove it from GroupEventDispatcher
        final Lock lock = CACHE_ROOM.getLock(roomname);
        lock.lock();
        try {
            final MUCRoom room = CACHE_ROOM.remove(roomname);
            if (room != null) {
                GroupEventDispatcher.removeListener(room);
            }
            return room;
        } finally {
            lock.unlock();
        }
    }
    
    public void cleanupRooms(final Date cleanUpDate) {
        final Set<String> roomNames = getRooms().stream().map(MUCRoom::getName).collect(Collectors.toSet());
        for (final String roomName : roomNames) {
            final Lock lock = CACHE_ROOM.getLock(roomName);
            lock.lock();
            try {
                final MUCRoom room = getRoom(roomName);
                if (room.getEmptyDate() != null && room.getEmptyDate().before(cleanUpDate)) {
                    removeRoom(roomName);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
