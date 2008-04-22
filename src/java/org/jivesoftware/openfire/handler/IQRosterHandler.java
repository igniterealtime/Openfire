/**
 * $RCSfile: IQRosterHandler.java,v $
 * $Revision: 3163 $
 * $Date: 2005-12-05 17:54:23 -0300 (Mon, 05 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.handler;

import org.jivesoftware.stringprep.IDNAException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Implements the TYPE_IQ jabber:iq:roster protocol. Clients
 * use this protocol to retrieve, update, and rosterMonitor roster
 * entries (buddy lists). The server manages the basics of
 * roster subscriptions and roster updates based on presence
 * and iq:roster packets, while the client maintains the user
 * interface aspects of rosters such as organizing roster
 * entries into groups.
 * <p/>
 * A 'get' query retrieves a snapshot of the roster.
 * A 'set' query updates the roster (typically with new group info).
 * The server sends 'set' updates asynchronously when roster
 * entries change status.
 * <p/>
 * Currently an empty implementation to allow usage with normal
 * clients. Future implementation needed.
 * <p/>
 * <h2>Assumptions</h2>
 * This handler assumes that the request is addressed to the server.
 * An appropriate TYPE_IQ tag matcher should be placed in front of this
 * one to route TYPE_IQ requests not addressed to the server to
 * another channel (probably for direct delivery to the recipient).
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class IQRosterHandler extends IQHandler implements ServerFeaturesProvider {

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
    public IQ handleIQ(IQ packet) throws UnauthorizedException, PacketException {
        try {
            IQ returnPacket = null;
            org.xmpp.packet.Roster roster = (org.xmpp.packet.Roster)packet;

            JID recipientJID = packet.getTo();

            // The packet is bound for the server and must be roster management
            if (recipientJID == null || recipientJID.getNode() == null ||
                    !UserManager.getInstance().isRegisteredUser(recipientJID.getNode())) {
                returnPacket = manageRoster(roster);
            }
            // The packet must be a roster removal from a foreign domain user.
            else {
                removeRosterItem(roster);
            }
            return returnPacket;
        }
        catch (SharedGroupException e) {
            IQ result = IQ.createResultIQ(packet);
            result.setChildElement(packet.getChildElement().createCopy());
            result.setError(PacketError.Condition.not_acceptable);
            return result;
        }
        catch (Exception e) {
            if (e.getCause() instanceof IDNAException) {
                Log.warn(LocaleUtils.getLocalizedString("admin.error"), e);
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
     * Remove a roster item. At this stage, this is recipient who has received
     * a roster update. We must check that it is a removal, and if so, remove
     * the roster item based on the sender's id rather than what is in the item
     * listing itself.
     *
     * @param packet The packet suspected of containing a roster removal
     */
    private void removeRosterItem(org.xmpp.packet.Roster packet) throws UnauthorizedException,
            SharedGroupException {
        JID recipientJID = packet.getTo();
        JID senderJID = packet.getFrom();
        try {
            for (org.xmpp.packet.Roster.Item packetItem : packet.getItems()) {
                if (packetItem.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
                    Roster roster = userManager.getUser(recipientJID.getNode()).getRoster();
                    RosterItem item = roster.getRosterItem(senderJID);
                    roster.deleteRosterItem(senderJID, true);
                    item.setSubStatus(RosterItem.SUB_REMOVE);
                    item.setSubStatus(RosterItem.SUB_NONE);

                    Packet itemPacket = packet.createCopy();
                    sessionManager.userBroadcast(recipientJID.getNode(), itemPacket);
                }
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
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
                    !userManager.isRegisteredUser(sender.getNode())) &&
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
                returnPacket = cachedRoster.getReset();
                returnPacket.setType(IQ.Type.result);
                returnPacket.setTo(sender);
                returnPacket.setID(packet.getID());
                // Force delivery of the response because we need to trigger
                // a presence probe from all contacts
                deliverer.deliver(returnPacket);
                returnPacket = null;
            }
            else if (IQ.Type.set == type) {

                for (org.xmpp.packet.Roster.Item item : packet.getItems()) {
                    if (item.getSubscription() == org.xmpp.packet.Roster.Subscription.remove) {
                        removeItem(cachedRoster, packet.getFrom(), item);
                    }
                    else {
                        if (cachedRoster.isRosterItem(item.getJID())) {
                            // existing item
                            RosterItem cachedItem = cachedRoster.getRosterItem(item.getJID());
                            cachedItem.setAsCopyOf(item);
                            cachedRoster.updateRosterItem(cachedItem);
                        }
                        else {
                            // new item
                            cachedRoster.createRosterItem(item);
                        }
                    }
                }
                returnPacket = IQ.createResultIQ(packet);
            }
        }
        catch (UserNotFoundException e) {
            throw new UnauthorizedException(e);
        }

        return returnPacket;

    }

    /**
     * Remove the roster item from the sender's roster (and possibly the recipient's).
     * Actual roster removal is done in the removeItem(Roster,RosterItem) method.
     *
     * @param roster The sender's roster.
     * @param sender The JID of the sender of the removal request
     * @param item   The removal item element
     */
    private void removeItem(org.jivesoftware.openfire.roster.Roster roster, JID sender,
            org.xmpp.packet.Roster.Item item) throws SharedGroupException {
        JID recipient = item.getJID();
        // Remove recipient from the sender's roster
        roster.deleteRosterItem(item.getJID(), true);
        // Forward set packet to the subscriber
        if (localServer.isLocal(recipient)) { // Recipient is local so let's handle it here
            try {
                Roster recipientRoster = userManager.getUser(recipient.getNode()).getRoster();
                recipientRoster.deleteRosterItem(sender, true);
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

    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
        userManager = server.getUserManager();
        router = server.getPacketRouter();
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator<String> getFeatures() {
        ArrayList<String> features = new ArrayList<String>();
        features.add("jabber:iq:roster");
        return features.iterator();
    }
}