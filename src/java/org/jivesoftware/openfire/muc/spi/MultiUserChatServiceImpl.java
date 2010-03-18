/**
 * $RCSfile: MultiUserChatServerImpl.java,v $
 * $Revision: 3036 $
 * $Date: 2005-11-07 15:15:00 -0300 (Mon, 07 Nov 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.disco.DiscoInfoProvider;
import org.jivesoftware.openfire.disco.DiscoItem;
import org.jivesoftware.openfire.disco.DiscoItemsProvider;
import org.jivesoftware.openfire.disco.DiscoServerItem;
import org.jivesoftware.openfire.disco.ServerItemsProvider;
import org.jivesoftware.openfire.muc.HistoryStrategy;
import org.jivesoftware.openfire.muc.MUCEventDelegate;
import org.jivesoftware.openfire.muc.MUCEventDispatcher;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.MUCRoom;
import org.jivesoftware.openfire.muc.MUCUser;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.muc.cluster.GetNumberConnectedUsers;
import org.jivesoftware.openfire.muc.cluster.OccupantAddedEvent;
import org.jivesoftware.openfire.muc.cluster.RoomAvailableEvent;
import org.jivesoftware.openfire.muc.cluster.RoomRemovedEvent;
import org.jivesoftware.util.FastDateFormat;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveProperties;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.forms.DataForm.Type;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.resultsetmanagement.ResultSet;

/**
 * Implements the chat server as a cached memory resident chat server. The server is also
 * responsible for responding Multi-User Chat disco requests as well as removing inactive users from
 * the rooms after a period of time and to maintain a log of the conversation in the rooms that 
 * require to log their conversations. The conversations log is saved to the database using a 
 * separate process<p>
 *
 * Temporary rooms are held in memory as long as they have occupants. They will be destroyed after
 * the last occupant left the room. On the other hand, persistent rooms are always present in memory
 * even after the last occupant left the room. In order to keep memory clean of persistent rooms that
 * have been forgotten or abandoned this class includes a clean up process. The clean up process
 * will remove from memory rooms that haven't had occupants for a while. Moreover, forgotten or
 * abandoned rooms won't be loaded into memory when the Multi-User Chat service starts up.
 *
 * @author Gaston Dombiak
 */
public class MultiUserChatServiceImpl implements Component, MultiUserChatService,
        ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider {

	private static final Logger Log = LoggerFactory.getLogger(MultiUserChatServiceImpl.class);

    private static final FastDateFormat dateFormatter = FastDateFormat
            .getInstance(JiveConstants.XMPP_DELAY_DATETIME_FORMAT, TimeZone.getTimeZone("UTC"));



    /**
     * The time to elapse between clearing of idle chat users.
     */
    private int user_timeout = 300000;
    /**
     * The number of milliseconds a user must be idle before he/she gets kicked from all the rooms.
     */
    private int user_idle = -1;
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
     * the chat service's hostname (subdomain)
     */
    private final String chatServiceName;
    /**
     * the chat service's description
     */
    private String chatDescription = null;

    /**
     * chatrooms managed by this manager, table: key room name (String); value ChatRoom
     */
    private Map<String, LocalMUCRoom> rooms = new ConcurrentHashMap<String, LocalMUCRoom>();

    /**
     * Chat users managed by this manager. This includes only users connected to this JVM.
     * That means that when running inside of a cluster each node will have its own manager
     * that in turn will keep its own list of locally connected.
     *
     * table: key user jid (XMPPAddress); value ChatUser
     */
    private Map<JID, LocalMUCUser> users = new ConcurrentHashMap<JID, LocalMUCUser>();
    private HistoryStrategy historyStrategy;

    private RoutingTable routingTable = null;
    /**
     * The packet router for the server.
     */
    private PacketRouter router = null;
    /**
     * The handler of packets with namespace jabber:iq:register for the server.
     */
    private IQMUCRegisterHandler registerHandler = null;
    
    /**
     * The handler of search requests ('jabber:iq:search' namespace).
     */
    private IQMUCSearchHandler searchHandler = null;
    
    /**
     * The total time all agents took to chat *
     */
    public long totalChatTime;

    /**
     * Timer to monitor chatroom participants. If they've been idle for too long, probe for
     * presence.
     */
    private Timer timer = new Timer("MUC cleanup");

    /**
     * Flag that indicates if the service should provide information about locked rooms when
     * handling service discovery requests.
     * Note: Setting this flag in false is not compliant with the spec. A user may try to join a
     * locked room thinking that the room doesn't exist because the user didn't discover it before.
     */
    private boolean allowToDiscoverLockedRooms = true;

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
    private List<String> allowedToCreate = new CopyOnWriteArrayList<String>();

    /**
     * Bare jids of users that are system administrators of the MUC service. A sysadmin has the same
     * permissions as a room owner.
     */
    private List<String> sysadmins = new CopyOnWriteArrayList<String>();

    /**
     * Queue that holds the messages to log for the rooms that need to log their conversations.
     */
    private Queue<ConversationLogEntry> logQueue = new LinkedBlockingQueue<ConversationLogEntry>(100000);

    /**
     * Max number of hours that a persistent room may be empty before the service removes the
     * room from memory. Unloaded rooms will exist in the database and may be loaded by a user
     * request. Default time limit is: 30 days.
     */
    private long emptyLimit = 30 * 24;
    /**
     * Task that removes rooms from memory that have been without activity for a period of time. A
     * room is considered without activity when no occupants are present in the room for a while.
     */
    private CleanupTask cleanupTask;
    /**
     * The time to elapse between each rooms cleanup. Default frequency is 60 minutes.
     */
    private static final long CLEANUP_FREQUENCY = 60 * 60 * 1000;

    /**
     * Total number of received messages in all rooms since the last reset. The counter
     * is reset each time the Statistic makes a sampling.
     */
    private AtomicInteger inMessages = new AtomicInteger(0);
    /**
     * Total number of broadcasted messages in all rooms since the last reset. The counter
     * is reset each time the Statistic makes a sampling.
     */
    private AtomicLong outMessages = new AtomicLong(0);

    /**
     * Flag that indicates if MUC service is enabled.
     */
    private boolean serviceEnabled = true;

    /**
     * Flag that indicates if MUC service is hidden from services views.
     */
    private boolean isHidden = true;

    /**
     * Delegate responds to events for the MUC service.
     */
    protected MUCEventDelegate mucEventDelegate;

    /**
     * Additional features to be added to the disco response for the service.
     */
    private List<String> extraDiscoFeatures = new ArrayList<String>();

    /**
     * Additional identities to be added to the disco response for the service.
     */
    private List<Element> extraDiscoIdentities = new ArrayList<Element>();

    /**
	 * Create a new group chat server.
	 * 
	 * @param subdomain
	 *            Subdomain portion of the conference services (for example,
	 *            conference for conference.example.org)
	 * @param description
	 *            Short description of service for disco and such. If
	 *            <tt>null</tt> or empty, a default value will be used.
	 * @param isHidden
	 *            True if this service should be hidden from services views.
	 * @throws IllegalArgumentException
	 *             if the provided subdomain is an invalid, according to the JID
	 *             domain definition.
	 */
    public MultiUserChatServiceImpl(String subdomain, String description, Boolean isHidden) {
        // Check subdomain and throw an IllegalArgumentException if its invalid
        new JID(null,subdomain + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null);

        this.chatServiceName = subdomain;
        if (description != null && description.trim().length() > 0) {
            this.chatDescription = description;
        }
        else {
            this.chatDescription = LocaleUtils.getLocalizedString("muc.service-name");
        }
        this.isHidden = isHidden;
        historyStrategy = new HistoryStrategy(null);
    }

    public String getDescription() {
        return chatDescription;
    }

    public void setDescription(String desc) {
        this.chatDescription = desc;
    }

    public void processPacket(Packet packet) {
        if (!isServiceEnabled()) {
            return;
        }
        // The MUC service will receive all the packets whose domain matches the domain of the MUC
        // service. This means that, for instance, a disco request should be responded by the
        // service itself instead of relying on the server to handle the request.
        try {
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (process((IQ)packet)) {
                    return;
                }
            }
            // The packet is a normal packet that should possibly be sent to the room
            JID receipient = packet.getTo();
            String roomName = receipient != null ? receipient.getNode() : null;
            getChatUser(packet.getFrom(), roomName).process(packet);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Returns true if the IQ packet was processed. This method should only process disco packets
     * as well as jabber:iq:register packets sent to the MUC service.
     *
     * @param iq the IQ packet to process.
     * @return true if the IQ packet was processed.
     */
    private boolean process(IQ iq) {
        Element childElement = iq.getChildElement();
        String namespace = null;
        // Ignore IQs of type ERROR
        if (IQ.Type.error == iq.getType()) {
            return false;
        }
        if (iq.getTo().getResource() != null) {
            // Ignore IQ packets sent to room occupants
            return false;
        }
        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }
        if ("jabber:iq:register".equals(namespace)) {
            IQ reply = registerHandler.handleIQ(iq);
            router.route(reply);
        }
        else if ("jabber:iq:search".equals(namespace)) {
            IQ reply = searchHandler.handleIQ(iq);
            router.route(reply);
        }
        else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            // TODO MUC should have an IQDiscoInfoHandler of its own when MUC becomes
            // a component
            IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
            router.route(reply);
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            // TODO MUC should have an IQDiscoItemsHandler of its own when MUC becomes
            // a component
            IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
            router.route(reply);
        }
        else {
            return false;
        }
        return true;
    }

    public void initialize(JID jid, ComponentManager componentManager) {
        initialize(XMPPServer.getInstance());

    }

    public void shutdown() {

    }

    public String getServiceDomain() {
        return chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null, true);
    }

    /**
     * Probes the presence of any user who's last packet was sent more than 5 minute ago.
     */
    private class UserTimeoutTask extends TimerTask {
        /**
         * Remove any user that has been idle for longer than the user timeout time.
         */
        @Override
		public void run() {
            checkForTimedOutUsers();
        }
    }

    private void checkForTimedOutUsers() {
        final long deadline = System.currentTimeMillis() - user_idle;
        for (LocalMUCUser user : users.values()) {
            try {
                // If user is not present in any room then remove the user from
                // the list of users
                if (!user.isJoined()) {
                    removeUser(user.getAddress());
                    continue;
                }
                // Do nothing if this feature is disabled (i.e USER_IDLE equals -1)
                if (user_idle == -1) {
                    continue;
                }
                if (user.getLastPacketTime() < deadline) {
                    // Kick the user from all the rooms that he/she had previuosly joined
                    MUCRoom room;
                    Presence kickedPresence;
                    for (LocalMUCRole role : user.getRoles()) {
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
        @Override
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
        ConversationLogEntry entry;
        boolean success;
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
        ConversationLogEntry entry;
        while (!logQueue.isEmpty()) {
            entry = logQueue.poll();
            if (entry != null) {
                MUCPersistenceManager.saveConversationLogEntry(entry);
            }
        }
    }

    /**
     * Removes from memory rooms that have been without activity for a period of time. A room is
     * considered without activity when no occupants are present in the room for a while.
     */
    private class CleanupTask extends TimerTask {
        @Override
		public void run() {
            if (ClusterManager.isClusteringStarted() && !ClusterManager.isSeniorClusterMember()) {
                // Do nothing if we are in a cluster and this JVM is not the senior cluster member
                return;
            }
            try {
                cleanupRooms();
            }
            catch (Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    private void cleanupRooms() {
        for (MUCRoom room : rooms.values()) {
            if (room.getEmptyDate() != null && room.getEmptyDate().before(getCleanupDate())) {
                removeChatRoom(room.getName());
            }
        }
    }

    public MUCRoom getChatRoom(String roomName, JID userjid) throws NotAllowedException {
        LocalMUCRoom room;
        boolean loaded = false;
        boolean created = false;
        synchronized (roomName.intern()) {
            room = rooms.get(roomName);
            if (room == null) {
                room = new LocalMUCRoom(this, roomName, router);
                // If the room is persistent load the configuration values from the DB
                try {
                    // Try to load the room's configuration from the database (if the room is
                    // persistent but was added to the DB after the server was started up or the
                    // room may be an old room that was not present in memory)
                    MUCPersistenceManager.loadFromDB(room);
                    loaded = true;
                }
                catch (IllegalArgumentException e) {
                    // Check if room needs to be recreated in case it failed to be created previously
                    // (or was deleted somehow and is expected to exist by a delegate).
                    if (mucEventDelegate != null && mucEventDelegate.shouldRecreate(roomName, userjid)) {
                        if (mucEventDelegate.loadConfig(room)) {
                            loaded = true;
                            if (room.isPersistent()) {
                                MUCPersistenceManager.saveToDB(room);
                            }
                        }
                        else {
                            // Room does not exist and delegate does not recognize it and does
                            // not allow room creation
                            throw new NotAllowedException();

                        }
                    }
                    else {
                        // The room does not exist so check for creation permissions
                        // Room creation is always allowed for sysadmin
                        if (isRoomCreationRestricted() && !sysadmins.contains(userjid.toBareJID())) {
                            // The room creation is only allowed for certain JIDs
                            if (!allowedToCreate.contains(userjid.toBareJID())) {
                                // The user is not in the list of allowed JIDs to create a room so raise
                                // an exception
                                throw new NotAllowedException();
                            }
                        }
                        room.addFirstOwner(userjid.toBareJID());
                        created = true;
                    }
                }
                rooms.put(roomName, room);
            }
        }
        if (created) {
            // Fire event that a new room has been created
            MUCEventDispatcher.roomCreated(room.getRole().getRoleAddress());
        }
        if (loaded || created) {
            // Notify other cluster nodes that a new room is available
            CacheFactory.doClusterTask(new RoomAvailableEvent(room));
            for (MUCRole role : room.getOccupants()) {
                if (role instanceof LocalMUCRole) {
                    CacheFactory.doClusterTask(new OccupantAddedEvent(room, role));
                }
            }
        }
        return room;
    }

    public MUCRoom getChatRoom(String roomName) {
        boolean loaded = false;
        LocalMUCRoom room = rooms.get(roomName);
        if (room == null) {
            // Check if the room exists in the databclase and was not present in memory
            synchronized (roomName.intern()) {
                room = rooms.get(roomName);
                if (room == null) {
                    room = new LocalMUCRoom(this, roomName, router);
                    // If the room is persistent load the configuration values from the DB
                    try {
                        // Try to load the room's configuration from the database (if the room is
                        // persistent but was added to the DB after the server was started up or the
                        // room may be an old room that was not present in memory)
                        MUCPersistenceManager.loadFromDB(room);
                        loaded = true;
                        rooms.put(roomName, room);
                    }
                    catch (IllegalArgumentException e) {
                        // The room does not exist so do nothing
                        room = null;
                    }
                }
            }
        }
        if (loaded) {
            // Notify other cluster nodes that a new room is available
            CacheFactory.doClusterTask(new RoomAvailableEvent(room));
        }
        return room;
    }

    public LocalMUCRoom getLocalChatRoom(String roomName) {
        return rooms.get(roomName);
    }

    public List<MUCRoom> getChatRooms() {
        return new ArrayList<MUCRoom>(rooms.values());
    }

    public boolean hasChatRoom(String roomName) {
        return getChatRoom(roomName) != null;
    }

    public void removeChatRoom(String roomName) {
        removeChatRoom(roomName, true);
    }

    /**
     * Notification message indicating that the specified chat room was
     * removed from some other cluster member.
     *
     * @param room the removed room in another cluster node.
     */
    public void chatRoomRemoved(LocalMUCRoom room) {
        removeChatRoom(room.getName(), false);
    }

    /**
     * Notification message indicating that a chat room has been created
     * in another cluster member.
     *
     * @param room the created room in another cluster node.
     */
    public void chatRoomAdded(LocalMUCRoom room) {
        rooms.put(room.getName(), room);
    }

    private void removeChatRoom(String roomName, boolean notify) {
        MUCRoom room = rooms.remove(roomName);
        if (room != null) {
            totalChatTime += room.getChatLength();
            if (notify) {
                // Notify other cluster nodes that a room has been removed
                CacheFactory.doClusterTask(new RoomRemovedEvent((LocalMUCRoom)room));
            }
        }
    }

    public String getServiceName() {
        return chatServiceName;
    }

    public String getName() {
        return getServiceName();
    }

    public HistoryStrategy getHistoryStrategy() {
        return historyStrategy;
    }

    /**
     * Removes a user from all chat rooms.
     *
     * @param jabberID The user's normal jid, not the chat nickname jid.
     */
    private void removeUser(JID jabberID) {
        LocalMUCUser user = users.remove(jabberID);
        if (user != null) {
            for (LocalMUCRole role : user.getRoles()) {
                try {
                    role.getChatRoom().leaveRoom(role);
                }
                catch (Exception e) {
                    Log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Obtain a chat user by XMPPAddress. Only returns users that are connected to this JVM.
     *
     * @param userjid The XMPPAddress of the user.
     * @param roomName name of the room to receive the packet.
     * @return The chatuser corresponding to that XMPPAddress.
     */
    private MUCUser getChatUser(JID userjid, String roomName) {
        if (router == null) {
            throw new IllegalStateException("Not initialized");
        }
        LocalMUCUser user;
        synchronized (userjid.toString().intern()) {
            user = users.get(userjid);
            if (user == null) {
                if (roomName != null) {
                    // Check if the JID belong to a user hosted in another cluster node
                    LocalMUCRoom localMUCRoom = rooms.get(roomName);
                    if (localMUCRoom != null) {
                        MUCRole occupant = localMUCRoom.getOccupantByFullJID(userjid);
                        if (occupant != null && !occupant.isLocal()) {
                            return new RemoteMUCUser(userjid, localMUCRoom);
                        }
                    }
                }
                user = new LocalMUCUser(this, router, userjid);
                users.put(userjid, user);
            }
        }
        return user;
    }

    public Collection<MUCRole> getMUCRoles(JID user) {
        List<MUCRole> userRoles = new ArrayList<MUCRole>();
        for (LocalMUCRoom room : rooms.values()) {
            MUCRole role = room.getOccupantByFullJID(user);
            if (role != null) {
                userRoles.add(role);
            }
        }
        return userRoles;
    }

    /**
     * Returns the limit date after which rooms without activity will be removed from memory.
     *
     * @return the limit date after which rooms without activity will be removed from memory.
     */
    private Date getCleanupDate() {
        return new Date(System.currentTimeMillis() - (emptyLimit * 3600000));
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
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.timeout", Integer.toString(timeout));
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
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.idle", Integer.toString(idleTime));
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
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.log.timeout", Integer.toString(timeout));
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
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.log.batchsize", Integer.toString(size));
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
        // CopyOnWriteArray does not allow sorting, so do sorting in temp list.
        ArrayList<String> tempList = new ArrayList<String>(sysadmins);
        Collections.sort(tempList);
        sysadmins = new CopyOnWriteArrayList<String>(tempList);
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        MUCPersistenceManager.setProperty(chatServiceName, "sysadmin.jid", fromArray(jids));
    }

    public void removeSysadmin(String userJID) {
        sysadmins.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[sysadmins.size()];
        jids = sysadmins.toArray(jids);
        MUCPersistenceManager.setProperty(chatServiceName, "sysadmin.jid", fromArray(jids));
    }

    /**
     * Returns the flag that indicates if the service should provide information about locked rooms
     * when handling service discovery requests.
     *
     * @return true if the service should provide information about locked rooms.
     */
    public boolean isAllowToDiscoverLockedRooms() {
        return allowToDiscoverLockedRooms;
    }

    /**
     * Sets the flag that indicates if the service should provide information about locked rooms
     * when handling service discovery requests.
     * Note: Setting this flag in false is not compliant with the spec. A user may try to join a
     * locked room thinking that the room doesn't exist because the user didn't discover it before.
     *
     * @param allowToDiscoverLockedRooms if the service should provide information about locked
     *        rooms.
     */
    public void setAllowToDiscoverLockedRooms(boolean allowToDiscoverLockedRooms) {
        this.allowToDiscoverLockedRooms = allowToDiscoverLockedRooms;
        MUCPersistenceManager.setProperty(chatServiceName, "discover.locked",
                Boolean.toString(allowToDiscoverLockedRooms));
    }

    public boolean isRoomCreationRestricted() {
        return roomCreationRestricted;
    }

    public void setRoomCreationRestricted(boolean roomCreationRestricted) {
        this.roomCreationRestricted = roomCreationRestricted;
        MUCPersistenceManager.setProperty(chatServiceName, "create.anyone", Boolean.toString(roomCreationRestricted));
    }

    public void addUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
        // variable there is no need to restart the service
        allowedToCreate.add(userJID.trim().toLowerCase());
        // CopyOnWriteArray does not allow sorting, so do sorting in temp list.
        ArrayList<String> tempList = new ArrayList<String>(allowedToCreate);
        Collections.sort(tempList);
        allowedToCreate = new CopyOnWriteArrayList<String>(tempList);
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        MUCPersistenceManager.setProperty(chatServiceName, "create.jid", fromArray(jids));
    }

    public void removeUserAllowedToCreate(String userJID) {
        // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
        // variable there is no need to restart the service
        allowedToCreate.remove(userJID.trim().toLowerCase());
        // Update the config.
        String[] jids = new String[allowedToCreate.size()];
        jids = allowedToCreate.toArray(jids);
        MUCPersistenceManager.setProperty(chatServiceName, "create.jid", fromArray(jids));
    }

    public void initialize(XMPPServer server) {
        initializeSettings();

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();
        // Configure the handler of iq:register packets
        registerHandler = new IQMUCRegisterHandler(this);
        // Configure the handler of jabber:iq:search packets
        searchHandler = new IQMUCSearchHandler(this);
    }

    public void initializeSettings() {
        serviceEnabled = JiveProperties.getInstance().getBooleanProperty("xmpp.muc.enabled", true);
        serviceEnabled = MUCPersistenceManager.getBooleanProperty(chatServiceName, "enabled", serviceEnabled);
        // Trigger the strategy to load itself from the context
        historyStrategy.setContext(chatServiceName, "history");
        // Load the list of JIDs that are sysadmins of the MUC service
        String property = MUCPersistenceManager.getProperty(chatServiceName, "sysadmin.jid");
        String[] jids;
        sysadmins.clear();
        if (property != null) {
            jids = property.split(",");
            for (String jid : jids) {
                sysadmins.add(jid.trim().toLowerCase());
            }
        }
        allowToDiscoverLockedRooms =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "discover.locked", true);
        roomCreationRestricted =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "create.anyone", false);
        // Load the list of JIDs that are allowed to create a MUC room
        property = MUCPersistenceManager.getProperty(chatServiceName, "create.jid");
        allowedToCreate.clear();
        if (property != null) {
            jids = property.split(",");
            for (String jid : jids) {
                allowedToCreate.add(jid.trim().toLowerCase());
            }
        }
        String value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.timeout");
        user_timeout = 300000;
        if (value != null) {
            try {
                user_timeout = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.timeout for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.idle");
        user_idle = -1;
        if (value != null) {
            try {
                user_idle = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.idle for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.log.timeout");
        log_timeout = 300000;
        if (value != null) {
            try {
                log_timeout = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property tasks.log.timeout for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.log.batchsize");
        log_batch_size = 50;
        if (value != null) {
            try {
                log_batch_size = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property tasks.log.batchsize for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "unload.empty_days");
        emptyLimit = 30 * 24;
        if (value != null) {
            try {
                emptyLimit = Integer.parseInt(value) * 24;
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property unload.empty_days for service "+chatServiceName, e);
            }
        }
    }

    public void start() {
        // Run through the users every 5 minutes after a 5 minutes server startup delay (default
        // values)
        userTimeoutTask = new UserTimeoutTask();
        timer.schedule(userTimeoutTask, user_timeout, user_timeout);
        // Log the room conversations every 5 minutes after a 5 minutes server startup delay
        // (default values)
        logConversationTask = new LogConversationTask();
        timer.schedule(logConversationTask, log_timeout, log_timeout);
        // Remove unused rooms from memory
        cleanupTask = new CleanupTask();
        timer.schedule(cleanupTask, CLEANUP_FREQUENCY, CLEANUP_FREQUENCY);

        // Set us up to answer disco item requests
        XMPPServer.getInstance().getIQDiscoItemsHandler().addServerItemsProvider(this);
        XMPPServer.getInstance().getIQDiscoInfoHandler().setServerNodeInfoProvider(this.getServiceDomain(), this);
        XMPPServer.getInstance().getServerItemsProviders().add(this);

        ArrayList<String> params = new ArrayList<String>();
        params.clear();
        params.add(getServiceDomain());
        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", params));
        // Load all the persistent rooms to memory
        for (LocalMUCRoom room : MUCPersistenceManager.loadRoomsFromDB(this, this.getCleanupDate(), router)) {
            rooms.put(room.getName().toLowerCase(), room);
        }
    }

    public void stop() {
        XMPPServer.getInstance().getIQDiscoItemsHandler().removeServerItemsProvider(this);
        XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerNodeInfoProvider(this.getServiceDomain());
        XMPPServer.getInstance().getServerItemsProviders().remove(this);
        // Remove the route to this service
        routingTable.removeComponentRoute(getAddress());
        timer.cancel();
        logAllConversation();

    }

    public void enableService(boolean enabled, boolean persistent) {
        if (isServiceEnabled() == enabled) {
            // Do nothing if the service status has not changed
            return;
        }
        XMPPServer server = XMPPServer.getInstance();
        if (!enabled) {
            // Disable disco information
            server.getIQDiscoItemsHandler().removeServerItemsProvider(this);
            // Stop the service/module
            stop();
        }
        if (persistent) {
            MUCPersistenceManager.setProperty(chatServiceName, "enabled", Boolean.toString(enabled));
        }
        serviceEnabled = enabled;
        if (enabled) {
            // Start the service/module
            start();
            // Enable disco information
            server.getIQDiscoItemsHandler().addServerItemsProvider(this);
        }
    }

    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    public long getTotalChatTime() {
        return totalChatTime;
    }

    /**
     * Retuns the number of existing rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return the number of existing rooms in the server.
     */
    public int getNumberChatRooms() {
        return rooms.size();
    }

    /**
     * Retuns the total number of occupants in all rooms in the server.
     *
     * @param onlyLocal true if only users connected to this JVM will be considered. Otherwise count cluster wise.
     * @return the number of existing rooms in the server.
     */
    public int getNumberConnectedUsers(boolean onlyLocal) {
        int total = 0;
        for (LocalMUCUser user : users.values()) {
            if (user.isJoined()) {
                total = total + 1;
            }
        }
        // Add users from remote cluster nodes
        if (!onlyLocal) {
            Collection<Object> results =
                    CacheFactory.doSynchronousClusterTask(new GetNumberConnectedUsers(), false);
            for (Object result : results) {
                if (result == null) {
                    continue;
                }
                total = total + (Integer) result;
            }
        }
        return total;
    }

    /**
     * Retuns the total number of users that have joined in all rooms in the server.
     *
     * @return the number of existing rooms in the server.
     */
    public int getNumberRoomOccupants() {
        int total = 0;
        for (MUCRoom room : rooms.values()) {
            total = total + room.getOccupantsCount();
        }
        return total;
    }

    /**
     * Returns the total number of incoming messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of incoming messages through the service.
     */
    public long getIncomingMessageCount(boolean resetAfter) {
        if (resetAfter) {
            return inMessages.getAndSet(0);
        }
        else {
            return inMessages.get();
        }
    }

    /**
     * Returns the total number of outgoing messages since last reset.
     *
     * @param resetAfter True if you want the counter to be reset after results returned.
     * @return the number of outgoing messages through the service.
     */
    public long getOutgoingMessageCount(boolean resetAfter) {
        if (resetAfter) {
            return outMessages.getAndSet(0);
        }
        else {
            return outMessages.get();
        }
    }

    public void logConversation(MUCRoom room, Message message, JID sender) {
        // Only log messages that have a subject or body. Otherwise ignore it.
        if (message.getSubject() != null || message.getBody() != null) {
            logQueue.add(new ConversationLogEntry(new Date(), room, message, sender));
        }
    }

    public void messageBroadcastedTo(int numOccupants) {
        // Increment counter of received messages that where broadcasted by one
        inMessages.incrementAndGet();
        // Increment counter of outgoing messages with the number of room occupants
        // that received the message
        outMessages.addAndGet(numOccupants);
    }

    public Iterator<DiscoServerItem> getItems() {
        // Check if the service is disabled. Info is not available when
		// disabled.
		if (!isServiceEnabled())
		{
			return null;
		}
		
		final ArrayList<DiscoServerItem> items = new ArrayList<DiscoServerItem>();
		final DiscoServerItem item = new DiscoServerItem(new JID(
			getServiceDomain()), getDescription(), null, null, this, this);
		items.add(item);
		return items.iterator();
	}

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        ArrayList<Element> identities = new ArrayList<Element>();
        if (name == null && node == null) {
            // Answer the identity of the MUC service
            Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "conference");
            identity.addAttribute("name", getDescription());
            identity.addAttribute("type", "text");
            identities.add(identity);

            // TODO: Should internationalize Public Chatroom Search, and make it configurable.
            Element searchId = DocumentHelper.createElement("identity");
            searchId.addAttribute("category", "directory");
            searchId.addAttribute("name", "Public Chatroom Search");
            searchId.addAttribute("type", "chatroom");
            identities.add(searchId);

            if (!extraDiscoIdentities.isEmpty()) {
                identities.addAll(extraDiscoIdentities);
            }
        }
        else if (name != null && node == null) {
            // Answer the identity of a given room
            MUCRoom room = getChatRoom(name);
            if (room != null && canDiscoverRoom(room)) {
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

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        ArrayList<String> features = new ArrayList<String>();
        if (name == null && node == null) {
            // Answer the features of the MUC service
            features.add("http://jabber.org/protocol/muc");
            features.add("http://jabber.org/protocol/disco#info");
            features.add("http://jabber.org/protocol/disco#items");
            features.add("jabber:iq:search");
            features.add(ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT);
            if (!extraDiscoFeatures.isEmpty()) features.addAll(extraDiscoFeatures);
        }
        else if (name != null && node == null) {
            // Answer the features of a given room
            MUCRoom room = getChatRoom(name);
            if (room != null && canDiscoverRoom(room)) {
                features.add("http://jabber.org/protocol/muc");
                // Always add public since only public rooms can be discovered
                features.add("muc_public");
                if (room.isMembersOnly()) {
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

    public DataForm getExtendedInfo(String name, String node, JID senderJID) {
        if (name != null && node == null) {
            // Answer the extended info of a given room
            MUCRoom room = getChatRoom(name);
            if (room != null && canDiscoverRoom(room)) {
                final DataForm dataForm = new DataForm(Type.result);

                final FormField fieldType = dataForm.addField();
                fieldType.setVariable("FORM_TYPE");
                fieldType.setType(FormField.Type.hidden);
                fieldType.addValue("http://jabber.org/protocol/muc#roominfo");

                final FormField fieldDescr = dataForm.addField();
                fieldDescr.setVariable("muc#roominfo_description");
                fieldDescr.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.desc"));
                fieldDescr.addValue(room.getDescription());

                final FormField fieldSubj = dataForm.addField();
                fieldSubj.setVariable("muc#roominfo_subject");
                fieldSubj.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.subject"));
                fieldSubj.addValue(room.getSubject());

                final FormField fieldOcc = dataForm.addField();
                fieldSubj.setVariable("muc#roominfo_occupants");
                fieldSubj.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.occupants"));
                fieldSubj.addValue(Integer.toString(room.getOccupantsCount()));

                /*field = new XFormFieldImpl("muc#roominfo_lang");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.language"));
                field.addValue(room.getLanguage());
                dataForm.addField(field);*/

                final FormField fieldDate = dataForm.addField();
                fieldDate.setVariable("x-muc#roominfo_creationdate");
                fieldDate.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.creationdate"));
                fieldDate.addValue(dateFormatter.format(room.getCreationDate()));

                return dataForm;
            }
        }
        return null;
    }

    /**
     * Adds an extra Disco feature to the list of features returned for the conference service.
     * @param feature Feature to add.
     */
    public void addExtraFeature(String feature) {
        extraDiscoFeatures.add(feature);
    }

    /**
     * Removes an extra Disco feature from the list of features returned for the conference service.
     * @param feature Feature to remove.
     */
    public void removeExtraFeature(String feature) {
        extraDiscoFeatures.remove(feature);
    }

    /**
     * Adds an extra Disco identity to the list of identities returned for the conference service.
     * @param category Category for identity.  e.g. conference
     * @param name Descriptive name for identity.  e.g. Public Chatrooms
     * @param type Type for identity.  e.g. text 
     */
    public void addExtraIdentity(String category, String name, String type) {
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", category);
        identity.addAttribute("name", name);
        identity.addAttribute("type", type);
        extraDiscoIdentities.add(identity);
    }

    /**
     * Removes an extra Disco identity from the list of identities returned for the conference service.
     * @param name Name of identity to remove.
     */
    public void removeExtraIdentity(String name) {
        for (Element elem : extraDiscoIdentities) {
            if (name.equals(elem.attribute("name").getStringValue())) {
                extraDiscoFeatures.remove(elem);
                break;
            }
        }
    }

    /**
     * Sets the MUC event delegate handler for this service.
     * @param delegate Handler for MUC events.
     */
    public void setMUCDelegate(MUCEventDelegate delegate) {
        mucEventDelegate = delegate;
    }

    /**
     * Gets the MUC event delegate handler for this service.
     * @return Handler for MUC events (delegate)
     */
    public MUCEventDelegate getMUCDelegate() {
        return mucEventDelegate;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return false;
        }
        if (name == null && node == null) {
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

    public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        List<DiscoItem> answer = new ArrayList<DiscoItem>();
		if (name == null && node == null)
		{
			// Answer all the public rooms as items
			for (MUCRoom room : rooms.values())
			{
				if (canDiscoverRoom(room))
				{
					answer.add(new DiscoItem(room.getRole().getRoleAddress(),
						room.getNaturalLanguageName(), null, null));
				}
			}
		}
        else if (name != null && node == null) {
            // Answer the room occupants as items if that info is publicly available
            MUCRoom room = getChatRoom(name);
            if (room != null && canDiscoverRoom(room)) {
                for (MUCRole role : room.getOccupants()) {
                    // TODO Should we filter occupants that are invisible (presence is not broadcasted)?
                	answer.add(new DiscoItem(role.getRoleAddress(), null, null, null));
                }
            }
        }
        return answer.iterator();
    }

    private boolean canDiscoverRoom(MUCRoom room) {
        // Check if locked rooms may be discovered
        if (!allowToDiscoverLockedRooms && room.isLocked()) {
            return false;
        }
        return room.isPublicRoom();
    }

    /**
     * Converts an array to a comma-delimitted String.
     *
     * @param array the array.
     * @return a comma delimtted String of the array values.
     */
    private static String fromArray(String [] array) {
        StringBuilder buf = new StringBuilder();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(",");
            }
        }
        return buf.toString();
    }
    
    public boolean isHidden() {
        return isHidden;
    }

}