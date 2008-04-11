/**
 * $RCSfile: PresenceRouter.java,v $
 * $Revision: 3138 $
 * $Date: 2005-12-01 02:13:26 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.openfire.handler.PresenceSubscribeHandler;
import org.jivesoftware.openfire.handler.PresenceUpdateHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.RemotePresenceEventDispatcher;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.*;

/**
 * <p>Route presence packets throughout the server.</p>
 * <p>Routing is based on the recipient and sender addresses. The typical
 * packet will often be routed twice, once from the sender to some internal
 * server component for handling or processing, and then back to the router
 * to be delivered to it's final destination.</p>
 *
 * @author Iain Shigeoka
 */
public class PresenceRouter extends BasicModule {

    private RoutingTable routingTable;
    private PresenceUpdateHandler updateHandler;
    private PresenceSubscribeHandler subscribeHandler;
    private PresenceManager presenceManager;
    private SessionManager sessionManager;
    private EntityCapabilitiesManager entityCapsManager;
    private MulticastRouter multicastRouter;
    private String serverName;

    /**
     * Constructs a presence router.
     */
    public PresenceRouter() {
        super("XMPP Presence Router");
    }

    /**
     * Routes presence packets.
     *
     * @param packet the packet to route.
     * @throws NullPointerException if the packet is null.
     */
    public void route(Presence packet) {
        if (packet == null) {
            throw new NullPointerException();
        }
        ClientSession session = sessionManager.getSession(packet.getFrom());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, false);
            if (session == null || session.getStatus() != Session.STATUS_CONNECTED) {
                handle(packet);
            }
            else {
                packet.setTo(session.getAddress());
                packet.setFrom((JID)null);
                packet.setError(PacketError.Condition.not_authorized);
                session.process(packet);
            }
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, true);
        }
        catch (PacketRejectedException e) {
            if (session != null) {
                // An interceptor rejected this packet so answer a not_allowed error
                Presence reply = new Presence();
                reply.setID(packet.getID());
                reply.setTo(session.getAddress());
                reply.setFrom(packet.getTo());
                reply.setError(PacketError.Condition.not_allowed);
                session.process(reply);
                // Check if a message notifying the rejection should be sent
                if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                    // A message for the rejection will be sent to the sender of the rejected packet
                    Message notification = new Message();
                    notification.setTo(session.getAddress());
                    notification.setFrom(packet.getTo());
                    notification.setBody(e.getRejectionMessage());
                    session.process(notification);
                }
            }
        }
    }

    private void handle(Presence packet) {
        JID recipientJID = packet.getTo();
        JID senderJID = packet.getFrom();
        // Check if the packet was sent to the server hostname
        if (recipientJID != null && recipientJID.getNode() == null &&
                recipientJID.getResource() == null && serverName.equals(recipientJID.getDomain())) {
            if (packet.getElement().element("addresses") != null) {
                // Presence includes multicast processing instructions. Ask the multicastRouter
                // to route this packet
                multicastRouter.route(packet);
                return;
            }
        }
        try {
            // Presences sent between components are just routed to the component
            if (recipientJID != null && !XMPPServer.getInstance().isLocal(recipientJID) &&
                    !XMPPServer.getInstance().isLocal(senderJID)) {
                // Route the packet
                routingTable.routePacket(recipientJID, packet, false);
                return;
            }

            Presence.Type type = packet.getType();
            // Presence updates (null is 'available')
            if (type == null || Presence.Type.unavailable == type) {
                // check for local server target
                if (recipientJID == null || recipientJID.getDomain() == null ||
                        "".equals(recipientJID.getDomain()) || (recipientJID.getNode() == null &&
                        recipientJID.getResource() == null) &&
                        serverName.equals(recipientJID.getDomain())) {
                    entityCapsManager.process(packet);
                    updateHandler.process(packet);
                }
                else {
                    // Trigger events for presences of remote users
                    if (senderJID != null && !serverName.equals(senderJID.getDomain()) &&
                            !routingTable.hasComponentRoute(senderJID)) {
                        entityCapsManager.process(packet);
                        if (type == null) {
                            // Remote user has become available
                            RemotePresenceEventDispatcher.remoteUserAvailable(packet);
                        }
                        else if (type == Presence.Type.unavailable) {
                            // Remote user is now unavailable
                            RemotePresenceEventDispatcher.remoteUserUnavailable(packet);
                        }
                    }
                    
                    // Check that sender session is still active (let unavailable presence go through)
                    Session session = sessionManager.getSession(packet.getFrom());
                    if (session != null && session.getStatus() == Session.STATUS_CLOSED && type == null) {
                        Log.warn("Rejected available presence: " + packet + " - " + session);
                        return;
                    }

                    // The user sent a directed presence to an entity
                    // Broadcast it to all connected resources
                    for (JID jid : routingTable.getRoutes(recipientJID, senderJID)) {
                        // Register the sent directed presence
                        updateHandler.directedPresenceSent(packet, jid, recipientJID.toString());
                        // Route the packet
                        routingTable.routePacket(jid, packet, false);
                    }
                }

            }
            else if (Presence.Type.subscribe == type // presence subscriptions
                    || Presence.Type.unsubscribe == type
                    || Presence.Type.subscribed == type
                    || Presence.Type.unsubscribed == type)
            {
                subscribeHandler.process(packet);
            }
            else if (Presence.Type.probe == type) {
                // Handle a presence probe sent by a remote server
                if (!XMPPServer.getInstance().isLocal(recipientJID)) {
                    routingTable.routePacket(recipientJID, packet, false);
                }
                else {
                    // Handle probe to a local user
                    presenceManager.handleProbe(packet);
                }
            }
            else {
                // It's an unknown or ERROR type, just deliver it because there's nothing
                // else to do with it
                routingTable.routePacket(recipientJID, packet, false);
            }

        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
            Session session = sessionManager.getSession(packet.getFrom());
            if (session != null) {
                session.close();
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getXMPPDomain();
        routingTable = server.getRoutingTable();
        updateHandler = server.getPresenceUpdateHandler();
        subscribeHandler = server.getPresenceSubscribeHandler();
        presenceManager = server.getPresenceManager();
        multicastRouter = server.getMulticastRouter();
        sessionManager = server.getSessionManager();
        entityCapsManager = EntityCapabilitiesManager.getInstance();
    }

    /**
     * Notification message indicating that a packet has failed to be routed to the receipient.
     *
     * @param receipient address of the entity that failed to receive the packet.
     * @param packet Presence packet that failed to be sent to the receipient.
     */
    public void routingFailed(JID receipient, Packet packet) {
        // presence packets are dropped silently
    }
}
