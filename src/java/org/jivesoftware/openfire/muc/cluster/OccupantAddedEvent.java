/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
public class OccupantAddedEvent extends MUCRoomTask {
    private Presence presence;
    private int role;
    private int affiliation;
    private boolean voiceOnly;
    private JID roleAddress;
    private JID userAddress;
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
        nodeID = XMPPServer.getInstance().getNodeID();
    }


    public Presence getPresence() {
        return presence;
    }

    public String getNickname() {
        return presence.getTo().getResource().trim();
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

    public Object getResult() {
        return null;
    }

    public void run() {
        // Execute the operation considering that we may still be joining the cluster
        execute(new Runnable() {
            public void run() {
                getRoom().occupantAdded(OccupantAddedEvent.this);
            }
        });
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeInt(out, role);
        ExternalizableUtil.getInstance().writeInt(out, affiliation);
        ExternalizableUtil.getInstance().writeBoolean(out, voiceOnly);
        ExternalizableUtil.getInstance().writeSerializable(out, roleAddress);
        ExternalizableUtil.getInstance().writeSerializable(out, userAddress);
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID.toByteArray());
        ExternalizableUtil.getInstance().writeBoolean(out, sendPresence);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        role = ExternalizableUtil.getInstance().readInt(in);
        affiliation = ExternalizableUtil.getInstance().readInt(in);
        voiceOnly = ExternalizableUtil.getInstance().readBoolean(in);
        roleAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        userAddress = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        nodeID = NodeID.getInstance(ExternalizableUtil.getInstance().readByteArray(in));
        sendPresence = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
