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

import org.dom4j.Element;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representation of users interacting with the chat service. A user
 * may join serveral rooms hosted by the chat service. That means that
 * we are going to have an instance of this class for the user and several
 * MUCRoles for each joined room.<p>
 *
 * This room occupant is being hosted by this JVM. When the room occupant
 * is hosted by another cluster node then an instance of {@link RemoteMUCRole}
 * will be used instead.
 * 
 * @author Gaston Dombiak
 */
public class LocalMUCUser implements MUCUser {

    private static final Logger Log = LoggerFactory.getLogger(LocalMUCUser.class);

    /** The chat server this user belongs to. */
    private MultiUserChatService server;

    /** Real system XMPPAddress for the user. */
    private JID realjid;

    /** Table: key roomName.toLowerCase(); value LocalMUCRole. */
    private Map<String, LocalMUCRole> roles = new ConcurrentHashMap<>();

    /** Deliver packets to users. */
    private PacketRouter router;

    /**
     * Time of last packet sent.
     */
    private long lastPacketTime;

    /**
     * Create a new chat user.
     * 
     * @param chatservice the service the user belongs to.
     * @param packetRouter the router for sending packets from this user.
     * @param jid the real address of the user
     */
    LocalMUCUser(MultiUserChatService chatservice, PacketRouter packetRouter, JID jid) {
        this.realjid = jid;
        this.router = packetRouter;
        this.server = chatservice;
    }

    /**
     * Returns true if the user is currently present in one or more rooms.
     *
     * @return true if the user is currently present in one or more rooms.
     */
    public boolean isJoined() {
        return !roles.isEmpty();
    }

    /**
     * Get all roles for this user.
     *
     * @return Iterator over all roles for this user
     */
    public Collection<LocalMUCRole> getRoles() {
        return Collections.unmodifiableCollection(roles.values());
    }

    /**
     * Adds the role of the user in a particular room.
     *
     * @param roomName The name of the room.
     * @param role The new role of the user.
     */
    public void addRole(String roomName, LocalMUCRole role) {
        roles.put(roomName, role);
    }

    /**
     * Removes the role of the user in a particular room.<p>
     *
     * Note: PREREQUISITE: A lock on this object has already been obtained.
     *
     * @param roomName The name of the room we're being removed
     */
    public void removeRole(String roomName) {
        roles.remove(roomName);
    }

    /**
     * Get time (in milliseconds from System currentTimeMillis()) since last packet.
     *
     * @return The time when the last packet was sent from this user
     */
    public long getLastPacketTime() {
        return lastPacketTime;
    }

    /**
     * Generate a conflict packet to indicate that the nickname being requested/used is already in
     * use by another user.
     * 
     * @param packet the packet to be bounced.
     * @param error the reason why the operation failed.
     */
    private void sendErrorPacket(Packet packet, PacketError.Condition error) {
        if (packet instanceof IQ) {
            IQ reply = IQ.createResultIQ((IQ) packet);
            reply.setChildElement(((IQ) packet).getChildElement().createCopy());
            reply.setError(error);
            router.route(reply);
        }
        else {
            Packet reply = packet.createCopy();
            reply.setError(error);
            reply.setFrom(packet.getTo());
            reply.setTo(packet.getFrom());
            router.route(reply);
        }
    }

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
    @Override
    public JID getAddress() {
        return realjid;
    }

    @Override
    public void process(Packet packet) throws UnauthorizedException, PacketException {
        if (packet instanceof IQ) {
            process((IQ)packet);
        }
        else if (packet instanceof Message) {
            process((Message)packet);
        }
        else if (packet instanceof Presence) {
            process((Presence)packet);
        }
    }

    /**
     * This method does all packet routing in the chat server. Packet routing is actually very
     * simple:
     * 
     * <ul>
     * <li>Discover the room the user is talking to (server packets are dropped)</li>
     * <li>If the room is not registered and this is a presence "available" packet, try to join the
     * room</li>
     * <li>If the room is registered, and presence "unavailable" leave the room</li>
     * <li>Otherwise, rewrite the sender address and send to the room.</li>
     * </ul>
     * 
     * @param packet The packet to route.
     */
    public void process(Message packet) {
        // Ignore messages of type ERROR sent to a room 
        if (Message.Type.error == packet.getType()) {
            return;
        }
        lastPacketTime = System.currentTimeMillis();
        JID recipient = packet.getTo();
        String group = recipient.getNode();
        if (group == null) {
            // Packets to the groupchat server. This should not occur (should be handled by MultiUserChatServiceImpl instead)
            Log.warn( LocaleUtils.getLocalizedString( "muc.error.not-supported" ) + " " + packet.toString() );
        }
        else {
            MUCRole role = roles.get(group);
            if (role == null) {
                if (server.hasChatRoom(group)) {
                    boolean declinedInvitation = false;
                    Element userInfo = null;
                    if (Message.Type.normal == packet.getType()) {
                        // An user that is not an occupant could be declining an invitation
                        userInfo = packet.getChildElement(
                                "x", "http://jabber.org/protocol/muc#user");
                        if (userInfo != null
                                && userInfo.element("decline") != null) {
                            // A user has declined an invitation to a room
                            // WARNING: Potential fraud if someone fakes the "from" of the
                            // message with the JID of a member and sends a "decline"
                            declinedInvitation = true;
                        }
                    }
                    if (declinedInvitation) {
                        Element info = userInfo.element("decline");
                        server.getChatRoom(group).sendInvitationRejection(
                            new JID(info.attributeValue("to")),
                            info.elementTextTrim("reason"),
                            packet.getFrom());
                    }
                    else {
                        // The sender is not an occupant of the room
                        sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                    }
                }
                else {
                    // The sender is not an occupant of a NON-EXISTENT room!!!
                    sendErrorPacket(packet, PacketError.Condition.recipient_unavailable);
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getUserAddress().equals(packet.getFrom())) {
                    sendErrorPacket(packet, PacketError.Condition.conflict);
                }
                else {
                    try {
                        if (role.getChatRoom().getRoomHistory().isSubjectChangeRequest(packet)) {
                            // An occupant is trying to change the room's subject
                            role.getChatRoom().changeSubject(packet, role);

                        }
                        else {
                            // An occupant is trying to send a private, send public message,
                            // invite someone to the room or reject an invitation
                            Message.Type type = packet.getType();
                            String resource = packet.getTo().getResource();
                            if (resource == null || resource.trim().length() == 0) {
                                resource = null;
                            }
                            if (resource == null && Message.Type.groupchat == type) {
                                // An occupant is trying to send a public message
                                role.getChatRoom().sendPublicMessage(packet, role);
                            }
                            else if (resource != null
                                    && (Message.Type.chat == type || Message.Type.normal == type)) {
                                // An occupant is trying to send a private message
                                role.getChatRoom().sendPrivatePacket(packet, role);
                            }
                            else if (resource == null && Message.Type.normal == type) {
                                // An occupant could be sending an invitation or declining an
                                // invitation
                                Element userInfo = packet.getChildElement(
                                    "x",
                                    "http://jabber.org/protocol/muc#user");
                                // Real real real UGLY TRICK!!! Will and MUST be solved when
                                // persistence will be added. Replace locking with transactions!
                                LocalMUCRoom room = (LocalMUCRoom) role.getChatRoom();
                                if (userInfo != null && userInfo.element("invite") != null) {
                                    // An occupant is sending invitations

                                    // Try to keep the list of extensions sent together with the
                                    // message invitation. These extensions will be sent to the
                                    // invitees.
                                    @SuppressWarnings("unchecked")
                                    List<Element> extensions = new ArrayList<>(packet
                                            .getElement().elements());
                                    extensions.remove(userInfo);
                                    
                                    // Send invitations to invitees
                                    @SuppressWarnings("unchecked")
                                    Iterator<Element> it = userInfo.elementIterator("invite");
                                    while(it.hasNext()) {
                                        Element info = it.next();
                                        JID jid = new JID(info.attributeValue("to"));

                                        // Add the user as a member of the room if the room is
                                        // members only
                                        if (room.isMembersOnly()) {
                                            room.addMember(jid, null, role);
                                        }

                                        // Send the invitation to the invitee
                                        room.sendInvitation(jid,
                                                info.elementTextTrim("reason"), role, extensions);
                                    }
                                }
                                else if (userInfo != null
                                        && userInfo.element("decline") != null) {
                                    // An occupant has declined an invitation
                                    Element info = userInfo.element("decline");
                                    room.sendInvitationRejection(new JID(info.attributeValue("to")),
                                            info.elementTextTrim("reason"), packet.getFrom());
                                }
                                else {
                                    sendErrorPacket(packet, PacketError.Condition.bad_request);
                                }
                            }
                            else {
                                sendErrorPacket(packet, PacketError.Condition.bad_request);
                            }
                        }
                    }
                    catch (ForbiddenException e) {
                        sendErrorPacket(packet, PacketError.Condition.forbidden);
                    }
                    catch (NotFoundException e) {
                        sendErrorPacket(packet, PacketError.Condition.recipient_unavailable);
                    }
                    catch (ConflictException e) {
                        sendErrorPacket(packet, PacketError.Condition.conflict);
                    }
                    catch (CannotBeInvitedException e) {
                        sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                    }
                    catch (IllegalArgumentException e) {
                        sendErrorPacket(packet, PacketError.Condition.jid_malformed);
                    }
                }
            }
        }
    }

    public void process(IQ packet) {
        lastPacketTime = System.currentTimeMillis();
        JID recipient = packet.getTo();
        String group = recipient.getNode();
        if (group == null)
        {
            // Packets to the groupchat server. This should not occur (should be handled by MultiUserChatServiceImpl instead)
            if ( packet.isRequest() )
            {
                sendErrorPacket( packet, PacketError.Condition.feature_not_implemented );
            }
            Log.warn( LocaleUtils.getLocalizedString( "muc.error.not-supported" ) + " " + packet.toString() );
        }
        else
        {
            // Packets to a specific node/group/room
            MUCRole role = roles.get(group);
            if (role == null) {
                Log.debug( "Ignoring stanza received from a non-occupant of '{}': {}", group, packet.toXML() );
                if ( packet.isRequest() )
                {
                    // If a non-occupant sends a disco to an address of the form <room@service/nick>,
                    // a MUC service MUST return a <bad-request/> error.
                    // http://xmpp.org/extensions/xep-0045.html#disco-occupant
                    sendErrorPacket( packet, PacketError.Condition.bad_request );
                }
            }
            else if (IQ.Type.result == packet.getType()
                    || IQ.Type.error == packet.getType()) {
                // Only process IQ result packet if it's a private packet sent to another
                // room occupant
                if (packet.getTo().getResource() != null) {
                    try {
                        // User is sending an IQ result packet to another room occupant
                        role.getChatRoom().sendPrivatePacket(packet, role);
                    }
                    catch (NotFoundException | ForbiddenException e) {
                        // Do nothing. No error will be sent to the sender of the IQ result packet
                    }
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getUserAddress().equals(packet.getFrom())) {
                    sendErrorPacket(packet, PacketError.Condition.conflict);
                }
                else {
                    try {
                        Element query = packet.getElement().element("query");
                        if (query != null &&
                                "http://jabber.org/protocol/muc#owner".equals(query.getNamespaceURI())) {
                            role.getChatRoom().getIQOwnerHandler().handleIQ(packet, role);
                        }
                        else if (query != null &&
                                "http://jabber.org/protocol/muc#admin".equals(query.getNamespaceURI())) {
                            role.getChatRoom().getIQAdminHandler().handleIQ(packet, role);
                        }
                        else {
                            if (packet.getTo().getResource() != null) {
                                // User is sending an IQ packet to another room occupant
                                role.getChatRoom().sendPrivatePacket(packet, role);
                            }
                            else {
                                sendErrorPacket(packet, PacketError.Condition.bad_request);
                            }
                        }
                    }
                    catch (NotAcceptableException e) {
                        sendErrorPacket(packet, PacketError.Condition.not_acceptable );
                    }
                    catch (ForbiddenException e) {
                        sendErrorPacket(packet, PacketError.Condition.forbidden);
                    }
                    catch (NotFoundException e) {
                        sendErrorPacket(packet, PacketError.Condition.recipient_unavailable);
                    }
                    catch (ConflictException e) {
                        sendErrorPacket(packet, PacketError.Condition.conflict);
                    }
                    catch (NotAllowedException e) {
                        sendErrorPacket(packet, PacketError.Condition.not_allowed);
                    }
                    catch (CannotBeInvitedException e) {
                        sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                    }
                    catch (Exception e) {
                        sendErrorPacket(packet, PacketError.Condition.internal_server_error);
                        Log.error(e.getMessage(), e);
                    }
                }
            }
        }
    }

    public void process(Presence packet) {
        lastPacketTime = System.currentTimeMillis();
        JID recipient = packet.getTo();
        String group = recipient.getNode();
        if (group != null) {
            MUCRole role = roles.get(group);
            Element mucInfo = packet.getChildElement("x",
                    "http://jabber.org/protocol/muc");
            if (role == null || mucInfo != null) {
                // If we're not already in a room (role == null), we either are joining it or it's not
                // properly addressed and we drop it silently
                // Alternative is that mucInfo is not null, in which case the client thinks it isn't in the room, so we should join anyway.
                if (recipient.getResource() != null
                        && recipient.getResource().trim().length() > 0) {
                    if (packet.isAvailable()) {
                        try {
                            // Get or create the room
                            MUCRoom room = server.getChatRoom(group, packet.getFrom());
                            // User must support MUC in order to create a room
                            HistoryRequest historyRequest = null;
                            String password = null;
                            // Check for password & requested history if client supports MUC
                            if (mucInfo != null) {
                                password = mucInfo.elementTextTrim("password");
                                if (mucInfo.element("history") != null) {
                                    historyRequest = new HistoryRequest(mucInfo);
                                }
                            }
                            // The user joins the room
                            role = room.joinRoom(recipient.getResource().trim(),
                                    password,
                                    historyRequest,
                                    this,
                                    packet.createCopy());
                            // If the client that created the room is non-MUC compliant then
                            // unlock the room thus creating an "instant" room
                            if (mucInfo == null && room.isLocked() && !room.isManuallyLocked()) {
                                room.unlock(role);
                            }
                        }
                        catch (UnauthorizedException e) {
                            sendErrorPacket(packet, PacketError.Condition.not_authorized);
                        }
                        catch (ServiceUnavailableException e) {
                            sendErrorPacket(packet, PacketError.Condition.service_unavailable);
                        }
                        catch (UserAlreadyExistsException | ConflictException e) {
                            sendErrorPacket(packet, PacketError.Condition.conflict);
                        }
                        catch (RoomLockedException e) {
                            // If a user attempts to enter a room while it is "locked" (i.e., before the room creator provides an initial configuration and therefore before the room officially exists), the service MUST refuse entry and return an <item-not-found/> error to the user
                            sendErrorPacket(packet, PacketError.Condition.item_not_found);
                        }
                        catch (ForbiddenException e) {
                            sendErrorPacket(packet, PacketError.Condition.forbidden);
                        }
                        catch (RegistrationRequiredException e) {
                            sendErrorPacket(packet, PacketError.Condition.registration_required);
                        } catch (NotAcceptableException e) {
                            sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                        }
                        catch (NotAllowedException e) {
                            sendErrorPacket(packet, PacketError.Condition.not_allowed);
                        }
                    }
                    else if (packet.getType() != Presence.Type.error) {
                        sendErrorPacket(packet, PacketError.Condition.unexpected_request);
                    }
                }
                else {
                    if (packet.getType() != Presence.Type.error) {
                        // A resource is required in order to join a room
                        // http://xmpp.org/extensions/xep-0045.html#enter
                        // If the user does not specify a room nickname (note the bare JID on the 'from' address in the following example), the service MUST return a <jid-malformed/> error
                        sendErrorPacket(packet, PacketError.Condition.jid_malformed);
                    }
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getUserAddress().equals(packet.getFrom())) {
                    sendErrorPacket(packet, PacketError.Condition.conflict);
                }
                else {
                    if (Presence.Type.unavailable == packet.getType()) {
                        try {
                            // TODO Consider that different nodes can be creating and processing this presence at the same time (when remote node went down)
                            removeRole(group);
                            role.getChatRoom().leaveRoom(role);
                        }
                        catch (Exception e) {
                            Log.error(e.getMessage(), e);
                        }
                    }
                    else {
                        try {
                            String resource = (recipient.getResource() == null
                                    || recipient.getResource().trim().length() == 0 ? null
                                    : recipient.getResource().trim());
                            if (resource == null
                                    || role.getNickname().equalsIgnoreCase(resource)) {
                                // Occupant has changed his availability status
                                role.getChatRoom().presenceUpdated(role, packet);
                            }
                            else {
                                // Occupant has changed his nickname. Send two presences
                                // to each room occupant

                                if (role.getChatRoom().getOccupantsByBareJID(packet.getFrom().asBareJID()).size() > 1) {
                                    sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                                }
                                // Check if occupants are allowed to change their nicknames
                                else if (!role.getChatRoom().canChangeNickname()) {
                                    sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                                }
                                // Answer a conflic error if the new nickname is taken
                                else if (role.getChatRoom().hasOccupant(resource)) {
                                    sendErrorPacket(packet, PacketError.Condition.conflict);
                                }
                                else {
                                    // Send "unavailable" presence for the old nickname
                                    Presence presence = role.getPresence().createCopy();
                                    // Switch the presence to OFFLINE
                                    presence.setType(Presence.Type.unavailable);
                                    presence.setStatus(null);
                                    // Add the new nickname and status 303 as properties
                                    Element frag = presence.getChildElement("x",
                                            "http://jabber.org/protocol/muc#user");
                                    frag.element("item").addAttribute("nick", resource);
                                    frag.addElement("status").addAttribute("code", "303");
                                    role.getChatRoom().send(presence);

                                    // Send availability presence for the new nickname
                                    String oldNick = role.getNickname();
                                    role.getChatRoom().nicknameChanged(role, packet, oldNick, resource);
                                }
                            }
                        }
                        catch (Exception e) {
                            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                        }
                    }
                }
            }
        } else {
            // Packets to the groupchat server. This should not occur (should be handled by MultiUserChatServiceImpl instead)
            Log.warn( LocaleUtils.getLocalizedString( "muc.error.not-supported" ) + " " + packet.toString() );
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((realjid == null) ? 0 : realjid.hashCode());
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
        LocalMUCUser other = (LocalMUCUser) obj;
        if (realjid == null) {
            if (other.realjid != null)
                return false;
        } else if (!realjid.equals(other.realjid))
            return false;
        return true;
    }
}
