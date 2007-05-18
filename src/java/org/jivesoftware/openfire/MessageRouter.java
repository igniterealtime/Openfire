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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.*;

import java.util.*;

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
            if (session == null
                    || session.getStatus() == Session.STATUS_AUTHENTICATED)
            {
                JID recipientJID = packet.getTo();

                // Check if the message was sent to the server hostname
                if (recipientJID != null && recipientJID.getNode() == null &&
                        recipientJID.getResource() == null &&
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
                    // Check if message was sent to a bare JID of a local user
                    if (recipientJID != null && recipientJID.getResource() == null &&
                            serverName.equals(recipientJID.getDomain())) {
                        routeToBareJID(recipientJID, packet);
                    }
                    else {
                        // Deliver stanza to requested route
                        routingTable.routePacket(recipientJID, packet);
                    }
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
     * Deliver the message sent to the bare JID of a local user to the best connected resource. If the
     * target user is not online then messages will be stored offline according to the offline strategy.
     * However, if the user is connected from only one resource then the message will be delivered to
     * that resource. In the case that the user is connected from many resources the logic will be the
     * following:
     * <ol>
     *  <li>Select resources with highest priority</li>
     *  <li>Select resources with highest show value (chat, available, away, xa, dnd)</li>
     *  <li>Select resource with most recent activity</li>
     * </ol>
     *
     * Admins can override the above logic and just send the message to all connected resources
     * with highest priority by setting the system property <tt>route.all-resources</tt> to
     * <tt>true</tt>.
     *
     * @param recipientJID the bare JID of the target local user.
     * @param packet the message to send.
     */
    private void routeToBareJID(JID recipientJID, Message packet) {
        List<ClientSession> sessions = sessionManager.getHighestPrioritySessions(recipientJID.getNode());
        if (sessions.isEmpty()) {
            // No session is available so store offline
            messageStrategy.storeOffline(packet);
        }
        else if (sessions.size() == 1) {
            // Found only one session so deliver message
            sessions.get(0).process(packet);
        }
        else {
            // Many sessions have the highest priority (be smart now) :)
            if (!JiveGlobals.getBooleanProperty("route.all-resources", false)) {
                // Sort sessions by show value (e.g. away, xa)
                Collections.sort(sessions, new Comparator<ClientSession>() {

                    public int compare(ClientSession o1, ClientSession o2) {
                        int thisVal = getShowValue(o1);
                        int anotherVal = getShowValue(o2);
                        return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
                    }

                    /**
                     * Priorities are: chat, available, away, xa, dnd.
                     */
                    private int getShowValue(ClientSession session) {
                        Presence.Show show = session.getPresence().getShow();
                        if (show == Presence.Show.chat) {
                            return 1;
                        }
                        else if (show == null) {
                            return 2;
                        }
                        else if (show == Presence.Show.away) {
                            return 3;
                        }
                        else if (show == Presence.Show.xa) {
                            return 4;
                        }
                        else {
                            return 5;
                        }
                    }
                });

                // Get same sessions with same max show value
                List<ClientSession> targets = new ArrayList<ClientSession>();
                Presence.Show showFilter = sessions.get(0).getPresence().getShow();
                for (ClientSession session : sessions) {
                    if (session.getPresence().getShow() == showFilter) {
                        targets.add(session);
                    }
                    else {
                        break;
                    }
                }

                // Get session with most recent activity (and highest show value)
                Collections.sort(targets, new Comparator<ClientSession>() {
                    public int compare(ClientSession o1, ClientSession o2) {
                        return o1.getLastActiveDate().compareTo(o2.getLastActiveDate());
                    }
                });
                // Deliver stanza to session with highest priority, highest show value and most recent activity
                targets.get(0).process(packet);
            }
            else {
                // Deliver stanza to all connected resources with highest priority
                for (ClientSession session : sessions) {
                    session.process(packet);
                }
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
        serverName = server.getServerInfo().getName();
    }

    /**
     * Notification message indicating that a packet has failed to be routed to the receipient.
     *
     * @param packet Message packet that failed to be sent to the receipient.
     */
    public void routingFailed(Packet packet) {
        messageStrategy.storeOffline((Message) packet);
    }
}
