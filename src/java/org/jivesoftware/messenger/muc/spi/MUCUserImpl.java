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

import org.dom4j.Element;

import org.jivesoftware.messenger.muc.*;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserAlreadyExistsException;

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
    private XMPPAddress realjid;

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
    MUCUserImpl(MultiUserChatServerImpl chatserver, PacketRouter packetRouter, XMPPAddress jid) {
        this.realjid = jid;
        this.router = packetRouter;
        this.server = chatserver;
    }

    public long getID() throws UnauthorizedException {
        return -1;
    }

    public MUCRole getRole(String roomName) throws UnauthorizedException, NotFoundException {
        MUCRole role = roles.get(roomName.toLowerCase());
        if (role == null) {
            throw new NotFoundException(roomName);
        }
        return role;
    }

    public Iterator<MUCRole> getRoles() throws UnauthorizedException {
        return Collections.unmodifiableCollection(roles.values()).iterator();
    }

    public void removeRole(String roomName) {
        roles.remove(roomName.toLowerCase());
    }

    public long getLastPacketTime() {
        return lastPacketTime;
    }

    /**
     * Generate a conflict packet to indicate that the nickname being requested/used is already in
     * use by another user.
     * 
     * @param packet The packet to be bounced.
     */
    private void sendErrorPacket(XMPPPacket packet, XMPPError.Code errorCode) {
        packet = (XMPPPacket) packet.createDeepCopy();
        packet.setError(errorCode);
        XMPPAddress sender = packet.getSender();
        packet.setSender(packet.getRecipient());
        packet.setRecipient(sender);
        router.route(packet);
    }

    public XMPPAddress getAddress() {
        return realjid;
    }

    public void process(XMPPPacket packet) throws UnauthorizedException, PacketException {
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
        lastPacketTime = System.currentTimeMillis();
        XMPPAddress recipient = packet.getRecipient();
        String group = recipient.getName();
        if (group == null) {
            // Ignore packets to the groupchat server
            // In the future, we'll need to support TYPE_IQ queries to the server for MUC
            Log.info(LocaleUtils.getLocalizedString("muc.error.not-supported") + " "
                    + packet.toString());
        }
        else {
            MUCRole role = roles.get(group.toLowerCase());
            if (role == null) {
                if (server.hasChatRoom(group)) {
                    boolean declinedInvitation = false;
                    XMPPDOMFragment userInfo = null;
                    if (Message.NORMAL == packet.getType()) {
                        // An user that is not an occupant could be declining an invitation
                        userInfo = (XMPPDOMFragment) packet.getFragment(
                            "x",
                            "http://jabber.org/protocol/muc#user");
                        if (userInfo != null
                                && userInfo.getRootElement().element("decline") != null) {
                            // A user has declined an invitation to a room
                            // WARNING: Potential fraud if someone fakes the "from" of the
                            // message with the JID of a member and sends a "decline"
                            declinedInvitation = true;
                        }
                    }
                    if (declinedInvitation) {
                        Element info = userInfo.getRootElement().element("decline");
                        server.getChatRoom(group).sendInvitationRejection(
                            info.attributeValue("to"),
                            info.elementTextTrim("reason"),
                            packet.getSender(),
                            packet.getOriginatingSession());
                    }
                    else {
                        // The sender is not an occupant of the room
                        sendErrorPacket(packet, XMPPError.Code.NOT_ACCEPTABLE);
                    }
                }
                else {
                    // The sender is not an occupant of a NON-EXISTENT room!!!
                    sendErrorPacket(packet, XMPPError.Code.NOT_FOUND);
                }
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                    sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                }
                else {
                    try {
                        if (packet.getSubject() != null && packet.getSubject().trim().length() > 0
                                && Message.GROUP_CHAT == packet.getType()) {
                            // An occupant is trying to change the room's subject
                            role.getChatRoom().changeSubject(packet, role);

                        }
                        else {
                            // An occupant is trying to send a private, send public message,
                            // invite someone to the room or reject an invitation
                            Message.Type type = packet.getType();
                            String resource = packet.getRecipient().getResource();
                            if (resource == null || resource.trim().length() == 0) {
                                resource = null;
                            }
                            if (resource == null && Message.GROUP_CHAT == type) {
                                // An occupant is trying to send a public message
                                role.getChatRoom().sendPublicMessage(packet, role);
                            }
                            else if (resource != null
                                    && (Message.CHAT == type || Message.NORMAL == type)) {
                                // An occupant is trying to send a private message
                                role.getChatRoom().sendPrivateMessage(packet, role);
                            }
                            else if (resource == null && Message.NORMAL == type) {
                                // An occupant could be sending an invitation or declining an
                                // invitation
                                XMPPDOMFragment userInfo = (XMPPDOMFragment) packet.getFragment(
                                    "x",
                                    "http://jabber.org/protocol/muc#user");
                                // Real real real UGLY TRICK!!! Will and MUST be solved when
                                // persistence will be added. Replace locking with transactions!
                                MUCRoomImpl room = (MUCRoomImpl) role.getChatRoom();
                                if (userInfo != null
                                        && userInfo.getRootElement().element("invite") != null) {
                                    // An occupant is sending an invitation
                                    Element info = userInfo.getRootElement().element("invite");

                                    // Add the user as a member of the room if the room is
                                    // members only
                                    if (room.isInvitationRequiredToEnter()) {
                                        room.lock.writeLock().lock();
                                        try {
                                            room.addMember(info.attributeValue("to"), null, role);
                                        }
                                        finally {
                                            room.lock.writeLock().unlock();
                                        }
                                    }

                                    // Send the invitation to the user
                                    room.sendInvitation(info.attributeValue("to"), info
                                            .elementTextTrim("reason"), role, packet
                                            .getOriginatingSession());
                                }
                                else if (userInfo != null
                                        && userInfo.getRootElement().element("decline") != null) {
                                    // An occupant has declined an invitation
                                    Element info = userInfo.getRootElement().element("decline");
                                    room.sendInvitationRejection(info.attributeValue("to"), info
                                            .elementTextTrim("reason"), packet.getSender(), packet
                                            .getOriginatingSession());
                                }
                                else {
                                    sendErrorPacket(packet, XMPPError.Code.BAD_REQUEST);
                                }
                            }
                            else {
                                sendErrorPacket(packet, XMPPError.Code.BAD_REQUEST);
                            }
                        }
                    }
                    catch (UnauthorizedException e) {
                        sendErrorPacket(packet, XMPPError.Code.UNAUTHORIZED);
                    }
                    catch (ForbiddenException e) {
                        sendErrorPacket(packet, XMPPError.Code.FORBIDDEN);
                    }
                    catch (NotFoundException e) {
                        sendErrorPacket(packet, XMPPError.Code.NOT_FOUND);
                    }
                    catch (ConflictException e) {
                        sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                    }
                }
            }
        }
    }

    public void process(IQ packet) {
        lastPacketTime = System.currentTimeMillis();
        XMPPAddress recipient = packet.getRecipient();
        String group = recipient.getName();
        if (group == null) {
            // Ignore packets to the groupchat server
            // In the future, we'll need to support TYPE_IQ queries to the server for MUC
            Log.info(LocaleUtils.getLocalizedString("muc.error.not-supported") + " "
                    + packet.toString());
        }
        else {
            MUCRole role = roles.get(group.toLowerCase());
            if (role == null) {
                // TODO: send error message to user (can't send packets to group you haven't
                // joined)
            }
            else {
                // Check and reject conflicting packets with conflicting roles
                // In other words, another user already has this nickname
                if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                    sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                }
                else {
                    try {
                        if ("query".equals(packet.getChildName())
                                && "http://jabber.org/protocol/muc#owner".equals(packet
                                        .getChildNamespace())) {
                            role.getChatRoom().getIQOwnerHandler().handleIQ(packet, role);
                        }
                        else if ("query".equals(packet.getChildName())
                                && "http://jabber.org/protocol/muc#admin".equals(packet
                                        .getChildNamespace())) {
                            role.getChatRoom().getIQAdminHandler().handleIQ(packet, role);
                        }
                        else {
                            sendErrorPacket(packet, XMPPError.Code.BAD_REQUEST);
                        }
                    }
                    catch (UnauthorizedException e) {
                        sendErrorPacket(packet, XMPPError.Code.UNAUTHORIZED);
                    }
                    catch (ForbiddenException e) {
                        sendErrorPacket(packet, XMPPError.Code.FORBIDDEN);
                    }
                    catch (ConflictException e) {
                        sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                    }
                }
            }
        }
    }

    public void process(Presence packet) {
        lastPacketTime = System.currentTimeMillis();
        try {
            XMPPAddress recipient = packet.getRecipient();
            String group = recipient.getNamePrep();
            if (group == null) {
                if (Presence.UNAVAILABLE == packet.getType()) {
                    server.removeUser(packet.getSender());
                }
            }
            else {
                MUCRole role = roles.get(group.toLowerCase());
                if (role == null) {
                    // If we're not already in a room, we either are joining it or it's not
                    // properly addressed and we drop it silently
                    if (recipient.getResource() != null
                            && recipient.getResource().trim().length() > 0) {
                        if (packet.getType() == Presence.AVAILABLE
                                || Presence.INVISIBLE == packet.getType()) {
                            try {
                                // Get or create the room
                                MUCRoom room = server.getChatRoom(group, packet.getSender());
                                // User must support MUC in order to create a room
                                MetaDataFragment mucInfo = (MetaDataFragment) packet.getFragment("x",
                                        "http://jabber.org/protocol/muc");
                                HistoryRequest historyRequest = null;
                                String password = null;
                                // Check for password & requested history if client supports MUC
                                if (mucInfo != null) {
                                    password = mucInfo.getProperty("x.password");
                                    if (mucInfo.includesProperty("x.history")) {
                                        historyRequest = new HistoryRequest(mucInfo);
                                    }
                                }
                                // The user joins the room
                                role = room.joinRoom(recipient.getResource().trim(),
                                        password,
                                        historyRequest,
                                        this);
                                roles.put(group, role);
                                // If the client that created the room is non-MUC compliant then
                                // unlock the room thus creating an "instant" room
                                if (room.isLocked() && mucInfo == null) {
                                    room.unlockRoom(role);
                                }
                            }
                            catch (UnauthorizedException e) {
                                sendErrorPacket(packet, XMPPError.Code.UNAUTHORIZED);
                            }
                            catch (NotAllowedException e) {
                                sendErrorPacket(packet, XMPPError.Code.NOT_ALLOWED);
                            }
                            catch (UserAlreadyExistsException e) {
                                sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                            }
                            catch (RoomLockedException e) {
                                sendErrorPacket(packet, XMPPError.Code.NOT_FOUND);
                            }
                            catch (ForbiddenException e) {
                                sendErrorPacket(packet, XMPPError.Code.FORBIDDEN);
                            }
                            catch (RegistrationRequiredException e) {
                                sendErrorPacket(packet, XMPPError.Code.REGISTRATION_REQUIRED);
                            }
                            catch (ConflictException e) {
                                sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                            }
                        }
                        else {
                            // TODO: send error message to user (can't send presence to group you
                            // haven't joined)
                        }
                    }
                    else {
                        if (packet.getType() == Presence.AVAILABLE
                                || Presence.INVISIBLE == packet.getType()) {
                            // A resource is required in order to join a room
                            sendErrorPacket(packet, XMPPError.Code.BAD_REQUEST);
                        }
                        // TODO: send error message to user (can't send packets to group you haven't
                        // joined)
                    }
                }
                else {
                    // Check and reject conflicting packets with conflicting roles
                    // In other words, another user already has this nickname
                    if (!role.getChatUser().getAddress().equals(packet.getSender())) {
                        sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                    }
                    else {
                        if (Presence.UNAVAILABLE == packet.getType()) {
                            try {
                                roles.remove(group.toLowerCase());
                                role.getChatRoom().leaveRoom(role.getNickname());
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
                                    role.setPresence(packet);
                                    Presence presence = (Presence) role.getPresence()
                                            .createDeepCopy();
                                    presence.setSender(role.getRoleAddress());
                                    role.getChatRoom().send(presence);
                                }
                                else {
                                    // Occupant has changed his nickname. Send two presences
                                    // to each room occupant

                                    // Answer a conflic error if the new nickname is taken
                                    if (role.getChatRoom().hasOccupant(resource)) {
                                        sendErrorPacket(packet, XMPPError.Code.CONFLICT);
                                    }
                                    else {
                                        // Send "unavailable" presence for the old nickname
                                        Presence presence = (Presence) role.getPresence()
                                                .createDeepCopy();
                                        // Switch the presence to OFFLINE
                                        presence.setVisible(false);
                                        presence.setAvailable(false);
                                        presence.setSender(role.getRoleAddress());
                                        // Add the new nickname and status 303 as properties
                                        MetaDataFragment frag = (MetaDataFragment) presence
                                                .getFragment(
                                                        "x",
                                                        "http://jabber.org/protocol/muc#user");
                                        frag.setProperty("x.item:nick", resource);
                                        frag.setProperty("x.status:code", "303");
                                        role.getChatRoom().send(presence);

                                        // Send availability presence for the new nickname
                                        String oldNick = role.getNickname();
                                        role.setPresence(packet);
                                        role.changeNickname(resource);
                                        role.getChatRoom().nicknameChanged(oldNick, resource);
                                        presence = (Presence) role.getPresence().createDeepCopy();
                                        presence.setSender(role.getRoleAddress());
                                        role.getChatRoom().send(presence);
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
        catch (UnauthorizedException ue) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), ue);
        }
    }
}