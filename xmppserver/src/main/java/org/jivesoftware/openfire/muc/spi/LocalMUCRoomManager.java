package org.jivesoftware.openfire.muc.spi;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.event.GroupEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRoom;

/**
 * this class supports the simple LocalMUCRoom management including remove,add and query.
 * @author <a href="mailto:583424568@qq.com">wuchang</a>
 * 2016-1-14
 */
public class LocalMUCRoomManager {
    private final Map<String, LocalMUCRoom> rooms = new ConcurrentHashMap<>();
     
    public int getNumberChatRooms(){
        return rooms.size();
    }
    public void addRoom(final String roomname, final LocalMUCRoom room){
        rooms.put(roomname, room);
        GroupEventDispatcher.addListener(room);
    }
    
    public Collection<LocalMUCRoom> getRooms(){
        return rooms.values();
    }
    
    public LocalMUCRoom getRoom(final String roomname){
        return rooms.get(roomname);
    }
    
    public LocalMUCRoom removeRoom(final String roomname){
        //memory leak will happen if we forget remove it from GroupEventDispatcher
        final LocalMUCRoom room = rooms.remove(roomname);
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
