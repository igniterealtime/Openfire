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

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that changes the nickname of an existing room occupant in the cluster node. When
 * a room occupant changes his nickname the other cluster nodes, that hold a
 * {@link org.jivesoftware.openfire.muc.spi.RemoteMUCRole} will need to update their local
 * information with the new nickname.
 *
 * @author Gaston Dombiak
 */
public class ChangeNickname extends MUCRoomTask<Void> {
    private String oldNick;
    private String newNick;
    private Presence presence;

    public ChangeNickname() {
    }

    public ChangeNickname(LocalMUCRoom room, String oldNick, String newNick, Presence presence) {
        super(room);
        this.oldNick = oldNick;
        this.newNick = newNick;
        this.presence = presence;
    }

    public String getOldNick() {
        return oldNick;
    }

    public String getNewNick() {
        return newNick;
    }

    public Presence getPresence() {
        return presence;
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
                getRoom().nicknameChanged(ChangeNickname.this);
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeSafeUTF(out, oldNick);
        ExternalizableUtil.getInstance().writeSafeUTF(out, newNick);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        oldNick = ExternalizableUtil.getInstance().readSafeUTF(in);
        newNick = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
