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

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.messenger.user.spi.IQRosterItemImpl;
import java.util.ArrayList;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;

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

    public IQRosterHandler() {
        super("XMPP Roster Handler");
        info = new IQHandlerInfo("query", "jabber:iq:roster");
    }

    /**
     * Handles all roster queries.
     * There are two major types of queries:
     * <ul>
     * <li>Roster remove - A forced removal of items from a roster. Roster
     * removals are the only roster queries allowed to
     * directly affect the roster from another user.</li>
     * <li>Roster management - A local user looking up or updating their
     * roster.</li>
     * </ul>
     *
     * @param packet The update packet
     * @return The reply or null if no reply
     */
    public synchronized IQ handleIQ(IQ packet) throws
            UnauthorizedException, PacketException {
        try {
            IQ returnPacket = null;
            IQRoster roster = (IQRoster)packet;

            XMPPAddress recipientJID = packet.getRecipient();

            // The packet is bound for the server and must be roster management
            if (recipientJID == null || recipientJID.getName() == null) {
                returnPacket = manageRoster(roster);
            }
            else { // The packet must be a roster removal from a foreign domain user
                removeRosterItem(roster);
            }
            return returnPacket;
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        return null;
    }

    /**
     * Remove a roster item. At this stage, this is recipient who has received
     * a roster update. We must check that it is a removal, and if so, remove
     * the roster item based on the sender's id rather than what is in the item
     * listing itself.
     *
     * @param packet The packet suspected of containing a roster removal
     */
    private void removeRosterItem(IQRoster packet) throws
            UnauthorizedException, XMLStreamException {
        XMPPAddress recipientJID = packet.getRecipient();
        XMPPAddress senderJID = packet.getSender();
        try {
            Iterator itemIter = packet.getRosterItems();
            while (itemIter.hasNext()) {
                RosterItem packetItem = (RosterItem)itemIter.next();
                if (packetItem.getSubStatus() == RosterItem.SUB_REMOVE) {
                    Roster roster = userManager.getUser(recipientJID.getName()).getRoster();
                    RosterItem item = roster.getRosterItem(senderJID);
                    roster.deleteRosterItem(senderJID);
                    item.setSubStatus(RosterItem.SUB_REMOVE);
                    item.setSubStatus(RosterItem.SUB_NONE);

                    XMPPPacket itemPacket = (XMPPPacket)packet.createDeepCopy();
                    sessionManager.userBroadcast(recipientJID.getName().toLowerCase(), itemPacket);
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
    private IQ manageRoster(IQRoster packet) throws UnauthorizedException, UserAlreadyExistsException, XMLStreamException {

        IQ returnPacket = null;
        Session session = packet.getOriginatingSession();

        XMPPPacket.Type type = packet.getType();

        try {
            User sessionUser = userManager.getUser(session.getUsername());
            CachedRoster cachedRoster = (CachedRoster)sessionUser.getRoster();
            if (IQ.GET == type) {
                returnPacket = cachedRoster.getReset();
                returnPacket.setType(IQ.RESULT);
                returnPacket.setRecipient(session.getAddress());
                returnPacket.setID(packet.getID());
                // Force delivery of the response because we need to trigger
                // a presence probe from all contacts
                deliverer.deliver(returnPacket);
                returnPacket = null;

                String username = sessionUser.getUsername();
                Iterator items = cachedRoster.getRosterItems();
                while (items.hasNext()) {
                    RosterItem cachedItem = (RosterItem)items.next();
                    if (cachedItem.getSubStatus() == RosterItem.SUB_BOTH
                            || cachedItem.getSubStatus() == RosterItem.SUB_TO) {
                        presenceManager.probePresence(username, cachedItem.getJid());
                    }
                }
            }
            else if (IQ.SET == type) {

                Iterator itemIter = packet.getRosterItems();
                while (itemIter.hasNext()) {
                    RosterItem item = (RosterItem)itemIter.next();
                    if (item.getSubStatus() == RosterItem.SUB_REMOVE) {
                        removeItem(cachedRoster, packet.getSender(), item);
                    }
                    else {
                        if (cachedRoster.isRosterItem(item.getJid())) {
                            // existing item
                            CachedRosterItem cachedItem = (CachedRosterItem)cachedRoster.getRosterItem(item.getJid());
                            cachedItem.setAsCopyOf(item);
                            cachedRoster.updateRosterItem(cachedItem);
                        }
                        else {
                            // new item
                            cachedRoster.createRosterItem(item);
                        }
                    }
                }
                returnPacket = packet.createResult();
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
     * @param sender The XMPPAddress of the sender of the removal request
     * @param item   The removal item element
     */
    private void removeItem(Roster roster, XMPPAddress sender, RosterItem item)
            throws UnauthorizedException, XMLStreamException {

        XMPPAddress recipient = item.getJid();
        // Remove recipient from the sender's roster
        roster.deleteRosterItem(item.getJid());
        // Forward set packet to the subscriber
        if (localServer.isLocal(recipient)) { // Recipient is local so let's handle it here
            try {
                CachedRoster recipientRoster = userManager.getUser(recipient.getName()).getRoster();
                recipientRoster.deleteRosterItem(sender);
            }
            catch (UserNotFoundException e) {
            }
        }
        else { // Recipient is remote so we just forward the packet to them

            XMPPPacket removePacket = createRemoveForward(sender, recipient);
            transporter.deliver(removePacket);
        }
    }

    /**
     * Creates a forwarded removal packet.
     *
     * @param from The sender address to use
     * @param to   The recipient address to use
     * @return The forwarded packet generated
     */
    private XMPPPacket createRemoveForward(XMPPAddress from, XMPPAddress to) throws UnauthorizedException {

        IQ response = packetFactory.getIQ();
        response.setSender(from);
        response.setRecipient(to);
        response.setType(IQ.SET);
        PayloadFragment query = new PayloadFragment("jabber:iq:roster", "query");
        response.setChildFragment(query);
        IQRosterItem responseItem = new IQRosterItemImpl(from);
        responseItem.setSubStatus(RosterItem.SUB_REMOVE);
        query.addFragment(responseItem);

        return response;
    }

    public UserManager userManager;
    public XMPPServer localServer;
    public SessionManager sessionManager;
    public PresenceManager presenceManager;
    public PacketTransporter transporter;
    public PacketFactory packetFactory;
    public RoutingTable routingTable;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = super.getTrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(SessionManager.class, "sessionManager");
        trackInfo.getTrackerClasses().put(PresenceManager.class, "presenceManager");
        trackInfo.getTrackerClasses().put(PacketTransporter.class, "transporter");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        return trackInfo;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:roster");
        return features.iterator();
    }
}
