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
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that updates all information regarding a room occupant. Whenever a room
 * occupant gets his affiliation, role, nickname or presence updated the other
 * cluster nodes will need to reflect these changes.
 *
 * @author Gaston Dombiak
 */
public class UpdateOccupant extends MUCRoomTask {
    private Presence presence;
    private String nickname;
    private int role;
    private int affiliation;


    public UpdateOccupant() {
    }

    public UpdateOccupant(LocalMUCRoom room, MUCRole role) {
        super(room);
        this.presence = role.getPresence();
        this.nickname = role.getNickname();
        this.role = role.getRole().ordinal();
        this.affiliation = role.getAffiliation().ordinal();
    }

    public Presence getPresence() {
        return presence;
    }

    public String getNickname() {
        return nickname;
    }

    public MUCRole.Role getRole() {
        return MUCRole.Role.values()[role];
    }

    public MUCRole.Affiliation getAffiliation() {
        return MUCRole.Affiliation.values()[affiliation];
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().occupantUpdated(UpdateOccupant.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        ExternalizableUtil.getInstance().writeInt(out, role);
        ExternalizableUtil.getInstance().writeInt(out, affiliation);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        role = ExternalizableUtil.getInstance().readInt(in);
        affiliation = ExternalizableUtil.getInstance().readInt(in);
    }
}
