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

import org.dom4j.Element;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes iq packets throughout the server. Routing is based on the recipient
 * and sender addresses. The typical packet will often be routed twice, once
 * from the sender to some internal server component for handling or processing,
 * and then back to the router to be delivered to it's final destination.
 *
 * @author Iain Shigeoka
 */
public class IQRouter extends BasicModule {

    private static final Logger Log = LoggerFactory.getLogger(IQRouter.class);

    private RoutingTable routingTable;
    private MulticastRouter multicastRouter;
    private String serverName;
    private List<IQHandler> iqHandlers = new ArrayList<>();
    private Map<String, IQHandler> namespace2Handlers = new ConcurrentHashMap<>();
    private Map<String, IQResultListener> resultListeners = new ConcurrentHashMap<>();
    private Map<String, Long> resultTimeout = new ConcurrentHashMap<>();
    private SessionManager sessionManager;
    private UserManager userManager;

    /**
     * Creates a packet router.
     */
    public IQRouter() {
        super("XMPP IQ Router");
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
    public void route(IQ packet) {
        if (packet == null) {
            throw new NullPointerException();
        }
        JID sender = packet.getFrom();
        ClientSession session = sessionManager.getSession(sender);
        Element childElement = packet.getChildElement(); // may be null
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, false);
            JID to = packet.getTo();
            if (session != null && to != null && session.getStatus() == Session.STATUS_CONNECTED &&
                    !serverName.equals(to.toString())) {
                // User is requesting this server to authenticate for another server. Return
                // a bad-request error
                IQ reply = IQ.createResultIQ(packet);
                if (childElement != null) {
                    reply.setChildElement(childElement.createCopy());
                }
                reply.setError(PacketError.Condition.bad_request);
                session.process(reply);
                Log.warn("User tried to authenticate with this server using an unknown recipient: " +
                        packet.toXML());
            }
            else if (session == null || session.getStatus() == Session.STATUS_AUTHENTICATED || (
                    childElement != null && isLocalServer(to) && (
                        "jabber:iq:auth".equals(childElement.getNamespaceURI()) ||
                        "jabber:iq:register".equals(childElement.getNamespaceURI()) ||
                        "urn:ietf:params:xml:ns:xmpp-bind".equals(childElement.getNamespaceURI())))) {
                handle(packet);
            } else if (packet.getType() == IQ.Type.get || packet.getType() == IQ.Type.set) {
                IQ reply = IQ.createResultIQ(packet);
                if (childElement != null) {
                    reply.setChildElement(childElement.createCopy());
                }
                reply.setError(PacketError.Condition.not_authorized);
                session.process(reply);
            }
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true, true);
        }
        catch (PacketRejectedException e) {
            if (session != null) {
                // An interceptor rejected this packet so answer a not_allowed error
                IQ reply = new IQ();
                if (childElement != null) {
                    reply.setChildElement(childElement.createCopy());
                }
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

    /**
     * <p>Adds a new IQHandler to the list of registered handler. The new IQHandler will be
     * responsible for handling IQ packet whose namespace matches the namespace of the
     * IQHandler.</p>
     *
     * An IllegalArgumentException may be thrown if the IQHandler to register was already provided
     * by the server. The server provides a certain list of IQHandlers when the server is
     * started up.
     *
     * @param handler the IQHandler to add to the list of registered handler.
     */
    public void addHandler(IQHandler handler) {
        if (iqHandlers.contains(handler)) {
            throw new IllegalArgumentException("IQHandler already provided by the server");
        }
        // Ask the handler to be initialized
        handler.initialize(XMPPServer.getInstance());
        // Register the handler as the handler of the namespace
        namespace2Handlers.put(handler.getInfo().getNamespace(), handler);
    }

    /**
     * <p>Removes an IQHandler from the list of registered handler. The IQHandler to remove was
     * responsible for handling IQ packet whose namespace matches the namespace of the
     * IQHandler.</p>
     *
     * An IllegalArgumentException may be thrown if the IQHandler to remove was already provided
     * by the server. The server provides a certain list of IQHandlers when the server is
     * started up.
     *
     * @param handler the IQHandler to remove from the list of registered handler.
     */
    public void removeHandler(IQHandler handler) {
        if (iqHandlers.contains(handler)) {
            throw new IllegalArgumentException("Cannot remove an IQHandler provided by the server");
        }
        // Unregister the handler as the handler of the namespace
        namespace2Handlers.remove(handler.getInfo().getNamespace());
    }

    /**
     * Adds an {@link IQResultListener} that will be invoked when an IQ result
     * is sent to the server itself and is of type result or error. This is a
     * nice way for the server to send IQ packets to other XMPP entities and be
     * waked up when a response is received back.<p>
     *
     * Once an IQ result was received, the listener will be invoked and removed
     * from the list of listeners.<p>
     *
     * If no result was received within one minute, the timeout method of the
     * listener will be invoked and the listener will be removed from the list
     * of listeners.
     *
     * @param id
     *            the id of the IQ packet being sent from the server to an XMPP
     *            entity.
     * @param listener
     *            the IQResultListener that will be invoked when an answer is
     *            received
     */
    public void addIQResultListener(String id, IQResultListener listener) {
        addIQResultListener(id, listener, 60 * 1000);
    }

    /**
     * Adds an {@link IQResultListener} that will be invoked when an IQ result
     * is sent to the server itself and is of type result or error. This is a
     * nice way for the server to send IQ packets to other XMPP entities and be
     * waked up when a response is received back.<p>
     *
     * Once an IQ result was received, the listener will be invoked and removed
     * from the list of listeners.<p>
     *
     * If no result was received within the specified amount of milliseconds,
     * the timeout method of the listener will be invoked and the listener will
     * be removed from the list of listeners.<p>
     *
     * Note that the listener will remain active for <em>at least</em> the
     * specified timeout value. The listener will not be removed at the exact
     * moment it times out. Instead, purging of timed out listeners is a
     * periodic scheduled job.
     *
     * @param id
     *            the id of the IQ packet being sent from the server to an XMPP
     *            entity.
     * @param listener
     *            the IQResultListener that will be invoked when an answer is
     *            received.
     * @param timeoutmillis
     *            The amount of milliseconds after which waiting for a response
     *            should be stopped.
     */
    public void addIQResultListener(String id, IQResultListener listener, long timeoutmillis) {
        resultListeners.put(id, listener);
        resultTimeout.put(id, System.currentTimeMillis() + timeoutmillis);
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        TaskEngine.getInstance().scheduleAtFixedRate(new TimeoutTask(), 5000, 5000);
        serverName = server.getServerInfo().getXMPPDomain();
        routingTable = server.getRoutingTable();
        multicastRouter = server.getMulticastRouter();
        iqHandlers.addAll(server.getIQHandlers());
        sessionManager = server.getSessionManager();
        userManager = server.getUserManager();
    }

    /**
     * A JID is considered local if:
     * 1) is null or
     * 2) has no domain or domain is empty
     * or
     * if it's not a full JID and it was sent to the server itself.
     *
     * @param recipientJID address to check.
     * @return true if the specified address belongs to the local server.
     */
    private boolean isLocalServer(JID recipientJID) {
        // Check if no address was specified in the IQ packet
        boolean implicitServer =
                recipientJID == null || recipientJID.getDomain() == null || "".equals(recipientJID.getDomain());
        if (!implicitServer) {
            // We found an address. Now check if it's a bare or full JID
            if (recipientJID.getNode() == null || recipientJID.getResource() == null) {
                // Address is a bare JID so check if it was sent to the server itself
                return serverName.equals(recipientJID.getDomain());
            }
            // Address is a full JID. IQ packets sent to full JIDs are not handle by the server
            return false;
        }
        return true;
    }

    private void handle(IQ packet) {
        JID recipientJID = packet.getTo();
        // Check if the packet was sent to the server hostname
        if (recipientJID != null && recipientJID.getNode() == null &&
                recipientJID.getResource() == null && serverName.equals(recipientJID.getDomain())) {
            Element childElement = packet.getChildElement();
            if (childElement != null && childElement.element("addresses") != null) {
                // Packet includes multicast processing instructions. Ask the multicastRouter
                // to route this packet
                multicastRouter.route(packet);
                return;
            }
        }
        if (packet.getID() != null && (IQ.Type.result == packet.getType() || IQ.Type.error == packet.getType())) {
            // The server got an answer to an IQ packet that was sent from the server
            IQResultListener iqResultListener = resultListeners.remove(packet.getID());
            if (iqResultListener != null) {
                resultTimeout.remove(packet.getID());
                if (iqResultListener != null) {
                    try {
                        iqResultListener.receivedAnswer(packet);
                    }
                    catch (Exception e) {
                        Log.error(
                                "Error processing answer of remote entity. Answer: "
                                        + packet.toXML(), e);
                    }
                    return;
                }
            }
        }
        try {
            // Check for registered components, services or remote servers
            if (recipientJID != null &&
                    (routingTable.hasComponentRoute(recipientJID) || routingTable.hasServerRoute(new DomainPair(packet.getFrom().getDomain(), recipientJID.getDomain())))) {
                // A component/service/remote server was found that can handle the Packet
                routingTable.routePacket(recipientJID, packet, false);
                return;
            }

           if (isLocalServer(recipientJID)) {
                // Let the server handle the Packet
                Element childElement = packet.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if (namespace == null) {
                    if (packet.getType() != IQ.Type.result && packet.getType() != IQ.Type.error) {
                        // Do nothing. We can't handle queries outside of a valid namespace
                        Log.warn("Unknown packet " + packet.toXML());
                    }
                }
                else {
                    // Check if communication to local users is allowed
                    if (recipientJID != null && userManager.isRegisteredUser(recipientJID.getNode())) {
                        PrivacyList list =
                                PrivacyListManager.getInstance().getDefaultPrivacyList(recipientJID.getNode());
                        if (list != null && list.shouldBlockPacket(packet)) {
                            // Communication is blocked
                            if (IQ.Type.set == packet.getType() || IQ.Type.get == packet.getType()) {
                                // Answer that the service is unavailable
                                sendErrorPacket(packet, PacketError.Condition.service_unavailable);
                            }
                            return;
                        }
                    }
                    IQHandler handler = getHandler(namespace);
                    if (handler == null) {
                        if (recipientJID == null) {
                            // Answer an error since the server can't handle the requested namespace
                            sendErrorPacket(packet, PacketError.Condition.service_unavailable);
                        }
                        else if (recipientJID.getNode() == null ||
                                "".equals(recipientJID.getNode())) {
                            // Answer an error if JID is of the form <domain>
                            sendErrorPacket(packet, PacketError.Condition.feature_not_implemented);
                        }
                        else {
                            // JID is of the form <node@domain>
                            // Answer an error since the server can't handle packets sent to a node
                            sendErrorPacket(packet, PacketError.Condition.service_unavailable);
                        }
                    }
                    else {
                        handler.process(packet);
                    }
                }
            }
            else {

                // RFC 6121 8.5.1.  No Such User http://xmpp.org/rfcs/rfc6121.html#rules-localpart-nosuchuser
                // If the 'to' address specifies a bare JID <localpart@domainpart> or full JID <localpart@domainpart/resourcepart> where the domainpart of the JID matches a configured domain that is serviced by the server itself, the server MUST proceed as follows.
                // If the user account identified by the 'to' attribute does not exist, how the stanza is processed depends on the stanza type.
                if (recipientJID != null && recipientJID.getNode() != null && serverName.equals(recipientJID.getDomain()) && !userManager.isRegisteredUser(recipientJID.getNode()) && sessionManager.getSession(recipientJID) == null && (IQ.Type.set == packet.getType() || IQ.Type.get == packet.getType())) {
                    // For an IQ stanza, the server MUST return a <service-unavailable/> stanza error to the sender.
                    sendErrorPacket(packet, PacketError.Condition.service_unavailable);
                    return;
                }

                ClientSession session = sessionManager.getSession(packet.getFrom());
                boolean isAcceptable = true;
                if (session instanceof LocalClientSession) {
                    // Check if we could process IQ stanzas from the recipient.
                    // If not, return a not-acceptable error as per XEP-0016:
                    // If the user attempts to send an outbound stanza to a contact and that stanza type is blocked, the user's server MUST NOT route the stanza to the contact but instead MUST return a <not-acceptable/> error
                    IQ dummyIQ = packet.createCopy();
                    dummyIQ.setFrom(packet.getTo());
                    dummyIQ.setTo(packet.getFrom());
                    if (!((LocalClientSession) session).canProcess(dummyIQ)) {
                        packet.setTo(session.getAddress());
                        packet.setFrom((JID) null);
                        packet.setError(PacketError.Condition.not_acceptable);
                        session.process(packet);
                        isAcceptable = false;
                    }
                }

                if (isAcceptable) {
                    // JID is of the form <node@domain/resource> or belongs to a remote server
                    // or to an uninstalled component
                    routingTable.routePacket(recipientJID, packet, false);
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
            Session session = sessionManager.getSession(packet.getFrom());
            if (session != null) {
                IQ reply = IQ.createResultIQ(packet);
                reply.setError(PacketError.Condition.internal_server_error);
                session.process(reply);
            }
        }
    }

    private void sendErrorPacket(IQ originalPacket, PacketError.Condition condition) {
        if (IQ.Type.error == originalPacket.getType()) {
            Log.error("Cannot reply an IQ error to another IQ error: " + originalPacket.toXML());
            return;
        }
        if (originalPacket.getFrom() == null) {
            if (Log.isDebugEnabled()) {
                Log.debug("Original IQ has no sender for reply; dropped: " + originalPacket.toXML());
            }
            return;
        }
        IQ reply = IQ.createResultIQ(originalPacket);
        reply.setChildElement(originalPacket.getChildElement().createCopy());
        reply.setError(condition);
        // Check if the server was the sender of the IQ
        if (serverName.equals(originalPacket.getFrom().toString())) {
            // Just let the IQ router process the IQ error reply
            handle(reply);
            return;
        }
        // Route the error packet to the original sender of the IQ.
        routingTable.routePacket(reply.getTo(), reply, true);
    }

    /**
     * Determines if this instance has support (formally: has a IQ Handler) for the provided namespace.
     *
     * @param namespace Identifier of functionality (cannot be null)
     * @return true if the functionality identified by the namespace is supported, otherwise false.
     */
    public boolean supports( String namespace ) {
        return getHandler( namespace ) != null;
    }

    private IQHandler getHandler(String namespace) {
        IQHandler handler = namespace2Handlers.get(namespace);
        if (handler == null) {
            for (IQHandler handlerCandidate : iqHandlers) {
                IQHandlerInfo handlerInfo = handlerCandidate.getInfo();
                if (handlerInfo != null && namespace.equalsIgnoreCase(handlerInfo.getNamespace())) {
                    handler = handlerCandidate;
                    namespace2Handlers.put(namespace, handler);
                    break;
                }
            }
        }
        return handler;
    }

    /**
     * Notification message indicating that a packet has failed to be routed to the recipient.
     *
     * @param recipient address of the entity that failed to receive the packet.
     * @param packet    IQ packet that failed to be sent to the recipient.
     */
    public void routingFailed( JID recipient, Packet packet )
    {
        Log.debug( "IQ sent to unreachable address: " + packet.toXML() );
        final IQ iq = (IQ) packet;
        // If a route to the target address was not found then try to answer a service_unavailable error code to the sender of the IQ packet
        if ( iq.isRequest() )
        {
            sendErrorPacket( iq, PacketError.Condition.service_unavailable );
        }
    }

    /**
     * Timer task that will remove Listeners that wait for results to IQ stanzas
     * that have timed out. Time out values can be set to each listener
     * individually by adjusting the timeout value in the third parameter of
     * {@link IQRouter#addIQResultListener(String, IQResultListener, long)}.
     *
     * @author Guus der Kinderen, guus@nimbuzz.com
     */
    private class TimeoutTask extends TimerTask {

        /**
         * Iterates over and removes all timed out results.<p>
         *
         * The map that keeps track of timeout values is ordered by timeout
         * date. This way, iteration can be stopped as soon as the first value
         * has been found that didn't timeout yet.
         */
        @Override
        public void run() {
            // Use an Iterator to allow changes to the Map that is backing
            // the Iterator.
            final Iterator<Map.Entry<String, Long>> it = resultTimeout.entrySet().iterator();

            while (it.hasNext()) {
                final Map.Entry<String, Long> pointer = it.next();

                if (System.currentTimeMillis() < pointer.getValue()) {
                    // This entry has not expired yet. Ignore it.
                    continue;
                }

                final String packetId = pointer.getKey();

                // remove this listener from the list
                final IQResultListener listener = resultListeners.remove(packetId);
                if (listener != null) {
                    // notify listener of the timeout.
                    listener.answerTimeout(packetId);
                }

                // remove the packet from the list that's used to track
                // timeouts
                it.remove();
            }
        }
    }
}
