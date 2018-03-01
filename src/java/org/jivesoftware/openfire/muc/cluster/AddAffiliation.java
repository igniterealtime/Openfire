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

import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

/**
 * Task that adds an affiliation for a room in the other cluster nodes.
 *
 * @author Tom Evans
 */
public class AddAffiliation extends MUCRoomTask<Void> {
    private JID bareJID;
    private MUCRole.Affiliation affiliation;

    public AddAffiliation() {
        super();
    }

    public AddAffiliation(LocalMUCRoom room, JID bareJID, MUCRole.Affiliation affiliation) {
        super(room);
        this.bareJID = bareJID.asBareJID();
        this.affiliation = affiliation;
    }
    
    public AddAffiliation(LocalMUCRoom room, String bareJID, MUCRole.Affiliation affiliation) {
        super(room);
        this.bareJID = new JID(bareJID).asBareJID();
        this.affiliation = affiliation;
    }

    public JID getBareJID() {
        return bareJID;
    }

    public MUCRole.Affiliation getAffiliation() {
        return affiliation;
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
                getRoom().affiliationAdded(AddAffiliation.this);
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, bareJID);
        ExternalizableUtil.getInstance().writeSerializable(out, affiliation);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        bareJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        affiliation = (MUCRole.Affiliation) ExternalizableUtil.getInstance().readSerializable(in);
    }
}
