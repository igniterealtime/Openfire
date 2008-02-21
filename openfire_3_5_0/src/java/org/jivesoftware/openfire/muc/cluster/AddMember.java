/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that adds a new member to the room in the other cluster nodes.
 *
 * @author Gaston Dombiak
 */
public class AddMember extends MUCRoomTask {
    private String bareJID;
    private String nickname;

    public AddMember() {
        super();
    }

    public AddMember(LocalMUCRoom room, String bareJID, String nickname) {
        super(room);
        this.bareJID = bareJID;
        this.nickname = nickname;
    }

    public String getBareJID() {
        return bareJID;
    }

    public String getNickname() {
        return nickname;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        getRoom().memberAdded(this);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, bareJID);
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        bareJID = ExternalizableUtil.getInstance().readSafeUTF(in);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
