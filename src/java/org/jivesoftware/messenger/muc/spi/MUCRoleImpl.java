/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.muc.spi;

import org.jivesoftware.messenger.muc.*;
import org.jivesoftware.messenger.IQ;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.MetaDataFragment;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;

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
    private XMPPAddress rJID;

    /**
     * A fragment containing the x-extension for non-anonymous rooms.
     */
    private MetaDataFragment extendedInformation;

    /**
     * Create a new role.
     * 
     * @param chatserver the server hosting the role.
     * @param chatroom the room the role is valid in.
     * @param nickname the nickname of the user in the role.
     * @param role the role of the user in the room.
     * @param affiliation the affiliation of the user in the room.
     * @param chatuser the user on the chat server.
     * @param packetRouter the packet router for sending messages from this role.
     * @throws UnauthorizedException if the role could not be created due to security or permission
     *             violations
     */
    public MUCRoleImpl(MultiUserChatServer chatserver,
                       MUCRoomImpl chatroom,
                       String nickname,
                       int role,
                       int affiliation,
                       MUCUserImpl chatuser,
                       PacketRouter packetRouter) throws UnauthorizedException {
        this.room = chatroom;
        this.nick = nickname;
        this.user = chatuser;
        this.server = chatserver;
        this.router = packetRouter;
        this.role = role;
        this.affiliation = affiliation;
        extendedInformation = new MetaDataFragment("http://jabber.org/protocol/muc#user", "x");
        calculateExtendedInformation();
        rJID = new XMPPAddress(room.getName(), server.getChatServerName(), nick);
        setPresence(room.createPresence(Presence.STATUS_ONLINE));
    }

    public Presence getPresence() throws UnauthorizedException {
        return presence;
    }

    public MetaDataFragment getExtendedPresenceInformation() throws UnauthorizedException {
        return extendedInformation;
    }

    public void setPresence(Presence newPresence) throws UnauthorizedException {
        this.presence = newPresence;
        if (extendedInformation != null) {
            presence.addFragment(extendedInformation);
        }
    }

    public void setRole(int newRole) throws UnauthorizedException, NotAllowedException {
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
            presence.setAvailable(false);
            presence.setVisible(false);
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

    public void setAffiliation(int newAffiliation) throws UnauthorizedException,
            NotAllowedException {
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

    public void kick() throws UnauthorizedException {
        getChatUser().removeRole(room.getName());
    }

    public void changeNickname(String nickname) {
        this.nick = nickname;
        rJID = new XMPPAddress(room.getName(), server.getChatServerName(), nick);
    }

    public MUCUser getChatUser() {
        return user;
    }

    public MUCRoom getChatRoom() {
        return room;
    }

    public XMPPAddress getRoleAddress() {
        return rJID;
    }

    public void send(Presence packet) throws UnauthorizedException {
        packet.setRecipient(user.getAddress());
        router.route(packet);
    }

    public void send(Message packet) throws UnauthorizedException {
        packet.setRecipient(user.getAddress());
        router.route(packet);
    }

    public void send(IQ packet) throws UnauthorizedException {
        packet.setRecipient(user.getAddress());
        router.route(packet);
    }

    /**
     * Calculates and sets the extended presence information to add to the presence. The information
     * to add contains the user's jid, affiliation and role.
     */
    private void calculateExtendedInformation() throws UnauthorizedException {
        extendedInformation.setProperty("x.item:jid", user.getAddress().toString());
        extendedInformation.setProperty("x.item:affiliation", getAffiliationAsString());
        extendedInformation.setProperty("x.item:role", getRoleAsString());
    }
}