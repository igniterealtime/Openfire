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

package org.jivesoftware.openfire.handler;

import gnu.inet.encoding.IDNAException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.PacketException;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * Implements the TYPE_IQ jabber:iq:roster protocol. Clients
 * use this protocol to retrieve, update, and rosterMonitor roster
 * entries (buddy lists). The server manages the basics of
 * roster subscriptions and roster updates based on presence
 * and iq:roster packets, while the client maintains the user
 * interface aspects of rosters such as organizing roster
 * entries into groups.
 * <p>
 * A 'get' query retrieves a snapshot of the roster.
 * A 'set' query updates the roster (typically with new group info).
 * The server sends 'set' updates asynchronously when roster
 * entries change status.
 * </p>
 * <p>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.</p>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQRosterHandler extends IQHandler implements ServerFeaturesProvider {

    private static final Logger Log = LoggerFactory.getLogger(IQRosterHandler.class);

    private IQHandlerInfo info;

    private UserManager userManager;
    private XMPPServer localServer;
    private PacketRouter router;

    public IQRosterHandler() {
        super("XMPP Roster Handler");
        info = new IQHandlerInfo("query", "jabber:iq:roster");
    }

    /**
     * Handles all roster queries. There are two major types of queries:
     *
     * <ul>
     *      <li>Roster remove - A forced removal of items from a roster. Roster
     *      removals are the only roster queries allowed to
     *      directly affect the roster from another user.
     *      </li>
     *      <li>Roster management - A local user looking up or updating their
     *      roster.
     *      </li>
     * </ul>
     *
     * @param packet The update packet
     * @return The reply or null if no reply
     */
    @Override
    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        try {
            IQ returnPacket;
            org.xmpp.packet.Roster roster = (org.xmpp.packet.Roster)packet;

            JID recipientJID = packet.getTo();

            // The packet is bound for the server and must be roster management
            if (recipientJID == null || recipientJID.equals(packet.getFrom().asBareJID())) {
                returnPacket = manageRoster(roster);
            } else {
                returnPacket = IQ.createResultIQ(packet);
                // The server MUST return a <forbidden/> stanza error to the client if the sender of the roster set is not authorized to update the roster
                // (where typically only an authenticated resource of the account itself is authorized).
                returnPacket.setError(PacketError.Condition.forbidden);
            }
            return returnPacket;
        }
        catch (SharedGroupException e) {
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.not_acceptable);
            return result;
        }
        catch (UnauthorizedException e) {
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.not_authorized);
            return result;
        }
        catch (Exception e) {
            if (e.getCause() instanceof IDNAException || e.getCause() instanceof IllegalArgumentException) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error") + e.getMessage());
                IQ result = IQ.createResultIQ(packet);
                result.setChildElement(packet.getChildElement().createCopy());
                result.setError(PacketError.Condition.jid_malformed);
                return result;
            }
            else {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                IQ result = IQ.createResultIQ(packet);
                result.setChildElement(packet.getChildElement().createCopy());
                result.setError(PacketError.Condition.internal_server_error);
                return result;
            }
        }
    }

    /**
     * The packet is a typical 'set' or 'get' update targeted at the server.
     * Notice that the set could be a roster removal in which case we have to
     * generate a local roster removal update as well as a new roster removal
     * to send to the the roster item's owner.
     *
     * @param packet The packet that triggered this update
     * @return Either a response to the roster update or null if the packet is corrupt and the session was closed down
     */
    private IQ manageRoster(org.xmpp.packet.Roster packet) throws UnauthorizedException,
            UserAlreadyExistsException, SharedGroupException {

        IQ returnPacket = null;
        JID sender = packet.getFrom();
        IQ.Type type = packet.getType();

        try {
            if ((sender.getNode() == null || !RosterManager.isRosterServiceEnabled() ||
                    !userManager.isRegisteredUser(sender, false)) &&
                    IQ.Type.get == type) {
                // If anonymous user asks for his roster or roster service is disabled then
                // return an empty roster
                IQ reply = IQ.createResultIQ(packet);
                reply.setChildElement("query", "jabber:iq:roster");
                return reply;
            }
            if (!localServer.isLocal(sender)) {
                // Sender belongs to a remote server so discard this IQ request
                Log.warn("Discarding IQ roster packet of remote user: " + packet);
                return null;
            }

            Roster cachedRoster = userManager.getUser(sender.getNode()).getRoster();
            if (IQ.Type.get == type) {

                if (RosterManager.isRosterVersioningEnabled()) {
                    String clientVersion = packet.getChildElement().attributeValue("ver");
                    String latestVersion = String.valueOf( cachedRoster.hashCode() );
                    // Whether or not the roster has been modified since the version ID enumerated by the client, ...
                    if (!latestVersion.equals(clientVersion)) {
                        // ... the server MUST either return the complete roster
                        // (including a 'ver' attribute that signals the latest version)
                        returnPacket = cachedRoster.getReset();
                        returnPacket.getChildElement().addAttribute("ver", latestVersion );
                    } else {
                        // ... or return an empty IQ-result
                        returnPacket = new org.xmpp.packet.IQ();
                    }
                } else {
                    returnPacket = cachedRoster.getReset();
                }
                returnPacket.setType(IQ.Type.result);
                returnPacket.setTo(sender);
                returnPacket.setID(packet.getID());
                // Force delivery of the response because we need to trigger
                // a presence probe from all contacts
                deliverer.deliver(returnPacket);
                returnPacket = null;
            }
            else if (IQ.Type.set == type) {
                returnPacket = IQ.createResultIQ(packet);

                // RFC 6121 2.3.3.  Error Cases:
                // The server MUST return a <bad-request/> stanza error to the client if the roster set contains any of the following violations:
                // The <query/> element contains more than one <item/> child element.
                if (packet.getItems().size() > 1) {
                    returnPacket.setError(new PacketError(PacketError.Condition.bad_request, PacketError.Type.modify, "Query contains more than one item"));
                } else {
                    for (org.xmpp.packet.Roster.Item item : packet.getItems()) {
                        if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
                            if (removeItem(cachedRoster, packet.getFrom(), item) == null) {
                                // RFC 6121 2.5.3.  Error Cases: If the value of the 'jid' attribute specifies an item that is not in the roster, then the server MUST return an <item-not-found/> stanza error.
                                returnPacket.setError(PacketError.Condition.item_not_found);
                            }
                        } else {
                            PacketError error = checkGroups(item.getGroups());
                            if (error != null) {
                                returnPacket.setError(error);
                            } else {
                                if (cachedRoster.isRosterItem(item.getJID())) {
                                    // existing item
                                    RosterItem cachedItem = cachedRoster.getRosterItem(item.getJID());
                                    cachedItem.setAsCopyOf(item);
                                    cachedRoster.updateRosterItem(cachedItem);
                                } else {
                                    // new item
                                    cachedRoster.createRosterItem(item);
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }

        return returnPacket;

    }

    /**
     * Checks the roster groups for error conditions described in RFC 6121 § 2.3.3.
     *
     * @param groups The groups.
     * @return An error if the specification is violated or null if everything is fine.
     */
    private static PacketError checkGroups(Iterable<String> groups) {
        Set<String> set = new HashSet<>();
        for (String group : groups) {
            if (!set.add(group)) {
                // Duplicate group found.
                // RFC 6121 2.3.3.  Error Cases: 2. The <item/> element contains more than one <group/> element, but there are duplicate groups
                return new PacketError(PacketError.Condition.bad_request, PacketError.Type.modify, "Item contains duplicate groups");
            }
            if (group.isEmpty()) {
                // The server MUST return a <not-acceptable/> stanza error to the client if the roster set contains any of the following violations:
                // 2. The XML character data of the <group/> element is of zero length.
                return new PacketError(PacketError.Condition.not_acceptable, PacketError.Type.modify, "Group is of zero length");
            }
        }
        return null;
    }

    /**
     * Remove the roster item from the sender's roster (and possibly the recipient's).
     * Actual roster removal is done in the removeItem(Roster,RosterItem) method.
     *
     * @param roster The sender's roster.
     * @param sender The JID of the sender of the removal request
     * @param item   The removal item element
     * @return The removed item or null, if not item has been removed.
     */
    private RosterItem removeItem(org.jivesoftware.openfire.roster.Roster roster, JID sender,
            org.xmpp.packet.Roster.Item item) throws SharedGroupException {
        JID recipient = item.getJID();
        // Remove recipient from the sender's roster
        RosterItem removedItem = roster.deleteRosterItem(item.getJID(), true);

        // Forward set packet to the subscriber
        if (localServer.isLocal(recipient)) { // Recipient is local so let's handle it here
            try {
                Roster recipientRoster = userManager.getUser(recipient.getNode()).getRoster();
                // Instead of deleting the sender in the recipient's roster, update it.
                // https://igniterealtime.atlassian.net/browse/OF-720
                RosterItem rosterItem = recipientRoster.getRosterItem(sender);
                // If the receiver doesn't have subscribed yet, delete the sender from the receiver's roster, too.
                if (rosterItem.getRecvStatus().equals(RosterItem.RECV_SUBSCRIBE)) {
                    recipientRoster.deleteRosterItem(sender, true);
                }
                // Otherwise only update it, so that the sender is not deleted from the receivers roster.
                else {
                    rosterItem.setAskStatus(RosterItem.ASK_NONE);
                    rosterItem.setRecvStatus(RosterItem.RECV_NONE);
                    rosterItem.setSubStatus(RosterItem.SUB_NONE);
                    recipientRoster.updateRosterItem(rosterItem);
                }
            }
            catch (UserNotFoundException e) {
                // Do nothing
            }
        }
        else {
            // Recipient is remote so we just forward the packet to them
            String serverDomain = localServer.getServerInfo().getXMPPDomain();
            // Check if the recipient may be hosted by this server
            if (!recipient.getDomain().contains(serverDomain)) {
                // TODO Implete when s2s is implemented
            }
            else {
                Packet removePacket = createRemoveForward(sender, recipient);
                router.route(removePacket);
            }
        }
        return removedItem;
    }

    /**
     * Creates a forwarded removal packet.
     *
     * @param from The sender address to use
     * @param to   The recipient address to use
     * @return The forwarded packet generated
     */
    private Packet createRemoveForward(JID from, JID to) {
        org.xmpp.packet.Roster response = new org.xmpp.packet.Roster(IQ.Type.set);
        response.setFrom(from);
        response.setTo(to);
        response.addItem(from, org.xmpp.packet.Roster.Subscription.remove);

        return response;
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
        userManager = server.getUserManager();
        router = server.getPacketRouter();
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public Iterator<String> getFeatures() {
        return Collections.singleton("jabber:iq:roster").iterator();
    }
}
