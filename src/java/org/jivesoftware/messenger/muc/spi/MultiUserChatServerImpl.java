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

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.DiscoInfoProvider;
import org.jivesoftware.messenger.disco.DiscoItemsProvider;
import org.jivesoftware.messenger.disco.DiscoServerItem;
import org.jivesoftware.messenger.disco.ServerItemsProvider;
import org.jivesoftware.messenger.forms.DataForm;
import org.jivesoftware.messenger.forms.FormField;
import org.jivesoftware.messenger.forms.XDataForm;
import org.jivesoftware.messenger.forms.spi.XDataFormImpl;
import org.jivesoftware.messenger.forms.spi.XFormFieldImpl;
import org.jivesoftware.messenger.muc.*;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.handler.IQRegisterHandler;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.*;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;

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
    private static int USER_TIMEOUT = 300000;
    /**
     * The time to elapse between logging the room conversations.
     */
    private static int LOG_TIMEOUT = 300000;
    /**
     * The number of messages to log on each run of the logging process.
     */
    private static int LOG_BATCH_SIZE = 50;
    /**
     * the chat server's hostname
     */
    private String chatServerName = null;
    private XMPPAddress chatServerAddress = null;

    /**
     * chatrooms managed by this manager, table: key room name (String); value ChatRoom
     */
    private Map<String,MUCRoom> rooms = Collections.synchronizedMap(new HashMap<String,MUCRoom>());

    /**
     * Cache for the persistent room surrogates. There will be a persistent room surrogate for each
     * persistent room that has not been loaded into memory. Whenever a persistent room is loaded or
     * unloaded from memory the cache is updated.
     */
    private Cache persistentRoomSurrogateCache;

    /**
     * chat users managed by this manager, table: key user jid (XMPPAddress); value ChatUser
     */
    private Map users = Collections.synchronizedMap(new HashMap());
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
     * The handler of packets with namespace jabber:iq:register for the server.
     */
    public IQRegisterHandler registerHandler = null;
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
     * Bare jids of users that are allowed to create MUC rooms. An empty list means that anyone can 
     * create a room. 
     */
    private List allowedToCreate = new LinkedList();

    /**
     * Bare jids of users that are system administrators of the MUC service. A sysadmin has the same
     * permissions as a room owner. 
     */
    private List sysadmins = new LinkedList();

    /**
     * Queue that holds the messages to log for the rooms that need to log their conversations.
     */
    private Queue logQueue = new LinkedBlockingQueue();

    /**
     * Create a new group chat server.
     */
    public MultiUserChatServerImpl() {
        super("Basic multi user chat server");
        historyStrategy = new HistoryStrategy(null);
    }

    private void initializeCaches() {
        // create cache - no size limit and expires after one hour of being idle
        persistentRoomSurrogateCache = new Cache("Room Surrogates", -1, JiveConstants.HOUR);
    }

    /**
     * Probes the presence of any user who's last packet was sent more than 5 minute ago.
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
                MUCUser user = (MUCUser) userIter.next();
                if (user.getLastPacketTime() < deadline) {
                    // TODO If the idea is to remove the user...this code does not do that. Maybe we
                    // can just simulate that the user sent an unavailable presence to each room 
                    // that he/she previously joined
                    // TODO Analyze the side effect caused by PresenceManager.probePresence
                    presenceManager.probePresence(chatServerAddress, user.getAddress());
                }
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Logs the conversation of the rooms that have this feature enabled.
     */
    private class LogConversationTask extends TimerTask {
        public void run() {
            synchronized (users) {
                try {
                    logConversation();
                }
                catch (Throwable e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                }
            }
        }
    }

    private void logConversation() {
        ConversationLogEntry entry = null;
        boolean success = false;
        for (int index = 0; index <= LOG_BATCH_SIZE && !logQueue.isEmpty(); index++) {
            entry = (ConversationLogEntry)logQueue.poll();
            if (entry != null) {
                success = MUCPersistenceManager.saveConversationLogEntry(entry);
                if (!success) {
                    logQueue.add(entry);
                }
            }
        }
    }

    public MUCRoom getChatRoom(String roomName, XMPPAddress userjid) throws UnauthorizedException {
        MUCRoom room = null;
        synchronized (rooms) {
            room = rooms.get(roomName.toLowerCase());
            if (room == null) {
                room = new MUCRoomImpl(this, roomName, router);
                // Check whether the room was just created or loaded from the database 
                if (!room.isPersistent()) {
                    // Room creation is always allowed for sysadmin
                    if (allowedToCreate.size() > 0 &&
                            !sysadmins.contains(userjid.toBareStringPrep())) {
                        // The room creation is only allowed for certain JIDs
                        if (!allowedToCreate.contains(userjid.toBareStringPrep())) {
                            // The user is not in the list of allowed JIDs to create a room so raise
                            // an exception
                            throw new UnauthorizedException();
                        }
                    }
                    room.addFirstOwner(userjid.toBareStringPrep());
                }
                else {
                    // The room was loaded from the database and is now available in memory.
                    // Update in the database that the room is now available in memory
                    MUCPersistenceManager.updateRoomInMemory(room, true);
                    // Remove the surrogate of the room (if any) from the surrogates cache
                    persistentRoomSurrogateCache.remove(room.getName());
                }
                rooms.put(roomName.toLowerCase(), room);
            }
        }
        return room;
    }

    public MUCRoom getChatRoom(String roomName) {
        synchronized (rooms) {
            MUCRoom answer = rooms.get(roomName.toLowerCase());
            // If the room is not in memory check if there exists a surrogate of the room. There
            // will be a surrogate if and only if exists an room in the database for the requested
            // room name
            if (answer == null) {
                if (persistentRoomSurrogateCache.size() == 0) {
                    populateRoomSurrogateCache();
                }
                answer = (MUCRoom) persistentRoomSurrogateCache.get(roomName);
            }
            return answer;
        }
    }

    private void populateRoomSurrogateCache() {
        for (MUCRoom room : MUCPersistenceManager.getRoomSurrogates(this, router)) {
            persistentRoomSurrogateCache.put(room.getName(), room);
        }
    }

    public boolean hasChatRoom(String roomName) {
        return getChatRoom(roomName) != null;
    }

    public void removeChatRoom(String roomName) throws UnauthorizedException {
        synchronized (rooms) {
            final MUCRoom room = rooms.get(roomName.toLowerCase());
            final long chatLength = room.getChatLength();
            totalChatTime += chatLength;
            // Update the database to indicate that the room is no longer in memory (only if the
            // room is persistent
            MUCPersistenceManager.updateRoomInMemory(room, false);
            // Clear the surrogates cache if the room thas is being removed from memory is
            // persistent
            if (room.isPersistent()) {
                persistentRoomSurrogateCache.clear();
            }
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
            MUCUser user = (MUCUser)users.remove(jabberID);
            if (user != null) {
                Iterator roles = user.getRoles();
                while (roles.hasNext()) {
                    MUCRole role = (MUCRole)roles.next();
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

    public MUCUser getChatUser(XMPPAddress userjid) throws UnauthorizedException,
            UserNotFoundException {
        if (router == null) {
            throw new IllegalStateException("Not initialized");
        }
        MUCUser user = null;
        synchronized (users) {
            user = (MUCUser) users.get(userjid);
            if (user == null) {
                user = new MUCUserImpl(this, router, userjid);
                users.put(userjid, user);
            }
        }
        return user;
    }

    public void serverBroadcast(String msg) throws UnauthorizedException {
        synchronized (rooms) {
            Iterator itr = rooms.values().iterator();
            while (itr.hasNext()) {
                ((MUCRoom)itr.next()).serverBroadcast(msg);
            }
        }
    }

    /**
     * Initialize the track info for the server.
     *
     * @return the track information for this server.
     */
    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(PacketRouter.class, "router");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(PresenceManager.class, "presenceManager");
        // TODO Remove the tracking for IQRegisterHandler when the component JEP gets implemented.
        trackInfo.getTrackerClasses().put(IQRegisterHandler.class, "registerHandler");
        return trackInfo;
    }

    public void serviceAdded(Object service) {
        if (service instanceof RoutingTable) {
            ((RoutingTable)service).addRoute(chatServerAddress, this);
            ArrayList params = new ArrayList();
            params.clear();
            params.add(chatServerName);
            Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", params));
        }
        else if (service instanceof IQRegisterHandler) {
            ((IQRegisterHandler) service).addDelegate(
                    getChatServerName(),
                    new IQMUCRegisterHandler(this));
        }
    }

    /**
     * Set the address of the server.
     *
     * @param name the new server address.
     */
    public void setChatServerName(String name) {
        JiveGlobals.setProperty("xmpp.muc.domain", name);
    }

    public List getUsersAllowedToCreate() {
        return allowedToCreate;
    }

    public List getSysadmins() {
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

    public void initialize(Container container) {
        chatServerName = JiveGlobals.getProperty("xmpp.muc.domain");
        // Trigger the strategy to load itself from the context
        historyStrategy.setContext("xmpp.muc.history");
        // Load the list of JIDs that are sysadmins of the MUC service
        String property = JiveGlobals.getProperty("xmpp.muc.sysadmin.jid");
        String[] jids = (property != null ? property.split(",") : new String[]{""});
        for (int i = 0; i < jids.length; i++) {
            sysadmins.add(jids[i].trim().toLowerCase());
        }
        // Load the list of JIDs that are allowed to create a MUC room
        property = JiveGlobals.getProperty("xmpp.muc.create.jid");
        jids = (property != null ? property.split(",") : new String[]{""});
        for (int i = 0; i < jids.length; i++) {
            allowedToCreate.add(jids[i].trim().toLowerCase());
        }
        String value = JiveGlobals.getProperty("xmpp.muc.tasks.user.timeout");
        if (value != null) {
            try {
                USER_TIMEOUT = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.user.timeout", e);
            }
        }
        value = JiveGlobals.getProperty("xmpp.muc.tasks.log.timeout");
        if (value != null) {
            try {
                LOG_TIMEOUT = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.log.timeout", e);
            }
        }
        value = JiveGlobals.getProperty("xmpp.muc.tasks.log.batchsize");
        if (value != null) {
            try {
                LOG_BATCH_SIZE = Integer.parseInt(value);
            }
            catch (NumberFormatException e) {
                Log.error("Wrong number format of property xmpp.muc.tasks.log.batchsize", e);
            }
        }
        if (chatServerName == null) {
            chatServerName = "conference.127.0.0.1";
        }
        chatServerAddress = new XMPPAddress(null, chatServerName, null);
        // Run through the users every 5 minutes after a 5 minutes server startup delay (default
        // values)
        timer.schedule(new UserTimeoutTask(), USER_TIMEOUT, USER_TIMEOUT);
        // Log the room conversations every 5 minutes after a 5 minutes server startup delay
        // (default values)
        timer.schedule(new LogConversationTask(), LOG_TIMEOUT, LOG_TIMEOUT);
        // Update the DB to indicate that no room is in-memory. This may be necessary when the 
        // server went down unexpectedly
        MUCPersistenceManager.resetRoomInMemory();
        initializeCaches();
        super.initialize(container);
    }

    public void start() {
        super.start();
        routingTable = (RoutingTable)lookup.lookup(RoutingTable.class);
        routingTable.addRoute(chatServerAddress, this);
        ArrayList params = new ArrayList();
        params.clear();
        params.add(chatServerName);
        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", params));
    }

    public void stop() {
        super.stop();
        timer.cancel();
        if (registerHandler != null) {
            registerHandler.removeDelegate(getChatServerName());
        }
    }

    public XMPPAddress getAddress() {
        if (chatServerAddress == null) {
            throw new IllegalStateException("Not initialized");
        }
        return chatServerAddress;
    }

    public void process(XMPPPacket packet) {
        try {
            MUCUser user = getChatUser(packet.getSender());
            user.process(packet);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public long getTotalChatTime() {
        return totalChatTime;
    }

    public void roomRenamed(String oldName, String newName) {
        MUCRoom room = null;
        synchronized (rooms) {
            room = rooms.get(oldName);
            rooms.put(newName, room);
            rooms.remove(oldName);
        }
    }

    public void logConversation(MUCRoom room, Message message, XMPPAddress sender) {
        logQueue.add(new ConversationLogEntry(new Date(), room, message, sender));
    }

    public Iterator getItems() {
        ArrayList items = new ArrayList();

        items.add(new DiscoServerItem() {
            public String getJID() {
                return chatServerName;
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

    public Iterator getIdentities(String name, String node, XMPPAddress senderJID) {
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
                identity.addAttribute("name", room.getName());
                identity.addAttribute("type", "text");

                identities.add(identity);
            }
        }
        else if (name != null && "x-roomuser-item".equals(node)) {
            // Answer reserved nickname for the sender of the disco request in the requested room
            MUCRoom room = getChatRoom(name);
            if (room != null) {
                String reservedNick = room.getReservedNickname(senderJID.toBareStringPrep());
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

    public Iterator getFeatures(String name, String node, XMPPAddress senderJID) {
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

    public XDataForm getExtendedInfo(String name, String node, XMPPAddress senderJID) {
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

    public boolean hasInfo(String name, String node, XMPPAddress senderJID)
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

    public Iterator getItems(String name, String node, XMPPAddress senderJID)
            throws UnauthorizedException {
        List answer = new ArrayList();
        if (name == null && node == null) {
            // Answer all the public rooms as items
            synchronized (rooms) {
                Iterator itr = rooms.values().iterator();
                MUCRoom room;
                Element item;
                while (itr.hasNext()) {
                    room = (MUCRoom)itr.next();
                    if (room.isPublicRoom()) {
                        item = DocumentHelper.createElement("item");
                        item.addAttribute("jid", room.getRole().getRoleAddress().toStringPrep());
                        item.addAttribute("name", room.getName());

                        answer.add(item);
                    }
                }
                // Load the room surrogates for persistent rooms that aren't in memory (if the cache
                // is still empty)
                if (persistentRoomSurrogateCache.size() == 0) {
                    populateRoomSurrogateCache();
                }
                // Add items for each room surrogate (persistent room that is not in memory at
                // the moment)
                for (Iterator it=persistentRoomSurrogateCache.values().iterator(); it.hasNext();) {
                    room = (MUCRoom)it.next();
                    item = DocumentHelper.createElement("item");
                    item.addAttribute("jid", room.getRole().getRoleAddress().toStringPrep());
                    item.addAttribute("name", room.getName());

                    answer.add(item);
                }
            }
        }
        else if (name != null && node == null) {
            // Answer the room occupants as items if that info is publicly available
            MUCRoom room = getChatRoom(name);
            if (room != null && room.isPublicRoom()) {
                MUCRole role;
                Element item;
                for (Iterator members = room.getOccupants(); members.hasNext();) {
                    role = (MUCRole)members.next();
                    item = DocumentHelper.createElement("item");
                    item.addAttribute("jid", role.getRoleAddress().toStringPrep());

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