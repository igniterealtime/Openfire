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
import org.jivesoftware.openfire.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

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
    private String roomName;

    public RoomRemovedEvent() {
    }

    public RoomRemovedEvent(String roomName) {
        this.roomName = roomName;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        MultiUserChatServerImpl mucServer = (MultiUserChatServerImpl) XMPPServer.getInstance().getMultiUserChatServer();
        mucServer.chatRoomRemoved(roomName);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, roomName);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        roomName = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
