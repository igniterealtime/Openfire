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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

/**
 * Task that destroys the local room in the cluster node. Local room occupants
 * hosted in the cluster node will get the notification of the room being
 * destroyed.
 *
 * @author Gaston Dombiak
 */
public class DestroyRoomRequest extends MUCRoomTask<Void> {
    private JID alternateJID; // Is allowed to be null!
    private String reason; // Is allowed to be null or empty!

    public DestroyRoomRequest() {
    }

    public DestroyRoomRequest(LocalMUCRoom room, JID alternateJID, String reason) {
        super(room);
        this.alternateJID = alternateJID;
        this.reason = reason;
    }

    public DestroyRoomRequest(LocalMUCRoom room, String alternateJID, String reason) {
        super(room);
        this.alternateJID = new JID(alternateJID);
        this.reason = reason;
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
                getRoom().destroyRoom(DestroyRoomRequest.this);
            }
        });
    }

    public JID getAlternateJID() {
        return alternateJID;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeBoolean(out, alternateJID != null);
        if (alternateJID != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, alternateJID);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, reason != null);
        if (reason != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, reason);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            alternateJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            reason = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
    }
}
