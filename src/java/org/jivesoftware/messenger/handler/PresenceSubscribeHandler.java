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

import org.jivesoftware.util.CacheManager;
import org.jivesoftware.messenger.container.TrackInfo;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.chatbot.ChatbotManager;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.messenger.user.spi.CachedRosterImpl;
import java.util.Hashtable;
import java.util.Map;

/**
 * Implements the presence protocol. Clients use this protocol to
 * update presence and roster information.
 * <p/>
 * The handler must properly detect the presence type, update the user's roster,
 * and inform presence subscribers of the session's updated presence
 * status. Presence serves many purposes in Jabber so this handler will
 * likely be the most complex of all handlers in the server.
 * <p/>
 * There are four basic types of presence updates:
 * <ul>
 * <li>Simple presence updates - addressed to the server (or to address), these updates
 * are properly addressed by the server, and multicast to
 * interested subscribers on the user's roster. An empty, missing,
 * or "unavailable" type attribute indicates a simple update (there
 * is no "available" type although it should be accepted by the server.
 * <li>Directed presence updates - addressed to particular jabber entities,
 * these presence updates are properly addressed and directly delivered
 * to the entity without broadcast to roster subscribers. Any update type
 * is possible except those reserved for subscription requests.
 * <li>Subscription requests - these updates request presence subscription
 * status changes. Such requests always affect the roster.  The server must:
 * <ul>
 * <li>update the roster with the proper subscriber info
 * <li>push the roster changes to the user
 * <li>forward the update to the correct parties.
 * </ul>
 * The valid types include "subscribe", "subscribed", "unsubscribed",
 * and "unsubscribe".
 * <li>BasicServer probes - Provides a mechanism for servers to query the presence
 * status of users on another server. This allows users to immediately
 * know the presence status of users when they come online rather than way
 * for a presence update broadcast from the other server or tracking them
 * as they are received.  Requires S2S capabilities.
 * </ul>
 * <p/>
 * <h2>Warning</h2>
 * There should be a way of determining whether a session has
 * authorization to access this feature. I'm not sure it is a good
 * idea to do authorization in each handler. It would be nice if
 * the framework could assert authorization policies across channels.
 *
 * @author Iain Shigeoka
 */
public class PresenceSubscribeHandler extends BasicModule implements ChannelHandler {

    public PresenceSubscribeHandler() {
        super("Presence subscription handler");
    }

    public void process(XMPPPacket xmppPacket) throws UnauthorizedException, PacketException {
        Presence presence = (Presence)xmppPacket;
        try {
            XMPPAddress senderJID = presence.getSender();
            XMPPAddress recipientJID = presence.getRecipient();
            XMPPPacket.Type type = presence.getType();
            Roster roster = getRoster(senderJID);
            if (roster != null) {
                manageSub(recipientJID, true, type, roster);
            }
            roster = getRoster(recipientJID);
            if (roster != null) {
                manageSub(senderJID, false, type, roster);
            }
            if (chatbotManager.isChatbot(recipientJID)) {
                RoutableChannelHandler route = routingTable.getRoute(recipientJID);
                route.process((XMPPPacket)presence.createDeepCopy());
            }
            else {
                try {
                    // Try to obtain a handler for the packet based on the routes. If the handler is
                    // a module, the module will be able to handle the packet. If the handler is a
                    // Session the packet will be routed to the client. If a route cannot be found
                    // then the packet will be delivered based on its recipient and sender.
                    ChannelHandler handler = routingTable.getRoute(recipientJID);
                    handler.process((XMPPPacket)presence.createDeepCopy());
                }
                catch (NoSuchRouteException e) {
                    deliverer.deliver((XMPPPacket)presence.createDeepCopy());
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * <p>Obtain the roster for the given address or null if the address doesn't have a roster.</p>
     *
     * @param address The address to check
     * @return The roster or null if the address is not managed on the server
     */
    private Roster getRoster(XMPPAddress address) {
        Long userID = null;
        Roster roster = null;
        if (localServer.isLocal(address) && !"".equals(address.getName())) {
            try {
                if (chatbotManager != null) {
                    if (chatbotManager.isChatbot(address)) {
                        userID = chatbotManager.getChatbotID(address.getNamePrep());
                    }
                }
            }
            catch (UserNotFoundException e) {
                // do nothing
            }
            try {
                if (userID == null && userManager != null) {
                    userID = userManager.getUserID(address.getNamePrep());
                }
            }
            catch (UserNotFoundException e) {
                // do nothing
            }
            if (userID != null) {
                // Check for a cached roster:
                roster = (Roster)CacheManager.getCache("userid2roster").get(userID);
                if (roster == null) {
                    // Not in cache so load a new one:
                    roster = new CachedRosterImpl(userID, address.getNamePrep());
                    CacheManager.getCache("userid2roster").put(userID, roster);
                }
            }
        }
        return roster;
    }


    /**
     * Manage the subscription request. This method retrieves a user's roster
     * and updates it's state, storing any changes made, and updating the roster
     * owner if changes occured.
     *
     * @param target    The roster target's jid (the item's jid to be changed)
     * @param isSending True if the request is being sent by the owner
     * @param type      The subscription change type (subscribe, unsubscribe, etc.)
     * @throws UnauthorizedException If a security access violation occurs
     */
    private void manageSub(XMPPAddress target,
                           boolean isSending,
                           XMPPPacket.Type type,
                           Roster roster)
            throws UnauthorizedException, UserAlreadyExistsException {
        try {
            RosterItem item;
            if (roster.isRosterItem(target)) {
                item = roster.getRosterItem(target);
            }
            else {
                item = roster.createRosterItem(target);
            }
            //System.out.println("A " + item + " is " + item.getSubStatus() + " " + item.getAskStatus() + " " + item.getRecvStatus());
            updateState(item, type, isSending);
            //System.out.println("B " + item + " is " + item.getSubStatus() + " " + item.getAskStatus() + " " + item.getRecvStatus());
            roster.updateRosterItem(item);
        }
        catch (UserNotFoundException e) {
            // Should be there because we just checked that it's an item
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * <p>The transition state table.</p>
     * <p>The root 'old state' transition table is a Map of RosterItem.SubType keys to match
     * to the old state of the item. Each key returns a Map containing the next
     * transition table. Transitions are defined as:</p>
     * <ul>
     * <li>'send/receive' table: Lookup whether this updates was sent or received: obtain 'action' table - key: Presence.Type subcribe action, value: Map (transition table).</li>
     * <li>'new state' table: the changed item values</li>
     * </ul>
     */
    private static Hashtable stateTable = new Hashtable();

    static {
        Hashtable subrTable;
        Hashtable subsTable;
        Hashtable sr;

        sr = new Hashtable();
        subrTable = new Hashtable();
        subsTable = new Hashtable();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_NONE, sr);
        // Item wishes to subscribe from owner
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a roster subscription negotiation.
        subrTable.put(Presence.SUBSCRIBE, new Change(RosterItem.RECV_SUBSCRIBE, null, null)); // no transition
        // Item granted subscription to owner
        // The item's state immediately goes from NONE to TO and ask is reset
        subrTable.put(Presence.SUBSCRIBED, new Change(null, RosterItem.SUB_TO, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This makes no sense, there is no subscription to remove
        subrTable.put(Presence.UNSUBSCRIBE, new Change(null, null, null));
        // Owner has subscription to item revoked
        // Valid response if item requested subscription and we're denying request
        subrTable.put(Presence.UNSUBSCRIBED, new Change(null, null, RosterItem.ASK_NONE));
        // Owner asking to subscribe to item this is the normal way to begin
        // a roster subscription negotiation.
        subsTable.put(Presence.SUBSCRIBE, new Change(null, null, RosterItem.ASK_SUBSCRIBE));
        // Item granted a subscription from owner
        subsTable.put(Presence.SUBSCRIBED, new Change(RosterItem.RECV_NONE, RosterItem.SUB_FROM, null));
        // Owner asking to unsubscribe to item
        // This makes no sense (there is no subscription to unsubscribe from)
        subsTable.put(Presence.UNSUBSCRIBE, new Change(null, null, null));
        // Item has subscription from owner revoked
        // Valid response if item requested subscription and we're denying request
        subsTable.put(Presence.UNSUBSCRIBED, new Change(RosterItem.RECV_NONE, null, null));

        sr = new Hashtable();
        subrTable = new Hashtable();
        subsTable = new Hashtable();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_FROM, sr);
        // Owner asking to subscribe to item
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subsTable.put(Presence.SUBSCRIBE, new Change(null, null, RosterItem.ASK_SUBSCRIBE));
        // Item granted a subscription from owner
        // This may be necessary if the recipient didn't get an earlier subscribed grant
        // or as a denial of an unsubscribe request
        subsTable.put(Presence.SUBSCRIBED, new Change(RosterItem.RECV_NONE, null, null));
        // Owner asking to unsubscribe to item
        // This makes no sense (there is no subscription to unsubscribe from)
        subsTable.put(Presence.UNSUBSCRIBE, new Change(null, null, null));
        // Item has subscription from owner revoked
        // Immediately transition to NONE state
        subsTable.put(Presence.UNSUBSCRIBED, new Change(RosterItem.RECV_NONE, RosterItem.SUB_NONE, null));
        // Item wishes to subscribe from owner
        // Item already has a subscription so only interesting if item had requested unsubscribe
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subrTable.put(Presence.SUBSCRIBE, new Change(RosterItem.RECV_NONE, null, null));
        // Item granted subscription to owner
        // The item's state immediately goes from FROM to BOTH and ask is reset
        subrTable.put(Presence.SUBSCRIBED, new Change(null, RosterItem.SUB_BOTH, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This is the normal mechanism of removing subscription
        subrTable.put(Presence.UNSUBSCRIBE, new Change(RosterItem.RECV_UNSUBSCRIBE, null, null));
        // Owner has subscription to item revoked
        // Valid response if owner requested subscription and item is denying request
        subrTable.put(Presence.UNSUBSCRIBED, new Change(null, null, RosterItem.ASK_NONE));

        sr = new Hashtable();
        subrTable = new Hashtable();
        subsTable = new Hashtable();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_TO, sr);
        // Owner asking to subscribe to item
        // We're already subscribed, may be trying to unset a unsub request
        subsTable.put(Presence.SUBSCRIBE, new Change(null, null, RosterItem.ASK_NONE));
        // Item granted a subscription from owner
        // Sets mutual subscription
        subsTable.put(Presence.SUBSCRIBED, new Change(RosterItem.RECV_NONE, RosterItem.SUB_BOTH, null));
        // Owner asking to unsubscribe to item
        // Normal method of removing subscription
        subsTable.put(Presence.UNSUBSCRIBE, new Change(null, null, RosterItem.ASK_UNSUBSCRIBE));
        // Item has subscription from owner revoked
        // No subscription to unsub, makes sense if denying subscription request or for
        // situations where the original unsubscribed got lost
        subsTable.put(Presence.UNSUBSCRIBED, new Change(RosterItem.RECV_NONE, null, null));
        // Item wishes to subscribe from owner
        // This is the normal way to negotiate a mutual subscription
        subrTable.put(Presence.SUBSCRIBE, new Change(RosterItem.RECV_SUBSCRIBE, null, null));
        // Item granted subscription to owner
        // Owner already subscribed to item, could be a unsub denial or a lost packet
        subrTable.put(Presence.SUBSCRIBED, new Change(null, null, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // There is no subscription. May be trying to cancel earlier subscribe request.
        subrTable.put(Presence.UNSUBSCRIBE, new Change(RosterItem.RECV_NONE, null, null));
        // Owner has subscription to item revoked
        // Setting subscription to none
        subrTable.put(Presence.UNSUBSCRIBED, new Change(null, RosterItem.SUB_NONE, RosterItem.ASK_NONE));

        sr = new Hashtable();
        subrTable = new Hashtable();
        subsTable = new Hashtable();
        sr.put("recv", subrTable);
        sr.put("send", subsTable);
        stateTable.put(RosterItem.SUB_BOTH, sr);
        // Owner asking to subscribe to item
        // Makes sense if trying to cancel previous unsub request
        subsTable.put(Presence.SUBSCRIBE, new Change(null, null, RosterItem.ASK_NONE));
        // Item granted a subscription from owner
        // This may be necessary if the recipient didn't get an earlier subscribed grant
        // or as a denial of an unsubscribe request
        subsTable.put(Presence.SUBSCRIBED, new Change(RosterItem.RECV_NONE, null, null));
        // Owner asking to unsubscribe to item
        // Set flags
        subsTable.put(Presence.UNSUBSCRIBE, new Change(null, null, RosterItem.ASK_UNSUBSCRIBE));
        // Item has subscription from owner revoked
        // Immediately transition them to TO state
        subsTable.put(Presence.UNSUBSCRIBED, new Change(RosterItem.RECV_NONE, RosterItem.SUB_TO, null));
        // Item wishes to subscribe to owner
        // Item already has a subscription so only interesting if item had requested unsubscribe
        // Set flag and update roster if this is a new state, this is the normal way to begin
        // a mutual roster subscription negotiation.
        subrTable.put(Presence.SUBSCRIBE, new Change(RosterItem.RECV_NONE, null, null));
        // Item granted subscription to owner
        // Redundant unless denying unsub request
        subrTable.put(Presence.SUBSCRIBED, new Change(null, null, RosterItem.ASK_NONE));
        // Item wishes to unsubscribe from owner
        // This is the normal mechanism of removing subscription
        subrTable.put(Presence.UNSUBSCRIBE, new Change(RosterItem.RECV_UNSUBSCRIBE, null, null));
        // Owner has subscription to item revoked
        // Immediately downgrade state to FROM
        subrTable.put(Presence.UNSUBSCRIBED, new Change(RosterItem.RECV_NONE, RosterItem.SUB_FROM, RosterItem.ASK_NONE));
    }

    /**
     * <p>Indicate a state change.</p>
     * <p>Use nulls to indicate fields that should not be changed.</p>
     */
    private static class Change {
        public Change(RosterItem.RecvType recv, RosterItem.SubType sub, RosterItem.AskType ask) {
            newRecv = recv;
            newSub = sub;
            newAsk = ask;
        }

        public RosterItem.RecvType newRecv;
        public RosterItem.SubType newSub;
        public RosterItem.AskType newAsk;
    }

    /**
     * Determine and call the update method based on the item's subscription state.
     * The method also turns the action and sending status into an integer code
     * for easier processing (switch statements).
     * <p/>
     * Code relies on states being in numerical order without skipping.
     * In addition, the receive states must parallel the send states
     * so that (send state X) + STATE_RECV_SUBSCRIBE == (receive state X)
     * where X is subscribe, subscribed, etc.
     * </p>
     *
     * @param item      The item to be updated
     * @param action    The new state change request
     * @param isSending True if the roster owner of the item is sending the new state change request
     */
    private static void updateState(RosterItem item, XMPPPacket.Type action, boolean isSending) throws UnauthorizedException {
        Map srTable = (Map)stateTable.get(item.getSubStatus());
        Map changeTable = (Map)srTable.get(isSending ? "send" : "recv");
        Change change = (Change)changeTable.get(action);
        if (change.newAsk != null) {
            item.setAskStatus(change.newAsk);
        }
        if (change.newSub != null) {
            item.setSubStatus(change.newSub);
        }
        if (change.newRecv != null) {
            item.setRecvStatus(change.newRecv);
        }
    }

    public RoutingTable routingTable;
    public UserManager userManager;
    public XMPPServer localServer;
    public PacketDeliverer deliverer;
    public PacketFactory packetFactory;
    public ChatbotManager chatbotManager;

    protected TrackInfo getTrackInfo() {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.getTrackerClasses().put(UserManager.class, "userManager");
        trackInfo.getTrackerClasses().put(ChatbotManager.class, "chatbotManager");
        trackInfo.getTrackerClasses().put(RoutingTable.class, "routingTable");
        trackInfo.getTrackerClasses().put(XMPPServer.class, "localServer");
        trackInfo.getTrackerClasses().put(PacketDeliverer.class, "deliverer");
        trackInfo.getTrackerClasses().put(PacketFactory.class, "packetFactory");
        return trackInfo;
    }

    public void setRouter(PresenceRouter router) {
    }
}