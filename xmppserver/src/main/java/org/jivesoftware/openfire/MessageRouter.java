/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire;

import org.dom4j.QName;
import org.jivesoftware.openfire.carbons.Sent;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.forward.Forwarded;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.List;
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
    
    private static Logger log = LoggerFactory.getLogger(MessageRouter.class); 

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

                // If the server receives a message stanza with no 'to' attribute, it MUST treat the message as if the 'to' address were the bare JID <localpart@domainpart> of the sending entity.
                if (recipientJID == null) {
                    recipientJID = packet.getFrom().asBareJID();
                }

                // Check if the message was sent to the server hostname
                if (recipientJID.getNode() == null && recipientJID.getResource() == null &&
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

                boolean isAcceptable = true;
                if (session instanceof LocalClientSession) {
                    // Check if we could process messages from the recipient.
                    // If not, return a not-acceptable error as per XEP-0016:
                    // If the user attempts to send an outbound stanza to a contact and that stanza type is blocked, the user's server MUST NOT route the stanza to the contact but instead MUST return a <not-acceptable/> error
                    Message dummyMessage = packet.createCopy();
                    dummyMessage.setFrom(packet.getTo());
                    dummyMessage.setTo(packet.getFrom());
                    if (!((LocalClientSession) session).canProcess(dummyMessage)) {
                        packet.setTo(session.getAddress());
                        packet.setFrom((JID)null);
                        packet.setError(PacketError.Condition.not_acceptable);
                        session.process(packet);
                        isAcceptable = false;
                    }
                }
                if (isAcceptable) {
                    boolean isPrivate = packet.getElement().element(QName.get("private", "urn:xmpp:carbons:2")) != null;
                    try {
                        // Deliver stanza to requested route
                        routingTable.routePacket(recipientJID, packet, false);
                    } catch (Exception e) {
                        log.error("Failed to route packet: " + packet.toXML(), e);
                        routingFailed(recipientJID, packet);
                    }

                    // Sent carbon copies to other resources of the sender:
                    // When a client sends a <message/> of type "chat"
                    if (packet.getType() == Message.Type.chat && !isPrivate && session != null) { // && session.isMessageCarbonsEnabled() ??? // must the own session also be carbon enabled?
                        List<JID> routes = routingTable.getRoutes(packet.getFrom().asBareJID(), null);
                        for (JID route : routes) {
                            // The sending server SHOULD NOT send a forwarded copy to the sending full JID if it is a Carbons-enabled resource.
                            if (!route.equals(session.getAddress())) {
                                ClientSession clientSession = sessionManager.getSession(route);
                                if (clientSession != null && clientSession.isMessageCarbonsEnabled()) {
                                    Message message = new Message();
                                    // The wrapping message SHOULD maintain the same 'type' attribute value
                                    message.setType(packet.getType());
                                    // the 'from' attribute MUST be the Carbons-enabled user's bare JID
                                    message.setFrom(packet.getFrom().asBareJID());
                                    // and the 'to' attribute SHOULD be the full JID of the resource receiving the copy
                                    message.setTo(route);
                                    // The content of the wrapping message MUST contain a <sent/> element qualified by the namespace "urn:xmpp:carbons:2", which itself contains a <forwarded/> qualified by the namespace "urn:xmpp:forward:0" that contains the original <message/> stanza.
                                    message.addExtension(new Sent(new Forwarded(packet)));
                                    clientSession.process(message);
                                }
                            }
                        }
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

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        messageStrategy = server.getOfflineMessageStrategy();
        routingTable = server.getRoutingTable();
        sessionManager = server.getSessionManager();
        multicastRouter = server.getMulticastRouter();
        userManager = server.getUserManager();
        serverName = server.getServerInfo().getXMPPDomain();
    }

    /**
     * Notification message indicating that a packet has failed to be routed to the recipient.
     *
     * @param recipient address of the entity that failed to receive the packet.
     * @param packet    Message packet that failed to be sent to the recipient.
     */
    public void routingFailed( JID recipient, Packet packet )
    {
        log.debug( "Message sent to unreachable address: " + packet.toXML() );
        final Message msg = (Message) packet;

        if ( msg.getType().equals( Message.Type.chat ) && serverName.equals( recipient.getDomain() ) && recipient.getResource() != null ) {
            // Find an existing AVAILABLE session with non-negative priority.
            for (JID address : routingTable.getRoutes(recipient.asBareJID(), packet.getFrom())) {
                ClientSession session = routingTable.getClientRoute(address);
                if (session != null && session.isInitialized()) {
                    if (session.getPresence().getPriority() >= 0) {
                        // If message was sent to an unavailable full JID of a user then retry using the bare JID.
                        routingTable.routePacket( recipient.asBareJID(), packet, false );
                        return;
                    }
                }
            }
        }

        if ( serverName.equals( recipient.getDomain() ) )
        {
            // Delegate to offline message strategy, which will either bounce or ignore the message depending on user settings.
            log.trace( "Delegating to offline message strategy." );
            messageStrategy.storeOffline( (Message) packet );
        }
        else
        {
            // Recipient is not a local user. Bounce the message.
            // Note: this is similar, but not equal, to handling of message handling to local users in OfflineMessageStrategy.

            // 8.5.2.  localpart@domainpart
            // 8.5.2.2.  No Available or Connected Resources
            if (recipient.getResource() == null) {
                if (msg.getType() == Message.Type.headline || msg.getType() == Message.Type.error) {
                    // For a message stanza of type "headline" or "error", the server MUST silently ignore the message.
                    log.trace( "Not bouncing a message stanza to a bare JID of non-local user, of type {}", msg.getType() );
                    return;
                }
            } else {
                // 8.5.3.  localpart@domainpart/resourcepart
                // 8.5.3.2.1.  Message

                // For a message stanza of type "error", the server MUST silently ignore the stanza.
                if (msg.getType() == Message.Type.error) {
                    log.trace( "Not bouncing a message stanza to a full JID of non-local user, of type {}", msg.getType() );
                    return;
                }
            }

            bounce( msg );
        }
    }

    private void bounce(Message message) {
        // The bouncing behavior as implemented beyond this point was introduced as part
        // of OF-1852. This kill-switch allows it to be disabled again in case it
        // introduces unwanted side-effects.
        if ( !JiveGlobals.getBooleanProperty( "xmpp.message.bounce", true ) ) {
            log.trace( "Not bouncing a message stanza, as bouncing is disabled by configuration." );
            return;
        }

        // Do nothing if the packet included an error. This intends to prevent scenarios
        // where a stanza that is bounced itself gets bounced, causing a loop.
        if (message.getError() != null) {
            log.trace( "Not bouncing a stanza that included an error (to prevent never-ending loops of bounces-of-bounces)." );
            return;
        }

        // Do nothing if the sender was the server itself
        if (message.getFrom() == null || message.getFrom().toString().equals( serverName )) {
            log.trace( "Not bouncing a stanza that was sent by the server itself." );
            return;
        }
        try {
            log.trace( "Bouncing a message stanza." );

            // Generate a rejection response to the sender
            final Message errorResponse = message.createCopy();
            // return an error stanza to the sender, which SHOULD be <service-unavailable/>
            errorResponse.setError(PacketError.Condition.service_unavailable);
            errorResponse.setFrom(message.getTo());
            errorResponse.setTo(message.getFrom());
            // Send the response
            route(errorResponse);
        }
        catch (Exception e) {
            log.error("An exception occurred while trying to bounce a message.", e);
        }
    }
}
