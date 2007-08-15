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
        getRoom().leaveRoom(this);
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
