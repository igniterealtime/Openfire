/*
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
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;

/**
 * Task that removes a room occupant from the list of occupants in the room. The
 * occupant to remove is actually a {@link org.jivesoftware.openfire.muc.spi.RemoteMUCRole}.
 *
 * @author Gaston Dombiak
 */
public class OccupantLeftEvent extends MUCRoomTask<Void> {
    private MUCRole role;
    private String nickname;
    private JID userAddress;

    public OccupantLeftEvent() {
    }

    public OccupantLeftEvent(LocalMUCRoom room, MUCRole role) {
        super(room);
        this.role = role;
        this.nickname = role.getNickname();
        this.userAddress = role.getUserAddress();
    }

    public MUCRole getRole() {
        if (role == null) {
            try {
                // If there are multiple entries, get one with same full JID
                List<MUCRole> roles = getRoom().getOccupantsByNickname(nickname);
                for (MUCRole r : roles) {
                    if (userAddress.equals(r.getUserAddress())) {
                        role = r;
                        break;
                    }
                }
                // TODO: if no matching full JID, what to do?
            } catch (UserNotFoundException e) {
                // Ignore
            }
        }
        return role;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            @Override
            public void run() {
                getRoom().leaveRoom(OccupantLeftEvent.this);
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        ExternalizableUtil.getInstance().writeSerializable(out, role.getUserAddress());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        userAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
