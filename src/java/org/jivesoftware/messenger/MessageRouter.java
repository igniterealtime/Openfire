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

import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;

import java.util.StringTokenizer;

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

    private String serverName;

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

            // If the message was sent to the server hostname then forward the message to
            // a configurable set of JID's (probably admin users)
            if (recipientJID.getNode() == null && recipientJID.getResource() == null &&
                    serverName.equals(recipientJID.getDomain())) {
                sendMessageToAdmins(packet);
                return;
            }

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

    /**
     * Forwards the received message to the list of users defined in the property
     * <b>xmpp.forward.admins</b>. The property may include bare JIDs or just usernames separated
     * by commas or black spaces. When using bare JIDs the target user may belong to a remote
     * server.<p>
     *
     * If the property <b>xmpp.forward.admins</b> was not defined then the message will be sent
     * to all the users allowed to enter the admin console.
     *
     * @param packet the message to forward.
     */
    private void sendMessageToAdmins(Message packet) {
        String jids = JiveGlobals.getProperty("xmpp.forward.admins");
        if (jids != null && jids.trim().length() > 0) {
            // Forward the message to the users specified in the "xmpp.forward.admins" property
            StringTokenizer tokenizer = new StringTokenizer(jids, ", ");
            while (tokenizer.hasMoreTokens()) {
                String username = tokenizer.nextToken();
                Message forward = packet.createCopy();
                if (username.contains("@")) {
                    // Use the specified bare JID address as the target address
                    forward.setTo(username);
                }
                else {
                    forward.setTo(username + "@" + serverName);
                }
                route(forward);
            }
        }
        else {
            // Forward the message to the users allowed to log into the admin console
            jids = JiveGlobals.getXMLProperty("adminConsole.authorizedUsernames");
            jids = (jids == null || jids.trim().length() == 0) ? "admin" : jids;
            StringTokenizer tokenizer = new StringTokenizer(jids, ",");
            while (tokenizer.hasMoreTokens()) {
                String username = tokenizer.nextToken();
                Message forward = packet.createCopy();
                forward.setTo(username + "@" + serverName);
                route(forward);
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        routingTable = server.getRoutingTable();
        sessionManager = server.getSessionManager();
        serverName = server.getServerInfo().getName();
    }
}
