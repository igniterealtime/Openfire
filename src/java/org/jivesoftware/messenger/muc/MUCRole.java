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

import org.jivesoftware.messenger.MetaDataFragment;
import org.jivesoftware.messenger.Presence;
import org.jivesoftware.messenger.XMPPAddress;

/**
 * Defines the permissions and actions that a MUCUser may use in
 * a particular room. Each MUCRole defines the relationship between
 * a MUCRoom and a MUCUser.
 * <p/>
 * MUCUsers can play different roles in different chatrooms.
 *
 * @author Gaston Dombiak
 */
public interface MUCRole extends ChatDeliverer {

    /**
     * Runs moderated discussions. Is allowed to kick users, grant and revoke voice, etc.
     */
    int MODERATOR = 0;
    /**
     * A normal occupant of the room. An occupant who does not have administrative privileges; in
     * a moderated room, a participant is further defined as having voice
     */
    int PARTICIPANT = 1;
    /**
     * An occupant who does not have voice  (can't speak in the room)
     */
    int VISITOR = 2;
    /**
     * An occupant who does not permission to stay in the room (was banned)
     */
    int NONE_ROLE = 3;


    /**
     * Owner of the room
     */
    int OWNER = 10;
    /**
     * Administrator of the room
     */
    int ADMINISTRATOR = 20;
    /**
     * A user who is on the "whitelist" for a members-only room or who is registered with an
     * open room.
     */
    int MEMBER = 30;
    /**
     * A user who has been banned from a room.
     */
    int OUTCAST = 40;
    /**
     * A user who doesn't have an affiliation. This kind of users can register with members-only
     * rooms and may enter an open room.
     */
    int NONE = 50;

    /**
     * Obtain the current presence status of a user in a chatroom.
     *
     * @return The presence of the user in the room.
     */
    Presence getPresence();

    /**
     * Returns the extended presence information that includes information about roles,
     * affiliations, JIDs, etc.
     *
     * @return the extended presence information that includes information about roles,
     *         affiliations.
     */
    MetaDataFragment getExtendedPresenceInformation();

    /**
     * Set the current presence status of a user in a chatroom.
     *
     * @param presence The presence of the user in the room.
     */
    void setPresence(Presence presence);

    /**
     * Call this method to promote or demote a user's role in a chatroom.
     * It is common for the chatroom or other chat room members to change
     * the role of users (a moderator promoting another user to moderator
     * status for example).<p>
     * <p/>
     * Owning ChatUsers should have their membership roles updated.
     *
     * @param newRole The new role that the user will play.
     * @throws NotAllowedException   Thrown if trying to change the moderator role to an owner or
     *                               administrator.
     */
    void setRole(int newRole) throws NotAllowedException;

    /**
     * Obtain the role state of the user.
     *
     * @return The role status of this user.
     */
    int getRole();

    /**
     * Obtain the role state of the user as a String. This string representation will be
     * incorporated into the extended packet information.
     *
     * @return The role status of this user.
     */
    public String getRoleAsString();

    /**
     * Call this method to promote or demote a user's affiliation in a chatroom.
     *
     * @param newAffiliation The new affiliation that the user will play.
     * @throws NotAllowedException   Thrown if trying to ban an owner or an administrator.
     */
    public void setAffiliation(int newAffiliation) throws NotAllowedException;

    /**
     * Obtain the affiliation state of the user.
     *
     * @return The affiliation status of this user.
     */
    public int getAffiliation();

    /**
     * Obtain the affiliation state of the user as a String. This string representation will be
     * incorporated into the extended packet information.
     *
     * @return The affiliation status of this user.
     */
    public String getAffiliationAsString();

    /**
     * Obtain the nickname for the user in the chatroom.
     *
     * @return The user's nickname in the room or null if invisible.
     */
    String getNickname();

    /**
     * An event callback for kicks (being removed from a room). This provides the user an
     * opportunity to react to the kick (although the chat user has already been kicked when this
     * method is called). Remove users from a chatroom by calling ChatRoom.leaveRoom().
     */
    void kick();

    /**
     * Changes the nickname of the occupant within the room to the new nickname.
     *
     * @param nickname the new nickname of the occupant in the room.
     */
    void changeNickname(String nickname);

    /**
     * Obtain the chat user that plays this role.
     *
     * @return The chatuser playing this role.
     */
    MUCUser getChatUser();

    /**
     * Obtain the chat room that hosts this user's role.
     *
     * @return The chatroom hosting this role.
     */
    MUCRoom getChatRoom();

    /**
     * Obtain the XMPPAddress representing this role in a room: room@server/nickname
     *
     * @return The Jabber ID that represents this role in the room.
     */
    XMPPAddress getRoleAddress();
}
