/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.muc.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.messenger.muc.*;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;
import org.jivesoftware.messenger.user.UserNotFoundException;

import org.jivesoftware.messenger.muc.MUCRole;

/**
 * A surrogate for the persistent room that hasn't been loaded in memory. This class is an 
 * optimization so that persistent rooms don't need to be in memory in order to provide the 
 * necessary information to answer to a service discovery requests.<p>
 * 
 * The list of MUCPersistentRoomSurrogates is hold by MultiUserChatServerImpl. 
 * MultiUserChatServerImpl is also responsible for updating the list whenever a room is loaded from 
 * the database or a persistent room is removed from memory.<p>
 * 
 * Since this class is a surrogate for the real room, most of the room operations of this class will 
 * throw an UnsupportedOperationException.
 * 
 * @author Gaston Dombiak
 */
class MUCPersistentRoomSurrogate implements MUCRoom {

    /**
     * The name of the room.
     */
    private String name;

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

    public String getName() {
        return name;
    }

    public long getID() {
        return roomID;
    }

    public void setID(long roomID) {
        this.roomID = roomID;
    }

    public MUCRole getRole() throws UnauthorizedException {
        // TODO Implement this
        return null;
    }

    public MUCRole getOccupant(String nickname) throws UserNotFoundException {
        return null;
    }

    public List<MUCRole> getOccupantsByBareJID(String jid) throws UserNotFoundException {
        return null;
    }

    public MUCRole getOccupantByFullJID(String jid) throws UserNotFoundException {
        return null;
    }

    public Iterator<MUCRole> getOccupants() throws UnauthorizedException {
        return Collections.EMPTY_LIST.iterator();
    }

    public int getOccupantsCount() {
        return 0;
    }

    public boolean hasOccupant(String nickname) throws UnauthorizedException {
        return false;
    }

    public String getReservedNickname(String bareJID) {
        // TODO Implement this
        return null;
    }

    public int getAffiliation(String bareJID) {
        throw new UnsupportedOperationException();
    }

    public MUCRole joinRoom(String nickname,
                            String password,
                            HistoryRequest historyRequest,
                            MUCUser user) throws UnauthorizedException, UserAlreadyExistsException,
            RoomLockedException, ForbiddenException, RegistrationRequiredException,
            NotAllowedException, ConflictException {
        throw new UnsupportedOperationException();
    }

    public void leaveRoom(String nickname) throws UnauthorizedException, UserNotFoundException {
        throw new UnsupportedOperationException();
    }

    public void destroyRoom(String alternateJID, String reason) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

    public Presence createPresence(int presenceStatus) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

    public void serverBroadcast(String msg) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

    public long getChatLength() {
        return 0;
    }

    public void addFirstOwner(String bareJID) {
        throw new UnsupportedOperationException();
    }

    public List addOwner(String bareJID, MUCRole sendRole) throws ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public List addAdmin(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException {
        throw new UnsupportedOperationException();
    }

    public List addMember(String bareJID, String nickname, MUCRole sendRole)
            throws ForbiddenException, ConflictException {
        throw new UnsupportedOperationException();
    }

    public List addOutcast(String bareJID, String reason, MUCRole sendRole)
            throws NotAllowedException, ForbiddenException, ConflictException {
        throw new UnsupportedOperationException();
    }

    public List addNone(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException {
        throw new UnsupportedOperationException();
    }

    public boolean isLocked() {
        return false;
    }

    public void nicknameChanged(String oldNick, String newNick) {
        throw new UnsupportedOperationException();
    }

    public void changeSubject(Message packet, MUCRole role) throws UnauthorizedException,
            ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void sendPublicMessage(Message message, MUCRole senderRole)
            throws UnauthorizedException, ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public void sendPrivateMessage(Message message, MUCRole senderRole) throws NotFoundException {
        throw new UnsupportedOperationException();
    }

    public Presence addModerator(String fullJID, MUCRole sendRole) throws ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public Presence addParticipant(String fullJID, String reason, MUCRole sendRole)
            throws NotAllowedException, ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public Presence addVisitor(String fullJID, MUCRole sendRole) throws NotAllowedException,
            ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public Presence kickOccupant(String fullJID, String actorJID, String reason)
            throws NotAllowedException {
        throw new UnsupportedOperationException();
    }

    public IQOwnerHandler getIQOwnerHandler() {
        throw new UnsupportedOperationException();
    }

    public IQAdminHandler getIQAdminHandler() {
        throw new UnsupportedOperationException();
    }

    public Iterator getOwners() {
        return Collections.EMPTY_LIST.iterator();
    }

    public Iterator getAdmins() {
        return Collections.EMPTY_LIST.iterator();
    }

    public Iterator getMembers() {
        return Collections.EMPTY_LIST.iterator();
    }

    public Iterator getOutcasts() {
        return Collections.EMPTY_LIST.iterator();
    }

    public Iterator getModerators() {
        return Collections.EMPTY_LIST.iterator();
    }

    public Iterator getParticipants() {
        return Collections.EMPTY_LIST.iterator();
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
        return null;
    }

    public void setDescription(String description) {
        throw new UnsupportedOperationException();
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

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean wasSavedToDB() {
        return true;
    }

    public void setSavedToDB(boolean saved) {
        throw new UnsupportedOperationException();
    }

    public void saveToDB() {
        throw new UnsupportedOperationException();
    }

    public boolean isPublicRoom() {
        return true;
    }

    public void setPublicRoom(boolean publicRoom) {
        throw new UnsupportedOperationException();
    }

    public Iterator getRolesToBroadcastPresence() {
        return rolesToBroadcastPresence.iterator();
    }

    public void setRolesToBroadcastPresence(List rolesToBroadcastPresence) {
        this.rolesToBroadcastPresence = rolesToBroadcastPresence;
    }

    public boolean canBroadcastPresence(String roleToBroadcast) {
        throw new UnsupportedOperationException();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void unlockRoom() {
        throw new UnsupportedOperationException();
    }

    public List addAdmins(List newAdmins, MUCRole sendRole) throws ForbiddenException,
            ConflictException {
        throw new UnsupportedOperationException();
    }

    public List addOwners(List newOwners, MUCRole sendRole) throws ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public void sendInvitation(String to, String reason, MUCRole role, Session session)
            throws ForbiddenException {
        throw new UnsupportedOperationException();
    }

    public void sendInvitationRejection(String to,
                                        String reason,
                                        XMPPAddress sender,
                                        Session session) {
        // TODO Implement this???
    }

    public void send(Message packet) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

    public void send(Presence packet) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

    public void send(IQ packet) throws UnauthorizedException {
        throw new UnsupportedOperationException();
    }

}
