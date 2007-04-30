/**
 * $RCSfile$
 * $Revision: 919 $
 * $Date: 2005-01-29 19:26:02 -0300 (Sat, 29 Jan 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc;

import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

/**
 * Defines the permissions and actions that a MUCUser may use in
 * a particular room. Each MUCRole defines the relationship between
 * a MUCRoom and a MUCUser.
 * <p/>
 * MUCUsers can play different roles in different chatrooms.
 *
 * @author Gaston Dombiak
 */
public interface MUCRole {

    public enum Role {

        /**
         * Runs moderated discussions. Is allowed to kick users, grant and revoke voice, etc.
         */
        moderator(0),

        /**
         * A normal occupant of the room. An occupant who does not have administrative privileges; in
         * a moderated room, a participant is further defined as having voice
         */
        participant(1),

        /**
         * An occupant who does not have voice  (can't speak in the room)
         */
        visitor(2),

        /**
         * An occupant who does not permission to stay in the room (was banned)
         */
        none(3);

        private int value;

        Role(int value) {
            this.value = value;
        }

        /**
         * Returns the value for the role.
         *
         * @return the value.
         */
        public int getValue() {
            return value;
        }

        /**
         * Returns the affiliation associated with the specified value.
         *
         * @param value the value.
         * @return the associated affiliation.
         */
        public static Role valueOf(int value) {
            switch (value) {
                case 0: return moderator;
                case 1: return participant;
                case 2: return visitor;
                default: return none;
            }
        }
    }

    public enum Affiliation {

        /**
         * Owner of the room.
         */
        owner(10),

        /**
         * Administrator of the room.
         */
        admin(20),

        /**
         * A user who is on the "whitelist" for a members-only room or who is registered
         * with an open room.
         */
        member(30),

        /**
         * A user who has been banned from a room.
         */
        outcast(40),

        /**
         * A user who doesn't have an affiliation. This kind of users can register with members-only
         * rooms and may enter an open room.
         */
        none(50);

        private int value;

        Affiliation(int value) {
            this.value = value;
        }

        /**
         * Returns the value for the role.
         *
         * @return the value.
         */
        public int getValue() {
            return value;
        }

        /**
         * Returns the affiliation associated with the specified value.
         *
         * @param value the value.
         * @return the associated affiliation.
         */
        public static Affiliation valueOf(int value) {
            switch (value) {
                case 10: return owner;
                case 20: return admin;
                case 30: return member;
                case 40: return outcast;
                default: return none;
            }
        }
    }

    /**
     * Obtain the current presence status of a user in a chatroom.
     *
     * @return The presence of the user in the room.
     */
    public Presence getPresence();

    /**
     * Returns the extended presence information that includes information about roles,
     * affiliations, JIDs, etc.
     *
     * @return the extended presence information that includes information about roles,
     *         affiliations.
     */
    public Element getExtendedPresenceInformation();

    /**
     * Set the current presence status of a user in a chatroom.
     *
     * @param presence The presence of the user in the room.
     */
    public void setPresence(Presence presence);

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
    public void setRole(Role newRole) throws NotAllowedException;

    /**
     * Obtain the role state of the user.
     *
     * @return The role status of this user.
     */
    public Role getRole();

    /**
     * Call this method to promote or demote a user's affiliation in a chatroom.
     *
     * @param newAffiliation the new affiliation that the user will play.
     * @throws NotAllowedException thrown if trying to ban an owner or an administrator.
     */
    public void setAffiliation(Affiliation newAffiliation) throws NotAllowedException;

    /**
     * Obtain the affiliation state of the user.
     *
     * @return The affiliation status of this user.
     */
    public Affiliation getAffiliation();

    /**
     * Obtain the nickname for the user in the chatroom.
     *
     * @return The user's nickname in the room or null if invisible.
     */
    public String getNickname();

    /**
     * Changes the nickname of the occupant within the room to the new nickname.
     *
     * @param nickname the new nickname of the occupant in the room.
     */
    public void changeNickname(String nickname);

    /**
     * Returns true if the room occupant does not want to get messages broadcasted to all
     * room occupants. This type of users are called "deaf" occupants. Deaf occupants will still
     * be able to get private messages, presences, IQ packets or room history.<p>
     *
     * To be a deaf occupant the initial presence sent to the room while joining the room has
     * to include the following child element:
     * <pre>
     * &lt;x xmlns='http://jivesoftware.org/protocol/muc'&gt;
     *     &lt;deaf-occupant/&gt;
     * &lt;/x&gt;
     * </pre>
     *
     * Note that this is a custom extension to the MUC specification.
     *
     * @return true if the room occupant does not want to get messages broadcasted to all
     *         room occupants.
     */
    boolean isVoiceOnly();

    /**
     * Obtain the chat user that plays this role.
     *
     * @return The chatuser playing this role.
     */
    public MUCUser getChatUser();

    /**
     * Obtain the chat room that hosts this user's role.
     *
     * @return The chatroom hosting this role.
     */
    public MUCRoom getChatRoom();

    /**
     * Obtain the XMPPAddress representing this role in a room: room@server/nickname
     *
     * @return The Jabber ID that represents this role in the room.
     */
    public JID getRoleAddress();

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     */
    public void send(Packet packet);
}