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
import org.dom4j.QName;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQPingHandler;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.stanzaid.StanzaIDUtil;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Representation of users interacting with the chat service. A user
 * may join several rooms hosted by the chat service. That means that
 * we are going to have an instance of this class for the user and several
 * MUCRoles for each joined room.
 *
 * This room occupant is being hosted by this JVM. When the room occupant
 * is hosted by another cluster node then an instance of {@link RemoteMUCRole}
 * will be used instead.
 *
 * @author Gaston Dombiak
 */
public class LocalMUCUser implements MUCUser
{
    private static final Logger Log = LoggerFactory.getLogger(LocalMUCUser.class);

    /**
     * The chat server this user belongs to.
     */
    private final MultiUserChatService server;

    /**
     * Real system XMPPAddress for the user.
     */
    private final JID realjid;

    /**
     * Table: key roomName.toLowerCase(); value LocalMUCRole.
     */
    private final Map<String, LocalMUCRole> roles = new ConcurrentHashMap<>();

    /**
     * Deliver packets to users.
     */
    private final PacketRouter router;

    /**
     * Time of last packet sent.
     */
    private long lastPacketTime;

    /**
     * Create a new chat user.
     *
     * @param chatservice  the service the user belongs to.
     * @param packetRouter the router for sending packets from this user.
     * @param jid          the real address of the user
     */
    LocalMUCUser( MultiUserChatService chatservice, PacketRouter packetRouter, JID jid )
    {
        this.realjid = jid;
        this.router = packetRouter;
        this.server = chatservice;
    }

    /**
     * Returns true if the user is currently present in one or more rooms.
     *
     * @return true if the user is currently present in one or more rooms.
     */
    public boolean isJoined()
    {
        return !roles.isEmpty();
    }

    /**
     * Get all roles for this user.
     *
     * @return Iterator over all roles for this user
     */
    public Collection<LocalMUCRole> getRoles()
    {
        return Collections.unmodifiableCollection(roles.values());
    }

    /**
     * Adds the role of the user in a particular room.
     *
     * @param roomName The name of the room.
     * @param role     The new role of the user.
     */
    public void addRole( String roomName, LocalMUCRole role )
    {
        roles.put(roomName, role);
    }

    /**
     * Removes the role of the user in a particular room.
     *
     * Note: PREREQUISITE: A lock on this object has already been obtained.
     *
     * @param roomName The name of the room we're being removed
     */
    public void removeRole( String roomName )
    {
        roles.remove(roomName);
    }

    /**
     * Get time (in milliseconds from System currentTimeMillis()) since last packet.
     *
     * @return The time when the last packet was sent from this user
     */
    public long getLastPacketTime()
    {
        return lastPacketTime;
    }

    /**
     * Generate and send an error packet to indicate that something went wrong.
     *
     * @param packet  the packet to be responded to with an error.
     * @param error   the reason why the operation failed.
     * @param message an optional human-readable error message.
     */
    private void sendErrorPacket( Packet packet, PacketError.Condition error, String message )
    {
        if ( packet instanceof IQ )
        {
            IQ reply = IQ.createResultIQ((IQ) packet);
            reply.setChildElement(((IQ) packet).getChildElement().createCopy());
            reply.setError(error);
            if ( message != null )
            {
                reply.getError().setText(message);
            }
            router.route(reply);
        }
        else
        {
            Packet reply = packet.createCopy();
            reply.setError(error);
            if ( message != null )
            {
                reply.getError().setText(message);
            }
            reply.setFrom(packet.getTo());
            reply.setTo(packet.getFrom());
            router.route(reply);
        }
    }

    /**
     * Generate and send an error packet to indicate that something went wrong when processing an FMUC join request.
     *
     * @param packet  the packet to be responded to with an error.
     * @param message an optional human-readable reject message.
     */
    private void sendFMUCJoinReject( Presence packet, String message )
    {
        final Presence reply = new Presence();

        // XEP-0289: "(..) To do this it sends a 'presence' reply from its bare JID to the bare JID of the joining node (..)"
        reply.setTo( packet.getFrom().asBareJID() );
        reply.setFrom( this.getAddress().asBareJID() );

        final Element reject = reply.addChildElement("fmuc", "http://isode.com/protocol/fmuc").addElement("reject");
        if ( message != null && !message.trim().isEmpty() ) {
            reject.addText( message );
        }
        router.route(reply);
    }

    /**
     * Obtain the address of the user. The address is used by services like the core server packet router to determine
     * if a packet should be sent to the handler. Handlers that are working on behalf of the server should use the
     * generic server hostname address (e.g. server.com).
     *
     * @return the address of the packet handler.
     */
    @Override
    public JID getAddress()
    {
        return realjid;
    }

    /**
     * This method does all packet routing in the chat server. Packet routing is actually very simple:
     *
     * <ul>
     *   <li>Discover the room the user is talking to</li>
     *   <li>If the room is not registered and this is a presence "available" packet, try to join the room</li>
     *   <li>If the room is registered, and presence "unavailable" leave the room</li>
     *   <li>Otherwise, rewrite the sender address and send to the room.</li>
     * </ul>
     *
     * @param packet          The stanza to route
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    @Override
    public void process( Packet packet ) throws UnauthorizedException, PacketException
    {
        // Name of the room that the stanza is addressed to. Can't be null, given the check above.
        final String roomName = packet.getTo().getNode();

        if ( roomName == null )
        {
            // Packets to the groupchat service (as opposed to a specific room on the service). This should not occur
            // (should be handled by MultiUserChatServiceImpl instead).
            Log.warn(LocaleUtils.getLocalizedString("muc.error.not-supported") + " " + packet.toString());
            if ( packet instanceof IQ && ((IQ) packet).isRequest() )
            {
                sendErrorPacket(packet, PacketError.Condition.feature_not_implemented, "Unable to process stanza.");
            }
            return;
        }

        lastPacketTime = System.currentTimeMillis();

        StanzaIDUtil.ensureUniqueAndStableStanzaID(packet, packet.getTo());

        // Determine if this user has a pre-existing role in the addressed room.
        final MUCRole preExistingRole = roles.get(roomName);

        if ( packet instanceof IQ )
        {
            process((IQ) packet, roomName, preExistingRole);
        }
        else if ( packet instanceof Message )
        {
            process((Message) packet, roomName, preExistingRole);
        }
        else if ( packet instanceof Presence )
        {
            process((Presence) packet, roomName, preExistingRole);
        }
    }

    /**
     * Processes a Message stanza.
     *
     * @param packet          The stanza to route
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void process(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nullable final MUCRole preExistingRole )
    {
        if ( Message.Type.error == packet.getType() )
        {
            Log.trace("Ignoring messages of type 'error' sent to MUC room '{}'", roomName);
            return;
        }

        if ( preExistingRole == null )
        {
            processNonOccupantMessage(packet, roomName);
        }
        else
        {
            processOccupantMessage(packet, roomName, preExistingRole);
        }
    }

    /**
     * Processes a Message stanza that was sent by a user that's not in the room.
     *
     * Only declined invitations (to join a room) are acceptable messages from users that are not in the room. Other
     * messages are responded to with an error.
     *
     * @param packet   The stanza to process
     * @param roomName The name of the room that the stanza was addressed to.
     */
    private void processNonOccupantMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName )
    {
        if ( !server.hasChatRoom(roomName) )
        {
            // The sender is not an occupant of a NON-EXISTENT room!!!
            Log.debug("Rejecting message stanza sent by '{}' to room '{}': Room does not exist.", packet.getFrom(), roomName);
            sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The room that the message was addressed to is not available.");
        }

        boolean declinedInvitation = false;
        Element userInfo = null;
        if ( Message.Type.normal == packet.getType() )
        {
            // An user that is not an occupant could be declining an invitation
            userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");
            if ( userInfo != null && userInfo.element("decline") != null )
            {
                // A user has declined an invitation to a room
                // WARNING: Potential fraud if someone fakes the "from" of the
                // message with the JID of a member and sends a "decline"
                declinedInvitation = true;
            }
        }

        if ( declinedInvitation )
        {
            Log.debug("Processing room invitation declination sent by '{}' to room '{}'.", packet.getFrom(), roomName);
            final Element info = userInfo.element("decline");
            server.getChatRoom(roomName).sendInvitationRejection(
                new JID(info.attributeValue("to")),
                info.elementTextTrim("reason"),
                packet.getFrom());
        }
        else
        {
            Log.debug("Rejecting message stanza sent by '{}' to room '{}': Sender is not an occupant of the room: {}", packet.getFrom(), roomName, packet.toXML());
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You are not in the room.");
        }
    }

    /**
     * Processes a Message stanza that was sent by a user that's in the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processOccupantMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        // Check and reject conflicting packets with conflicting roles In other words, another user already has this nickname
        if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
        {
            Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
            sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
            return;
        }

        final MUCRoom chatRoom = preExistingRole.getChatRoom();
        if ( chatRoom.getRoomHistory().isSubjectChangeRequest(packet) )
        {
            processChangeSubjectMessage(packet, roomName, preExistingRole);
            return;
        }

        // An occupant is trying to send a private, send public message, invite someone to the room or reject an invitation.
        final Message.Type type = packet.getType();
        String nickname = packet.getTo().getResource();
        if ( nickname == null || nickname.trim().length() == 0 )
        {
            nickname = null;
        }

        // Public message (not addressed to a specific occupant)
        if ( nickname == null && Message.Type.groupchat == type )
        {
            processPublicMessage(packet, roomName, preExistingRole);
            return;
        }

        // Private message (not addressed to a specific occupant)
        if ( nickname != null && (Message.Type.chat == type || Message.Type.normal == type) )
        {
            processPrivateMessage(packet, roomName, preExistingRole);
            return;
        }

        if ( nickname == null && Message.Type.normal == type )
        {
            // An occupant could be sending an invitation or declining an invitation
            final Element userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");

            if ( userInfo != null && userInfo.element("invite") != null )
            {
                // An occupant is sending invitations
                processSendingInvitationMessage(packet, roomName, preExistingRole);
                return;
            }

            if ( userInfo != null && userInfo.element("decline") != null )
            {
                // An occupant has declined an invitation
                processDecliningInvitationMessage(packet, roomName, preExistingRole);
                return;
            }
        }

        Log.debug("Unable to process message: {}", packet.toXML());
        sendErrorPacket(packet, PacketError.Condition.bad_request, "Unable to process message.");
    }

    /**
     * Process a 'change subject' message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processChangeSubjectMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing subject change request from occupant '{}' to room '{}'.", packet.getFrom(), roomName);
        try
        {
            final MUCRoom chatRoom = preExistingRole.getChatRoom();
            chatRoom.changeSubject(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting subject change request from occupant '{}' to room '{}'.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to change the subject of this room.");
        }
    }

    /**
     * Process a public message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processPublicMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing public message from occupant '{}' to room '{}'.", packet.getFrom(), roomName);
        try
        {
            final MUCRoom chatRoom = preExistingRole.getChatRoom();
            chatRoom.sendPublicMessage(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting public message from occupant '{}' to room '{}'. User is not allowed to send message (might not have voice).", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to send a public message to the room (you might require 'voice').");
        }
    }

    /**
     * Process a private message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processPrivateMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing private message from occupant '{}' to room '{}'.", packet.getFrom(), roomName);
        try
        {
            final MUCRoom chatRoom = preExistingRole.getChatRoom();
            chatRoom.sendPrivatePacket(packet, preExistingRole);
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting private message from occupant '{}' to room '{}'. User has a role that disallows sending private messages in this room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You are not allowed to send a private messages in the room.");
        }
        catch ( NotFoundException e )
        {
            Log.debug("Rejecting private message from occupant '{}' to room '{}'. User addressing a non-existent recipient.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The intended recipient of your private message is not available.");
        }
    }

    /**
     * Process a room-invitation message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processSendingInvitationMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing an invitation message from occupant '{}' to room '{}'.", packet.getFrom(), roomName);
        try
        {
            final Element userInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc#user");

            // Try to keep the list of extensions sent together with the message invitation. These extensions will be sent to the invitees.
            final List<Element> extensions = new ArrayList<>(packet.getElement().elements());
            extensions.remove(userInfo);

            // Send invitations to invitees
            final Iterator<Element> it = userInfo.elementIterator("invite");
            while ( it.hasNext() )
            {
                Element info = it.next();
                JID jid = new JID(info.attributeValue("to"));

                // Add the user as a member of the room if the room is members only
                final MUCRoom chatRoom = preExistingRole.getChatRoom();
                if ( chatRoom.isMembersOnly() )
                {
                    chatRoom.addMember(jid, null, preExistingRole);
                }

                // Send the invitation to the invitee
                chatRoom.sendInvitation(jid, info.elementTextTrim("reason"), preExistingRole, extensions);
            }
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}': Invitations are not allowed, or occupant is not allowed to modify the member list.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "This room disallows invitations to be sent, or you're not allowed to modify the member list of this room.");
        }
        catch ( ConflictException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}'.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.conflict, "An unexpected exception occurred."); // TODO Is this code reachable?
        }
        catch ( CannotBeInvitedException e )
        {
            Log.debug("Rejecting invitation message from occupant '{}' in room '{}': The user being invited does not have access to the room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "The user being invited does not have access to the room.");
        }
    }

    /**
     * Process a declination of a room-invitation message sent by an occupant of the room.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void processDecliningInvitationMessage(
        @Nonnull final Message packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        Log.trace("Processing an invite declination message from '{}' to room '{}'.", packet.getFrom(), roomName);
        final Element info = packet.getChildElement("x", "http://jabber.org/protocol/muc#user").element("decline");
        final MUCRoom chatRoom = preExistingRole.getChatRoom();
        chatRoom.sendInvitationRejection(new JID(info.attributeValue("to")),
                                         info.elementTextTrim("reason"), packet.getFrom());
    }

    /**
     * Processes an IQ stanza.
     *
     * @param packet          The stanza to route
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void process(
        @Nonnull final IQ packet,
        @Nonnull final String roomName,
        @Nullable final MUCRole preExistingRole )
    {
        // Packets to a specific node/group/room
        if ( preExistingRole == null )
        {
            Log.debug("Ignoring stanza received from a non-occupant of '{}': {}", roomName, packet.toXML());
            if ( packet.isRequest() )
            {
                // If a non-occupant sends a disco to an address of the form <room@service/nick>, a MUC service MUST
                // return a <bad-request/> error. http://xmpp.org/extensions/xep-0045.html#disco-occupant
                sendErrorPacket(packet, PacketError.Condition.bad_request, "You are not an occupant of this room.");
            }
            return;
        }

        if ( packet.isResponse() )
        {
            // Only process IQ result packet if it's a private packet sent to another room occupant
            if ( packet.getTo().getResource() != null )
            {
                try
                {
                    // User is sending an IQ result packet to another room occupant
                    preExistingRole.getChatRoom().sendPrivatePacket(packet, preExistingRole);
                }
                catch ( NotFoundException | ForbiddenException e )
                {
                    // Do nothing. No error will be sent to the sender of the IQ result packet
                    Log.debug("Silently ignoring an IQ response sent to the room as a private message that caused an exception while being processed: {}", packet.toXML(), e);
                }
            }
            else
            {
                Log.trace("Silently ignoring an IQ response sent to the room, but not as a private message: {}", packet.toXML());
            }
        }
        else
        {
            // Check and reject conflicting packets with conflicting roles In other words, another user already has this nickname
            if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
            {
                Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
                sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
                return;
            }

            try
            {
                // TODO Analyze if it is correct for these first two blocks to be processed without evaluating if they're addressed to the room or if they're a PM.
                Element query = packet.getElement().element("query");
                if ( query != null && "http://jabber.org/protocol/muc#owner".equals(query.getNamespaceURI()) )
                {
                    preExistingRole.getChatRoom().getIQOwnerHandler().handleIQ(packet, preExistingRole);
                }
                else if ( query != null && "http://jabber.org/protocol/muc#admin".equals(query.getNamespaceURI()) )
                {
                    preExistingRole.getChatRoom().getIQAdminHandler().handleIQ(packet, preExistingRole);
                }
                else
                {
                    final String toNickname = packet.getTo().getResource();
                    if ( toNickname != null )
                    {
                        // User is sending to a room occupant.
                        final boolean selfPingEnabled = JiveGlobals.getBooleanProperty("xmpp.muc.self-ping.enabled", true);
                        if ( selfPingEnabled && toNickname.equals(preExistingRole.getNickname()) && packet.isRequest()
                            && packet.getElement().element(QName.get(IQPingHandler.ELEMENT_NAME, IQPingHandler.NAMESPACE)) != null )
                        {
                            Log.trace("User '{}' is sending an IQ 'ping' to itself. See XEP-0410: MUC Self-Ping (Schrödinger's Chat).", packet.getFrom());
                            router.route(IQ.createResultIQ(packet));
                        }
                        else
                        {
                            Log.trace("User '{}' is sending an IQ stanza to another room occupant (as a PM) with nickname: '{}'.", packet.getFrom(), toNickname);
                            preExistingRole.getChatRoom().sendPrivatePacket(packet, preExistingRole);
                        }
                    }
                    else
                    {
                        Log.debug("An IQ request was addressed to the MUC room '{}' which cannot answer it: {}", roomName, packet.toXML());
                        sendErrorPacket(packet, PacketError.Condition.bad_request, "IQ request cannot be processed by the MUC room itself.");
                    }
                }
            }
            catch ( NotAcceptableException e )
            {
                Log.debug("Unable to process IQ stanza: room requires a password, but none was supplied.", e);
                sendErrorPacket(packet, PacketError.Condition.not_acceptable, "Room requires a password, but none was supplied.");
            }
            catch ( ForbiddenException e )
            {
                Log.debug("Unable to process IQ stanza: sender don't have authorization to perform the request.", e);
                sendErrorPacket(packet, PacketError.Condition.forbidden, "You don't have authorization to perform this request.");
            }
            catch ( NotFoundException e )
            {
                Log.debug("Unable to process IQ stanza: the intended recipient is not available.", e);
                sendErrorPacket(packet, PacketError.Condition.recipient_unavailable, "The intended recipient is not available.");
            }
            catch ( ConflictException e )
            {
                Log.debug("Unable to process IQ stanza: processing this request would leave the room in an invalid state (eg: without owners).", e);
                sendErrorPacket(packet, PacketError.Condition.conflict, "Processing this request would leave the room in an invalid state (eg: without owners).");
            }
            catch ( NotAllowedException e )
            {
                Log.debug("Unable to process IQ stanza: an owner or administrator cannot be banned from the room.", e);
                sendErrorPacket(packet, PacketError.Condition.not_allowed, "An owner or administrator cannot be banned from the room.");
            }
            catch ( CannotBeInvitedException e )
            {
                Log.debug("Unable to process IQ stanza: user being invited as a result of being added to a members-only room still does not have permission.", e);
                sendErrorPacket(packet, PacketError.Condition.not_acceptable, "User being invited as a result of being added to a members-only room still does not have permission.");
            }
            catch ( Exception e )
            {
                Log.error("An unexpected exception occurred while processing IQ stanza: {}", packet.toXML(), e);
                sendErrorPacket(packet, PacketError.Condition.internal_server_error, "An unexpected exception occurred while processing your request.");
            }
        }
    }

    /**
     * Process a Presence stanza.
     *
     * @param packet          The stanza to process.
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza, if any.
     */
    private void process(
        @Nonnull final Presence packet,
        @Nonnull final String roomName,
        @Nullable final MUCRole preExistingRole )
    {
        final Element mucInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc");
        final String nickname = packet.getTo().getResource() == null
            || packet.getTo().getResource().trim().isEmpty() ? null
            : packet.getTo().getResource().trim();

        if ( preExistingRole == null || mucInfo != null )
        {
            // If we're not already in a room (role == null), we either are joining it or it's not properly addressed and we drop it silently.
            // Alternative is that mucInfo is not null, in which case the client thinks it isn't in the room, so we should join anyway.
            processRoomJoinRequest(packet, roomName, nickname);
        }
        else
        {
            // Check and reject conflicting packets with conflicting roles
            // In other words, another user already has this nickname
            if ( !preExistingRole.getUserAddress().equals(packet.getFrom()) )
            {
                Log.debug("Rejecting conflicting stanza with conflicting roles: {}", packet.toXML());
                sendErrorPacket(packet, PacketError.Condition.conflict, "Another user uses this nickname.");
                return;
            }

            try
            {
                if ( nickname != null && !preExistingRole.getNickname().equalsIgnoreCase(nickname) && Presence.Type.unavailable != packet.getType() )
                {
                    // Occupant has changed his nickname. Send two presences to each room occupant.
                    processNickNameChange(packet, preExistingRole, nickname);
                }
                else
                {
                    processPresenceUpdate(packet, roomName, preExistingRole);
                }
            }
            catch ( Exception e )
            {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Process a request to join a room.
     *
     * @param packet   The stanza representing the nickname-change request.
     * @param roomName The name of the room that the stanza was addressed to.
     * @param nickname The requested nickname.
     */
    private void processRoomJoinRequest(
        @Nonnull final Presence packet,
        @Nonnull final String roomName,
        @Nullable String nickname )
    {
        Log.trace("Processing join request from '{}' for room '{}'", packet.getFrom(), roomName);

        if ( nickname == null )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: request did not specify a nickname", packet.getFrom(), roomName);

            // A resource is required in order to join a room http://xmpp.org/extensions/xep-0045.html#enter
            // If the user does not specify a room nickname (note the bare JID on the 'from' address in the following example), the service MUST return a <jid-malformed/> error
            if ( packet.getType() != Presence.Type.error )
            {
                sendErrorPacket(packet, PacketError.Condition.jid_malformed, "A nickname (resource-part) is required in order to join a room.");
            }
            return;
        }

        if ( !packet.isAvailable() )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: request unexpectedly provided a presence stanza of type '{}'. Expected none.", packet.getFrom(), roomName, packet.getType());
            if ( packet.getType() != Presence.Type.error )
            {
                sendErrorPacket(packet, PacketError.Condition.unexpected_request, "Unexpected stanza type: " + packet.getType());
            }
            return;
        }

        final MUCRoom room;
        try
        {
            // Get or create the room
            room = server.getChatRoom(roomName, packet.getFrom());
        }
        catch ( NotAllowedException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user does not have permission to create a new room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.not_allowed, "You do not have permission to create a new room.");
            return;
        }

        try
        {
            // User must support MUC in order to create a room
            HistoryRequest historyRequest = null;
            String password = null;

            // Check for password & requested history if client supports MUC
            final Element mucInfo = packet.getChildElement("x", "http://jabber.org/protocol/muc");
            if ( mucInfo != null )
            {
                password = mucInfo.elementTextTrim("password");
                if ( mucInfo.element("history") != null )
                {
                    historyRequest = new HistoryRequest(mucInfo);
                }
            }

            // The user joins the room
            final MUCRole role = room.joinRoom(nickname,
                                               password,
                                               historyRequest,
                                               this,
                                               packet.createCopy());

            // If the client that created the room is non-MUC compliant then
            // unlock the room thus creating an "instant" room
            if ( mucInfo == null && room.isLocked() && !room.isManuallyLocked() )
            {
                room.unlock(role);
            }
        }
        catch ( UnauthorizedException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user not authorized to create or join the room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.not_authorized, "You're not authorized to create or join the room.");
        }
        catch ( ServiceUnavailableException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: the maximum number of users of the room has been reached.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.service_unavailable, "The maximum number of users of the room has been reached.");
        }
        catch ( UserAlreadyExistsException | ConflictException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: the requested nickname '{}' is being used by someone else in the room.", packet.getFrom(), roomName, nickname, e);
            sendErrorPacket(packet, PacketError.Condition.conflict, "The nickname that is being used is used by someone else.");
        }
        catch ( RoomLockedException e )
        {
            // If a user attempts to enter a room while it is "locked" (i.e., before the room creator provides an initial configuration and therefore before the room officially exists), the service MUST refuse entry and return an <item-not-found/> error to the user
            Log.debug("Request from '{}' to join room '{}' rejected: room is locked.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.item_not_found, "This room is locked (it might not have been configured yet).");
        }
        catch ( ForbiddenException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user not authorized join the room.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.forbidden, "You're not allowed to join this room.");
        }
        catch ( RegistrationRequiredException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: room is member-only, user is not a member.", packet.getFrom(), roomName, e);
            sendErrorPacket(packet, PacketError.Condition.registration_required, "This is a member-only room. Membership is required.");
        }
        catch ( NotAcceptableException e )
        {
            Log.debug("Request from '{}' to join room '{}' rejected: user attempts to use nickname '{}' which is different from the reserved nickname.", packet.getFrom(), roomName, nickname, e);
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You're trying to join with a nickname different than the reserved nickname.");
        }
    }

    /**
     * Process a presence status update for a user.
     *
     * @param packet          The stanza to process
     * @param roomName        The name of the room that the stanza was addressed to.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza.
     */
    private void processPresenceUpdate(
        @Nonnull final Presence packet,
        @Nonnull final String roomName,
        @Nonnull final MUCRole preExistingRole )
    {
        if ( Presence.Type.unavailable == packet.getType() )
        {
            Log.trace("Occupant '{}' of room '{}' is leaving.", preExistingRole.getUserAddress(), roomName);
            // TODO Consider that different nodes can be creating and processing this presence at the same time (when remote node went down)
            preExistingRole.setPresence(packet);
            preExistingRole.getChatRoom().leaveRoom(preExistingRole);
            removeRole(roomName);
        }
        else
        {
            Log.trace("Occupant '{}' of room '{}' changed its availability status.", preExistingRole.getUserAddress(), roomName);
            preExistingRole.getChatRoom().presenceUpdated(preExistingRole, packet);
        }
    }

    /**
     * Process a request to change a nickname.
     *
     * @param packet          The stanza representing the nickname-change request.
     * @param preExistingRole The role of this user in the addressed room prior to processing of this stanza.
     * @param nickname        The requested nickname.
     */
    private void processNickNameChange(
        @Nonnull final Presence packet,
        @Nonnull final MUCRole preExistingRole,
        @Nonnull String nickname )
        throws UserNotFoundException
    {
        final MUCRoom chatRoom = preExistingRole.getChatRoom();

        Log.trace("Occupant '{}' of room '{}' tries to change its nickname to '{}'.", preExistingRole.getUserAddress(), chatRoom.getName(), nickname);

        if ( chatRoom.getOccupantsByBareJID(packet.getFrom().asBareJID()).size() > 1 )
        {
            Log.trace("Nickname change request denied: requestor '{}' is not an occupant of the room.", packet.getFrom().asBareJID());
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "You are not an occupant of this chatroom.");
            return;
        }

        if ( !chatRoom.canChangeNickname() )
        {
            Log.trace("Nickname change request denied: Room configuration does not allow nickname changes.");
            sendErrorPacket(packet, PacketError.Condition.not_acceptable, "Chatroom does not allow nickname changes.");
            return;
        }

        if ( chatRoom.hasOccupant(nickname) )
        {
            Log.trace("Nickname change request denied: the requested nickname '{}' is used by another occupant of the room.", nickname);
            sendErrorPacket(packet, PacketError.Condition.conflict, "This nickname is taken.");
            return;
        }

        // Send "unavailable" presence for the old nickname
        final Presence presence = preExistingRole.getPresence().createCopy();
        // Switch the presence to OFFLINE
        presence.setType(Presence.Type.unavailable);
        presence.setStatus(null);
        // Add the new nickname and status 303 as properties
        final Element frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
        frag.element("item").addAttribute("nick", nickname);
        frag.addElement("status").addAttribute("code", "303");
        chatRoom.send(presence, preExistingRole);

        // Send availability presence for the new nickname
        final String oldNick = preExistingRole.getNickname();
        chatRoom.nicknameChanged(preExistingRole, packet, oldNick, nickname);
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((realjid == null) ? 0 : realjid.hashCode());
        return result;
    }

    @Override
    public boolean equals( Object obj )
    {
        if ( this == obj )
        { return true; }
        if ( obj == null )
        { return false; }
        if ( getClass() != obj.getClass() )
        { return false; }
        LocalMUCUser other = (LocalMUCUser) obj;
        if ( realjid == null )
        {
            if ( other.realjid != null )
            { return false; }
        }
        else if ( !realjid.equals(other.realjid) )
        { return false; }
        return true;
    }
}
