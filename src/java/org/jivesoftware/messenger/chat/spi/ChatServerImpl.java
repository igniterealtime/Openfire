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
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the chat server as a cached memory resident
 * chat server. It should be easy to extend the cache to create
 * persistent versions of some of the entities (such as persistent
 * rooms) using a database in the future.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class ChatServerImpl extends BasicModule implements ChatServer, RoutableChannelHandler {

    /**
     * The time to elapse between clearing of idle chat users.
     */
    private static final int USER_TIMEOUT = 300000;
    /**
     * the chat server's hostname
     */
    private String chatServerName = null;
    private XMPPAddress chatServerAddress = null;

    /**
     * chatrooms managed by this manager, table: key room name (String); value ChatRoom
     */
    private Map<String, ChatRoom> rooms = new ConcurrentHashMap<String, ChatRoom>();

    /**
     * chat users managed by this manager, table: key user jid (XMPPAddress); value ChatUser
     */
    private Map<XMPPAddress, ChatUser> users = new ConcurrentHashMap<XMPPAddress, ChatUser>();

    private HistoryStrategy historyStrategy;

    private RoutingTable routingTable = null;
    /**
     * The packet deliverer for the server.
     */
    public PacketDeliverer deliverer = null;
    /**
     * The packet router for the server.
     */
    public PacketRouter router = null;
    /**
     * The packet manager for the server.
     */
    public PresenceManager presenceManager = null;
    /**
     * The total time all agents took to chat *
     */
    public long totalChatTime;

    /**
     * Flag indicating if rooms should use anonymous presence (default).
     * If false, all presence updates in all rooms will send the true JID
     * of the room participants according to JEP-0045 (MUC) for non-anonyomus
     * rooms. 
     */
    private boolean useAnonRooms = true;


    /**
     * <p>Timer to monitor chatroom participants. If they've been idle for too long, probe for presence.</p>
     */
    private Timer timer = new Timer();


    /**
     * <p>Create a new group chat server.</p>
     */
    public ChatServerImpl() {
        super("Basic chat server");
        historyStrategy = new HistoryStrategy(null);

        // Run through the users every 5 minutes after a 1 minute server startup delay
        timer.schedule(new UserTimeoutTask(), USER_TIMEOUT, USER_TIMEOUT);
    }

    /**
     * <p>Probes the presence of any user who's last packet was sent more than 5 minute ago.</p>
     */
    private class UserTimeoutTask extends TimerTask {
        /**
         * Remove any user that has been idle for longer than the user timeout time.
         */
        public void run() {
            synchronized (users) {
                try {
                    checkForTimedOutUsers();
                }
                catch (ConcurrentModificationException e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
    }

    private void checkForTimedOutUsers() throws ConcurrentModificationException {
        final long deadline = System.currentTimeMillis() - USER_TIMEOUT;
        final Map userMap = new HashMap(users);
        final Iterator userIter = userMap.values().iterator();
        while (userIter.hasNext()) {
            try {
                ChatUser user = (ChatUser)userIter.next();
                if (user.getLastPacketTime() < deadline) {
                    presenceManager.probePresence(chatServerAddress, user.getAddress());
                }
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    public ChatRoom getChatRoom(String roomName) throws UnauthorizedException {
        ChatRoom room = null;
        synchronized (rooms) {
            room = (ChatRoom)rooms.get(roomName.toLowerCase());
            if (room == null) {
                room = new ChatRoomImpl(this, roomName, router);
                rooms.put(roomName.toLowerCase(), room);
            }
        }
        return room;
    }

    public void removeChatRoom(String roomName) throws UnauthorizedException {
        synchronized (rooms) {
            final ChatRoom room = (ChatRoom)rooms.get(roomName.toLowerCase());
            final long chatLength = room.getChatLength();
            totalChatTime += chatLength;
            rooms.remove(roomName.toLowerCase());
        }

    }

    public String getChatServerName() {
        return chatServerName;
    }

    public HistoryStrategy getHistoryStrategy() {
        return historyStrategy;
    }

    public void removeUser(XMPPAddress jabberID) throws UnauthorizedException {
        synchronized (users) {
            ChatUser user = (ChatUser)users.remove(jabberID);
            if (user != null) {
                Iterator roles = user.getRoles();
                while (roles.hasNext()) {
                    ChatRole role = (ChatRole)roles.next();
                    try {
                        role.getChatRoom().leaveRoom(role.getNickname());
                    }
                    catch (Exception e) {
                        Log.error(e);
                    }
                }
            }
        }
    }

    public ChatUser getChatUser(XMPPAddress userjid) throws UnauthorizedException, UserNotFoundException {
        if (router == null) {
            throw new IllegalStateException("Not initialized");
        }
        ChatUser user = null;
        synchronized (users) {
            user = (ChatUser)users.get(userjid);
            if (user == null) {
                user = new ChatUserImpl(this, router, userjid);
                users.put(userjid, user);
            }
        }
        return user;
    }

    public void serverBroadcast(String msg) throws UnauthorizedException {
        synchronized (rooms) {
            Iterator itr = rooms.values().iterator();
            while (itr.hasNext()) {
                ((ChatRoom)itr.next()).serverBroadcast(msg);
            }
        }
    }

    /**
     * Initialize the track info for the server.
     *
     * @return the track information for this server
     */
    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketRouter.class, "router");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(PresenceManager.class, "presenceManager");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof RoutingTable) {
            ((RoutingTable)service).addRoute(chatServerAddress, this);
            ArrayList params = new ArrayList();
            params.clear();
            params.add(chatServerName);
            Log.info(LocaleUtils.getLocalizedString("startup.starting.chat", params));
        }
    }

    /**
     * Set the address of the server.
     *
     * @param name the new server address.
     */
    public void setChatServerName(String name) {
        JiveGlobals.setProperty("xmpp.chat.domain", name);
    }

    public void initialize(Container container) {
        chatServerName = JiveGlobals.getProperty("xmpp.chat.domain");
        // Trigger the strategy to load itself from the context
        historyStrategy.setContext("xmpp.chat.history");

        // Pseudo MUC support for non-anonymous rooms - all or nothing setting
        useAnonRooms = JiveGlobals.getBooleanProperty("xmpp.chat.anonymous_rooms");
        if (chatServerName == null) {
            chatServerName = "chat.127.0.0.1";
        }
        chatServerAddress = new XMPPAddress(null, chatServerName, null);
        super.initialize(container);
    }

    public void start() {
        super.start();
        routingTable = (RoutingTable)lookup.lookup(RoutingTable.class);
        routingTable.addRoute(chatServerAddress, this);
        ArrayList params = new ArrayList();
        params.clear();
        params.add(chatServerName);
        Log.info(LocaleUtils.getLocalizedString("startup.starting.chat", params));
    }

    public XMPPAddress getAddress() {
        if (chatServerAddress == null) {
            throw new IllegalStateException("Not initialized");
        }
        return chatServerAddress;
    }

    public void process(XMPPPacket packet) throws UnauthorizedException, PacketException {
        try {
            ChatUser user = getChatUser(packet.getSender());
            user.process(packet);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public boolean isUseAnonymousRooms() {
        return useAnonRooms;
    }

    public long getTotalChatTime() {
        return totalChatTime;
    }
}
