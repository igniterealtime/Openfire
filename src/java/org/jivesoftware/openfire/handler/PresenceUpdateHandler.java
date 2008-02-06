/**
 * $RCSfile: PresenceUpdateHandler.java,v $
 * $Revision: 3125 $
 * $Date: 2005-11-30 15:14:14 -0300 (Wed, 30 Nov 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.handler;

import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
 * <li>XMPPServer probes - Provides a mechanism for servers to query the presence
 * status of users on another server. This allows users to immediately
 * know the presence status of users when they come online rather than way
 * for a presence update broadcast from the other server or tracking them
 * as they are received.  Requires S2S capabilities.
 * </ul>
 *
 * @author Iain Shigeoka
 */
public class PresenceUpdateHandler extends BasicModule implements ChannelHandler, ClusterEventListener {

    public static final String PRESENCE_CACHE_NAME = "Directed Presences";

    /**
     * Keeps track of entities that sent directed presences to other entities. In this map
     * we keep track of every directed presence no matter if the recipient was hosted in
     * this JVM or another cluster node.
     *
     * Key: sender, Value: list of DirectedPresences
     */
    private Cache<String, Collection<DirectedPresence>> directedPresencesCache;
    /**
     * Same as the directedPresencesCache but only keeps directed presences sent from
     * users connected to this JVM.
     */
    private Map<String, Collection<DirectedPresence>> localDirectedPresences;

    private RoutingTable routingTable;
    private RosterManager rosterManager;
    private XMPPServer localServer;
    private PresenceManager presenceManager;
    private PacketDeliverer deliverer;
    private OfflineMessageStore messageStore;
    private SessionManager sessionManager;
    private UserManager userManager;

    public PresenceUpdateHandler() {
        super("Presence update handler");
        localDirectedPresences = new ConcurrentHashMap<String, Collection<DirectedPresence>>();
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        process((Presence) packet, sessionManager.getSession(packet.getFrom()));
    }

    private void process(Presence presence, ClientSession session) throws UnauthorizedException, PacketException {
        try {
            Presence.Type type = presence.getType();
            // Available
            if (type == null) {
                if (session != null && session.getStatus() == Session.STATUS_CLOSED) {
                    Log.warn("Rejected available presence: " + presence + " - " + session);
                    return;
                }
                broadcastUpdate(presence.createCopy());
                if (session != null) {
                    session.setPresence(presence);
                    if (!session.isInitialized()) {
                        initSession(session);
                        session.setInitialized(true);
                    }
                }
                // Notify the presence manager that the user is now available. The manager may
                // remove the last presence status sent by the user when he went offline.
                presenceManager.userAvailable(presence);
            }
            else if (Presence.Type.unavailable == type) {
                broadcastUpdate(presence.createCopy());
                broadcastUnavailableForDirectedPresences(presence);
                if (session != null) {
                    session.setPresence(presence);
                }
                // Notify the presence manager that the user is now unavailable. The manager may
                // save the last presence status sent by the user and keep track when the user
                // went offline.
                presenceManager.userUnavailable(presence);
            }
            else {
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null, true));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.bad_request);
                deliverer.deliver(presence);
            }

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error") + ". Triggered by packet: " + presence, e);
        }
    }

    /**
     * Handle presence updates that affect roster subscriptions.
     *
     * @param presence The presence presence to handle
     * @throws PacketException if the packet is null or the packet could not be routed.
     */
    public void process(Presence presence) throws PacketException {
        try {
            process((Packet)presence);
        }
        catch (UnauthorizedException e) {
            try {
                LocalSession session = (LocalSession) sessionManager.getSession(presence.getFrom());
                presence = presence.createCopy();
                if (session != null) {
                    presence.setFrom(new JID(null, session.getServerName(), null, true));
                    presence.setTo(session.getAddress());
                }
                else {
                    JID sender = presence.getFrom();
                    presence.setFrom(presence.getTo());
                    presence.setTo(sender);
                }
                presence.setError(PacketError.Condition.not_authorized);
                deliverer.deliver(presence);
            }
            catch (Exception err) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), err);
            }
        }
    }

    /**
     * A session that has transitioned to available status must be initialized.
     * This includes:
     * <ul>
     * <li>Sending all offline presence subscription requests</li>
     * <li>Sending offline messages</li>
     * </ul>
     *
     * @param session The session being updated
     * @throws UserNotFoundException If the user being updated does not exist
     */
    private void initSession(ClientSession session) throws UserNotFoundException {

        // Only user sessions need to be authenticated
        if (userManager.isRegisteredUser(session.getAddress().getNode())) {
            String username = session.getAddress().getNode();

            // Send pending subscription requests to user if roster service is enabled
            if (RosterManager.isRosterServiceEnabled()) {
                Roster roster = rosterManager.getRoster(username);
                for (RosterItem item : roster.getRosterItems()) {
                    if (item.getRecvStatus() == RosterItem.RECV_SUBSCRIBE) {
                        session.process(createSubscribePresence(item.getJid(),
                                new JID(session.getAddress().toBareJID()), true));
                    } else if (item.getRecvStatus() == RosterItem.RECV_UNSUBSCRIBE) {
                        session.process(createSubscribePresence(item.getJid(),
                                new JID(session.getAddress().toBareJID()), false));
                    }
                    if (item.getSubStatus() == RosterItem.SUB_TO
                            || item.getSubStatus() == RosterItem.SUB_BOTH) {
                        presenceManager.probePresence(session.getAddress(), item.getJid());
                    }
                }
            }
            if (session.canFloodOfflineMessages()) {
                // deliver offline messages if any
                Collection<OfflineMessage> messages = messageStore.getMessages(username, true);
                for (Message message : messages) {
                    session.process(message);
                }
            }
        }
    }

    public Presence createSubscribePresence(JID senderAddress, JID targetJID, boolean isSubscribe) {
        Presence presence = new Presence();
        presence.setFrom(senderAddress);
        presence.setTo(targetJID);
        if (isSubscribe) {
            presence.setType(Presence.Type.subscribe);
        }
        else {
            presence.setType(Presence.Type.unsubscribe);
        }
        return presence;
    }

    /**
     * Broadcast the given update to all subscribers. We need to:
     * <ul>
     * <li>Query the roster table for subscribers</li>
     * <li>Iterate through the list and send the update to each subscriber</li>
     * </ul>
     * <p/>
     * Is there a safe way to cache the query results while maintaining
     * integrity with roster changes?
     *
     * @param update The update to broadcast
     */
    private void broadcastUpdate(Presence update) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            // Do nothing if roster service is disabled
            if (!RosterManager.isRosterServiceEnabled()) {
                return;
            }
            // Local updates can simply run through the roster of the local user
            String name = update.getFrom().getNode();
            try {
                if (name != null && !"".equals(name)) {
                    Roster roster = rosterManager.getRoster(name);
                    roster.broadcastPresence(update);
                }
            }
            catch (UserNotFoundException e) {
                Log.warn("Presence being sent from unknown user " + name, e);
            }
            catch (PacketException e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
        else {
            // Foreign updates will do a reverse lookup of entries in rosters
            // on the server
            Log.warn("Presence requested from server "
                    + localServer.getServerInfo().getXMPPDomain()
                    + " by unknown user: " + update.getFrom());
        }
    }

    /**
     * Notification method sent to this handler when a user has sent a directed
     * presence to an entity. If the sender of the presence is local (to this server)
     * and the target entity does not belong to the user's roster then update the
     * registry of sent directed presences by the user.
     *
     * @param update  the directed Presence sent by the user to an entity.
     * @param handlerJID the JID of the handler that will receive/handle/process the sent packet.
     * @param jid     the receipient specified in the packet to handle.
     */
    public void directedPresenceSent(Presence update, JID handlerJID, String jid) {
        if (update.getFrom() == null) {
            return;
        }
        if (localServer.isLocal(update.getFrom())) {
            boolean keepTrack = false;
            String name = update.getFrom().getNode();
            if (name != null && !"".equals(name)) {
                // Keep track of all directed presences if roster service is disabled
                if (!RosterManager.isRosterServiceEnabled()) {
                    keepTrack = true;
                }
                else {
                    try {
                        Roster roster = rosterManager.getRoster(name);
                        // If the directed presence was sent to an entity that is not in the user's
                        // roster, keep a registry of this so that when the user goes offline we
                        // will be able to send the unavailable presence to the entity
                        RosterItem rosterItem = null;
                        try {
                            rosterItem = roster.getRosterItem(update.getTo());
                        }
                        catch (UserNotFoundException e) {
                            // Ignore
                        }
                        if (rosterItem == null ||
                                RosterItem.SUB_NONE == rosterItem.getSubStatus() ||
                                RosterItem.SUB_TO == rosterItem.getSubStatus()) {
                            keepTrack = true;
                        }
                    }
                    catch (UserNotFoundException e) {
                        Log.warn("Presence being sent from unknown user " + name, e);
                    }
                    catch (PacketException e) {
                        Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    }
                }
            }
            else if (update.getFrom().getResource() != null){
                // Keep always track of anonymous users directed presences
                keepTrack = true;
            }
            if (keepTrack) {
                String sender = update.getFrom().toString();
                Collection<DirectedPresence> directedPresences = directedPresencesCache.get(sender);
                if (Presence.Type.unavailable.equals(update.getType())) {
                    if (directedPresences != null) {
                        // It's a directed unavailable presence
                        if (routingTable.hasClientRoute(handlerJID)) {
                            // Client sessions will receive only presences to the same JID (the
                            // address of the session) so remove the handler from the map
                            for (DirectedPresence directedPresence : directedPresences) {
                                if (directedPresence.getHandler().equals(handlerJID)) {
                                    directedPresences.remove(directedPresence);
                                    break;
                                }
                            }
                        }
                        else {
                            // A service may receive presences for many JIDs so in this case we
                            // just need to remove the jid that has received a directed
                            // unavailable presence
                            for (DirectedPresence directedPresence : directedPresences) {
                                if (directedPresence.getHandler().equals(handlerJID)) {
                                    directedPresence.removeReceiver(jid);
                                    if (directedPresence.isEmpty()) {
                                        directedPresences.remove(directedPresence);
                                    }
                                    break;
                                }
                            }
                        }
                        if (directedPresences.isEmpty()) {
                            // Remove the user from the registry since the list of directed
                            // presences is empty
                            directedPresencesCache.remove(sender);
                            localDirectedPresences.remove(sender);
                        }
                        else {
                            directedPresencesCache.put(sender, directedPresences);
                            localDirectedPresences.put(sender, directedPresences);
                        }
                    }
                }
                else {
                    if (directedPresences == null) {
                        // We are using a set to avoid duplicate jids in case the user
                        // sends several directed presences to the same handler. The Map also
                        // ensures that if the user sends several presences to the same handler
                        // we will have only one entry in the Map
                        directedPresences = new ConcurrentLinkedQueue<DirectedPresence>();
                    }
                    // Add the handler to the list of handler that processed the directed
                    // presence sent by the user. This handler will be used to send
                    // the unavailable presence when the user goes offline
                    DirectedPresence affectedDirectedPresence = null;
                    for (DirectedPresence directedPresence : directedPresences) {
                        if (directedPresence.getHandler().equals(handlerJID)) {
                            affectedDirectedPresence = directedPresence;
                            break;
                        }
                    }

                    if (affectedDirectedPresence == null) {
                        affectedDirectedPresence = new DirectedPresence(handlerJID);
                        directedPresences.add(affectedDirectedPresence);
                    }
                    affectedDirectedPresence.addReceiver(jid);

                    directedPresencesCache.put(sender, directedPresences);
                    localDirectedPresences.put(sender, directedPresences);
                }
            }
        }
    }

    /**
     * Sends an unavailable presence to the entities that received a directed (available) presence
     * by the user that is now going offline.
     *
     * @param update the unavailable presence sent by the user.
     */
    private void broadcastUnavailableForDirectedPresences(Presence update) {
        JID from = update.getFrom();
        if (from == null) {
            return;
        }
        if (localServer.isLocal(from)) {
            // Remove the registry of directed presences of this user
            Collection<DirectedPresence> directedPresences = directedPresencesCache.remove(from.toString());
            if (directedPresences != null) {
                // Iterate over all the entities that the user sent a directed presence
                for (DirectedPresence directedPresence : directedPresences) {
                    for (String receiver : directedPresence.getReceivers()) {
                        Presence presence = update.createCopy();
                        presence.setTo(receiver);
                        localServer.getPresenceRouter().route(presence);
                    }
                }
                localDirectedPresences.remove(from.toString());
            }
        }
    }

    public boolean hasDirectPresence(Session session, JID recipientJID) {
        Collection<DirectedPresence> directedPresences = directedPresencesCache.get(session.getAddress().toString());
        if (directedPresences != null) {
            String recipient = recipientJID.toBareJID();
            for (DirectedPresence directedPresence : directedPresences) {
                for (String receiver : directedPresence.getReceivers()) {
                    if (receiver.contains(recipient)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Removes directed presences sent to entities that are no longer available.
     */
    public void removedExpiredPresences() {
        Map<String, Collection<DirectedPresence>> copy =
                new HashMap<String, Collection<DirectedPresence>>(localDirectedPresences);
        for (Map.Entry<String, Collection<DirectedPresence>> entry : copy.entrySet()) {
            for (DirectedPresence directedPresence : entry.getValue()) {
                if (!routingTable.hasClientRoute(directedPresence.getHandler()) &&
                        !routingTable.hasComponentRoute(directedPresence.getHandler())) {
                    Collection<DirectedPresence> presences = localDirectedPresences.get(entry.getKey());
                    presences.remove(directedPresence);
                    if (presences.isEmpty()) {
                        localDirectedPresences.remove(entry.getKey());
                    }
                }
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        localServer = server;
        rosterManager = server.getRosterManager();
        presenceManager = server.getPresenceManager();
        deliverer = server.getPacketDeliverer();
        messageStore = server.getOfflineMessageStore();
        sessionManager = server.getSessionManager();
        userManager = server.getUserManager();
        routingTable = server.getRoutingTable();
        directedPresencesCache = CacheFactory.createCache(PRESENCE_CACHE_NAME);
        // TODO Add as route listener (to remove direct presences info for removed routes). Mainly for c2s sessions which is uncommon.
        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    public void joinedCluster() {
        // Populate directedPresencesCache with local content since when not in a cluster
        // we could still send directed presences to entities that when connected to a cluster
        // they will be replicated. An example would be MUC rooms.
        for (Map.Entry<String, Collection<DirectedPresence>> entry : localDirectedPresences.entrySet()) {
            directedPresencesCache.put(entry.getKey(), entry.getValue());
        }
    }

    public void joinedCluster(byte[] nodeID) {
        // Do nothing
    }

    public void leftCluster() {
        if (!XMPPServer.getInstance().isShuttingDown()) {
            // Populate directedPresencesCache with local content
            for (Map.Entry<String, Collection<DirectedPresence>> entry : localDirectedPresences.entrySet()) {
                directedPresencesCache.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public void leftCluster(byte[] nodeID) {
        // Do nothing
    }

    public void markedAsSeniorClusterMember() {
        // Do nothing
    }
}
