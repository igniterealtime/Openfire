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

package org.jivesoftware.messenger;

import org.xmpp.packet.Presence;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.jivesoftware.messenger.handler.PresenceUpdateHandler;
import org.jivesoftware.messenger.handler.PresenceSubscribeHandler;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;

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
    private SessionManager sessionManager;

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
        Session session = sessionManager.getSession(packet.getFrom());
        if (session == null || session.getStatus() == Session.STATUS_AUTHENTICATED) {
            handle(packet);
        }
        else {
            packet.setTo(session.getAddress());
            packet.setFrom((JID)null);
            packet.setError(PacketError.Condition.not_authorized);
            try {
                session.process(packet);
            }
            catch (UnauthorizedException ue) {
                Log.error(ue);
            }
        }
    }

    private void handle(Presence packet) {
        JID recipientJID = packet.getTo();
        try {
            Presence.Type type = packet.getType();
            // Presence updates (null is 'available')
            if (type == null || Presence.Type.unavailable == type) {
                // check for local server target
                if (recipientJID == null
                        || recipientJID.getDomain() == null
                        || "".equals(recipientJID.getDomain())
                        || (recipientJID.getNode() == null && recipientJID.getResource() == null)) {

                    updateHandler.process(packet);
                }
                else {
                    // The user sent a directed presence to an entity
                    ChannelHandler handler = routingTable.getRoute(recipientJID);
                    handler.process(packet);
                    // Notify the PresenceUpdateHandler of the directed presence
                    updateHandler.directedPresenceSent(packet, handler);
                }

            }
            else if (Presence.Type.subscribe == type // presence subscriptions
                    || Presence.Type.unsubscribe == type
                    || Presence.Type.subscribed == type
                    || Presence.Type.unsubscribed == type)
            {
                subscribeHandler.process(packet);
            }
            else {
                // It's an unknown or ERROR type, just deliver it because there's nothing else to do with it
                routingTable.getRoute(recipientJID).process(packet);
            }

        }
        catch (NoSuchRouteException e) {
            // Do nothing, presence to unreachable routes are dropped
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
            try {
                Session session = sessionManager.getSession(packet.getFrom());
                if (session != null) {
                    Connection conn = session.getConnection();
                    if (conn != null) {
                        conn.close();
                    }
                }
            }
            catch (UnauthorizedException e1) {
                // do nothing
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
        updateHandler = server.getPresenceUpdateHandler();
        subscribeHandler = server.getPresenceSubscribeHandler();
        sessionManager = server.getSessionManager();
    }
}
