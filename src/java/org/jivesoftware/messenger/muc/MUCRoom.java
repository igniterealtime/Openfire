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

package org.jivesoftware.messenger.muc;

import java.util.Iterator;
import java.util.List;

import org.jivesoftware.messenger.muc.spi.IQAdminHandler;
import org.jivesoftware.messenger.muc.spi.IQOwnerHandler;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.messenger.Message;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.Session;
import org.jivesoftware.messenger.XMPPAddress;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;


/**
 * A chat room on the chat server manages its users, and
 * enforces it's own security rules.
 *
 * @author Gaston Dombiak
 */
public interface MUCRoom extends ChatDeliverer {

    /**
     * Get the name of this room.
     *
     * @return The name for this room
     */
    String getName();

    /**
     * Obtain a unique numerical id for this room. Useful for storing rooms in databases. If the 
     * room is persistent or is logging the conversation then the returned ID won't be -1.
     *
     * @return The unique id for this room or -1 if the room is temporary and is not logging the
     * conversation.
     */
    long getID();

    /**
     * Sets a new room ID if the room has just been saved to the database or sets the saved ID of
     * the room in the database while loading the room. 
     * 
     * @param roomID the saved ID of the room in the DB or a new one if the room is being saved to 
     * the DB.
     */
    void setID(long roomID);

    /**
     * Obtain the role of the chat server (mainly for addressing messages and presence).
     *
     * @return The role for the chat room itself
     * @throws UnauthorizedException If you don't have permission
     */
    MUCRole getRole() throws UnauthorizedException;

    /**
     * Obtain the role of a given user by nickname.
     *
     * @param nickname The nickname of the user you'd like to obtain
     * @return The user's role in the room
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    MUCRole getOccupant(String nickname) throws UserNotFoundException;

    /**
     * Obtain the roles of a given user in the room by his bare JID. A user can have several roles,
     * one for each client resource from which the user has joined the room. 
     *
     * @param jid The bare jid of the user you'd like to obtain
     * @return The user's roles in the room
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    List<MUCRole> getOccupantsByBareJID(String jid) throws UserNotFoundException;

    /**
     * Obtain the role of a given user in the room by his full JID.
     *
     * @param jid The full jid of the user you'd like to obtain
     * @return The user's role in the room
     * @throws UserNotFoundException If there is no user with the given nickname
     */
    MUCRole getOccupantByFullJID(String jid) throws UserNotFoundException;

    /**
     * Obtain the roles of all users in the chatroom.
     *
     * @return Iterator over all users in the chatroom
     * @throws UnauthorizedException If you don't have permission to access the user
     */
    Iterator<MUCRole> getOccupants() throws UnauthorizedException;

    /**
     * Returns the number of occupants in the chatroom at the moment.
     *
     * @return int the number of occupants in the chatroom at the moment.
     */
    int getOccupantsCount();

    /**
     * Determine if a given nickname is taken.
     *
     * @param nickname The nickname of the user you'd like to obtain
     * @return True if a nickname is taken
     * @throws UnauthorizedException If you don't have permission to access the user
     */
    boolean hasOccupant(String nickname) throws UnauthorizedException;

    /**
     * Returns the reserved room nickname for the bare JID or null if none.
     * 
     * @param bareJID The bare jid of the user of which you'd like to obtain his reserved nickname.
     * @return the reserved room nickname for the bare JID or null if none.
     */
    String getReservedNickname(String bareJID);

    /**
     * Returns the affiliation state of the user in the room. Possible affiliations are 
     * MUCRole.OWNER, MUCRole.ADMINISTRATOR, MUCRole.MEMBER, MUCRole.OUTCAST and MUCRole.NONE.<p>
     * 
     * Note: Prerequisite - A lock must already be obtained before sending this message.
     *  
     * @param bareJID The bare jid of the user of which you'd like to obtain his affiliation.
     * @return the affiliation state of the user in the room.
     */
    int getAffiliation(String bareJID);

    /**
     * Joins the room using the given nickname.
     * 
     * @param nickname The nickname the user wants to use in the chatroom.
     * @param password The password provided by the user to enter the chatroom or null if none.
     * @param historyRequest The amount of history that the user request or null meaning default.
     * @param user The user joining.
     * @return The role created for the user.
     * @throws UnauthorizedException If the user doesn't have permision to join the room.
     * @throws UserAlreadyExistsException If the nickname is already taken.
     * @throws RoomLockedException If the user is trying to join a locked room.
     * @throws ForbiddenException If the user is an outcast.
     * @throws RegistrationRequiredException If the user is not a member of a members-only room.
     * @throws NotAllowedException If the user is has exceded the max number of occupants.
     * @throws ConflictException If another user attempts to join the room with a nickname reserved
     *             by the first user.
     */
    MUCRole joinRoom(String nickname, String password, HistoryRequest historyRequest, MUCUser user)
            throws UnauthorizedException, UserAlreadyExistsException, RoomLockedException,
            ForbiddenException, RegistrationRequiredException, NotAllowedException,
            ConflictException;

    /**
     * Remove a member from the chat room.
     *
     * @param nickname The user to remove
     * @throws UnauthorizedException If the user doesn't have permission to leave the room.
     * @throws UserNotFoundException If the nickname is not found.
     */
    void leaveRoom(String nickname)
            throws UnauthorizedException, UserNotFoundException;

    /**
     * Destroys the room. Each occupant will be removed and will receive a presence stanza of type
     * "unavailable" whose "from" attribute will be the occupant's nickname that the user knows he 
     * or she has been removed from the room.
     * 
     * @param alternateJID the alternate JID. Commonly used to provide a replacement room.
     * @param reason the reason why the room was destroyed.
     * @throws UnauthorizedException If the user doesn't have permission to destroy the room.
     */
    void destroyRoom(String alternateJID, String reason) throws UnauthorizedException;

    /**
     * Create a new presence in this room for the given role.
     *
     * @return The new presence
     * @throws UnauthorizedException If the user doesn't have permission to leave the room
     */
    Presence createPresence(int presenceStatus) throws UnauthorizedException;

    /**
     * Broadcast a given message to all members of this chat room. The sender is always set to 
     * be the chatroom.
     *
     * @param msg The message to broadcast
     */
    void serverBroadcast(String msg) throws UnauthorizedException;
    
    /**
     * Returns the total length of the chat session.
     * 
     * @return length of chat session in milliseconds.
     */
    public long getChatLength();

    /**
     * Adds a new user to the list of owners. The user is the actual creator of the room. Only the
     * MultiUserChatServer should use this method. Regular owners list maintenance MUST be done
     * through {@link #addOwner(String,MUCRole)}.
     * 
     * @param bareJID The bare JID of the user to add as owner.
     */
    public void addFirstOwner(String bareJID);

    /**
     * Adds a new user to the list of owners.
     * 
     * @param bareJID The bare JID of the user to add as owner.
     * @param sendRole the role of the user that is trying to modify the owners list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the owner list.
     */
    public List addOwner(String bareJID, MUCRole sendRole) throws ForbiddenException;

    /**
     * Adds a new user to the list of admins.
     * 
     * @param bareJID The bare JID of the user to add as admin.
     * @param sendRole the role of the user that is trying to modify the admins list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the admin list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List addAdmin(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException;

    /**
     * Adds a new user to the list of members.
     * 
     * @param bareJID The bare JID of the user to add as a member.
     * @param nickname The reserved nickname of the member for the room or null if none.
     * @param sendRole the role of the user that is trying to modify the members list.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws ForbiddenException If the user is not allowed to modify the members list.
     * @throws ConflictException If the desired room nickname is already reserved for the room or if
     *             the room was going to lose all its owners.
     */
    public List addMember(String bareJID, String nickname, MUCRole sendRole)
            throws ForbiddenException, ConflictException;

    /**
     * Adds a new user to the list of outcast users.
     * 
     * @param bareJID The bare JID of the user to add as an outcast.
     * @param reason The reason why the user was banned.
     * @param sendRole the role of the user that initiated the ban.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws NotAllowedException Thrown if trying to ban an owner or an administrator.
     * @throws ForbiddenException If the user is not allowed to modify the outcast list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List addOutcast(String bareJID, String reason, MUCRole sendRole)
            throws NotAllowedException, ForbiddenException, ConflictException;

    /**
     * Removes the user from all the other affiliation list thus giving the user a NONE affiliation.
     * 
     * @param bareJID The bare JID of the user to keep with a NONE affiliation.
     * @param sendRole the role of the user that set the affiliation to none.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room or null if none was updated.
     * @throws ForbiddenException If the user is not allowed to modify the none list.
     * @throws ConflictException If the room was going to lose all its owners.
     */
    public List addNone(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException;

    /**
     * Returns true if the room is locked. The lock will persist for a defined period of time. If 
     * the room owner does not configure the room within the timeout period, the room owner is 
     * assumed to have accepted the default configuration.
     * 
     * @return true if the room is locked. 
     */
    public boolean isLocked();

    /**
     * An event callback fired whenever an occupant changes his nickname within the chatroom.
     *  
     * @param oldNick old nickname within the room.
     * @param newNick new nickname within the room.
     */
    public void nicknameChanged(String oldNick, String newNick);
    
    /**
     * Changes the room's subject if the occupant has enough permissions. The occupant must be
     * a moderator or the room must be configured so that anyone can change its subject. Otherwise
     * a forbidden exception will be thrown.<p>
     * 
     * The new subject will be added to the history of the room.
     *  
     * @param packet the sent packet to change the room's subject.
     * @param role the role of the user that is trying to change the subject.
     * @throws UnauthorizedException If the user doesn't have permision to change the subject.
     * @throws ForbiddenException If the user is not allowed to change the subject.
     */
    public void changeSubject(Message packet, MUCRole role) throws UnauthorizedException,
            ForbiddenException;

    /**
     * Returns the last subject that some occupant set to the room.
     * 
     * @return the last subject that some occupant set to the room.
     */
    public String getSubject();

    /**
     * Sets the last subject that some occupant set to the room. This message will only be used
     * when loading a room from the database. 
     * 
     * @param subject the last known subject of the room.
     */
    public void setSubject(String subject);

    /**
     * Sends a message to the all the occupants. In a moderated room, this privilege is restricted
     * to occupants with a role of participant or higher. In an unmoderated room, any occupant can
     * send a message to all other occupants.
     * 
     * @param message The message to send.
     * @param senderRole the role of the user that is trying to send a public message.
     * @throws UnauthorizedException Thrown if unauthorized
     * @throws ForbiddenException If the user is not allowed to send a public message (i.e. does not
     *             have voice in the room).
     */
    public void sendPublicMessage(Message message, MUCRole senderRole) throws UnauthorizedException,
            ForbiddenException;

    /**
     * Sends a private message to a selected occupant. 
     * 
     * @param message The message to send.
     * @param senderRole the role of the user that is trying to send a public message.
     * @throws NotFoundException If the user is sending a message to a room JID that does not exist.
     */
    public void sendPrivateMessage(Message message, MUCRole senderRole) throws NotFoundException;

    public Presence addModerator(String fullJID, MUCRole sendRole) throws ForbiddenException;

    public Presence addParticipant(String fullJID, String reason, MUCRole sendRole)
            throws NotAllowedException, ForbiddenException;

    public Presence addVisitor(String fullJID, MUCRole sendRole) throws NotAllowedException,
            ForbiddenException;

    /**
     * Kicks a user from the room. If the user was in the room, the returned updated presence will
     * be sent to the remaining occupants. 
     * 
     * @param fullJID The full JID of the kicked user.
     * @param actorJID The JID of the actor that initiated the kick.
     * @param reason The reason why the user was kicked.
     * @return the updated presence of the kicked user or null if the user was not in the room.
     * @throws NotAllowedException Thrown if trying to ban an owner or an administrator.
     */
    public Presence kickOccupant(String fullJID, String actorJID, String reason)
            throws NotAllowedException;

    public IQOwnerHandler getIQOwnerHandler();

    public IQAdminHandler getIQAdminHandler();

    public Iterator<String> getOwners();

    public Iterator<String> getAdmins();

    public Iterator<String> getMembers();

    public Iterator<String> getOutcasts();

    public Iterator<MUCRole> getModerators();

    public Iterator<MUCRole> getParticipants();

    public boolean canAnyoneDiscoverJID();

    public void setCanAnyoneDiscoverJID(boolean canAnyoneDiscoverJID);

    public boolean canOccupantsChangeSubject();

    public void setCanOccupantsChangeSubject(boolean canOccupantsChangeSubject);

    public boolean canOccupantsInvite();

    public void setCanOccupantsInvite(boolean canOccupantsInvite);

    public String getNaturalLanguageName();

    public void setNaturalLanguageName(String naturalLanguageName);

    public String getDescription();

    public void setDescription(String description);

    public boolean isInvitationRequiredToEnter();

    public void setInvitationRequiredToEnter(boolean invitationRequiredToEnter);

    public boolean isLogEnabled();

    public void setLogEnabled(boolean logEnabled);

    public int getMaxUsers();

    public void setMaxUsers(int maxUsers);

    public boolean isModerated();

    public void setModerated(boolean moderated);

    public String getPassword();

    public void setPassword(String password);

    public boolean isPasswordProtected();

    public boolean isPersistent();

    public void setPersistent(boolean persistent);

    /**
     * Returns true if the room has already been made persistent. If the room is temporary the 
     * answer will always be false.
     * 
     * @return true if the room has already been made persistent.
     */
    public boolean wasSavedToDB();

    /**
     * Sets if the room has already been made persistent.
     * 
     * @param saved boolean that indicates if the room was saved to the database.
     */
    public void setSavedToDB(boolean saved);

    /**
     * Saves the room configuration to the DB. After the room has been saved to the DB it will
     * become persistent. 
     */
    public void saveToDB();

    /**
     * Returns true if the room is searchable and visible through service discovery. 
     * 
     * @return true if the room is searchable and visible through service discovery.
     */
    public boolean isPublicRoom();

    /**
     * Sets if the room is searchable and visible through service discovery.
     * 
     * @param publicRoom if the room is searchable and visible through service discovery.
     */
    public void setPublicRoom(boolean publicRoom);

    /**
     * Returns the list of roles of which presence will be broadcasted to the rest of the occupants.
     * This feature is useful for implementing "invisible" occupants.
     * 
     * @return the list of roles of which presence will be broadcasted to the rest of the occupants.
     */
    public Iterator getRolesToBroadcastPresence();

    /**
     * Sets the list of roles of which presence will be broadcasted to the rest of the occupants.
     * This feature is useful for implementing "invisible" occupants.
     * 
     * @param rolesToBroadcastPresence the list of roles of which presence will be broadcasted to 
     * the rest of the occupants.
     */
    public void setRolesToBroadcastPresence(List rolesToBroadcastPresence);

    /**
     * Returns true if the presences of the requested role will be broadcasted.
     * 
     * @param roleToBroadcast the role to check if its presences will be broadcasted.
     * @return true if the presences of the requested role will be broadcasted.
     */
    public boolean canBroadcastPresence(String roleToBroadcast);

    /**
     * Unlocks the room so that users can join the room. The room is locked when created and only
     * the owner of the room can unlock it by sending the configuration form to the Multi-User Chat
     * service.
     *
     * @param sendRole the role of the occupant that unlocked the room.
     */
    public void unlockRoom(MUCRole sendRole);

    public List addAdmins(List newAdmins, MUCRole sendRole) throws ForbiddenException,
            ConflictException;

    public List addOwners(List newOwners, MUCRole sendRole) throws ForbiddenException;

    /**
     * Sends an invitation to a user. The invitation will be sent as if the room is inviting the 
     * user. The invitation will include the original occupant the sent the invitation together with
     * the reason for the invitation if any. Since the invitee could be offline at the moment we 
     * need the originating session so that the offline strategy could potentially bounce the 
     * message with the invitation.
     * 
     * @param to the bare JID of the user that is being invited. 
     * @param reason the reason of the invitation or null if none.
     * @param role the role of the occupant that sent the invitation.
     * @param session the originating session that the occupant used for sending the invitation.
     * @throws ForbiddenException If the user is not allowed to send the invitation.
     */
    public void sendInvitation(String to, String reason, MUCRole role, Session session)
            throws ForbiddenException;

    /**
     * Sends the rejection to the inviter. The rejection will be sent as if the room is rejecting 
     * the invitation is named of the invitee. The rejection will include the address of the invitee
     * together with the reason for the rejection if any. Since the inviter could be offline at the 
     * moment we need the originating session so that the offline strategy could potentially bounce 
     * the message with the rejection.
     * 
     * @param to the bare JID of the user that is originated the invitation.
     * @param reason the reason for the rejection or null if none.
     * @param sender the address of the invitee that is rejecting the invitation.
     * @param session the originating session that the invitee used for rejecting the invitation.
     */
    public void sendInvitationRejection(String to, String reason, XMPPAddress sender,
            Session session);
}