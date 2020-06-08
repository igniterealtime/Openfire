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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.spi.LocalMUCRoom;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will remove a room occupant from the list of occupants in the room.
 *
 * @author Gaston Dombiak
 */
public class OccupantAddedEvent extends MUCRoomTask<Void> {
    private Presence presence;
    private int role;
    private int affiliation;
    private boolean voiceOnly;
    private JID roleAddress;
    private JID userAddress;
    private JID reportedFmucAddress;
    private NodeID nodeID;
    private boolean sendPresence;

    public OccupantAddedEvent() {
    }

    public OccupantAddedEvent(LocalMUCRoom room, MUCRole occupant) {
        super(room);
        presence = occupant.getPresence();
        role = occupant.getRole().ordinal();
        affiliation = occupant.getAffiliation().ordinal();
        voiceOnly = occupant.isVoiceOnly();
        roleAddress = occupant.getRoleAddress();
        userAddress = occupant.getUserAddress();
        reportedFmucAddress = occupant.getReportedFmucAddress();
        nodeID = XMPPServer.getInstance().getNodeID();
    }


    public Presence getPresence() {
        return presence;
    }

    public String getNickname() {
        return presence.getFrom().getResource().trim();
    }

    public MUCRole.Role getRole() {
        return MUCRole.Role.values()[role];
    }

    public MUCRole.Affiliation getAffiliation() {
        return MUCRole.Affiliation.values()[affiliation];
    }

    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    public JID getRoleAddress() {
        return roleAddress;
    }

    public JID getUserAddress() {
        return userAddress;
    }

    public JID getReportedFmucAddress() {
        return reportedFmucAddress;
    }

    public NodeID getNodeID() {
        return nodeID;
    }

    /**
     * Sets if the room should broadcast presence of the new occupant to occupants
     * hosted by this cluster node.
     *
     * @param sendPresence true if the room should broadcast presence of the new occupant to occupants
     * hosted by this cluster node.
     */
    public void setSendPresence(boolean sendPresence) {
        this.sendPresence = sendPresence;
    }

    /**
     * Returns true if the room should broadcast presence of the new occupant to occupants
     * hosted by this cluster node.
     *
     * @return true if the room should broadcast presence of the new occupant to occupants
     * hosted by this cluster node.
     */
    public boolean isSendPresence() {
        return sendPresence;
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
                getRoom().occupantAdded(OccupantAddedEvent.this);
            }
        });
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeInt(out, role);
        ExternalizableUtil.getInstance().writeInt(out, affiliation);
        ExternalizableUtil.getInstance().writeBoolean(out, voiceOnly);
        ExternalizableUtil.getInstance().writeSerializable(out, roleAddress);
        ExternalizableUtil.getInstance().writeSerializable(out, userAddress);
        ExternalizableUtil.getInstance().writeBoolean(out, reportedFmucAddress != null);
        if ( reportedFmucAddress != null ) {
            ExternalizableUtil.getInstance().writeSerializable(out, reportedFmucAddress);
        }
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID.toByteArray());
        ExternalizableUtil.getInstance().writeBoolean(out, sendPresence);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        role = ExternalizableUtil.getInstance().readInt(in);
        affiliation = ExternalizableUtil.getInstance().readInt(in);
        voiceOnly = ExternalizableUtil.getInstance().readBoolean(in);
        roleAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        userAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            reportedFmucAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        } else {
            reportedFmucAddress = null;
        }
        nodeID = NodeID.getInstance(ExternalizableUtil.getInstance().readByteArray(in));
        sendPresence = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
