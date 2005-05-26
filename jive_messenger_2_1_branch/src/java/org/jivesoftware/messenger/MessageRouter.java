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

import org.xmpp.packet.Message;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;

/**
 * <p>Route message packets throughout the server.</p>
 * <p>Routing is based on the recipient and sender addresses. The typical
 * packet will often be routed twice, once from the sender to some internal
 * server component for handling or processing, and then back to the router
 * to be delivered to it's final destination.</p>
 *
 * @author Iain Shigeoka
 */
public class MessageRouter extends BasicModule {

    private OfflineMessageStrategy messageStrategy;
    private RoutingTable routingTable;
    private SessionManager sessionManager;

    /**
     * Constructs a message router.
     */
    public MessageRouter() {
        super("XMPP Message Router");
    }

    /**
     * <p>Performs the actual packet routing.</p>
     * <p>You routing is considered 'quick' and implementations may not take
     * excessive amounts of time to complete the routing. If routing will take
     * a long amount of time, the actual routing should be done in another thread
     * so this method returns quickly.</p>
     * <h2>Warning</h2>
     * <p>Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.</p>
     *
     * @param packet The packet to route
     * @throws NullPointerException If the packet is null
     */
    public void route(Message packet) {
        if (packet == null) {
            throw new NullPointerException();
        }
        Session session = sessionManager.getSession(packet.getFrom());
        if (session == null
                || session.getStatus() == Session.STATUS_AUTHENTICATED)
        {
            JID recipientJID = packet.getTo();

            try {
                routingTable.getBestRoute(recipientJID).process(packet);
            }
            catch (Exception e) {
                try {
                    messageStrategy.storeOffline(packet);
                }
                catch (Exception e1) {
                    Log.error(e1);
                }
            }

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

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        routingTable = server.getRoutingTable();
        sessionManager = server.getSessionManager();
    }
}
