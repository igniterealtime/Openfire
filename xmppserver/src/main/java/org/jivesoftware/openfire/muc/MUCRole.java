/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ElementUtil;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
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
public class MUCRole implements Cacheable, Externalizable {

    private static final Logger Log = LoggerFactory.getLogger(MUCRole.class);

    /**
     * The (bare) JID of the room this role is valid in.
     */
    private JID roomJid;

    /**
     * The (bare) JID of the user of the role.
     */
    private JID userJid;

    /**
     * The user's nickname in the room.
     */
    private String nick;

    /**
     * The user's presence in the room.
     */
    @GuardedBy("this")
    private Presence presence;

    /**
     * The role.
     */
    private MUCRole.Role role;

    /**
     * The affiliation.
     */
    private MUCRole.Affiliation affiliation;

    /**
     * Flag that indicates if the room occupant is in the room only to send messages or also
     * to receive room messages. True means that the room occupant is deaf.
     */
    private boolean voiceOnly = false;

    /**
     * The address of the person masquerading in this role.
     */
    private JID rJID;

    /**
     * A fragment containing the x-extension for non-anonymous rooms.
     */
    @GuardedBy("this")
    private Element extendedInformation;

    /**
     * The address of the person on the joining FMUC node, if the person joined through FMUC (otherwise null).
     */
    private JID reportedFmucJID;

    /**
     * A cached value for the cache size of this instance.
     */
    private transient int cacheSize;

    /**
     * This constructor is provided to comply with the Externalizable interface contract. It should not be used directly.
     */
    public MUCRole()
    {}

    /**
     * Create a new role.
     *
     * Note that whenever a new instance of MUCRole is created to reflect a new relation between an end-user and a MUC
     * room, it is typically needed to register the same relation by adding the name of the chatroom to the collection
     * of room names in MUCUser#roomNames. It is the responsibility of the caller to ensure that this is done.
     *
     * @param chatroom the room the role is valid in.
     * @param nickname the nickname of the user in the role.
     * @param role the role of the user in the room.
     * @param affiliation the affiliation of the user in the room.
     * @param userJid the 'real' JID of the user in the role.
     * @param presence the presence sent by the user to join the room.
     */
    public MUCRole(MUCRoom chatroom, String nickname,
                   MUCRole.Role role, MUCRole.Affiliation affiliation, JID userJid, Presence presence)
    {
        this.roomJid = chatroom.getJID();
        this.nick = nickname;
        this.userJid = userJid;
        this.role = role;
        this.affiliation = affiliation;
        rJID = new JID(roomJid.getNode(), roomJid.getDomain(), nick);

        synchronized (this) {
            extendedInformation = DocumentHelper.createElement(QName.get("x", "http://jabber.org/protocol/muc#user"));
            calculateExtendedInformation();

            setPresence(presence);

            // Check if new occupant wants to be a deaf occupant
            Element element = presence.getElement()
                .element(QName.get("x", "http://jivesoftware.org/protocol/muc"));
            if (element != null) {
                voiceOnly = element.element("deaf-occupant") != null;
            }
        }
    }

    /**
     * Create a new role that is a 'Room role'. This should never be used to represent anything else than 'the room itself'.
     *
     * @param room the room the role is valid in.
     */
    private MUCRole(MUCRoom room)
    {
        this.roomJid = room.getJID();
        this.role = Role.moderator;
        this.affiliation = Affiliation.owner;

        rJID = new JID(roomJid.getNode(), roomJid.getDomain(), null);
    }

    /**
     * An empty role that represents the room itself in the chatroom. Chatrooms need to be able to
     * speak (server messages) and so must have their own role in the chatroom.
     *
     * @param room The room for which to return a role.
     * @return The role representing the room.
     */
    public static MUCRole createRoomRole(@Nonnull final MUCRoom room) {
        return new MUCRole(room);
    }

    /**
     * Obtains a copy of the current presence status of a user in a chatroom.
     *
     * The 'from' address of the presence stanza is guaranteed to reflect the room role of this role.
     *
     * @return The presence of the user in the room.
     */
    public synchronized Presence getPresence() {
        return presence.createCopy();
    }

    /**
     * Set the current presence status of a user in a chatroom.
     *
     * @param newPresence The presence of the user in the room.
     */
    public synchronized void setPresence(Presence newPresence) {
        // Try to remove the element whose namespace is "http://jabber.org/protocol/muc" since we
        // don't need to include that element in future presence broadcasts
        Element element = newPresence.getElement().element(QName.get("x", "http://jabber.org/protocol/muc"));
        if (element != null) {
            newPresence.getElement().remove(element);
        }

        synchronized (this) {
            this.presence = newPresence;
            this.presence.setFrom(getRoleAddress());
            updatePresence();
        }
    }

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
    public void setRole(MUCRole.Role newRole) throws NotAllowedException {
        // Don't allow to change the role to an owner or admin unless the new role is moderator
        if (MUCRole.Affiliation.owner == affiliation || MUCRole.Affiliation.admin == affiliation) {
            if (MUCRole.Role.moderator != newRole) {
                throw new NotAllowedException();
            }
        }
        // A moderator cannot be kicked from a room unless there has also been an affiliation change
        if (MUCRole.Role.moderator == role && MUCRole.Role.none == newRole && MUCRole.Affiliation.none != affiliation) {
            throw new NotAllowedException();
        }
        // TODO OF-2288: A moderator MUST NOT be able to revoke voice from a user whose affiliation is at or above the moderator's level.

        role = newRole;
        synchronized (this) {
            if (MUCRole.Role.none == role) {
                presence.setType(Presence.Type.unavailable);
                presence.setStatus(null);
            }
            calculateExtendedInformation();
        }
    }

    /**
     * Obtain the role state of the user.
     *
     * A role is a temporary position or privilege level within a room, distinct from a user's long-lived affiliation
     * with the room. A role lasts only for the duration of an occupant's visit to a room.
     *
     * @return The role status of this user.
     */
    public MUCRole.Role getRole() {
        return role;
    }

    /**
     * Call this method to promote or demote a user's affiliation in a chatroom. An affiliation is a long-lived
     * association or connection with a room. Affiliation is distinct from role. An affiliation lasts across a user's
     * visits to a room.
     *
     * @param newAffiliation the new affiliation that the user will play.
     * @throws NotAllowedException thrown if trying to ban an owner or an administrator.
     */
    public void setAffiliation(MUCRole.Affiliation newAffiliation) throws NotAllowedException {
        // Don't allow to ban an owner or an admin
        if (MUCRole.Affiliation.owner == affiliation || MUCRole.Affiliation.admin== affiliation) {
            if (MUCRole.Affiliation.outcast == newAffiliation) {
                throw new NotAllowedException();
            }
        }
        affiliation = newAffiliation;
        // TODO The fragment is being calculated twice (1. setting the role & 2. setting the aff)
        synchronized (this) {
            calculateExtendedInformation();
        }
    }

    /**
     * Obtain the affiliation state of the user, which is a long-lived association or connection with a room.
     * Affiliation is distinct from role. An affiliation lasts across a user's visits to a room.
     *
     * @return The affiliation status of this user.
     */
    public MUCRole.Affiliation getAffiliation() {
        return affiliation;
    }

    /**
     * Obtain the nickname for the user in the chatroom.
     *
     * @return The user's nickname in the room or null if invisible.
     */
    public String getNickname() {
        return nick;
    }

    /**
     * Changes the nickname of the occupant within the room to the new nickname.
     *
     * @param nickname the new nickname of the occupant in the room.
     */
    public void changeNickname(String nickname) {
        this.nick = nickname;
        setRoleAddress(new JID(roomJid.getNode(), roomJid.getDomain(), nick));
        synchronized (this) {
            cacheSize = -1;
        }
    }

    /**
     * Obtain the chat room that hosts this user's role.
     *
     * @return The chatroom hosting this role.
     */
    protected MUCRoom getChatRoom() {
        final MultiUserChatService multiUserChatService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJid);
        if (multiUserChatService == null) {
            throw new NullPointerException("The MUC service for room '" + roomJid + "' does not exist! This is likely a bug in Openfire.");
        }
        return multiUserChatService.getChatRoom(roomJid.getNode());
    }

    /**
     * Obtain the XMPPAddress representing this role in a room: room@server/nickname
     *
     * @return The Jabber ID that represents this role in the room.
     */
    public JID getRoleAddress() {
        return rJID;
    }

    /**
     * Obtain the XMPPAddress of the user that joined the room. A {@code null} null value
     * represents the room's role.
     *
     * @return The address of the user that joined the room or null if this role belongs to the room itself.
     */
    public JID getUserAddress() {
        return userJid;
    }

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
    public JID getReportedFmucAddress() {
        return reportedFmucJID;
    }

    public void setReportedFmucAddress( @Nonnull final JID reportedFmucAddress ) {
        this.reportedFmucJID = reportedFmucAddress;
        cacheSize = -1;
    }

    /**
     * Returns true if the user is one that is in the room as a result of that user being in another room that is
     * federated with this room, through the FMUC protocol
     *
     * @return true if this user is a user on a remote MUC room that is federated with this chatroom.
     */
    public boolean isRemoteFmuc() {
        return getReportedFmucAddress() != null;
    }

    private void setRoleAddress(JID jid) {
        rJID = jid;
        // Set the new sender of the user presence in the room
        synchronized (this) {
            presence.setFrom(jid);
            cacheSize = -1;
        }
    }

    /**
     * Returns true if the room occupant does not want to get messages broadcast to all
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
    public boolean isVoiceOnly() {
        return voiceOnly;
    }

    /**
     * Sends a packet to the user.
     *
     * Note that sending a packet can modify it (notably, the 'to' address can be changed). If this is undesired (for
     * example, because post-processing should not expose the modified 'to' address), then a copy of the original
     * stanza should be provided as an argument to this method.
     *
     * @param packet The packet to send
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2163">issue OF-2163</a>
     */
    public void send(Packet packet) {
        if (packet == null) {
            return;
        }

        Log.debug("Send packet {} to nickname {} and userJid {}", packet.toXML(), getNickname(), userJid);

        if (getNickname() == null) { // If this is a 'room role'.
            Log.debug("Nickname is null, assuming room role");
            getChatRoom().send(packet, this);
            return;
        }

        if (this.isRemoteFmuc()) {
            // Sending stanzas to individual occupants that are on remote FMUC nodes defeats the purpose of FMUC, which is to reduce message. This reduction is based on sending data just once, and have it 'fan out' on the remote node (as opposed to sending each occupant on that node a distinct stanza from this node).
            Log.warn( "Sending data directly to an entity ({}) on a remote FMUC node. Instead of individual messages, we expect data to be sent just once (and be fanned out locally by the remote node).", this, new Throwable() );

            // Check if stanza needs to be enriched with FMUC metadata.
            augmentOutboundStanzaWithFMUCData(packet);
        }

        packet.setTo(userJid);


        XMPPServer.getInstance().getPacketRouter().route(packet);
    }

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
    public void augmentOutboundStanzaWithFMUCData( @Nonnull Packet packet )
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

            // If this user is joined through FMUC, use the FMUC-reported address, otherwise, use the local address.
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
     * Calculates and sets the extended presence information to add to the presence.
     * The information to add contains the user's jid, affiliation and role.
     */
    @GuardedBy("this")
    private void calculateExtendedInformation() {
        ElementUtil.setProperty(extendedInformation, "x.item:jid", userJid.toString());
        ElementUtil.setProperty(extendedInformation, "x.item:affiliation", affiliation.toString());
        ElementUtil.setProperty(extendedInformation, "x.item:role", role.toString());
        updatePresence();
    }

    @GuardedBy("this")
    private void updatePresence() {
        if (extendedInformation != null && presence != null) {
            // Remove any previous extendedInformation, then re-add it.
            Element mucUser = presence.getElement().element(QName.get("x", "http://jabber.org/protocol/muc#user"));
            if (mucUser != null) {
                // Remove any previous extendedInformation, then re-add it.
                presence.getElement().remove(mucUser);
            }
            Element exi = extendedInformation.createCopy();
            presence.getElement().add(exi);
            cacheSize = -1;
        }
    }

    /**
     * A temporary position or privilege level within a room, distinct from a user's long-lived affiliation with the
     * room. A role lasts only for the duration of an occupant's visit to a room.
     */
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

        private final int value;

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
        public static MUCRole.Role valueOf(int value) {
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

        private final int value;

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
        public static MUCRole.Affiliation valueOf(int value) {
            switch (value) {
                case 10: return owner;
                case 20: return admin;
                case 30: return member;
                case 40: return outcast;
                default: return none;
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((nick == null) ? 0 : nick.hashCode());
        result = prime * result + ((rJID == null) ? 0 : rJID.hashCode());
        result = prime * result + ((roomJid == null) ? 0 : roomJid.hashCode());
        result = prime * result + ((userJid == null) ? 0 : userJid.hashCode());
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
        MUCRole other = (MUCRole) obj;
        if (nick == null) {
            if (other.nick != null)
                return false;
        } else if (!nick.equals(other.nick))
            return false;
        if (rJID == null) {
            if (other.rJID != null)
                return false;
        } else if (!rJID.equals(other.rJID))
            return false;
        if (roomJid == null) {
            if (other.roomJid != null)
                return false;
        } else if (!roomJid.equals(other.roomJid))
            return false;
        if (userJid == null) {
            if (other.userJid != null)
                return false;
        } else if (!userJid.equals(other.userJid))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return "MUCRole{" +
            "roomJid=" + roomJid +
            ", userJid=" + userJid +
            ", nick='" + nick + '\'' +
            ", role=" + role +
            ", affiliation=" + affiliation +
            ", voiceOnly=" + voiceOnly +
            ", rJID=" + rJID +
            ", reportedFmucJID=" + reportedFmucJID +
            '}';
    }

    @Override
    public synchronized int getCachedSize() throws CannotCalculateSizeException {
        if (cacheSize == -1) {
            int size = CacheSizes.sizeOfObject(); // overhead of object.
            size += CacheSizes.sizeOfAnything(roomJid);
            size += CacheSizes.sizeOfAnything(userJid);
            size += CacheSizes.sizeOfString(nick);
            size += CacheSizes.sizeOfAnything(extendedInformation);
            if (presence != null) {
                size += CacheSizes.sizeOfAnything(presence.getElement());
            }
            size += CacheSizes.sizeOfAnything(role);
            size += CacheSizes.sizeOfAnything(affiliation);
            size += CacheSizes.sizeOfBoolean(); // voiceOnly
            size += CacheSizes.sizeOfAnything(rJID);
            size += CacheSizes.sizeOfAnything(reportedFmucJID);

            cacheSize = size;
        }
        return cacheSize;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        try {
            ExternalizableUtil.getInstance().writeSafeUTF(out, roomJid.toString());
            ExternalizableUtil.getInstance().writeBoolean(out, userJid != null);
            if (userJid != null) {
                ExternalizableUtil.getInstance().writeSafeUTF(out, userJid.toString());
            }
            ExternalizableUtil.getInstance().writeSafeUTF(out, nick);
            synchronized (this) {
                ExternalizableUtil.getInstance().writeBoolean(out, presence != null);
                if (presence != null) {
                    ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
                }
            }
            ExternalizableUtil.getInstance().writeSerializable(out, role);
            ExternalizableUtil.getInstance().writeSerializable(out, affiliation);
            ExternalizableUtil.getInstance().writeBoolean(out, voiceOnly);
            ExternalizableUtil.getInstance().writeSafeUTF(out, rJID.toString());
            synchronized (this) {
                ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) extendedInformation);
            }
            ExternalizableUtil.getInstance().writeBoolean(out, reportedFmucJID != null);
            if (reportedFmucJID != null) {
                ExternalizableUtil.getInstance().writeSafeUTF(out, reportedFmucJID.toString());
            }
        } catch (IOException | RuntimeException e ) {
            Log.error("write error", e);
            throw e;
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            roomJid = new JID(ExternalizableUtil.getInstance().readSafeUTF(in), false);
            if (ExternalizableUtil.getInstance().readBoolean(in)) {
                userJid = new JID(ExternalizableUtil.getInstance().readSafeUTF(in), false);
            } else {
                userJid = null;
            }
            nick = ExternalizableUtil.getInstance().readSafeUTF(in);
            synchronized (this) { // Unlikely to be needed, as this should operate on a new instance. Will prevent static analyzers from complaining at negligible cost.
                if (ExternalizableUtil.getInstance().readBoolean(in)) {
                    presence = new Presence((Element) ExternalizableUtil.getInstance().readSerializable(in));
                } else {
                    presence = null;
                }
            }
            role = (MUCRole.Role) ExternalizableUtil.getInstance().readSerializable(in);
            affiliation = (MUCRole.Affiliation) ExternalizableUtil.getInstance().readSerializable(in);
            voiceOnly = ExternalizableUtil.getInstance().readBoolean(in);
            rJID = new JID(ExternalizableUtil.getInstance().readSafeUTF(in), false);
            synchronized (this) { // Unlikely to be needed, as this should operate on a new instance. Will prevent static analyzers from complaining at negligible cost.
                extendedInformation = (Element) ExternalizableUtil.getInstance().readSerializable(in);
            }
            if (ExternalizableUtil.getInstance().readBoolean(in)) {
                reportedFmucJID = new JID(ExternalizableUtil.getInstance().readSafeUTF(in), false);
            }
            cacheSize = -1;
        } catch (IOException | RuntimeException e ) {
            Log.error("read error", e);
            throw e;
        }
    }
}
