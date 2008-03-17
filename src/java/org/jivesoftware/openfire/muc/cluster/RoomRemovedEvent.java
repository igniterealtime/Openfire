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
 * Task that will remove a local room from the cluster node. When a room is destroyed
 * in a cluster node the rest of the cluster nodes will need to destroy their copy
 * and send notifications to the room occupants hosted in the local cluster node.
 *
 * @author Gaston Dombiak
 */
public class RoomRemovedEvent implements ClusterTask {
    private LocalMUCRoom room;

    public RoomRemovedEvent() {
    }

    public RoomRemovedEvent(LocalMUCRoom room) {
        this.room = room;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MultiUserChatService mucService = room.getMUCService();
        mucService.chatRoomRemoved(room);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        room.writeExternal(out);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        room = new LocalMUCRoom();
        room.readExternal(in);
    }
}
