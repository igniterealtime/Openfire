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
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatUser implementation.
 *
 * @author Iain Shigeoka
 */
public class ChatUserImpl implements ChatUser {

    private ChatServer server;
    private XMPPAddress realjid;
    private Map<String, ChatRole> roles = new ConcurrentHashMap<String, ChatRole>();
    private PacketRouter router;
    private long lastPacketTime;

    /**
     * Create a new chat user.
     *
     * @param chatserver the server the user belongs to.
     * @param packetRouter the router for sending packets from this user.
     * @param jid the real address of the user
     */
    ChatUserImpl(ChatServerImpl chatserver, PacketRouter packetRouter, XMPPAddress jid) {
        this.realjid = jid;
        this.router = packetRouter;
        this.server = chatserver;
    }

    public long getID() throws UnauthorizedException {
        return -1;
    }

    public ChatRole getRole(String roomName) throws UnauthorizedException, NotFoundException {
        ChatRole role = roles.get(roomName.toLowerCase());
        if (role == null) {
            throw new NotFoundException(roomName);
        }
        return role;
    }

    public Iterator getRoles() throws UnauthorizedException {
        return Collections.unmodifiableCollection(roles.values()).iterator();
    }

    public long getLastPacketTime() {
        return lastPacketTime;
    }

    /**
     * Generate a conflict packet to indicate that the nickname being requested/used
     * is already in use by another user.
     *
     * @param packet The packet to be bounced
     */
    private void sendConflict(XMPPPacket packet) {
        packet = (XMPPPacket)packet.createDeepCopy();
        packet.setError(XMPPError.Code.CONFLICT);
        XMPPAddress sender = packet.getSender();
        packet.setSender(packet.getRecipient());
        packet.setRecipient(sender);
        router.route(packet);
    }

    /**
     * Translate the chat packet into a XMPPDocumentPacket with appropriate
     * addressing for the underlying user. This method is only used by
     * DbChatRole for delivering packets.
     *
     * @param packet The Packet to deliver
     */
    void deliver(XMPPPacket packet) {
        packet.setRecipient(realjid);
        ChatRole role = (ChatRole)roles.get(packet.getSender().getNamePrep());
        packet.setSender(role.getRoleAddress());
        router.route(packet);
    }

    public XMPPAddress getAddress() {
        return realjid;
    }

    /**
     * This method does all packet routing in the chat server.
     * Packet routing is actually very simple:
     * <p/>
     * <ul>
     * <li>Discover the room the user is talking to (server packets are dropped)</li>
     * <li>If the room is not registered and this is a presence "available" packet,
     *      try to join the room</li>
     * <li>If the room is registered, and presence "unavailable" leave the room</li>
     * <li>Otherwise, rewrite the sender address and send to the room.</li>
     * </ul>
     *
     * @param packet the packet to route.
     */
    public void process(Message packet) {
        lastPacketTime = System.currentTimeMillis();
        XMPPAddress recipient = packet.getRecipient();
        String group = recipient.getName();
        if (group == null) {
            // Ignore packets to the groupchat server.
            Log.info(LocaleUtils.getLocalizedString("chat.error.not-supported") + " " +
                    packet.toString());
        }
        else {
            ChatRole role = roles.get(group.toLowerCase());
            if (role == null) {
                // TODO: send error message to user (can't send packets to group you haven't joined)
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                    sendConflict(packet);
                }
                else {
                    try {
                        packet.setSender(role.getRoleAddress());
                        role.getChatRoom().send(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
    }

    public void process(XMPPPacket packet) throws UnauthorizedException, PacketException {
        if (packet instanceof IQ) {
            process((IQ)packet);
        }
        else if (packet instanceof Message) {
            process((Message)packet);
        }
        else if (packet instanceof Presence) {
            process((Presence)packet);
        }
    }

    public void process(IQ packet) {
        lastPacketTime = System.currentTimeMillis();
        XMPPAddress recipient = packet.getRecipient();
        String group = recipient.getName();
        if (group == null) {
            // Ignore packets to the groupchat server
            Log.info(LocaleUtils.getLocalizedString("chat.error.not-supported") + " " +
                    packet.toString());
        }
        else {
            ChatRole role = roles.get(group.toLowerCase());
            if (role == null) {
                // TODO: send error message to user (can't send packets to group you haven't joined)
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                    sendConflict(packet);
                }
                else {
                    try {
                        role.getChatRoom().send(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
        }
    }

    public void process(Presence packet) {
        lastPacketTime = System.currentTimeMillis();
        try {
            XMPPAddress recipient = packet.getRecipient();
            String group = recipient.getNamePrep();
            if (group == null) {
                if (Presence.UNAVAILABLE == packet.getType()) {
                    server.removeUser(packet.getSender());
                }
            }
            else {
                ChatRole role = (ChatRole)roles.get(group.toLowerCase());
                if (role == null) {
                    // If we're not already in a room, we either are joining it
                    // or it's not properly addressed and we drop it silently
                    // In the future, we'll need to support TYPE_IQ queries to the room for MUC
                    if (recipient.getResource() != null || recipient.getResource().trim().length() > 0) {
                        if (packet.getType() == Presence.AVAILABLE ||
                                Presence.INVISIBLE == packet.getType())
                        {
                            try {
                                ChatRoom room = server.getChatRoom(group);
                                role = room.joinRoom(recipient.getResource().trim(), this);
                                roles.put(group, role);
                            }
                            catch (UnauthorizedException e) {
                                Log.error(e);
                            }
                            catch (UserAlreadyExistsException e) {
                                sendConflict(packet);
                            }
                        }
                        else {
                            // TODO: send error message to user (can't send presence to group
                            // TODO: you haven't joined)
                        }
                    }
                    else {
                        // TODO: send error message to user (can't send packets to group
                        // TODO: you haven't joined)
                    }
                }
                else {
                    // Check and reject conflicting packets with conflicting roles
                    // In other words, another user already has this nickname
                    if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                        sendConflict(packet);
                    }
                    else {
                        if (Presence.UNAVAILABLE == packet.getType()) {
                            try {
                                roles.remove(group.toLowerCase());
                                role.getChatRoom().leaveRoom(role.getNickname());
                            }
                            catch (Exception e) {
                                Log.error(e);
                            }
                        }
                        else {
                            try {
                                // We must set the role presence, and then use
                                // the new role presence from the role 
                                // (not just pass through the packet). The role
                                // may manipulate the presence packet to support
                                // things like non-anonymous rooms
                                role.setPresence(packet);
                                role.getChatRoom().send(role.getPresence());
                            }
                            catch (Exception e) {
                                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                            }
                        }
                    }
                }
            }
        }
        catch (UnauthorizedException ue) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), ue);
        }
    }
}
