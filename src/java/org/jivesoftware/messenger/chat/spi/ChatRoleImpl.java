/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.chat.spi;

import org.jivesoftware.messenger.chat.ChatRole;
import org.jivesoftware.messenger.chat.ChatRoom;
import org.jivesoftware.messenger.chat.ChatServer;
import org.jivesoftware.messenger.chat.ChatUser;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Simple in-memory implementation of a role in a chatroom
 *
 * @author Iain Shigeoka
 */
public class ChatRoleImpl implements ChatRole {

    /**
     * The room this role is valid in.
     */
    private ChatRoomImpl room;
    /**
     * The user of the role.
     */
    private ChatUserImpl user;
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
    private ChatServer server;
    /**
     * The role ID.
     */
    private int role;
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
    private MetaDataFragment nonAnonFragment;

    /**
     * Create a new role.
     *
     * @param chatserver   the server hosting the role.
     * @param chatroom     the room the role is valid in.
     * @param nickname     the nickname of the user in the role.
     * @param chatuser     the user on the chat server.
     * @param packetRouter the packet router for sending messages from this role.
     * @throws UnauthorizedException if the role could not be created due to
     *                               security or permission violations
     */
    public ChatRoleImpl(ChatServer chatserver,
                        ChatRoomImpl chatroom,
                        String nickname,
                        ChatUserImpl chatuser,
                        PacketRouter packetRouter) throws UnauthorizedException {
        this.room = chatroom;
        this.nick = nickname;
        this.user = chatuser;
        this.server = chatserver;
        this.router = packetRouter;
        role = ChatRole.OCCUPANT;
        rJID = new XMPPAddress(room.getName(), server.getChatServerName(), nick);
        if (server.isUseAnonymousRooms()) {
            nonAnonFragment = null;
        }
        else {
            nonAnonFragment = new MetaDataFragment("http://jabber.org/protocol/muc#user", "x");
            nonAnonFragment.setProperty("x.item:jid", user.getAddress().toString());
            nonAnonFragment.setProperty("x.item:affiliation", "none");
            nonAnonFragment.setProperty("x.item:role", "participant");
        }
        setPresence(room.createPresence(Presence.STATUS_ONLINE));
    }

    public Presence getPresence() throws UnauthorizedException {
        return presence;
    }

    public void setPresence(Presence newPresence) throws UnauthorizedException {
        this.presence = newPresence;
        if (nonAnonFragment != null) {
            presence.addFragment(nonAnonFragment);
        }
    }

    public void setRole(int newRole) throws UnauthorizedException {
        role = newRole;
    }

    public int getRole() throws UnauthorizedException {
        return role;
    }

    public String getNickname() throws UnauthorizedException {
        return nick;
    }

    public void kick() throws UnauthorizedException {
    }

    public ChatUser getChatUser() {
        return user;
    }

    public ChatRoom getChatRoom() {
        return room;
    }

    public XMPPAddress getRoleAddress() {
        return rJID;
    }

    public void send(Presence packet) throws UnauthorizedException {
        presence = room.createPresence(Presence.UNAVAILABLE == packet.getType()
                ? Presence.STATUS_OFFLINE
                : Presence.STATUS_ONLINE);
        packet.setRecipient(user.getAddress());
        if (nonAnonFragment != null) {
            presence.addFragment(nonAnonFragment);
        }
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
}
