package org.jivesoftware.openfire.muc.spi;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoom;

/**
 * this class supports the simple MUCRoom management including remove,add and query.
 *
 * Note that this implementation provides a representation of rooms that are currently actively loaded in memory only.
 * More rooms might exist in the database.
 *
 * @author <a href="mailto:583424568@qq.com">wuchang</a>
 * 2016-1-14
 */
public class LocalMUCRoomManager {
    private final Map<String, MUCRoom> rooms = new ConcurrentHashMap<>();

    /**
     * Returns the number of chat rooms that are currently actively loaded in memory.
     *
     * @return a chat room count.
     */
    public int getNumberChatRooms(){
        return rooms.size();
    }

    public void addRoom(final String roomname, final MUCRoom room){
        rooms.put(roomname, room);
        GroupEventDispatcher.addListener(room);
    }
    
    public Collection<MUCRoom> getRooms(){
        return rooms.values();
    }
    
    public MUCRoom getRoom(final String roomname){
        return rooms.get(roomname);
    }
    
    public MUCRoom removeRoom(final String roomname){
        //memory leak will happen if we forget remove it from GroupEventDispatcher
        final MUCRoom room = rooms.remove(roomname);
        if (room != null) {
            GroupEventDispatcher.removeListener(room);
        }
        return room;
    }
    
    public void cleanupRooms(final Date cleanUpDate) {
        for (final MUCRoom room : getRooms()) {
            if (room.getEmptyDate() != null && room.getEmptyDate().before(cleanUpDate)) {
                removeRoom(room.getName());
            }
        }
    }
}
