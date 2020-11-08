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

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.util.ElementUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;

/**
 * Implementation of a local room occupant.
 * 
 * @author Gaston Dombiak
 */
public class LocalMUCRole implements MUCRole {

    /**
     * The room this role is valid in.
     */
    private LocalMUCRoom room;

    /**
     * The user of the role.
     */
    private LocalMUCUser user;

    /**
     * The user's nickname in the room.
     */
    private String nick;

    /**
     * The user's presence in the room.
     */
    private Presence presence;

    /**
     * The chatserver that hosts this role.
     */
    private MultiUserChatService server;

    /**
     * The role.
     */
    private MUCRole.Role role;

    /**
     * The affiliation.
     */
    private MUCRole.Affiliation affiliation;

    /**
     * Flag that indicates if the room occupant is in the room only to send messages or also
     * to receive room messages. True means that the room occupant is deaf.
     */
    private boolean voiceOnly = false;

    /**
     * The router used to send packets from this role.
     */
    private PacketRouter router;

    /**
     * The address of the person masquerading in this role.
     */
    private JID rJID;

    /**
     * A fragment containing the x-extension for non-anonymous rooms.
     */
    private Element extendedInformation;

    /**
     * The address of the person on the joining FMUC node, if the person joined through FMUC (otherwise null).
     */
    private JID reportedFmucJID;

    /**
     * Create a new role.
     * 
     * @param chatserver the server hosting the role.
     * @param chatroom the room the role is valid in.
     * @param nickname the nickname of the user in the role.
     * @param role the role of the user in the room.
     * @param affiliation the affiliation of the user in the room.
     * @param chatuser the user on the chat server.
     * @param presence the presence sent by the user to join the room.
     * @param packetRouter the packet router for sending messages from this role.
     */
    public LocalMUCRole(MultiUserChatService chatserver, LocalMUCRoom chatroom, String nickname,
            MUCRole.Role role, MUCRole.Affiliation affiliation, LocalMUCUser chatuser, Presence presence,
            PacketRouter packetRouter)
    {
        this.room = chatroom;
        this.nick = nickname;
        this.user = chatuser;
        this.server = chatserver;
        this.router = packetRouter;
        this.role = role;
        this.affiliation = affiliation;

        extendedInformation =
                DocumentHelper.createElement(QName.get("x", "http://jabber.org/protocol/muc#user"));
        calculateExtendedInformation();
        rJID = new JID(room.getName(), server.getServiceDomain(), nick);
        setPresence(presence);

        // Check if new occupant wants to be a deaf occupant
        Element element = presence.getElement()
                .element(QName.get("x", "http://jivesoftware.org/protocol/muc"));
        if (element != null) {
            voiceOnly = element.element("deaf-occupant") != null;
        }

        // Add the new role to the list of roles
        user.addRole(room.getName(), this);
    }

    @Override
    public Presence getPresence() {
        return presence;
    }

    @Override
    public void setPresence(Presence newPresence) {
        // Try to remove the element whose namespace is "http://jabber.org/protocol/muc" since we
        // don't need to include that element in future presence broadcasts
        Element element = newPresence.getElement().element(QName.get("x", "http://jabber.org/protocol/muc"));
        if (element != null) {
            newPresence.getElement().remove(element);
        }

        this.presence = newPresence;
        this.presence.setFrom(getRoleAddress());
        updatePresence();
    }

    @Override
    public void setRole(MUCRole.Role newRole) throws NotAllowedException {
        // Don't allow to change the role to an owner or admin unless the new role is moderator
        if (MUCRole.Affiliation.owner == affiliation || MUCRole.Affiliation.admin == affiliation) {
            if (MUCRole.Role.moderator != newRole) {
                throw new NotAllowedException();
            }
        }
        // A moderator cannot be kicked from a room unless there has also been an affiliation change
        if (MUCRole.Role.moderator == role && MUCRole.Role.none == newRole && MUCRole.Affiliation.none != affiliation) {
            throw new NotAllowedException();
        }
        // TODO A moderator MUST NOT be able to revoke voice from a user whose affiliation is at or
        // TODO above the moderator's level.

        role = newRole;
        if (MUCRole.Role.none == role) {
            presence.setType(Presence.Type.unavailable);
            presence.setStatus(null);
        }
        calculateExtendedInformation();
    }

    @Override
    public MUCRole.Role getRole() {
        return role;
    }

    @Override
    public void setAffiliation(MUCRole.Affiliation newAffiliation) throws NotAllowedException {
        // Don't allow to ban an owner or an admin
        if (MUCRole.Affiliation.owner == affiliation || MUCRole.Affiliation.admin== affiliation) {
            if (MUCRole.Affiliation.outcast == newAffiliation) {
                throw new NotAllowedException();
            }
        }
        affiliation = newAffiliation;
        // TODO The fragment is being calculated twice (1. setting the role & 2. setting the aff)
        calculateExtendedInformation();
    }

    @Override
    public MUCRole.Affiliation getAffiliation() {
        return affiliation;
    }

    @Override
    public String getNickname() {
        return nick;
    }

    @Override
    public void changeNickname(String nickname) {
        this.nick = nickname;
        setRoleAddress(new JID(room.getName(), server.getServiceDomain(), nick));
    }

    @Override
    public void destroy() {
        // Notify the user that he/she is no longer in the room
        user.removeRole(room.getName());
    }

    @Override
    public MUCRoom getChatRoom() {
        return room;
    }

    @Override
    public JID getRoleAddress() {
        return rJID;
    }

    @Override
    public JID getUserAddress() {
        return user.getAddress();
    }

    @Override
    public JID getReportedFmucAddress() {
        return reportedFmucJID;
    }

    public void setReportedFmucAddress( @Nonnull final JID reportedFmucAddress ) {
        this.reportedFmucJID = reportedFmucAddress;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public NodeID getNodeID() {
        return XMPPServer.getInstance().getNodeID();
    }

    private void setRoleAddress(JID jid) {
        rJID = jid;
        // Set the new sender of the user presence in the room
        presence.setFrom(jid);
    }

    @Override
    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    @Override
    public void send(Packet packet) {
        if (packet == null) {
            return;
        }

        if (this.isRemoteFmuc()) {
            // Sending stanzas to individual occupants that are on remote FMUC nodes defeats the purpose of FMUC, which is to reduce message. This reduction is based on sending data just once, and have it 'fan out' on the remote node (as opposed to sending each occupant on that node a distinct stanza from this node).
            Log.warn( "Sending data directly to an entity ({}) on a remote FMUC node. Instead of individual messages, we expect data to be sent just once (and be fanned out locally by the remote node).", this, new Throwable() );

            // Check if stanza needs to be enriched with FMUC metadata.
            augmentOutboundStanzaWithFMUCData(packet);
        }

        packet.setTo(user.getAddress());


        router.route(packet);
    }

    /**
     * Calculates and sets the extended presence information to add to the presence.
     * The information to add contains the user's jid, affiliation and role.
     */
    private void calculateExtendedInformation() {
        ElementUtil.setProperty(extendedInformation, "x.item:jid", user.getAddress().toString());
        ElementUtil.setProperty(extendedInformation, "x.item:affiliation", affiliation.toString());
        ElementUtil.setProperty(extendedInformation, "x.item:role", role.toString());
        updatePresence();
    }

    private void updatePresence() {
        if (extendedInformation != null && presence != null) {
            // Remove any previous extendedInformation, then re-add it.
            Element mucUser = presence.getElement().element(QName.get("x", "http://jabber.org/protocol/muc#user"));
            if (mucUser != null) {
                // Remove any previous extendedInformation, then re-add it.
                presence.getElement().remove(mucUser);
            }
            Element exi = extendedInformation.createCopy();
            presence.getElement().add(exi);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((nick == null) ? 0 : nick.hashCode());
        result = prime * result + ((rJID == null) ? 0 : rJID.hashCode());
        result = prime * result + ((room == null) ? 0 : room.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LocalMUCRole other = (LocalMUCRole) obj;
        if (nick == null) {
            if (other.nick != null)
                return false;
        } else if (!nick.equals(other.nick))
            return false;
        if (rJID == null) {
            if (other.rJID != null)
                return false;
        } else if (!rJID.equals(other.rJID))
            return false;
        if (room == null) {
            if (other.room != null)
                return false;
        } else if (!room.equals(other.room))
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.equals(other.user))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return "LocalMUCRole{" +
            "room=" + room +
            ", user=" + user +
            ", nick='" + nick + '\'' +
            ", role=" + role +
            ", affiliation=" + affiliation +
            ", voiceOnly=" + voiceOnly +
            ", rJID=" + rJID +
            ", reportedFmucJID=" + reportedFmucJID +
            '}';
    }
}
