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

import org.jivesoftware.messenger.chat.*;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.MessageImpl;
import org.jivesoftware.messenger.spi.PresenceImpl;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of a chatroom.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class ChatRoomImpl implements ChatRoom {

    private ChatServer server;
    private Map<String, ChatRole> members = new ConcurrentHashMap<String, ChatRole>();
    private String name;
    private ChatRole role;
    private PacketRouter router;
    private HistoryStrategy historyStrategy;
    private long startTime;
    private long endTime;
    private ChatRoomHistory roomHistory;

    /**
     * Create a new chat room.
     *
     * @param chatserver the server hosting the room.
     * @param roomname the name of the room.
     * @param packetRouter the router for sending packets from the room.
     */
    ChatRoomImpl(ChatServer chatserver, String roomname, PacketRouter packetRouter) {
        server = chatserver;
        name = roomname;
        router = packetRouter;
        startTime = System.currentTimeMillis();
        roomHistory = new ChatRoomHistory(roomname);
        roomHistory.setStartTime(startTime);
        role = new RoomRole(this);
        historyStrategy = new HistoryStrategy(server.getHistoryStrategy());
    }

    public String getName() {
        return name;
    }

    public long getID() {
        return -1;
    }

    public HistoryStrategy getHistoryStrategy() {
        return historyStrategy;
    }

    public ChatRole getRole() throws UnauthorizedException {
        return role;
    }

    public ChatRole getMember(String nickname) throws UnauthorizedException, UserNotFoundException {
        return members.get(nickname.toLowerCase());
    }

    public Iterator<ChatRole> getMembers() throws UnauthorizedException {
        return Collections.unmodifiableCollection(members.values()).iterator();
    }

    public boolean hasMember(String nickname) throws UnauthorizedException {
        return members.containsKey(nickname.toLowerCase());
    }

    public synchronized ChatRole joinRoom(String nickname, ChatUser user)
            throws UnauthorizedException, UserAlreadyExistsException
    {
        ChatRoleImpl joinRole = null;
        if (members.containsKey(nickname.toLowerCase())) {
            throw new UserAlreadyExistsException();
        }
        joinRole = new ChatRoleImpl(server, this, nickname, (ChatUserImpl)user, router);

        // Handle ChatRoomHistory
        if (roomHistory.getUserID() == null) {
            roomHistory.setUserID(nickname);
        }
        else {
            roomHistory.userJoined(user, new Date());
        }

        Iterator iter = members.values().iterator();
        while (iter.hasNext()) {
            ChatRole memberRole = (ChatRole)iter.next();
            Presence memberPresence =
                    (Presence)memberRole.getPresence().createDeepCopy();
            memberPresence.setSender(memberRole.getRoleAddress());
            joinRole.send(memberPresence);
        }
        members.put(nickname.toLowerCase(), joinRole);
        if (joinRole != null) {
            List params = new ArrayList();
            params.add(nickname);
            try {
                Presence joinPresence = (Presence)joinRole.getPresence().createDeepCopy();
                joinPresence.setSender(joinRole.getRoleAddress());
                broadcast(joinPresence);
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            Iterator history = historyStrategy.getMessageHistory();
            while (history.hasNext()) {
                joinRole.send((Message)history.next());
            }
            serverBroadcast(LocaleUtils.getLocalizedString("chat.join", params));
        }
        return joinRole;
    }

    public synchronized void leaveRoom(String nickname) throws UnauthorizedException,
            UserNotFoundException
    {
        ChatRole leaveRole = null;
        leaveRole = (ChatRole)members.remove(nickname.toLowerCase());
        //Testing
        ChatUser user = leaveRole.getChatUser();

        roomHistory.userLeft(user, new Date());

        if (members.isEmpty()) {
            // Adding new ChatTranscript logic.
            endTime = System.currentTimeMillis();

            // Update RoomHistory with HistoryStrategy
            roomHistory.setHistory(getHistoryStrategy());
            roomHistory.setEndTime(System.currentTimeMillis());

            // Update ChatAuditManager
            ChatAuditManager cAuditManager = ChatAuditManager.getInstance();
            cAuditManager.addChatHistory(roomHistory);
            cAuditManager.fireChatRoomClosed(server.getChatRoom(name));

            server.removeChatRoom(name);
        }

        if (leaveRole != null) {
            try {
                Presence presence = createPresence(Presence.STATUS_OFFLINE);
                presence.setSender(leaveRole.getRoleAddress());
                broadcast((Presence)presence.createDeepCopy());
                leaveRole.kick();
                List params = new ArrayList();
                params.add(nickname);
                serverBroadcast(LocaleUtils.getLocalizedString("chat.leave", params));
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    public Presence createPresence(int presenceStatus) throws UnauthorizedException {
        Presence presence = new PresenceImpl();
        presence.setSender(role.getRoleAddress());
        switch (presenceStatus) {
            case Presence.STATUS_INVISIBLE:
                presence.setAvailable(true);
                presence.setVisible(false);
                break;
            case Presence.STATUS_ONLINE:
                presence.setAvailable(true);
                presence.setVisible(true);
                break;
            case Presence.STATUS_OFFLINE:
                presence.setAvailable(false);
                presence.setVisible(false);
                break;
            default:
        }
        return presence;
    }

    public void serverBroadcast(String msg) throws UnauthorizedException {
        Message message = new MessageImpl();
        message.setType(Message.GROUP_CHAT);
        message.setBody(msg);
        message.setSender(role.getRoleAddress());
        historyStrategy.addMessage(message);
        broadcast(message);
    }

    public void send(Message packet) throws UnauthorizedException {
        String resource = packet.getRecipient().getResource();
        if (resource == null || resource.trim().length() == 0) {
            resource = null;
        }
        if (resource == null && Message.GROUP_CHAT == packet.getType()) {
            // normal groupchat
            historyStrategy.addMessage(packet);
            broadcast(packet);
        }
        else if (resource != null && (Message.CHAT == packet.getType()
                || Message.NORMAL == packet.getType()))
        {
            // private message
            ChatRole member = members.get(resource.toLowerCase());
            if (member != null) {
                packet.setRecipient(member.getChatUser().getAddress());
                router.route(packet);
            }
        }
        else {
            packet = (Message)packet.createDeepCopy();
            packet.setError(XMPPError.Code.BAD_REQUEST);
            packet.setRecipient(packet.getSender());
            packet.setSender(role.getRoleAddress());
            router.route(packet);
        }
    }

    public void send(Presence packet) throws UnauthorizedException {
        broadcast(packet);
    }

    public void send(IQ packet) throws UnauthorizedException {
        packet = (IQ)packet.createDeepCopy();
        packet.setError(XMPPError.Code.BAD_REQUEST);
        packet.setRecipient(packet.getSender());
        packet.setSender(role.getRoleAddress());
        router.route(packet);
    }


    private void broadcast(XMPPPacket packet) {
        for (ChatRole member : members.values()) {
            packet.setRecipient(member.getChatUser().getAddress());
            router.route(packet);
        }
    }

    /**
     * An empty role that represents the room itself in the chatroom.
     * Chatrooms need to be able to speak (server messages) and so
     * must have their own role in the chatroom.
     */
    private class RoomRole implements ChatRole {

        private ChatRoom room;

        private RoomRole(ChatRoom room) {
            this.room = room;
        }

        public Presence getPresence() throws UnauthorizedException {
            return null;
        }

        public void setPresence(Presence presence) throws UnauthorizedException {
        }

        public void setRole(int newRole) throws UnauthorizedException {
        }

        public int getRole() throws UnauthorizedException {
            return ChatRole.OWNER;
        }

        public String getNickname() throws UnauthorizedException {
            return null;
        }

        public void kick() throws UnauthorizedException {
        }

        public ChatUser getChatUser() {
            return null;
        }

        public ChatRoom getChatRoom() {
            return room;
        }

        private XMPPAddress crJID;

        public XMPPAddress getRoleAddress() {
            if (crJID == null) {
                crJID = new XMPPAddress(room.getName(), server.getChatServerName(), "");
            }
            return crJID;
        }

        public void send(Message packet) throws UnauthorizedException {
            room.send(packet);
        }

        public void send(Presence packet) throws UnauthorizedException {
            room.send(packet);
        }

        public void send(IQ packet) throws UnauthorizedException {
            room.send(packet);
        }
    }

    public long getChatLength() {
        return endTime - startTime;
    }
}
