/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that adds a new local room to the cluster node. When a room is created in a
 * cluster node the  rest of the cluster nodes will need to get the new room added
 * in their list of existing rooms.
 *
 * @author Gaston Dombiak
 */
public class RoomAvailableEvent implements ClusterTask {
    private LocalMUCRoom room;

    public RoomAvailableEvent() {
    }

    public RoomAvailableEvent(LocalMUCRoom room) {
        this.room = room;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MultiUserChatService mucService = room.getMUCService();
        mucService.chatRoomAdded(room);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        room.writeExternal(out);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        room = new LocalMUCRoom();
        room.readExternal(in);
    }
}
