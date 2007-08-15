/**
 * $RCSfile: LocalMUCRole.java,v $
 * $Revision: 3168 $
 * $Date: 2005-12-07 13:55:47 -0300 (Wed, 07 Dec 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
import org.jivesoftware.openfire.muc.MultiUserChatServer;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.ElementUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

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
    private MultiUserChatServer server;

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
     * Cache session of local user that is a room occupant. If the room occupant is not a local
     * user then nothing will be cached and packets will be sent through the PacketRouter.
     */
    private ClientSession session;

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
    public LocalMUCRole(MultiUserChatServer chatserver, LocalMUCRoom chatroom, String nickname,
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
        // Cache the user's session (will only work for local users)
        this.session = XMPPServer.getInstance().getSessionManager().getSession(presence.getFrom());

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

    public Presence getPresence() {
        return presence;
    }

    public void setPresence(Presence newPresence) {
        // Try to remove the element whose namespace is "http://jabber.org/protocol/muc" since we
        // don't need to include that element in future presence broadcasts
        Element element = newPresence.getElement().element(QName.get("x", "http://jabber.org/protocol/muc"));
        if (element != null) {
            newPresence.getElement().remove(element);
        }
        this.presence = newPresence;
        this.presence.setFrom(getRoleAddress());
        if (extendedInformation != null) {
            extendedInformation.setParent(null);
            presence.getElement().add(extendedInformation);
        }
    }

    public void setRole(MUCRole.Role newRole) throws NotAllowedException {
        // Don't allow to change the role to an owner or admin unless the new role is moderator
        if (MUCRole.Affiliation.owner == affiliation || MUCRole.Affiliation.admin == affiliation) {
            if (MUCRole.Role.moderator != newRole) {
                throw new NotAllowedException();
            }
        }
        // A moderator cannot be kicked from a room
        if (MUCRole.Role.moderator == role && MUCRole.Role.none == newRole) {
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

    public MUCRole.Role getRole() {
        return role;
    }

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

    public MUCRole.Affiliation getAffiliation() {
        return affiliation;
    }

    public String getNickname() {
        return nick;
    }

    public void changeNickname(String nickname) {
        this.nick = nickname;
        setRoleAddress(new JID(room.getName(), server.getServiceDomain(), nick));
    }

    public void destroy() {
        // Notify the user that he/she is no longer in the room
        user.removeRole(room.getName());
    }

    public MUCRoom getChatRoom() {
        return room;
    }

    public JID getRoleAddress() {
        return rJID;
    }

    public JID getUserAddress() {
        return user.getAddress();
    }

    public boolean isLocal() {
        return true;
    }

    public NodeID getNodeID() {
        return XMPPServer.getInstance().getNodeID();
    }

    private void setRoleAddress(JID jid) {
        rJID = jid;
        // Set the new sender of the user presence in the room
        presence.setFrom(jid);
    }

    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    public void send(Packet packet) {
        if (packet == null) {
            return;
        }
        packet.setTo(user.getAddress());

        if (session != null && session.getStatus() == Session.STATUS_AUTHENTICATED) {
            // Send the packet directly to the local user session
            session.process(packet);
        }
        else {
            router.route(packet);
        }
    }

    /**
     * Calculates and sets the extended presence information to add to the presence.
     * The information to add contains the user's jid, affiliation and role.
     */
    private void calculateExtendedInformation() {
        ElementUtil.setProperty(extendedInformation, "x.item:jid", user.getAddress().toString());
        ElementUtil.setProperty(extendedInformation, "x.item:affiliation", affiliation.toString());
        ElementUtil.setProperty(extendedInformation, "x.item:role", role.toString());
    }
}