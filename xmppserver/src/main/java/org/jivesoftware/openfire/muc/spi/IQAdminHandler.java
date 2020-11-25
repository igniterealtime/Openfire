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

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupJID;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.muc.CannotBeInvitedException;
import org.jivesoftware.openfire.muc.ConflictException;
import org.jivesoftware.openfire.muc.ForbiddenException;
import org.jivesoftware.openfire.muc.MUCRole;
import org.jivesoftware.openfire.muc.NotAllowedException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * A handler for the IQ packet with namespace http://jabber.org/protocol/muc#admin. This kind of 
 * packets are usually sent by room admins. So this handler provides the necessary functionality
 * to support administrator requirements such as: managing room members/outcasts/etc., kicking 
 * occupants and banning users.
 *
 * @author Gaston Dombiak
 */
public class IQAdminHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(IQAdminHandler.class);

    private final LocalMUCRoom room;

    private final PacketRouter router;

    private final boolean skipInvite;

    public IQAdminHandler(LocalMUCRoom chatroom, PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
        this.skipInvite = JiveGlobals.getBooleanProperty("xmpp.muc.skipInvite", false);
    }

    /**
     * Handles the IQ packet sent by an owner or admin of the room. Possible actions are:
     * <ul>
     * <li>Return the list of participants</li>
     * <li>Return the list of moderators</li>
     * <li>Return the list of members</li>
     * <li>Return the list of outcasts</li>
     * <li>Change user's affiliation to member</li>
     * <li>Change user's affiliation to outcast</li>
     * <li>Change user's affiliation to none</li>
     * <li>Change occupant's affiliation to moderator</li>
     * <li>Change occupant's affiliation to participant</li>
     * <li>Change occupant's affiliation to visitor</li>
     * <li>Kick occupants from the room</li>
     * </ul>
     *
     * @param packet the IQ packet sent by an owner or admin of the room.
     * @param role the role of the user that sent the request packet.
     * @throws ForbiddenException If the user is not allowed to perform his request.
     * @throws ConflictException If the desired room nickname is already reserved for the room or
     *                           if the room was going to lose all of its owners.
     * @throws NotAllowedException Thrown if trying to ban an owner or an administrator.
     * @throws CannotBeInvitedException If the user being invited as a result of being added to a members-only room still does not have permission
     */
    public void handleIQ(IQ packet, MUCRole role) throws ForbiddenException, ConflictException,
            NotAllowedException, CannotBeInvitedException {
        IQ reply = IQ.createResultIQ(packet);
        Element element = packet.getChildElement();

        // Analyze the action to perform based on the included element
        @SuppressWarnings("unchecked")
        List<Element> itemsList = element.elements("item");
        
        if (!itemsList.isEmpty()) {
            handleItemsElement(role, itemsList, reply);
        }
        else {
            // An unknown and possibly incorrect element was included in the query
            // element so answer a BAD_REQUEST error
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.bad_request);
        }
        if (reply.getTo() != null) {
            // Send a reply only if the sender of the original packet was from a real JID. (i.e. not
            // a packet generated locally)
            router.route(reply);
        }
    }

    /**
     * Handles packets that includes item elements. Depending on the item's attributes the
     * interpretation of the request may differ. For example, an item that only contains the
     * "affiliation" attribute is requesting the list of participants or members. Whilst if the item
     * contains the affiliation together with a jid means that the client is changing the
     * affiliation of the requested jid.
     *
     * @param senderRole the role of the user that sent the request packet.
     * @param itemsList  the list of items sent by the client.
     * @param reply      the iq packet that will be sent back as a reply to the client's request.
     * @throws ForbiddenException If the user is not allowed to perform his request.
     * @throws ConflictException If the desired room nickname is already reserved for the room or
     *                           if the room was going to lose all of its owners.
     * @throws NotAllowedException Thrown if trying to ban an owner or an administrator.
     * @throws CannotBeInvitedException If the user being invited as a result of being added to a members-only room still does not have permission
     */
    private void handleItemsElement(MUCRole senderRole, List<Element> itemsList, IQ reply)
            throws ForbiddenException, ConflictException, NotAllowedException, CannotBeInvitedException {
        String affiliation;
        String roleAttribute;
        boolean hasJID = itemsList.get(0).attributeValue("jid") != null;
        boolean hasNick = itemsList.get(0).attributeValue("nick") != null;
        // Check if the client is requesting or changing the list of moderators/members/etc.
        if (!hasJID && !hasNick) {
            // The client is requesting the list of moderators/members/participants/outcasts

            // Create the result that will hold an item for each
            // moderator/member/participant/outcast
            Element result = reply.setChildElement("query", "http://jabber.org/protocol/muc#admin");

            for (final Element item : itemsList) {
                affiliation = item.attributeValue("affiliation");
                roleAttribute = item.attributeValue("role");

                Element metaData;
                if ("outcast".equals(affiliation)) {
                    // The client is requesting the list of outcasts
                    if (MUCRole.Affiliation.admin != senderRole.getAffiliation()
                            && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    for (JID jid : room.getOutcasts()) {
                        if (GroupJID.isGroup(jid)) {
                            try {
                                // add each group member to the result (clients don't understand groups)
                                Group group = GroupManager.getInstance().getGroup(jid);
                                for (JID groupMember : group.getAll()) {
                                    metaData = addAffiliationToResult(affiliation, result, groupMember);
                                }
                            } catch (GroupNotFoundException gnfe) {
                                logger.warn("Invalid group JID in the outcast list: " + jid);
                            }
                        } else {
                            metaData = addAffiliationToResult(affiliation, result, jid);
                        }
                    }

                } else if ("member".equals(affiliation)) {
                    // The client is requesting the list of members
                    // In a members-only room members can get the list of members
                    if (!room.isMembersOnly()
                            && MUCRole.Affiliation.admin != senderRole.getAffiliation()
                            && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    for (JID jid : room.getMembers()) {
                        if (GroupJID.isGroup(jid)) {
                            try {
                                // add each group member to the result (clients don't understand groups)
                                Group group = GroupManager.getInstance().getGroup(jid);
                                for (JID groupMember : group.getAll()) {
                                    metaData = addAffiliationToResult(affiliation, result, groupMember);
                                }
                            } catch (GroupNotFoundException gnfe) {
                                logger.warn("Invalid group JID in the member list: " + jid);
                            }
                        } else {
                            metaData = addAffiliationToResult(affiliation, result, jid);
                        }
                    }
                } else if ("moderator".equals(roleAttribute)) {
                    // The client is requesting the list of moderators
                    if (MUCRole.Affiliation.admin != senderRole.getAffiliation()
                            && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    for (MUCRole role : room.getModerators()) {
                        metaData = result.addElement("item", "http://jabber.org/protocol/muc#admin");
                        metaData.addAttribute("role", "moderator");
                        metaData.addAttribute("jid", role.getUserAddress().toString());
                        metaData.addAttribute("nick", role.getNickname());
                        metaData.addAttribute("affiliation", role.getAffiliation().toString());
                    }
                } else if ("participant".equals(roleAttribute)) {
                    // The client is requesting the list of participants
                    if (MUCRole.Role.moderator != senderRole.getRole()) {
                        throw new ForbiddenException();
                    }
                    for (MUCRole role : room.getParticipants()) {
                        metaData = result.addElement("item", "http://jabber.org/protocol/muc#admin");
                        metaData.addAttribute("role", "participant");
                        metaData.addAttribute("jid", role.getUserAddress().toString());
                        metaData.addAttribute("nick", role.getNickname());
                        metaData.addAttribute("affiliation", role.getAffiliation().toString());
                    }
                } else if ("owner".equals(affiliation)) {
                    // The client is requesting the list of owners
                    for (JID jid : room.getOwners()) {
                        if (GroupJID.isGroup(jid)) {
                            try {
                                // add each group member to the result (clients don't understand groups)
                                Group group = GroupManager.getInstance().getGroup(jid);
                                for (JID groupMember : group.getAll()) {
                                    metaData = addAffiliationToResult(affiliation, result, groupMember);
                                }
                            } catch (GroupNotFoundException gnfe) {
                                logger.warn("Invalid group JID in the owner list: " + jid);
                            }
                        } else {
                            metaData = addAffiliationToResult(affiliation, result, jid);
                        }
                    }
                } else if ("admin".equals(affiliation)) {
                    // The client is requesting the list of admins
                    for (JID jid : room.getAdmins()) {
                        if (GroupJID.isGroup(jid)) {
                            try {
                                // add each group member to the result (clients don't understand groups)
                                Group group = GroupManager.getInstance().getGroup(jid);
                                for (JID groupMember : group.getAll()) {
                                    metaData = addAffiliationToResult(affiliation, result, groupMember);
                                }
                            } catch (GroupNotFoundException gnfe) {
                                logger.warn("Invalid group JID in the admin list: " + jid);
                            }
                        } else {
                            metaData = addAffiliationToResult(affiliation, result, jid);
                        }
                    }
                } else {
                    reply.setError(PacketError.Condition.bad_request);
                }
            }
        }
        else {
            // The client is modifying the list of moderators/members/participants/outcasts
            String nick;
            String target;
            boolean hasAffiliation;

            // Keep a registry of the updated presences
            List<Presence> presences = new ArrayList<>(itemsList.size());

            // Collect the new affiliations or roles for the specified jids
            for (final Element item : itemsList) {
                try {
                    affiliation = item.attributeValue("affiliation");
                    hasAffiliation = affiliation != null;
                    target = (hasAffiliation ? affiliation : item.attributeValue("role"));
                    List<JID> jids = new ArrayList<>();
                    // jid could be of the form "full JID" or "bare JID" depending if we are
                    // going to change a role or an affiliation
                    nick = item.attributeValue("nick");
                    if (hasJID) {
                        // could be a group JID
                        jids.add(GroupJID.fromString(item.attributeValue("jid")));
                    } else {
                        // Get the JID based on the requested nick
                        for (MUCRole role : room.getOccupantsByNickname(nick)) {
                            if (!jids.contains(role.getUserAddress())) {
                                jids.add(role.getUserAddress());
                            }
                        }
                    }

                    for (JID jid : jids) {
                        switch (target) {
                            case "moderator":
                                // Add the user as a moderator of the room based on the full JID
                                presences.add(room.addModerator(jid, senderRole));
                                break;

                            case "owner":
                                presences.addAll(room.addOwner(jid, senderRole));
                                break;

                            case "admin":
                                presences.addAll(room.addAdmin(jid, senderRole));
                                break;

                            case "participant":
                                // Add the user as a participant of the room based on the full JID
                                presences.add(room.addParticipant(jid,
                                    item.elementTextTrim("reason"),
                                    senderRole));
                                break;

                            case "visitor":
                                // Add the user as a visitor of the room based on the full JID
                                presences.add(room.addVisitor(jid, senderRole));
                                break;

                            case "member":
                                // Add the user as a member of the room based on the bare JID
                                boolean hadAffiliation = room.getAffiliation(jid) != MUCRole.Affiliation.none;
                                presences.addAll(room.addMember(jid, nick, senderRole));
                                // If the user had an affiliation don't send an invitation. Otherwise
                                // send an invitation if the room is members-only and skipping invites
                                // are not disabled system-wide xmpp.muc.skipInvite
                                if (!skipInvite && !hadAffiliation && room.isMembersOnly()) {
                                    List<JID> invitees = new ArrayList<>();
                                    if (GroupJID.isGroup(jid)) {
                                        try {
                                            Group group = GroupManager.getInstance().getGroup(jid);
                                            invitees.addAll(group.getAll());
                                        } catch (GroupNotFoundException gnfe) {
                                            logger.error("Failed to send invitations for group members", gnfe);
                                        }
                                    } else {
                                        invitees.add(jid);
                                    }
                                    for (JID invitee : invitees) {
                                        room.sendInvitation(invitee, null, senderRole, null);
                                    }
                                }
                                break;

                            case "outcast":
                                // Add the user as an outcast of the room based on the bare JID
                                presences.addAll(room.addOutcast(jid, item.elementTextTrim("reason"), senderRole));
                                break;

                            case "none":
                                if (hasAffiliation) {
                                    // Set that this jid has a NONE affiliation based on the bare JID
                                    presences.addAll(room.addNone(jid, senderRole));
                                } else {
                                    // Kick the user from the room
                                    if (MUCRole.Role.moderator != senderRole.getRole()) {
                                        throw new ForbiddenException();
                                    }
                                    presences.add(room.kickOccupant(jid, senderRole.getUserAddress(), senderRole.getNickname(),
                                        item.elementTextTrim("reason")));
                                }
                                break;

                            default:
                                reply.setError(PacketError.Condition.bad_request);
                                break;
                        }
                    }
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
            }

            // Send the updated presences to the room occupants
            for (Presence presence : presences) {
                room.send(presence, room.getRole());
            }
        }
    }

    private Element addAffiliationToResult(String affiliation, Element parent, JID jid) {
        Element result = parent.addElement("item", "http://jabber.org/protocol/muc#admin");
        result.addAttribute("affiliation", affiliation);
        result.addAttribute("jid", jid.toString());
        try {
            List<MUCRole> roles = room.getOccupantsByBareJID(jid);
            MUCRole role = roles.get(0);
            result.addAttribute("role", role.getRole().toString());
            result.addAttribute("nick", role.getNickname());
        }
        catch (UserNotFoundException e) {
            // the JID is not currently an occupant
        }
        return result;
    }
}
