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

import org.jivesoftware.messenger.muc.ConflictException;
import org.jivesoftware.messenger.muc.ForbiddenException;
import org.jivesoftware.messenger.muc.MUCRole;
import org.jivesoftware.messenger.muc.NotAllowedException;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

/**
 * A handler for the IQ packet with namespace http://jabber.org/protocol/muc#admin. This kind of 
 * packets are usually sent by room admins. So this handler provides the necessary functionality
 * to support administrator requirements such as: managing room members/outcasts/etc., kicking 
 * occupants and banning users.
 *
 * @author Gaston Dombiak
 */
public class IQAdminHandler {
    private MUCRoomImpl room;

    private PacketRouter router;

    public IQAdminHandler(MUCRoomImpl chatroom, PacketRouter packetRouter) {
        this.room = chatroom;
        this.router = packetRouter;
    }

    public void handleIQ(IQ packet, MUCRole role) throws ForbiddenException, UnauthorizedException,
            ConflictException {
        IQ reply = packet.createResult();
        Element element = ((XMPPDOMFragment)packet.getChildFragment()).getRootElement();

        // Analyze the action to perform based on the included element
        List itemsList = element.elements("item");
        if (!itemsList.isEmpty()) {
            handleItemsElement(role, itemsList, reply);
        }
        else {
            // An unknown and possibly incorrect element was included in the query
            // element so answer a BAD_REQUEST error
            reply.setError(XMPPError.Code.BAD_REQUEST);
        }
        router.route(reply);
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
     */
    private void handleItemsElement(MUCRole senderRole, List itemsList, IQ reply)
            throws ForbiddenException, ConflictException {
        Element item;
        String affiliation = null;
        String roleAttribute = null;
        boolean hasJID = ((Element)itemsList.get(0)).attributeValue("jid") != null;
        boolean hasNick = ((Element)itemsList.get(0)).attributeValue("nick") != null;
        // Check if the client is requesting or changing the list of moderators/members/etc.
        if (!hasJID && !hasNick) {
            // The client is requesting the list of moderators/members/participants/outcasts
            for (Iterator items = itemsList.iterator(); items.hasNext();) {
                item = (Element)items.next();
                affiliation = item.attributeValue("affiliation");
                roleAttribute = item.attributeValue("role");
                // Create the result that will hold an item for each
                // moderator/member/participant/outcast
                MetaDataFragment result = new MetaDataFragment(DocumentHelper.createElement(QName
                        .get("query", "http://jabber.org/protocol/muc#admin")));

                MetaDataFragment metaData;
                MUCRole role;
                if ("outcast".equals(affiliation)) {
                    // The client is requesting the list of outcasts
                    if (MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                            && MUCRole.OWNER != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    String jid;
                    for (Iterator<String> it = room.getOutcasts(); it.hasNext();) {
                        jid = it.next();
                        metaData = new MetaDataFragment("http://jabber.org/protocol/muc#admin",
                                "item");
                        metaData.setProperty("item:affiliation", "outcast");
                        metaData.setProperty("item:jid", jid);
                        // Add the item with the outcast's information to the result
                        result.addFragment(metaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else if ("member".equals(affiliation)) {
                    // The client is requesting the list of members
                    // In a members-only room members can get the list of members
                    if (!room.isInvitationRequiredToEnter()
                            && MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                            && MUCRole.OWNER != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    String jid;
                    for (Iterator<String> it = room.getMembers(); it.hasNext();) {
                        jid = it.next();
                        metaData = new MetaDataFragment("http://jabber.org/protocol/muc#admin",
                                "item");
                        metaData.setProperty("item:affiliation", "member");
                        metaData.setProperty("item:jid", jid);
                        try {
                            List<MUCRole> roles = room.getOccupantsByBareJID(jid);
                            role = roles.get(0);
                            metaData.setProperty("item:role", role.getRoleAsString());
                            metaData.setProperty("item:nick", role.getNickname());
                        }
                        catch (UserNotFoundException e) {
                            // Do nothing
                        }
                        // Add the metadata to the result
                        result.addFragment(metaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else if ("moderator".equals(roleAttribute)) {
                    // The client is requesting the list of moderators
                    if (MUCRole.ADMINISTRATOR != senderRole.getAffiliation()
                            && MUCRole.OWNER != senderRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                    for (Iterator<MUCRole> roles = room.getModerators(); roles.hasNext();) {
                        role = roles.next();
                        metaData = new MetaDataFragment("http://jabber.org/protocol/muc#admin",
                                "item");
                        metaData.setProperty("item:role", "moderator");
                        metaData.setProperty("item:jid", role.getChatUser().getAddress()
                                .toStringPrep());
                        metaData.setProperty("item:nick", role.getNickname());
                        metaData.setProperty("item:affiliation", role.getAffiliationAsString());
                        // Add the metadata to the result
                        result.addFragment(metaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else if ("participant".equals(roleAttribute)) {
                    // The client is requesting the list of participants
                    if (MUCRole.MODERATOR != senderRole.getRole()) {
                        throw new ForbiddenException();
                    }
                    for (Iterator<MUCRole> roles = room.getParticipants(); roles.hasNext();) {
                        role = roles.next();
                        metaData = new MetaDataFragment("http://jabber.org/protocol/muc#admin",
                                "item");
                        metaData.setProperty("item:role", "participant");
                        metaData.setProperty("item:jid", role.getChatUser().getAddress()
                                .toStringPrep());
                        metaData.setProperty("item:nick", role.getNickname());
                        metaData.setProperty("item:affiliation", role.getAffiliationAsString());
                        // Add the metadata to the result
                        result.addFragment(metaData);
                    }
                    // Add the result items to the reply
                    reply.addFragment(result);
                }
                else {
                    reply.setError(XMPPError.Code.BAD_REQUEST);
                }
            }
        }
        else {
            // The client is modifying the list of moderators/members/participants/outcasts
            String jid = null;
            String nick;
            String target = null;
            boolean hasAffiliation = ((Element) itemsList.get(0)).attributeValue("affiliation") !=
                    null;

            // Keep a registry of the updated presences
            List<Presence> presences = new ArrayList<Presence>(itemsList.size());

            // Collect the new affiliations or roles for the specified jids
            for (Iterator items = itemsList.iterator(); items.hasNext();) {
                try {
                    item = (Element)items.next();
                    target = (hasAffiliation ? item.attributeValue("affiliation") : item
                            .attributeValue("role"));
                    // jid could be of the form "full JID" or "bare JID" depending if we are
                    // going to change a role or an affiliation
                    if (hasJID) {
                        jid = item.attributeValue("jid");
                    }
                    else {
                        // Get the JID based on the requested nick
                        nick = item.attributeValue("nick");
                        jid = room.getOccupant(nick).getChatUser().getAddress().toStringPrep();
                    }

                    room.lock.writeLock().lock();
                    try {
                        if ("moderator".equals(target)) {
                            // Add the user as a moderator of the room based on the full JID
                            presences.add(room.addModerator(jid, senderRole));
                        }
                        else if ("participant".equals(target)) {
                            // Add the user as a participant of the room based on the full JID
                            presences.add(room.addParticipant(jid,
                                    item.elementTextTrim("reason"),
                                    senderRole));
                        }
                        else if ("visitor".equals(target)) {
                            // Add the user as a visitor of the room based on the full JID
                            presences.add(room.addVisitor(jid, senderRole));
                        }
                        else if ("member".equals(target)) {
                            // Add the user as a member of the room based on the bare JID
                            boolean hadAffiliation = room.getAffiliation(XMPPAddress
                                    .parseBareAddress(jid)) != MUCRole.NONE;
                            presences.addAll(room.addMember(
                                    XMPPAddress.parseBareAddress(jid),
                                    null,
                                    senderRole));
                            // If the user had an affiliation don't send an invitation. Otherwise
                            // send an invitation if the room is members-only
                            if (!hadAffiliation && room.isInvitationRequiredToEnter()) {
                                room.sendInvitation(jid, null, senderRole, reply
                                        .getOriginatingSession());
                            }
                        }
                        else if ("outcast".equals(target)) {
                            // Add the user as an outcast of the room based on the bare JID
                            presences.addAll(room.addOutcast(XMPPAddress.parseBareAddress(jid),
                                    item.elementTextTrim("reason"),
                                    senderRole));
                        }
                        else if ("none".equals(target)) {
                            if (hasAffiliation) {
                                // Set that this jid has a NONE affiliation based on the bare JID
                                presences.addAll(room.addNone(XMPPAddress.parseBareAddress(jid),
                                        senderRole));
                            }
                            else {
                                // Kick the user from the room
                                if (MUCRole.MODERATOR != senderRole.getRole()) {
                                    throw new ForbiddenException();
                                }
                                presences.add(room.kickOccupant(jid, senderRole.getChatUser()
                                        .getAddress().toBareStringPrep(), item
                                        .elementTextTrim("reason")));
                            }
                        }
                        else {
                            reply.setError(XMPPError.Code.BAD_REQUEST);
                        }
                    }
                    catch (NotAllowedException e) {
                        reply.setError(XMPPError.Code.NOT_ALLOWED);
                    }
                    finally {
                        room.lock.writeLock().unlock();
                    }
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
            }

            // Send the updated presences to the room occupants
            try {
                for (Presence presence : presences) {
                    room.send(presence);
                }
            }
            catch (UnauthorizedException e) {
                // Do nothing
            }
        }
    }
}