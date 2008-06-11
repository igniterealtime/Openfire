/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task related to a room to be executed in a cluster node. This is a base
 * class to specific room tasks. The base class just keeps track of the room
 * related to the task.
 *
 * @author Gaston Dombiak
 */
public abstract class MUCRoomTask implements ClusterTask {
    private boolean originator;
    private String roomName;
    private String subdomain;

    protected MUCRoomTask() {
    }

    protected MUCRoomTask(LocalMUCRoom room) {
        this.roomName = room.getName();
        this.subdomain = room.getMUCService().getServiceName();
    }

    public LocalMUCRoom getRoom() {
        MultiUserChatService mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        if (mucService == null) {
            throw new IllegalArgumentException("MUC service not found for subdomain: "+subdomain);
        }
        LocalMUCRoom room = (LocalMUCRoom) mucService.getChatRoom(roomName);
        if (room == null) {
            throw new IllegalArgumentException("Room not found: " + roomName);
        }
        return room;
    }

    /**
     * Executes the requested task considering that this JVM may still be joining the cluster.
     * This means that events regarding rooms that were not loaded yet will be stored for later
     * processing. Once the JVM is done joining the cluster queued tasks will be processed.
     *
     * @param runnable the task to execute.
     */
    protected void execute(Runnable runnable) {
        // Check if we are joining a cluster
        boolean clusterStarting = ClusterManager.isClusteringStarting();
        try {
            // Check that the room exists
            getRoom();
            // Room was found so now execute the task
            runnable.run();
        }
        catch (IllegalArgumentException e) {
            // Room not found so check if we are still joining the cluster
            if (clusterStarting) {
                // Queue task in case the cluster
                QueuedTasksManager.getInstance().addTask(this);
            }
            else {
                // Task failed since room was not found
                Log.error(e);
            }
        }
    }

    public boolean isOriginator() {
        return originator;
    }

    public void setOriginator(boolean originator) {
        this.originator = originator;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeBoolean(out, originator);
        ExternalizableUtil.getInstance().writeSafeUTF(out, roomName);
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        originator = ExternalizableUtil.getInstance().readBoolean(in);
        roomName = ExternalizableUtil.getInstance().readSafeUTF(in);
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
