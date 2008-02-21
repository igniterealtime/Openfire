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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatServer;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to be requested by a node that joins a cluster and be executed in the senior cluster member to get
 * the rooms with occupants. The list of rooms with occupants is returned to the new cluster node so that
 * the new cluster node can be updated and have the same information shared by the cluster.<p>
 *
 * Moreover, each existing cluster node will also need to learn the rooms with occupants that exist in
 * the new cluster node and replicate them. This work is accomplished using {@link GetNewMemberRoomsRequest}.
 *
 * @author Gaston Dombiak
 */
public class SeniorMemberRoomsRequest implements ClusterTask {
    private List<RoomInfo> rooms;

    public SeniorMemberRoomsRequest() {
    }

    public Object getResult() {
        return rooms;
    }

    public void run() {
        rooms = new ArrayList<RoomInfo>();
        // Get rooms that have occupants and include them in the reply
        MultiUserChatServer mucServer = XMPPServer.getInstance().getMultiUserChatServer();
        for (MUCRoom room : mucServer.getChatRooms()) {
            LocalMUCRoom localRoom = (LocalMUCRoom) room;
            if (!room.getOccupants().isEmpty()) {
                rooms.add(new RoomInfo(localRoom, localRoom.getOccupants()));
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Do nothing
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Do nothing
    }
}
