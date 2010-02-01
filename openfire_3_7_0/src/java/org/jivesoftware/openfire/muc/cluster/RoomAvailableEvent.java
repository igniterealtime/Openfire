/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
