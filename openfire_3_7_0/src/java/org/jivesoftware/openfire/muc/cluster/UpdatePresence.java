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

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that updates the presence of an occupant in a room. Each time an occupant
 * changes his presence in the room the other cluster nodes will need to get the
 * presence updated too for the occupant.
 *
 * @author Gaston Dombiak
 */
public class UpdatePresence extends MUCRoomTask {
    private Presence presence;
    private String nickname;

    public UpdatePresence() {
    }

    public UpdatePresence(LocalMUCRoom room, Presence presence, String nickname) {
        super(room);
        this.presence = presence;
        this.nickname = nickname;
    }

    public Presence getPresence() {
        return presence;
    }

    public String getNickname() {
        return nickname;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().presenceUpdated(UpdatePresence.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
