/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.muc.spi;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.messenger.muc.MUCRoom;
import org.jivesoftware.messenger.muc.MUCUser;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.muc.NotAllowedException;
import org.jivesoftware.util.ElementUtil;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

/**
 * Simple in-memory implementation of a role in a chatroom
 * 
 * @author Gaston Dombiak
 */
// TODO Review name of this class that represents role and affiliation at the same time.!!!!
public class MUCRoleImpl implements MUCRole {

    /**
     * The room this role is valid in.
     */
    private MUCRoomImpl room;

    /**
     * The user of the role.
     */
    private MUCUserImpl user;

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
     * The role ID.
     */
    private int role;

    /**
     * The affiliation ID.
     */
    private int affiliation;

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
    public MUCRoleImpl(MultiUserChatServer chatserver, MUCRoomImpl chatroom, String nickname,
            int role, int affiliation, MUCUserImpl chatuser, Presence presence,
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
        rJID = new JID(room.getName(), server.getServiceName(), nick);
        setPresence(presence);
    }

    public Presence getPresence() {
        return presence;
    }

    public Element getExtendedPresenceInformation() {
        return extendedInformation;
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

    public void setRole(int newRole) throws NotAllowedException {
        // Don't allow to change the role to an owner or admin unless the new role is moderator
        if (MUCRole.OWNER == affiliation || MUCRole.ADMINISTRATOR == affiliation) {
            if (MUCRole.MODERATOR != newRole) {
                throw new NotAllowedException();
            }
        }
        // A moderator cannot be kicked from a room
        if (MUCRole.MODERATOR == role && MUCRole.NONE_ROLE == newRole) {
            throw new NotAllowedException();
        }
        // TODO A moderator MUST NOT be able to revoke voice from a user whose affiliation is at or
        // above the moderator's level.

        role = newRole;
        if (MUCRole.NONE_ROLE == role) {
            presence.setType(Presence.Type.unavailable);
            presence.setStatus(null);
        }
        calculateExtendedInformation();
    }

    public int getRole() {
        return role;
    }

    public String getRoleAsString() {
        if (MUCRole.MODERATOR == role) {
            return "moderator";
        }
        else if (MUCRole.PARTICIPANT == role) {
            return "participant";
        }
        else if (MUCRole.VISITOR == role) {
            return "visitor";
        }
        return "none";
    }

    public void setAffiliation(int newAffiliation) throws NotAllowedException {
        // Don't allow to ban an owner or an admin
        if (MUCRole.OWNER == affiliation || MUCRole.ADMINISTRATOR == affiliation) {
            if (MUCRole.OUTCAST == newAffiliation) {
                throw new NotAllowedException();
            }
        }
        affiliation = newAffiliation;
        // TODO The fragment is being calculated twice (1. setting the role & 2. setting the aff)
        calculateExtendedInformation();
    }

    public int getAffiliation() {
        return affiliation;
    }

    public String getAffiliationAsString() {
        if (MUCRole.OWNER == affiliation) {
            return "owner";
        }
        else if (MUCRole.ADMINISTRATOR == affiliation) {
            return "admin";
        }
        else if (MUCRole.MEMBER == affiliation) {
            return "member";
        }
        else if (MUCRole.OUTCAST == affiliation) {
            return "outcast";
        }
        return "none";
    }

    public String getNickname() {
        return nick;
    }

    public void kick() {
        getChatUser().removeRole(room.getName());
    }

    public void changeNickname(String nickname) {
        this.nick = nickname;
        setRoleAddress(new JID(room.getName(), server.getServiceName(), nick));
    }

    public MUCUser getChatUser() {
        return user;
    }

    public MUCRoom getChatRoom() {
        return room;
    }

    public JID getRoleAddress() {
        return rJID;
    }

    private void setRoleAddress(JID jid) {
        rJID = jid;
        // Set the new sender of the user presence in the room
        presence.setFrom(jid);
    }

    public void send(Presence packet) {
        packet.setTo(user.getAddress());
        router.route(packet);
    }

    public void send(Message packet) {
        packet.setTo(user.getAddress());
        router.route(packet);
    }

    public void send(IQ packet) {
        packet.setTo(user.getAddress());
        router.route(packet);
    }

    /**
     * Calculates and sets the extended presence information to add to the presence.
     * The information to add contains the user's jid, affiliation and role.
     */
    private void calculateExtendedInformation() {
        ElementUtil.setProperty(extendedInformation, "x.item:jid", user.getAddress().toString());
        ElementUtil.setProperty(extendedInformation, "x.item:affiliation", getAffiliationAsString());
        ElementUtil.setProperty(extendedInformation, "x.item:role", getRoleAsString());
    }
}