/**
 * $RCSfile: MessageRouter.java,v $
 * $Revision: 3007 $
 * $Date: 2005-10-31 13:29:25 -0300 (Mon, 31 Oct 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
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
    private MulticastRouter multicastRouter;
    private UserManager userManager;

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
        ClientSession session = sessionManager.getSession(packet.getFrom());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, false);
            if (session == null || session.getStatus() == Session.STATUS_AUTHENTICATED) {
                JID recipientJID = packet.getTo();

                // Check if the message was sent to the server hostname
                if (recipientJID != null && recipientJID.getNode() == null && recipientJID.getResource() == null &&
                        serverName.equals(recipientJID.getDomain())) {
                    if (packet.getElement().element("addresses") != null) {
                        // Message includes multicast processing instructions. Ask the multicastRouter
                        // to route this packet
                        multicastRouter.route(packet);
                    }
                    else {
                        // Message was sent to the server hostname so forward it to a configurable
                        // set of JID's (probably admin users)
                        sendMessageToAdmins(packet);
                    }
                    return;
                }

                try {
                    // Deliver stanza to requested route
                    routingTable.routePacket(recipientJID, packet, false);
                }
                catch (Exception e) {
                    routingFailed(recipientJID, packet);
                }
            }
            else {
                packet.setTo(session.getAddress());
                packet.setFrom((JID)null);
                packet.setError(PacketError.Condition.not_authorized);
                session.process(packet);
            }
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, true);
        } catch (PacketRejectedException e) {
            // An interceptor rejected this packet
            if (session != null && e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
                // A message for the rejection will be sent to the sender of the rejected packet
                Message reply = new Message();
                reply.setID(packet.getID());
                reply.setTo(session.getAddress());
                reply.setFrom(packet.getTo());
                reply.setType(packet.getType());
                reply.setThread(packet.getThread());
                reply.setBody(e.getRejectionMessage());
                session.process(reply);
            }
        }
    }

    /**
     * Forwards the received message to the list of users defined in the property
     * <b>xmpp.forward.admins</b>. The property may include bare JIDs or just usernames separated
     * by commas or white spaces. When using bare JIDs the target user may belong to a remote
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
            for (JID jid : XMPPServer.getInstance().getAdmins()) {
                Message forward = packet.createCopy();
                forward.setTo(jid);
                route(forward);
            }
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        routingTable = server.getRoutingTable();
        sessionManager = server.getSessionManager();
        multicastRouter = server.getMulticastRouter();
        userManager = server.getUserManager();
        serverName = server.getServerInfo().getName();
    }

    /**
     * Notification message indicating that a packet has failed to be routed to the receipient.
     *
     * @param receipient address of the entity that failed to receive the packet.
     * @param packet Message packet that failed to be sent to the receipient.
     */
    public void routingFailed(JID receipient, Packet packet) {
        // If message was sent to an unavailable full JID of a user then retry using the bare JID
        if (serverName.equals(receipient.getDomain()) && receipient.getResource() != null &&
                userManager.isRegisteredUser(receipient.getNode())) {
            routingTable.routePacket(new JID(receipient.toBareJID()), packet, false);
        } else {
            // Just store the message offline
            messageStrategy.storeOffline((Message) packet);
        }
    }
}
