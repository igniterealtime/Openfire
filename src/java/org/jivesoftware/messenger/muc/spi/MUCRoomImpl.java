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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.messenger.muc.*;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.spi.MessageImpl;
import org.jivesoftware.messenger.spi.PresenceImpl;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * Simple in-memory implementation of a chatroom. A MUCRoomImpl could represent a persistent room 
 * which means that its configuration will be maintained in synch with its representation in the 
 * database.
 * 
 * @author Gaston Dombiak
 */
public class MUCRoomImpl implements MUCRoom {

    /**
     * The timeout period to unlock a room. If the period expired the default means that the default
     * configuration was accepted for the room.
     */
    // TODO Set this variable from a default configuration. Add setters and getters.
    // Default value is 30 min ( 30(min) * 60(sec) * 1000(mill) )
    public static long LOCK_TIMEOUT = 1800000;

    /**
     * The server hosting the room.
     */
    private MultiUserChatServer server;

    /**
     * The occupants of the room accessible by the occupants nickname.
     */
    private Map<String,MUCRole> occupants = new ConcurrentHashMap<String, MUCRole>();

    /**
     * The occupants of the room accessible by the occupants bare JID.
     */
    private Map<String, List<MUCRole>> occupantsByBareJID = new ConcurrentHashMap<String, List<MUCRole>>();

    /**
     * The occupants of the room accessible by the occupants full JID.
     */
    private Map<String, MUCRole> occupantsByFullJID = new ConcurrentHashMap<String, MUCRole>();

    /**
     * The name of the room.
     */
    private String name;

    /**
     * A lock to protect the room occupants.
     */
    ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The role of the room itself.
     */
    private MUCRole role;

    /**
     * The router used to send packets for the room.
     */
    private PacketRouter router;

    /**
     * The start time of the chat.
     */
    long startTime;

    /**
     * The end time of the chat.
     */
    long endTime;

    /**
     * ChatRoomHistory object.
     */
    private MUCRoomHistory roomHistory;

    /**
     * Flag that indicates whether a room is locked or not.
     */
    private boolean roomLocked;

    /**
     * The time when the room was locked.
     */
    long lockedTime;

    /**
     * List of chatroom's owner. The list contains only bare jid.
     */
    List<String> owners = new ArrayList<String>();

    /**
     * List of chatroom's admin. The list contains only bare jid.
     */
    List<String> admins = new ArrayList<String>();

    /**
     * List of chatroom's members. The list contains only bare jid.
     */
    private Map<String, String> members = new ConcurrentHashMap<String,String>();

    /**
     * List of chatroom's outcast. The list contains only bare jid of not allowed users.
     */
    private List<String> outcasts = new ArrayList<String>();

    /**
     * Description of the room. The owner can change the description using the room configuration
     * form.
     */
    private String description;

    /**
     * Indicates if occupants are allowed to change the subject of the room. 
     */
    private boolean canOccupantsChangeSubject = false;

    /**
     * Maximum number of occupants that could be present in the room. If the limit's been reached
     * and a user tries to join, a not-allowed error will be returned.
     */
    private int maxUsers = 30;

    /**
     * List of roles of which presence will be broadcasted to the rest of the occupants. This
     * feature is useful for implementing "invisible" occupants.
     */
    private List rolesToBroadcastPresence = new ArrayList();

    /**
     * A public room means that the room is searchable and visible. This means that the room can be
     * located using disco requests.
     */
    private boolean publicRoom = true;

    /**
     * Persistent rooms are saved to the database so that when the last occupant leaves the room,
     * the room is removed from memory but it's configuration is saved in the database.
     */
    private boolean persistent = false;

    /**
     * Moderated rooms enable only participants to speak. Users that join the room and aren't
     * participants can't speak (they are just visitors).
     */
    private boolean moderated = false;

    /**
     * A room is considered members-only if an invitation is required in order to enter the room.
     * Any user that is not a member of the room won't be able to join the room unless the user
     * decides to register with the room (thus becoming a member).
     */
    private boolean invitationRequiredToEnter = false;

    /**
     * Some rooms may restrict the occupants that are able to send invitations. Sending an 
     * invitation in a members-only room adds the invitee to the members list.
     */
    private boolean canOccupantsInvite = false;

    /**
     * Indicates if the room is password protected.
     */
    private boolean passwordProtected = false;

    /**
     * The password that every occupant should provide in order to enter the room.
     */
    private String password = "";

    /**
     * Every presence packet can include the JID of every occupant unless the owner deactives this
     * configuration. 
     */
    private boolean canAnyoneDiscoverJID = false;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean logEnabled = false;

    /**
     * Internal component that handles IQ packets sent by the room owners.
     */
    private IQOwnerHandler iqOwnerHandler;

    /**
     * Internal component that handles IQ packets sent by moderators, admins and owners.
     */
    private IQAdminHandler iqAdminHandler;

    /**
     * The last known subject of the room. This information is used to respond disco requests. The
     * MUCRoomHistory class holds the history of the room together with the last message that set
     * the room's subject.
     */
    private String subject = "";
    
    /**
     * The ID of the room. If the room is temporary and does not log its conversation then the value
     * will always be -1. Otherwise a value will be obtained from the database.
     */
    private long roomID = -1;
    
    /**
     * Indicates if the room is present in the database.
     */
    private boolean savedToDB = false;

    /**
     * Create a new chat room.
     * 
     * @param chatserver the server hosting the room.
     * @param roomname the name of the room.
     * @param packetRouter the router for sending packets from the room.
     */
    MUCRoomImpl(MultiUserChatServer chatserver, String roomname, PacketRouter packetRouter) {
        this.server = chatserver;
        this.name = roomname;
        this.description = roomname;
        this.router = packetRouter;
        this.startTime = System.currentTimeMillis();
        // TODO Allow to set the history strategy from the configuration form?
        roomHistory = new MUCRoomHistory(this, new HistoryStrategy(server.getHistoryStrategy()));
        roomHistory.setStartTime(startTime);
        role = new RoomRole(this);
        this.iqOwnerHandler = new IQOwnerHandler(this, packetRouter);
        this.iqAdminHandler = new IQAdminHandler(this, packetRouter);
        // No one can join the room except the room's owner
        this.roomLocked = true;
        this.lockedTime = startTime;
        // Set the default roles for which presence is broadcast
        rolesToBroadcastPresence.add("moderator");
        rolesToBroadcastPresence.add("participant");
        rolesToBroadcastPresence.add("visitor");
        // If the room is persistent load the configuration values from the DB
        try {
            MUCPersistenceManager.loadFromDB(this);
            if (this.isPersistent()) {
                this.savedToDB = true;
                this.roomLocked = false;
            }
        }
        catch (IllegalArgumentException e) {
            // Do nothing. The room does not exist.
        }
    }

    public String getName() {
        return name;
    }

    public long getID() {
        if (isPersistent() || isLogEnabled()) {
            if (roomID == -1) {
                roomID = SequenceManager.nextID(JiveConstants.MUC_ROOM);
            }
        }
        return roomID;
    }

    public void setID(long roomID) {
        this.roomID = roomID;
    }

    public MUCRole getRole() {
        return role;
    }

    public MUCRole getOccupant(String nickname) throws UserNotFoundException {
        lock.readLock().lock();
        try {
            MUCRole role = occupants.get(nickname.toLowerCase());
            if (role != null) {
                return role;
            }
            throw new UserNotFoundException();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public List<MUCRole> getOccupantsByBareJID(String jid) throws UserNotFoundException {
        lock.readLock().lock();
        try {
            List<MUCRole> roles = occupantsByBareJID.get(jid);
            if (roles != null && !roles.isEmpty()) {
                return roles;
            }
            throw new UserNotFoundException();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public MUCRole getOccupantByFullJID(String jid) throws UserNotFoundException {
        lock.readLock().lock();
        try {
            MUCRole role = occupantsByFullJID.get(jid);
            if (role != null) {
                return role;
            }
            throw new UserNotFoundException();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<MUCRole> getOccupants() throws UnauthorizedException {
        lock.readLock().lock();
        try {
            List<MUCRole> list = new ArrayList<MUCRole>(occupants.values());
            return list.iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public int getOccupantsCount() {
        return occupants.size();
    }

    public boolean hasOccupant(String nickname) throws UnauthorizedException {
        lock.readLock().lock();
        try {
            return occupants.containsKey(nickname.toLowerCase());
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public String getReservedNickname(String bareJID) {
        lock.readLock().lock();
        try {
            String answer = members.get(bareJID);
            if (answer == null || answer.trim().length() == 0) {
                return null;
            }
            return answer;
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public int getAffiliation(String bareJID) {
        if (owners.contains(bareJID)) {
            return MUCRole.OWNER;
        }
        else if (admins.contains(bareJID)) {
            return MUCRole.ADMINISTRATOR;
        }
        else if (members.containsKey(bareJID)) {
            return MUCRole.MEMBER;
        }
        else if (outcasts.contains(bareJID)) {
            return MUCRole.OUTCAST;
        }
        return MUCRole.NONE;
    }

    public MUCRole joinRoom(String nickname, String password, HistoryRequest historyRequest,
            MUCUser user) throws UnauthorizedException, UserAlreadyExistsException,
            RoomLockedException, ForbiddenException, RegistrationRequiredException,
            NotAllowedException, ConflictException {
        MUCRoleImpl joinRole = null;
        lock.writeLock().lock();
        try {
            // If the room has a limit of max user then check if the limit was reached
            if (getMaxUsers() > 0 && getOccupantsCount() >= getMaxUsers()) {
                throw new NotAllowedException();
            }
            boolean isOwner = owners.contains(user.getAddress().toBareString());
            // If the room is locked and this user is not an owner raise a RoomLocked exception
            if (roomLocked) {
                if (!isOwner) {
                    throw new RoomLockedException();
                }
            }
            // If the user is already in the room raise a UserAlreadyExists exception
            if (occupants.containsKey(nickname.toLowerCase())) {
                throw new UserAlreadyExistsException();
            }
            // If the room is password protected and the provided password is incorrect raise a
            // Unauthorized exception
            if (isPasswordProtected()) {
                if (password == null || !password.equals(getPassword())) {
                    throw new UnauthorizedException();
                }
            }
            // If another user attempts to join the room with a nickname reserved by the first user
            // raise a ConflictException
            if (members.containsValue(nickname)) {
                if (!nickname.equals(members.get(user.getAddress().toBareStringPrep()))) {
                    throw new ConflictException();
                }
            }

            // Set the corresponding role based on the user's affiliation
            int role;
            int affiliation;
            if (isOwner) {
                // The user is an owner. Set the role and affiliation accordingly.
                role = MUCRole.MODERATOR;
                affiliation = MUCRole.OWNER;
            }
            else if (server.getSysadmins().contains(user.getAddress().toBareString())) {
                // The user is a system administrator of the MUC service. Treat him as an owner 
                // although he won't appear in the list of owners
                role = MUCRole.MODERATOR;
                affiliation = MUCRole.OWNER;
            }
            else if (admins.contains(user.getAddress().toBareString())) {
                // The user is an admin. Set the role and affiliation accordingly.
                role = MUCRole.MODERATOR;
                affiliation = MUCRole.ADMINISTRATOR;
            }
            else if (members.containsKey(user.getAddress().toBareString())) {
                // The user is a member. Set the role and affiliation accordingly.
                role = MUCRole.PARTICIPANT;
                affiliation = MUCRole.MEMBER;
            }
            else if (outcasts.contains(user.getAddress().toBareString())) {
                // The user is an outcast. Raise a "Forbidden" exception.
                throw new ForbiddenException();
            }
            else {
                // The user has no affiliation (i.e. NONE). Set the role accordingly.
                if (isInvitationRequiredToEnter()) {
                    // The room is members-only and the user is not a member. Raise a
                    // "Registration Required" exception.
                    throw new RegistrationRequiredException();
                }
                role = (isModerated() ? MUCRole.VISITOR : MUCRole.PARTICIPANT);
                affiliation = MUCRole.NONE;
            }
            // Create a new role for this user in this room
            joinRole = new MUCRoleImpl(server, this, nickname, role, affiliation,
                    (MUCUserImpl) user, router);

            // Handle ChatRoomHistory
            if (roomHistory.getUserID() == null) {
                roomHistory.setUserID(nickname);
            }
            else {
                roomHistory.userJoined(user, new java.util.Date());
            }

            // Send presence of existing occupants to new occupant
            Iterator iter = occupants.values().iterator();
            while (iter.hasNext()) {
                MUCRole occupantsRole = (MUCRole) iter.next();
                Presence occupantsPresence = (Presence) occupantsRole.getPresence()
                        .createDeepCopy();
                occupantsPresence.setSender(occupantsRole.getRoleAddress());
                // Don't include the occupant's JID if the room is semi-anon and the new occupant
                // is not a moderator
                if (!canAnyoneDiscoverJID() && MUCRole.MODERATOR != joinRole.getRole()) {
                    MetaDataFragment frag = (MetaDataFragment) occupantsPresence.getFragment(
                            "x",
                            "http://jabber.org/protocol/muc#user");
                    frag.deleteProperty("x.item:jid");
                }
                joinRole.send(occupantsPresence);
            }
            // Add the new user as an occupant of this room
            occupants.put(nickname.toLowerCase(), joinRole);
            // Update the tables of occupants based on the bare and full JID
            List<MUCRole> list = occupantsByBareJID.get(user.getAddress().toBareStringPrep());
            if (list == null) {
                list = new ArrayList<MUCRole>();
                occupantsByBareJID.put(user.getAddress().toBareStringPrep(), list);
            }
            list.add(joinRole);
            occupantsByFullJID.put(user.getAddress().toStringPrep(), joinRole);
        }
        finally {
            lock.writeLock().unlock();
        }
        if (joinRole != null) {
            // It is assumed that the room is new based on the fact that it's locked and
            // it has only one occupants (the owner).
            boolean isRoomNew = roomLocked && occupants.size() == 1;
            List params = new ArrayList();
            params.add(nickname);
            try {
                // Send the presence of this new occupant to existing occupants
                Presence joinPresence = (Presence) joinRole.getPresence().createDeepCopy();
                if (isRoomNew) {
                    MetaDataFragment frag = (MetaDataFragment) joinPresence.getFragment(
                            "x",
                            "http://jabber.org/protocol/muc#user");
                    frag.setProperty("x.status:code", "201");
                }
                joinPresence.setSender(joinRole.getRoleAddress());
                broadcastPresence(joinPresence);

            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            // Send the "user has joined" message only if the presence of the occupant was sent
            if (canBroadcastPresence(joinRole.getRoleAsString())) {
                serverBroadcast(LocaleUtils.getLocalizedString("muc.join", params));
            }
            // If the room has just been created send the "room locked until configuration is
            // confirmed" message
            if (isRoomNew) {
                Message message = new MessageImpl();
                message.setType(Message.GROUP_CHAT);
                message.setBody(LocaleUtils.getLocalizedString("muc.locked"));
                message.setSender(role.getRoleAddress());
                message.setRecipient(user.getAddress());
                router.route(message);
            }
            else if (canAnyoneDiscoverJID()) {
                // Warn the new occupant that the room is non-anonymous (i.e. his JID will be
                // public)
                Message message = new MessageImpl();
                message.setType(Message.GROUP_CHAT);
                message.setBody(LocaleUtils.getLocalizedString("muc.warnnonanonymous"));
                message.setSender(role.getRoleAddress());
                message.setRecipient(user.getAddress());
                MetaDataFragment frag = new MetaDataFragment("http://jabber.org/protocol/muc#user",
                        "x");
                frag.setProperty("x.status:code", "100");
                message.addFragment(frag);
                router.route(message);
            }
            if (historyRequest == null) {
                Iterator history = roomHistory.getMessageHistory();
                while (history.hasNext()) {
                    joinRole.send((Message) history.next());
                }
            }
            else {
                historyRequest.sendHistory(joinRole, roomHistory);
            }
        }
        return joinRole;
    }

    public void leaveRoom(String nickname) throws UnauthorizedException, UserNotFoundException {
        MUCRole leaveRole = null;
        lock.writeLock().lock();
        try {
            leaveRole = occupants.remove(nickname.toLowerCase());
            if (leaveRole == null) {
                throw new UserNotFoundException();
            }
            // Removes the role from the room
            removeOccupantRole(leaveRole);

            // TODO Implement this: If the room owner becomes unavailable for any reason before
            // submitting the form (e.g., a lost connection), the service will receive a presence
            // stanza of type "unavailable" from the owner to the room@service/nick or room@service
            // (or both). The service MUST then destroy the room, sending a presence stanza of type
            // "unavailable" from the room to the owner including a <destroy/> element and reason
            // (if provided) as defined under the "Destroying a Room" use case.

            if (occupants.isEmpty()) {
                // Adding new ChatTranscript logic.
                endTime = System.currentTimeMillis();

                // Update RoomHistory with HistoryStrategy
                roomHistory.setEndTime(System.currentTimeMillis());

                // Update ChatManager
                //ChatManager cManager = ChatManager.getInstance();
                //cManager.addChatHistory(roomHistory);
                //cManager.fireChatRoomClosed(server.getChatRoom(name));

                server.removeChatRoom(name);
            }
        }
        finally {
            lock.writeLock().unlock();
        }

        if (leaveRole != null) {
            try {
                Presence presence = createPresence(Presence.STATUS_OFFLINE);
                presence.setSender(leaveRole.getRoleAddress());
                presence.addFragment(leaveRole.getExtendedPresenceInformation());
                broadcastPresence((Presence) presence.createDeepCopy());
                leaveRole.kick();
                List params = new ArrayList();
                params.add(nickname);
                // Send the "user has left" message only if the presence of the occupant was sent
                if (canBroadcastPresence(leaveRole.getRoleAsString())) {
                    serverBroadcast(LocaleUtils.getLocalizedString("muc.leave", params));
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }

    /**
     * @param leaveRole
     */
    private void removeOccupantRole(MUCRole leaveRole) {
        occupants.remove(leaveRole.getNickname().toLowerCase());

        MUCUser user = leaveRole.getChatUser();
        // Update the tables of occupants based on the bare and full JID
        List list = occupantsByBareJID.get(user.getAddress().toBareStringPrep());
        if (list != null) {
            list.remove(leaveRole);
            if (list.isEmpty()) {
                occupantsByBareJID.remove(user.getAddress().toBareStringPrep());
            }
        }
        occupantsByFullJID.remove(user.getAddress().toStringPrep());

        roomHistory.userLeft(user, new java.util.Date());
    }

    public void destroyRoom(String alternateJID, String reason) throws UnauthorizedException {
        MUCRole leaveRole = null;
        lock.writeLock().lock();
        try {
            // Remove each occupant
            for (String nickname: occupants.keySet()) {
                leaveRole = occupants.remove(nickname);

                if (leaveRole != null) {
                    try {
                        // Send a presence stanza of type "unavailable" to the occupant
                        Presence presence = createPresence(Presence.STATUS_OFFLINE);
                        presence.setSender(leaveRole.getRoleAddress());
                        presence.setRecipient(leaveRole.getChatUser().getAddress());

                        // A fragment containing the x-extension for room destruction.
                        // TODO Analyze if we need/can reuse the same fragment instead of creating a
                        // new one each time
                        MetaDataFragment fragment;
                        fragment = new MetaDataFragment("http://jabber.org/protocol/muc#user", "x");
                        fragment.setProperty("x.item:affiliation", "none");
                        fragment.setProperty("x.item:role", "none");
                        if (alternateJID != null && alternateJID.length() > 0) {
                            fragment.setProperty("x.destroy:jid", alternateJID);
                        }
                        if (reason != null && reason.length() > 0) {
                            fragment.setProperty("x.destroy.reason", reason);
                        }
                        presence.addFragment(fragment);

                        router.route(presence);
                        leaveRole.kick();
                    }
                    catch (Exception e) {
                        Log.error(e);
                    }
                }
            }

            // Adding new ChatTranscript logic.
            endTime = System.currentTimeMillis();

            // Update RoomHistory with HistoryStrategy
            roomHistory.setEndTime(System.currentTimeMillis());

            // Update ChatAuditManager
            //ChatAuditManager cManager = ChatAuditManager.getInstance();
            //cManager.addChatHistory(roomHistory);
            //cManager.fireChatRoomClosed(server.getChatRoom(name));

            MUCPersistenceManager.deleteFromDB(this);
            server.removeChatRoom(name);
        }
        finally {
            lock.writeLock().unlock();
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
        roomHistory.addMessage(message);
        broadcast(message);
    }

    public void sendPublicMessage(Message message, MUCRole senderRole)
            throws UnauthorizedException, ForbiddenException {
        // Check that if the room is moderated then the sender of the message has to have voice
        if (isModerated() && senderRole.getRole() > MUCRole.PARTICIPANT) {
            throw new ForbiddenException();
        }
        // Send the message to all occupants
        message.setSender(senderRole.getRoleAddress());
        send(message);
    }

    public void sendPrivateMessage(Message message, MUCRole senderRole) throws NotFoundException {
        String resource = message.getRecipient().getResource();
        MUCRole occupant = occupants.get(resource.toLowerCase());
        if (occupant != null) {
            message.setSender(senderRole.getRoleAddress());
            message.setRecipient(occupant.getChatUser().getAddress());
            router.route(message);
        }
        else {
            throw new NotFoundException();
        }
    }

    public void send(Message packet) throws UnauthorizedException {
        // normal groupchat
        roomHistory.addMessage(packet);
        broadcast(packet);
    }

    public void send(Presence packet) throws UnauthorizedException {
        broadcastPresence(packet);
    }

    public void send(IQ packet) throws UnauthorizedException {
        packet = (IQ) packet.createDeepCopy();
        packet.setError(XMPPError.Code.BAD_REQUEST);
        packet.setRecipient(packet.getSender());
        packet.setSender(role.getRoleAddress());
        router.route(packet);
    }

    private void broadcastPresence(Presence presence) {
        if (presence == null) {
            return;
        }

        MetaDataFragment frag = null;
        String jid = null;

        if (hasToCheckRoleToBroadcastPresence()) {
            frag = (MetaDataFragment) presence.getFragment(
                    "x",
                    "http://jabber.org/protocol/muc#user");
            // Check if we can broadcast the presence for this role
            if (!canBroadcastPresence(frag.getProperty("x.item:role"))) {
                // Just send the presence to the sender of the presence
                try {
                    MUCRole occupant = getOccupant(presence.getSender().getResourcePrep());
                    presence.setRecipient(occupant.getChatUser().getAddress());
                    router.route(presence);
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
                return;
            }
        }

        // Don't include the occupant's JID if the room is semi-anon and the new occupant
        // is not a moderator
        if (!canAnyoneDiscoverJID()) {
            if (frag == null) {
                frag = (MetaDataFragment) presence.getFragment(
                        "x",
                        "http://jabber.org/protocol/muc#user");
            }
            jid = frag.getProperty("x.item:jid");
        }
        lock.readLock().lock();
        try {
            for (MUCRole occupant : occupants.values()) {
                presence.setRecipient(occupant.getChatUser().getAddress());
                // Don't include the occupant's JID if the room is semi-anon and the new occupant
                // is not a moderator
                if (!canAnyoneDiscoverJID()) {
                    if (MUCRole.MODERATOR == occupant.getRole()) {
                        frag.setProperty("x.item:jid", jid);
                    }
                    else {
                        frag.deleteProperty("x.item:jid");
                    }
                }
                router.route(presence);
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    private void broadcast(Message message) {
        lock.readLock().lock();
        try {
            Iterator itr = occupants.values().iterator();
            while (itr.hasNext()) {
                MUCRole occupant = (MUCRole) itr.next();
                message.setRecipient(occupant.getChatUser().getAddress());
                router.route(message);
            }
            if (isLogEnabled()) {
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    MUCRole senderRole;
                    XMPPAddress senderAddress;
                    senderRole = occupants.get(message.getSender().getResourcePrep());
                    if (senderRole == null) {
                        // The room itself is sending the message 
                        senderAddress = getRole().getRoleAddress();
                    }
                    else {
                        // An occupant is sending the message 
                        senderAddress = senderRole.getChatUser().getAddress(); 
                    }
                    // Log the conversation
                    server.logConversation(this, message, senderAddress);
                }
                finally {
                    lock.writeLock().unlock();
                    lock.readLock().lock();
                }
            }
        }
        finally {
            lock.readLock().unlock();
        }
    }

    /**
     * An empty role that represents the room itself in the chatroom. Chatrooms need to be able to
     * speak (server messages) and so must have their own role in the chatroom.
     */
    private class RoomRole implements MUCRole {

        private MUCRoom room;

        private RoomRole(MUCRoom room) {
            this.room = room;
        }

        public Presence getPresence() throws UnauthorizedException {
            return null;
        }

        public MetaDataFragment getExtendedPresenceInformation() throws UnauthorizedException {
            return null;
        }

        public void setPresence(Presence presence) throws UnauthorizedException {
        }

        public void setRole(int newRole) throws UnauthorizedException {
        }

        public int getRole() {
            return MUCRole.MODERATOR;
        }

        public String getRoleAsString() {
            return "moderator";
        }

        public void setAffiliation(int newAffiliation) throws UnauthorizedException {
        }

        public int getAffiliation() {
            return MUCRole.OWNER;
        }

        public String getAffiliationAsString() {
            return "owner";
        }

        public String getNickname() {
            return null;
        }

        public void kick() throws UnauthorizedException {
        }

        public MUCUser getChatUser() {
            return null;
        }

        public MUCRoom getChatRoom() {
            return room;
        }

        private XMPPAddress crJID = null;

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

        public void changeNickname(String nickname) {
        }
    }

    public long getChatLength() {
        return endTime - startTime;
    }

    /**
     * Updates all the presences of the given user with the new affiliation and role information. Do
     * nothing if the given jid is not present in the room. If the user has joined the room from
     * several client resources, all his/her occupants' presences will be updated.
     * 
     * @param bareJID the bare jid of the user to update his/her role.
     * @param newAffiliation the new affiliation for the JID.
     * @param newRole the new role for the JID.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     */
    private List changeAffiliationOfOccupant(String bareJID, int newAffiliation, int newRole)
            throws NotAllowedException {
        List presences = new ArrayList();
        // Get all the roles (i.e. occupants) of this user based on his/her bare JID
        List roles = occupantsByBareJID.get(bareJID);
        if (roles == null) {
            return presences;
        }
        MUCRole role;
        // Collect all the updated presences of these roles
        for (Iterator it = roles.iterator(); it.hasNext();) {
            try {
                role = (MUCRole) it.next();
                // Update the presence with the new affiliation and role
                role.setAffiliation(newAffiliation);
                role.setRole(newRole);
                // Prepare a new presence to be sent to all the room occupants
                Presence presence = (Presence) role.getPresence().createDeepCopy();
                presence.setSender(role.getRoleAddress());
                presences.add(presence);
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
        }
        // Answer all the updated presences
        return presences;
    }

    /**
     * Updates the presence of the given user with the new role information. Do nothing if the given
     * jid is not present in the room.
     * 
     * @param fullJID the full jid of the user to update his/her role.
     * @param newRole the new role for the JID.
     * @return the updated presence of the user or null if none.
     */
    private Presence changeRoleOfOccupant(String fullJID, int newRole) throws NotAllowedException {
        // Try looking the role in the bare JID list
        MUCRole role = occupantsByFullJID.get(fullJID);
        if (role != null) {
            try {
                // Update the presence with the new role
                role.setRole(newRole);
                // Prepare a new presence to be sent to all the room occupants
                Presence presence = (Presence) role.getPresence().createDeepCopy();
                presence.setSender(role.getRoleAddress());
                return presence;
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
        }
        return null;
    }

    public void addFirstOwner(String bareJID) {
        owners.add(bareJID);
    }

    public List addOwner(String bareJID, MUCRole sendRole) throws ForbiddenException {
        int oldAffiliation = MUCRole.NONE;
        if (MUCRole.OWNER != sendRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        owners.add(bareJID);
        // Remove the user from other affiliation lists
        if (removeAdmin(bareJID)) {
            oldAffiliation = MUCRole.ADMINISTRATOR;
        }
        else if (removeMember(bareJID)) {
            oldAffiliation = MUCRole.MEMBER;
        }
        else if (removeOutcast(bareJID)) {
            oldAffiliation = MUCRole.OUTCAST;
        }
        // Update the DB if the room is persistent
        MUCPersistenceManager.saveAffiliationToDB(
            this,
            bareJID,
            null,
            MUCRole.OWNER,
            oldAffiliation);
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeAffiliationOfOccupant(bareJID, MUCRole.OWNER, MUCRole.MODERATOR);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeOwner(String bareJID) {
        return owners.remove(bareJID);
    }

    public List addAdmin(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException {
        int oldAffiliation = MUCRole.NONE;
        if (MUCRole.OWNER != sendRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        // Check that the room always has an owner
        if (owners.contains(bareJID) && owners.size() == 1) {
            throw new ConflictException();
        }
        admins.add(bareJID);
        // Remove the user from other affiliation lists
        if (removeOwner(bareJID)) {
            oldAffiliation = MUCRole.OWNER;
        }
        else if (removeMember(bareJID)) {
            oldAffiliation = MUCRole.MEMBER;
        }
        else if (removeOutcast(bareJID)) {
            oldAffiliation = MUCRole.OUTCAST;
        }
        // Update the DB if the room is persistent
        MUCPersistenceManager.saveAffiliationToDB(
            this,
            bareJID,
            null,
            MUCRole.ADMINISTRATOR,
            oldAffiliation);
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeAffiliationOfOccupant(bareJID, MUCRole.ADMINISTRATOR, MUCRole.MODERATOR);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeAdmin(String bareJID) {
        return admins.remove(bareJID);
    }

    public List addMember(String bareJID, String nickname, MUCRole sendRole)
            throws ForbiddenException, ConflictException {
        int oldAffiliation = (members.containsKey(bareJID) ? MUCRole.MEMBER : MUCRole.NONE);
        if (isInvitationRequiredToEnter()) {
            if (!canOccupantsInvite()) {
                if (MUCRole.ADMINISTRATOR != sendRole.getAffiliation()
                        && MUCRole.OWNER != sendRole.getAffiliation()) {
                    throw new ForbiddenException();
                }
            }
        }
        else {
            if (MUCRole.ADMINISTRATOR != sendRole.getAffiliation()
                    && MUCRole.OWNER != sendRole.getAffiliation()) {
                throw new ForbiddenException();
            }
        }
        // Check if the desired nickname is already reserved for another member
        if (nickname != null && nickname.trim().length() > 0 && members.containsValue(nickname)) {
            if (!nickname.equals(members.get(bareJID))) {
                throw new ConflictException();
            }
        }
        // Check that the room always has an owner
        if (owners.contains(bareJID) && owners.size() == 1) {
            throw new ConflictException();
        }
        // Associate the reserved nickname with the bareJID. If nickname is null then associate an
        // empty string
        members.put(bareJID, (nickname == null ? "" : nickname));
        // Remove the user from other affiliation lists
        if (removeOwner(bareJID)) {
            oldAffiliation = MUCRole.OWNER;
        }
        else if (removeAdmin(bareJID)) {
            oldAffiliation = MUCRole.ADMINISTRATOR;
        }
        else if (removeOutcast(bareJID)) {
            oldAffiliation = MUCRole.OUTCAST;
        }
        // Update the DB if the room is persistent
        MUCPersistenceManager.saveAffiliationToDB(
            this,
            bareJID,
            nickname,
            MUCRole.MEMBER,
            oldAffiliation);
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeAffiliationOfOccupant(bareJID, MUCRole.MEMBER, MUCRole.PARTICIPANT);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeMember(String bareJID) {
        boolean answer = members.containsKey(bareJID);
        members.remove(bareJID);
        return answer;
    }

    public List addOutcast(String bareJID, String reason, MUCRole senderRole)
            throws NotAllowedException, ForbiddenException, ConflictException {
        int oldAffiliation = MUCRole.NONE;
        if (MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                && MUCRole.OWNER != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        // Check that the room always has an owner
        if (owners.contains(bareJID) && owners.size() == 1) {
            throw new ConflictException();
        }
        // Update the presence with the new affiliation and inform all occupants
        String actorJID = senderRole.getChatUser().getAddress().toBareStringPrep();
        List updatedPresences = changeAffiliationOfOccupant(
                bareJID,
                MUCRole.OUTCAST,
                MUCRole.NONE_ROLE);
        if (!updatedPresences.isEmpty()) {
            Presence presence;
            MetaDataFragment frag;
            // Add the status code and reason why the user was banned to the presences that will
            // be sent to the room occupants (the banned user will not receive this presences)
            for (Iterator it = updatedPresences.iterator(); it.hasNext();) {
                presence = (Presence) it.next();
                frag = (MetaDataFragment) presence.getFragment(
                        "x",
                        "http://jabber.org/protocol/muc#user");
                // Add the status code 301 that indicates that the user was banned
                frag.setProperty("x.status:code", "301");
                // Add the reason why the user was banned
                if (reason != null && reason.trim().length() > 0) {
                    frag.setProperty("x.item.reason", reason);
                }

                // Remove the banned users from the room. If a user has joined the room from
                // different client resources, he/she will be kicked from all the client resources
                // Effectively kick the occupant from the room
                kickPresence(presence, actorJID);
            }
        }
        // Update the affiliation lists
        outcasts.add(bareJID);
        // Remove the user from other affiliation lists
        if (removeOwner(bareJID)) {
            oldAffiliation = MUCRole.OWNER;
        }
        else if (removeAdmin(bareJID)) {
            oldAffiliation = MUCRole.ADMINISTRATOR;
        }
        else if (removeMember(bareJID)) {
            oldAffiliation = MUCRole.MEMBER;
        }
        // Update the DB if the room is persistent
        MUCPersistenceManager.saveAffiliationToDB(
            this,
            bareJID,
            null,
            MUCRole.OUTCAST,
            oldAffiliation);
        return updatedPresences;
    }

    private boolean removeOutcast(String bareJID) {
        return outcasts.remove(bareJID);
    }

    public List addNone(String bareJID, MUCRole senderRole) throws ForbiddenException,
            ConflictException {
        int oldAffiliation = MUCRole.NONE;
        if (MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                && MUCRole.OWNER != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        // Check that the room always has an owner
        if (owners.contains(bareJID) && owners.size() == 1) {
            throw new ConflictException();
        }
        List updatedPresences = null;
        boolean wasMember = members.containsKey(bareJID);
        // Remove the user from ALL the affiliation lists
        if (removeOwner(bareJID)) {
            oldAffiliation = MUCRole.OWNER;
        }
        else if (removeAdmin(bareJID)) {
            oldAffiliation = MUCRole.ADMINISTRATOR;
        }
        else if (removeMember(bareJID)) {
            oldAffiliation = MUCRole.MEMBER;
        }
        else if (removeOutcast(bareJID)) {
            oldAffiliation = MUCRole.OUTCAST;
        }
        // Remove the affiliation of this user from the DB if the room is persistent
        MUCPersistenceManager.removeAffiliationFromDB(this, bareJID, oldAffiliation);

        // Update the presence with the new affiliation and inform all occupants
        try {
            int newRole;
            if (isInvitationRequiredToEnter() && wasMember) {
                newRole = MUCRole.NONE_ROLE;
            }
            else {
                newRole = isModerated() ? MUCRole.VISITOR : MUCRole.PARTICIPANT;
            }
            updatedPresences = changeAffiliationOfOccupant(bareJID, MUCRole.NONE, newRole);
            if (isInvitationRequiredToEnter() && wasMember) {
                // If the room is members-only, remove the user from the room including a status
                // code of 321 to indicate that the user was removed because of an affiliation
                // change
                Presence presence;
                MetaDataFragment frag;
                // Add the status code to the presences that will be sent to the room occupants
                for (Iterator it = updatedPresences.iterator(); it.hasNext();) {
                    presence = (Presence) it.next();
                    // Set the presence as an unavailable presence
                    try {
                        presence.setAvailable(false);
                        presence.setVisible(false);
                    }
                    catch (UnauthorizedException e) {
                    }
                    frag = (MetaDataFragment) presence.getFragment(
                            "x",
                            "http://jabber.org/protocol/muc#user");
                    // Add the status code 321 that indicates that the user was removed because of
                    // an affiliation change
                    frag.setProperty("x.status:code", "321");

                    // Remove the ex-member from the room. If a user has joined the room from
                    // different client resources, he/she will be kicked from all the client
                    // resources.
                    // Effectively kick the occupant from the room
                    MUCUser senderUser = senderRole.getChatUser();
                    String actorJID = (senderUser == null ?
                            null : senderUser.getAddress().toBareStringPrep());
                    kickPresence(presence, actorJID);
                }
            }
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
        }
        return updatedPresences;
    }

    public boolean isLocked() {
        if (System.currentTimeMillis() - startTime > LOCK_TIMEOUT) {
            // Unlock the room. The default configuration is assumed to be accepted by the owner.
            roomLocked = false;
        }
        return roomLocked;
    }

    public void nicknameChanged(String oldNick, String newNick) {
        lock.writeLock().lock();
        try {
            occupants.put(newNick.toLowerCase(), occupants.get(oldNick.toLowerCase()));
            occupants.remove(oldNick.toLowerCase());
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void changeSubject(Message packet, MUCRole role) throws UnauthorizedException,
            ForbiddenException {
        if (canOccupantsChangeSubject() || MUCRole.MODERATOR == role.getRole()) {
            lock.writeLock().lock();
            try {
                // Set the new subject to the room
                subject = packet.getSubject();
                MUCPersistenceManager.updateRoomSubject(this, subject);
            }
            finally {
                lock.writeLock().unlock();
            }
            // Notify all the occupants that the subject has changed
            packet.setSender(role.getRoleAddress());
            send(packet);
        }
        else {
            throw new ForbiddenException();
        }
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void sendInvitation(String to, String reason, MUCRole senderRole, Session session)
            throws ForbiddenException {
        if (!isInvitationRequiredToEnter() || canOccupantsInvite()
                || MUCRole.ADMINISTRATOR == senderRole.getAffiliation()
                || MUCRole.OWNER == senderRole.getAffiliation()) {
            // If the room is not members-only OR if the room is members-only and anyone can send
            // invitations or the sender is an admin or an owner, then send the invitation
            Message message = new MessageImpl();
            message.setOriginatingSession(session);
            message.setSender(role.getRoleAddress());
            message.setRecipient(XMPPAddress.parseJID(to));
            MetaDataFragment frag = new MetaDataFragment("http://jabber.org/protocol/muc#user", "x");
            frag.setProperty("x.invite:from", senderRole.getChatUser().getAddress()
                    .toBareStringPrep());
            if (reason != null && reason.length() > 0) {
                frag.setProperty("x.invite.reason", reason);
            }
            if (isPasswordProtected()) {
                frag.setProperty("x.password", getPassword());
            }
            message.addFragment(frag);

            // Include the jabber:x:conference information for backward compatibility
            frag = new MetaDataFragment("jabber:x:conference", "x");
            frag.setProperty("x:jid", role.getRoleAddress().toBareStringPrep());
            message.addFragment(frag);

            // Send the message with the invitation
            router.route(message);
        }
        else {
            throw new ForbiddenException();
        }
    }

    public void sendInvitationRejection(String to, String reason, XMPPAddress sender,
            Session session) {
        Message message = new MessageImpl();
        message.setOriginatingSession(session);
        message.setSender(role.getRoleAddress());
        message.setRecipient(XMPPAddress.parseJID(to));
        MetaDataFragment frag = new MetaDataFragment("http://jabber.org/protocol/muc#user", "x");
        frag.setProperty("x.decline:from", sender.toBareStringPrep());
        if (reason != null && reason.length() > 0) {
            frag.setProperty("x.decline.reason", reason);
        }
        message.addFragment(frag);

        // Send the message with the invitation
        router.route(message);
    }

    public IQOwnerHandler getIQOwnerHandler() {
        return iqOwnerHandler;
    }

    public IQAdminHandler getIQAdminHandler() {
        return iqAdminHandler;
    }

    public Iterator<String> getOwners() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(owners).iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<String> getAdmins() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableCollection(admins).iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<String> getMembers() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableMap(members).keySet().iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<String> getOutcasts() {
        lock.readLock().lock();
        try {
            return outcasts.iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<MUCRole> getModerators() {
        lock.readLock().lock();
        try {
            ArrayList<MUCRole> moderators = new ArrayList<MUCRole>();
            for (MUCRole role : occupants.values()) {
                if (MUCRole.MODERATOR == role.getRole()) {
                    moderators.add(role);
                }
            }
            return moderators.iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Iterator<MUCRole> getParticipants() {
        lock.readLock().lock();
        try {
            ArrayList<MUCRole> participants = new ArrayList<MUCRole>();
            for (MUCRole role : occupants.values()) {
                if (MUCRole.PARTICIPANT == role.getRole()) {
                    participants.add(role);
                }
            }
            return participants.iterator();
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Presence addModerator(String fullJID, MUCRole senderRole) throws ForbiddenException {
        if (MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                && MUCRole.OWNER != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        // Update the presence with the new role and inform all occupants
        try {
            return changeRoleOfOccupant(fullJID, MUCRole.MODERATOR);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    public Presence addParticipant(String fullJID, String reason, MUCRole senderRole)
            throws NotAllowedException, ForbiddenException {
        if (MUCRole.MODERATOR != senderRole.getRole()) {
            throw new ForbiddenException();
        }
        // Update the presence with the new role and inform all occupants
        Presence updatedPresence = changeRoleOfOccupant(fullJID, MUCRole.PARTICIPANT);
        if (updatedPresence != null) {
            MetaDataFragment frag = (MetaDataFragment) updatedPresence.getFragment(
                    "x",
                    "http://jabber.org/protocol/muc#user");

            // Add the reason why the user was granted voice
            if (reason != null && reason.trim().length() > 0) {
                frag.setProperty("x.item.reason", reason);
            }
        }
        return updatedPresence;
    }

    public Presence addVisitor(String fullJID, MUCRole senderRole) throws NotAllowedException,
            ForbiddenException {
        if (MUCRole.MODERATOR != senderRole.getRole()) {
            throw new ForbiddenException();
        }
        return changeRoleOfOccupant(fullJID, MUCRole.VISITOR);
    }

    public Presence kickOccupant(String fullJID, String actorJID, String reason)
            throws NotAllowedException {
        // Update the presence with the new role and inform all occupants
        Presence updatedPresence = changeRoleOfOccupant(fullJID, MUCRole.NONE_ROLE);
        if (updatedPresence != null) {
            MetaDataFragment frag = (MetaDataFragment) updatedPresence.getFragment(
                    "x",
                    "http://jabber.org/protocol/muc#user");

            // Add the status code 307 that indicates that the user was kicked
            frag.setProperty("x.status:code", "307");
            // Add the reason why the user was kicked
            if (reason != null && reason.trim().length() > 0) {
                frag.setProperty("x.item.reason", reason);
            }

            // Effectively kick the occupant from the room
            kickPresence(updatedPresence, actorJID);
        }
        return updatedPresence;
    }

    /**
     * Kicks the occupant from the room. This means that the occupant will receive an unavailable
     * presence with the actor that initiated the kick (if any). The occupant will also be removed
     * from the occupants lists.
     * 
     * @param kickPresence the presence of the occupant to kick from the room.
     * @param actorJID The JID of the actor that initiated the kick or <tt>null</tt> if the info
     * was not provided.
     */
    private void kickPresence(Presence kickPresence, String actorJID) {
        MUCRole kickedRole;
        // Get the role to kick
        kickedRole = occupants.get(kickPresence.getSender().getResourcePrep());
        if (kickedRole != null) {
            try {
                kickPresence = (Presence) kickPresence.createDeepCopy();
                // Add the actor's JID that kicked this user from the room
                if (actorJID != null && actorJID.trim().length() > 0) {
                    MetaDataFragment frag = (MetaDataFragment) kickPresence.getFragment(
                            "x",
                            "http://jabber.org/protocol/muc#user");
                    frag.setProperty("x.item.actor:jid", actorJID);
                }
                // Send the unavailable presence to the banned user
                kickedRole.send(kickPresence);
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
            // Remove the occupant from the room's occupants lists
            removeOccupantRole(kickedRole);
            try {
                kickedRole.kick();
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
        }
    }

    public boolean canAnyoneDiscoverJID() {
        return canAnyoneDiscoverJID;
    }

    public void setCanAnyoneDiscoverJID(boolean canAnyoneDiscoverJID) {
        this.canAnyoneDiscoverJID = canAnyoneDiscoverJID;
    }

    public boolean canOccupantsChangeSubject() {
        return canOccupantsChangeSubject;
    }

    public void setCanOccupantsChangeSubject(boolean canOccupantsChangeSubject) {
        this.canOccupantsChangeSubject = canOccupantsChangeSubject;
    }

    public boolean canOccupantsInvite() {
        return canOccupantsInvite;
    }

    public void setCanOccupantsInvite(boolean canOccupantsInvite) {
        this.canOccupantsInvite = canOccupantsInvite;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isInvitationRequiredToEnter() {
        return invitationRequiredToEnter;
    }

    public void setInvitationRequiredToEnter(boolean invitationRequiredToEnter) {
        this.invitationRequiredToEnter = invitationRequiredToEnter;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPasswordProtected() {
        return passwordProtected;
    }

    public void setPasswordProtected(boolean passwordProtected) {
        this.passwordProtected = passwordProtected;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean wasSavedToDB() {
        if (!isPersistent()) {
            return false;
        }
        return savedToDB;
    }
    
    public void setSavedToDB(boolean saved) {
        this.savedToDB = saved;
    }
    
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPublicRoom() {
        return publicRoom;
    }

    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    public Iterator getRolesToBroadcastPresence() {
        return rolesToBroadcastPresence.iterator();
    }

    public void setRolesToBroadcastPresence(List rolesToBroadcastPresence) {
        // TODO If the list changes while there are occupants in the room we must send available or
        // unavailable presences of the affected occupants to the rest of the occupants
        this.rolesToBroadcastPresence = rolesToBroadcastPresence;
    }

    /**
     * Returns true if we need to check whether a presence could be sent or not.
     * 
     * @return true if we need to check whether a presence could be sent or not.
     */
    private boolean hasToCheckRoleToBroadcastPresence() {
        // For performance reasons the check is done based on the size of the collection.
        return rolesToBroadcastPresence.size() < 3;
    }

    public boolean canBroadcastPresence(String roleToBroadcast) {
        return "none".equals(roleToBroadcast) || rolesToBroadcastPresence.contains(roleToBroadcast);
    }

    public void setName(String name) {
        if (name == null || name.length() == 0) {
            return;
        }
        if (!this.name.equals(name)) {
            String oldName = this.name;
            this.name = name;
            // Notify the server that the name of this room has changed
            server.roomRenamed(oldName, name);
        }
    }

    public void unlockRoom() {
        roomLocked = false;
        this.lockedTime = 0;
    }

    public List addAdmins(List newAdmins, MUCRole senderRole) throws ForbiddenException,
            ConflictException {
        List answer = new ArrayList(newAdmins.size());
        String newAdmin;
        for (Iterator it = newAdmins.iterator(); it.hasNext();) {
            newAdmin = (String) it.next();
            if (newAdmin.trim().length() > 0 && !admins.contains(newAdmin)) {
                answer.addAll(addAdmin(newAdmin, senderRole));
            }
        }
        return answer;
    }

    public List addOwners(List newOwners, MUCRole senderRole) throws ForbiddenException {
        List answer = new ArrayList(newOwners.size());
        String newOwner;
        for (Iterator it = newOwners.iterator(); it.hasNext();) {
            newOwner = (String) it.next();
            if (newOwner.trim().length() > 0 && !owners.contains(newOwner)) {
                answer.addAll(addOwner(newOwner, senderRole));
            }
        }
        return answer;
    }

    public void saveToDB() {
        // Make the room persistent
        MUCPersistenceManager.saveToDB(this);
        if (!savedToDB) {
            // Set that the room is now in the DB
            savedToDB = true;
            // Save the existing room owners to the DB
            for (Iterator it=owners.iterator(); it.hasNext();) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    (String) it.next(),
                    null,
                    MUCRole.OWNER,
                    MUCRole.NONE);
            }
            // Save the existing room admins to the DB
            for (Iterator it=admins.iterator(); it.hasNext();) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    (String) it.next(),
                    null,
                    MUCRole.ADMINISTRATOR,
                    MUCRole.NONE);
            }
            // Save the existing room members to the DB
            for (Iterator it=members.keySet().iterator(); it.hasNext();) {
                String bareJID = (String)it.next();
                MUCPersistenceManager.saveAffiliationToDB(this, bareJID, (String) members
                        .get(bareJID), MUCRole.MEMBER, MUCRole.NONE);
            }
            // Save the existing room outcasts to the DB
            for (Iterator it=outcasts.iterator(); it.hasNext();) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    (String) it.next(),
                    null,
                    MUCRole.OUTCAST,
                    MUCRole.NONE);
            }
        }
    }
}