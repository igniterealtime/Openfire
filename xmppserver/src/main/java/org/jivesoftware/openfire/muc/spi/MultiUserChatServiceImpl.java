/*
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

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerListener;
import org.jivesoftware.openfire.archive.Archiver;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.disco.DiscoInfoProvider;
import org.jivesoftware.openfire.disco.DiscoItem;
import org.jivesoftware.openfire.disco.DiscoItemsProvider;
import org.jivesoftware.openfire.disco.DiscoServerItem;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.ServerItemsProvider;
import org.jivesoftware.openfire.group.ConcurrentGroupList;
import org.jivesoftware.openfire.group.GroupAwareList;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.handler.IQHandler;
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
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.AutoCloseableReentrantLock;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.JiveProperties;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.DataForm.Type;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;
import org.xmpp.resultsetmanagement.ResultSet;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implements the chat server as a cached memory resident chat server. The server is also
 * responsible for responding Multi-User Chat disco requests as well as removing inactive users from
 * the rooms after a period of time and to maintain a log of the conversation in the rooms that
 * require to log their conversations. The conversations log is saved to the database using a
 * separate process.
 * <p>
 * Temporary rooms are held in memory as long as they have occupants. They will be destroyed after
 * the last occupant left the room. On the other hand, persistent rooms are always present in memory
 * even after the last occupant left the room. In order to keep memory clean of persistent rooms that
 * have been forgotten or abandoned this class includes a clean up process. The clean up process
 * will remove from memory rooms that haven't had occupants for a while. Moreover, forgotten or
 * abandoned rooms won't be loaded into memory when the Multi-User Chat service starts up.</p>
 *
 * @author Gaston Dombiak
 */
public class MultiUserChatServiceImpl implements Component, MultiUserChatService,
        ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider, XMPPServerListener
{
    private static final Logger Log = LoggerFactory.getLogger(MultiUserChatServiceImpl.class);

    private static final Interner<String> roomBaseMutex = Interners.newWeakInterner();
    private static final Interner<JID> jidBaseMutex = Interners.newWeakInterner();

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
     * The maximum amount of logs to be written to the database in one iteration.
     */
    private int logMaxConversationBatchSize;

    /**
     * The maximum time between database writes of log batches.
     */
    private Duration logMaxBatchInterval;

    /**
     * Logs are written to the database almost instantly, but are batched together
     * when a new log entry becomes available within the amount of time defined
     * in this field - unless the total amount of time since the last write
     * is larger then #maxbatchinterval.
     */
    private Duration logBatchGracePeriod;

    /**
     * the chat service's hostname (subdomain)
     */
    private final String chatServiceName;
    /**
     * the chat service's description
     */
    private String chatDescription;

    /**
     * LocalMUCRoom chat manager which supports simple chatroom management
     */
    private final LocalMUCRoomManager localMUCRoomManager = new LocalMUCRoomManager();

    /**
     * Chat users managed by this manager. This includes only users connected to this JVM.
     * That means that when running inside of a cluster each node will have its own manager
     * that in turn will keep its own list of locally connected.
     *
     * table: key user jid (XMPPAddress); value ChatUser
     */
    private final Map<JID, LocalMUCUser> users = new ConcurrentHashMap<>();
    private final HistoryStrategy historyStrategy;

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
     * The handler of search requests ('https://xmlns.zombofant.net/muclumbus/search/1.0' namespace).
     */
    private IQMuclumbusSearchHandler muclumbusSearchHandler = null;

    /**
     * The handler of VCard requests.
     */
    private IQMUCvCardHandler mucVCardHandler = null;

    /**
     * Plugin (etc) provided IQ Handlers for MUC:
     */
    private Map<String,IQHandler> iqHandlers = null;

    /**
     * The total time all agents took to chat *
     */
    private long totalChatTime;

    /**
     * Flag that indicates if the service should provide information about locked rooms when
     * handling service discovery requests.
     * Note: Setting this flag in false is not compliant with the spec. A user may try to join a
     * locked room thinking that the room doesn't exist because the user didn't discover it before.
     */
    private boolean allowToDiscoverLockedRooms = true;

    /**
     * Flag that indicates if the service should provide information about non-public members-only
     * rooms when handling service discovery requests.
     */
    private boolean allowToDiscoverMembersOnlyRooms = false;

    /**
     * Returns the permission policy for creating rooms. A true value means that not anyone can
     * create a room. Users are allowed to create rooms only when
     * <code>isAllRegisteredUsersAllowedToCreate</code> or <code>getUsersAllowedToCreate</code>
     * (or both) allow them to.
     */
    private boolean roomCreationRestricted = false;

    /**
     * Determines if all registered users (as opposed to anonymous users, and users from other
     * XMPP domains) are allowed to create rooms.
     */
    private boolean allRegisteredUsersAllowedToCreate = false;

    /**
     * Bare jids of users that are allowed to create MUC rooms. Might also include group jids.
     */
    private GroupAwareList<JID> allowedToCreate = new ConcurrentGroupList<>();

    /**
     * Bare jids of users that are system administrators of the MUC service. A sysadmin has the same
     * permissions as a room owner. Might also contain group jids.
     */
    private GroupAwareList<JID> sysadmins = new ConcurrentGroupList<>();

    /**
     * Queue that holds the messages to log for the rooms that need to log their conversations.
     */
    private volatile Archiver<ConversationLogEntry> archiver;

    /**
     * Max number of hours that a persistent room may be empty before the service removes the
     * room from memory. Unloaded rooms will exist in the database and may be loaded by a user
     * request. Default time limit is: 30 days.
     */
    private long emptyLimit = 30 * 24;

    /**
     * The time to elapse between each rooms cleanup. Default frequency is 60 minutes.
     */
    private static final long CLEANUP_FREQUENCY = 60;

    /**
     * Total number of received messages in all rooms since the last reset. The counter
     * is reset each time the Statistic makes a sampling.
     */
    private final AtomicInteger inMessages = new AtomicInteger(0);
    /**
     * Total number of broadcasted messages in all rooms since the last reset. The counter
     * is reset each time the Statistic makes a sampling.
     */
    private final AtomicLong outMessages = new AtomicLong(0);

    /**
     * Flag that indicates if MUC service is enabled.
     */
    private boolean serviceEnabled = true;

    /**
     * Flag that indicates if MUC service is hidden from services views.
     */
    private final boolean isHidden;

    /**
     * Delegate responds to events for the MUC service.
     */
    private MUCEventDelegate mucEventDelegate;

    /**
     * Additional features to be added to the disco response for the service.
     */
    private final List<String> extraDiscoFeatures = new ArrayList<>();

    /**
     * Additional identities to be added to the disco response for the service.
     */
    private final List<Element> extraDiscoIdentities = new ArrayList<>();

    /**
     * Create a new group chat server.
     *
     * @param subdomain
     *            Subdomain portion of the conference services (for example,
     *            conference for conference.example.org)
     * @param description
     *            Short description of service for disco and such. If
     *            {@code null} or empty, a default value will be used.
     * @param isHidden
     *            True if this service should be hidden from services views.
     * @throws IllegalArgumentException
     *             if the provided subdomain is an invalid, according to the JID
     *             domain definition.
     */
    public MultiUserChatServiceImpl(final String subdomain, final String description, final Boolean isHidden) {
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

    @Override
    public void addIQHandler(final IQHandler iqHandler) {
        if (this.iqHandlers == null) {
            this.iqHandlers = new HashMap<>();
        }
        this.iqHandlers.put(iqHandler.getInfo().getNamespace(), iqHandler);
    }

    @Override
    public void removeIQHandler(final IQHandler iqHandler) {
        if (this.iqHandlers != null) {
            if (iqHandler == this.iqHandlers.get(iqHandler.getInfo().getNamespace())) {
                this.iqHandlers.remove(iqHandler.getInfo().getNamespace());
            }
        }
    }

    @Override
    public String getDescription() {
        return chatDescription;
    }

    public void setDescription(final String desc) {
        this.chatDescription = desc;
    }

    @Override
    public void processPacket(final Packet packet) {

        Log.trace( "Routing stanza: {}", packet.toXML() );
        if (!isServiceEnabled()) {
            Log.trace( "Service is disabled. Ignoring stanza." );
            return;
        }
        // The MUC service will receive all the packets whose domain matches the domain of the MUC
        // service. This means that, for instance, a disco request should be responded by the
        // service itself instead of relying on the server to handle the request.
        try {
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (process((IQ)packet)) {
                    Log.trace( "Done processing IQ stanza." );
                    return;
                }
            } else if (packet instanceof Message) {
                final Message msg = (Message) packet;
                if (msg.getType() == Message.Type.error) {
                    // Bounced message, drop user.
                    removeUser(packet.getFrom());
                    Log.trace( "Done processing Message stanza." );
                    return;
                }
            } else if (packet instanceof Presence) {
                final Presence pres = (Presence) packet;
                if (pres.getType() == Presence.Type.error) {
                    // Bounced presence, drop user.
                    removeUser(packet.getFrom());
                    Log.trace( "Done processing Presence stanza." );
                    return;
                }
            }

            if ( packet.getTo().getNode() == null )
            {
                Log.trace( "Stanza was addressed at the service itself, which by now should have been handled." );
                if ( packet instanceof IQ && ((IQ) packet).isRequest() )
                {
                    final IQ reply = IQ.createResultIQ( (IQ) packet );
                    reply.setChildElement( ((IQ) packet).getChildElement().createCopy() );
                    reply.setError( PacketError.Condition.feature_not_implemented );
                    router.route( reply );
                }
                Log.debug( "Ignoring stanza addressed at conference service: {}", packet.toXML() );
            }
            else
            {
                Log.trace( "The stanza is a normal packet that should possibly be sent to the room." );
                final JID recipient = packet.getTo();
                final String roomName = recipient != null ? recipient.getNode() : null;
                final JID userJid = packet.getFrom();
                Log.trace( "Stanza recipient: {}, room name: {}, sender: {}", recipient, roomName, userJid );
                try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(MultiUserChatServiceImpl.class, userJid.toString()).lock()) {
                    if ( !packet.getElement().elements(FMUCHandler.FMUC).isEmpty() ) {
                        Log.trace( "Stanza is a FMUC stanza." );
                        final MUCRoom chatRoom = getChatRoom(roomName);
                        if ( chatRoom != null ) {
                            chatRoom.getFmucHandler().process(packet);
                        } else {
                            Log.warn( "Unable to process FMUC stanza, as room it's addressed to does not exist: {}", roomName );
                            // FIXME need to send error back in case of IQ request, and FMUC join. Might want to send error back in other cases too.
                        }
                    } else {
                        Log.trace( "Stanza is a regular MUC stanza." );
                        getChatUser(userJid, roomName).process(packet);
                    }
                }
            }
        }
        catch (final Exception e) {
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
    private boolean process(final IQ iq) {
        final Element childElement = iq.getChildElement();
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
            final IQ reply = registerHandler.handleIQ(iq);
            router.route(reply);
        }
        else if ("jabber:iq:search".equals(namespace)) {
            final IQ reply = searchHandler.handleIQ(iq);
            router.route(reply);
        }
        else if (IQMuclumbusSearchHandler.NAMESPACE.equals(namespace)) {
            final IQ reply = muclumbusSearchHandler.handleIQ(iq);
            router.route(reply);
        }
        else if (IQMUCvCardHandler.NAMESPACE.equals(namespace)) {
            final IQ reply = mucVCardHandler.handleIQ(iq);
            router.route(reply);
        }
        else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            // TODO MUC should have an IQDiscoInfoHandler of its own when MUC becomes
            // a component
            final IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
            router.route(reply);
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            // TODO MUC should have an IQDiscoItemsHandler of its own when MUC becomes
            // a component
            final IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
            router.route(reply);
        }
        else if ("urn:xmpp:ping".equals(namespace)) {
            router.route( IQ.createResultIQ(iq) );
        }
        else if (this.iqHandlers != null) {
            final IQHandler h = this.iqHandlers.get(namespace);
            if (h != null) {
                try {
                    final IQ reply = h.handleIQ(iq);
                    if (reply != null) {
                        router.route(reply);
                    }
                } catch (final UnauthorizedException e) {
                    final IQ reply = IQ.createResultIQ(iq);
                    reply.setType(IQ.Type.error);
                    reply.setError(PacketError.Condition.service_unavailable);
                    router.route(reply);
                }
                return true;
            }
            return false;
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void initialize(final JID jid, final ComponentManager componentManager) {
        initialize(XMPPServer.getInstance());

    }

    @Override
    public void shutdown() {
        enableService( false, false );
    }

    @Override
    public String getServiceDomain() {
        return chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null, true);
    }

    @Override
    public void serverStarted()
    {}

    @Override
    public void serverStopping()
    {
        // When this is executed, we can be certain that all server modules have not yet shut down. This allows us to
        // inform all users.
        shutdown();
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

    /**
     * Informs all users local to this cluster node that he or she is being removed from the room because the MUC
     * service is being shut down.
     *
     * The implementation is optimized to run as fast as possible (to prevent prolonging the shutdown).
     */
    private void broadcastShutdown()
    {
        Log.debug( "Notifying all local users about the imminent destruction of chat service '{}'", chatServiceName );

        if (users.isEmpty()) {
            return;
        }

        // A thread pool is used to broadcast concurrently, as well as to limit the execution time of this service.
        final ExecutorService service = Executors.newFixedThreadPool( Math.min( users.size(), 10 ) );

        // Queue all tasks in the executor service.
        for ( final LocalMUCUser user : users.values() )
        {
            // Submit a concurrent task for each local user (that could be in more than one (local) room).
            service.submit(() -> {
                try
                {
                    for ( final LocalMUCRole role : user.getRoles() )
                    {
                        final MUCRoom room = role.getChatRoom();

                        // Send a presence stanza of type "unavailable" to the occupant
                        final Presence presence = room.createPresence( Presence.Type.unavailable );
                        presence.setFrom( role.getRoleAddress() );

                        // A fragment containing the x-extension.
                        final Element fragment = presence.addChildElement( "x", "http://jabber.org/protocol/muc#user" );
                        final Element item = fragment.addElement( "item" );
                        item.addAttribute( "affiliation", "none" );
                        item.addAttribute( "role", "none" );
                        fragment.addElement( "status" ).addAttribute( "code", "332" );

                        // Make sure that the presence change for each user is only sent to that user (and not broadcasted in the room)!
                        role.send( presence );
                    }
                }
                catch ( final Exception e )
                {
                    Log.debug( "Unable to inform {} about the imminent destruction of chat service '{}'", user.getAddress(), chatServiceName, e );
                }
            });
        }

        // Try to shutdown - wait - force shutdown.
        service.shutdown();
        try
        {
            service.awaitTermination( JiveGlobals.getIntProperty( "xmpp.muc.await-termination-millis", 500 ), TimeUnit.MILLISECONDS );
            Log.debug( "Successfully notified all {} local users about the imminent destruction of chat service '{}'", users.size(), chatServiceName );
        }
        catch ( final InterruptedException e )
        {
            Log.debug( "Interrupted while waiting for all users to be notified of shutdown of chat service '{}'. Shutting down immediately.", chatServiceName );
        }
        service.shutdownNow();
    }

    private void checkForTimedOutUsers() {
        final long deadline = System.currentTimeMillis() - user_idle;
        for (final LocalMUCUser user : users.values()) {
            try (final AutoCloseableReentrantLock.AutoCloseableLock ignored = new AutoCloseableReentrantLock(MultiUserChatServiceImpl.class, user.getAddress().toString()).lock()) {
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
                    String timeoutKickReason = JiveGlobals.getProperty("admin.mucRoom.timeoutKickReason",
                            "User exceeded idle time limit.");
                    // Kick the user from all the rooms that he/she had previuosly joined
                    MUCRoom room;
                    Presence kickedPresence;
                    for (final LocalMUCRole role : user.getRoles()) {
                        room = role.getChatRoom();
                        try {
                            kickedPresence =
                                    room.kickOccupant(user.getAddress(), null, null, timeoutKickReason);
                            // Send the updated presence to the room occupants
                            room.send(kickedPresence, room.getRole());
                        }
                        catch (final NotAllowedException e) {
                            // Do nothing since we cannot kick owners or admins
                        }
                    }
                }
            }
            catch (final Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Stores Conversations in the database.
     */
    private static class ConversationLogEntryArchiver extends Archiver<ConversationLogEntry>
    {
        ConversationLogEntryArchiver( String id, int maxWorkQueueSize, Duration maxPurgeInterval, Duration gracePeriod )
        {
            super( id, maxWorkQueueSize, maxPurgeInterval, gracePeriod );
        }

        @Override
        protected void store( List<ConversationLogEntry> batch )
        {
            if ( batch.isEmpty() )
            {
                return;
            }

            MUCPersistenceManager.saveConversationLogBatch( batch );
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
                Date cleanUpDate = getCleanupDate();
                if (cleanUpDate!=null)
                {
                    Iterator<LocalMUCRoom> it = localMUCRoomManager.getRooms().iterator();
                    while (it.hasNext()) {
                        LocalMUCRoom room = it.next();
                        Date emptyDate = room.getEmptyDate();
                        if (emptyDate != null && emptyDate.before(cleanUpDate)) {
                            removeChatRoom(room.getName());
                        }
                    }
                }
            }
            catch (final Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Checks if a particular JID is allowed to create rooms.
     *
     * @param jid The jid for which to check (cannot be null).
     * @return true if the JID is allowed to create a room, otherwise false.
     */
    private boolean isAllowedToCreate(final JID jid) {
        // If room creation is not restricted, everyone is allowed to create a room.
        if (!isRoomCreationRestricted()) {
            return true;
        }

        final JID bareJID = jid.asBareJID();

        // System administrators are always allowed to create rooms.
        if (sysadmins.includes(bareJID)) {
            return true;
        }

        // If the JID of the user has explicitly been given permission, room creation is allowed.
        if (allowedToCreate.includes(bareJID)) {
            return true;
        }

        // Verify the policy that allows all local, registered users to create rooms.
        return allRegisteredUsersAllowedToCreate && UserManager.getInstance().isRegisteredUser(bareJID, false);
    }

    @Override
    public MUCRoom getChatRoom(final String roomName, final JID userjid) throws NotAllowedException {
        LocalMUCRoom room;
        boolean loaded = false;
        boolean created = false;
        synchronized (roomBaseMutex.intern(roomName)) {
            room = localMUCRoomManager.getRoom(roomName);
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
                catch (final IllegalArgumentException e) {
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
                        if (!isAllowedToCreate(userjid)) {
                            throw new NotAllowedException();
                        }
                        room.addFirstOwner(userjid);
                        created = true;
                    }
                }
                localMUCRoomManager.addRoom(roomName, room);
            }
        }
        if (created) {
            // Fire event that a new room has been created
            MUCEventDispatcher.roomCreated(room.getRole().getRoleAddress());
        }
        if (loaded || created) {
            // Initiate FMUC, when enabled.
            room.getFmucHandler().applyConfigurationChanges();

            // Notify other cluster nodes that a new room is available
            CacheFactory.doClusterTask(new RoomAvailableEvent(room));
            for (final MUCRole role : room.getOccupants()) {
                if (role instanceof LocalMUCRole) {
                    CacheFactory.doClusterTask(new OccupantAddedEvent(room, role));
                }
            }
        }
        return room;
    }

    @Override
    public MUCRoom getChatRoom(final String roomName) {
        boolean loaded = false;
        LocalMUCRoom room = localMUCRoomManager.getRoom(roomName);
        if (room == null) {
            // Check if the room exists in the databclase and was not present in memory
            synchronized (roomBaseMutex.intern(roomName)) {
                room = localMUCRoomManager.getRoom(roomName);
                if (room == null) {
                    room = new LocalMUCRoom(this, roomName, router);
                    // If the room is persistent load the configuration values from the DB
                    try {
                        // Try to load the room's configuration from the database (if the room is
                        // persistent but was added to the DB after the server was started up or the
                        // room may be an old room that was not present in memory)
                        MUCPersistenceManager.loadFromDB(room);
                        loaded = true;
                        localMUCRoomManager.addRoom(roomName,room);
                    }
                    catch (final IllegalArgumentException e) {
                        // The room does not exist so do nothing
                        room = null;
                        loaded = false;
                    }
                }
            }
        }
        if (loaded) {
            // Initiate FMUC, when enabled.
            room.getFmucHandler().applyConfigurationChanges();

            // Notify other cluster nodes that a new room is available
            CacheFactory.doClusterTask(new RoomAvailableEvent(room));
        }
        return room;
    }

    @Override
    public void refreshChatRoom(final String roomName) {
        localMUCRoomManager.removeRoom(roomName);
        getChatRoom(roomName);
    }

    public LocalMUCRoom getLocalChatRoom(final String roomName) {
        return localMUCRoomManager.getRoom(roomName);
    }

    @Override
    public List<MUCRoom> getChatRooms() {
        return new ArrayList<>(localMUCRoomManager.getRooms());
    }

    @Override
    public boolean hasChatRoom(final String roomName) {
        return getChatRoom(roomName) != null;
    }

    @Override
    public void removeChatRoom(final String roomName) {
        removeChatRoom(roomName, true);
    }

    /**
     * Notification message indicating that the specified chat room was
     * removed from some other cluster member.
     *
     * @param room the removed room in another cluster node.
     */
    @Override
    public void chatRoomRemoved(final LocalMUCRoom room) {
        removeChatRoom(room.getName(), false);
    }

    /**
     * Notification message indicating that a chat room has been created
     * in another cluster member.
     *
     * @param room the created room in another cluster node.
     */
    @Override
    public void chatRoomAdded(final LocalMUCRoom room) {
        localMUCRoomManager.addRoom(room.getName(), room) ;
    }

    private void removeChatRoom(final String roomName, final boolean notify) {
        final LocalMUCRoom room = localMUCRoomManager.removeRoom(roomName);
        if (room != null) {
            Log.info("removing chat room:" + roomName + "|" + room.getClass().getName());
            totalChatTime += room.getChatLength();
            if (notify) {
                // Notify other cluster nodes that a room has been removed
                CacheFactory.doClusterTask(new RoomRemovedEvent(room));
            }
        } else {
            Log.info("No chatroom {} during removal.", roomName);
        }
    }

    @Override
    public String getServiceName() {
        return chatServiceName;
    }

    @Override
    public String getName() {
        return getServiceName();
    }

    @Override
    public HistoryStrategy getHistoryStrategy() {
        return historyStrategy;
    }

    /**
     * Removes a user from all chat rooms.
     *
     * @param jabberID The user's normal jid, not the chat nickname jid.
     */
    private void removeUser(final JID jabberID) {
        final LocalMUCUser user = users.remove(jabberID);
        if (user != null) {
            for (final LocalMUCRole role : user.getRoles()) {
                try {
                    role.getChatRoom().leaveRoom(role);
                }
                catch (final Exception e) {
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
    private MUCUser getChatUser(final JID userjid, final String roomName) {
        if (router == null) {
            throw new IllegalStateException("Not initialized");
        }
        LocalMUCUser user;
        synchronized (jidBaseMutex.intern(userjid)) {
            user = users.get(userjid);
            if (user == null) {
                if (roomName != null) {
                    // Check if the JID belong to a user hosted in another cluster node
                    final LocalMUCRoom localMUCRoom = localMUCRoomManager.getRoom(roomName);
                    if (localMUCRoom != null) {
                        final MUCRole occupant = localMUCRoom.getOccupantByFullJID(userjid);
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

    @Override
    public Collection<MUCRole> getMUCRoles(final JID user) {
        final List<MUCRole> userRoles = new ArrayList<>();
        for (final LocalMUCRoom room : localMUCRoomManager.getRooms()) {
            final MUCRole role = room.getOccupantByFullJID(user);
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
        if (emptyLimit!=-1)
            return new Date(System.currentTimeMillis() - (emptyLimit * 3600000));
        else
            return null;
    }

    @Override
    public void setKickIdleUsersTimeout(final int timeout) {
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
        TaskEngine.getInstance().schedule(userTimeoutTask, user_timeout, user_timeout);
        // Set the new property value
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.timeout", Integer.toString(timeout));
    }

    @Override
    public int getKickIdleUsersTimeout() {
        return user_timeout;
    }

    @Override
    public void setUserIdleTime(final int idleTime) {
        if (this.user_idle == idleTime) {
            return;
        }
        this.user_idle = idleTime;
        // Set the new property value
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.idle", Integer.toString(idleTime));
    }

    @Override
    public int getUserIdleTime() {
        return user_idle;
    }

    @Override
    public Collection<JID> getUsersAllowedToCreate() {
        return Collections.unmodifiableCollection(allowedToCreate);
    }

    @Override
    public Collection<JID> getSysadmins() {
        return Collections.unmodifiableCollection(sysadmins);
    }

    @Override
    public boolean isSysadmin(final JID bareJID) {
        return sysadmins.includes(bareJID);
    }

    @Override
    public void addSysadmins(final Collection<JID> userJIDs) {
        for (final JID userJID : userJIDs) {
            addSysadmin(userJID);
        }
    }

    @Override
    public void addSysadmin(final JID userJID) {
        final JID bareJID = userJID.asBareJID();

        if (!sysadmins.contains(userJID)) {
            sysadmins.add(bareJID);
        }

        // CopyOnWriteArray does not allow sorting, so do sorting in temp list.
        final ArrayList<JID> tempList = new ArrayList<>(sysadmins);
        Collections.sort(tempList);
        sysadmins = new ConcurrentGroupList<>(tempList);

        // Update the config.
        final String[] jids = new String[sysadmins.size()];
        for (int i = 0; i < jids.length; i++) {
            jids[i] = sysadmins.get(i).toBareJID();
        }
        MUCPersistenceManager.setProperty(chatServiceName, "sysadmin.jid", fromArray(jids));
    }

    @Override
    public void removeSysadmin(final JID userJID) {
        final JID bareJID = userJID.asBareJID();

        sysadmins.remove(bareJID);

        // Update the config.
        final String[] jids = new String[sysadmins.size()];
        for (int i = 0; i < jids.length; i++) {
            jids[i] = sysadmins.get(i).toBareJID();
        }
        MUCPersistenceManager.setProperty(chatServiceName, "sysadmin.jid", fromArray(jids));
    }

    /**
     * Returns the flag that indicates if the service should provide information about non-public
     * members-only rooms when handling service discovery requests.
     *
     * @return true if the service should provide information about non-public members-only rooms.
     */
    public boolean isAllowToDiscoverMembersOnlyRooms() {
        return allowToDiscoverMembersOnlyRooms;
    }

    /**
     * Sets the flag that indicates if the service should provide information about non-public
     * members-only rooms when handling service discovery requests.
     *
     * @param allowToDiscoverMembersOnlyRooms
     *         if the service should provide information about
     *         non-public members-only rooms.
     */
    public void setAllowToDiscoverMembersOnlyRooms(final boolean allowToDiscoverMembersOnlyRooms) {
        this.allowToDiscoverMembersOnlyRooms = allowToDiscoverMembersOnlyRooms;
        MUCPersistenceManager.setProperty(chatServiceName, "discover.membersOnly",
                Boolean.toString(allowToDiscoverMembersOnlyRooms));
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
    public void setAllowToDiscoverLockedRooms(final boolean allowToDiscoverLockedRooms) {
        this.allowToDiscoverLockedRooms = allowToDiscoverLockedRooms;
        MUCPersistenceManager.setProperty(chatServiceName, "discover.locked",
                Boolean.toString(allowToDiscoverLockedRooms));
    }

    @Override
    public boolean isRoomCreationRestricted() {
        return roomCreationRestricted;
    }

    @Override
    public void setRoomCreationRestricted(final boolean roomCreationRestricted) {
        this.roomCreationRestricted = roomCreationRestricted;
        MUCPersistenceManager.setProperty(chatServiceName, "create.anyone", Boolean.toString(roomCreationRestricted));
    }

    @Override
    public boolean isAllRegisteredUsersAllowedToCreate() {
        return allRegisteredUsersAllowedToCreate;
    }

    @Override
    public void setAllRegisteredUsersAllowedToCreate( final boolean allow ) {
        this.allRegisteredUsersAllowedToCreate = allow;
        MUCPersistenceManager.setProperty(chatServiceName, "create.all-registered", Boolean.toString(allow));
    }

    @Override
    public void addUsersAllowedToCreate(final Collection<JID> userJIDs) {
        boolean listChanged = false;

        for(final JID userJID: userJIDs) {
            // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
            // variable there is no need to restart the service
            if (!allowedToCreate.contains(userJID)) {
                allowedToCreate.add(userJID);
                listChanged = true;
            }
        }

        // if nothing was added, there's nothing to update
        if(listChanged) {
            // CopyOnWriteArray does not allow sorting, so do sorting in temp list.
            final List<JID> tempList = new ArrayList<>(allowedToCreate);
            Collections.sort(tempList);
            allowedToCreate = new ConcurrentGroupList<>(tempList);
            // Update the config.
            MUCPersistenceManager.setProperty(chatServiceName, "create.jid", fromCollection(allowedToCreate));
        }
    }

    @Override
    public void addUserAllowedToCreate(final JID userJID) {
        final List<JID> asList = new ArrayList<>();
        asList.add(userJID);
        addUsersAllowedToCreate(asList);
    }

    @Override
    public void removeUsersAllowedToCreate(final Collection<JID> userJIDs) {
        boolean listChanged = false;

        for(final JID userJID: userJIDs) {
            // Update the list of allowed JIDs to create MUC rooms. Since we are updating the instance
            // variable there is no need to restart the service
            listChanged |= allowedToCreate.remove(userJID);
        }

        // if none of the JIDs were on the list, there's nothing to update
        if(listChanged) {
            MUCPersistenceManager.setProperty(chatServiceName, "create.jid", fromCollection(allowedToCreate));
        }
    }

    @Override
    public void removeUserAllowedToCreate(final JID userJID) {
        removeUsersAllowedToCreate(Collections.singleton(userJID));
    }

    public void initialize(final XMPPServer server) {
        initializeSettings();

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();
        // Configure the handler of iq:register packets
        registerHandler = new IQMUCRegisterHandler(this);
        // Configure the handlers of search requests
        searchHandler = new IQMUCSearchHandler(this);
        muclumbusSearchHandler = new IQMuclumbusSearchHandler(this);
        mucVCardHandler = new IQMUCvCardHandler(this);
    }

    public void initializeSettings() {
        serviceEnabled = JiveProperties.getInstance().getBooleanProperty("xmpp.muc.enabled", true);
        serviceEnabled = MUCPersistenceManager.getBooleanProperty(chatServiceName, "enabled", serviceEnabled);
        // Trigger the strategy to load itself from the context
        historyStrategy.setContext(chatServiceName, "history");
        // Load the list of JIDs that are sysadmins of the MUC service
        String property = MUCPersistenceManager.getProperty(chatServiceName, "sysadmin.jid");

        sysadmins.clear();
        if (property != null && property.trim().length() > 0) {
            final String[] jids = property.split(",");
            for (final String jid : jids) {
                if (jid == null || jid.trim().length() == 0) {
                    continue;
                }
                try {
                    // could be a group jid
                    sysadmins.add(GroupJID.fromString(jid.trim().toLowerCase()).asBareJID());
                } catch (final IllegalArgumentException e) {
                    Log.warn("The 'sysadmin.jid' property contains a value that is not a valid JID. It is ignored. Offending value: '" + jid + "'.", e);
                }
            }
        }
        allowToDiscoverLockedRooms =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "discover.locked", true);
        allowToDiscoverMembersOnlyRooms =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "discover.membersOnly", true);
        roomCreationRestricted =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "create.anyone", false);
        allRegisteredUsersAllowedToCreate =
                MUCPersistenceManager.getBooleanProperty(chatServiceName, "create.all-registered", false );
        // Load the list of JIDs that are allowed to create a MUC room
        property = MUCPersistenceManager.getProperty(chatServiceName, "create.jid");
        allowedToCreate.clear();
        if (property != null && property.trim().length() > 0) {
            final String[] jids = property.split(",");
            for (final String jid : jids) {
                if (jid == null || jid.trim().length() == 0) {
                    continue;
                }
                try {
                    // could be a group jid
                    allowedToCreate.add(GroupJID.fromString(jid.trim().toLowerCase()).asBareJID());
                } catch (final IllegalArgumentException e) {
                    Log.warn("The 'create.jid' property contains a value that is not a valid JID. It is ignored. Offending value: '" + jid + "'.", e);
                }
            }
        }
        String value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.timeout");
        user_timeout = 300000;
        if (value != null) {
            try {
                user_timeout = Integer.parseInt(value);
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.timeout for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.idle");
        user_idle = -1;
        if (value != null) {
            try {
                user_idle = Integer.parseInt(value);
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.idle for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.log.maxbatchsize");
        logMaxConversationBatchSize = 500;
        if (value != null) {
            try {
                logMaxConversationBatchSize = Integer.parseInt(value);
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.log.maxbatchsize for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.log.maxbatchinterval");
        logMaxBatchInterval = Duration.ofSeconds( 1 );
        if (value != null) {
            try {
                logMaxBatchInterval = Duration.ofMillis( Long.parseLong(value) );
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.log.maxbatchinterval for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.log.batchgrace");
        logBatchGracePeriod = Duration.ofMillis( 50 );
        if (value != null) {
            try {
                logBatchGracePeriod = Duration.ofMillis( Long.parseLong(value) );
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.log.batchgrace for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "unload.empty_days");
        emptyLimit = 30 * 24;
        if (value != null) {
            try {
            	if (Integer.parseInt(value)>0)
            		emptyLimit = Integer.parseInt(value) * (long)24;
            	else
            		emptyLimit = -1;
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property unload.empty_days for service "+chatServiceName, e);
            }
        }
    }

    /**
     * Property accessor temporarily retained for backward compatibility. The interface prescribes use of
     * {@link #setLogMaxConversationBatchSize(int)} - so please use that instead.
     * @param size the number of messages to save to the database on each run of the logging process.
     * @deprecated Use {@link #setLogMaxConversationBatchSize(int)} instead.
     */
    @Override
    @Deprecated
    public void setLogConversationBatchSize(int size)
    {
        setLogMaxConversationBatchSize(size);
    }

    /**
     * Property accessor temporarily retained for backward compatibility. The interface prescribes use of
     * {@link #getLogMaxConversationBatchSize()} - so please use that instead.
     * @return the number of messages to save to the database on each run of the logging process.
     * @deprecated Use {@link #getLogMaxConversationBatchSize()} instead.
     */
    @Override
    @Deprecated
    public int getLogConversationBatchSize()
    {
        return getLogMaxConversationBatchSize();
    }

    /**
     * Sets the maximum number of messages to save to the database on each run of the archiving process.
     * Even though the saving of queued conversations takes place in another thread it is not
     * recommended specifying a big number.
     *
     * @param size the maximum number of messages to save to the database on each run of the archiving process.
     */
    @Override
    public void setLogMaxConversationBatchSize(int size) {
        if ( this.logMaxConversationBatchSize == size ) {
            return;
        }
        this.logMaxConversationBatchSize = size;

        if (archiver != null) {
            archiver.setMaxWorkQueueSize(size);
        }
        MUCPersistenceManager.setProperty( chatServiceName, "tasks.log.maxbatchsize", Integer.toString( size));
    }

    /**
     * Returns the maximum number of messages to save to the database on each run of the archiving process.
     * @return the maximum number of messages to save to the database on each run of the archiving process.
     */
    @Override
    public int getLogMaxConversationBatchSize() {
        return logMaxConversationBatchSize;
    }

    /**
     * Sets the maximum time allowed to elapse between writing archive batches to the database.
     * @param interval the maximum time allowed to elapse between writing archive batches to the database.
     */
    @Override
    public void setLogMaxBatchInterval( Duration interval )
    {
        if ( this.logMaxBatchInterval.equals( interval ) ) {
            return;
        }
        this.logMaxBatchInterval = interval;

        if (archiver != null) {
            archiver.setMaxPurgeInterval(interval);
        }
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.log.maxbatchinterval", Long.toString( interval.toMillis() ) );
    }

    /**
     * Returns the maximum time allowed to elapse between writing archive entries to the database.
     * @return the maximum time allowed to elapse between writing archive entries to the database.
     */
    @Override
    public Duration getLogMaxBatchInterval()
    {
        return logMaxBatchInterval;
    }

    /**
     * Sets the maximum time to wait for a next incoming entry before writing the batch to the database.
     * @param interval the maximum time to wait for a next incoming entry before writing the batch to the database.
     */
    @Override
    public void setLogBatchGracePeriod( Duration interval )
    {
        if ( this.logBatchGracePeriod.equals( interval ) ) {
            return;
        }

        this.logBatchGracePeriod = interval;
        if (archiver != null) {
            archiver.setGracePeriod(interval);
        }
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.log.batchgrace", Long.toString( interval.toMillis() ) );
    }

    /**
     * Returns the maximum time to wait for a next incoming entry before writing the batch to the database.
     * @return the maximum time to wait for a next incoming entry before writing the batch to the database.
     */
    @Override
    public Duration getLogBatchGracePeriod()
    {
        return logBatchGracePeriod;
    }

    /**
     * Accessor uses the "double-check idiom" for proper lazy instantiation.
     * @return An Archiver instance, never null.
     */
    @Override
    public Archiver getArchiver() {
        Archiver result = this.archiver;
        if (result == null) {
            synchronized (this) {
                result = this.archiver;
                if (result == null) {
                    result = new ConversationLogEntryArchiver("MUC Service " + this.getAddress().toString(), logMaxConversationBatchSize, logMaxBatchInterval, logBatchGracePeriod);
                    XMPPServer.getInstance().getArchiveManager().add(result);
                    this.archiver = result;
                }
            }
        }

        return result;
    }

    @Override
    public void start() {
        XMPPServer.getInstance().addServerListener( this );

        // Run through the users every 5 minutes after a 5 minutes server startup delay (default values)
        userTimeoutTask = new UserTimeoutTask();
        TaskEngine.getInstance().schedule(userTimeoutTask, user_timeout, user_timeout);

        // Remove unused rooms from memory
        long cleanupFreq = JiveGlobals.getLongProperty("xmpp.muc.cleanupFrequency.inMinutes", CLEANUP_FREQUENCY) * 60 * 1000;
        TaskEngine.getInstance().schedule(new CleanupTask(), cleanupFreq, cleanupFreq);

        // Set us up to answer disco item requests
        XMPPServer.getInstance().getIQDiscoItemsHandler().addServerItemsProvider(this);
        XMPPServer.getInstance().getIQDiscoInfoHandler().setServerNodeInfoProvider(this.getServiceDomain(), this);

        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", Collections.singletonList(getServiceDomain())));

        // Load all the persistent rooms to memory
        for (final LocalMUCRoom room : MUCPersistenceManager.loadRoomsFromDB(this, this.getCleanupDate(), router)) {
            localMUCRoomManager.addRoom(room.getName().toLowerCase(),room);

            // Start FMUC, if desired.
            room.getFmucHandler().applyConfigurationChanges();
        }
    }

    private void stop() {
        XMPPServer.getInstance().getIQDiscoItemsHandler().removeServerItemsProvider(this);
        XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerNodeInfoProvider(this.getServiceDomain());
        // Remove the route to this service
        routingTable.removeComponentRoute(getAddress());
        broadcastShutdown();
        XMPPServer.getInstance().removeServerListener( this );
        if (archiver != null) {
            XMPPServer.getInstance().getArchiveManager().remove(archiver);
        }
    }

    @Override
    public void enableService(final boolean enabled, final boolean persistent) {
        if (isServiceEnabled() == enabled) {
            // Do nothing if the service status has not changed
            return;
        }
        if (!enabled) {
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
        }
    }

    @Override
    public boolean isServiceEnabled() {
        return serviceEnabled;
    }

    @Override
    public long getTotalChatTime() {
        return totalChatTime;
    }

    /**
     * Retuns the number of existing rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return the number of existing rooms in the server.
     */
    @Override
    public int getNumberChatRooms() {
         return localMUCRoomManager.getNumberChatRooms();
    }

    /**
     * Retuns the total number of occupants in all rooms in the server.
     *
     * @param onlyLocal true if only users connected to this JVM will be considered. Otherwise count cluster wise.
     * @return the number of existing rooms in the server.
     */
    @Override
    public int getNumberConnectedUsers(final boolean onlyLocal) {
        int total = 0;
        for (final LocalMUCUser user : users.values()) {
            if (user.isJoined()) {
                total = total + 1;
            }
        }
        // Add users from remote cluster nodes
        if (!onlyLocal) {
            final Collection<Integer> results =
                    CacheFactory.doSynchronousClusterTask(new GetNumberConnectedUsers(), false);
            for (final Integer result : results) {
                if (result == null) {
                    continue;
                }
                total = total + result;
            }
        }
        return total;
    }

    /**
     * Retuns the total number of users that have joined in all rooms in the server.
     *
     * @return the number of existing rooms in the server.
     */
    @Override
    public int getNumberRoomOccupants() {
        int total = 0;
        for (final MUCRoom room : localMUCRoomManager.getRooms()) {
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
    @Override
    public long getIncomingMessageCount(final boolean resetAfter) {
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
    @Override
    public long getOutgoingMessageCount(final boolean resetAfter) {
        if (resetAfter) {
            return outMessages.getAndSet(0);
        }
        else {
            return outMessages.get();
        }
    }

    @Override
    public void logConversation(final MUCRoom room, final Message message, final JID sender) {
        // Only log messages that have a subject or body. Otherwise ignore it.
        if (message.getSubject() != null || message.getBody() != null) {
            getArchiver().archive( new ConversationLogEntry( new Date(), room, message, sender) );
        }
    }

    @Override
    public void messageBroadcastedTo(final int numOccupants) {
        // Increment counter of received messages that where broadcasted by one
        inMessages.incrementAndGet();
        // Increment counter of outgoing messages with the number of room occupants
        // that received the message
        outMessages.addAndGet(numOccupants);
    }

    @Override
    public Iterator<DiscoServerItem> getItems() {
        // Check if the service is disabled. Info is not available when
        // disabled.
        if (!isServiceEnabled())
        {
            return null;
        }

        final ArrayList<DiscoServerItem> items = new ArrayList<>();
        final DiscoServerItem item = new DiscoServerItem(new JID(
            getServiceDomain()), getDescription(), null, null, this, this);
        items.add(item);
        return items.iterator();
    }

    @Override
    public Iterator<Element> getIdentities(final String name, final String node, final JID senderJID) {
        final ArrayList<Element> identities = new ArrayList<>();
        if (name == null && node == null) {
            // Answer the identity of the MUC service
            final Element identity = DocumentHelper.createElement("identity");
            identity.addAttribute("category", "conference");
            identity.addAttribute("name", getDescription());
            identity.addAttribute("type", "text");
            identities.add(identity);

            // TODO: Should internationalize Public Chatroom Search, and make it configurable.
            final Element searchId = DocumentHelper.createElement("identity");
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
            final MUCRoom room = getChatRoom(name);
            if (room != null) {
                final Element identity = DocumentHelper.createElement("identity");
                identity.addAttribute("category", "conference");
                identity.addAttribute("name", room.getNaturalLanguageName());
                identity.addAttribute("type", "text");

                identities.add(identity);
            }
        }
        else if (name != null && "x-roomuser-item".equals(node)) {
            // Answer reserved nickname for the sender of the disco request in the requested room
            final MUCRoom room = getChatRoom(name);
            if (room != null) {
                final String reservedNick = room.getReservedNickname(senderJID);
                if (reservedNick != null) {
                    final Element identity = DocumentHelper.createElement("identity");
                    identity.addAttribute("category", "conference");
                    identity.addAttribute("name", reservedNick);
                    identity.addAttribute("type", "text");

                    identities.add(identity);
                }
            }
        }
        return identities.iterator();
    }

    @Override
    public Iterator<String> getFeatures(final String name, final String node, final JID senderJID) {
        final ArrayList<String> features = new ArrayList<>();
        if (name == null && node == null) {
            // Answer the features of the MUC service
            features.add("http://jabber.org/protocol/muc");
            features.add("http://jabber.org/protocol/disco#info");
            features.add("http://jabber.org/protocol/disco#items");
            if ( IQMuclumbusSearchHandler.PROPERTY_ENABLED.getValue() ) {
                features.add( "jabber:iq:search" );
            }
            features.add(IQMuclumbusSearchHandler.NAMESPACE);
            features.add(ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT);
            if (!extraDiscoFeatures.isEmpty()) {
                features.addAll(extraDiscoFeatures);
            }
        }
        else if (name != null && node == null) {
            // Answer the features of a given room
            final MUCRoom room = getChatRoom(name);
            if (room != null) {
                features.add("http://jabber.org/protocol/muc");
                if (room.isPublicRoom()) {
                    features.add("muc_public");
                } else {
                    features.add("muc_hidden");
                }
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
                if (!extraDiscoFeatures.isEmpty()) {
                    features.addAll(extraDiscoFeatures);
                }
                if ( JiveGlobals.getBooleanProperty( "xmpp.muc.self-ping.enabled", true ) ) {
                    features.add( "http://jabber.org/protocol/muc#self-ping-optimization" );
                }
                if ( IQMUCvCardHandler.PROPERTY_ENABLED.getValue() ) {
                    features.add( IQMUCvCardHandler.NAMESPACE );
                }
                features.add( "urn:xmpp:sid:0" );
            }
        }
        return features.iterator();
    }

    @Override
    public DataForm getExtendedInfo(final String name, final String node, final JID senderJID) {
        return IQDiscoInfoHandler.getFirstDataForm(this.getExtendedInfos(name, node, senderJID));
    }

    @Override
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        if (name != null && node == null) {
            // Answer the extended info of a given room
            final MUCRoom room = getChatRoom(name);
            if (room != null) {
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
                fieldOcc.setVariable("muc#roominfo_occupants");
                fieldOcc.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.occupants"));
                fieldOcc.addValue(Integer.toString(room.getOccupantsCount()));

                /*field = new XFormFieldImpl("muc#roominfo_lang");
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.language"));
                field.addValue(room.getLanguage());
                dataForm.addField(field);*/

                final FormField fieldDate = dataForm.addField();
                fieldDate.setVariable("x-muc#roominfo_creationdate");
                fieldDate.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.creationdate"));
                fieldDate.addValue(XMPPDateTimeFormat.format(room.getCreationDate()));
                final Set<DataForm> dataForms = new HashSet<>();
                dataForms.add(dataForm);
                return dataForms;
            }
        }
        return new HashSet<DataForm>();
    }

    /**
     * Adds an extra Disco feature to the list of features returned for the conference service.
     * @param feature Feature to add.
     */
    @Override
    public void addExtraFeature(final String feature) {
        extraDiscoFeatures.add(feature);
    }

    /**
     * Removes an extra Disco feature from the list of features returned for the conference service.
     * @param feature Feature to remove.
     */
    @Override
    public void removeExtraFeature(final String feature) {
        extraDiscoFeatures.remove(feature);
    }

    /**
     * Adds an extra Disco identity to the list of identities returned for the conference service.
     * @param category Category for identity.  e.g. conference
     * @param name Descriptive name for identity.  e.g. Public Chatrooms
     * @param type Type for identity.  e.g. text
     */
    @Override
    public void addExtraIdentity(final String category, final String name, final String type) {
        final Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", category);
        identity.addAttribute("name", name);
        identity.addAttribute("type", type);
        extraDiscoIdentities.add(identity);
    }

    /**
     * Removes an extra Disco identity from the list of identities returned for the conference service.
     * @param name Name of identity to remove.
     */
    @Override
    public void removeExtraIdentity(final String name) {
        final Iterator<Element> iter = extraDiscoIdentities.iterator();
        while (iter.hasNext()) {
            final Element elem = iter.next();
            if (name.equals(elem.attribute("name").getStringValue())) {
                iter.remove();
                break;
            }
        }
    }

    /**
     * Sets the MUC event delegate handler for this service.
     * @param delegate Handler for MUC events.
     */
    public void setMUCDelegate(final MUCEventDelegate delegate) {
        mucEventDelegate = delegate;
    }

    /**
     * Gets the MUC event delegate handler for this service.
     * @return Handler for MUC events (delegate)
     */
    public MUCEventDelegate getMUCDelegate() {
        return mucEventDelegate;
    }

    @Override
    public boolean hasInfo(final String name, final String node, final JID senderJID) {
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

    @Override
    public Iterator<DiscoItem> getItems(final String name, final String node, final JID senderJID) {
        // Check if the service is disabled. Info is not available when disabled.
        if (!isServiceEnabled()) {
            return null;
        }
        final Set<DiscoItem> answer = new HashSet<>();
        if (name == null && node == null)
        {
            // Answer all the public rooms as items
            for (final MUCRoom room : localMUCRoomManager.getRooms())
            {
                if (canDiscoverRoom(room, senderJID))
                {
                    answer.add(new DiscoItem(room.getRole().getRoleAddress(),
                        room.getNaturalLanguageName(), null, null));
                }
            }
        }
        else if (name != null && node == null) {
            // Answer the room occupants as items if that info is publicly available
            final MUCRoom room = getChatRoom(name);
            if (room != null && canDiscoverRoom(room, senderJID)) {
                for (final MUCRole role : room.getOccupants()) {
                    // TODO Should we filter occupants that are invisible (presence is not broadcasted)?
                    answer.add(new DiscoItem(role.getRoleAddress(), null, null, null));
                }
            }
        }
        return answer.iterator();
    }

    @Override
    public boolean canDiscoverRoom(final MUCRoom room, final JID entity) {
        // Check if locked rooms may be discovered
        if (!allowToDiscoverLockedRooms && room.isLocked()) {
            return false;
        }
        if (!room.isPublicRoom()) {
            if (!allowToDiscoverMembersOnlyRooms && room.isMembersOnly()) {
                return false;
            }
            final MUCRole.Affiliation affiliation = room.getAffiliation(entity.asBareJID());
            return affiliation == MUCRole.Affiliation.owner
                || affiliation == MUCRole.Affiliation.admin
                || affiliation == MUCRole.Affiliation.member;
        }
        return true;
    }

    /**
     * Converts an array to a comma-delimited String.
     *
     * @param array the array.
     * @return a comma delimited String of the array values.
     */
    private static String fromArray(final String [] array) {
        final StringBuilder buf = new StringBuilder();
        for (int i=0; i<array.length; i++) {
            buf.append(array[i]);
            if (i != array.length-1) {
                buf.append(',');
            }
        }
        return buf.toString();
    }

    /**
     * Converts a collection to a comma-delimited String.
     *
     * @param coll the collection.
     * @return a comma delimited String of the array values.
     */
    private static String fromCollection(final Collection<JID> coll) {
        final StringBuilder buf = new StringBuilder();
        for (final JID elem: coll) {
            buf.append(elem.toBareJID()).append(',');
        }
        final int endPos = buf.length() > 1 ? buf.length() - 1 : 0;
        return buf.substring(0, endPos);
    }

    @Override
    public boolean isHidden() {
        return isHidden;
    }

}
