/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.archive.Archiver;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.group.ConcurrentGroupList;
import org.jivesoftware.openfire.group.GroupAwareList;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.cluster.SyncLocalOccupantsAndSendJoinPresenceTask;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentManager;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.DataForm.Type;
import org.xmpp.forms.FormField;
import org.xmpp.packet.*;
import org.xmpp.resultsetmanagement.ResultSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class MultiUserChatServiceImpl implements Component, MultiUserChatService,
    ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider, XMPPServerListener, ClusterEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(MultiUserChatServiceImpl.class);

    /**
     * The time to elapse between clearing of idle chat users.
     */
    private Duration userIdleTaskInterval;

    /**
     * The period that a user must be idle before he/she gets kicked from all the rooms. Null to disable the feature.
     */
    private Duration userIdleKick = null;

    /**
     * The period that a user must be idle before he/she gets pinged from the rooms that they're in, to determine if
     * they're a 'ghost'. Null to disable the feature.
     */
    private Duration userIdlePing = null;

    /**
     * Task that kicks and pings idle users from the rooms.
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
     * Responsible for maintaining the in-memory collection of MUCRooms for this service.
     */
    private final LocalMUCRoomManager localMUCRoomManager;

    /**
     * Responsible for maintaining the in-memory collection of MUCUsers for this service.
     */
    private final OccupantManager occupantManager;

    private final HistoryStrategy historyStrategy;

    private RoutingTable routingTable = null;

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
    private boolean isHidden;

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
     * Briefly holds stanza IDs and recipients of IQ ping requests, sent to determine if an occupant is still present in
     * the room ('ghost user' detection). Maps a stanza ID to the JID that has been pinged. Note that the stanza ID
     * value should be unique, to not overwrite others.
     *
     * The eviction time of entries is short, so that we do not need to bother with manual cache evictions.
     *
     * The same cache can be used for all MUC services as there is no service or room-specific data in the cache.
     */
    private static final Cache<String, JID> PINGS_SENT = CacheFactory.createCache("MUC Service Pings Sent");

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
        historyStrategy = new HistoryStrategy(getAddress(), null);

        localMUCRoomManager = new LocalMUCRoomManager(this);
        occupantManager = new OccupantManager(this);
    }

    @Override
    @Nonnull
    public OccupantManager getOccupantManager() {
        return occupantManager;
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
            // Check for IQ Ping responses
            if (isPendingPingResponse(packet)) {
                Log.debug("Ping response received from occupant '{}', addressed to: '{}'", packet.getFrom(), packet.getTo());
                return;
            }
            // Check for 'ghost' users (OF-910 / OF-2209 / OF-2369)
            if (isDeliveryRelatedErrorResponse(packet)) {
                Log.info("Received a stanza that contained a delivery-related error response from {}. This is indicative of a 'ghost' user. Removing this user from all chat rooms.", packet.getFrom());
                removeChatUser(packet.getFrom());
                return;
            }
            // Check if the packet is a disco request or a packet with namespace iq:register
            if (packet instanceof IQ) {
                if (process((IQ)packet)) {
                    Log.trace( "Done processing IQ stanza." );
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
                    XMPPServer.getInstance().getPacketRouter().route( reply );
                }
                Log.debug( "Ignoring stanza addressed at conference service: {}", packet.toXML() );
            }
            else
            {
                Log.trace( "The stanza is a normal packet that should possibly be sent to the room." );
                final JID recipient = packet.getTo();
                final String roomName = recipient != null ? recipient.getNode() : null;
                final JID userJid = packet.getFrom();
                occupantManager.registerActivity(userJid);
                Log.trace( "Stanza recipient: {}, room name: {}, sender: {}", recipient, roomName, userJid );
                if ( !packet.getElement().elements(FMUCHandler.FMUC).isEmpty() ) {
                    Log.trace( "Stanza is a FMUC stanza." );
                    if (roomName == null) {
                        Log.warn("Unable to process FMUC stanza, as it does not address a room: {}", packet.toXML());
                    } else {
                        final Lock lock = getChatRoomLock(roomName);
                        lock.lock();
                        try {
                            final MUCRoom chatRoom = getChatRoom(roomName);
                            if (chatRoom != null) {
                                chatRoom.getFmucHandler().process(packet);
                                // Ensure that other cluster nodes see the changes applied by the method above.
                                syncChatRoom(chatRoom);
                            } else {
                                Log.warn("Unable to process FMUC stanza, as room it's addressed to does not exist: {}", roomName);
                                // FIXME need to send error back in case of IQ request, and FMUC join. Might want to send error back in other cases too.
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                } else if (IQMUCvCardHandler.PROPERTY_ENABLED.getValue() && packet instanceof IQ && ((IQ) packet).isResponse() && (((IQ) packet).getChildElement() != null) && ((IQ) packet).getChildElement().getNamespaceURI().equals(IQMUCvCardHandler.NAMESPACE)) {
                    Log.trace( "Stanza is a VCard response stanza." );
                    processVCardResponse((IQ) packet);
                } else {
                    Log.trace( "Stanza is a regular MUC stanza." );
                    processRegularStanza(packet);
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
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if ("jabber:iq:search".equals(namespace)) {
            final IQ reply = searchHandler.handleIQ(iq);
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if (IQMuclumbusSearchHandler.NAMESPACE.equals(namespace)) {
            final IQ reply = muclumbusSearchHandler.handleIQ(iq);
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if (IQMUCvCardHandler.NAMESPACE.equals(namespace)) {
            final IQ reply = mucVCardHandler.handleIQ(iq);
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            // TODO MUC should have an IQDiscoInfoHandler of its own when MUC becomes a component
            final IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            // TODO MUC should have an IQDiscoItemsHandler of its own when MUC becomes a component
            final IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
            if (reply != null) {
                XMPPServer.getInstance().getPacketRouter().route(reply);
            }
        }
        else if ("urn:xmpp:ping".equals(namespace)) {
            if (iq.isRequest()) {
                XMPPServer.getInstance().getPacketRouter().route( IQ.createResultIQ(iq) );
            }
        }
        else if (this.iqHandlers != null) {
            final IQHandler h = this.iqHandlers.get(namespace);
            if (h != null) {
                try {
                    final IQ reply = h.handleIQ(iq);
                    if (reply != null) {
                        XMPPServer.getInstance().getPacketRouter().route(reply);
                    }
                } catch (final UnauthorizedException e) {
                    Log.trace("Responding with 'service-unavailable' due to authorization exception, to: {}", iq, e);
                    final IQ reply = IQ.createResultIQ(iq);
                    reply.setType(IQ.Type.error);
                    reply.setError(PacketError.Condition.service_unavailable);
                    XMPPServer.getInstance().getPacketRouter().route(reply);
                }
                return true;
            }
            return false;
        } else {
            return false;
        }
        return true;
    }

    /**
     * Generate and send an error packet to indicate that something went wrong.
     *
     * @param packet  the packet to be responded to with an error.
     * @param error   the reason why the operation failed.
     * @param message an optional human-readable error message.
     */
    private void sendErrorPacket( Packet packet, PacketError.Condition error, String message )
    {
        if (packet.getError() != null) {
            Log.debug("Avoid generating an error in response to a stanza that itself has an error (to avoid the chance of entering an endless back-and-forth of exchanging errors). Suppress sending an {} error (with message '{}') in response to: {}", error, message, packet);
            return;
        }
        if ( packet instanceof IQ )
        {
            IQ reply = IQ.createResultIQ((IQ) packet);
            reply.setChildElement(((IQ) packet).getChildElement().createCopy());
            reply.setError(error);
            if ( message != null )
            {
                reply.getError().setText(message);
            }
            XMPPServer.getInstance().getPacketRouter().route(reply);
        }
        else
        {
            Packet reply = packet.createCopy();
            reply.setError(error);
            if ( message != null )
            {
                reply.getError().setText(message);
            }
            reply.setFrom(packet.getTo());
            reply.setTo(packet.getFrom());
            XMPPServer.getInstance().getPacketRouter().route(reply);
        }
    }

    /**
     * Processes a VCard response stanza that is supposedly sent to an occupant of a room as a result of that occupant
     * having requested the VCard of another occupant earlier.
     *
     * VCard processing in MUC rooms depends on a bit of a hack: most clients of requestees cannot process a request for
     * a VCard themselves (see XEP-0054). Instead, the request is rerouted to the bare JID of the requestee, which is
     * responded to by its home server. {@link MUCRoom#sendPrivatePacket(Packet, MUCRole)} implements this rerouting.
     * The response from the home server is then processed by this method. It needs special care as its 'from' address
     * is a bare JID (and thus not directly related to a single occupant).
     *
     * @param response An IQ stanza that should be a response to an earlier VCard request.
     */
    public void processVCardResponse(@Nonnull final IQ response)
    {
        if (!response.isResponse()) {
            throw new IllegalArgumentException("IQ must be a response, but is of type: " + response.getType());
        }

        // Name of the room that the stanza is addressed to.
        final String roomName = response.getTo().getNode();

        if ( roomName == null )
        {
            // Packets to the groupchat service (as opposed to a specific room on the service). This should not occur
            // (should be handled by MultiUserChatServiceImpl instead).
            Log.warn(LocaleUtils.getLocalizedString("muc.error.not-supported") + " " + response);
            return;
        }

        StanzaIDUtil.ensureUniqueAndStableStanzaID(response, response.getTo().asBareJID());

        final Lock lock = getChatRoomLock(roomName);
        lock.lock();
        try {
            // Get the room, if one exists.
            @Nullable MUCRoom room = getChatRoom(roomName);

            // Determine if this sender (responding to the VCard request) has a pre-existing role in the addressed room.
            // VCards responses are sent by servers on behalf of the user, so have a bare JID in the 'from' address.
            // This cannot be uniquely matched to a single occupant. In case that they are with multiple occupants in
            // the room, we will use the role with the most liberal permissions.
            MUCRole preExistingRole;
            if (room == null) {
                preExistingRole = null;
            } else {
                try {
                    preExistingRole = room.getOccupantsByBareJID(response.getFrom().asBareJID())
                        .stream()
                        .min(Comparator.comparingInt(o -> o.getRole().getValue()))
                        .orElse(null);
                } catch (UserNotFoundException e) {
                    preExistingRole = null;
                }
            }
            Log.debug("Preexisting role for user {} in room {} (that currently {} exist): {}", response.getFrom(), roomName, room == null ? "does not" : "does", preExistingRole == null ? "(none)" : preExistingRole);

            process(response, room, preExistingRole);

            // Ensure that other cluster nodes see any changes (unlikely to have occurred here, but better safe than sorry) that might have been applied.
            if (room != null) {
                syncChatRoom(room);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method does all stanza routing in the chat server for 'regular' MUC stanzas. Packet routing is actually very
     * simple:
     *
     * <ul>
     *   <li>Discover the room the user is talking to</li>
     *   <li>If the room is not registered and this is a presence "available" packet, try to join the room</li>
     *   <li>If the room is registered, and presence "unavailable" leave the room</li>
     *   <li>Otherwise, rewrite the sender address and send to the room.</li>
     * </ul>
     *
     * @param packet The stanza to route
     */
    public void processRegularStanza( Packet packet ) throws UnauthorizedException, PacketException
    {
        // Name of the room that the stanza is addressed to.
        final String roomName = packet.getTo().getNode();

        if ( roomName == null )
        {
            // Packets to the groupchat service (as opposed to a specific room on the service). This should not occur
            // (should be handled by MultiUserChatServiceImpl instead).
            Log.warn(LocaleUtils.getLocalizedString("muc.error.not-supported") + " " + packet);
            if ( packet instanceof IQ && ((IQ) packet).isRequest() )
            {
                sendErrorPacket(packet, PacketError.Condition.feature_not_implemented, "Unable to process stanza.");
            }
            return;
        }

        StanzaIDUtil.ensureUniqueAndStableStanzaID(packet, packet.getTo().asBareJID());

        final Lock lock = getChatRoomLock(roomName);
        lock.lock();
        try {
            // Get the room, if one exists.
            @Nullable MUCRoom room = getChatRoom(roomName);

            // Determine if this user has a pre-existing role in the addressed room.
            final MUCRole preExistingRole;
            if (room == null) {
                preExistingRole = null;
            } else {
                preExistingRole = room.getOccupantByFullJID(packet.getFrom());
            }
            Log.debug("Preexisting role for user {} in room {} (that currently {} exist): {}", packet.getFrom(), roomName, room == null ? "does not" : "does", preExistingRole == null ? "(none)" : preExistingRole);

            if ( packet instanceof IQ )
            {
                process((IQ) packet, room, preExistingRole);
            }
            else if ( packet instanceof Message )
            {
                process((Message) packet, room, preExistingRole);
            }
            else if ( packet instanceof Presence )
            {
                // Return value is non-null while argument is, in case this is a request to create a new room.
                room = process((Presence) packet, roomName, room, preExistingRole);

            }

            // Ensure that other cluster nodes see any changes that might have been applied.
            if (room != null) {
                syncChatRoom(room);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Processes a Message stanza.
     *
     * @param packet          The stanza to route
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void process(
        @Nonnull final Message packet,
        @Nullable final MUCRoom room,
        @Nullable final MUCRole preExistingRole )
    {
        if (Message.Type.error == packet.getType()) {
            Log.trace("Ignoring messages of type 'error' sent by '{}' to MUC room '{}'", packet.getFrom(), packet.getTo());
            return;
        }

        if (room == null) {
            Log.debug("Rejecting message stanza sent by '{}' to room '{}': Room does not exist.", packet.getFrom(), packet.getTo());
            sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The room that the message was addressed to is not available.");
            return;
        }

        if ( preExistingRole == null )
        {
            processNonOccupantMessage(packet, room);
        }
        else
        {
            processOccupantMessage(packet, room, preExistingRole);
        }
    }

    /**
     * Processes a Message stanza that was sent by a user that's not in the room.
     *
     * Only declined invitations (to join a room) are acceptable messages from users that are not in the room. Other
     * messages are responded to with an error.
     *
     * @param packet   The stanza to process
     * @param room     The room that the stanza was addressed to.
     */
    private void processNonOccupantMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room )
    {
        boolean declinedInvitation = false;
        Element userInfo = null;
        if ( Message.Type.normal == packet.getType() )
        {
            // An user that is not an occupant could be declining an invitation
            userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");
            if ( userInfo != null && userInfo.element("decline") != null )
            {
                // A user has declined an invitation to a room
                // WARNING: Potential fraud if someone fakes the "from" of the
                // message with the JID of a member and sends a "decline"
                declinedInvitation = true;
            }
        }

        if ( declinedInvitation )
        {
            Log.debug("Processing room invitation declination sent by '{}' to room '{}'.", packet.getFrom(), room.getName());
            final Element info = userInfo.element("decline");
            room.sendInvitationRejection(
                new JID(info.attributeValue("to")),
                info.elementTextTrim("reason"),
                packet.getFrom());
        }
        else
        {
            Log.debug("Rejecting message stanza sent by '{}' to room '{}': Sender is not an occupant of the room: {}", packet.getFrom(), room.getName(), packet.toXML());
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You are not in the room.");
        }
    }

    /**
     * Processes a Message stanza that was sent by a user that's in the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processOccupantMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        // Check and reject conflicting packets with conflicting roles In other words, another user already has this nickname
        if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
        {
            Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
            sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
            return;
        }

        if (room.getRoomHistory().isSubjectChangeRequest(packet))
        {
            processChangeSubjectMessage(packet, room, preExistingRole);
            return;
        }

        // An occupant is trying to send a private message, send public message, invite someone to the room or reject an invitation.
        final Message.Type type = packet.getType();
        String nickname = packet.getTo().getResource();
        if ( nickname == null || nickname.trim().length() == 0 )
        {
            nickname = null;
        }

        // Public message (not addressed to a specific occupant)
        if ( nickname == null && Message.Type.groupchat == type )
        {
            processPublicMessage(packet, room, preExistingRole);
            return;
        }

        // Private message (addressed to a specific occupant)
        if ( nickname != null && (Message.Type.chat == type || Message.Type.normal == type) )
        {
            processPrivateMessage(packet, room, preExistingRole);
            return;
        }

        if ( nickname == null && Message.Type.normal == type )
        {
            // An occupant could be sending an invitation or declining an invitation
            final Element userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");

            if ( userInfo != null && userInfo.element("invite") != null )
            {
                // An occupant is sending invitations
                processSendingInvitationMessage(packet, room, preExistingRole);
                return;
            }

            if ( userInfo != null && userInfo.element("decline") != null )
            {
                // An occupant has declined an invitation
                processDecliningInvitationMessage(packet, room);
                return;
            }
        }

        Log.debug("Unable to process message: {}", packet.toXML());
        sendErrorPacket(packet, PacketError.Condition.bad_request, "Unable to process message.");
    }

    /**
     * Process a 'change subject' message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processChangeSubjectMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing subject change request from occupant '{}' to room '{}'.", packet.getFrom(), room.getName());
        try
        {
            room.changeSubject(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting subject change request from occupant '{}' to room '{}'.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to change the subject of this room.");
        }
    }

    /**
     * Process a public message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processPublicMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing public message from occupant '{}' to room '{}'.", packet.getFrom(), room.getName());
        try
        {
            room.sendPublicMessage(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting public message from occupant '{}' to room '{}'. User is not allowed to send message (might not have voice).", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to send a public message to the room (you might require 'voice').");
        }
    }

    /**
     * Process a private message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processPrivateMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing private message from occupant '{}' to room '{}'.", packet.getFrom(), room.getName());
        try
        {
            room.sendPrivatePacket(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting private message from occupant '{}' to room '{}'. User has a role that disallows sending private messages in this room.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to send a private messages in the room.");
        }
        catch ( NotFoundException e )
        {
            Log.debug("Rejecting private message from occupant '{}' to room '{}'. User addressing a non-existent recipient.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The intended recipient of your private message is not available.");
        }
    }

    /**
     * Process a room-invitation message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processSendingInvitationMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing an invitation message from occupant '{}' to room '{}'.", packet.getFrom(), room.getName());
        try
        {
            final Element userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");

            // Try to keep the list of extensions sent together with the message invitation. These extensions will be sent to the invitees.
            final List<Element> extensions = new ArrayList<>(packet.getElement().elements());
            extensions.remove(userInfo);

            // Send invitations to invitees
            final Iterator<Element> it = userInfo.elementIterator("invite");
            while ( it.hasNext() )
            {
                Element info = it.next();
                JID jid = new JID(info.attributeValue("to"));

                // Add the user as a member of the room if the room is members only
                if (room.isMembersOnly())
                {
                    room.addMember(jid, null, preExistingRole);
                }

                // Send the invitation to the invitee
                room.sendInvitation(jid, info.elementTextTrim("reason"), preExistingRole, extensions);
            }
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}': Invitations are not allowed, or occupant is not allowed to modify the member list.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "This room disallows invitations to be sent, or you're not allowed to modify the member list of this room.");
        }
        catch ( ConflictException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}'.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.conflict, "An unexpected exception occurred."); // TODO Is this code reachable?
        }
        catch ( CannotBeInvitedException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}': The user being invited does not have access to the room.", packet.getFrom(), room.getName(), e);
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "The user being invited does not have access to the room.");
        }
    }

    /**
     * Process a declination of a room-invitation message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     */
    private void processDecliningInvitationMessage(
        @Nonnull final Message packet,
        @Nonnull final MUCRoom room)
    {
        Log.trace("Processing an invite declination message from '{}' to room '{}'.", packet.getFrom(), room.getName());
        final Element info = packet.getChildElement("x", "http://jabber.org/protocol/muc#user").element("decline");
        room.sendInvitationRejection(new JID(info.attributeValue("to")),
            info.elementTextTrim("reason"), packet.getFrom());
    }

    /**
     * Processes an IQ stanza.
     *
     * @param packet          The stanza to route
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void process(
        @Nonnull final IQ packet,
        @Nullable final MUCRoom room,
        @Nullable final MUCRole preExistingRole )
    {
        // Packets to a specific node/group/room
        if ( preExistingRole == null || room == null)
        {
            Log.debug("Ignoring stanza received from a non-occupant of a room (room might not even exist): {}", packet.toXML());
            if ( packet.isRequest() )
            {
                // If a non-occupant sends a disco to an address of the form <room@service/nick>, a MUC service MUST
                // return a <bad-request/> error. http://xmpp.org/extensions/xep-0045.html#disco-occupant
                sendErrorPacket(packet, PacketError.Condition.bad_request, "You are not an occupant of this room.");
            }
            return;
        }

        if ( packet.isResponse() )
        {
            // Only process IQ result packet if it's a private packet sent to another room occupant
            if ( packet.getTo().getResource() != null )
            {
                try
                {
                    // User is sending an IQ result packet to another room occupant
                    room.sendPrivatePacket(packet, preExistingRole);
                }
                catch ( NotFoundException | ForbiddenException e )
                {
                    // Do nothing. No error will be sent to the sender of the IQ result packet
                    Log.debug("Silently ignoring an IQ response sent to the room as a private message that caused an exception while being processed: {}", packet.toXML(), e);
                }
            }
            else
            {
                Log.trace("Silently ignoring an IQ response sent to the room, but not as a private message: {}", packet.toXML());
            }
        }
        else
        {
            // Check and reject conflicting packets with conflicting roles In other words, another user already has this nickname
            if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
            {
                Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
                sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
                return;
            }

            try
            {
                // TODO Analyze if it is correct for these first two blocks to be processed without evaluating if they're addressed to the room or if they're a PM.
                Element query = packet.getElement().element("query");
                if ( query != null && "http://jabber.org/protocol/muc#owner".equals(query.getNamespaceURI()) )
                {
                    room.getIQOwnerHandler().handleIQ(packet, preExistingRole);
                }
                else if ( query != null && "http://jabber.org/protocol/muc#admin".equals(query.getNamespaceURI()) )
                {
                    room.getIQAdminHandler().handleIQ(packet, preExistingRole);
                }
                else
                {
                    final String toNickname = packet.getTo().getResource();
                    if ( toNickname != null )
                    {
                        // User is sending to a room occupant.
                        final boolean selfPingEnabled = JiveGlobals.getBooleanProperty("xmpp.muc.self-ping.enabled", true);
                        if ( selfPingEnabled && toNickname.equals(preExistingRole.getNickname()) && packet.isRequest()
                            && packet.getElement().element(QName.get(IQPingHandler.ELEMENT_NAME, IQPingHandler.NAMESPACE)) != null )
                        {
                            Log.trace("User '{}' is sending an IQ 'ping' to itself. See XEP-0410: MUC Self-Ping (Schrödinger's Chat).", packet.getFrom());
                            XMPPServer.getInstance().getPacketRouter().route(IQ.createResultIQ(packet));
                        }
                        else
                        {
                            Log.trace("User '{}' is sending an IQ stanza to another room occupant (as a PM) with nickname: '{}'.", packet.getFrom(), toNickname);
                            room.sendPrivatePacket(packet, preExistingRole);
                        }
                    }
                    else
                    {
                        Log.debug("An IQ request was addressed to the MUC room '{}' which cannot answer it: {}", room.getName(), packet.toXML());
                        sendErrorPacket(packet, PacketError.Condition.bad_request, "IQ request cannot be processed by the MUC room itself.");
                    }
                }
            }
            catch ( NotAcceptableException e )
            {
                Log.debug("Unable to process IQ stanza: room requires a password, but none was supplied.", e);
                sendErrorPacket(packet, PacketError.Condition.not_acceptable, "Room requires a password, but none was supplied.");
            }
            catch ( ForbiddenException e )
            {
                Log.debug("Unable to process IQ stanza: sender don't have authorization to perform the request.", e);
                sendErrorPacket(packet, PacketError.Condition.forbidden, "You don't have authorization to perform this request.");
            }
            catch ( NotFoundException e )
            {
                Log.debug("Unable to process IQ stanza: the intended recipient is not available.", e);
                sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The intended recipient is not available.");
            }
            catch ( ConflictException e )
            {
                Log.debug("Unable to process IQ stanza: processing this request would leave the room in an invalid state (eg: without owners).", e);
                sendErrorPacket(packet, PacketError.Condition.conflict, "Processing this request would leave the room in an invalid state (eg: without owners).");
            }
            catch ( NotAllowedException e )
            {
                Log.debug("Unable to process IQ stanza: an owner or administrator cannot be banned from the room.", e);
                sendErrorPacket(packet, PacketError.Condition.not_allowed, "An owner or administrator cannot be banned from the room.");
            }
            catch ( CannotBeInvitedException e )
            {
                Log.debug("Unable to process IQ stanza: user being invited as a result of being added to a members-only room still does not have permission.", e);
                sendErrorPacket(packet, PacketError.Condition.not_acceptable, "User being invited as a result of being added to a members-only room still does not have permission.");
            }
            catch ( Exception e )
            {
                Log.error("An unexpected exception occurred while processing IQ stanza: {}", packet.toXML(), e);
                sendErrorPacket(packet, PacketError.Condition.internal_server_error, "An unexpected exception occurred while processing your request.");
            }
        }
    }

    /**
     * Process a Presence stanza.
     *
     * This method might be invoked for a room that does not yet exist (when the presence is a room-creation request).
     * This is why this method, unlike the process methods for Message and IQ stanza takes a <em>room name</em> argument
     * and returns the room that processed to request.
     *
     * @param packet          The stanza to process.
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param room            The room that the stanza was addressed to, if it exists.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     * @return the room that handled the request
     */
    @Nullable
    private MUCRoom process(
        @Nonnull final Presence packet,
        @Nonnull final String roomName,
        @Nullable final MUCRoom room,
        @Nullable MUCRole preExistingRole )
    {
        final Element mucInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc"); // only sent in initial presence
        final String nickname = packet.getTo().getResource() == null
            || packet.getTo().getResource().trim().isEmpty() ? null
            : packet.getTo().getResource().trim();

        if ( preExistingRole == null && Presence.Type.unavailable == packet.getType() ) {

            // This is for clustering scenarios where one node could already have cleaned up the clustered cache,
            // but the local node still needs to process the 'unavailable' presence of the leaving occupant.
            final MUCRoom localRoom = localMUCRoomManager.getLocalRooms().get(roomName);
            if (localRoom != null) {
                preExistingRole = localRoom.getOccupantByFullJID(packet.getFrom());
            }

            if (preExistingRole == null) {
                Log.debug("Silently ignoring user '{}' leaving a room that it has no role in '{}' (was the room just destroyed)?", packet.getFrom(), roomName);
                return null;
            } else {
                Log.debug("NOT silently ignoring user {} leaving a room. Sending 'unavailable' presence for room {} because the occupant was still present in the local room cache", packet.getFrom(), roomName);
            }
        }
        if ( preExistingRole == null || (mucInfo != null && preExistingRole.getNickname().equalsIgnoreCase(nickname) ) )
        {
            // If we're not already in a room (role == null), we either are joining it or it's not properly addressed and we drop it silently.
            // Alternative is that mucInfo is not null, in which case the client thinks it isn't in the room, so we should join anyway.
            return processRoomJoinRequest(packet, roomName, room, nickname);
        }
        else
        {
            // Check and reject conflicting packets with conflicting roles
            // In other words, another user already has this nickname
            if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
            {
                Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
                sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
                return room;
            }

            if (room == null) {
                if (Presence.Type.unavailable == packet.getType()) {
                    Log.debug("Silently ignoring user '{}' leaving a non-existing room '{}' (was the room just destroyed)?", packet.getFrom(), roomName);
                } else {
                    Log.warn("Unable to process presence update from user '{}' to a non-existing room: {}", packet.getFrom(), roomName);
                }
                return null;
            }
            try
            {
                if ( nickname != null && !preExistingRole.getNickname().equalsIgnoreCase(nickname) && Presence.Type.unavailable != packet.getType() )
                {
                    // Occupant has changed his nickname. Send two presences to each room occupant.
                    processNickNameChange(packet, room, preExistingRole, nickname);
                }
                else
                {
                    processPresenceUpdate(packet, room, preExistingRole);
                }
            }
            catch ( Exception e )
            {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            return room;
        }
    }

    /**
     * Process a request to join a room.
     *
     * This method might be invoked for a room that does not yet exist (when the presence is a room-creation request).
     *
     * @param packet   The stanza representing the nickname-change request.
     * @param roomName The name of the room that the stanza was addressed to.
     * @param room     The room that the stanza was addressed to, if it exists.
     * @param nickname The requested nickname.
     * @return the room that handled the request
     */
    private MUCRoom processRoomJoinRequest(
        @Nonnull final Presence packet,
        @Nonnull final String roomName,
        @Nullable MUCRoom room,
        @Nullable String nickname )
    {
        Log.trace("Processing join request from '{}' for room '{}'", packet.getFrom(), roomName);

        if ( nickname == null )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: request did not specify a nickname", packet.getFrom(), roomName);

            // A resource is required in order to join a room http://xmpp.org/extensions/xep-0045.html#enter
            // If the user does not specify a room nickname (note the bare JID on the 'from' address in the following example), the service MUST return a <jid-malformed/> error
            if ( packet.getType() != Presence.Type.error )
            {
                sendErrorPacket(packet, PacketError.Condition.jid_malformed, "A nickname (resource-part) is required in order to join a room.");
            }
            return null;
        }

        if ( !packet.isAvailable() )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: request unexpectedly provided a presence stanza of type '{}'. Expected none.", packet.getFrom(), roomName, packet.getType());
            if ( packet.getType() != Presence.Type.error )
            {
                sendErrorPacket(packet, PacketError.Condition.unexpected_request, "Unexpected stanza type: " + packet.getType());
            }
            return null;
        }

        if (room == null) {
            try {
                // Create the room
                room = getChatRoom(roomName, packet.getFrom());
            } catch (NotAllowedException e) {
                Log.debug("Request from '{}' to join room '{}' rejected: user does not have permission to create a new room.", packet.getFrom(), roomName, e);
                sendErrorPacket(packet, PacketError.Condition.not_allowed, "You do not have permission to create a new room.");
                return null;
            }
        }

        try
        {
            // User must support MUC in order to create a room
            HistoryRequest historyRequest = null;
            String password = null;

            // Check for password & requested history if client supports MUC
            final Element mucInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc");
            if ( mucInfo != null )
            {
                password = mucInfo.elementTextTrim("password");
                if ( mucInfo.element("history") != null )
                {
                    historyRequest = new HistoryRequest(mucInfo);
                }
            }

            // The user joins the room
            final MUCRole role = room.joinRoom(nickname,
                password,
                historyRequest,
                packet.getFrom(),
                packet.createCopy());

            // If the client that created the room is non-MUC compliant then
            // unlock the room thus creating an "instant" room
            if ( mucInfo == null && room.isLocked() && !room.isManuallyLocked() )
            {
                room.unlock(role);
            }
        }
        catch ( UnauthorizedException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user not authorized to create or join the room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.not_authorized, "You're not authorized to create or join the room.");
        }
        catch ( ServiceUnavailableException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: the maximum number of users of the room has been reached.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.service_unavailable, "The maximum number of users of the room has been reached.");
        }
        catch ( UserAlreadyExistsException | ConflictException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: the requested nickname '{}' is being used by someone else in the room.", packet.getFrom(), roomName, nickname, e);
            sendErrorPacket(packet, PacketError.Condition.conflict, "The nickname that is being used is used by someone else.");
        }
        catch ( RoomLockedException e )
        {
            // If a user attempts to enter a room while it is "locked" (i.e., before the room creator provides an initial configuration and therefore before the room officially exists), the service MUST refuse entry and return an <item-not-found/> error to the user
            Log.debug("Request from '{}' to join room '{}' rejected: room is locked.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.item_not_found, "This room is locked (it might not have been configured yet).");
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user not authorized join the room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You're not allowed to join this room.");
        }
        catch ( RegistrationRequiredException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: room is member-only, user is not a member.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.registration_required, "This is a member-only room. Membership is required.");
        }
        catch ( NotAcceptableException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user attempts to use nickname '{}' which is different from the reserved nickname.", packet.getFrom(), roomName, nickname, e);
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You're trying to join with a nickname different than the reserved nickname.");
        }
        return room;
    }

    /**
     * Process a presence status update for a user.
     *
     * @param packet          The stanza to process
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza.
     */
    private void processPresenceUpdate(
        @Nonnull final Presence packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole )
    {
        if ( Presence.Type.unavailable == packet.getType() )
        {
            Log.trace("Occupant '{}' of room '{}' is leaving.", preExistingRole.getUserAddress(), room.getName());
            // TODO Consider that different nodes can be creating and processing this presence at the same time (when remote node went down)
            preExistingRole.setPresence(packet);
            room.leaveRoom(preExistingRole);
        }
        else
        {
            Log.trace("Occupant '{}' of room '{}' changed its availability status.", preExistingRole.getUserAddress(), room.getName());
            room.presenceUpdated(preExistingRole, packet);
        }
    }

    /**
     * Process a request to change a nickname.
     *
     * @param packet          The stanza representing the nickname-change request.
     * @param room            The room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza.
     * @param nickname        The requested nickname.
     */
    private void processNickNameChange(
        @Nonnull final Presence packet,
        @Nonnull final MUCRoom room,
        @Nonnull final MUCRole preExistingRole,
        @Nonnull String nickname )
        throws UserNotFoundException
    {
        Log.trace("Occupant '{}' of room '{}' tries to change its nickname to '{}'.", preExistingRole.getUserAddress(), room.getName(), nickname);

        if ( room.getOccupantsByBareJID(packet.getFrom().asBareJID()).isEmpty() )
        {
            Log.trace("Nickname change request denied: requestor '{}' is not an occupant of the room.", packet.getFrom().asBareJID());
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You are not an occupant of this chatroom.");
            return;
        }

        if ( !room.canChangeNickname() )
        {
            Log.trace("Nickname change request denied: Room configuration does not allow nickname changes.");
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "Chatroom does not allow nickname changes.");
            return;
        }

        List<MUCRole> existingOccupants;
        try {
            existingOccupants = room.getOccupantsByNickname(nickname);
        } catch (UserNotFoundException e) {
            existingOccupants = Collections.emptyList();
        }

        if ( !existingOccupants.isEmpty() && !existingOccupants.stream().allMatch(r->r.getUserAddress().asBareJID().equals(preExistingRole.getUserAddress().asBareJID())) )
        {
            Log.trace("Nickname change request denied: the requested nickname '{}' is used by another occupant of the room.", nickname);
            sendErrorPacket(packet, PacketError.Condition.conflict, "This nickname is taken.");
            return;
        }

        final JID memberBareJID = room.getMemberForReservedNickname(nickname);
        if (memberBareJID != null && !memberBareJID.equals(preExistingRole.getUserAddress().asBareJID()))
        {
            Log.trace("Nickname change request denied: the requested nickname '{}' is reserved by a member of the room.", nickname);
            sendErrorPacket(packet, PacketError.Condition.conflict, "This nickname is taken.");
            return;
        }

        // Send "unavailable" presence for the old nickname
        final Presence presence = preExistingRole.getPresence(); // This returns a copy. Modifications will not be applied to the original.
        // Switch the presence to OFFLINE
        presence.setType(Presence.Type.unavailable);
        presence.setStatus(null);
        // Add the new nickname and status 303 as properties
        final Element frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
        frag.element("item").addAttribute("nick", nickname);
        frag.addElement("status").addAttribute("code", "303");
        room.send(presence, preExistingRole);

        // Send availability presence for the new nickname
        final String oldNick = preExistingRole.getNickname();
        room.nicknameChanged(preExistingRole, packet, oldNick, nickname);
    }

    /**
     * Determines if a stanza is a client-generated response to an IQ Ping request sent by this server.
     *
     * A 'true' result of this method indicates that the client sending the IQ response is currently reachable.
     *
     * @param stanza The stanza to check
     * @return true if the stanza is a response to an IQ Ping request sent by the server, otherwise false.
     */
    public boolean isPendingPingResponse(@Nonnull final Packet stanza) {
        if (!(stanza instanceof IQ)) {
            return false;
        }
        final IQ iq = (IQ) stanza;
        if (iq.isRequest()) {
            return false;
        }

        // Check if this is an error to a ghost-detection ping that we've sent out. Note that clients that are
        // connected but do not support XEP-0199 should send back a 'service-unavailable' error per the XEP, but
        // some clients are known to send 'feature-not-available'. Treat these as indications that the client is
        // still connected.
        final Collection<PacketError.Condition> pingErrorsIndicatingClientConnectivity = Arrays.asList(
            PacketError.Condition.service_unavailable,
            PacketError.Condition.feature_not_implemented
        );

        final JID jid = PINGS_SENT.get(iq.getID());
        final boolean result = jid != null && iq.getFrom().equals(jid)
            && (iq.getError() == null || pingErrorsIndicatingClientConnectivity.contains(iq.getError().getCondition()));

        if (result) {
            // If this is _not_ a valid response, 'isDeliveryRelatedErrorResponse' might want to process this. Otherwise, remove.
            PINGS_SENT.remove(iq.getID());
        }
        return result;
    }

    /**
     * Determines if a stanza is sent by (on behalf of) an entity that MUC room believes to be an occupant
     * when they've left: a 'ghost' occupant
     *
     * For message and presence stanzas, the delivery errors as defined in section 18.1.2 of XEP-0045 are used.
     *
     * For IQ stanzas, these errors may be due to lack of client support rather than a vanished occupant. Therefore,
     * IQ stanzas will only return 'true' when they are an error response to an IQ Ping request sent by this server.
     *
     * @param stanza The stanza to check
     * @return true if the stanza is a delivery related error response (from a 'ghost user'), otherwise false.
     * @see <a href="https://xmpp.org/extensions/xep-0045.html#impl-service-ghosts">XEP-0045, section 'ghost users'</a>
     */
    public boolean isDeliveryRelatedErrorResponse(@Nonnull final Packet stanza)
    {
        if (stanza instanceof IQ) {
            final IQ iq = ((IQ)stanza);
            if (iq.getType() != IQ.Type.error) {
                return false;
            }

            // Check if this is an error to a ghost-detection ping that we've sent out. Note that clients that are
            // connected but do not support XEP-0199 should send back a 'service-unavailable' error per the XEP, but
            // some clients are known to send 'feature-not-available'. Treat these as indications that the client is
            // still connected.
            final Collection<PacketError.Condition> pingErrorsIndicatingClientConnectivity = Arrays.asList(
                PacketError.Condition.service_unavailable,
                PacketError.Condition.feature_not_implemented
            );

            if (stanza.getError() != null && pingErrorsIndicatingClientConnectivity.contains(stanza.getError().getCondition())) {
                return false;
            }

            final JID jid = PINGS_SENT.get(iq.getID());
            return jid != null && iq.getFrom().equals(jid) && stanza.getError() != null;
        }

        // The remainder of this implementation applies to Message and Presence stanzas.

        // Conditions as defined in XEP-0045, section 'ghost users'.
        final Collection<PacketError.Condition> deliveryRelatedErrorConditions = Arrays.asList(
            PacketError.Condition.gone,
            PacketError.Condition.item_not_found,
            PacketError.Condition.recipient_unavailable,
            PacketError.Condition.redirect,
            PacketError.Condition.remote_server_not_found,
            PacketError.Condition.remote_server_timeout
        );

        final PacketError error = stanza.getError();
        return error != null && deliveryRelatedErrorConditions.contains(error.getCondition());
    }

    @Override
    public void initialize(final JID jid, final ComponentManager componentManager) {
        initialize(XMPPServer.getInstance());
    }

    @Override
    public void shutdown() {
        enableService( false, false );
        ClusterManager.removeListener(this);
        MUCEventDispatcher.removeListener(occupantManager);
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
     * Operates on users that have been inactive for a while. Depending on the configuration of Openfire, these uses
     * could either be kicked, or be pinged (to determine if they're 'ghost users').
     */
    private class UserTimeoutTask extends TimerTask {
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

        final Set<OccupantManager.Occupant> localOccupants = occupantManager.getLocalOccupants();

        if (localOccupants.isEmpty()) {
            return;
        }

        // A thread pool is used to broadcast concurrently, as well as to limit the execution time of this service.
        final ThreadFactory threadFactory = new NamedThreadFactory("MUC-Shutdown-", Executors.defaultThreadFactory(), false, Thread.NORM_PRIORITY);
        final ExecutorService service = Executors.newFixedThreadPool( Math.min( localOccupants.size(), 10 ), threadFactory );

        // Queue all tasks in the executor service.
        for ( final OccupantManager.Occupant localOccupant : localOccupants )
        {
            service.submit(() -> {
                try
                {
                    // Obtaining the room without acquiring a lock. Usage of the room is read-only (the implementation below
                    // should not modify the room state in a way that the cluster cares about), and more importantly, speed
                    // is of importance (waiting for every room's lock to be acquired would slow down the shutdown process).
                    // Lastly, this service is shutting down (likely because the server is shutting down). The trade-off
                    // between speed and access of room state while not holding a lock seems worth while here.
                    final MUCRoom room = getChatRoom(localOccupant.getRoomName());
                    if (room == null) {
                        // Mismatch between MUCUser#getRooms() and MUCRoom#localMUCRoomManager ?
                        Log.warn("User '{}' appears to have had a role in room '{}' of service '{}' that does not seem to exist.", localOccupant.getRealJID(), localOccupant.getRoomName(), chatServiceName);
                        return;
                    }
                    final MUCRole role = room.getOccupantByFullJID(localOccupant.getRealJID());
                    if (role == null) {
                        // Mismatch between MUCUser#getRooms() and MUCRoom#occupants ?
                        Log.warn("User '{}' appears to have had a role in room '{}' of service '{}' but that role does not seem to exist.", localOccupant.getRealJID(), localOccupant.getRoomName(), chatServiceName);
                        return;
                    }

                    // Send a presence stanza of type "unavailable" to the occupant
                    final Presence presence = room.createPresence( Presence.Type.unavailable );
                    presence.setFrom( role.getRoleAddress() );

                    // A fragment containing the x-extension.
                    final Element fragment = presence.addChildElement( "x", "http://jabber.org/protocol/muc#user" );
                    final Element item = fragment.addElement( "item" );
                    item.addAttribute( "affiliation", "none" );
                    item.addAttribute( "role", "none" );
                    fragment.addElement( "status" ).addAttribute( "code", "332" );

                    // Make sure that the presence change for each user is only sent to that user (and not broadcast in the room)!
                    // Not needed to create a defensive copy of the stanza. It's not used anywhere else.
                    role.send( presence );

                    // Let all other cluster nodes know!
                    room.removeOccupantRole(role);
                }
                catch ( final Exception e )
                {
                    Log.debug( "Unable to inform {} about the imminent destruction of chat service '{}'", localOccupant.realJID, chatServiceName, e );
                }
            });
        }

        // Try to shutdown - wait - force shutdown.
        service.shutdown();
        try
        {
            if (service.awaitTermination( JiveGlobals.getIntProperty( "xmpp.muc.await-termination-millis", 500 ), TimeUnit.MILLISECONDS )) {
                Log.debug("Successfully notified all local users about the imminent destruction of chat service '{}'", chatServiceName);
            } else {
                Log.debug("Unable to notify all local users about the imminent destruction of chat service '{}' (timeout)", chatServiceName);
            }
        }
        catch ( final InterruptedException e )
        {
            Log.debug( "Interrupted while waiting for all users to be notified of shutdown of chat service '{}'. Shutting down immediately.", chatServiceName );
        }
        service.shutdownNow();
    }

    /**
     * Iterates over the local occupants of MUC rooms (users connected to the local cluster node), to determine if an
     * action needs to be taken based on their (lack of) activity. Depending on the configuration of Openfire, inactive
     * users (users that are connected, but have not typed anything) are kicked from the room, and/or are explicitly
     * asked for a proof of life (connectivity), removing them if this proof is not given.
     */
    private void checkForTimedOutUsers()
    {
        for (final OccupantManager.Occupant occupant : occupantManager.getLocalOccupants())
        {
            try
            {
                if (userIdleKick != null && occupant.getLastActive().isBefore(Instant.now().minus(userIdleKick)))
                {
                    // Kick users if 'user_idle' feature is enabled and the user has been idle for too long.
                    tryRemoveOccupantFromRoom(occupant, JiveGlobals.getProperty("admin.mucRoom.timeoutKickReason", "User was inactive for longer than the allowed maximum duration of " + userIdleKick.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase()) + "." );
                }
                else if (userIdlePing != null)
                {
                    // Check if the occupant has been inactive for to long.
                    final Instant lastActive = occupant.getLastActive();
                    final boolean isInactive = lastActive.isBefore(Instant.now().minus(userIdlePing));

                    // Ensure that we didn't quite recently send a ping already.
                    final Instant lastPing = occupant.getLastPingRequest();
                    final boolean isRecentlyPinged = lastPing != null && lastPing.isAfter(Instant.now().minus(userIdlePing));

                    if (isInactive && !isRecentlyPinged) {
                        // Ping the user if it hasn't been kicked already, the feature is enabled, and the user has been idle for too long.
                        pingOccupant(occupant);
                    }
                }
            }
            catch (final Throwable e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Removes an occupant from a room.
     *
     * When the system is 'busy', the occupant will not be removed to prevent threads blocking. In such cases, a message
     * is added to the log files, but the occupant remains in the room.
     *
     * @param occupant The occupant to be removed
     * @param reason A human-readable reason for the removal (to be shared with the occupant as well as other occupants of the room).
     */
    private void tryRemoveOccupantFromRoom(@Nonnull final OccupantManager.Occupant occupant, @Nonnull final String reason)
    {
        final Lock lock = getChatRoomLock(occupant.getRoomName());
        if (!lock.tryLock()) { // Don't block on locked rooms, as we're processing many of them. We'll get them in the next round.
            Log.info("Skip removing as a cluster-wide mutex for the room could not immediately be obtained: {}, should have been removed, because: {}", occupant, reason);
            return;
        }
        try {
            final MUCRoom room = getChatRoom(occupant.getRoomName());
            if (room == null) {
                // Room was recently removed? Mismatch between MUCUser#getRooms() and MUCRoom#localMUCRoomManager?
                Log.info("Skip removing {} as the room no longer exists.", occupant);
                return;
            }

            if (!room.hasOccupant(occupant.getRealJID())) {
                // Occupant no longer in room? Mismatch between MUCUser#getRooms() and MUCRoom#localMUCRoomManager?
                Log.debug("Skip removing {} as this occupant no longer is in the room.", occupant);
                return;
            }

            // Kick the user from the room that he/she had previously joined.
            Log.debug("Removing/kicking {}: {}", occupant, reason);
            room.kickOccupant(occupant.getRealJID(), null, null, reason);

            // Ensure that other cluster nodes see any changes that might have been applied.
            syncChatRoom(room);
        } catch (final NotAllowedException e) {
            // Do nothing since we cannot kick owners or admins
            Log.debug("Skip removing {}, because it's not allowed (this user likely is an owner of admin of the room).", occupant, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends an IQ Ping request to an occupant, and schedules a check that determines if a response to that request
     * was received (removing the occupant if it is not).
     *
     * @param occupant The occupant to ping.
     */
    private void pingOccupant(@Nonnull final OccupantManager.Occupant occupant)
    {
        Log.debug("Pinging {} as the occupant is exceeding the idle time limit.", occupant);
        final IQ pingRequest = new IQ( IQ.Type.get );
        pingRequest.setChildElement( IQPingHandler.ELEMENT_NAME, IQPingHandler.NAMESPACE );
        pingRequest.setFrom( occupant.getRoomName() + "@" + getServiceDomain() );
        pingRequest.setTo( occupant.getRealJID() );
        pingRequest.setID( UUID.randomUUID().toString() ); // Generate unique ID, to prevent duplicate cache entries.
        PINGS_SENT.put(pingRequest.getID(), pingRequest.getTo());

        // Schedule a check to see if the ping was answered, kicking the occupant if it wasn't.
        // The check should be done _before_ the next ping would be sent (to prevent a backlog of ping requests forming)
        Duration timeoutMs = userIdlePing.dividedBy(4);
        final CheckPingResponseTask task = new CheckPingResponseTask(occupant, pingRequest.getID());
        occupant.setPendingPingTask(task);
        TaskEngine.getInstance().schedule(task, timeoutMs);

        XMPPServer.getInstance().getPacketRouter().route(pingRequest);
    }

    /**
     * A task that verifies if a response to a certain IQ Ping stanza (identified by stanza ID) was received, kicking
     * an occupant of a MUC room if it is not, and if there hasn't been any other activity from the occupant since.
     */
    private class CheckPingResponseTask extends TimerTask {
        final OccupantManager.Occupant occupant;
        final String stanzaID;
        final Instant pingRequestSent = Instant.now();

        public CheckPingResponseTask(@Nonnull final OccupantManager.Occupant occupant, @Nonnull final String stanzaID) {
            this.occupant = occupant;
            this.stanzaID = stanzaID;
        }

        @Override
        public void run()
        {
            occupant.setPendingPingTask(null);

            Log.trace("Checking if {} has responded to a ping request that we sent earlier (with stanza ID '{}').", occupant, stanzaID);
            if (!occupant.getRealJID().equals(PINGS_SENT.remove(stanzaID)))  // A null-check should be enough. Check against recipient for extra safety.
            {
                Log.trace("The ping request that we sent earlier to {} seems to have been answered. No need to remove this occupant.", occupant);
                return;
            }

            final Instant lastActivity = occupantManager.lastActivityOnLocalNode(occupant.getRealJID());
            if (lastActivity == null) {
                // If the occupant is in the room, it likely reconnected to another cluster node. Have that node worry about ghost detection.
                Log.debug("{} that has been sent a ping request earlier is no longer connected to the local cluster node. No need to remove this occupant.", occupant);
                return;
            }

            if (lastActivity.isAfter(pingRequestSent)) {
                Log.debug("{} has not responded to a ping request that we sent earlier, but has had other activity. No need to remove this occupant.", occupant);
                return;
            }

            Log.debug("{} has not responded to a ping request that we sent earlier and didn't have other activity. Occupant should be kicked from the room.", occupant);
            tryRemoveOccupantFromRoom(occupant, JiveGlobals.getProperty("admin.mucRoom.noPingResponseKickReason", "User seems to be unreachable (didn't respond to a ping request).") );
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
                    totalChatTime += localMUCRoomManager.unloadInactiveRooms(cleanUpDate).toMillis();
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
    @Nonnull public Lock getChatRoomLock(@Nonnull final String roomName) {
        return localMUCRoomManager.getLock(roomName);
    }

    @Override
    public void syncChatRoom(@Nonnull final MUCRoom room) {
        localMUCRoomManager.sync(room);
    }

    @Override
    @Nonnull
    public MUCRoom getChatRoom(@Nonnull final String roomName, @Nonnull final JID userjid) throws NotAllowedException {
        MUCRoom room;
        boolean loaded = false;
        boolean created = false;
        final Lock lock = localMUCRoomManager.getLock(roomName);
        lock.lock();
        try {
            room = localMUCRoomManager.get(roomName);
            if (room == null) {
                room = new MUCRoom(this, roomName);
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
                localMUCRoomManager.add(room);
            }
        } finally {
            lock.unlock();
        }
        if (created) {
            // Fire event that a new room has been created
            MUCEventDispatcher.roomCreated(room.getRole().getRoleAddress());
        }
        if (loaded || created) {
            // Initiate FMUC, when enabled.
            room.getFmucHandler().applyConfigurationChanges();
        }
        return room;
    }

    @Override
    public MUCRoom getChatRoom(@Nonnull final String roomName) {
        boolean loaded = false;
        MUCRoom room = localMUCRoomManager.get(roomName);
        if (room == null) {
            // Check if the room exists in the database and was not present in memory
            final Lock lock = localMUCRoomManager.getLock(roomName);
            lock.lock();
            try {
                room = localMUCRoomManager.get(roomName);
                if (room == null) {
                    room = new MUCRoom(this, roomName);
                    // If the room is persistent load the configuration values from the DB
                    try {
                        // Try to load the room's configuration from the database (if the room is
                        // persistent but was added to the DB after the server was started up or the
                        // room may be an old room that was not present in memory)
                        MUCPersistenceManager.loadFromDB(room);
                        loaded = true;
                        localMUCRoomManager.add(room);
                    }
                    catch (final IllegalArgumentException e) {
                        // The room does not exist so do nothing
                        room = null;
                        loaded = false;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        if (loaded) {
            // Initiate FMUC, when enabled.
            room.getFmucHandler().applyConfigurationChanges();
        }
        return room;
    }

    @Override
    public List<MUCRoom> getActiveChatRooms() {
        return new ArrayList<>(localMUCRoomManager.getAll());
    }

    /**
     *  Combine names of all rooms in the database (to catch any rooms that aren't currently in memory) with all
     *  names of rooms currently in memory (to include rooms that are non-persistent / never saved in the database).
     *  Duplicates will be removed by virtue of using a Set.
     *
     * @return Names of all rooms that are known to this service.
     */
    @Override
    public Set<String> getAllRoomNames() {
        final Set<String> result = new HashSet<>();
        result.addAll( MUCPersistenceManager.loadRoomNamesFromDB(this) );
        result.addAll( localMUCRoomManager.getAll().stream().map(MUCRoom::getName).collect(Collectors.toSet()) );

        return result;
    }

    // This method operates on MUC rooms without acquiring a cluster lock for them. As the usage is read-only, and the
    // method would have to lock _every_ room, the cost of acquiring all locks seem to outweigh the benefit.
    @Override
    public Collection<MUCRoomSearchInfo> getAllRoomSearchInfo() {
        // Base the result for all rooms that are in memory, then complement with rooms in the database that haven't
        // been added yet (to catch all non-active rooms);
        return getActiveAndInactiveRooms().stream().map(MUCRoomSearchInfo::new).collect(Collectors.toList());
    }

    /**
     * Returns all rooms serviced by this service. This includes rooms designated as non-active, which are loaded from
     * the database if necessary. This method can also be used solely for that 'side' effect of ensuring that all rooms
     * are loaded.
     *
     * @return All rooms serviced by this service.
     */
    public List<MUCRoom> getActiveAndInactiveRooms() {
        final List<MUCRoom> result = getActiveChatRooms();

        if (JiveGlobals.getBooleanProperty("xmpp.muc.search.skip-unloaded-rooms", false)) {
            return result;
        }

        final Set<String> loadedNames = result.stream().map(MUCRoom::getName).collect(Collectors.toSet());
        final Collection<String> dbNames = MUCPersistenceManager.loadRoomNamesFromDB(this);
        dbNames.removeAll(loadedNames); // what remains needs to be loaded from the database;

        for (final String name : dbNames) {
            // TODO improve scalability instead of loading every room that wasn't loaded before.
            final MUCRoom chatRoom = this.getChatRoom(name);
            if (chatRoom != null) {
                result.add(chatRoom);
            }
        }
        return result;
    }

    @Override
    public boolean hasChatRoom(final String roomName) {
        return getChatRoom(roomName) != null;
    }

    @Override
    public void removeChatRoom(final String roomName) {
        final Lock lock = localMUCRoomManager.getLock(roomName);
        lock.lock();
        try {
            final MUCRoom room = localMUCRoomManager.remove(roomName);
            if (room != null) {
                Log.info("removing chat room:" + roomName + "|" + room.getClass().getName());
                totalChatTime += room.getChatLength();
            } else {
                Log.info("No chatroom {} during removal.", roomName);
            }
        } finally {
            lock.unlock();
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
     * @param userAddress The user's normal jid, not the chat nickname jid.
     */
    private void removeChatUser(final JID userAddress)
    {
        final Set<String> roomNames = occupantManager.roomNamesForAddress(userAddress);

        for (final String roomName : roomNames)
        {
            final Lock lock = getChatRoomLock(roomName);
            lock.lock();
            try {
                final MUCRoom room = getChatRoom(roomName);
                if (room == null) {
                    // Mismatch between MUCUser#getRooms() and MUCRoom#localMUCRoomManager ?
                    Log.warn("User '{}' appears to have had a role in room '{}' of service '{}' that does not seem to exist.", userAddress, roomName, chatServiceName);
                    continue;
                }
                final MUCRole role = room.getOccupantByFullJID(userAddress);
                if (role == null) {
                    // Mismatch between MUCUser#getRooms() and MUCRoom#occupants ?
                    Log.warn("User '{}' appears to have had a role in room '{}' of service '{}' but that role does not seem to exist.", userAddress, roomName, chatServiceName);
                    continue;
                }
                try {
                    room.leaveRoom(role);
                    // Ensure that all cluster nodes see the change to the room
                    syncChatRoom(room);
                } catch (final Exception e) {
                    Log.error(e.getMessage(), e);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public Collection<MUCRole> getMUCRoles(final JID user) {
        final List<MUCRole> userRoles = new ArrayList<>();
        for (final MUCRoom room : localMUCRoomManager.getAll()) {
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
    public void setIdleUserTaskInterval(final @Nonnull Duration duration) {
        // Set the new property value
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.timeout", Long.toString(userIdleTaskInterval.toMillis()));

        rescheduleUserTimeoutTask();
    }

    private void rescheduleUserTimeoutTask()
    {
        // Use the 25% of the smallest of 'userIdleKick' or 'userIdlePing', or 5 minutes if both are unset.
        Duration recalculated = Stream.of(userIdleKick, userIdlePing)
            .filter(Objects::nonNull)
            .filter(duration -> duration.compareTo(Duration.ofSeconds(30)) > 0) // Not faster than every so often.
            .sorted()
            .findFirst()
            .orElse(Duration.ofMinutes(5*4))
            .dividedBy(4);

        // But if the property is set, use that.
        String value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.timeout");
        if (value != null) {
            try {
                recalculated = Duration.ofMillis(Long.parseLong(value));
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.timeout for service "+chatServiceName, e);
            }
        }

        if (Objects.equals(recalculated, this.userIdleTaskInterval)) {
            return;
        }
        Log.info("Rescheduling user idle task, recurring every {}", recalculated);
        this.userIdleTaskInterval = recalculated;

        synchronized (this) {
            // Cancel the existing task because the timeout has changed
            if (userTimeoutTask != null) {
                TaskEngine.getInstance().cancelScheduledTask(userTimeoutTask);
            }

            // Create a new task and schedule it with the new timeout
            userTimeoutTask = new UserTimeoutTask();
            TaskEngine.getInstance().schedule(userTimeoutTask, userIdleTaskInterval, userIdleTaskInterval);
        }
    }

    @Override
    @Nonnull
    public Duration getIdleUserTaskInterval() {
        return this.userIdleTaskInterval;
    }

    @Override
    public void setIdleUserKickThreshold(final @Nullable Duration duration)
    {
        if (Objects.equals(duration, this.userIdleKick)) {
            return;
        }

        this.userIdleKick = duration;

        // Set the new property value
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.idle", userIdleKick == null ? "-1" : Long.toString(userIdleKick.toMillis()));

        rescheduleUserTimeoutTask();
    }

    @Override
    public Duration getIdleUserKickThreshold()
    {
        return userIdleKick;
    }

    @Override
    public void setIdleUserPingThreshold(final @Nullable Duration duration)
    {
        if (Objects.equals(duration, this.userIdlePing)) {
            return;
        }

        this.userIdlePing = duration;

        if (userIdlePing != null && PINGS_SENT.getMaxLifetime() < userIdlePing.dividedBy(4).toMillis()) {
            Log.warn("The cache that tracks Ping requests ({}) has a maximum entry lifetime ({}) that is shorter than the time that we wait for responses ({}). This will result in (some) occupants not being removed when they fail to respond to Pings. An Openfire admin should adjust the configuration.", PINGS_SENT.getName(), Duration.ofMillis(PINGS_SENT.getMaxLifetime()), userIdlePing.dividedBy(4));
        }

        // Set the new property value
        MUCPersistenceManager.setProperty(chatServiceName, "tasks.user.ping", userIdlePing == null ? "-1" : Long.toString(userIdlePing.toMillis()));

        rescheduleUserTimeoutTask();
    }

    @Override
    @Nullable
    public Duration getIdleUserPingThreshold() {
        return userIdlePing;
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
        // Configure the handler of iq:register packets
        registerHandler = new IQMUCRegisterHandler(this);
        // Configure the handlers of search requests
        searchHandler = new IQMUCSearchHandler(this);
        muclumbusSearchHandler = new IQMuclumbusSearchHandler(this);
        mucVCardHandler = new IQMUCvCardHandler(this);
        MUCEventDispatcher.addListener(occupantManager);

        // Ensure that cluster events are handled in RoutingTableImpl first (due to higher listener sequence here)
        ClusterManager.addListener(this, 0);
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
        String value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.idle");
        userIdleKick = null;
        if (value != null) {
            try {
                final long millis = Long.parseLong(value);
                if ( millis < 0 ) {
                    userIdleKick = null; // feature is disabled.
                } else {
                    userIdleKick = Duration.ofMillis(millis);
                }
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.idle for service "+chatServiceName, e);
            }
        }
        value = MUCPersistenceManager.getProperty(chatServiceName, "tasks.user.ping");
        userIdlePing = Duration.ofMinutes(60);
        if (value != null) {
            try {
                final long millis = Long.parseLong(value);
                if ( millis < 0 ) {
                    userIdlePing = null; // feature is disabled.
                } else {
                    userIdlePing = Duration.ofMillis(millis);
                }
            }
            catch (final NumberFormatException e) {
                Log.error("Wrong number format of property tasks.user.ping for service "+chatServiceName, e);
            }
        }
        if (userIdlePing != null && PINGS_SENT.getMaxLifetime() < userIdlePing.dividedBy(4).toMillis()) {
            Log.warn("The cache that tracks Ping requests ({}) has a maximum entry lifetime ({}) that is shorter than the time that we wait for responses ({}). This will result in (some) occupants not being removed when they fail to respond to Pings. An Openfire admin should adjust the configuration.", PINGS_SENT.getName(), Duration.ofMillis(PINGS_SENT.getMaxLifetime()), userIdlePing.dividedBy(4));
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
        rescheduleUserTimeoutTask();
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
    public Archiver<ConversationLogEntry> getArchiver() {
        Archiver<ConversationLogEntry> result = this.archiver;
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

        // Remove unused rooms from memory
        Duration cleanupFreq = Duration.ofMinutes(JiveGlobals.getLongProperty("xmpp.muc.cleanupFrequency.inMinutes", CLEANUP_FREQUENCY));
        TaskEngine.getInstance().schedule(new CleanupTask(), cleanupFreq, cleanupFreq);

        // Set us up to answer disco item requests
        XMPPServer.getInstance().getIQDiscoItemsHandler().addServerItemsProvider(this);
        XMPPServer.getInstance().getIQDiscoInfoHandler().setServerNodeInfoProvider(this.getServiceDomain(), this);

        Log.info(LocaleUtils.getLocalizedString("startup.starting.muc", Collections.singletonList(getServiceDomain())));

        final int preloadDays = MUCPersistenceManager.getIntProperty(chatServiceName, "preload.days", 30);
        if (preloadDays > 0) {
            if (ClusterManager.isClusteringEnabled()) {
                Log.warn("Preloading MUC rooms when clustering is enabled can lead to a lot of duplicated database overhead. Consider disabling MUC room preloading.");
            }
            // Load all the persistent rooms to memory
            final Instant cutoff = Instant.now().minus(Duration.ofDays(preloadDays));
            for (final MUCRoom room : MUCPersistenceManager.loadRoomsFromDB(this, Date.from(cutoff))) {
                localMUCRoomManager.add(room);

                // Start FMUC, if desired.
                room.getFmucHandler().applyConfigurationChanges();
            }
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
     * Returns the number of existing rooms in the server (i.e. persistent or not,
     * in memory or not).
     *
     * @return the number of existing rooms in the server.
     */
    @Override
    public int getNumberChatRooms() {
        // Note that inactive, persistent rooms might not live in memory, only in the database.
        int persisted = MUCPersistenceManager.countRooms(this);
        final long nonPersisted = localMUCRoomManager.getNonPersistentRoomCount();
        return persisted + (int) nonPersisted;
    }

    /**
     * Returns the total number of occupants in all rooms.
     *
     * @return the total number of occupants.
     */
    @Override
    public int getNumberConnectedUsers() {
        return occupantManager.numberOfUniqueUsers();
    }

    /**
     * Returns the total number of users that have joined in all rooms in the server.
     *
     * @return the number of existing rooms in the server.
     */
    @Override
    public int getNumberRoomOccupants() {
        int total = 0;
        for (final Set<OccupantManager.Occupant> nodeSet : occupantManager.getLocalOccupantsByNode().values()) {
            total += nodeSet.size();
        }
        total += occupantManager.getFederatedOccupants().size();
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
    public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
        if (name != null && node == null) {
            // Answer the extended info of a given room
            final MUCRoom room = getChatRoom(name);
            if (room != null) {

                final Locale preferredLocale = SessionManager.getInstance().getLocaleForSession(senderJID);

                final DataForm dataForm = new DataForm(Type.result);

                final FormField fieldType = dataForm.addField();
                fieldType.setVariable("FORM_TYPE");
                fieldType.setType(FormField.Type.hidden);
                fieldType.addValue("http://jabber.org/protocol/muc#roominfo");

                final FormField fieldDescr = dataForm.addField();
                fieldDescr.setVariable("muc#roominfo_description");
                fieldDescr.setType(FormField.Type.text_single);
                fieldDescr.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.desc", preferredLocale));
                fieldDescr.addValue(room.getDescription());

                final FormField fieldSubj = dataForm.addField();
                fieldSubj.setVariable("muc#roominfo_subject");
                fieldSubj.setType(FormField.Type.text_single);
                fieldSubj.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.subject", preferredLocale));
                fieldSubj.addValue(room.getSubject());

                final FormField fieldOcc = dataForm.addField();
                fieldOcc.setVariable("muc#roominfo_occupants");
                fieldOcc.setType(FormField.Type.text_single);
                fieldOcc.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.occupants", preferredLocale));
                fieldOcc.addValue(Integer.toString(room.getOccupantsCount()));

                /*field = new XFormFieldImpl("muc#roominfo_lang");
                field.setType(FormField.Type.text_single);
                field.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.language"));
                field.addValue(room.getLanguage());
                dataForm.addField(field);*/

                final FormField fieldDate = dataForm.addField();
                fieldDate.setVariable("x-muc#roominfo_creationdate");
                fieldDate.setType(FormField.Type.text_single);
                fieldDate.setLabel(LocaleUtils.getLocalizedString("muc.extended.info.creationdate", preferredLocale));
                fieldDate.addValue(XMPPDateTimeFormat.format(room.getCreationDate()));
                final Set<DataForm> dataForms = new HashSet<>();
                dataForms.add(dataForm);
                return dataForms;
            }
        }
        return new HashSet<>();
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
    @Override
    public void setMUCDelegate(final MUCEventDelegate delegate) {
        mucEventDelegate = delegate;
    }

    /**
     * Gets the MUC event delegate handler for this service.
     * @return Handler for MUC events (delegate)
     */
    @Override
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
            // Before returning the items, ensure that all rooms are properly loaded in memory
            getActiveAndInactiveRooms();

            // Answer all the public rooms as items
            for (final MUCRoom room : localMUCRoomManager.getAll())
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
                for (final org.jivesoftware.openfire.muc.MUCRole role : room.getOccupants()) {
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

    public void setHidden(boolean isHidden) {
        this.isHidden = isHidden;
    }

    @Override
    public boolean isHidden() {
        return isHidden;
    }


    @Override
    public void joinedCluster() {
        final String fullServiceName = chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Log.debug("Cluster event: service {} joined a cluster - going to restore {} rooms", fullServiceName, localMUCRoomManager.size());

        // The local node joined a cluster.

        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        final Set<OccupantManager.Occupant> occupantsToSync = localMUCRoomManager.restoreCacheContentAfterJoin(occupantManager);

        Log.debug("Occupants to sync: {}", occupantsToSync);

        // Let the other nodes know about our local occupants, as they need this information when it might leave the
        // cluster (OF-2224) and should tell its local users that new occupants have joined (OF-2233).
        if (!occupantsToSync.isEmpty()) {
            CacheFactory.doClusterTask(new SyncLocalOccupantsAndSendJoinPresenceTask(this.chatServiceName, occupantsToSync));
        }
        // TODO does this work properly when the rooms are not known on the other nodes?
    }

    @Override
    public void joinedCluster(byte[] nodeID) {

        final String fullServiceName = chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Log.debug("Cluster event: service {} got notified that node {} joined a cluster", fullServiceName, new String(nodeID));

        // Another node joined a cluster that we're already part of.

        // Let the new node know about our local occupants, as it needs this information when it might leave the cluster
        // again (OF-2224). The data is also needed for the joined node to be able to tell its local users that other
        // occupants have now joined a MUC room that they're in (OF-2233). To generate these stanzas, the receiving node
        // will probably need additional information. It can obtain this from the clustered cache. Even though we can't
        // be sure if that node has fully added _its own_ data to that cache (we don't know if restoreCacheContent()
        // has finished execution), we can be sure that the cache already holds data as provided by _us_.
        final Set<OccupantManager.Occupant> localOccupants = occupantManager.getLocalOccupants();
        CacheFactory.doClusterTask( new SyncLocalOccupantsAndSendJoinPresenceTask(this.chatServiceName, localOccupants), nodeID );
    }

    @Override
    public void leftCluster() {
        final String fullServiceName = chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Log.debug("Cluster event: service {} left a cluster - going to restore {} rooms", fullServiceName, localMUCRoomManager.size());

        // The local cluster node left the cluster.
        if (XMPPServer.getInstance().isShuttingDown()) {
            // Do not put effort in restoring the correct state if we're shutting down anyway.
            return;
        }

        // Get all room occupants that lived on all other nodes, as from the perspective of this node, those nodes are
        // now no longer part of the cluster.
        final Set<OccupantManager.Occupant> occupantsOnRemovedNodes = occupantManager.leftCluster();

        // Upon leaving a cluster, clustered caches are reset to their local equivalent (by the swap from the clustered
        // cache implementation to the default cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.leftCluster). This means that they now hold no data (as a new cache
        // has been created). Data that's available on the local node needs to be added again.
        localMUCRoomManager.restoreCacheContentAfterLeave(occupantsOnRemovedNodes);

        // Send presence 'leave' for all of these users to the users that remain in the chatroom (on this node)
        makeOccupantsOnDisconnectedClusterNodesLeave(occupantsOnRemovedNodes, null);
    }

    @Override
    public void leftCluster(byte[] nodeID)
    {
        // Another node left the cluster.
        //
        // If the cluster node leaves in an orderly fashion, it might have broadcast
        // the necessary events itself. This cannot be depended on, as the cluster node
        // might have disconnected unexpectedly (as a result of a crash or network issue).
        //
        // All chatroom occupants that were connected to the now disconnected node are no longer 'in the room'. The
        // remaining occupants should receive 'occupant left' stanzas to reflect this.

        final String fullServiceName = chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Log.debug("Cluster event: service {} got notified that node {} left a cluster", fullServiceName, new String(nodeID));


        // We can't be certain if the room cache is fully intact at this point, as described in OF-2297. It is not always
        // feasible to maintain enough 'local data' to be able to recover from this. Here, we check the cache for
        // consistency, and remove occupants from rooms (citing 'technical difficulties') where we find such inconsistency.
        final Set<String> lostRoomNames = localMUCRoomManager.detectAndRemoveLostRooms();
        for (final String lostRoomName : lostRoomNames) {
            try {
                Log.info("Room '{}' was lost from the data structure that's shared in the cluster (the cache). This room is now considered 'gone' for this cluster node. Occupants will be informed.", lostRoomName);
                final Set<OccupantManager.Occupant> occupants = occupantManager.occupantsForRoomByNode(lostRoomName, XMPPServer.getInstance().getNodeID(), true);
                final JID roomJID = new JID(lostRoomName, fullServiceName, null);
                for (final OccupantManager.Occupant occupant : occupants) {
                    try {
                        // Send a presence stanza of type "unavailable" to the occupant
                        final Presence presence = new Presence(Presence.Type.unavailable);
                        presence.setTo(occupant.getRealJID());
                        assert lostRoomName.equals(occupant.getRoomName());
                        presence.setFrom(new JID(lostRoomName, fullServiceName, occupant.getNickname()));

                        // A fragment containing the x-extension.
                        final Element fragment = presence.addChildElement("x", "http://jabber.org/protocol/muc#user");
                        final Element item = fragment.addElement("item");
                        item.addAttribute("affiliation", "none");
                        item.addAttribute("role", "none");
                        fragment.addElement("status").addAttribute("code", "110"); // Inform user that presence refers to itself.
                        fragment.addElement("status").addAttribute("code", "333"); // Inform users that a user was removed because of an error reply.

                        Log.debug("Informing local occupant '{}' on local cluster node that it is being removed from room '{}' because of a (cluster) error.", occupant.getRealJID(), lostRoomName);
                        XMPPServer.getInstance().getPacketRouter().route(presence);
                        XMPPServer.getInstance().getPresenceUpdateHandler().removeDirectPresence(occupant.getRealJID(), roomJID);
                    } catch (Exception e) {
                        Log.warn("Unable to inform local occupant '{}' on local cluster node that it is being removed from room '{}' because of a (cluster) error.", occupant.getRealJID(), lostRoomName, e);
                    }
                }
                // Clean up the locally maintained bookkeeping.
                occupantManager.roomDestroyed(roomJID);
                removeChatRoom(lostRoomName);
            } catch (Exception e) {
                Log.warn("Unable to inform occupants on local cluster node that they are being removed from room '{}' because of a (cluster) error.", lostRoomName, e);
            }
        }

        // Now that occupants have been properly ousted from the lost rooms, we can make an effort to restore the rooms
        // from the database. Calling getActiveAndInactiveRooms() will do just that.
        getActiveAndInactiveRooms();

        // From this point onwards, the remainder of what's in the cache can be considered 'consistent' (as we've dealt with the inconsistencies).
        // We now need to inform these occupants that occupants of the same room, that exist on other cluster nodes (which are now unreachable)
        // are no longer in the room.


        // Get all room occupants that lived on the node that disconnected
        final Set<OccupantManager.Occupant> occupantsOnRemovedNode = occupantManager.leftCluster(NodeID.getInstance(nodeID));

        // The content of the room cache can still hold (some) of these occupants. They should be removed from there!
        // The state of the rooms in the clustered cache should be modified to remove all but our local occupants.
        for (Iterator<OccupantManager.Occupant> i = occupantsOnRemovedNode.iterator(); i.hasNext(); ) {
            OccupantManager.Occupant occupant = i.next();
            final Lock lock = this.getChatRoomLock(occupant.getRoomName());
            lock.lock();
            try {
                final MUCRoom room = this.getChatRoom(occupant.getRoomName());
                if (room == null) {
                    continue;
                }
                final MUCRole occupantRole = room.getOccupantByFullJID(occupant.getRealJID());

                // When leaving a cluster, the routing table will also detect that some clients are no
                // longer available. And send presence unavailable stanzas accordingly. Because of that,
                // the occupant may already have been removed from the room. This may however not happen
                // at all if federated users are in play. Hence the null check here: if the occupant is
                // no longer in the room, we don't need to remove it.
                if (occupantRole != null) {
                    room.removeOccupantRole(occupantRole);
                    this.syncChatRoom(room);
                    Log.debug("Removed occupant role {} from room {}.", occupantRole, room.getJID());
                } else {
                    Log.debug("Occupant '{}' already removed, so we don't need to send 'leave' presence in room {}", occupant, room.getJID());
                    i.remove();
                }
            } finally {
                lock.unlock();
            }
        }

        // Send presence 'leave' for all of these users to the users that remain in the chatroom (on this node)
        makeOccupantsOnDisconnectedClusterNodesLeave(occupantsOnRemovedNode, NodeID.getInstance(nodeID));
    }

    @Override
    public void markedAsSeniorClusterMember() {
        final String fullServiceName = chatServiceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Log.debug("Cluster event: service {} got notified that it is now the senior cluster member", fullServiceName);
        // Do nothing
        // TODO: Check if all occupants are still reachable
    }

    @Override
    public LocalMUCRoomManager getLocalMUCRoomManager() {
        return localMUCRoomManager;
    }

    /**
     * Used by other nodes telling us about their occupants.
     * @param task
     */
    public void process(@Nonnull final SyncLocalOccupantsAndSendJoinPresenceTask task) {
        occupantManager.process(task);
        makeOccupantsOnConnectedClusterNodeJoin(task.getOccupants(), task.getOriginator());
    }

    /**
     * Sends presence 'join' stanzas on behalf of chatroom occupants that are now connected to the cluster (by virtue
     * of a cluster node joining the cluster) to other room occupants are local to this cluster node.
     *
     * Note that this method does not change state (in either the clustered cache, or local equivalents): it only sends stanzas.
     *
     * @param occupantsOnConnectedNodes The occupants for which to send stanzas.
     * @param remoteNodeID The cluster node ID of the server that joined the cluster.
     */
    private void makeOccupantsOnConnectedClusterNodeJoin(@Nullable final Set<OccupantManager.Occupant> occupantsOnConnectedNodes, @Nonnull NodeID remoteNodeID)
    {
        Log.debug("Going to send 'join' presence stanzas on the local cluster node for {} occupant(s) of cluster node {} that is now part of our cluster.", occupantsOnConnectedNodes == null || occupantsOnConnectedNodes.isEmpty() ? 0 : occupantsOnConnectedNodes.size(), remoteNodeID);
        if (occupantsOnConnectedNodes == null || occupantsOnConnectedNodes.isEmpty()) {
            return;
        }

        if (!MUCRoom.JOIN_PRESENCE_ENABLE.getValue()) {
            Log.trace("Skipping, as presences are disabled by configuration.");
            return;
        }

        // Find all occupants that were not already on any other node in the cluster. This intends to prevent sending 'join'
        // presences for occupants that are in the same room, using the same nickname, but using a client that is
        // connected to a cluster node that already was in the cluster.
        final Set<OccupantManager.Occupant> toAdd = occupantsOnConnectedNodes.stream()
            .filter(occupant -> !occupantManager.exists(occupant, remoteNodeID))
            .collect(Collectors.toSet());
        Log.trace("After accounting for occupants already in the room by virtue of having another connected device using the same nickname, {} presence stanza(s) are to be sent.", toAdd.size());

        // For each, broadcast a 'join' presence in the room(s).
        for (final OccupantManager.Occupant occupant : toAdd)
        {
            Log.trace("Preparing to send 'join' stanza for occupant '{}' (using nickname '{}') of room '{}'", occupant.getRealJID(), occupant.getNickname(), occupant.getRoomName());

            final MUCRoom chatRoom = getChatRoom(occupant.roomName);
            if (chatRoom == null) {
                Log.info("User {} seems to be an occupant (using nickname '{}') of a non-existent room named '{}' on newly connected cluster node(s).", occupant.realJID, occupant.nickname, occupant.roomName);
                continue;
            }

            // At this stage, the occupant information must already be available through the clustered cache.
            final MUCRole occupantRole = chatRoom.getOccupantByFullJID(occupant.getRealJID());
            if (occupantRole == null) {
                Log.warn("A remote cluster node ({}) tells us that user {} is supposed to be an occupant (using nickname '{}') of a room named '{}' but the data in the cluster cache does not indicate that this is true.", remoteNodeID, occupant.realJID, occupant.nickname, occupant.roomName);
                continue;
            }

            if (!chatRoom.canBroadcastPresence(occupantRole.getRole())) {
                Log.trace("Skipping join stanza, as room is configured to not broadcast presence for role {}", occupantRole.getRole());
                continue;
            }

            // To prevent each (remaining) cluster node from broadcasting the same presence to all occupants of all cluster nodes,
            // this broadcasts only to occupants on the local node.
            // This also broadcasts to all federated occupants, as we've not yet identified a reliable way to make at least one cluster node inform these occupants. TODO: find such a reliable way, to prevent each (remaining) node sending presence updates.
            final Set<OccupantManager.Occupant> recipients = occupantManager.occupantsForRoomByNode(occupant.roomName, XMPPServer.getInstance().getNodeID(), true);
            for (OccupantManager.Occupant recipient : recipients) {
                Log.trace("Preparing stanza for recipient {} (nickname: {})", recipient.getRealJID(), recipient.getNickname());
                try {
                    // Note that we cannot use org.jivesoftware.openfire.muc.MUCRoom.broadcastPresence() as this would attempt to
                    // broadcast to the user that is 'joining' to all users. We only want to broadcast locally here.

                    // We _need_ to go through the MUCRole for sending this stanza, as that has some additional logic (eg: FMUC).
                    final MUCRole recipientRole = chatRoom.getOccupantByFullJID(recipient.getRealJID());
                    if (recipientRole != null) {
                        recipientRole.send(occupantRole.getPresence());
                    } else {
                        Log.warn("Unable to find MUCRole for recipient '{}' in room {} while broadcasting 'join' presence for occupants on joining cluster node {}.", recipient.getRealJID(), chatRoom.getJID(), remoteNodeID);
                        XMPPServer.getInstance().getPacketRouter().route(occupantRole.getPresence()); // This is a partial fix that will _probably_ work if FMUC is not used. Better than nothing? (although an argument for failing-fast can be made).
                    }
                } catch (Exception e) {
                    Log.warn("A problem occurred while notifying local occupant that user '{}' joined room '{}' as a result of a cluster node {} joining the cluster.", occupant.nickname, occupant.roomName, remoteNodeID, e);
                }
            }
        }
        Log.debug("Finished sending 'join' presence stanzas on the local cluster node.");
    }

    /**
     * Sends presence 'leave' stanzas on behalf of chatroom occupants that are no longer connected to the cluster to
     * room occupants that are local to this cluster node.
     *
     * Note that this method does not change state (in either the clustered cache, or local equivalents): it only sends stanzas.
     *
     * @param occupantsOnRemovedNodes The occupants for which to send stanzas
     * @param nodeID the node that left the cluster. Null if the local node left the cluster.
     */
    private void makeOccupantsOnDisconnectedClusterNodesLeave(@Nullable final Set<OccupantManager.Occupant> occupantsOnRemovedNodes, NodeID nodeID)
    {
        Log.debug("Going to send 'leave' presence stanzas on the local cluster node for {} occupant(s) of one or more cluster nodes that are no longer part of our cluster.", occupantsOnRemovedNodes == null || occupantsOnRemovedNodes.isEmpty() ? 0 : occupantsOnRemovedNodes.size());
        if (occupantsOnRemovedNodes == null || occupantsOnRemovedNodes.isEmpty()) {
            return;
        }

        if (!MUCRoom.JOIN_PRESENCE_ENABLE.getValue()) {
            Log.trace("Skipping, as presences are disabled by configuration.");
            return;
        }

        // Find all occupants that are now no longer on any node in the cluster. This intends to prevent sending 'leave'
        // presences for occupants that are in the same room, using the same nickname, but using a client that is
        // connected to a cluster node that is still in the cluster.
        final Set<OccupantManager.Occupant> toRemove = occupantsOnRemovedNodes.stream()
            .filter(occupant -> !occupantManager.exists(occupant))
            .collect(Collectors.toSet());
        Log.trace("After accounting for occupants still in the room by virtue of having another connected device using the same nickname, {} presence stanza(s) are to be sent.", toRemove.size());

        // For each, broadcast a 'leave' presence in the room(s).
        for(final OccupantManager.Occupant occupant : toRemove) {
            Log.trace("Preparing to send 'leave' stanza for occupant '{}' (using nickname '{}') of room '{}'", occupant.getRealJID(), occupant.getNickname(), occupant.getRoomName());
            final MUCRoom chatRoom = getChatRoom(occupant.getRoomName());
            if (chatRoom == null) {
                Log.info("User {} seems to be an occupant (using nickname '{}') of a non-existent room named '{}' on disconnected cluster node(s).", occupant.getRealJID(), occupant.getNickname(), occupant.getRoomName());
                continue;
            }

            // To prevent each (remaining) cluster node from broadcasting the same presence to all occupants of all remaining nodes,
            // this broadcasts only to occupants on the local node.
            // This also broadcasts to all federated occupants, as we've not yet identified a reliable way to make at least one cluster node inform these occupants. TODO: find such a reliable way, to prevent each (remaining) node sending presence updates.
            final Set<OccupantManager.Occupant> recipients;
            if (nodeID == null) {
                recipients = occupantManager.occupantsForRoomByNode(occupant.getRoomName(), XMPPServer.getInstance().getNodeID(), true);
                Log.trace("Intended recipients, count: {} (occupants of the same room, on local node): {}", recipients.size(), recipients.stream().map(OccupantManager.Occupant::getRealJID).map(JID::toString).collect(Collectors.joining( ", " )));
            } else {
                recipients = occupantManager.occupantsForRoomExceptForNode(occupant.getRoomName(), nodeID, true);
                Log.trace("Intended recipients, count: {} (occupants of the same room, on all remaining cluster nodes): {}", recipients.size(), recipients.stream().map(OccupantManager.Occupant::getRealJID).map(JID::toString).collect(Collectors.joining( ", " )));
            }
            for (OccupantManager.Occupant recipient : recipients) {
                try {
                    Log.trace("Preparing stanza for recipient {} (nickname: {})", recipient.getRealJID(), recipient.getNickname());
                    // Note that we cannot use chatRoom.sendLeavePresenceToExistingOccupants(leaveRole) as this would attempt to
                    // broadcast to the user that is leaving. That user is clearly unreachable in this instance (as it lives on
                    // a now disconnected cluster node.
                    final Presence presence = new Presence(Presence.Type.unavailable);
                    presence.setTo(recipient.getRealJID());
                    presence.setFrom(new JID(chatRoom.getJID().getNode(), chatRoom.getJID().getDomain(), occupant.nickname));
                    final Element childElement = presence.addChildElement("x", "http://jabber.org/protocol/muc#user");
                    final Element item = childElement.addElement("item");
                    item.addAttribute("role", "none");
                    if (chatRoom.canAnyoneDiscoverJID() || chatRoom.getModerators().stream().anyMatch(m->m.getUserAddress().asBareJID().equals(recipient.getRealJID().asBareJID()))) {
                        // Send non-anonymous - add JID.
                        item.addAttribute("jid", occupant.getRealJID().toString());
                    }
                    childElement.addElement( "status" ).addAttribute( "code", "333" );

                    // We _need_ to go through the MUCRole for sending this stanza, as that has some additional logic (eg: FMUC).
                    final MUCRole recipientRole = chatRoom.getOccupantByFullJID(recipient.getRealJID());
                    Log.debug("Stanza now being sent: {}", presence.toXML());
                    if (recipientRole != null) {
                        recipientRole.send(presence);
                    } else {
                        Log.warn("Unable to find MUCRole for recipient '{}' in room {} while broadcasting 'leave' presence for occupants on disconnected cluster node(s).", recipient.getRealJID(), chatRoom.getJID());
                        XMPPServer.getInstance().getPacketRouter().route(presence); // This is a partial fix that will _probably_ work if FMUC is not used. Better than nothing? (although an argument for failing-fast can be made).
                    }
                } catch (Exception e) {
                    Log.warn("A problem occurred while notifying local occupant that user '{}' left room '{}' as a result of a cluster disconnect.", occupant.nickname, occupant.roomName, e);
                }
            }
        }
        Log.debug("Finished sending 'leave' presence stanzas on the local cluster node.");
    }
}
