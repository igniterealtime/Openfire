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
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatServer;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Task requested by each cluster node when a new node joins the cluster. Each existing cluster
 * node will request the list of rooms with occupants <tt>hosted by</tt> the new cluster node.
 *
 * @author Gaston Dombiak
 */
public class GetNewMemberRoomsRequest implements ClusterTask {
    private List<RoomInfo> rooms;

    public GetNewMemberRoomsRequest() {
    }

    public Object getResult() {
        return rooms;
    }

    public void run() {
        rooms = new ArrayList<RoomInfo>();
        // Get rooms that have local occupants and include them in the reply
        MultiUserChatServer mucServer = XMPPServer.getInstance().getMultiUserChatServer();
        for (MUCRoom room : mucServer.getChatRooms()) {
            LocalMUCRoom localRoom = (LocalMUCRoom) room;
            Collection<MUCRole> localOccupants = new ArrayList<MUCRole>();
            for (MUCRole occupant : room.getOccupants()) {
                if (occupant.isLocal()) {
                    localOccupants.add(occupant);
                }
            }
            if (!localOccupants.isEmpty()) {
                rooms.add(new RoomInfo(localRoom, localOccupants));
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
