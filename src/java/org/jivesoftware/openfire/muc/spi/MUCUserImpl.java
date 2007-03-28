/**
 * $RCSfile$
 * $Revision: 3084 $
 * $Date: 2005-11-15 23:51:41 -0300 (Tue, 15 Nov 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of MUCUser. There will be a MUCUser per user that is connected to one or more 
 * rooms. A MUCUser contains a collection of MUCRoles for each room where the user has joined.
 * 
 * @author Gaston Dombiak
 */
public class MUCUserImpl implements MUCUser {

    /** The chat server this user belongs to. */
    private MultiUserChatServer server;

    /** Real system XMPPAddress for the user. */
    private JID realjid;

    /** Table: key roomName.toLowerCase(); value MUCRole. */
    private Map<String, MUCRole> roles = new ConcurrentHashMap<String, MUCRole>();

    /** Deliver packets to users. */
    private PacketRouter router;

    /**
     * Time of last packet sent.
     */
    private long lastPacketTime;

    /**
     * Create a new chat user.
     * 
     * @param chatserver the server the user belongs to.
     * @param packetRouter the router for sending packets from this user.
     * @param jid the real address of the user
     */
    MUCUserImpl(MultiUserChatServerImpl chatserver, PacketRouter packetRouter, JID jid) {
        this.realjid = jid;
        this.router = packetRouter;
        this.server = chatserver;
    }

    public long getID() {
        return -1;
    }

    public MUCRole getRole(String roomName) throws NotFoundException {
        MUCRole role = roles.get(roomName);
        if (role == null) {
            throw new NotFoundException(roomName);
        }
        return role;
    }

    public boolean isJoined() {
        return !roles.isEmpty();
    }

    public Iterator<MUCRole> getRoles() {
        return Collections.unmodifiableCollection(roles.values()).iterator();
    }

    public void addRole(String roomName, MUCRole role) {
        roles.put(roomName, role);
    }

    public void removeRole(String roomName) {
        roles.remove(roomName);
    }

    public long getLastPacketTime() {
        return lastPacketTime;
    }

    /**
     * Generate a conflict packet to indicate that the nickname being requested/used is already in
     * use by another user.
     * 
     * @param packet the packet to be bounced.
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

    public JID getAddress() {
        return realjid;
    }

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
            // Ignore packets to the groupchat server
            // In the future, we'll need to support TYPE_IQ queries to the server for MUC
            Log.info(LocaleUtils.getLocalizedString("muc.error.not-supported") + " "
                    + packet.toString());
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
                if (!role.getChatUser().getAddress().equals(packet.getFrom())) {
                    sendErrorPacket(packet, PacketError.Condition.conflict);
                }
                else {
                    try {
                        if (packet.getSubject() != null && packet.getSubject().trim().length() > 0
                                && Message.Type.groupchat == packet.getType()) {
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
                                MUCRoomImpl room = (MUCRoomImpl) role.getChatRoom();
                                if (userInfo != null && userInfo.element("invite") != null) {
                                    // An occupant is sending invitations

                                    // Try to keep the list of extensions sent together with the
                                    // message invitation. These extensions will be sent to the
                                    // invitees.
                                    List<Element> extensions = new ArrayList<Element>(packet
                                            .getElement().elements());
                                    extensions.remove(userInfo);
                                    // Send invitations to invitees
                                    for (Iterator it=userInfo.elementIterator("invite");it.hasNext();) {
                                        Element info = (Element) it.next();

                                        // Add the user as a member of the room if the room is
                                        // members only
                                        if (room.isMembersOnly()) {
                                            room.lock.writeLock().lock();
                                            try {
                                                room.addMember(info.attributeValue("to"), null, role);
                                            }
                                            finally {
                                                room.lock.writeLock().unlock();
                                            }
                                        }

                                        // Send the invitation to the invitee
                                        room.sendInvitation(new JID(info.attributeValue("to")),
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
                }
            }
        }
    }

    public void process(IQ packet) {
        // Ignore IQs of type ERROR or RESULT sent to a room
        if (IQ.Type.error == packet.getType()) {
            return;
        }
        lastPacketTime = System.currentTimeMillis();
        JID recipient = packet.getTo();
        String group = recipient.getNode();
        if (group == null) {
            // Ignore packets to the groupchat server
            // In the future, we'll need to support TYPE_IQ queries to the server for MUC
            Log.info(LocaleUtils.getLocalizedString("muc.error.not-supported") + " "
                    + packet.toString());
        }
        else {
            MUCRole role = roles.get(group);
            if (role == null) {
                // TODO: send error message to user (can't send packets to group you haven't
                // joined)
            }
            else if (IQ.Type.result == packet.getType()) {
                // Only process IQ result packet if it's a private packet sent to another
                // room occupant
                if (packet.getTo().getResource() != null) {
                    try {
                        // User is sending an IQ result packet to another room occupant
                        role.getChatRoom().sendPrivatePacket(packet, role);
                    }
                    catch (NotFoundException e) {
                        // Do nothing. No error will be sent to the sender of the IQ result packet
                    }
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getFrom())) {
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
                    catch (Exception e) {
                        sendErrorPacket(packet, PacketError.Condition.internal_server_error);
                    }
                }
            }
        }
    }

    public void process(Presence packet) {
        // Ignore presences of type ERROR sent to a room
        if (Presence.Type.error == packet.getType()) {
            return;
        }
        lastPacketTime = System.currentTimeMillis();
        JID recipient = packet.getTo();
        String group = recipient.getNode();
        if (group == null) {
            if (Presence.Type.unavailable == packet.getType()) {
                server.removeUser(packet.getFrom());
            }
        }
        else {
            MUCRole role = roles.get(group);
            if (role == null) {
                // If we're not already in a room, we either are joining it or it's not
                // properly addressed and we drop it silently
                if (recipient.getResource() != null
                        && recipient.getResource().trim().length() > 0) {
                    if (packet.isAvailable()) {
                        try {
                            // Get or create the room
                            MUCRoom room = server.getChatRoom(group, packet.getFrom());
                            // User must support MUC in order to create a room
                            Element mucInfo = packet.getChildElement("x",
                                    "http://jabber.org/protocol/muc");
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
                        catch (UserAlreadyExistsException e) {
                            sendErrorPacket(packet, PacketError.Condition.conflict);
                        }
                        catch (RoomLockedException e) {
                            sendErrorPacket(packet, PacketError.Condition.recipient_unavailable);
                        }
                        catch (ForbiddenException e) {
                            sendErrorPacket(packet, PacketError.Condition.forbidden);
                        }
                        catch (RegistrationRequiredException e) {
                            sendErrorPacket(packet, PacketError.Condition.registration_required);
                        }
                        catch (ConflictException e) {
                            sendErrorPacket(packet, PacketError.Condition.conflict);
                        }
                        catch (NotAcceptableException e) {
                            sendErrorPacket(packet, PacketError.Condition.not_acceptable);
                        }
                        catch (NotAllowedException e) {
                            sendErrorPacket(packet, PacketError.Condition.not_allowed);
                        }
                    }
                    else {
                        // TODO: send error message to user (can't send presence to group you
                        // haven't joined)
                    }
                }
                else {
                    if (packet.isAvailable()) {
                        // A resource is required in order to join a room
                        sendErrorPacket(packet, PacketError.Condition.bad_request);
                    }
                    // TODO: send error message to user (can't send packets to group you haven't
                    // joined)
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getFrom())) {
                    sendErrorPacket(packet, PacketError.Condition.conflict);
                }
                else {
                    if (Presence.Type.unavailable == packet.getType()) {
                        try {
                            removeRole(group);
                            role.getChatRoom().leaveRoom(role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing since the users has already left the room
                        }
                        catch (Exception e) {
                            Log.error(e);
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
                                role.setPresence(packet.createCopy());
                                role.getChatRoom().send(role.getPresence().createCopy());
                            }
                            else {
                                // Occupant has changed his nickname. Send two presences
                                // to each room occupant

                                // Check if occupants are allowed to change their nicknames
                                if (!role.getChatRoom().canChangeNickname()) {
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
                                    role.setPresence(packet.createCopy());
                                    role.changeNickname(resource);
                                    role.getChatRoom().nicknameChanged(oldNick, resource);
                                    role.getChatRoom().send(role.getPresence().createCopy());
                                }
                            }
                        }
                        catch (Exception e) {
                            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                        }
                    }
                }
            }
        }
    }
}