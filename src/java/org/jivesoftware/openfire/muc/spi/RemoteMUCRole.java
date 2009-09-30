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

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedEvent;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Representation of a room occupant of a local room that is being hosted by
 * another cluster node. An instance of this class will exist for each room
 * occupant that is hosted by another cluster node. Local rooms keep track of
 * local and remote occupants in a transparent way.
 *
 * @author Gaston Dombiak
 */
public class RemoteMUCRole implements MUCRole, Externalizable {
    private String serviceDomain;
    private Presence presence;
    private Role role;
    private Affiliation affiliation;
    private String nickname;
    private boolean voiceOnly;
    private JID roleAddress;
    private JID userAddress;
    private MUCRoom room;
    private NodeID nodeID;

    /**
     * Do not use this constructor. Only used for Externalizable.
     */
    public RemoteMUCRole() {
    }

    public RemoteMUCRole(MultiUserChatService server, OccupantAddedEvent event) {
        this.serviceDomain = server.getServiceDomain();
        presence = event.getPresence();
        role = event.getRole();
        affiliation = event.getAffiliation();
        nickname = event.getNickname();
        voiceOnly = event.isVoiceOnly();
        roleAddress = event.getRoleAddress();
        userAddress = event.getUserAddress();
        room = event.getRoom();
        this.nodeID = event.getNodeID();
    }

    public Presence getPresence() {
        return presence;
    }

    public void setPresence(Presence presence) {
        this.presence = presence;
    }

    public void setRole(Role newRole) {
        this.role = newRole;
    }

    public Role getRole() {
        return role;
    }

    public void setAffiliation(Affiliation newAffiliation) {
        this.affiliation = newAffiliation;
    }

    public Affiliation getAffiliation() {
        return affiliation;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
        setRoleAddress(new JID(room.getName(), serviceDomain, nickname, true));
    }

    private void setRoleAddress(JID jid) {
        roleAddress = jid;
        // Set the new sender of the user presence in the room
        presence.setFrom(jid);
    }

    public String getNickname() {
        return nickname;
    }

    public void destroy() {
        // Do nothing
    }

    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    public MUCRoom getChatRoom() {
        return room;
    }

    public JID getRoleAddress() {
        return roleAddress;
    }

    public JID getUserAddress() {
        return userAddress;
    }

    public boolean isLocal() {
        return false;
    }

    public NodeID getNodeID() {
        return nodeID;
    }

    public void send(Packet packet) {
        XMPPServer.getInstance().getRoutingTable().routePacket(userAddress, packet, false);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, serviceDomain);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeInt(out, role.ordinal());
        ExternalizableUtil.getInstance().writeInt(out, affiliation.ordinal());
        ExternalizableUtil.getInstance().writeSafeUTF(out, nickname);
        ExternalizableUtil.getInstance().writeBoolean(out, voiceOnly);
        ExternalizableUtil.getInstance().writeSerializable(out, roleAddress);
        ExternalizableUtil.getInstance().writeSerializable(out, userAddress);
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID.toByteArray());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        serviceDomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        presence = new Presence((Element)ExternalizableUtil.getInstance().readSerializable(in), true);
        role = Role.values()[ExternalizableUtil.getInstance().readInt(in)];
        affiliation = Affiliation.values()[ExternalizableUtil.getInstance().readInt(in)];
        nickname = ExternalizableUtil.getInstance().readSafeUTF(in);
        voiceOnly = ExternalizableUtil.getInstance().readBoolean(in);
        roleAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        userAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        nodeID = NodeID.getInstance(ExternalizableUtil.getInstance().readByteArray(in));
    }
}
