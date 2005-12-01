/**
 * $RCSfile: IQRouter.java,v $
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.dom4j.Element;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private RoutingTable routingTable;
    private String serverName;
    private List<IQHandler> iqHandlers = new ArrayList<IQHandler>();
    private Map<String, IQHandler> namespace2Handlers = new ConcurrentHashMap<String, IQHandler>();
    private Map<String, IQResultListener> resultListeners =
            new ConcurrentHashMap<String, IQResultListener>();
    private SessionManager sessionManager;

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
        Session session = sessionManager.getSession(packet.getFrom());
        if (session == null || session.getStatus() == Session.STATUS_AUTHENTICATED || (
                isLocalServer(packet.getTo()) && (
                        "jabber:iq:auth".equals(packet.getChildElement().getNamespaceURI()) ||
                                "jabber:iq:register"
                                        .equals(packet.getChildElement().getNamespaceURI()) ||
                                "urn:ietf:params:xml:ns:xmpp-bind"
                                        .equals(packet.getChildElement().getNamespaceURI())))) {
            handle(packet);
        }
        else {
            IQ reply = IQ.createResultIQ(packet);
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.not_authorized);
            sessionManager.getSession(packet.getFrom()).process(reply);
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
     * Adds an {@link IQResultListener} that will be invoked when an IQ result is sent to the
     * server itself and is of type result or error. This is a nice way for the server to
     * send IQ packets to other XMPP entities and be waked up when a response is received back.<p>
     *
     * Once an IQ result was received, the listener will be invoked and removed from
     * the list of listeners.
     *
     * @param id the id of the IQ packet being sent from the server to an XMPP entity.
     * @param listener the IQResultListener that will be invoked when an answer is received
     */
    void addIQResultListener(String id, IQResultListener listener) {
        resultListeners.put(id, listener);
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverName = server.getServerInfo().getName();
        routingTable = server.getRoutingTable();
        iqHandlers.addAll(server.getIQHandlers());
        sessionManager = server.getSessionManager();
    }

    /**
     * A JID is considered local if:
     * 1) is null or
     * 2) has no domain or domain is empty or
     * 3) has no resource or resource is empty
     */
    private boolean isLocalServer(JID recipientJID) {
        return recipientJID == null || recipientJID.getDomain() == null
                || "".equals(recipientJID.getDomain()) || recipientJID.getResource() == null
                || "".equals(recipientJID.getResource());
    }

    private void handle(IQ packet) {
        JID recipientJID = packet.getTo();
        // Check if the packet was sent to the server hostname
        if (recipientJID != null && recipientJID.getNode() == null &&
                recipientJID.getResource() == null && serverName.equals(recipientJID.getDomain())) {
            if (IQ.Type.result == packet.getType() || IQ.Type.error == packet.getType()) {
                // The server got an answer to an IQ packet that was sent from the server
                IQResultListener iqResultListener = resultListeners.remove(packet.getID());
                if (iqResultListener != null) {
                    iqResultListener.receivedAnswer(packet);
                    return;
                }
            }
        }
        try {
            // Check for registered components, services or remote servers
            if (recipientJID != null) {
                RoutableChannelHandler serviceRoute = routingTable.getRoute(recipientJID);
                if (serviceRoute != null && !(serviceRoute instanceof ClientSession)) {
                    // A component/service/remote server was found that can handle the Packet
                    serviceRoute.process(packet);
                    return;
                }
            }
            if (isLocalServer(recipientJID)) {
                // Let the server handle the Packet
                Element childElement = packet.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if (namespace == null) {
                    if (packet.getType() != IQ.Type.result) {
                        // Do nothing. We can't handle queries outside of a valid namespace
                        Log.warn("Unknown packet " + packet);
                    }
                }
                else {
                    IQHandler handler = getHandler(namespace);
                    if (handler == null) {
                        IQ reply = IQ.createResultIQ(packet);
                        if (recipientJID == null) {
                            // Answer an error since the server can't handle the requested namespace
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(PacketError.Condition.service_unavailable);
                        }
                        else if (recipientJID.getNode() == null ||
                                "".equals(recipientJID.getNode())) {
                            // Answer an error if JID is of the form <domain>
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(PacketError.Condition.feature_not_implemented);
                        }
                        else {
                            // JID is of the form <node@domain>
                            // Answer an error since the server can't handle packets sent to a node
                            reply.setChildElement(packet.getChildElement().createCopy());
                            reply.setError(PacketError.Condition.service_unavailable);
                        }

                        // Locate a route to the sender of the IQ and ask it to process
                        // the packet. Use the routingTable so that routes to remote servers
                        // may be found
                        ChannelHandler route = routingTable.getRoute(packet.getFrom());
                        if (route != null) {
                            route.process(reply);
                        }
                        else {
                            // No root was found so try looking for local sessions that have never
                            // sent an available presence or haven't authenticated yet
                            Session session = sessionManager.getSession(packet.getFrom());
                            if (session != null) {
                                session.process(reply);
                            }
                            else {
                                Log.warn("Packet could not be delivered " + packet);
                            }
                        }
                    }
                    else {
                        handler.process(packet);
                    }
                }

            }
            else {
                // JID is of the form <node@domain/resource>
                boolean handlerFound = false;
                // IQ packets should be sent to users even before they send an available presence.
                // So if the target address belongs to this server then use the sessionManager
                // instead of the routingTable since unavailable clients won't have a route to them
                if (XMPPServer.getInstance().isLocal(recipientJID)) {
                    Session session = sessionManager.getBestRoute(recipientJID);
                    if (session != null) {
                        session.process(packet);
                        handlerFound = true;
                    }
                    else {
                        Log.info("Packet sent to unreachable address " + packet);
                    }
                }
                else {
                    ChannelHandler route = routingTable.getRoute(recipientJID);
                    if (route != null) {
                        route.process(packet);
                        handlerFound = true;
                    }
                    else {
                        Log.info("Packet sent to unreachable address " + packet);
                    }
                }
                // If a route to the target address was not found then try to answer a
                // service_unavailable error code to the sender of the IQ packet
                if (!handlerFound && IQ.Type.result != packet.getType()) {
                    IQ reply = IQ.createResultIQ(packet);
                    reply.setChildElement(packet.getChildElement().createCopy());
                    reply.setError(PacketError.Condition.service_unavailable);
                    // Locate a route to the sender of the IQ and ask it to process
                    // the packet. Use the routingTable so that routes to remote servers
                    // may be found
                    ChannelHandler route = routingTable.getRoute(packet.getFrom());
                    if (route != null) {
                        route.process(reply);
                    }
                    else {
                        // No root was found so try looking for local sessions that have never
                        // sent an available presence or haven't authenticated yet
                        Session session = sessionManager.getSession(packet.getFrom());
                        if (session != null) {
                            session.process(reply);
                        }
                        else {
                            Log.warn("Packet could not be delivered " + reply);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
            Session session = sessionManager.getSession(packet.getFrom());
            if (session != null) {
                Connection conn = session.getConnection();
                if (conn != null) {
                    conn.close();
                }
            }
        }
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
}
