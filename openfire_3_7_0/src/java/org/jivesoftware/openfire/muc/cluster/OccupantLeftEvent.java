/**
 * $RCSfile: $
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

import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that removes a room occupant from the list of occupants in the room. The
 * occupant to remove is actualy a {@link org.jivesoftware.openfire.muc.spi.RemoteMUCRole}.
 *
 * @author Gaston Dombiak
 */
public class OccupantLeftEvent extends MUCRoomTask {
    private MUCRole role;
    private String nickname;

    public OccupantLeftEvent() {
    }

    public OccupantLeftEvent(LocalMUCRoom room, MUCRole role) {
        super(room);
        this.role = role;
        this.nickname = role.getNickname();
    }

    public MUCRole getRole() {
        if (role == null) {
            try {
                role = getRoom().getOccupant(nickname);
            } catch (UserNotFoundException e) {
                // Ignore
            }
        }
        return role;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().leaveRoom(OccupantLeftEvent.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
