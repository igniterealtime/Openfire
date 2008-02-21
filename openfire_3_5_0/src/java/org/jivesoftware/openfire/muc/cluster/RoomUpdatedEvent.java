/**
 * $RCSfile: $
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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that updates the configuration of a local room. When a room gets updated in a
 * cluster node the rest of the cluster nodes will need to update their copy of the
 * local room.
 *
 * @author Gaston Dombiak
 */
public class RoomUpdatedEvent extends MUCRoomTask {
    private LocalMUCRoom room;

    public RoomUpdatedEvent() {
    }

    public RoomUpdatedEvent(LocalMUCRoom room) {
        super(room);
        this.room = room;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getRoom().updateConfiguration(room);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        room.writeExternal(out);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        room = new LocalMUCRoom();
        room.readExternal(in);
    }
}
