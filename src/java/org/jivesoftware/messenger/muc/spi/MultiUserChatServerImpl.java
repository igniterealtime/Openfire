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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.messenger.PacketRouter;
import org.jivesoftware.messenger.RoutableChannelHandler;
import org.jivesoftware.messenger.RoutingTable;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.disco.DiscoInfoProvider;
import org.jivesoftware.messenger.disco.DiscoItemsProvider;
import org.jivesoftware.messenger.disco.DiscoServerItem;
import org.jivesoftware.messenger.disco.ServerItemsProvider;
import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.handler.IQRegisterHandler;
import org.jivesoftware.messenger.muc.HistoryStrategy;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.messenger.muc.MUCRoom;
import org.jivesoftware.messenger.muc.MUCUser;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.muc.NotAllowedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Implements the chat server as a cached memory resident chat server. The server is also
 * responsible for responding Multi-User Chat disco requests as well as removing inactive users from
 * the rooms after a period of time and to maintain a log of the conversation in the rooms that 
 * require to log their conversations. The conversations log is saved to the database using a 
 * separate process<p>
 *
 * Rooms in memory are held in the instance variable rooms. For optimization reasons, persistent
 * rooms that don't have occupants aren't kept in memory. But a client may need to discover all the
 * rooms (present in memory or not). So MultiUserChatServerImpl uses a cache of persistent room
 * surrogates. A room surrogate (lighter object) is created for each persistent room that is public,
 * persistent but is not in memory.  The cache starts up empty until a client requests the list of
 * rooms through service discovery. Once the disco request is received the cache is filled up with
 * persistent room surrogates.  The cache will keep all the surrogates in memory for an hour. If the
 * cache's entries weren't used in an hour, they will be removed from memory. Whenever a persistent
 * room is removed from memory (because all the occupants have left), the cache is cleared. But if
 * a persistent room is loaded from the database then the entry for that room in the cache is
 * removed. Note: Since the cache contains an entry for each room surrogate and the clearing
 * algorithm is based on the usage of each entry, it's possible that some entries are removed
 * while others don't thus generating that the provided list of discovered rooms won't be complete.
 * However, this possibility is low since the clients will most of the time ask for all the cache
 * entries and then ask for a particular entry. Anyway, if this possibility happens the cache will
 * be reset the next time that a persistent room is removed from memory.
 *
 * @author Gaston Dombiak
 */
public class MultiUserChatServerImpl extends BasicModule implements MultiUserChatServer,
        ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider, RoutableChannelHandler {

    /**
     * The time to elapse between clearing of idle chat users.
     */
    private int user_timeout = 300000;
    /**
     * The number of milliseconds a user must be idle before he/she gets kicked from all the rooms.
     */
    private int user_idle = 1800000;
    /**
     * Task that kicks idle users from the rooms.
     */
    private UserTimeoutTask userTimeoutTask;
    /**
     * The time to elapse between logging the room conversations.
     */
    private int log_timeout = 300000;
    /**
     * The number of messages to log on each run of the logging process.
     */
    private int log_batch_size = 50;
    /**
     * Task that flushes room conversation logs to the database.
     */
    private LogConversationTask logConversationTask;
    /**
     * the chat service's hostname
     */
    private String chatServiceName = null;
    private JID chatServiceAddress = null;

    /**
     * chatrooms managed by this manager, table: key room name (String); value ChatRoom
     */
    private Map<String,MUCRoom> rooms = new ConcurrentHashMap<String,MUCRoom>();

    /**
     * chat users managed by this manager, table: key user jid (XMPPAddress); value ChatUser
     */
    private Map<JID, MUCUser> users = new ConcurrentHashMap<JID, MUCUser>();
    private HistoryStrategy historyStrategy;

    private RoutingTable routingTable = null;
    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;
    /**
     * The handler of packets with namespace jabber:iq:register for the server.
     */
    private IQRegisterHandler registerHandler = null;
    /**
     * The total time all agents took to chat *
     */
    public long totalChatTime;

    /**
     * Timer to monitor chatroom participants. If they've been idle for too long, probe for
     * presence.
     */
    private Timer timer = new Timer();

    /**
     * Returns the permission policy for creating rooms. A true value means that not anyone can
     * create a room, only the JIDs listed in <code>allowedToCreate</code> are allowed to create
     * rooms.
     */
    private boolean roomCreationRestricted = false;

    /**
     * Bare jids of users that are allowed to create MUC rooms. An empty list means that anyone can 
     * create a room. 
     */
    private Collection<String> allowedToCreate = new CopyOnWriteArrayList<String>();

    /**
     * Bare jids of users that are system administrators of the MUC service. A sysadmin has the same
     * permissions as a room owner. 
     */
    private Collection<String> sysadmins = new CopyOnWriteArrayList<String>();

    /**
     * Queue that holds the messages to log for the rooms that need to log their conversations.
     */
    private Queue<ConversationLogEntry> logQueue = new LinkedBlockingQueue<ConversationLogEntry>();

    /**
     * Create a new group chat server.
     */
    public MultiUserChatServerImpl() {
        super("Basic multi user chat server");
        historyStrategy = new HistoryStrategy(null);
    }

    /**
     * Probes the presence of any user who's last packet was sent more than 5 minute ago.
     */
    private class UserTimeoutTask extends TimerTask {
        /**
         * Remove any user that has been idle for longer than the user timeout time.
         */
        public void run() {
            checkForTimedOutUsers();
        }
    }

    private void checkForTimedOutUsers() {
        // Do nothing if this feature is disabled (i.e USER_IDLE equals -1)
        if (user_idle == -1) {
            return;
        }
        final long deadline = System.currentTimeMillis() - user_idle;
        for (MUCUser user : users.values()) {
            try {
                if (user.getLastPacketTime() < deadline) {
                    // Kick the user from all the rooms that he/she had previuosly joined
                    Iterator<MUCRole> roles = user.getRoles();
                    MUCRole role;
                    MUCRoom room;
                    Presence kickedPresence;
                    while (roles.hasNext()) {
                        role = roles.next();
                        room = role.getChatRoom();
                        try {
                            kickedPresence =
                                    room.kickOccupant(user.getAddress(), null, null);
                            // Send the updated presence to the room occupants
                            room.send(kickedPresence);
                        }
                        catch (NotAllowedException e) {
                            // Do nothing since we cannot kick owners or admins
                        }
                    }
                }
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Logs the conversation of the rooms that have this feature enabled.
     */
    private class LogConversationTask extends TimerTask {
        public void run() {
            try {
                logConversation();
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void logConversation() {
        ConversationLogEntry entry = null;
        boolean success = false;
        for (int index = 0; index <= log_batch_size && !logQueue.isEmpty(); index++) {
            entry = logQueue.poll();
            if (entry != null) {
                success = MUCPersistenceManager.saveConversationLogEntry(entry);
                if (!success) {
                    logQueue.add(entry);
                }
            }
        }
    }

    /**
     * Logs all the remaining conversation log entries to the database. Use this method to force
     * saving all the conversation log entries before the service becomes unavailable.
     */
    private void logAllConversation() {
        ConversationLogEntry entry = null;
        while (!logQueue.isEmpty()) {
            entry = logQueue.poll();
            if (entry != null) {
                MUCPersistenceManager.saveConversationLogEntry(entry);
            }
        }
    }

    public MUCRoom getChatRoom(String roomName, JID userjid) throws UnauthorizedException {
        MUCRoom room = null;
        synchronized (rooms) {
            room = rooms.get(roomName.toLowerCase());
            if (room == null) {
                room = new MUCRoomImpl(this, roomName, router);
                // If the room is persistent load the configuration values from the DB
                try {
                    // Try to load the room's configuration from the database (if the room is
                    // persistent but was added to the DB after the server was started up)
                    MUCPersistenceManager.loadFromDB(room);
                }
                catch (IllegalArgumentException e) {
                    // The room does not exist so check for creation permissions
                    // Room creation is always allowed for sysadmin
                    if (isRoomCreationRestricted() &&
                            !sysadmins.contains(userjid.toBareJID())) {
                        // The room creation is only allowed for certain JIDs
                        if (!allowedToCreate.contains(userjid.toBareJID())) {
                            // The user is not in the list of allowed JIDs to create a room so raise
                            // an exception
                            throw new UnauthorizedException();
                        }
                    }
                    room.addFirstOwner(userjid.toBareJID());
                }
                rooms.put(roomName.toLowerCase(), room);
            }
        }
        return room;
    }

    public MUCRoom getChatRoom(String roomName) {
        return rooms.get(roomName.toLowerCase());
    }

    public List<MUCRoom> getChatRooms() {
        return new ArrayList<MUCRoom>(rooms.values());
    }

    public boolean hasChatRoom(String roomName) {
        return getChatRoom(roomName) != null;
    }

    public void removeChatRoom(String roomName) {
        final MUCRoom room = rooms.remove(roomName.toLowerCase());
        if (room != null) {
            final long chatLength = room.getChatLength();
            totalChatTime += chatLength;
        }
    }

    public String getServiceName() {
        return chatServiceName;
    }

    public HistoryStrategy getHistoryStrategy() {
        return historyStrategy;
    }

    public void removeUser(JID jabberID) {
        MUCUser user = users.remove(jabberID);
        if (user != null) {
            Iterator<MUCRole> roles = user.getRoles();
            while (roles.hasNext()) {
                MUCRole role = roles.next();
                try {
                    role.getChatRoom().leaveRoom(role.getNickname());
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }
    }

    public MUCUser getChatUser(JID userjid) throws UserNotFoundException {
        if (router == null) {
            throw new IllegalStateException("Not initialized");
        }
        MUCUser user = null;
        synchronized (users) {
            user = users.get(userjid);
            if (user == null) {
                user = new MUCUserImpl(this, router, userjid);
                users.put(userjid, user);
            }
        }
        return user;
    }

    public void serverBroadcast(String msg) throws UnauthorizedException {
        for (MUCRoom room : rooms.values()) {
            room.serverBroadcast(msg);
        }
    }

    public void setServiceName(String name) {
        JiveGlobals.setProperty("xmpp.muc.service", name);
    }

    public void setKickIdleUsersTimeout(int timeout) {
        if (this.user_timeout == timeout) {
            return;
        }
        // Cancel the existing task because the timeout has changed
        if (userTimeoutTask != null) {
            userTimeoutTask.cancel();
        }
        this.user_timeout = timeout;
        // Create a new task and schedule it with the new timeout
        userTimeoutTask = new UserTimeoutTask();
        timer.schedule(userTimeoutTask, user_timeout, user_timeout);
        // Set the new property value
        JiveGlobals.setProperty("xmpp.muc.tasks.user.timeout", Integer.toString(timeout));
    }

    public int getKickIdleUsersTimeout() {
        return user_timeout;
    }

    public void setUserIdleTime(int idleTime) {
        if (this.user_idle == idleTime) {
            return;
        }
        this.user_idle = idleTime;
        // Set the new property value
        JiveGlobals.setProperty("xmpp.muc.tasks.user.idle", Integer.toString(idleTime));
    }

    public int getUserIdleTime() {
        return user_idle;
    }

    public void setLogConversationsTimeout(int timeout) {
        if (this.log_timeout == timeout) {
            return;
        }
        // Cancel the existing task because the timeout has changed
        if (logConversationTask != null) {
            logConversationTask.cancel();
        }
        this.log_timeout = timeout;
        // Create a new task and schedule it with the new timeout
        logConversationTask = new LogConversationTask();
        timer.schedule(logConversationTask, log_timeout, log_timeout);
        // Set the new property value
        JiveGlobals.setProperty("xmpp.muc.tasks.log.timeout", Integer.toString(timeout));
    }

    public int getLogConversationsTimeout() {
        return log_timeout;
    }

    public void setLogConversationBatchSize(int size) {
        if (this.log_batch_size == size) {
            return;
        }
        this.log_batch_size = size;
        // Set the new property value
        JiveGlobals.setProperty("xmpp.muc.tasks.log.batchsize", Integer.toString(size));
    }

    public int getLogConversationBatchSize() {
        return log_batch_size;
    }

    public Collection<String> getUsersAllowedToCreate() {
        return allowedToCreate;
    }

    public Collection<String> getSysadmins() {
        return sysadmins;
    }

    public void addSysadmin(String userJID) {
        sysadmins.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = (String[])sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.muc.sysadmin.jid", fromArray(jids));
    }

    public void removeSysadmin(String userJID) {
        sysadmins.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = (String[])sysadmins.toArray(jids);
        JiveGlobals.setProperty("xmpp.muc.sysadmin.jid", fromArray(jids));
    }

    public boolean isRoomCreationRestricted() {
        return roomCreationRestricted;
    }

    public void setRoomCreationRestricted(boolean roomCreationRestricted) {
        this.roomCreationRestricted = roomCreationRestricted;
        JiveGlobals.setProperty("xmpp.muc.create.anyone", Boolean.toString(roomCreationRestricted));
    }

    public void addUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
        // variable there is no need to restart the service
        allowedToCreate.add(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = (String[])allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.muc.create.jid", fromArray(jids));
    }

    public void removeUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
        // variable there is no need to restart the service
        allowedToCreate.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = (String[])allowedToCreate.toArray(jids);
        JiveGlobals.setProperty("xmpp.muc.create.jid", fromArray(jids));
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        chatServiceName = JiveGlobals.getProperty("xmpp.muc.service");
        // Trigger the strategy to load itself from the context
        historyStrategy.setContext("xmpp.muc.history");
        // Load the list of JIDs that are sysadmins of the MUC service
        String property = JiveGlobals.getProperty("xmpp.muc.sysadmin.jid");
        String[] jids;
        if (property != null) {
            jids = property.split(",");
            for (int i = 0; i < jids.length; i++) {
                sysadmins.add(jids[i].trim().toLowerCase());
            }
        }
        roomCreationRestricted =
                Boolean.parseBoolean(JiveGlobals.getProperty("xmpp.muc.create.anyone", "false"));
        // Load the list of JIDs that are allowed to create a MUC room
        property = JiveGlobals.getProperty("xmpp.muc.create.jid");
        if (property != null) {
            jids = property.split(",");
            for (int i = 0; i < jids.length; i++) {
                allowedToCreate.add(jids[i].trim().toLowerCase());
            }
        }
        String value = JiveGlobals.getProperty("xmpp.muc.tasks.user.timeout");
        if (value != null) {
            try {
                user_timeout = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.user.timeout", e);
            }
        }
        value = JiveGlobals.getProperty("xmpp.muc.tasks.user.idle");
        if (value != null) {
            try {
                user_idle = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.user.idle", e);
            }
        }
        value = JiveGlobals.getProperty("xmpp.muc.tasks.log.timeout");
        if (value != null) {
            try {
                log_timeout = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.log.timeout", e);
            }
        }
        value = JiveGlobals.getProperty("xmpp.muc.tasks.log.batchsize");
        if (value != null) {
            try {
                log_batch_size = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.log.batchsize", e);
            }
        }
        if (chatServiceName == null) {
            chatServiceName = "conference";
        }
        String serverName = null;
        serverName = server.getServerInfo().getName();
        if (serverName != null) {
            chatServiceName += "." + serverName;
        }
        chatServiceAddress = new JID(null, chatServiceName, null);
        // Run through the users every 5 minutes after a 5 minutes server startup delay (default
        // values)
        userTimeoutTask = new UserTimeoutTask();
        timer.schedule(userTimeoutTask, user_timeout, user_timeout);
        // Log the room conversations every 5 minutes after a 5 minutes server startup delay
        // (default values)
        logConversationTask = new LogConversationTask();
        timer.schedule(logConversationTask, log_timeout, log_timeout);

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();
        // TODO Remove the tracking for IQRegisterHandler when the component JEP gets implemented.
        registerHandler = server.getIQRegisterHandler();
        registerHandler.addDelegate(getServiceName(), new IQMUCRegisterHandler(this));

        // Add the route to this service
        routingTable.addRoute(chatServiceAddress, this);
        ArrayList params = new ArrayList();
        params.clear();
        params.add(chatServiceName);
        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", params));
    }

    public void start() {
        super.start();
        routingTable.addRoute(chatServiceAddress, this);
        ArrayList params = new ArrayList();
        params.clear();
        params.add(chatServiceName);
        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", params));
        // Load all the persistent rooms to memory
        for (MUCRoom room : MUCPersistenceManager.loadRoomsFromDB(this, router)) {
            rooms.put(room.getName().toLowerCase(), room);
        }
    }

    public void stop() {
        super.stop();
        routingTable.removeRoute(chatServiceAddress);
        timer.cancel();
        logAllConversation();
        if (registerHandler != null) {
            registerHandler.removeDelegate(getServiceName());
        }
    }

    public JID getAddress() {
        if (chatServiceAddress == null) {
            throw new IllegalStateException("Not initialized");
        }
        return chatServiceAddress;
    }

    public void process(Packet packet) {
        try {
            MUCUser user = getChatUser(packet.getFrom());
            user.process(packet);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public long getTotalChatTime() {
        return totalChatTime;
    }

    public void logConversation(MUCRoom room, Message message, JID sender) {
        logQueue.add(new ConversationLogEntry(new Date(), room, message, sender));
    }

    public Iterator getItems() {
        ArrayList items = new ArrayList();

        items.add(new DiscoServerItem() {
            public String getJID() {
                return chatServiceName;
            }

            public String getName() {
                return "Public Chatrooms";
            }

            public String getAction() {
                return null;
            }

            public String getNode() {
                return null;
            }

            public DiscoInfoProvider getDiscoInfoProvider() {
                return MultiUserChatServerImpl.this;
            }

            public DiscoItemsProvider getDiscoItemsProvider() {
                return MultiUserChatServerImpl.this;
            }
        });
        return items.iterator();
    }

    public Iterator getIdentities(String name, String node, JID senderJID) {
        // TODO Improve performance by not creating objects each time
        ArrayList identities = new ArrayList();
        if (name == null && node == null) {
            // Answer the identity of the MUC service
            Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "conference");
            identity.addAttribute("name", "Public Chatrooms");
            identity.addAttribute("type", "text");

            identities.add(identity);
        }
        else if (name != null && node == null) {
            // Answer the identity of a given room
            MUCRoom room = getChatRoom(name);
            if (room != null && room.isPublicRoom()) {
                Element identity = DocumentHelper.createElement("identity");
                identity.addAttribute("category", "conference");
                identity.addAttribute("name", room.getNaturalLanguageName());
                identity.addAttribute("type", "text");

                identities.add(identity);
            }
        }
        else if (name != null && "x-roomuser-item".equals(node)) {
            // Answer reserved nickname for the sender of the disco request in the requested room
            MUCRoom room = getChatRoom(name);
            if (room != null) {
                String reservedNick = room.getReservedNickname(senderJID.toBareJID());
                if (reservedNick != null) {
                    Element identity = DocumentHelper.createElement("identity");
                    identity.addAttribute("category", "conference");
                    identity.addAttribute("name", reservedNick);
                    identity.addAttribute("type", "text");

                    identities.add(identity);
                }
            }
        }
        return identities.iterator();
    }

    public Iterator getFeatures(String name, String node, JID senderJID) {
        ArrayList features = new ArrayList();
        if (name == null && node == null) {
            // Answer the features of the MUC service
            features.add("http://jabber.org/protocol/muc");
            features.add("http://jabber.org/protocol/disco#info");
            features.add("http://jabber.org/protocol/disco#items");
        }
        else if (name != null && node == null) {
            // Answer the features of a given room
            // TODO lock the room while gathering this info???
            MUCRoom room = getChatRoom(name);
            if (room != null && room.isPublicRoom()) {
                features.add("http://jabber.org/protocol/muc");
                // Always add public since only public rooms can be discovered
                features.add("muc_public");
                if (room.isInvitationRequiredToEnter()) {
                    features.add("muc_membersonly");
                }
                else {
                    features.add("muc_open");
                }
                if (room.isModerated()) {
                    features.add("muc_moderated");
                }
                else {
                    features.add("muc_unmoderated");
                }
                if (room.canAnyoneDiscoverJID()) {
                    features.add("muc_nonanonymous");
                }
                else {
                    features.add("muc_semianonymous");
                }
                if (room.isPasswordProtected()) {
                    features.add("muc_passwordprotected");
                }
                else {
                    features.add("muc_unsecured");
                }
                if (room.isPersistent()) {
                    features.add("muc_persistent");
                }
                else {
                    features.add("muc_temporary");
                }
            }
        }
        return features.iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        if (name != null && node == null) {
            // Answer the extended info of a given room
            // TODO lock the room while gathering this info???
            // TODO Do not generate a form each time. Keep it as static or instance variable
            MUCRoom room = getChatRoom(name);
            if (room != null && room.isPublicRoom()) {
                XDataFormImpl dataForm = new XDataFormImpl(DataForm.TYPE_RESULT);

                XFormFieldImpl field = new XFormFieldImpl("FORM_TYPE");
                field.setType(FormField.TYPE_HIDDEN);
                field.addValue("http://jabber.org/protocol/muc#roominfo");
                dataForm.addField(field);

                field = new XFormFieldImpl("muc#roominfo_description");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.desc"));
                field.addValue(room.getDescription());
                dataForm.addField(field);

                field = new XFormFieldImpl("muc#roominfo_subject");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.subject"));
                field.addValue(room.getSubject());
                dataForm.addField(field);

                field = new XFormFieldImpl("muc#roominfo_occupants");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.occupants"));
                field.addValue(Integer.toString(room.getOccupantsCount()));
                dataForm.addField(field);

                /*field = new XFormFieldImpl("muc#roominfo_lang");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.language"));
                field.addValue(room.getLanguage());
                dataForm.addField(field);*/

                return dataForm;
            }
        }
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID)
            throws UnauthorizedException {
        if (name == null && node == node) {
            // We always have info about the MUC service
            return true;
        }
        else if (name != null && node == null) {
            // We only have info if the room exists
            return hasChatRoom(name);
        }
        else if (name != null && "x-roomuser-item".equals(node)) {
            // We always have info about reserved names as long as the room exists
            return hasChatRoom(name);
        }
        return false;
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID)
            throws UnauthorizedException {
        List<Element> answer = new ArrayList<Element>();
        if (name == null && node == null) {
            Element item;
            // Answer all the public rooms as items
            for (MUCRoom room : rooms.values()) {
                if (room.isPublicRoom()) {
                    item = DocumentHelper.createElement("item");
                    item.addAttribute("jid", room.getRole().getRoleAddress().toString());
                    item.addAttribute("name", room.getNaturalLanguageName());

                    answer.add(item);
                }
            }
        }
        else if (name != null && node == null) {
            // Answer the room occupants as items if that info is publicly available
            MUCRoom room = getChatRoom(name);
            if (room != null && room.isPublicRoom()) {
                Element item;
                for (MUCRole role : room.getOccupants()) {
                    // TODO Should we filter occupants that are invisible (presence is not broadcasted)?
                    item = DocumentHelper.createElement("item");
                    item.addAttribute("jid", role.getRoleAddress().toString());

                    answer.add(item);
                }
            }
        }
        return answer.iterator();
    }

    /**
     * Converts an array to a comma-delimitted String.
     *
     * @param array the array.
     * @return a comma delimtted String of the array values.
     */
    private static String fromArray(String [] array) {
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(",");
            }
        }
        return buf.toString();
    }
}