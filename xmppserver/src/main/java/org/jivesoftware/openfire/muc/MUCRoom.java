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

package org.jivesoftware.openfire.muc;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.event.GroupEventListener;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.group.*;
import org.jivesoftware.openfire.muc.spi.*;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;
import org.xmpp.resultsetmanagement.Result;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * A chat room on the chat server manages its users, and enforces its own security rules.
 *
 * A MUCRoom could represent a persistent room which means that its configuration will be maintained in sync with its
 * representation in the database, or it represents a non-persistent room. These rooms have no representation in the
 * database.
 *
 * @author Gaston Dombiak
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@JiveID(JiveConstants.MUC_ROOM)
public class MUCRoom implements GroupEventListener, UserEventListener, Externalizable, Result, Cacheable {

    private static final Logger Log = LoggerFactory.getLogger(MUCRoom.class);

    public static final SystemProperty<Boolean> JOIN_PRESENCE_ENABLE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.muc.join.presence")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();

    private static final SystemProperty<Duration> SELF_PRESENCE_TIMEOUT = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.muc.join.self-presence-timeout")
        .setDynamic(true)
        .setDefaultValue(Duration.ofSeconds( 2 ))
        .setChronoUnit(ChronoUnit.MILLIS)
        .build();

    public static final SystemProperty<Boolean> ALLOWPM_BLOCKALL = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("xmpp.muc.allowpm.blockall")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    /**
     * The service hosting the room.
     */
    private MultiUserChatService mucService;

    /**
     * All occupants that are associated with this room.
     */
    public final ArrayList<MUCOccupant> occupants = new ArrayList<>();

    /**
     * The name of the room.
     */
    private String name;

    /**
     * The occupant data representing the room itself.
     */
    private MUCOccupant selfOccupantData;

    /**
     * The start time of the chat.
     */
    long startTime;

    /**
     * The end time of the chat.
     */
    long endTime;

    /**
     * After a room has been destroyed it may remain in memory, but it won't be possible to use it.
     * When a room is destroyed it is immediately removed from the MultiUserChatService, but it's
     * possible that while the room was being destroyed it was being used by another thread, so we
     * need to protect the room under these rare circumstances.
     */
    public boolean isDestroyed = false;

    /**
     * ChatRoomHistory object.
     */
    private MUCRoomHistory roomHistory;

    /**
     * Time when the room was locked. A value of zero means that the room is unlocked.
     */
    private long lockedTime;

    /**
     * List of chatroom's owner. The list contains only bare jid.
     */
    GroupAwareList<JID> owners = new ConcurrentGroupList<>();

    /**
     * List of chatroom's admin. The list contains only bare jid.
     */
    GroupAwareList<JID> admins = new ConcurrentGroupList<>();

    /**
     * List of chatroom's members. The list contains only bare jid, mapped to a nickname.
     */
    GroupAwareMap<JID, String> members = new ConcurrentGroupMap<>();

    /**
     * List of chatroom's outcast. The list contains only bare jid of not allowed users.
     */
    private GroupAwareList<JID> outcasts = new ConcurrentGroupList<>();

    /**
     * The natural language name of the room.
     */
    private String naturalLanguageName;

    /**
     * Description of the room. The owner can change the description using the room configuration
     * form.
     */
    private String description;

    /**
     * Indicates if occupants are allowed to change the subject of the room.
     */
    private boolean canOccupantsChangeSubject;

    /**
     * Maximum number of occupants that could be present in the room. If the limit's been reached
     * and a user tries to join, a not-allowed error will be returned.
     */
    private int maxUsers;

    /**
     * List of roles of which presence will be broadcast to the rest of the occupants. This
     * feature is useful for implementing "invisible" occupants.
     */
    private List<Role> rolesToBroadcastPresence = new ArrayList<>();

    /**
     * A public room means that the room is searchable and visible. This means that the room can be
     * located using service discovery requests.
     */
    private boolean publicRoom;

    /**
     * Persistent rooms are saved to the database to make sure that rooms configurations can be
     * restored in case the server goes down.
     */
    private boolean persistent;

    /**
     * Moderated rooms enable only participants to speak. Users that join the room and aren't
     * participants can't speak (they are just visitors).
     */
    private boolean moderated;

    /**
     * A room is considered members-only if an invitation is required in order to enter the room.
     * Any user that is not a member of the room won't be able to join the room unless the user
     * decides to register with the room (thus becoming a member).
     */
    private boolean membersOnly;

    /**
     * Some rooms may restrict the occupants that are able to send invitations. Sending an
     * invitation in a members-only room adds the invitee to the members list.
     */
    private boolean canOccupantsInvite;

    /**
     * The password that every occupant should provide in order to enter the room.
     */
    private String password = null;

    /**
     * Every presence packet can include the JID of every occupant unless the owner deactivates this configuration.
     */
    private boolean canAnyoneDiscoverJID;

    /**
     * The minimal role of persons that are allowed to send private messages in the room.
     */
    private String canSendPrivateMessage;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean logEnabled;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean loginRestrictedToNickname;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean canChangeNickname;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean registrationEnabled;

    /**
     * Enables the FMUC functionality.
     */
    private boolean fmucEnabled;

    /**
     * The address of the MUC room (typically on a remote XMPP domain) to which this room should initiate
     * FMUC federation. In this federation, the local node takes the role of the 'joining' node, while the remote node
     * takes the role of the 'joined' node.
     *
     * When this room is not expected to initiate federation (note that it can still accept inbound federation attempts)
     * then this is null.
     *
     * Although a room can accept multiple inbound joins (where it acts as a 'parent' node), it can initiate only one
     * outbound join at a time (where it acts as a 'child' node).
     */
    private JID fmucOutboundNode;

    /**
     * The 'mode' that describes the FMUC configuration is captured in the supplied object, which is
     * either master-master or master-slave.
     *
     * This should be null only when no outbound federation should be attempted (when {@link #fmucEnabled} is false).
     */
    private FMUCMode fmucOutboundMode;

    /**
     * A set of addresses of MUC rooms (typically on a remote XMPP domain) that defines the list of rooms that is
     * permitted to federate with the local room.
     *
     * A null value is to be interpreted as allowing all rooms to be permitted.
     *
     * An empty set of addresses is to be interpreted as disallowing all rooms to be permitted.
     */
    private Set<JID> fmucInboundNodes;

    /**
     * Internal component that handles IQ packets sent by the room owners.
     */
    private IQOwnerHandler iqOwnerHandler;

    /**
     * Internal component that handles IQ packets sent by moderators, admins and owners.
     */
    private IQAdminHandler iqAdminHandler;

    /**
     * Internal component that handles FMUC stanzas.
     */
    private FMUCHandler fmucHandler;

    /**
     * The last known subject of the room. This information is used to respond disco requests. The
     * MUCRoomHistory class holds the history of the room together with the last message that set
     * the room's subject.
     */
    private String subject = "";

    /**
     * The ID of the room. If the room is temporary and does not log its conversation then the value
     * will always be -1, otherwise a value will be obtained from the database.
     */
    private long roomID = -1;

    /**
     * The date when the room was created.
     */
    private Date creationDate;

    /**
     * The last date when the room's configuration was modified.
     */
    private Date modificationDate;

    /**
     * The date when the last occupant left the room. A null value means that there are occupants
     * in the room at the moment.
     */
    private Date emptyDate;

    /**
     * Indicates if the room is present in the database.
     */
    private boolean savedToDB = false;

    /**
     * Do not use this constructor. It was added to implement the Externalizable
     * interface required to work inside a cluster.
     */
    public MUCRoom() {
    }

    /**
     * Create a new chat room.
     *
     * @param chatService the service hosting the room.
     * @param roomName the name of the room.
     */
    public MUCRoom(@Nonnull MultiUserChatService chatService, @Nonnull String roomName) {
        this.mucService = chatService;
        this.name = roomName;
        this.naturalLanguageName = roomName;
        this.description = roomName;
        this.startTime = System.currentTimeMillis();
        this.creationDate = new Date(startTime);
        this.modificationDate = new Date(startTime);
        this.emptyDate = new Date(startTime);
        this.canOccupantsChangeSubject = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canOccupantsChangeSubject", false);
        this.maxUsers = MUCPersistenceManager.getIntProperty(mucService.getServiceName(), "room.maxUsers", 30);
        this.publicRoom = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.publicRoom", true);
        this.persistent = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.persistent", false);
        this.moderated = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.moderated", false);
        this.membersOnly = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.membersOnly", false);
        this.canOccupantsInvite = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canOccupantsInvite", false);
        this.canAnyoneDiscoverJID = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canAnyoneDiscoverJID", true);
        this.logEnabled = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.logEnabled", true);
        this.loginRestrictedToNickname = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.loginRestrictedToNickname", false);
        this.canChangeNickname = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canChangeNickname", true);
        this.registrationEnabled = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.registrationEnabled", true);
        // TODO Allow to set the history strategy from the configuration form?
        roomHistory = new MUCRoomHistory(this, new HistoryStrategy(getJID(), mucService.getHistoryStrategy()));
        this.iqOwnerHandler = new IQOwnerHandler(this);
        this.iqAdminHandler = new IQAdminHandler(this);
        this.fmucHandler = new FMUCHandler(this);
        // No one can join the room except the room's owner
        this.lockedTime = startTime;
        // Set the default roles for which presence is broadcast
        rolesToBroadcastPresence.add(Role.moderator);
        rolesToBroadcastPresence.add(Role.participant);
        rolesToBroadcastPresence.add(Role.visitor);
        selfOccupantData = MUCOccupant.createRoomSelfRepresentation(this);
    }

    /**
     * Get the name of this room.
     *
     * @return The name for this room
     */
    public String getName() {
        return name;
    }

    /**
     * Get the full JID of this room.
     *
     * @return the JID for this room.
     */
    public JID getJID() {
        return new JID(getName(), getMUCService().getServiceDomain(), null);
    }

    /**
     * Get the multi-user chat service the room is attached to.
     *
     * @return the MultiUserChatService instance that the room is attached to.
     */
    public MultiUserChatService getMUCService() {
        return mucService;
    }

    /**
     * Sets the multi-user chat service the room is attached to.
     *
     * @param service The MultiUserChatService that the room is attached to (cannot be {@code null}).
     */
    public void setMUCService( MultiUserChatService service) {
        this.mucService = service;
    }

    /**
     * Obtain a unique numerical id for this room. Useful for storing rooms in databases. If the
     * room is persistent or is logging the conversation then the returned ID won't be -1.
     *
     * @return The unique id for this room or -1 if the room is temporary and is not logging the
     * conversation.
     */
    public long getID() {
        if (isPersistent() || isLogEnabled()) {
            if (roomID == -1) {
                roomID = SequenceManager.nextID(JiveConstants.MUC_ROOM);
            }
        }
        return roomID;
    }

    /**
     * Sets a new room ID if the room has just been saved to the database or sets the saved ID of
     * the room in the database while loading the room.
     *
     * @param roomID the saved ID of the room in the DB or a new one if the room is being saved to the DB.
     */
    public void setID(long roomID) {
        this.roomID = roomID;
    }

    /**
     * Returns the date when the room was created.
     *
     * @return the date when the room was created.
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the date when the room was created.
     *
     * @param creationDate the date when the room was created (cannot be {@code null}).
     */
    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    /**
     * Returns the last date when the room's configuration was modified. If the room's configuration
     * was never modified then the creation date will be returned.
     *
     * @return the last date when the room's configuration was modified.
     */
    public Date getModificationDate() {
        return modificationDate;
    }

    /**
     * Sets the last date when the room's configuration was modified. If the room's configuration
     * was never modified then the initial value will be the same as the creation date.
     *
     * @param modificationDate the last date when the room's configuration was modified (cannot be {@code null}).
     */
    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    /**
     * Sets the date when the last occupant left the room. A null value means that there are
     * occupants in the room at the moment.
     *
     * @param emptyDate the date when the last occupant left the room or null if there are occupants in the room (can be {@code null}).
     */
    public void setEmptyDate(Date emptyDate) {
        // Do nothing if old value is same as new value
        if (this.emptyDate == emptyDate) {
            return;
        }
        this.emptyDate = emptyDate;
        MUCPersistenceManager.updateRoomEmptyDate(this);
    }

    /**
     * Returns the date when the last occupant left the room. A null value means that there are
     * occupants in the room at the moment.
     *
     * @return the date when the last occupant left the room or null if there are occupants in the
     *         room at the moment.
     */
    public Date getEmptyDate() {
        return this.emptyDate;
    }

    /**
     * Obtain the occupant data representing the chat service itself (mainly for addressing messages and presence).
     *
     * @return The representation of the chat room itself
     */
    public MUCOccupant getSelfRepresentation() {
        return selfOccupantData;
    }

    /**
     * Obtain the roles of a given user by nickname. A user can be connected to a room more than once.
     *
     * @param nickname The nickname of the user you'd like to obtain (cannot be {@code null})
     * @return The user's role in the room
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    public List<MUCOccupant> getOccupantsByNickname(String nickname) throws UserNotFoundException {
        if (nickname == null) {
            throw new UserNotFoundException();
        }

        final List<MUCOccupant> filteredOccupants = occupants.stream()
            .filter(occupant -> occupant.getNickname().equalsIgnoreCase(nickname))
            .collect(Collectors.toList());

        if (filteredOccupants.isEmpty()) {
            throw new UserNotFoundException("Unable to find occupant with nickname '" + nickname + "' in room '" + name + "'");
        }
        return filteredOccupants;
    }

    /**
     * Obtain the roles of a given user in the room by his bare JID. A user can have several roles,
     * one for each client resource from which the user has joined the room.
     *
     * @param userAddress The (real) JID of the user you'd like to obtain.
     * @return The user's roles in the room
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    public List<MUCOccupant> getOccupantsByBareJID(@Nonnull final JID userAddress) throws UserNotFoundException
    {
        final List<MUCOccupant> filteredOccupants = occupants.stream()
            .filter(occupant -> occupant.getUserAddress().asBareJID().equals(userAddress.asBareJID()))
            .collect(Collectors.toList());

        if (filteredOccupants.isEmpty()) {
            throw new UserNotFoundException();
        }

        return Collections.unmodifiableList(filteredOccupants);
    }

    /**
     * Returns the occupant data of a given user in the room by his full JID or {@code null}
     * if no occupant data was found for the specified user.
     *
     * @param userAddress The full JID of the user you'd like to obtain.
     * @return The user's occupant data in the room or null if not found.
     */
    public MUCOccupant getOccupantByFullJID(@Nonnull final JID userAddress)
    {
        final List<MUCOccupant> filteredOccupants = occupants.stream()
            .filter(occupant -> occupant.getUserAddress().equals(userAddress))
            .collect(Collectors.toList());

        switch (filteredOccupants.size()) {
            case 0: return null;
            default:
                Log.warn("Room '{}' has more than one occupant with full JID '{}'!", getJID(), userAddress);
                // Intended fall-through: return the first one.
            case 1: return filteredOccupants.iterator().next();
        }
    }

    /**
     * Obtain the roles of all users in the chatroom.
     *
     * @return a collection with all users in the chatroom
     */
    public Collection<MUCOccupant> getOccupants() {
        return Collections.unmodifiableCollection(occupants);
    }

    /**
     * Returns the number of occupants in the chatroom at the moment.
     *
     * @return int the number of occupants in the chatroom at the moment.
     */
    public int getOccupantsCount() {
        return occupants.size();
    }

    /**
     * Determine if a given nickname is taken.
     *
     * @param nickname The nickname of the user you'd like to obtain  (cannot be {@code null}).
     * @return True if a nickname is taken
     */
    public boolean hasOccupant(String nickname)
    {
        return occupants.stream()
            .anyMatch(occupant -> occupant.getNickname().equalsIgnoreCase(nickname));
    }

    public boolean hasOccupant(final JID jid)
    {
        return occupants.stream()
            .anyMatch(occupant -> occupant.getUserAddress().equals(jid) || occupant.getUserAddress().asBareJID().equals(jid));
    }

    /**
     * Attempts to find a nickname for a user.
     *
     * This method will first check if there's an occupant that matches the provided user address. If none is found,
     * then any reserved nickname for the user address is returned.
     *
     * When no nickname can be found, null is returned.
     *
     * @param userAddress The (real) address for which to return a nickname
     * @return a nickname for the user, or null.
     */
    public String findNickname(@Nullable final JID userAddress)
    {
        if (userAddress == null) {
            return null;
        }

        final MUCOccupant fullJidOccupant = occupants.stream().filter(occupant -> occupant.getUserAddress().equals(userAddress)).findAny().orElse(null);
        if (fullJidOccupant != null) {
            return fullJidOccupant.getNickname();
        }

        final MUCOccupant bareJidOccupant = occupants.stream().filter(occupant -> occupant.getUserAddress().asBareJID().equals(userAddress)).findAny().orElse(null);
        if (bareJidOccupant != null) {
            return bareJidOccupant.getNickname();
        }

        return getReservedNickname(userAddress);
    }

    /**
     * Returns the reserved room nickname for the bare JID or null if none.
     *
     * @param userAddress The (real) JID of the user of which you'd like to obtain his reserved nickname .
     * @return the reserved room nickname for the user, or null if none.
     */
    public String getReservedNickname(@Nonnull final JID userAddress) {
        final JID bareJID = userAddress.asBareJID();
        String answer = members.get(bareJID);
        if (answer == null || answer.trim().isEmpty()) {
            return null;
        }
        return answer;
    }

    /**
     * Returns the bare JID of the member for which a nickname is reserved. Returns null if no member registered the
     * nickname.
     *
     * @param nickname The nickname for which to look up a member.
     * @return the bare JID of the member that has registered this nickname, or null if none.
     */
    public JID getMemberForReservedNickname(@Nonnull final String nickname) {
        for (final Map.Entry<JID, String> entry : members.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(nickname)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns the affiliation state of the user in the room. Possible affiliations are
     * MUCOccupant.OWNER, MUCOccupant.ADMINISTRATOR, MUCOccupant.MEMBER, MUCOccupant.OUTCAST and MUCOccupant.NONE.
     *
     * Note: Prerequisite - A lock must already be obtained before sending this message.
     *
     * @param userAddress The (real) JID of the user of which you'd like to obtain his affiliation.
     * @return the affiliation state of the user in the room.
     */
    public Affiliation getAffiliation(@Nonnull final JID userAddress) {
        final JID bareJID = userAddress.asBareJID();

        if (mucService.isSysadmin(bareJID)) {
            // The user is a system administrator of the MUC service. Treat him as an owner, although he won't appear in the list of owners.
            Log.debug( "User '{}' is a sysadmin. Treat as owner.", userAddress);
            return Affiliation.owner;
        }

        if (owners.includes(bareJID)) {
            return Affiliation.owner;
        }
        else if (admins.includes(bareJID)) {
            return Affiliation.admin;
        }
        // explicit outcast status has higher precedence than member status
        else if (outcasts.includes(bareJID)) {
            return Affiliation.outcast;
        }
        else if (members.includesKey(bareJID)) {
            return Affiliation.member;
        }
        return Affiliation.none;
    }

    public Role getRole(@Nonnull JID userAddress)
    {
        final JID bareJID = userAddress.asBareJID();

        if (mucService.isSysadmin(bareJID)) {
            // The user is a system administrator of the MUC service. Treat him as an owner, although he won't appear in the list of owners.
            Log.debug( "User '{}' is a sysadmin. Treat as owner.", userAddress);
            return Role.moderator;
        }

        if (owners.includes(bareJID)) {
            return Role.moderator;
        }
        else if (admins.includes(bareJID)) {
            return Role.moderator;
        }
        // explicit outcast status has higher precedence than member status
        else if (outcasts.contains(bareJID)) {
            return null; // Outcasts have no role, as they're not allowed in the room.
        }
        else if (members.includesKey(bareJID)) {
            // The user is a member. Set the role and affiliation accordingly.
            return Role.participant;
        }
        else {
            return isModerated() ? Role.visitor : Role.participant;
        }
    }

    /**
     * Joins the room using the given nickname.
     *
     * @param nickname       The nickname the user wants to use in the chatroom  (cannot be {@code null}).
     * @param password       The password provided by the user to enter the chatroom or null if none.
     * @param historyRequest The amount of history that the user request or null meaning default.
     * @param realAddress    The 'real' (non-room) JID of the user that is joining (cannot be {@code null}).
     * @param presence       The presence sent by the user to join the room (cannot be {@code null}).
     * @return The occupant data created for the user.
     * @throws UnauthorizedException         If the user doesn't have permission to join the room.
     * @throws UserAlreadyExistsException    If the nickname is already taken.
     * @throws RoomLockedException           If the user is trying to join a locked room.
     * @throws ForbiddenException            If the user is an outcast.
     * @throws RegistrationRequiredException If the user is not a member of a members-only room.
     * @throws ConflictException             If another user attempts to join the room with a
     *                                       nickname reserved by the first user.
     * @throws ServiceUnavailableException   If the user cannot join the room since the max number
     *                                       of users has been reached.
     * @throws NotAcceptableException       If the registered user is trying to join with a
     *                                      nickname different from the reserved nickname.
     */
    public MUCOccupant joinRoom(@Nonnull String nickname,
                                @Nullable String password,
                                @Nullable HistoryRequest historyRequest,
                                @Nonnull JID realAddress,
                                @Nonnull Presence presence )
        throws UnauthorizedException, UserAlreadyExistsException, RoomLockedException, ForbiddenException,
        RegistrationRequiredException, ConflictException, ServiceUnavailableException, NotAcceptableException
    {
        Log.debug( "User '{}' attempts to join room '{}' using nickname '{}'.", realAddress, this.getJID(), nickname );
        MUCOccupant joiningOccupant;
        boolean clientOnlyJoin; // A "client only join" here is one where the client is already joined, but has re-joined.

        synchronized (this) {
            // Determine the corresponding role based on the user's affiliation
            final JID bareJID = realAddress.asBareJID();
            Role role = getRole( bareJID );
            Affiliation affiliation = getAffiliation( bareJID );
            Log.debug( "User '{}' role and affiliation in room '{} are determined to be: {}, {}", realAddress, this.getJID(), role, affiliation );

            // Verify that the attempt meets all preconditions for joining the room.
            checkJoinRoomPreconditions( realAddress, nickname, affiliation, password, presence );

            // Is this client already joined with this nickname?
            clientOnlyJoin = alreadyJoinedWithThisNick( realAddress, nickname );

            // TODO up to this point, room state has not been modified, even though a write-lock has been acquired. Can we optimize concurrency by locking with only a read-lock up until here?
            if (!clientOnlyJoin)
            {
                Log.debug( "Adding user '{}' as an occupant of room '{}' using nickname '{}'.", realAddress, this.getJID(), nickname );

                // Create a new occupant for this user in this room.
                joiningOccupant = new MUCOccupant(this, nickname, role, affiliation, realAddress, presence);

                // See if we need to join a federated room. Note that this can be blocking!
                final Future<?> join = fmucHandler.join(joiningOccupant);
                try
                {
                    // FIXME make this properly asynchronous, instead of blocking the thread!
                    join.get(5, TimeUnit.MINUTES);
                }
                catch ( InterruptedException | ExecutionException | TimeoutException e )
                {
                    Log.error( "An exception occurred while processing FMUC join for user '{}' in room '{}'", joiningOccupant.getUserAddress(), this.getJID(), e);
                }

                addOccupant(joiningOccupant);

            } else {
                // Grab the existing one.
                Log.debug( "Skip adding user '{}' as an occupant of room '{}' using nickname '{}', as it already is. Updating occupancy with its latest presence information.", realAddress, this.getJID(), nickname );
                joiningOccupant = getOccupantByFullJID(realAddress);
                joiningOccupant.setPresence(presence); // OF-1581: Use the latest presence information.
            }
        }

        // Exchange initial presence information between occupants of the room.
        sendInitialPresencesToNewOccupant( joiningOccupant );

        // OF-2042: XEP dictates an order of events. Wait for the presence exchange to finish, before progressing.
        final CompletableFuture<Void> future = sendInitialPresenceToExistingOccupants(joiningOccupant);
        try {
            final Duration timeout = SELF_PRESENCE_TIMEOUT.getValue();
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch ( InterruptedException e ) {
            Log.debug( "Presence broadcast has been interrupted before it completed. Will continue to process the join of occupant '{}' to room '{}' as if it has.", joiningOccupant.getUserAddress(), this.getJID(), e);
        } catch ( TimeoutException e ) {
            Log.warn( "Presence broadcast has not yet been completed within the allocated period. Will continue to process the join of occupant '{}' to room '{}' as if it has.", joiningOccupant.getUserAddress(), this.getJID(), e);
        } catch ( ExecutionException e ) {
            Log.warn( "Presence broadcast caused an exception. Will continue to process the join of occupant '{}' to room '{}' as if it has.", joiningOccupant.getUserAddress(), this.getJID(), e);
        }

        // If the room has just been created send the "room locked until configuration is confirmed" message.
        // It is assumed that the room is new based on the fact that it's locked and that it was locked when it was created.
        final boolean isRoomNew = isLocked() && creationDate.getTime() == lockedTime;
        if (!isRoomNew && isLocked()) {
            // TODO Verify if it's right that this check occurs only _after_ a join was deemed 'successful' and initial presences have been exchanged.
            // http://xmpp.org/extensions/xep-0045.html#enter-locked
            Log.debug( "User '{}' attempts to join room '{}' that is locked (pending configuration confirmation). Sending an error.", realAddress, this.getJID() );
            final Presence presenceItemNotFound = new Presence(Presence.Type.error);
            presenceItemNotFound.setError(PacketError.Condition.item_not_found);
            presenceItemNotFound.setFrom(selfOccupantData.getOccupantJID());

            // Not needed to create a defensive copy of the stanza. It's not used anywhere else.
            joiningOccupant.send(presenceItemNotFound);
        }

        sendRoomHistoryAfterJoin( realAddress, joiningOccupant, historyRequest );
        sendRoomSubjectAfterJoin( realAddress, joiningOccupant );

        if (!clientOnlyJoin) {
            // Update the date when the last occupant left the room
            setEmptyDate(null);
        }
        return joiningOccupant;
    }

    /**
     * Sends the room history to a user that just joined the room.
     */
    private void sendRoomHistoryAfterJoin(@Nonnull final JID realAddress, @Nonnull MUCOccupant joiningOccupant, @Nullable HistoryRequest historyRequest )
    {
        if (historyRequest == null) {
            Log.trace( "Sending default room history to user '{}' that joined room '{}'.", realAddress, this.getJID() );
            final Iterator<Message> history = roomHistory.getMessageHistory();
            while (history.hasNext()) {
                // OF-2163: Prevent modifying the original history stanza (that can be retrieved by others later) by making a defensive copy.
                //          This prevents the stanzas in the room history to have a 'to' address for the last user that it was sent to.
                final Message message = history.next().createCopy();
                joiningOccupant.send(message);
            }
        } else {
            Log.trace( "Sending user-requested room history to user '{}' that joined room '{}'.", realAddress, this.getJID() );
            historyRequest.sendHistory(joiningOccupant, roomHistory);
        }
    }

    /**
     * Sends the room subject to a user that just joined the room.
     */
    private void sendRoomSubjectAfterJoin(@Nonnull final JID realAddress, @Nonnull MUCOccupant joiningOccupant )
    {
        Log.trace( "Sending room subject to user '{}' that joined room '{}'.", realAddress, this.getJID() );

        Message roomSubject = roomHistory.getChangedSubject();
        if (roomSubject != null) {
            // OF-2163: Prevent modifying the original subject stanza (that can be retrieved by others later) by making a defensive copy.
            //          This prevents the stanza kept in memory to have the 'to' address for the last user that it was sent to.
            roomSubject.createCopy();
        } else {
            // 7.2.15 If there is no subject set, the room MUST return an empty <subject/> element.
            roomSubject = new Message();
            roomSubject.setFrom( this.getJID() );
            roomSubject.setType( Message.Type.groupchat );
            roomSubject.setID( UUID.randomUUID().toString() );
            roomSubject.getElement().addElement( "subject" );
        }
        joiningOccupant.send(roomSubject);
    }

    public boolean alreadyJoinedWithThisNick(@Nonnull final JID realJID, @Nonnull final String nickname)
    {
        return occupants.stream()
            .anyMatch(occupant -> occupant.getUserAddress().equals(realJID) && occupant.getNickname().equalsIgnoreCase(nickname));
    }

    /**
     * Checks all preconditions for joining a room. If one of them fails, an Exception is thrown.
     */
    private void checkJoinRoomPreconditions(
        @Nonnull final JID realAddress,
        @Nonnull final String nickname,
        @Nonnull final Affiliation affiliation,
        @Nullable final String password,
        @Nonnull final Presence presence)
        throws ServiceUnavailableException, RoomLockedException, UserAlreadyExistsException, UnauthorizedException, ConflictException, NotAcceptableException, ForbiddenException, RegistrationRequiredException
    {
        Log.debug( "Checking all preconditions for user '{}' to join room '{}'.", realAddress, this.getJID() );

        checkJoinRoomPreconditionDelegate( realAddress );

        // If the room has a limit of max user then check if the limit has been reached
        checkJoinRoomPreconditionMaxOccupants( realAddress );

        // If the room is locked and this user is not an owner raise a RoomLocked exception
        checkJoinRoomPreconditionLocked( realAddress );

        // Check if the nickname is already used in the room
        checkJoinRoomPreconditionNicknameInUse( realAddress, nickname );

        // If the room is password protected and the provided password is incorrect raise an
        // Unauthorized exception - unless the JID that is joining is a system admin.
        checkJoinRoomPreconditionPasswordProtection( realAddress, password );

        // If another user attempts to join the room with a nickname reserved by the first user
        // raise a ConflictException
        checkJoinRoomPreconditionNicknameReserved( realAddress, nickname );

        checkJoinRoomPreconditionRestrictedToNickname( realAddress, nickname );

        // Check if the user can join the room.
        checkJoinRoomPreconditionIsOutcast( realAddress, affiliation );

        // If the room is members-only and the user is not a member. Raise a "Registration Required" exception.
        checkJoinRoomPreconditionMemberOnly( realAddress, affiliation );

        Log.debug( "All preconditions for user '{}' to join room '{}' have been met. User can join the room.", realAddress, this.getJID() );
    }

    private void checkJoinRoomPreconditionDelegate( @Nonnull final JID realAddress ) throws UnauthorizedException
    {
        boolean canJoin = true;
        if (mucService.getMUCDelegate() != null) {
            if (!mucService.getMUCDelegate().joiningRoom(this, realAddress)) {
                // Delegate said no, reject join.
                canJoin = false;
            }
        }
        Log.trace( "{} Room join precondition 'delegate': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new UnauthorizedException();
        }
    }

    /**
     * Checks if the room has a limit of max user, then check if the limit has been reached
     *
     * @param realAddress The address of the user that attempts to join.
     * @throws ServiceUnavailableException when joining is prevented by virtue of the room having reached maximum capacity.
     */
    private void checkJoinRoomPreconditionMaxOccupants( @Nonnull final JID realAddress ) throws ServiceUnavailableException
    {
        final boolean canJoin = canJoinRoom(realAddress);
        Log.trace( "{} Room join precondition 'max occupants': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoinRoom(realAddress)) {
            throw new ServiceUnavailableException( "This room has reached its maximum number of occupants." );
        }
    }

    /**
     * Checks if the room is locked and this user is not an owner
     *
     * @param realAddress The address of the user that attempts to join.
     * @throws RoomLockedException when joining is prevented by virtue of the room being locked.
     */
    private void checkJoinRoomPreconditionLocked( @Nonnull final JID realAddress ) throws RoomLockedException
    {
        boolean canJoin = true;
        final JID bareJID = realAddress.asBareJID();
        boolean isOwner = owners.includes(bareJID);
        if (isLocked()) {
            if (!isOwner) {
                canJoin = false;
            }
        }
        Log.trace( "{} Room join precondition 'room locked': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new RoomLockedException( "This room is locked (and you are not an owner)." );
        }
    }

    /**
     * Checks if the nickname that the user attempts to use is already used by someone else in the room.
     *
     * @param realAddress The address of the user that attempts to join.
     * @param nickname The nickname that the user is attempting to use
     * @throws UserAlreadyExistsException when joining is prevented by virtue of someone else in the room using the nickname.
     */
    private void checkJoinRoomPreconditionNicknameInUse(@Nonnull final JID realAddress, @Nonnull String nickname) throws UserAlreadyExistsException
    {
        final JID bareJID = realAddress.asBareJID();
        final boolean canJoin = occupants.stream().noneMatch(occupant -> !occupant.getUserAddress().asBareJID().equals(bareJID) && occupant.getNickname().equalsIgnoreCase(nickname));
        Log.trace( "{} Room join precondition 'nickname in use': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new UserAlreadyExistsException( "Someone else in the room uses the nickname that you want to use." );
        }
    }

    /**
     * Checks if the user provided the correct password, if applicable.
     *
     * @param realAddress The address of the user that attempts to join.
     * @throws UnauthorizedException when joining is prevented by virtue of password protection.
     */
    private void checkJoinRoomPreconditionPasswordProtection(@Nonnull final JID realAddress, @Nullable String providedPassword ) throws UnauthorizedException
    {
        boolean canJoin = true;
        final JID bareJID = realAddress.asBareJID();
        if (isPasswordProtected()) {
            final boolean isCorrectPassword = (providedPassword != null && providedPassword.equals(getPassword()));
            final boolean isSysadmin = mucService.isSysadmin(bareJID);
            final boolean requirePassword = !isSysadmin || mucService.isPasswordRequiredForSysadminsToJoinRoom();
            if (!isCorrectPassword && requirePassword ) {
                canJoin = false;
            }
        }
        Log.trace( "{} Room join precondition 'password protection': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new UnauthorizedException( "You did not supply the correct password needed to join this room." );
        }
    }

    /**
     * Checks if the nickname that the user attempts to use has been reserved by a(nother) member of the room.
     *
     * @param realAddress The address of the user that attempts to join.
     * @param nickname The nickname that the user is attempting to use
     * @throws ConflictException when joining is prevented by virtue of someone else in the room having reserved the nickname.
     */
    private void checkJoinRoomPreconditionNicknameReserved(@Nonnull final JID realAddress, @Nonnull final String nickname ) throws ConflictException
    {
        final JID bareJID = realAddress.asBareJID();
        final JID bareMemberJid = getMemberForReservedNickname(nickname);
        final boolean canJoin = bareMemberJid == null || bareMemberJid.equals(bareJID);
        Log.trace( "{} Room join precondition 'nickname reserved': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new ConflictException( "Someone else in the room has reserved the nickname that you want to use." );
        }
    }

    /**
     * Checks, when joins are restricted to reserved nicknames, if the nickname that the user attempts to use is the
     * nickname that has been reserved by that room.
     *
     * @param realAddress The address of the user that attempts to join.
     * @param nickname The nickname that the user is attempting to use
     * @throws NotAcceptableException when joining is prevented by virtue of using an incorrect nickname.
     */
    private void checkJoinRoomPreconditionRestrictedToNickname(@Nonnull final JID realAddress, @Nonnull final String nickname ) throws NotAcceptableException
    {
        boolean canJoin = true;
        String reservedNickname = null;
        final JID bareJID = realAddress.asBareJID();
        if (isLoginRestrictedToNickname()) {
            reservedNickname = members.get(bareJID);
            if (reservedNickname != null && !nickname.toLowerCase().equals(reservedNickname)) {
                canJoin = false;
            }
        }

        Log.trace( "{} Room join precondition 'restricted to nickname': User '{}' {} join room {}. Reserved nickname: '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID(), reservedNickname );
        if (!canJoin) {
            throw new NotAcceptableException( "This room is configured to restrict joins to reserved nicknames. The nickname that you supplied was not the nickname that you reserved for this room, which is: " + reservedNickname );
        }
    }

    /**
     * Checks if the person that attempts to join has been banned from the room.
     *
     * @param realAddress The address of the user that attempts to join.
     * @throws ForbiddenException when joining is prevented by virtue of the user being banned.
     */
    private void checkJoinRoomPreconditionIsOutcast(@Nonnull final JID realAddress, @Nonnull final Affiliation affiliation ) throws ForbiddenException
    {
        boolean canJoin = affiliation != Affiliation.outcast;

        Log.trace( "{} Room join precondition 'is outcast': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new ForbiddenException( "You have been banned (marked as 'outcast') from this room." );
        }
    }

    /**
     * Checks if the person that attempts to join is a member of a member-only room.
     *
     * @param realAddress The address of the user that attempts to join.
     * @throws RegistrationRequiredException when joining is prevented by virtue of the user joining a member-only room without being a member.
     */
    private void checkJoinRoomPreconditionMemberOnly(@Nonnull final JID realAddress, @Nonnull final Affiliation affiliation ) throws RegistrationRequiredException
    {
        boolean canJoin = !isMembersOnly() || Arrays.asList( Affiliation.admin, Affiliation.owner, Affiliation.member ).contains( affiliation );

        Log.trace( "{} Room join precondition 'member-only': User '{}' {} join room '{}'.", canJoin ? "PASS" : "FAIL", realAddress, canJoin ? "can" : "cannot", this.getJID() );
        if (!canJoin) {
            throw new RegistrationRequiredException( "This room is member-only, but you are not a member." );
        }
    }

    /**
     * Can a user join this room
     *
     * @param realAddress The address of the user that attempts to join.
     * @return indication if the user can join
     */
    private boolean canJoinRoom(@Nonnull final JID realAddress){
        boolean isOwner = owners.includes(realAddress.asBareJID());
        boolean isAdmin = admins.includes(realAddress.asBareJID());
        return (!isDestroyed && (!hasOccupancyLimit() || isAdmin || isOwner || (getOccupantsCount() < getMaxUsers())));
    }

    /**
     * Does this room have an occupancy limit?
     *
     * @return boolean
     */
    private boolean hasOccupancyLimit(){
        return getMaxUsers() != 0;
    }

    /**
     * Sends presence of existing occupants to new occupant.
     *
     * @param joinedOccupant the occupant data of the new occupant in the room.
     */
    void sendInitialPresencesToNewOccupant(MUCOccupant joinedOccupant) {
        if (!JOIN_PRESENCE_ENABLE.getValue()) {
            Log.debug( "Skip exchanging presence between existing occupants of room '{}' and new occupant '{}' as it is disabled by configuration.", this.getJID(), joinedOccupant.getUserAddress() );
            return;
        }

        Log.trace( "Send presence of existing occupants of room '{}' to new occupant '{}'.", this.getJID(), joinedOccupant.getUserAddress() );
        for ( final MUCOccupant occupant : getOccupants() ) {
            if (occupant == joinedOccupant) {
                continue;
            }

            // Skip to the next occupant if we cannot send presence of this occupant
            if (!canBroadcastPresence(occupant.getRole())) {
                continue;
            }

            final Presence occupantPresence = occupant.getPresence(); // This returns a copy. Modifications will not be applied to the original.
            if (!canAnyoneDiscoverJID() && Role.moderator != joinedOccupant.getRole()) {
                // Don't include the occupant's JID if the room is semi-anon and the new occupant is not a moderator
                final Element frag = occupantPresence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                frag.element("item").addAttribute("jid", null);
            }
            joinedOccupant.send(occupantPresence);
        }
    }

    /**
     * Adds an occupant to all the internal occupants collections.
     *
     * @param occupant the occupant to add.
     */
    public void addOccupant(@Nonnull final MUCOccupant occupant)
    {
        if (occupants.contains(occupant)) {
            // Ignore a data consistency problem. This indicates that a bug exists somewhere, so log it verbosely.
            Log.warn("Not re-adding an occupant {} that already exists in room {}!", occupant, this.getJID(), new IllegalStateException("Duplicate occupant: " + occupant));
            return;
        }

        Log.trace( "Add occupant to room {}: {}", this.getJID(), occupant);
        occupants.add(occupant);

        // Fire event that occupant joined the room.
        MUCEventDispatcher.occupantJoined(occupant.getOccupantJID().asBareJID(), occupant.getUserAddress(), occupant.getNickname());
    }

    /**
     * Sends presence of a leaving occupant to applicable occupants of the room that is being left.
     *
     * @param leavingOccupant the occupant that is leaving.
     */
    public CompletableFuture<Void> sendLeavePresenceToExistingOccupants(MUCOccupant leavingOccupant) {
        // Send the presence of this new occupant to existing occupants
        Log.trace( "Send presence of leaving occupant '{}' to existing occupants of room '{}'.", leavingOccupant.getUserAddress(), this.getJID() );
        try {
            final Presence presence = leavingOccupant.getPresence(); // This returns a copy. Modifications will not be applied to the original.
            presence.setType(Presence.Type.unavailable);
            presence.setStatus(null);
            // Change (or add) presence information about roles and affiliations
            Element childElement = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
            if (childElement == null) {
                childElement = presence.addChildElement("x", "http://jabber.org/protocol/muc#user");
            }
            Element item = childElement.element("item");
            if (item == null) {
                item = childElement.addElement("item");
            }
            item.addAttribute("role", "none");

            // Check to see if the user's original occupant data is one we should broadcast a leave packet for,
            // or if the leaving occupant is using multi-session nick (in which case _only_ the leaving client should be informed).
            if(!canBroadcastPresence(leavingOccupant.getRole()) || getOccupantsByNickname(leavingOccupant.getNickname()).size() > 1){
                // Inform the leaving user that he/she has left the room
                leavingOccupant.send(createSelfPresenceCopy(presence, false));
                return CompletableFuture.completedFuture(null);
            }
            else {
                // Inform all room occupants that the user has left the room
                if (JOIN_PRESENCE_ENABLE.getValue()) {
                    return broadcastPresence(presence, false, leavingOccupant);
                }
            }
        }
        catch (Exception e) {
            Log.error( "An exception occurred while sending leave presence of occupant '{}' to the other occupants of room: '{}'.", leavingOccupant.getUserAddress(), this.getJID(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Remove a member from the chat room.
     *
     * @param leavingOccupant The occupant data that the user that left the room has prior to the user leaving.
     */
    public void leaveRoom(@Nonnull final MUCOccupant leavingOccupant) {
        sendLeavePresenceToExistingOccupants(leavingOccupant)
            // DO NOT use 'thenRunAsync', as that will cause issues with clustering (it uses an executor that overrides the contextClassLoader, causing ClassNotFound exceptions in ClusterExternalizableUtil).
            .thenRun( () -> {
                // Remove occupant from room and destroy room if empty and not persistent
                removeOccupant(leavingOccupant);

                // TODO Implement this: If the room owner becomes unavailable for any reason before
                // submitting the form (e.g., a lost connection), the service will receive a presence
                // stanza of type "unavailable" from the owner to the room@service/nick or room@service
                // (or both). The service MUST then destroy the room, sending a presence stanza of type
                // "unavailable" from the room to the owner including a <destroy/> element and reason
                // (if provided) as defined under the "Destroying a Room" use case.

                // Remove the room from the service only if there are no more occupants and the room is
                // not persistent
                if (getOccupants().isEmpty()) {
                    if (!isPersistent()) {
                        endTime = System.currentTimeMillis();
                        destroyRoom(null, "Removal of empty, non-persistent room.");
                    }
                    // Update the date when the last occupant left the room
                    setEmptyDate(new Date());
                }
            });
    }

    /**
     * Sends presence of new occupant to existing occupants.
     *
     * @param joinedOccupant the occupant data of the new user in the room.
     */
    public CompletableFuture<Void> sendInitialPresenceToExistingOccupants(MUCOccupant joinedOccupant) {
        // Send the presence of this new occupant to existing occupants
        Log.trace( "Send presence of new occupant '{}' to existing occupants of room '{}'.", joinedOccupant.getUserAddress(), this.getJID() );
        try {
            final Presence joinPresence = joinedOccupant.getPresence();
            return broadcastPresence(joinPresence, true, joinedOccupant);
        } catch (Exception e) {
            Log.error( "An exception occurred while sending initial presence of new occupant '{}' to the existing occupants of room: '{}'", joinedOccupant.getUserAddress(), this.getJID(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Removes the occupant from all the internal occupants collections. The occupant data will
     * also be removed from the user's occupant-data collection.
     *
     * @param occupant the occupant to remove.
     */
    public void removeOccupant(@Nonnull final MUCOccupant occupant) {
        Log.trace( "Remove occupant from room {}: {}", this.getJID(), occupant );
        occupants.remove(occupant);
        MUCEventDispatcher.occupantLeft(occupant.getOccupantJID(), occupant.getUserAddress(), occupant.getNickname());
    }

    /**
     * Destroys the room. Each occupant will be removed and will receive a presence stanza of type
     * "unavailable" whose "from" attribute will be the occupant's nickname that the user knows he
     * or she has been removed from the room.
     *
     * @param alternateJID an optional alternate JID. Commonly used to provide a replacement room. (can be {@code null})
     * @param reason an optional reason why the room was destroyed (can be {@code null}).
     */
    public void destroyRoom(JID alternateJID, String reason) {
        destroyRoom(alternateJID,  null, reason);
    }

    /**
     * Destroys the room. Each occupant will be removed and will receive a presence stanza of type
     * "unavailable" whose "from" attribute will be the occupant's nickname that the user knows he
     * or she has been removed from the room.
     *
     * @param alternateJID an optional alternate JID. Commonly used to provide a replacement room. (can be {@code null})
     * @param password an optional password to be used for accessing the replacement room (can be {@code null}).
     * @param reason an optional reason why the room was destroyed (can be {@code null}).
     */
    public void destroyRoom(JID alternateJID, String password, String reason) {
        Collection<MUCOccupant> removedOccupants = new CopyOnWriteArrayList<>();

        fmucHandler.stop();

        // Remove each occupant from a copy of the list of occupants (to prevent ConcurrentModificationException).
        for (MUCOccupant leavingOccupant : getOccupants()) {
            if (leavingOccupant != null) {
                // Add the removed occupant to the list of removed occupants. We are keeping a
                // list of removed occupants to process later outside the lock.
                removedOccupants.add(leavingOccupant);
            }
        }
        endTime = System.currentTimeMillis();
        // Set that the room has been destroyed
        isDestroyed = true;
        // Removes the room from the list of rooms hosted in the service
        mucService.removeChatRoom(name);

        // Send an unavailable presence to each removed occupant
        for (MUCOccupant removedOccupant : removedOccupants) {
            try {
                // Send a presence stanza of type "unavailable" to the occupant
                final Presence presence = createPresence(Presence.Type.unavailable);
                presence.setFrom(removedOccupant.getOccupantJID());

                // A fragment containing the x-extension for room destruction.
                final Element fragment = presence.addChildElement("x","http://jabber.org/protocol/muc#user");
                final Element item = fragment.addElement("item");
                item.addAttribute("affiliation", "none");
                item.addAttribute("role", "none");
                final Element destroy = fragment.addElement("destroy");
                if (alternateJID != null) {
                    destroy.addAttribute("jid", alternateJID.toString());
                }
                if (password != null) {
                    destroy.addElement("password").setText(password);
                }
                if (reason != null && !reason.isBlank()) {
                    destroy.addElement("reason").setText(reason.trim());
                }

                // Not needed to create a defensive copy of the stanza. It's not used anywhere else.
                removedOccupant.send(presence);
                removeOccupant(removedOccupant);
            }
            catch (Exception e) {
                Log.error("An exception occurred while tyring to inform occupant '{}' that room '{}' was destroyed.", removedOccupant, name, e);
            }
        }

        // Remove the room from the DB if the room was persistent
        MUCPersistenceManager.deleteFromDB(this);
        // Remove the history of the room from memory (preventing it to pop up in a new room by the same name).
        roomHistory.purge();
        // Fire event that the room has been destroyed
        MUCEventDispatcher.roomDestroyed(getSelfRepresentation().getOccupantJID());
    }

    /**
     * Create a new presence in this room for the given role.
     *
     * @param presenceType Type of presence to create (cannot be {@code null}).
     * @return The new presence
     */
    public Presence createPresence(Presence.Type presenceType) {
        Presence presence = new Presence();
        presence.setType(presenceType);
        presence.setFrom(selfOccupantData.getOccupantJID());
        return presence;
    }

    /**
     * Broadcast a given message to all members of this chat room. The sender is always set to
     * be the chatroom.
     *
     * @param msg The message to broadcast (cannot be {@code null})
     */
    public void serverBroadcast(String msg) {
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        message.setBody(msg);
        message.setFrom(selfOccupantData.getOccupantJID());
        broadcast(message, selfOccupantData);
    }

    /**
     * Sends a message to the all the occupants. In a moderated room, this privilege is restricted
     * to occupants with a role of participant or higher. In an unmoderated room, any occupant can
     * send a message to all other occupants.
     *
     * @param message The message to send (cannot be {@code null}).
     * @param sender the occupant data of the user that is trying to send a public message (cannot be {@code null}).
     * @throws ForbiddenException If the user is not allowed to send a public message (i.e. does not
     *             have voice in the room).
     */
    public void sendPublicMessage(Message message, MUCOccupant sender) throws ForbiddenException {
        // Check that if the room is moderated then the sender of the message has to have voice
        if (isModerated() && sender.getRole().compareTo(Role.participant) > 0) {
            throw new ForbiddenException();
        }
        // Send the message to all occupants
        message.setFrom(sender.getOccupantJID());
        if (canAnyoneDiscoverJID) {
            addRealJidToMessage(message, sender);
        }
        send(message, sender);
        // Fire event that message was received by the room
        MUCEventDispatcher.messageReceived(getSelfRepresentation().getOccupantJID(), sender.getUserAddress(),
            sender.getNickname(), message);
    }

    /**
     * Sends a private packet to a selected occupant. The packet can be a Message for private
     * conversation between room occupants or IQ packets when an occupant wants to send IQ packets
     * to other room occupants.
     *
     * If the system property xmpp.muc.allowpm.blockall is set to true, this will block all private packets
     * However, if this property is false (by default) then it will allow non-message private packets.
     *
     * If the system property xmpp.muc.vcard.enabled is set to true, then IQ requests that are VCard requests for
     * occupants of the MUC room are processed differently to allow for VCards to be requested from the home server of
     * the occupant. See {@link MultiUserChatServiceImpl#processVCardResponse(IQ)} for details.
     *
     * @param packet The packet to send.
     * @param sender the occupant data of the user that is trying to send a public message.
     * @throws NotFoundException If the user is sending a packet to a room JID that does not exist.
     * @throws ForbiddenException If a user of this role is not permitted to send private messages in this room.
     */
    public void sendPrivatePacket(@Nonnull final Packet packet, final @Nullable MUCOccupant sender) throws NotFoundException, ForbiddenException, NotAcceptableException
    {
        if (sender == null) {
            throw new NotAcceptableException();
        }

        if (packet instanceof Message || ALLOWPM_BLOCKALL.getValue()){
            //If the packet is a message, check that the user has permissions to send
            switch (sender.getRole()) { // intended fall-through
                case none:
                    throw new NotAcceptableException();
                default:
                case visitor:
                    if (canSendPrivateMessage().equals( "participants" )) throw new ForbiddenException();
                case participant:
                    if (canSendPrivateMessage().equals( "moderators" )) throw new ForbiddenException();
                case moderator:
                    if (canSendPrivateMessage().equals( "none" )) throw new ForbiddenException();
            }
        }

        String resource = packet.getTo().getResource();

        List<MUCOccupant> occupants;
        try {
            occupants = getOccupantsByNickname(resource.toLowerCase());
        } catch (UserNotFoundException e) {
            throw new NotFoundException();
        }

        // OF-2163: Prevent modifying the original stanza (that can be used by unrelated code, after this method returns) by making a defensive copy.
        final Packet stanza = packet.createCopy();
        if (canAnyoneDiscoverJID && stanza instanceof Message) {
            addRealJidToMessage((Message)stanza, sender);
        }
        stanza.setFrom(sender.getOccupantJID());

        // Sending the stanza will modify it. Make sure that the event listeners that are triggered after sending
        // the stanza don't get the 'real' address from the recipient.
        final Packet immutable = stanza.createCopy();

        // If this is a VCard request, then the intended recipient's home server (possibly not this instance of Openfire) needs
        // to answer on behalf of the intended recipient. See {@link MultiUserChatServiceImpl#processVCardResponse(IQ)} for details.
        if (IQMUCvCardHandler.PROPERTY_ENABLED.getValue() && stanza instanceof IQ && ((IQ)stanza).getType() == IQ.Type.get) {
            final IQ request = (IQ)stanza;
            if (IQMUCvCardHandler.NAMESPACE.equals(request.getChildElement().getNamespaceURI())) {
                // Build request from the requestor's room nickname (to have the response be delivered through the MUC room) to the home user of the intended recipient.
                final JID bareJID = occupants.get(0).getUserAddress().asBareJID();
                Log.debug("Sending VCard request to occupant {}'s real JID ('{}') to answer VCard request of {}", resource, bareJID, request.getFrom());
                request.setTo(bareJID);

                XMPPServer.getInstance().getPacketRouter().route(request);
                return;
            }
        }

        // Forward it to each occupant.
        for (final MUCOccupant occupant : occupants) {
            occupant.send(stanza); // Use the stanza copy to send data. The 'to' address of this object will be changed by sending it.
            if (stanza instanceof Message) {
                // Use an unmodified copy of the stanza (with the original 'to' address) when invoking event listeners (OF-2163)
                MUCEventDispatcher.privateMessageRecieved(occupant.getUserAddress(), sender.getUserAddress(), (Message) immutable);
            }
        }
    }

    /**
     * Sends a packet to the occupants of the room.
     *
     * The second argument defines the sender/originator of the stanza. Typically, this is the same entity that's also
     * the 'subject' of the stanza (eg: someone that changed its presence or nickname). It is important to realize that
     * this needs to be the case. When, for example, an occupant is made a moderator, the 'sender' typically is the
     * entity that granted the role to another entity. It is also possible for the sender to be a reflection of the room
     * itself. This scenario typically occurs when the sender can't be identified as an occupant of the room, such as,
     * for example, changes applied through the Openfire admin console.
     *
     * @param packet The packet to send
     * @param sender Representation of the entity that sent the stanza.
     */
    public void send(@Nonnull Packet packet, @Nonnull MUCOccupant sender) {
        if (packet instanceof Message) {
            broadcast((Message)packet, sender);
        }
        else if (packet instanceof Presence) {
            broadcastPresence((Presence)packet, false, sender);
        }
        else if (packet instanceof IQ) {
            IQ reply = IQ.createResultIQ((IQ) packet);
            reply.setChildElement(((IQ) packet).getChildElement());
            reply.setError(PacketError.Condition.bad_request);
            XMPPServer.getInstance().getPacketRouter().route(reply);
        }
    }

    /**
     * Broadcasts the specified presence to all room occupants. If the presence belongs to a
     * user whose role cannot be broadcast then the presence will only be sent to the presence's
     * user. On the other hand, the JID of the user that sent the presence won't be included if the
     * room is semi-anon and the target occupant is not a moderator.
     *
     * @param presence the presence to broadcast.
     * @param isJoinPresence If the presence is sent in the context of joining the room.
     * @param sender The occupant that initiated the presence broadcast
     */
    private CompletableFuture<Void> broadcastPresence( Presence presence, boolean isJoinPresence, @Nonnull MUCOccupant sender) {
        if (presence == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (!presence.getFrom().asBareJID().equals(this.getJID())) {
            // At this point, the 'from' address of the to-be broadcast stanza can be expected to be the role-address
            // of the subject, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcast presence stanza's 'from' JID " + presence.getFrom() + " does not match room JID: " + this.getJID());
        }

        // Create a defensive copy, to prevent modifications to leak back to the invoker.
        final Presence stanza = presence.createCopy();

        // Some clients send a presence update to the room, rather than to their own nickname.
        if ( JiveGlobals.getBooleanProperty("xmpp.muc.presence.overwrite-to-room", true) && stanza.getTo() != null && stanza.getTo().getResource() == null && sender.getOccupantJID() != null) {
            stanza.setTo( sender.getOccupantJID() );
        }

        if (!canBroadcastPresence(sender.getRole())) {
            // Just send the presence to the sender of the presence
            final Presence selfPresence = createSelfPresenceCopy(stanza, isJoinPresence);
            sender.send(selfPresence);
            return CompletableFuture.completedFuture(null);
        }

        // If FMUC is active, propagate the presence through FMUC first. Note that when a master-slave mode is active,
        // we need to wait for an echo back, before the message can be broadcast locally. The 'propagate' method will
        // return a CompletableFuture object that is completed as soon as processing can continue.
        return fmucHandler.propagate(stanza, sender)
            // DO NOT use 'thenRunAsync', as that will cause issues with clustering (it uses an executor that overrides the contextClassLoader, causing ClassNotFound exceptions in ClusterExternalizableUtil).
            .thenRun(() -> broadcast(stanza, isJoinPresence));
    }

    /**
     * Broadcasts the presence stanza as captured by the argument to all occupants that are local to the local domain
     * (in other words, it excludes occupants that are connected via FMUC).
     *
     * @param presence The presence stanza
     * @param isJoinPresence If the presence is sent in the context of joining the room.
     */
    public void broadcast(final @Nonnull Presence presence, final boolean isJoinPresence)
    {
        Log.debug("Broadcasting presence update in room {} for occupant {}", this.getName(), presence.getFrom() );

        if (!presence.getFrom().asBareJID().equals(this.getJID())) {
            // At this point, the 'from' address of the to-be broadcast stanza can be expected to be the role-address
            // of the subject, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcast presence stanza's 'from' JID " + presence.getFrom() + " does not match room JID: " + this.getJID());
        }

        // Three distinct flavors of the presence stanzas can be sent:
        // 1. The original stanza (that includes the real JID of the user), usable when the room is not semi-anon or when the occupant is a moderator.
        // 2. One that does not include the real JID of the user (if the room is semi-anon and the occupant isn't a moderator)
        // 3. One that is reflected to the joining user (this stanza has additional status codes, signalling 'self-presence')
        final Presence nonAnonPresence = presence.createCopy(); // create a copy, as the 'to' will be overwritten when dispatched!
        final Presence anonPresence = createAnonCopy(presence);
        final Presence selfPresence = createSelfPresenceCopy(presence, isJoinPresence);

        for (final MUCOccupant occupant : getOccupants())
        {
            try
            {
                Log.trace("Broadcasting presence update in room {} for occupant {} to occupant {}", this.getName(), presence.getFrom(), occupant );

                // Do not send broadcast presence to occupants hosted in other FMUC nodes.
                if (occupant.isRemoteFmuc()) {
                    Log.trace( "Not sending presence update of '{}' to {}: This occupant is on another FMUC node.", presence.getFrom(), occupant.getUserAddress() );
                    continue;
                }

                // Determine what stanza flavor to send to this occupant.
                final Presence toSend;
                if (occupant.getPresence().getFrom().equals(presence.getTo())) {
                    // This occupant is the subject of the stanza. Send the 'self-presence' stanza.
                    Log.trace( "Sending self-presence of '{}' to {}", presence.getFrom(), occupant.getUserAddress() );
                    toSend = selfPresence;
                } else if ( !canAnyoneDiscoverJID && Role.moderator != occupant.getRole() ) {
                    Log.trace( "Sending anonymized presence of '{}' to {}: The room is semi-anon, and this occupant is not a moderator.", presence.getFrom(), occupant.getUserAddress() );
                    toSend = anonPresence;
                } else {
                    Log.trace( "Sending presence of '{}' to {}", presence.getFrom(), occupant.getUserAddress() );
                    toSend = nonAnonPresence;
                }

                // Send stanza to this occupant.
                occupant.send(toSend);
            }
            catch ( Exception e )
            {
                Log.warn("An unexpected exception prevented a presence update from {} to be broadcast to {}.", presence.getFrom(), occupant.getUserAddress(), e);
            }
        }
    }

    /**
     * Creates a copy of the presence stanza encapsulated in the argument, that is suitable to be sent to entities that
     * have no permission to view the real JID of the sender.
     *
     * @param presence The object encapsulating the presence to be broadcast.
     * @return A copy of the stanza to be broadcast.
     */
    @Nonnull
    private Presence createAnonCopy(@Nonnull Presence presence)
    {
        final Presence result = presence.createCopy();
        final Element frag = result.getChildElement("x", "http://jabber.org/protocol/muc#user");
        frag.element("item").addAttribute("jid", null);
        return result;
    }

    /**
     * Creates a copy of the presence stanza encapsulated in the argument, that is suitable to be sent back to the
     * entity that is the subject of the presence update (a 'self-presence'). The returned stanza contains several
     * flags that indicate to the receiving client that the information relates to their user.
     *
     * @param presence The object encapsulating the presence to be broadcast.
     * @param isJoinPresence If the presence is sent in the context of joining the room.
     * @return A copy of the stanza to be broadcast.
     */
    @Nonnull
    private Presence createSelfPresenceCopy(@Nonnull final Presence presence, final boolean isJoinPresence)
    {
        final Presence result = presence.createCopy();
        Element fragSelfPresence = result.getChildElement("x", "http://jabber.org/protocol/muc#user");
        fragSelfPresence.addElement("status").addAttribute("code", "110");

        // Only in the context of entering the room status code 100, 201 and 210 should be sent.
        // http://xmpp.org/registrar/mucstatus.html
        if ( isJoinPresence )
        {
            boolean isRoomNew = isLocked() && creationDate.getTime() == lockedTime;
            if ( canAnyoneDiscoverJID() )
            {
                // XEP-0045: Example 26.
                // If the user is entering a room that is non-anonymous (i.e., which informs all occupants of each occupant's full JID as shown above),
                // the service MUST warn the user by including a status code of "100" in the initial presence that the room sends to the new occupant
                fragSelfPresence.addElement("status").addAttribute("code", "100");
            }

            if ( isLogEnabled() )
            {
                // XEP-0045 section 7.2.12: Room Logging:
                // If the user is entering a room in which the discussions are logged (...), the service (..) MUST also
                // warn the user that the discussions are logged. This is done by including a status code of "170" in
                // the initial presence that the room sends to the new occupant
                fragSelfPresence.addElement("status").addAttribute("code", "170");
            }

            if ( isRoomNew )
            {
                fragSelfPresence.addElement("status").addAttribute("code", "201");
            }
        }

        return result;
    }

    private void broadcast(@Nonnull final Message message, @Nonnull final MUCOccupant sender)
    {
        if (!message.getFrom().asBareJID().equals(this.getJID())) {
            // At this point, the 'from' address of the to-be broadcast stanza can be expected to be the role-address
            // of the subject, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcast message stanza's 'from' JID " + message.getFrom() + " does not match room JID: " + this.getJID());
        }

        // If FMUC is active, propagate the message through FMUC first. Note that when a master-slave mode is active,
        // we need to wait for an echo back, before the message can be broadcast locally. The 'propagate' method will
        // return a CompletableFuture object that is completed as soon as processing can continue.
        fmucHandler.propagate( message, sender )
            // DO NOT use 'thenRunAsync', as that will cause issues with clustering (it uses an executor that overrides the contextClassLoader, causing ClassNotFound exceptions in ClusterExternalizableUtil).
            .thenRun( () -> {
                    // Broadcast message to occupants connected to this domain.
                    broadcast(message);
                }
            );
    }

    /**
     * Broadcasts the message stanza as captured by the argument to all occupants that are local to the domain (in other
     * words, it excludes occupants that connected via FMUC).
     *
     * This method also ensures that the broadcast message is logged to persistent storage, if that feature is enabled
     * for this room
     *
     * @param message The message stanza
     */
    public void broadcast(@Nonnull final Message message)
    {
        Log.debug("Broadcasting message in room {} for occupant {}", this.getName(), message.getFrom() );

        if (!message.getFrom().asBareJID().equals(this.getJID())) {
            // At this point, the 'from' address of the to-be broadcast stanza can be expected to be the role-address
            // of the sender, or more broadly: it's bare JID representation should match that of the room. If that's not
            // the case then there's a bug in Openfire. Catch this here, as otherwise, privacy-sensitive data is leaked.
            // See: OF-2152
            throw new IllegalArgumentException("Broadcast message stanza's 'from' JID " + message.getFrom() + " does not match room JID: " + this.getJID());
        }

        // Add message to the room history
        roomHistory.addMessage(message);
        // Send message to occupants connected to this JVM

        // Create a defensive copy of the message that will be broadcast, as the broadcast will modify it ('to' addresses
        // will be changed), and it's undesirable to see these modifications in post-processing (OF-2163).
        final Message mutatingCopy = message.createCopy();
        final Collection<MUCOccupant> occupants = getOccupants();
        for (final MUCOccupant occupant : occupants) {
            try
            {
                // Do not send broadcast messages to deaf occupants or occupants hosted in other FMUC nodes.
                if ( !occupant.isVoiceOnly() && !occupant.isRemoteFmuc() )
                {
                    occupant.send( mutatingCopy );
                }
            }
            catch ( Exception e )
            {
                Log.warn("An unexpected exception prevented a message from {} to be broadcast to {}.", message.getFrom(), occupant.getUserAddress(), e);
            }
        }
        if (isLogEnabled()) {
            JID senderAddress = getSelfRepresentation().getOccupantJID(); // default to the room being the sender of the message.

            // convert the MUC nickname/role JID back into a real user JID
            if (message.getFrom() != null && message.getFrom().getResource() != null) {
                try {
                    // get the first MUCOccupant for the sender
                    senderAddress = getOccupantsByNickname(message.getFrom().getResource()).get(0).getUserAddress();
                } catch (UserNotFoundException e) {
                    // The room itself is sending the message
                    senderAddress = getSelfRepresentation().getOccupantJID();
                }
            }
            // Log the conversation
            mucService.logConversation(this, message, senderAddress);
        }
        mucService.messageBroadcastedTo(occupants.size());
    }

    /**
     * Based on XEP-0045, section 7.2.13:
     * If the room is non-anonymous, the service MAY include an
     * Extended Stanza Addressing (XEP-0033) [16] element that notes the original
     * full JID of the sender by means of the "ofrom" address type
     */
    public void addRealJidToMessage(Message message, MUCOccupant sender) {
        Element addresses = DocumentHelper.createElement(QName.get("addresses", "http://jabber.org/protocol/address"));
        Element address = addresses.addElement("address");
        address.addAttribute("type", "ofrom");
        address.addAttribute("jid", sender.getUserAddress().toString());
        message.addExtension(new PacketExtension(addresses));
    }

    /**
     * Returns the total length of the chat session.
     *
     * @return length of chat session in milliseconds.
     */
    public long getChatLength() {
        return endTime - startTime;
    }

    /**
     * Updates the internal state of a given occupant after a role change has been applied.
     *
     * Do nothing if the given jid is not present in the room.
     *
     * If the user has joined the room from several client resources, all his/her occupants' presences will be updated.
     *
     * @param userAddress the (real) JID of the occupant that is affected.
     * @param newRole the new role for the occupant.
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     */
    private List<Presence> applyRoleChange(@Nonnull final JID userAddress, @Nonnull final Role newRole)
    {
        final List<Presence> presences = new ArrayList<>();
        // Get all the roles (i.e. occupants) of this user based on his/her bare JID
        final List<MUCOccupant> occupants;
        try {
            occupants = getOccupantsByBareJID(userAddress);
        } catch (UserNotFoundException e) {
            return presences;
        }

        // Collect all the updated presences of these roles
        for (final MUCOccupant occupant : occupants)
        {
            // Update the presence with the new role
            occupant.setRole(newRole);

            // Prepare a new presence to be sent to all the room occupants
            presences.add(occupant.getPresence());
        }
        // Answer all the updated presences
        return presences;
    }

    /**
     * Adds a new user to the list of owners. The user is the actual creator of the room. Only the
     * MultiUserChatServer should use this method. Regular owners list maintenance MUST be done
     * through {@link #addOwner(JID jid, Affiliation)}.
     *
     * @param targetUserAddress The (real) JID of the user to add as owner.
     */
    public void addFirstOwner(@Nonnull final JID targetUserAddress) {
        owners.add( targetUserAddress.asBareJID() );
    }

    /**
     * Adds a list of users to the list of owners.
     *
     * @param targetUserAddresses the list of (real) JIDs of the users to add to the list of existing owners.
     * @param actorAffiliation the room affiliation of the user that is trying to modify the owners list.
     * @return the list of updated presences of all the clients resources that the clients used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the owner list.
     */
    public List<Presence> addOwners(@Nonnull final List<JID> targetUserAddresses, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        synchronized (this) {
            final List<Presence> answer = new ArrayList<>(targetUserAddresses.size());
            for (final JID targetUserAddress : targetUserAddresses) {
                final JID targetUserAddressBare = targetUserAddress.asBareJID();
                if (!owners.contains(targetUserAddress)) {
                    // TODO if this throws an exception for any one new entry, then some, but not all data is processed. Instead, ensure that either all, or none of the data is processed.
                    answer.addAll(addOwner(targetUserAddressBare, actorAffiliation));
                }
            }
            return answer;
        }
    }

    /**
     * Adds a new user to the list of owners.
     *
     * @param targetUserAddress the (real) JID of the user to add as owner.
     * @param actorAffiliation the room affiliation of the user that is trying to modify the owners list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the owner list.
     */
    public List<Presence> addOwner(@Nonnull final JID targetUserAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        final JID targetUserAddressBare = targetUserAddress.asBareJID();

        synchronized (this) {
            // Check if user is already an owner (explicitly)
            if (owners.contains(targetUserAddressBare)) {
                // Do nothing
                return Collections.emptyList();
            }

            // Remove the user from other affiliation lists
            final Affiliation oldAffiliation = getAffiliation(targetUserAddressBare);
            switch (oldAffiliation) {
                case admin:
                    removeAdmin(targetUserAddressBare, actorAffiliation);
                    break;
                case member:
                    removeMember(targetUserAddressBare, actorAffiliation, null); // No need to calculate actor role, as the 'owner' affiliation is guaranteed to be allowed to perform this action.
                    break;
                case outcast:
                    removeOutcast(targetUserAddressBare, actorAffiliation, null); // No need to calculate actor role, as the 'owner' affiliation is guaranteed to be allowed to perform this action.
                    break;
            }
            owners.add(targetUserAddressBare);

            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                targetUserAddressBare,
                null,
                Affiliation.owner,
                oldAffiliation);
        }
        // apply the affiliation change, assigning a new affiliation based on the group(s) of the affected user(s)
        return applyAffiliationChange(targetUserAddressBare, null, null, null);
    }

    private boolean removeOwner(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        return owners.remove(userAddress.asBareJID());
    }

    /**
     * Adds a list of users to the list of admins.
     *
     * @param targetUserAddresses the list of (real) JIDs of the users to add to the list of existing admins.
     * @param actorAffiliation the room affiliation of the user that is trying to modify the admins list.
     * @return the list of updated presences of all the clients resources that the clients used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the admin list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List<Presence> addAdmins(@Nonnull final List<JID> targetUserAddresses, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException, ConflictException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        synchronized(this) {
            final List<Presence> answer = new ArrayList<>(targetUserAddresses.size());
            for (final JID newAdmin : targetUserAddresses) {
                final JID bareJID = newAdmin.asBareJID();
                if (!admins.contains(bareJID)) {
                    // TODO if this throws an exception for any one new entry, then some, but not all data is processed. Instead, ensure that either all, or none of the data is processed.
                    answer.addAll(addAdmin(bareJID, actorAffiliation));
                }
            }
            return answer;
        }
    }

    /**
     * Adds a new user to the list of admins.
     *
     * @param targetUserAddress the (real) JID of the user to add as admin.
     * @param actorAffiliation The room affiliation the user that is trying to modify the admins list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the admin list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List<Presence> addAdmin(@Nonnull final JID targetUserAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException, ConflictException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        final JID targetUserAddressBare = targetUserAddress.asBareJID();
        synchronized (this) {
            // Check that the room always has an owner
            if (owners.contains(targetUserAddressBare) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Check if user is already an admin
            if (admins.contains(targetUserAddressBare)) {
                // Do nothing
                return Collections.emptyList();
            }
            admins.add(targetUserAddressBare);

            // Remove the user from other affiliation lists
            final Affiliation oldAffiliation = getAffiliation(targetUserAddressBare);
            switch (oldAffiliation) {
                case owner:
                    removeOwner(targetUserAddressBare, actorAffiliation);
                    break;
                case member:
                    removeMember(targetUserAddressBare, actorAffiliation, null); // No need to calculate actor role, as the 'owner' affiliation is guaranteed to be allowed to perform this action.
                    break;
                case outcast:
                    removeOutcast(targetUserAddressBare, actorAffiliation, null);  // No need to calculate actor role, as the 'owner' affiliation is guaranteed to be allowed to perform this action.
                    break;
            }
            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                targetUserAddressBare,
                null,
                Affiliation.admin,
                oldAffiliation);
        }
        // apply the affiliation change, assigning a new affiliation
        // based on the group(s) of the affected user(s)
        return applyAffiliationChange(targetUserAddressBare, null, null, null);
    }

    private boolean removeAdmin(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException
    {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }
        return admins.remove( userAddress.asBareJID() );
    }

    /**
     * Adds a new user to the list of members.
     *
     * @param targetUserAddress The (real) JID of the user to add as a member.
     * @param nickname The reserved nickname of the member for the room or null if none.
     * @param actorAffiliation the room affiliation of the user that is trying to modify the members list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the members list.
     * @throws ConflictException If the desired room nickname is already reserved for the room or if
     *             the room was going to lose all its owners.
     */
    public List<Presence> addMember(@Nonnull final JID targetUserAddress, @Nullable final String nickname, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException, ConflictException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }

        final JID targetUserAddressBare = targetUserAddress.asBareJID();
        synchronized (this) {
            // Check if the desired nickname is already reserved for another member
            if (nickname != null && !nickname.trim().isEmpty() && members.containsValue(nickname.toLowerCase())) {
                if (!nickname.equals(members.get(targetUserAddressBare))) {
                    throw new ConflictException();
                }
            } else if (isLoginRestrictedToNickname() && (nickname == null || nickname.trim().isEmpty())) {
                throw new ConflictException();
            }
            // Check that the room always has an owner
            if (owners.contains(targetUserAddressBare) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Check if user is already a member
            if (members.containsKey(targetUserAddressBare)) {
                // Do nothing
                return Collections.emptyList();
            }

            // Remove the user from other affiliation lists. This can throw a 'forbidden' when the actor tries to demote a higher-up!
            final Affiliation oldAffiliation = getAffiliation(targetUserAddressBare);
            switch (oldAffiliation) {
                case owner:
                    removeOwner(targetUserAddressBare, actorAffiliation);
                    break;
                case admin:
                    removeAdmin(targetUserAddressBare, actorAffiliation);
                    break;
                case outcast:
                    removeOutcast(targetUserAddressBare, actorAffiliation, null);  // No need to calculate actor role, as both the 'owner' and 'admin' affiliation is guaranteed to be allowed to perform this action.
                    break;
            }

            // Associate the reserved nickname with the bareJID. If nickname is null then associate an empty string
            members.put(targetUserAddressBare, (nickname == null ? "" : nickname.toLowerCase()));

            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                targetUserAddressBare,
                nickname,
                Affiliation.member,
                oldAffiliation);
        }

        // apply the affiliation change, assigning a new affiliation
        // based on the group(s) of the affected user(s)
        return applyAffiliationChange(targetUserAddressBare, null, null, null);
    }

    private boolean removeMember(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole) throws ForbiddenException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && actorRole != Role.moderator) {
            throw new ForbiddenException();
        }

        return members.remove(userAddress.asBareJID()) != null;
    }

    /**
     * Adds a new user to the list of outcast users.
     *
     * @param targetUserAddress The (real) JID of the user to add as an outcast.
     * @param reason an optional reason why the user was banned.
     * @param actorUserAddress The (real) address of the user that initiated the ban.
     * @param actorAffiliation The room affiliation of the user that initiated the ban.
     * @param actorRole The room role of the user that initiated the ban (can be null when the user is currently not an occupant).
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws NotAllowedException Thrown if trying to ban an owner or an administrator.
     * @throws ForbiddenException If the user is not allowed to modify the outcast list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List<Presence> addOutcast(@Nonnull final JID targetUserAddress, @Nullable final String reason, @Nullable final JID actorUserAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole) throws NotAllowedException, ForbiddenException, ConflictException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && Role.moderator != actorRole) {
            throw new ForbiddenException();
        }

        final JID targetUserAddressBare = targetUserAddress.asBareJID();

        synchronized (this) {
            // Check that the room always has an owner
            if (owners.contains(targetUserAddressBare) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Check if user is already an outcast
            if (outcasts.contains(targetUserAddressBare)) {
                // Do nothing
                return Collections.emptyList();
            }

            // Remove the user from other affiliation lists. This can throw a 'forbidden' when the actor tries to demote a higher-up!
            final Affiliation oldAffiliation = getAffiliation(targetUserAddressBare);
            switch (oldAffiliation) {
                case owner:
                    removeOwner(targetUserAddressBare, actorAffiliation);
                    break;
                case admin:
                    removeAdmin(targetUserAddressBare, actorAffiliation);
                    break;
                case member:
                    removeMember(targetUserAddressBare, actorAffiliation, actorRole);
                    break;
            }

            // Update the affiliation lists
            outcasts.add(targetUserAddressBare);

            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                targetUserAddressBare,
                null,
                Affiliation.outcast,
                oldAffiliation);
        }
        // apply the affiliation change, assigning a new affiliation
        // based on the group(s) of the affected user(s)
        return applyAffiliationChange(targetUserAddressBare, actorUserAddress, null, reason);
    }

    private boolean removeOutcast(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole) throws ForbiddenException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && actorRole != Role.moderator) {
            throw new ForbiddenException();
        }
        return outcasts.remove( userAddress.asBareJID() );
    }

    /**
     * Removes the user from all the other affiliation list thus giving the user a NONE affiliation.
     *
     * When kicking a user from the room, use {@link #kickOccupant(JID, Affiliation, Role, JID, String, String)}
     * instead, which causes the <em>role</em> of the occupant to be changed (instead of the <em>affiliation</em>, which is modified
     * by this method).
     *
     * @param targetUserAddress The (real) JID of the user to give a NONE affiliation.
     * @param actorAffiliation The room affiliation of the user that set the affiliation to none.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room or null if none was updated.
     * @throws ForbiddenException If the user is not allowed to modify the none list.
     * @throws ConflictException If the room was going to lose all its owners.
     * @see #kickOccupant(JID, Affiliation, Role, JID, String, String)
     */
    public List<Presence> addNone(@Nonnull final JID targetUserAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException, ConflictException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }
        final JID targetUserAddressBare = targetUserAddress.asBareJID();

        boolean jidWasAffiliated = false;
        synchronized (this) {
            // Check that the room always has an owner
            if (owners.contains(targetUserAddressBare) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Remove the jid from ALL the affiliation lists. This can throw a 'forbidden' when the actor tries to demote a higher-up!
            final Affiliation oldAffiliation = getAffiliation(targetUserAddressBare);
            switch (oldAffiliation) {
                case owner:
                    removeOwner(targetUserAddressBare, actorAffiliation);
                    jidWasAffiliated = true;
                    break;
                case admin:
                    removeAdmin(targetUserAddressBare, actorAffiliation);
                    jidWasAffiliated = true;
                    break;
                case member:
                    removeMember(targetUserAddressBare, actorAffiliation, null); // No need to calculate actor role, as both the 'owner' and 'admin' affiliation is guaranteed to be allowed to perform this action.
                    jidWasAffiliated = true;
                    break;
                case outcast:
                    removeOutcast(targetUserAddressBare, actorAffiliation, null); // No need to calculate actor role, as both the 'owner' and 'admin' affiliation is guaranteed to be allowed to perform this action.
                    break;
            }
            // Remove the affiliation of this user from the DB if the room is persistent
            MUCPersistenceManager.removeAffiliationFromDB(this, targetUserAddressBare, oldAffiliation);
        }
        if (jidWasAffiliated) {
            // apply the affiliation change, assigning a new affiliation
            // based on the group(s) of the affected user(s)
            return applyAffiliationChange(targetUserAddressBare, null, null, null);
        } else {
            // no presence updates needed
            return Collections.emptyList();
        }
    }

    /**
     * Kicks a user from the room. If the user was in the room, the returned updated presence will be sent to the
     * remaining occupants.
     *
     * When attempting to remove the <em>affiliation</em> from a user from the room, use
     * {@link #addNone(JID, Affiliation)} instead. Kicking a user will only cause the
     * <em>role</em> of the occupant to be changed.
     *
     * The status code that is used in the presence stanzas that are sent as a result of the kick is '307'.
     *
     * @param userAddress the (real) JID of the occupant to change to visitor
     * @param actorJID      The JID of the actor that initiated the kick.
     * @param actorNickname The actor nickname.
     * @param reason        An optional reason why the user was kicked (can be {@code null}).
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     * @throws ForbiddenException Thrown if trying to ban an owner or an administrator.
     */
    public List<Presence> kickOccupant(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole, @Nullable final JID actorJID, @Nullable final String actorNickname, @Nullable final String reason) throws ForbiddenException, NotAllowedException
    {
        return kickOccupant(userAddress, actorAffiliation, actorRole, actorJID, actorNickname, reason, 307);
    }

    /**
     * Kicks a user from the room. If the user was in the room, the returned updated presence will be sent to the
     * remaining occupants.
     *
     * When attempting to remove the <em>affiliation</em> from a user from the room, use
     * {@link #addNone(JID, Affiliation)} instead. Kicking a user will only cause the
     * <em>role</em> of the occupant to be changed.
     *
     * @param userAddress the (real) JID of the occupant to change to visitor
     * @param actorJID      The JID of the actor that initiated the kick.
     * @param actorNickname The actor nickname.
     * @param reason        An optional reason why the user was kicked (can be {@code null}).
     * @param status        The status code that references the reason for the kick (typically 307).
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     * @throws ForbiddenException Thrown if trying to ban an owner or an administrator.
     */
    public List<Presence> kickOccupant(@Nonnull final JID userAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole, @Nullable final JID actorJID, @Nullable final String actorNickname, @Nullable final String reason, final int status) throws ForbiddenException, NotAllowedException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && Role.moderator != actorRole) {
            throw new ForbiddenException();
        }

        // A moderator SHOULD NOT be allowed to revoke moderation privileges from someone with a higher affiliation than
        // themselves (i.e., an unaffiliated moderator SHOULD NOT be allowed to revoke moderation privileges from an
        // admin or an owner, and an admin SHOULD NOT be allowed to revoke moderation privileges from an owner).
        final Affiliation targetAffiliation = getAffiliation(userAddress);
        if (targetAffiliation.getValue() < actorAffiliation.getValue()) { // note that Openfire's values are ordered the other way around.
            throw new NotAllowedException();
        }

        // Update the presence with the new role and inform all occupants
        final List<Presence> updatedPresences = applyRoleChange(userAddress, Role.none);

        // Determine the occupant data of the actor that initiates the kick.
        MUCOccupant sender;
        if ( actorJID == null ) {
            sender = getSelfRepresentation(); // originates from the room itself (eg: through admin console changes).
        } else {
            sender = getOccupantByFullJID(actorJID);
            if ( sender == null ) {
                sender = getSelfRepresentation();
            }
        }

        Log.debug("Kicking '{}' from '{}' by '{}' because of: {} ", userAddress, getJID(), sender, reason);

        for (final Presence updatedPresence : updatedPresences)
        {
            final Element frag = updatedPresence.getChildElement("x", "http://jabber.org/protocol/muc#user");

            // Add the status code that indicates why user was kicked
            frag.addElement("status").addAttribute("code", String.valueOf(status));

            // Effectively kick the occupant from the room
            kickPresence(updatedPresence, actorJID, actorNickname, reason);

            //Inform the other occupants that user has been kicked
            broadcastPresence(updatedPresence, false, sender);
        }
        // TODO should this return presence/should callers distribute this stanza (alternatively: should this stanza be broadcast in this method)?
        return updatedPresences;
    }

    /**
     * Updates the internal state of a given occupant after an affiliation change has been applied.
     * <p>
     * Evaluate the given JID to determine what the appropriate affiliation should be
     * after a change has been made. Each affected user will be granted the highest
     * affiliation they now possess, either explicitly or implicitly via membership
     * in one or more groups.
     * <p>
     * If the JID is a user, the effective affiliation is
     * applied to each presence corresponding to that user. If the given JID is a group,
     * each user in the group is evaluated to determine what their new affiliations will
     * be.
     * <p>
     * The returned presence updates will be broadcast to the occupants of the room.
     *
     * @param target           The JID for the user or group for which the affiliation has been changed
     * @param actorUserAddress The (real) address of the user that initiated the ban.
     * @param actorNickname    The actor nickname.
     * @param reason           An optional reason why the user was kicked.
     * @return List of presence updates to be delivered to the room's occupants
     */
    private List<Presence> applyAffiliationChange(@Nonnull final JID target, @Nullable final JID actorUserAddress, @Nullable final String actorNickname, @Nullable final String reason)
    {
        // Update the presence(s) for the new affiliation and inform all occupants
        final List<JID> affectedOccupants = new ArrayList<>();

        // first, determine which actual (user) JIDs are affected by the affiliation change
        if (GroupJID.isGroup(target)) {
            try {
                final Group group = GroupManager.getInstance().getGroup(target);
                // check each occupant to see if they are in the group that was changed
                // if so, calculate a new affiliation (if any) for the occupant(s)
                for (final JID groupMember : group.getAll()) {
                    if (hasOccupant(groupMember)) {
                        affectedOccupants.add(groupMember);
                    }
                }
            } catch (GroupNotFoundException gnfe) {
                Log.error("Error updating group presences for " + target, gnfe);
            }
        } else {
            if (hasOccupant(target)) {
                affectedOccupants.add(target);
            }
        }

        // now update each of the affected occupants with a new role/affiliation
        final List<Presence> updatedPresences = new ArrayList<>();
        // new role/affiliation may be granted via group membership
        for (final JID occupantJID : affectedOccupants) {
            boolean kickMember = false, isOutcast = false;
            final Role newRole;
            final Affiliation newAffiliation;
            if (owners.includes(occupantJID)) {
                newRole = Role.moderator;
                newAffiliation = Affiliation.owner;
            } else if (admins.includes(occupantJID)) {
                newRole = Role.moderator;
                newAffiliation = Affiliation.admin;
            }
            // outcast trumps member when an affiliation is changed
            else if (outcasts.includes(occupantJID)) {
                newAffiliation = Affiliation.outcast;
                newRole = Role.none;
                kickMember = true;
                isOutcast = true;
            } else if (members.includesKey(occupantJID)) {
                newRole = Role.participant;
                newAffiliation = Affiliation.member;
            } else if (isMembersOnly()) {
                newRole = Role.none;
                newAffiliation = Affiliation.none;
                kickMember = true;
            } else {
                newRole = isModerated() ? Role.visitor : Role.participant;
                newAffiliation = Affiliation.none;
            }
            Log.debug("Applying affiliation change for {}. New affiliation: {}", occupantJID, newAffiliation);

            final List<Presence> presences = new ArrayList<>();
            // Get all the roles (i.e. occupants) of this user based on his/her bare JID
            final List<MUCOccupant> occupants;
            try {
                occupants = getOccupantsByBareJID(occupantJID);
                // Collect all the updated presences of these roles
                for (final MUCOccupant occupant : occupants) {
                    // Update the presence with the new affiliation and role
                    occupant.setAffiliation(newAffiliation);
                    occupant.setRole(newRole);

                    // Prepare a new presence to be sent to all the room occupants
                    presences.add(occupant.getPresence());
                }
            } catch (UserNotFoundException e) {
                return presences;
            }

            if (kickMember) {
                // If the room is members-only, remove the user from the room including a status
                // code of 321 to indicate that the user was removed because of an affiliation change
                // a status code of 301 indicates the user was removed as an outcast
                for (final Presence presence : presences) {
                    presence.setType(Presence.Type.unavailable);
                    presence.setStatus(null);

                    final Element x = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                    x.addElement("status").addAttribute("code", isOutcast ? "301" : "321");

                    // This removes the kicked occupant from the room. The presenceUpdates returned by this method
                    // (that will be broadcast to all occupants by the caller) won't reach it.
                    kickPresence(presence, actorUserAddress, actorNickname, reason);
                }
            }
            updatedPresences.addAll(presences);
        }
        return updatedPresences;
    }

    /**
     * Returns true if the room is locked. The lock will persist for a defined period of time. If
     * the room owner does not configure the room within the timeout period, the room owner is
     * assumed to have accepted the default configuration.
     *
     * @return true if the room is locked.
     */
    public boolean isLocked() {
        return lockedTime > 0;
    }

    /**
     * Returns true if the room is locked and it was locked by a room owner after the room was
     * initially configured.
     *
     * @return true if the room is locked and it was locked by a room owner after the room was
     *         initially configured.
     */
    public boolean isManuallyLocked() {
        return lockedTime > 0 && creationDate.getTime() != lockedTime;
    }

    /**
     * An event callback fired whenever an occupant updated his presence in the chatroom.
     *
     * Handles occupants updating their presence in the chatroom. Assumes the user updates their presence whenever their
     * availability in the room changes. This method should not be called to handle other presence related updates, such
     * as nickname changes.
     *
     * @param occupant occupant that changed his presence in the room (cannot be {@code null}).
     * @param newPresence presence sent by the occupant (cannot be {@code null}).
     */
    public void presenceUpdated(final MUCOccupant occupant, final Presence newPresence) {
        final String occupantNickName = occupant.getNickname();

        try {
            List<MUCOccupant> occupants = getOccupantsByNickname(occupantNickName);
            for (MUCOccupant occ : occupants) {
                occ.setPresence(newPresence.createCopy());
            }
        } catch (UserNotFoundException e) {
            Log.debug("Failed to update presence of room occupant. Occupant nickname: {}", occupantNickName, e);
        }

        // Get the new, updated presence for the occupant in the room. The presence reflects the occupant's updated
        // availability and their existing association.
        final Presence updatedPresence = occupant.getPresence();

        // Broadcast updated presence of occupant.
        broadcastPresence(updatedPresence, false, occupant);
    }

    /**
     * An event callback fired whenever an occupant changes his nickname within the chatroom.
     *
     * @param occupant occupant that changed his nickname in the room (cannot be {@code null}).
     * @param newPresence presence sent by the occupant with the new nickname (cannot be {@code null}).
     * @param oldNick old nickname within the room (cannot be {@code null}).
     * @param newNick new nickname within the room (cannot be {@code null}).
     */
    public void nicknameChanged(MUCOccupant occupant, Presence newPresence, String oldNick, String newNick)
    {
        List<MUCOccupant> occupants;
        try {
            occupants = getOccupantsByNickname(oldNick);
        } catch (UserNotFoundException e) {
            Log.debug("Unable to process nickname change from old '{}' to new '{}' for occupant '{}' as no occupant with the old nickname is found.", oldNick, newNick, occupant, e);
            return;
        }

        for (MUCOccupant occ : occupants) {
            // Update the role with the new info
            occ.setPresence(newPresence);
            occ.changeNickname(newNick);

            // Fire event that user changed his nickname
            MUCEventDispatcher.nicknameChanged(getSelfRepresentation().getOccupantJID(), occ.getUserAddress(), oldNick, newNick);
        }

        // Broadcast new presence of occupant
        broadcastPresence(occupant.getPresence(), false, occupant);
    }

    /**
     * Changes the room's subject if the occupant has enough permissions. The occupant must be
     * a moderator or the room must be configured so that anyone can change its subject, otherwise
     * a forbidden exception will be thrown.
     *
     * The new subject will be added to the history of the room.
     *
     * @param packet the stanza used to change the room's subject (cannot be {@code null}).
     * @param actor the occupant that is trying to change the subject (cannot be {@code null}).
     * @throws ForbiddenException If the user is not allowed to change the subject.
     */
    public void changeSubject(Message packet, MUCOccupant actor) throws ForbiddenException {
        if ((canOccupantsChangeSubject() && actor.getRole().compareTo(Role.visitor) < 0) ||
            Role.moderator == actor.getRole()) {
            // Set the new subject to the room
            subject = packet.getSubject();
            MUCPersistenceManager.updateRoomSubject(this);
            // Notify all the occupants that the subject has changed
            packet.setFrom(actor.getOccupantJID());
            send(packet, actor);

            // Fire event signifying that the room's subject has changed.
            MUCEventDispatcher.roomSubjectChanged(getJID(), actor.getUserAddress(), subject);
        }
        else {
            throw new ForbiddenException();
        }
    }

    /**
     * Returns the last subject that some occupant set to the room.
     *
     * @return the last subject that some occupant set to the room.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets the last subject that some occupant set to the room. This message will only be used
     * when loading a room from the database.
     *
     * @param subject the last known subject of the room (cannot be {@code null}).
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Sends an invitation to a user. The invitation will be sent as if the room is inviting the
     * user. The invitation will include the original occupant that sent the invitation together with
     * the reason for the invitation if any. Since the invitee could be offline at the moment we
     * need the originating session so that the offline strategy could potentially bounce the
     * message with the invitation.
     *
     * @param to the JID of the user that is being invited.
     * @param reason the reason of the invitation or null if none.
     * @param senderAffiliation the room affiliation the user that sent the invitation.
     * @param senderUserAddress the (real) user address of the user that sent the invitation.
     * @param extensions the list of extensions sent with the original message invitation or null
     *        if none.
     * @throws ForbiddenException If the user is not allowed to send the invitation.
     * @throws CannotBeInvitedException (Optionally) If the user being invited does not have access to the room
     */
    public void sendInvitation(@Nonnull final JID to, @Nullable final String reason, @Nonnull final Affiliation senderAffiliation, @Nullable final JID senderUserAddress, @Nullable final List<Element> extensions)
        throws ForbiddenException, CannotBeInvitedException {
        if (!isMembersOnly() || canOccupantsInvite()
            || Affiliation.admin == senderAffiliation
            || Affiliation.owner == senderAffiliation) {
            // If the room is not members-only OR if the room is members-only and anyone can send
            // invitations or the sender is an admin or an owner, then send the invitation
            Message message = new Message();
            message.setFrom(selfOccupantData.getOccupantJID());
            message.setTo(to);

            if (mucService.getMUCDelegate() != null) {
                switch(mucService.getMUCDelegate().sendingInvitation(this, to, senderUserAddress, reason)) {
                    case HANDLED_BY_DELEGATE:
                        //if the delegate is taking care of it, there's nothing for us to do
                        return;
                    case HANDLED_BY_OPENFIRE:
                        //continue as normal if we're asked to handle it
                        break;
                    case REJECTED:
                        //we can't invite that person
                        throw new CannotBeInvitedException();
                }
            }

            // Add a list of extensions sent with the original message invitation (if any)
            if (extensions != null) {
                for(Element element : extensions) {
                    element.setParent(null);
                    message.getElement().add(element);
                }
            }
            Element frag = message.addChildElement("x", "http://jabber.org/protocol/muc#user");
            // ChatUser will be null if the room itself (i.e. via admin console) made the request. In that case, use the room JID. See OF-2486.
            final JID from = senderUserAddress != null ? senderUserAddress : getJID();
            frag.addElement("invite").addAttribute("from", from.toBareJID());
            if (reason != null && !reason.isEmpty()) {
                Element invite = frag.element("invite");
                if (invite == null) {
                    invite = frag.addElement("invite");
                }
                invite.addElement("reason").setText(reason);
            }
            if (isPasswordProtected()) {
                frag.addElement("password").setText(getPassword());
            }

            // Include the jabber:x:conference information for backward compatibility
            frag = message.addChildElement("x", "jabber:x:conference");
            frag.addAttribute("jid", selfOccupantData.getOccupantJID().toBareJID());

            // Send the message with the invitation
            XMPPServer.getInstance().getPacketRouter().route(message);
        }
        else {
            throw new ForbiddenException();
        }
    }

    /**
     * Sends the rejection to the inviter. The rejection will be sent as if the room is rejecting
     * the invitation is named of the invitee. The rejection will include the address of the invitee
     * together with the reason for the rejection if any. Since the inviter could be offline at the
     * moment we need the originating session so that the offline strategy could potentially bounce
     * the message with the rejection.
     *
     * @param to the JID of the user that is originated the invitation.
     * @param reason the reason for the rejection or null if none.
     * @param sender the JID of the invitee that is rejecting the invitation.
     */
    public void sendInvitationRejection(JID to, String reason, JID sender) {
        if (mucService.getMUCDelegate() != null) {
            switch(mucService.getMUCDelegate().sendingInvitationRejection(this, to, sender, reason)) {
                case HANDLED_BY_DELEGATE:
                    //if the delegate is taking care of it, there's nothing for us to do
                    return;
                case HANDLED_BY_OPENFIRE:
                    //continue as normal if we're asked to handle it
                    break;
            }
        }

        Message message = new Message();
        message.setFrom(selfOccupantData.getOccupantJID());
        message.setTo(to);
        Element frag = message.addChildElement("x", "http://jabber.org/protocol/muc#user");
        frag.addElement("decline").addAttribute("from", sender.toBareJID());
        if (reason != null && !reason.isEmpty()) {
            frag.element("decline").addElement("reason").setText(reason);
        }

        // Send the message with the invitation
        XMPPServer.getInstance().getPacketRouter().route(message);
    }

    public IQOwnerHandler getIQOwnerHandler() {
        return iqOwnerHandler;
    }

    public IQAdminHandler getIQAdminHandler() {
        return iqAdminHandler;
    }

    public FMUCHandler getFmucHandler() {
        return fmucHandler;
    }

    /**
     * Returns the history of the room which includes chat transcripts.
     *
     * @return the history of the room which includes chat transcripts.
     */
    public MUCRoomHistory getRoomHistory() {
        return roomHistory;
    }

    /**
     * Returns a collection with the current list of owners. The collection contains the bareJID of
     * the users with owner affiliation.
     *
     * @return a collection with the current list of owners.
     */
    public Collection<JID> getOwners() {
        return Collections.unmodifiableList(owners);
    }

    /**
     * Returns a collection with the current list of admins. The collection contains the bareJID of
     * the users with admin affiliation.
     *
     * @return a collection with the current list of admins.
     */
    public Collection<JID> getAdmins() {
        return Collections.unmodifiableList(admins);
    }

    /**
     * Returns a collection with the current list of room members. The collection contains the
     * bareJID of the users with member affiliation. If the room is not members-only then the list
     * will contain the users that registered with the room and therefore they may have reserved a
     * nickname.
     *
     * @return a collection with the current list of members.
     */
    public Collection<JID> getMembers() {
        return Collections.unmodifiableMap(members).keySet();
    }

    /**
     * Returns a collection with the current list of outcast users. An outcast user is not allowed
     * to join the room again. The collection contains the bareJID of the users with outcast
     * affiliation.
     *
     * @return a collection with the current list of outcast users.
     */
    public Collection<JID> getOutcasts() {
        return Collections.unmodifiableList(outcasts);
    }

    /**
     * Returns a collection with the current list of room moderators. The collection contains the
     * data of the occupants with moderator role.
     *
     * @return a collection with the current list of moderators.
     */
    public Collection<MUCOccupant> getModerators() {
        return occupants.stream()
            .filter(occupant -> occupant.getRole() == Role.moderator)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns a collection with the current list of room participants. The collection contains the
     * data of the occupants with participant role.
     *
     * @return a collection with the current list of moderators.
     */
    public Collection<MUCOccupant> getParticipants() {
        return occupants.stream()
            .filter(occupant -> occupant.getRole() == Role.participant)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Changes the role of the user within the room to moderator. A moderator is allowed to kick
     * occupants as well as granting/revoking voice from occupants.
     *
     * @param targetUserAddress the (real) JID of the occupant to give moderator privileges.
     * @param actorAffiliation The room affiliation of the user that is granting moderator privileges to an occupant.
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     * @throws ForbiddenException If the user is not allowed to grant moderator privileges.
     */
    public List<Presence> addModerator(@Nonnull final JID targetUserAddress, @Nonnull final Affiliation actorAffiliation) throws ForbiddenException
    {
        if (Affiliation.admin != actorAffiliation&& Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }
        // Update the presence with the new role and inform all occupants
        return applyRoleChange(targetUserAddress, Role.moderator);
    }

    /**
     * Changes the role of the user within the room to participant. A participant is allowed to send
     * messages to the room (i.e. has voice) and may change the room's subject.
     *
     * @param targetUserAddress the (real) JID of the occupant to give participant privileges.
     * @param reason The reason why participant privileges were given to the user.
     * @param actorAffiliation The room affiliation of the user that is granting participant privileges to an occupant.
     * @param actorRole The room role of the user that is granting participant privileges to an occupant (can be null when the user is currently not an occupant).
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     * @throws ForbiddenException If the user is not allowed to grant participant privileges.
     */
    public List<Presence> addParticipant(@Nonnull final JID targetUserAddress, @Nullable final String reason,
                                         @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole) throws ForbiddenException, NotAllowedException
    {
        // Moderator grants voice, or Admin or owner changes role to participant or revokes moderator status
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && Role.moderator != actorRole) {
            throw new ForbiddenException();
        }

        // A moderator SHOULD NOT be allowed to revoke moderation privileges from someone with a higher affiliation than
        // themselves (i.e., an unaffiliated moderator SHOULD NOT be allowed to revoke moderation privileges from an
        // admin or an owner, and an admin SHOULD NOT be allowed to revoke moderation privileges from an owner).
        final Affiliation targetAffiliation = getAffiliation(targetUserAddress);
        if (targetAffiliation.getValue() < actorAffiliation.getValue()) { // note that Openfire's values are ordered the other way around.
            throw new NotAllowedException();
        }

        // An admin might want to revoke a user's moderator status. An admin MAY revoke moderator status only from a
        // user whose affiliation is "member" or "none" (i.e., not from an owner or admin).
        if (actorAffiliation == Affiliation.admin && (targetAffiliation == Affiliation.owner || targetAffiliation == Affiliation.admin)) {
            throw new NotAllowedException();
        }

        // Update the presence with the new role and inform all occupants
        final List<Presence> updatedPresences = applyRoleChange(targetUserAddress, Role.participant);
        for (final Presence updatedPresence : updatedPresences)
        {
            final Element frag = updatedPresence.getChildElement("x", "http://jabber.org/protocol/muc#user");
            // Add the reason why the user was granted voice
            if (reason != null && !reason.trim().isEmpty()) {
                frag.element("item").addElement("reason").setText(reason);
            }
        }
        return updatedPresences;
    }

    /**
     * Changes the role of the user within the room to visitor. A visitor can receive messages but
     * is not allowed to send messages to the room (i.e. does not have voice) and may invite others
     * to the room.
     *
     * @param targetUserAddress the (real) JID of the occupant to change to visitor
     * @param actorAffiliation The room affiliation of the user that is changing the role to visitor.
     * @param actorRole The room role of the user that is changing the role to visitor (can be null when the user is currently not an occupant).
     * @return the list of updated presences of all the client resources that the occupant has used to join the room.
     * @throws ForbiddenException if the actor is allowed to change the role.
     */
    public List<Presence> addVisitor(@Nonnull final JID targetUserAddress, @Nonnull final Affiliation actorAffiliation, @Nullable final Role actorRole) throws ForbiddenException, NotAllowedException
    {
        if (Affiliation.admin != actorAffiliation && Affiliation.owner != actorAffiliation && Role.moderator != actorRole) {
            throw new ForbiddenException();
        }

        // A moderator SHOULD NOT be allowed to revoke moderation privileges from someone with a higher affiliation than
        // themselves (i.e., an unaffiliated moderator SHOULD NOT be allowed to revoke moderation privileges from an
        // admin or an owner, and an admin SHOULD NOT be allowed to revoke moderation privileges from an owner).
        final Affiliation targetAffiliation = getAffiliation(targetUserAddress);
        if (targetAffiliation.getValue() < actorAffiliation.getValue()) { // note that Openfire's values are ordered the other way around.
            throw new NotAllowedException();
        }

        return applyRoleChange(targetUserAddress, Role.visitor);
    }

    /**
     * Kicks the occupant from the room. This means that the occupant will receive an unavailable
     * presence with the actor that initiated the kick (if any). The occupant will also be removed
     * from the occupants lists.
     *
     * Note that the remaining occupants of the room are not informed by this implementation. It is the responsibility
     * of the caller to ensure that this occurs.
     *
     * @param kickPresence     The presence of the occupant to kick from the room.
     * @param actorUserAddress The (real) JID of the actor that initiated the kick.
     * @param actorNickname    The nickname in the room of the actor that initiated the kick.
     * @param reason           An optional reason why the user was kicked (can be {@code null}).
     */
    private void kickPresence(@Nonnull final Presence kickPresence, @Nullable final JID actorUserAddress, @Nullable String actorNickname, @Nullable final String reason)
    {
        // When no nickname is provided, try to look it up.
        final boolean hasActorJid = actorUserAddress != null;
        boolean hasActorNick = actorNickname != null && !actorNickname.isEmpty();
        if (!hasActorNick && hasActorJid) {
            actorNickname = findNickname(actorUserAddress);
        }
        hasActorNick = actorNickname != null && !actorNickname.isEmpty();

        final List<MUCOccupant> kickedOccupants;
        try {
            kickedOccupants = getOccupantsByNickname(kickPresence.getFrom().getResource());
            for (MUCOccupant kickedOccupant : kickedOccupants) {
                final Element item =  kickPresence.getChildElement("x", "http://jabber.org/protocol/muc#user").element("item");

                // Add the reason why the user was kicked
                if (reason != null && !reason.trim().isEmpty()) {
                    item.addElement("reason").setText(reason);
                }

                // Add the actor's JID that kicked this user from the room
                if (hasActorJid || hasActorNick) {
                    final Element actor = item.addElement("actor");
                    if (hasActorJid && canAnyoneDiscoverJID) {
                        actor.addAttribute("jid", actorUserAddress.toBareJID());
                    }
                    if (hasActorNick) {
                        actor.addAttribute("nick", actorNickname);
                    }
                }

                // Send a defensive copy (to not leak a change to the 'to' address - this is possibly overprotective here,
                // but we're erring on the side of caution) of the unavailable presence to the banned user.
                final Presence kickSelfPresence = kickPresence.createCopy();
                final Element fragKickSelfPresence = kickSelfPresence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                fragKickSelfPresence.addElement("status").addAttribute("code", "110");
                kickedOccupant.send(kickSelfPresence);

                // Remove the occupant from the room's occupants lists
                removeOccupant(kickedOccupant);
            }
        } catch (UserNotFoundException e) {
            Log.debug("Unable to kick '{}' from room '{}' as there's no occupant with that nickname.", kickPresence.getFrom().getResource(), getJID(), e);
        }
    }

    /**
     * Returns true if every presence packet will include the JID of every occupant. This
     * configuration can be modified by the owner while editing the room's configuration.
     *
     * @return true if every presence packet will include the JID of every occupant.
     */
    public boolean canAnyoneDiscoverJID() {
        return canAnyoneDiscoverJID;
    }

    /**
     * Sets if every presence packet will include the JID of every occupant. This
     * configuration can be modified by the owner while editing the room's configuration.
     *
     * @param canAnyoneDiscoverJID boolean that specifies if every presence packet will include the
     *        JID of every occupant.
     */
    public void setCanAnyoneDiscoverJID(boolean canAnyoneDiscoverJID) {
        this.canAnyoneDiscoverJID = canAnyoneDiscoverJID;
    }

    /**
     * Returns the minimal role of persons that are allowed to send private messages in the room. The returned value is
     * any one of: "anyone", "moderators", "participants", "none".
     *
     * @return The minimal role of persons that are allowed to send private messages in the room (never null).
     */
    public String canSendPrivateMessage() {
        return canSendPrivateMessage == null ? "anyone" : canSendPrivateMessage;
    }

    /**
     * Sets the minimal role of persons that are allowed to send private messages in the room. The provided value is
     * any one of: "anyone", "moderators", "participants", "none". If another value is set, "anyone" is used instead.
     *
     * @param role The minimal role of persons that are allowed to send private messages in the room (never null).
     */
    public void setCanSendPrivateMessage(String role) {
        if ( role == null ) {
            role = "(null)";
        }

        switch( role.toLowerCase() ) {
            case "none":
            case "moderators":
            case "participants":
            case "anyone":
                this.canSendPrivateMessage = role.toLowerCase();
                break;
            default:
                Log.warn( "Illegal value for muc#roomconfig_allowpm: '{}'. Defaulting to 'anyone'", role.toLowerCase() );
                this.canSendPrivateMessage = "anyone";
        }
    }

    /**
     * Returns true if participants are allowed to change the room's subject.
     *
     * @return true if participants are allowed to change the room's subject.
     */
    public boolean canOccupantsChangeSubject() {
        return canOccupantsChangeSubject;
    }

    /**
     * Sets if participants are allowed to change the room's subject.
     *
     * @param canOccupantsChangeSubject boolean that specifies if participants are allowed to
     *        change the room's subject.
     */
    public void setCanOccupantsChangeSubject(boolean canOccupantsChangeSubject) {
        this.canOccupantsChangeSubject = canOccupantsChangeSubject;
    }

    /**
     * Returns true if occupants can invite other users to the room. If the room does not require an
     * invitation to enter (i.e. is not members-only) then any occupant can send invitations. On
     * the other hand, if the room is members-only and occupants cannot send invitation then only
     * the room owners and admins are allowed to send invitations.
     *
     * @return true if occupants can invite other users to the room.
     */
    public boolean canOccupantsInvite() {
        return canOccupantsInvite;
    }

    /**
     * Sets if occupants can invite other users to the room. If the room does not require an
     * invitation to enter (i.e. is not members-only) then any occupant can send invitations. On
     * the other hand, if the room is members-only and occupants cannot send invitation then only
     * the room owners and admins are allowed to send invitations.
     *
     * @param canOccupantsInvite boolean that specified in any occupant can invite other users to
     *        the room.
     */
    public void setCanOccupantsInvite(boolean canOccupantsInvite) {
        this.canOccupantsInvite = canOccupantsInvite;
    }

    /**
     * Returns the natural language name of the room. This name can only be modified by room owners.
     * It's mainly used for users while discovering rooms hosted by the Multi-User Chat service.
     *
     * @return the natural language name of the room.
     */
    public String getNaturalLanguageName() {
        return naturalLanguageName;
    }

    /**
     * Sets the natural language name of the room. This name can only be modified by room owners.
     * It's mainly used for users while discovering rooms hosted by the Multi-User Chat service.
     *
     * @param naturalLanguageName the natural language name of the room.
     */
    public void setNaturalLanguageName(String naturalLanguageName) {
        this.naturalLanguageName = naturalLanguageName;
    }

    /**
     * Returns a description set by the room's owners about the room. This information will be used
     * when discovering extended information about the room.
     *
     * @return a description set by the room's owners about the room.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets a description set by the room's owners about the room. This information will be used
     * when discovering extended information about the room.
     *
     * @param description a description set by the room's owners about the room.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns true if the room requires an invitation to enter. That is if the room is
     * members-only.
     *
     * @return true if the room requires an invitation to enter.
     */
    public boolean isMembersOnly() {
        return membersOnly;
    }

    /**
     * Sets if the room requires an invitation to enter. That is if the room is members-only.
     *
     * @param membersOnly if true then the room is members-only.
     * @return the list of updated presences of all the occupants that aren't members of the room if
     *         the room is now members-only.
     */
    public List<Presence> setMembersOnly(final boolean membersOnly, @Nonnull final Affiliation actorAffiliation, @Nullable final JID actorJid) throws ForbiddenException, NotAllowedException
    {
        if (actorAffiliation != Affiliation.owner) {
            throw new ForbiddenException();
        }

        final List<Presence> presences = new CopyOnWriteArrayList<>();
        final String actorNickname = findNickname(actorJid);

        if (membersOnly && !this.membersOnly) {
            Log.debug("Room '{}' was not members-only, but now is. Kick occupants that aren't a member.", getJID());
            final Set<JID> userAddressesToKick = getOccupants().stream()
                .filter(mucOccupant -> mucOccupant.getAffiliation().equals(Affiliation.none) || mucOccupant.getAffiliation().equals(Affiliation.outcast))
                .map(MUCOccupant::getUserAddress)
                .collect(Collectors.toSet());
            for (final JID userAddressToKick : userAddressesToKick) {
                presences.addAll(kickOccupant(userAddressToKick, actorAffiliation, null, actorJid, actorNickname, LocaleUtils.getLocalizedString("muc.roomIsNowMembersOnly"), 322));
            }
        }
        this.membersOnly = membersOnly;
        return presences;
    }

    /**
     * Returns true if the room's conversation is being logged. If logging is activated the room
     * conversation will be saved to the database every couple of minutes. The saving frequency is
     * the same for all the rooms and can be configured by changing the property
     * "xmpp.muc.tasks.log.timeout" of MultiUserChatServerImpl.
     *
     * @return true if the room's conversation is being logged.
     */
    public boolean isLogEnabled() {
        return logEnabled;
    }

    /**
     * Sets if the room's conversation is being logged. If logging is activated the room
     * conversation will be saved to the database every couple of minutes. The saving frequency is
     * the same for all the rooms and can be configured by changing the property
     * "xmpp.muc.tasks.log.timeout" of MultiUserChatServerImpl.
     *
     * @param logEnabled boolean that specified if the room's conversation must be logged.
     */
    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    /**
     * Sets if registered users can only join the room using their registered nickname. A
     * not_acceptable error will be returned if the user tries to join the room with a nickname
     * different from the reserved nickname.
     *
     * @param restricted if registered users can only join the room using their registered nickname.
     */
    public void setLoginRestrictedToNickname(boolean restricted) {
        this.loginRestrictedToNickname = restricted;
    }

    /**
     * Returns true if registered users can only join the room using their registered nickname. By
     * default, registered users can join the room using any nickname. A not_acceptable error
     * will be returned if the user tries to join the room with a nickname different from the
     * reserved nickname.
     *
     * @return true if registered users can only join the room using their registered nickname.
     */
    public boolean isLoginRestrictedToNickname() {
        return loginRestrictedToNickname;
    }

    /**
     * Sets if room occupants are allowed to change their nicknames in the room. By default,
     * occupants are allowed to change their nicknames. A not_acceptable error will be returned if
     * an occupant tries to change his nickname and this feature is not enabled.
     *
     * Notice that this feature is not supported by the MUC spec so answering a not_acceptable
     * error may break some clients.
     *
     * @param canChange if room occupants are allowed to change their nicknames in the room.
     */
    public void setChangeNickname(boolean canChange) {
        this.canChangeNickname = canChange;
    }

    /**
     * Returns true if room occupants are allowed to change their nicknames in the room. By
     * default, occupants are allowed to change their nicknames. A not_acceptable error will be
     * returned if an occupant tries to change his nickname and this feature is not enabled.
     *
     * Notice that this feature is not supported by the MUC spec so answering a not_acceptable
     * error may break some clients.
     *
     * @return true if room occupants are allowed to change their nicknames in the room.
     */
    public boolean canChangeNickname() {
        return canChangeNickname;
    }

    /**
     * Sets if users are allowed to register with the room. By default, room registration
     * is enabled. A not_allowed error will be returned if a user tries to register with the room
     * and this feature is disabled.
     *
     * @param registrationEnabled if users are allowed to register with the room.
     */
    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    /**
     * Returns true if users are allowed to register with the room. By default, room registration
     * is enabled. A not_allowed error will be returned if a user tries to register with the room
     * and this feature is disabled.
     *
     * @return true if users are allowed to register with the room.
     */
    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    /**
     * Sets if this room accepts FMUC joins. By default, FMUC functionality is not enabled.
     * When joining nodes are attempting a join, a rejection will be returned when this feature is disabled.
     */
    public void setFmucEnabled(boolean fmucEnabled) {
        this.fmucEnabled = fmucEnabled;
    }

    /**
     * Returns true if this room accepts FMUC joins. By default, FMUC functionality is not enabled.
     * When joining nodes are attempting a join, a rejection will be returned when this feature is disabled.
     */
    public boolean isFmucEnabled() {
        return fmucEnabled;
    }

    /**
     * Sets the address of the MUC room (typically on a remote XMPP domain) to which this room should initiate
     * FMUC federation. In this federation, the local node takes the role of the 'joining' node, while the remote node
     * takes the role of the 'joined' node.
     *
     * When this room is not expected to initiate federation (note that it can still accept inbound federation attempts)
     * then this method returns null.
     *
     * Although a room can accept multiple inbound joins (where it acts as a 'parent' node), it can initiate only one
     * outbound join at a time (where it acts as a 'child' node).
     *
     * @param fmucOutboundNode Address of peer for to-be-initiated outbound FMUC federation, possibly null.
     */
    public void setFmucOutboundNode( JID fmucOutboundNode ) {
        this.fmucOutboundNode = fmucOutboundNode;
    }

    /**
     * Returns the address of the MUC room (typically on a remote XMPP domain) to which this room should initiate
     * FMUC federation. In this federation, the local node takes the role of the 'joining' node, while the remote node
     * takes the role of the 'joined' node.
     *
     * When this room is not expected to initiate federation (note that it can still accept inbound federation attempts)
     * then this method returns null.
     *
     * Although a room can accept multiple inbound joins (where it acts as a 'parent' node), it can initiate only one
     * outbound join at a time (where it acts as a 'child' node).
     *
     * @return Address of peer for to-be-initiated outbound FMUC federation, possibly null.
     */
    public JID getFmucOutboundNode() {
        return fmucOutboundNode;
    }

    /**
     * Sets the 'mode' that describes the FMUC configuration is captured in the supplied object, which is
     * either master-master or master-slave.
     *
     * @param fmucOutboundMode FMUC mode applied to outbound FMUC federation attempts.
     */
    public void setFmucOutboundMode( FMUCMode fmucOutboundMode ) {
        this.fmucOutboundMode = fmucOutboundMode;
    }

    /**
     * Returns the 'mode' that describes the FMUC configuration is captured in the supplied object, which is
     * either master-master or master-slave.
     *
     * This method should return null only when no outbound federation should be attempted.
     *
     * @return FMUC mode applied to outbound FMUC federation attempts.
     */
    public FMUCMode getFmucOutboundMode() {
        return fmucOutboundMode;
    }

    /**
     * A set of addresses of MUC rooms (typically on a remote XMPP domain) that defines the list of rooms that is
     * permitted to federate with the local room.
     *
     * A null value is to be interpreted as allowing all rooms to be permitted.
     *
     * An empty set of addresses is to be interpreted as disallowing all rooms to be permitted.
     *
     * @param fmucInboundNodes A list of rooms allowed to join, possibly empty, possibly null
     */
    public void setFmucInboundNodes( Set<JID> fmucInboundNodes ) {
        this.fmucInboundNodes = fmucInboundNodes;
    }

    /**
     * A set of addresses of MUC rooms (typically on a remote XMPP domain) that defines the list of rooms that is
     * permitted to federate with the local room.
     *
     * A null value is to be interpreted as allowing all rooms to be permitted.
     *
     * An empty set of addresses is to be interpreted as disallowing all rooms to be permitted.
     *
     * @return A list of rooms allowed to join, possibly empty, possibly null
     */
    public Set<JID> getFmucInboundNodes() {
        return fmucInboundNodes;
    }

    /**
     * Returns the maximum number of occupants that can be simultaneously in the room. If the number
     * is zero then there is no limit.
     *
     * @return the maximum number of occupants that can be simultaneously in the room. Zero means
     *         unlimited number of occupants.
     */
    public int getMaxUsers() {
        return maxUsers;
    }

    /**
     * Sets the maximum number of occupants that can be simultaneously in the room. If the number
     * is zero then there is no limit.
     *
     * @param maxUsers the maximum number of occupants that can be simultaneously in the room. Zero
     *        means unlimited number of occupants.
     */
    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    /**
     * Returns if the room in which only those with "voice" may send messages to all occupants.
     *
     * @return if the room in which only those with "voice" may send messages to all occupants.
     */
    public boolean isModerated() {
        return moderated;
    }

    /**
     * Sets if the room in which only those with "voice" may send messages to all occupants.
     *
     * @param moderated if the room in which only those with "voice" may send messages to all
     *        occupants.
     */
    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    /**
     * Returns the password that the user must provide to enter the room.
     *
     * @return the password that the user must provide to enter the room.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password that the user must provide to enter the room.
     *
     * @param password the password that the user must provide to enter the room.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns true if a user cannot enter without first providing the correct password.
     *
     * @return true if a user cannot enter without first providing the correct password.
     */
    public boolean isPasswordProtected() {
        return password != null && password.trim().length() > 0;
    }

    /**
     * Returns true if the room is not destroyed if the last occupant exits. Persistent rooms are
     * saved to the database to make their configurations persistent together with the affiliation
     * of the users.
     *
     * @return true if the room is not destroyed if the last occupant exits.
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Returns true if the room has already been made persistent. If the room is temporary the
     * answer will always be false.
     *
     * @return true if the room has already been made persistent.
     */
    public boolean wasSavedToDB() {
        return isPersistent() && savedToDB;
    }

    /**
     * Sets if the room has already been made persistent.
     *
     * @param saved boolean that indicates if the room was saved to the database.
     */
    public void setSavedToDB(boolean saved) {
        this.savedToDB = saved;
    }

    /**
     * Sets if the room is not destroyed if the last occupant exits. Persistent rooms are
     * saved to the database to make their configurations persistent together with the affiliation
     * of the users.
     *
     * @param persistent if the room is not destroyed if the last occupant exits.
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Returns true if the room is searchable and visible through service discovery.
     *
     * @return true if the room is searchable and visible through service discovery.
     */
    public boolean isPublicRoom() {
        return !isDestroyed && publicRoom;
    }

    /**
     * Sets if the room is searchable and visible through service discovery.
     *
     * @param publicRoom if the room is searchable and visible through service discovery.
     */
    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    /**
     * Returns the list of roles of which presence will be broadcast to the rest of the occupants.
     * This feature is useful for implementing "invisible" occupants.
     *
     * @return the list of roles of which presence will be broadcast to the rest of the occupants.
     */
    @Nonnull
    public List<Role> getRolesToBroadcastPresence() {
        return Collections.unmodifiableList(rolesToBroadcastPresence);
    }

    /**
     * Sets the list of roles of which presence will be broadcast to the rest of the occupants.
     * This feature is useful for implementing "invisible" occupants.
     *
     * @param rolesToBroadcastPresence the list of roles of which presence will be broadcast to
     * the rest of the occupants.
     */
    public void setRolesToBroadcastPresence(@Nonnull final List<Role> rolesToBroadcastPresence) {
        // TODO If the list changes while there are occupants in the room we must send available or
        // unavailable presences of the affected occupants to the rest of the occupants
        this.rolesToBroadcastPresence = rolesToBroadcastPresence;
    }

    /**
     * Returns true if the presences of the requested role will be broadcast.
     *
     * @param roleToBroadcast the role to check if its presences will be broadcast.
     * @return true if the presences of the requested role will be broadcast.
     */
    public boolean canBroadcastPresence(@Nonnull final Role roleToBroadcast) {
        return Role.none.equals(roleToBroadcast) || rolesToBroadcastPresence.contains(roleToBroadcast);
    }

    /**
     * Locks the room so that users cannot join the room. Only the owner of the room can lock/unlock
     * the room.
     *
     * @param actor the occupant data of the user that locked the room.
     * @throws ForbiddenException If the user is not an owner of the room.
     */
    public void lock(MUCOccupant actor) throws ForbiddenException {
        if (Affiliation.owner != actor.getAffiliation()) {
            throw new ForbiddenException();
        }
        if (isLocked()) {
            // Do nothing if the room was already locked
            return;
        }
        setLocked(true);
    }

    /**
     * Unlocks the room so that users can join the room. The room is locked when created and only
     * the owner of the room can unlock it by sending the configuration form to the Multi-User Chat
     * service.
     *
     * @param actorAffiliation the room affiliation of the user that unlocked the room.
     * @throws ForbiddenException If the user is not an owner of the room.
     */
    public void unlock(@Nonnull final Affiliation actorAffiliation) throws ForbiddenException {
        if (Affiliation.owner != actorAffiliation) {
            throw new ForbiddenException();
        }
        if (!isLocked()) {
            // Do nothing if the room was already unlocked
            return;
        }
        setLocked(false);
    }

    private void setLocked(boolean locked) {
        if (locked) {
            this.lockedTime = System.currentTimeMillis();
        }
        else {
            this.lockedTime = 0;
        }
        MUCPersistenceManager.updateRoomLock(this);
    }

    /**
     * Sets the date when the room was locked. Initially when the room is created it is locked so
     * the locked date is the creation date of the room. Afterwards, the room may be manually
     * locked and unlocked so the locked date may be in these cases different from the creation
     * date. A Date with time 0 means that the room is unlocked.
     *
     * @param lockedTime the date when the room was locked.
     */
    public void setLockedDate(Date lockedTime) {
        this.lockedTime = lockedTime.getTime();
    }

    /**
     * Returns the date when the room was locked. Initially when the room is created it is locked so
     * the locked date is the creation date of the room. Afterwards, the room may be manually
     * locked and unlocked so the locked date may be in these cases different from the creation
     * date. When the room is unlocked a Date with time 0 is returned.
     *
     * @return the date when the room was locked.
     */
    public Date getLockedDate() {
        return new Date(lockedTime);
    }


    /**
     * Saves the room configuration to the DB. After the room has been saved to the DB it will
     * become persistent.
     */
    public void saveToDB() {
        // Make the room persistent
        MUCPersistenceManager.saveToDB(this);
        if (!savedToDB) {
            // Set that the room is now in the DB
            savedToDB = true;
            // Save the existing room owners to the DB
            for (JID owner : owners) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    owner,
                    null,
                    Affiliation.owner,
                    Affiliation.none);
            }
            // Save the existing room admins to the DB
            for (JID admin : admins) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    admin,
                    null,
                    Affiliation.admin,
                    Affiliation.none);
            }
            // Save the existing room members to the DB
            for (JID bareJID : members.keySet()) {
                MUCPersistenceManager.saveAffiliationToDB(this, bareJID, members.get(bareJID),
                    Affiliation.member, Affiliation.none);
            }
            // Save the existing room outcasts to the DB
            for (JID outcast : outcasts) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    outcast,
                    null,
                    Affiliation.outcast,
                    Affiliation.none);
            }
        }
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        // Approximate the size of the object in bytes by calculating the size of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();      // overhead of object
        size += CacheSizes.sizeOfCollection(occupants);
        size += CacheSizes.sizeOfString(name);
        size += CacheSizes.sizeOfAnything(selfOccupantData);
        size += CacheSizes.sizeOfLong();        // startTime
        size += CacheSizes.sizeOfLong();        // endTime
        size += CacheSizes.sizeOfBoolean();     // isDestroyed

        // The size of roomHistory is expensive to calculate. Use an estimation instead.
        size += 25 * 100;

        size += CacheSizes.sizeOfAnything(roomHistory);
        size += CacheSizes.sizeOfLong();        // lockedTime
        size += CacheSizes.sizeOfCollection(owners);
        size += CacheSizes.sizeOfCollection(admins);
        size += CacheSizes.sizeOfMap(members);
        size += CacheSizes.sizeOfCollection(outcasts);
        size += CacheSizes.sizeOfString(naturalLanguageName);
        size += CacheSizes.sizeOfString(description);
        size += CacheSizes.sizeOfBoolean();     // canOccupantsChangeSubject
        size += CacheSizes.sizeOfInt();         // maxUsers
        size += CacheSizes.sizeOfCollection(rolesToBroadcastPresence);
        size += CacheSizes.sizeOfBoolean();     // publicRoom
        size += CacheSizes.sizeOfBoolean();     // persistent
        size += CacheSizes.sizeOfBoolean();     // moderated
        size += CacheSizes.sizeOfBoolean();     // membersOnly
        size += CacheSizes.sizeOfBoolean();     // canOccupantsInvite
        size += CacheSizes.sizeOfString(password);
        size += CacheSizes.sizeOfBoolean();     // canAnyoneDiscoverJID
        size += CacheSizes.sizeOfString(canSendPrivateMessage);
        size += CacheSizes.sizeOfBoolean();     // logEnabled
        size += CacheSizes.sizeOfBoolean();     // loginRestrictedToNickname
        size += CacheSizes.sizeOfBoolean();     // canChangeNickname
        size += CacheSizes.sizeOfBoolean();     // registrationEnabled
        size += CacheSizes.sizeOfBoolean();     // fmucEnabled
        size += CacheSizes.sizeOfAnything(fmucOutboundNode);
        size += CacheSizes.sizeOfObject();      // fmucOutboundMode enum reference
        size += CacheSizes.sizeOfCollection(fmucInboundNodes);
        size += 1024; // Handwavy size of IQOwnerHandler (which holds sizeable data forms)
        size += CacheSizes.sizeOfObject() + CacheSizes.sizeOfObject() + CacheSizes.sizeOfBoolean(); // iqAdminHandler
        if (fmucHandler != null) {
            size += 2048; // Guestimate of fmucHandler
        }
        size += CacheSizes.sizeOfString(subject);
        size += CacheSizes.sizeOfLong();        // roomID
        size += CacheSizes.sizeOfDate();        // creationDate
        size += CacheSizes.sizeOfDate();        // modificationDate
        size += CacheSizes.sizeOfDate();        // emptyDate
        size += CacheSizes.sizeOfBoolean();     // savedToDB
        return size;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, name);
        ExternalizableUtil.getInstance().writeExternalizableCollection(out, occupants);
        ExternalizableUtil.getInstance().writeLong(out, startTime);
        ExternalizableUtil.getInstance().writeLong(out, endTime);
        ExternalizableUtil.getInstance().writeLong(out, lockedTime);
        ExternalizableUtil.getInstance().writeSerializableCollection(out, owners);
        ExternalizableUtil.getInstance().writeSerializableCollection(out, admins);
        ExternalizableUtil.getInstance().writeSerializableMap(out, members);
        ExternalizableUtil.getInstance().writeSerializableCollection(out, outcasts);
        ExternalizableUtil.getInstance().writeSafeUTF(out, naturalLanguageName);
        ExternalizableUtil.getInstance().writeSafeUTF(out, description);
        ExternalizableUtil.getInstance().writeBoolean(out, canOccupantsChangeSubject);
        ExternalizableUtil.getInstance().writeInt(out, maxUsers);
        ExternalizableUtil.getInstance().writeStringList(out, rolesToBroadcastPresence.stream().map(Enum::name).collect(Collectors.toList())); // This uses stringlist for compatibility with Openfire 4.6.0. Can be replaced the next major release.
        ExternalizableUtil.getInstance().writeBoolean(out, publicRoom);
        ExternalizableUtil.getInstance().writeBoolean(out, persistent);
        ExternalizableUtil.getInstance().writeBoolean(out, moderated);
        ExternalizableUtil.getInstance().writeBoolean(out, membersOnly);
        ExternalizableUtil.getInstance().writeBoolean(out, canOccupantsInvite);
        ExternalizableUtil.getInstance().writeSafeUTF(out, password);
        ExternalizableUtil.getInstance().writeBoolean(out, canAnyoneDiscoverJID);
        ExternalizableUtil.getInstance().writeSafeUTF(out, canSendPrivateMessage);
        ExternalizableUtil.getInstance().writeBoolean(out, logEnabled);
        ExternalizableUtil.getInstance().writeBoolean(out, loginRestrictedToNickname);
        ExternalizableUtil.getInstance().writeBoolean(out, canChangeNickname);
        ExternalizableUtil.getInstance().writeBoolean(out, registrationEnabled);
        ExternalizableUtil.getInstance().writeBoolean(out, fmucEnabled);
        ExternalizableUtil.getInstance().writeBoolean(out, fmucOutboundNode != null);
        if (fmucOutboundNode != null) {
            ExternalizableUtil.getInstance().writeSerializable(out, fmucOutboundNode);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, fmucOutboundMode != null);
        if (fmucOutboundMode != null) {
            ExternalizableUtil.getInstance().writeInt(out, fmucOutboundMode.ordinal());
        }
        ExternalizableUtil.getInstance().writeBoolean(out, fmucInboundNodes != null);
        if (fmucInboundNodes != null) {
            ExternalizableUtil.getInstance().writeSerializableCollection(out, fmucInboundNodes);
        }
        ExternalizableUtil.getInstance().writeSafeUTF(out, subject);
        ExternalizableUtil.getInstance().writeLong(out, roomID);
        ExternalizableUtil.getInstance().writeLong(out, creationDate.getTime());
        ExternalizableUtil.getInstance().writeBoolean(out, modificationDate != null);
        if (modificationDate != null) {
            ExternalizableUtil.getInstance().writeLong(out, modificationDate.getTime());
        }
        ExternalizableUtil.getInstance().writeBoolean(out, emptyDate != null);
        if (emptyDate != null) {
            ExternalizableUtil.getInstance().writeLong(out, emptyDate.getTime());
        }
        ExternalizableUtil.getInstance().writeBoolean(out, savedToDB);
        ExternalizableUtil.getInstance().writeSafeUTF(out, mucService.getServiceName());
        ExternalizableUtil.getInstance().writeSerializable(out, roomHistory);
        ExternalizableUtil.getInstance().writeSerializable(out, selfOccupantData);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        ExternalizableUtil.getInstance().readExternalizableCollection(in, occupants, getClass().getClassLoader());
        startTime = ExternalizableUtil.getInstance().readLong(in);
        endTime = ExternalizableUtil.getInstance().readLong(in);
        lockedTime = ExternalizableUtil.getInstance().readLong(in);
        ExternalizableUtil.getInstance().readSerializableCollection(in, owners, getClass().getClassLoader());
        ExternalizableUtil.getInstance().readSerializableCollection(in, admins, getClass().getClassLoader());
        ExternalizableUtil.getInstance().readSerializableMap(in, members, getClass().getClassLoader());
        ExternalizableUtil.getInstance().readSerializableCollection(in, outcasts, getClass().getClassLoader());
        naturalLanguageName = ExternalizableUtil.getInstance().readSafeUTF(in);
        description = ExternalizableUtil.getInstance().readSafeUTF(in);
        canOccupantsChangeSubject = ExternalizableUtil.getInstance().readBoolean(in);
        maxUsers = ExternalizableUtil.getInstance().readInt(in);
        rolesToBroadcastPresence.addAll(ExternalizableUtil.getInstance().readStringList(in).stream().map(Role::valueOf).collect(Collectors.toSet())); // This uses stringlist for compatibility with Openfire 4.6.0. Can be replaced the next major release.
        publicRoom = ExternalizableUtil.getInstance().readBoolean(in);
        persistent = ExternalizableUtil.getInstance().readBoolean(in);
        moderated = ExternalizableUtil.getInstance().readBoolean(in);
        membersOnly = ExternalizableUtil.getInstance().readBoolean(in);
        canOccupantsInvite = ExternalizableUtil.getInstance().readBoolean(in);
        password = ExternalizableUtil.getInstance().readSafeUTF(in);
        canAnyoneDiscoverJID = ExternalizableUtil.getInstance().readBoolean(in);
        canSendPrivateMessage = ExternalizableUtil.getInstance().readSafeUTF(in);
        logEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        loginRestrictedToNickname = ExternalizableUtil.getInstance().readBoolean(in);
        canChangeNickname = ExternalizableUtil.getInstance().readBoolean(in);
        registrationEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        fmucEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            fmucOutboundNode = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        } else {
            fmucOutboundNode = null;
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            final int i = ExternalizableUtil.getInstance().readInt(in);
            fmucOutboundMode = FMUCMode.values()[i];
        } else {
            fmucOutboundMode = null;
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            fmucInboundNodes = new HashSet<>();
            ExternalizableUtil.getInstance().readSerializableCollection(in, fmucInboundNodes, getClass().getClassLoader());
        } else {
            fmucInboundNodes = null;
        }
        subject = ExternalizableUtil.getInstance().readSafeUTF(in);
        roomID = ExternalizableUtil.getInstance().readLong(in);
        creationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            modificationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            emptyDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        }
        savedToDB = ExternalizableUtil.getInstance().readBoolean(in);
        String subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        if (mucService == null) throw new IllegalArgumentException("MUC service not found for subdomain: " + subdomain);
        roomHistory = new MUCRoomHistory(this, new HistoryStrategy(getJID(), mucService.getHistoryStrategy()));

        this.iqOwnerHandler = new IQOwnerHandler(this);
        this.iqAdminHandler = new IQAdminHandler(this);
        this.fmucHandler = new FMUCHandler(this);

        roomHistory = (MUCRoomHistory) ExternalizableUtil.getInstance().readSerializable(in);
        selfOccupantData = (MUCOccupant) ExternalizableUtil.getInstance().readSerializable(in);
    }

    public void updateConfiguration(MUCRoom otherRoom) {
        startTime = otherRoom.startTime;
        lockedTime = otherRoom.lockedTime;
        owners = otherRoom.owners;
        admins = otherRoom.admins;
        members = otherRoom.members;
        outcasts = otherRoom.outcasts;
        naturalLanguageName = otherRoom.naturalLanguageName;
        description = otherRoom.description;
        canOccupantsChangeSubject = otherRoom.canOccupantsChangeSubject;
        maxUsers = otherRoom.maxUsers;
        rolesToBroadcastPresence = otherRoom.rolesToBroadcastPresence;
        publicRoom = otherRoom.publicRoom;
        persistent = otherRoom.persistent;
        moderated = otherRoom.moderated;
        membersOnly = otherRoom.membersOnly;
        canOccupantsInvite = otherRoom.canOccupantsInvite;
        password = otherRoom.password;
        canAnyoneDiscoverJID = otherRoom.canAnyoneDiscoverJID;
        logEnabled = otherRoom.logEnabled;
        loginRestrictedToNickname = otherRoom.loginRestrictedToNickname;
        canChangeNickname = otherRoom.canChangeNickname;
        registrationEnabled = otherRoom.registrationEnabled;
        fmucHandler = otherRoom.fmucHandler;
        subject = otherRoom.subject;
        roomID = otherRoom.roomID;
        creationDate = otherRoom.creationDate;
        modificationDate = otherRoom.modificationDate;
        emptyDate = otherRoom.emptyDate;
        savedToDB = otherRoom.savedToDB;
        mucService = otherRoom.mucService;
    }

    @Override
    public String toString() {
        return "MUCRoom{" +
            "roomID=" + roomID +
            ", name='" + name + '\'' +
            ", occupants=" + occupants.size() +
            ", mucService=" + mucService +
            ", savedToDB=" + savedToDB +
            '}';
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.util.resultsetmanager.Result#getUID()
     */
    @Override
    public String getUID()
    {
        // name is unique for each one particular MUC service.
        return name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
            + ((creationDate == null) ? 0 : creationDate.hashCode());
        result = prime * result
            + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
            + ((password == null) ? 0 : password.hashCode());
        result = prime * result + (int) (roomID ^ (roomID >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MUCRoom other = (MUCRoom) obj;
        if (creationDate == null) {
            if (other.creationDate != null)
                return false;
        } else if (!creationDate.equals(other.creationDate))
            return false;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        return roomID==other.roomID;
    }

    // overrides for important Group events

    @Override
    public void groupDeleting(Group group, Map params) {
        // remove the group from this room's affiliations
        GroupJID groupJID = group.getJID();
        try {
            addNone(groupJID, getSelfRepresentation().getAffiliation());
        } catch (Exception ex) {
            Log.error("Failed to remove deleted group from affiliation lists: " + groupJID, ex);
        }
    }

    @Override
    public void groupModified(Group group, Map params) {
        // check the affiliation lists for the old group jid, replace with a new group jid
        if ("nameModified".equals(params.get("type"))) {
            GroupJID originalJID = (GroupJID) params.get("originalJID");
            GroupJID newJID = group.getJID();
            try {
                if (owners.contains(originalJID)) {
                    addOwner(newJID, getSelfRepresentation().getAffiliation());
                } else if (admins.contains(originalJID)) {
                    addAdmin(newJID, getSelfRepresentation().getAffiliation());
                } else if (outcasts.contains(originalJID)) {
                    addOutcast(newJID, null, null, getSelfRepresentation().getAffiliation(), getSelfRepresentation().getRole());
                } else if (members.containsKey(originalJID)) {
                    addMember(newJID, null, getSelfRepresentation().getAffiliation());
                }
                addNone(originalJID, getSelfRepresentation().getAffiliation());
            } catch (Exception ex) {
                Log.error("Failed to update group affiliation for " + newJID, ex);
            }
        }
    }

    @Override
    public void memberAdded(Group group, Map params) {
        applyAffiliationChangeAndSendPresence(new JID((String)params.get("member")));
    }

    @Override
    public void memberRemoved(Group group, Map params) {
        applyAffiliationChangeAndSendPresence(new JID((String)params.get("member")));
    }

    @Override
    public void adminAdded(Group group, Map params) {
        applyAffiliationChangeAndSendPresence(new JID((String)params.get("admin")));
    }

    @Override
    public void adminRemoved(Group group, Map params) {
        applyAffiliationChangeAndSendPresence(new JID((String)params.get("admin")));
    }

    private void applyAffiliationChangeAndSendPresence(JID groupMember) {
        final List<Presence> presences = applyAffiliationChange(groupMember, null, null, null);
        for (Presence presence : presences) {
            send(presence, this.getSelfRepresentation());
        }
    }

    @Override
    public void groupCreated(Group group, Map params) {
        // ignore
    }

    @Override
    public void userCreated(User user, Map<String, Object> params)
    {}

    @Override
    public void userDeleting(User user, Map<String, Object> params)
    {
        // When a user is being deleted, all its affiliations need to be removed from chat rooms (OF-2166). Note that
        // this event handler only works for rooms that are loaded into memory from the database. Corresponding code
        // in MultiUserChatManager will remove affiliations from rooms that are not in memory, but only in the database.
        final JID userJid = XMPPServer.getInstance().createJID(user.getUsername(), null);

        final Lock lock = getMUCService().getChatRoomLock(getJID().getNode());
        try {
            lock.lock();

            if (getAffiliation(userJid) == Affiliation.none) {
                // User had no affiliation with this room.
                return;
            }

            // Cannot remove the last owner of a chat room. To prevent issues, replace the owner with an administrative account.
            if (getOwners().contains(userJid) && getOwners().size() == 1) {
                final JID adminJid = XMPPServer.getInstance().getAdmins().iterator().next();
                Log.info("User '{}' is being deleted, but is also the only owner of MUC room '{}'. To prevent having a room without owner, server admin '{}' was made owner of the room.", user.getUsername(), getJID(), adminJid);
                addOwner(adminJid, getSelfRepresentation().getAffiliation());
            }

            // Remove the affiliation of the deleted user with the room
            addNone(userJid, getSelfRepresentation().getAffiliation());
            getMUCService().syncChatRoom(this);
        } catch (Throwable t) {
            Log.warn("A problem occurred while trying to update room '{}' as a result of user '{}' being deleted from Openfire.", getJID(), user);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void userModified(User user, Map<String, Object> params)
    {}
}
