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

package org.jivesoftware.openfire.muc;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines the permissions and actions that a MUCUser currently may use in a particular room. Each MUCRole defines the
 * relationship between a MUCRoom and a MUCUser.
 *
 * MUCUsers can play different roles in different chat rooms.
 *
 * @author Gaston Dombiak
 */
public interface MUCRole {

    Logger Log = LoggerFactory.getLogger( MUCRole.class );

    /**
     * Obtain the current presence status of a user in a chatroom.
     *
     * @return The presence of the user in the room.
     */
    Presence getPresence();

    /**
     * Set the current presence status of a user in a chatroom.
     *
     * @param presence The presence of the user in the room.
     */
    void setPresence( Presence presence );

    /**
     * Call this method to promote or demote a user's role in a chatroom.
     * It is common for the chatroom or other chat room members to change
     * the role of users (a moderator promoting another user to moderator
     * status for example).
     *
     * Owning ChatUsers should have their membership roles updated.
     *
     * A role is a temporary position or privilege level within a room, distinct from a user's long-lived affiliation
     * with the room. A role lasts only for the duration of an occupant's visit to a room.
     *
     * @param newRole The new role that the user will play.
     * @throws NotAllowedException   Thrown if trying to change the moderator role to an owner or
     *                               administrator.
     */
    void setRole( Role newRole ) throws NotAllowedException;

    /**
     * Obtain the role state of the user.
     *
     * A role is a temporary position or privilege level within a room, distinct from a user's long-lived affiliation
     * with the room. A role lasts only for the duration of an occupant's visit to a room.
     *
     * @return The role status of this user.
     */
    Role getRole();

    /**
     * Call this method to promote or demote a user's affiliation in a chatroom. An affiliation is a long-lived
     * association or connection with a room. Affiliation is distinct from role. An affiliation lasts across a user's
     * visits to a room.
     *
     * @param newAffiliation the new affiliation that the user will play.
     * @throws NotAllowedException thrown if trying to ban an owner or an administrator.
     */
    void setAffiliation( Affiliation newAffiliation ) throws NotAllowedException;

    /**
     * Obtain the affiliation state of the user, which is a long-lived association or connection with a room.
     * Affiliation is distinct from role. An affiliation lasts across a user's visits to a room.
     *
     * @return The affiliation status of this user.
     */
    Affiliation getAffiliation();

    /**
     * Changes the nickname of the occupant within the room to the new nickname.
     *
     * @param nickname the new nickname of the occupant in the room.
     */
    void changeNickname(String nickname);

    /**
     * Obtain the nickname for the user in the chatroom.
     *
     * @return The user's nickname in the room or null if invisible.
     */
    String getNickname();

    /**
     * Destroys this role after the occupant left the room. This role will be
     * removed from MUCUser.
     */
    void destroy();

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
    JID getRoleAddress();

    /**
     * Obtain the XMPPAddress of the user that joined the room. A {@code null} null value
     * represents the room's role.
     *
     * @return The address of the user that joined the room or null if this role belongs to the room itself.
     */
    JID getUserAddress();

    /**
     * Obtain the XMPPAddress representing this role in a room in context of FMUC. This typically represents the
     * XMPPAddress as it is known locally at the joining FMUC node.
     *
     * For users that are joined through FMUC from a remote node, this method will return the value as reported by the
     * joining FMUC node.
     *
     * For users that are in the room, but connected directly to this instance of Openfire, this method returns null,
     * even if the room is part of an FMUC node.
     *
     * Users that joined through server-to-server federation (as opposed to FMUC), will not have a FMUC address. Null is
     * returned by this method for these users.
     *
     * @return The address of the user that joined the room through FMUC from a remote domain, or null.
     */
    JID getReportedFmucAddress();

    /**
     * Returns true if the user is one that is in the room as a result of that user being in another room that is
     * federated with this room, through the FMUC protocol
     *
     * @return true if this user is a user on a remote MUC room that is federated with this chatroom.
     */
    default boolean isRemoteFmuc() {
        return getReportedFmucAddress() != null;
    };

    /**
     * Returns true if this room occupant is hosted by this JVM.
     *
     * @return true if this room occupant is hosted by this JVM
     */
    boolean isLocal();

    /**
     * Returns the id of the node that is hosting the room occupant.
     *
     * @return the id of the node that is hosting the room occupant.
     */
    NodeID getNodeID();

    /**
     * Sends a packet to the user.
     *
     * @param packet The packet to send
     */
    void send( Packet packet );

    /**
     * When sending data to a user that joined the room through FMUC (when the user is a user that is local to a remote
     * chatroom that joined our room as a 'joining FMUC node'), then we'll need to add an 'fmuc' element to all data
     * that we send it.
     *
     * The data that is to be added must include the 'from' address representing the FMUC role for the occupant that
     * sent the stanza. We either use the reported FMUC address as passed down from other FMUC nodes, or we use the
     * address of users connected locally to our server.
     *
     * This method will add an 'fmuc' child element to the stanza when the user is a user that joined through FMUC (is
     * a member of a room that federates with the MUC room local to our server).
     *
     * If the provided stanza already contains an FMUC element with relevant data, this data is left unchanged.
     *
     * @param packet The stanza to augment
     */
    default void augmentOutboundStanzaWithFMUCData( @Nonnull Packet packet )
    {
        if ( !isRemoteFmuc() ) {
            Log.trace( "Recipient '{}' is not in a remote FMUC room. No need to augment stanza with FMUC data.", this.getUserAddress() );
            return;
        }
        Log.trace( "Sending data to recipient '{}' that has joined local room '{}' through a federated remote room (FMUC). Outbound stanza is required to include FMUC data: {}", this.getUserAddress(), this.getChatRoom().getJID(), packet );

        // Data that was sent to us from a(nother) FMUC node might already have this value. If not, we need to ensure that this is added.
        Element fmuc = packet.getElement().element(QName.get("fmuc", "http://isode.com/protocol/fmuc"));
        if ( fmuc == null )
        {
            fmuc = packet.getElement().addElement(QName.get("fmuc", "http://isode.com/protocol/fmuc"));
        }

        if ( fmuc.attributeValue( "from" ) != null && !fmuc.attributeValue( "from" ).trim().isEmpty() )
        {
            Log.trace( "Outbound stanza already includes FMUC data. No need to include further data." );
            return;
        }

        final JID reportingFmucAddress;
        if (packet.getFrom().getResource() == null) {
            Log.trace( "Sender is the room itself: '{}'", packet.getFrom() );
            reportingFmucAddress = this.getChatRoom().getRole().getRoleAddress();
        } else {
            Log.trace( "Sender is an occupant of the room: '{}'", packet.getFrom() );

            // Determine the role of the entity that sent the message.
            final Set<MUCRole> sender = new HashSet<>();
            try
            {
                sender.addAll( this.getChatRoom().getOccupantsByNickname(packet.getFrom().getResource()) );
            }
            catch ( UserNotFoundException e )
            {
                Log.trace( "Unable to identify occupant '{}'", packet.getFrom() );
            }

            // If this users is user joined through FMUC, use the FMUC-reported address, otherwise, use the local address.
            switch ( sender.size() )
            {
                case 0:
                    Log.warn("Cannot add required FMUC data to outbound stanza. Unable to determine the role of the sender of stanza sent over FMUC: {}", packet);
                    return;

                case 1:
                    final MUCRole role = sender.iterator().next();
                    if ( role.isRemoteFmuc() ) {
                        reportingFmucAddress = role.getReportedFmucAddress();
                    } else {
                        reportingFmucAddress = role.getUserAddress();
                    }
                    break;

                default:
                    // The user has more than one role, which probably means it joined the room from more than one device.
                    // At this point in the code flow, we can't determine anymore which full JID caused the stanza to be sent.
                    // As a fallback, send the _bare_ JID of the user (which should be equal for all its resources).
                    // TODO verify if the compromise is acceptable in the XEP.
                    final Set<JID> bareJids = sender.stream()
                        .map(r -> {
                            if ( r.isRemoteFmuc() ) {
                                return r.getReportedFmucAddress().asBareJID();
                            } else {
                                return r.getUserAddress().asBareJID();
                            }
                        })
                        .collect(Collectors.toSet());

                    if ( bareJids.size() == 1 ) {
                        Log.warn("Sender '{}' has more than one role in room '{}', indicating that the user joined the room from more than one device. Using its bare instead of full JID for FMUC reporting.",
                                 packet.getFrom(),
                                 this.getChatRoom().getJID());
                        reportingFmucAddress = bareJids.iterator().next().asBareJID();
                    } else {
                        throw new IllegalStateException("Unable to deduce one FMUC address for occupant address '" + packet.getFrom() + "'.");
                    }
                    break;
            }
        }

        Log.trace( "Adding 'from' FMUC data to outbound stanza, using FMUC address of sender of data, that has been determined to be '{}'.", reportingFmucAddress );
        fmuc.addAttribute("from", reportingFmucAddress.toString() );
    }

    /**
     * A temporary position or privilege level within a room, distinct from a user's long-lived affiliation with the
     * room. A role lasts only for the duration of an occupant's visit to a room.
     */
    enum Role {

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

    /**
     * A long-lived association or connection with a room. Affiliation is distinct from role. An affiliation lasts
     * across a user's visits to a room.
     */
    enum Affiliation {

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
}
