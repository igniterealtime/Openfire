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

import org.dom4j.Element;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.component.Component;

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
    private List<IQHandler> iqHandlers = new ArrayList<IQHandler>();
    private Map<String, IQHandler> namespace2Handlers = new ConcurrentHashMap<String, IQHandler>();
    private SessionManager sessionManager;
    private InternalComponentManager componentManager;

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
        if (session == null || session.getStatus() == Session.STATUS_AUTHENTICATED
                || (isLocalServer(packet.getTo())
                && ("jabber:iq:auth".equals(packet.getChildElement().getNamespaceURI())
                || "jabber:iq:register".equals(packet.getChildElement().getNamespaceURI())))
        ) {
            handle(packet);
        }
        else {
            packet.setTo(sessionManager.getSession(packet.getFrom()).getAddress());
            packet.setError(PacketError.Condition.not_authorized);
            sessionManager.getSession(packet.getFrom()).process(packet);
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

    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
        iqHandlers.addAll(server.getIQHandlers());
        sessionManager = server.getSessionManager();
        componentManager = InternalComponentManager.getInstance();
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
        try {
            // Check for registered components
            Component component = null;
            if (recipientJID != null) {
                component = componentManager.getComponent(packet.getTo().toBareJID());
            }
            if (component != null) {
                // A component was found that can handle the Packet
                component.processPacket(packet);
            }
            else if (isLocalServer(recipientJID)) {
                // Let the server handle the Packet
                Element childElement = packet.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if (namespace == null) {
                    // Do nothing. We can't handle queries outside of a valid namespace
                    Log.warn("Unknown packet " + packet);
                }
                else {
                    IQHandler handler = getHandler(namespace);
                    if (handler == null) {
                        IQ reply = IQ.createResultIQ(packet);
                        if (recipientJID == null) {
                            // Answer an error since the server can't handle the requested namespace
                            reply.setError(PacketError.Condition.service_unavailable);
                        }
                        else if (recipientJID.getNode() == null ||
                                "".equals(recipientJID.getNode())) {
                            // Answer an error if JID is of the form <domain>
                            reply.setError(PacketError.Condition.feature_not_implemented);
                        }
                        else {
                            // JID is of the form <node@domain>
                            try {
                                // Let a "service" handle this packet otherwise return an error
                                // Useful for MUC where node refers to a room and domain is the
                                // MUC service.
                                ChannelHandler route = routingTable.getRoute(recipientJID);
                                if (route instanceof BasicModule) {
                                    route.process(packet);
                                    return;
                                }
                            }
                            catch (NoSuchRouteException e) {
                                // do nothing
                            }
                            // Answer an error since the server can't handle packets sent to a node
                            reply.setError(PacketError.Condition.service_unavailable);
                        }
                        Session session = sessionManager.getSession(packet.getFrom());
                        if (session != null) {
                            session.getConnection().deliver(reply);
                        }
                        else {
                            Log.warn("Packet could not be delivered " + packet);
                        }
                    }
                    else {
                        handler.process(packet);
                    }
                }

            }
            else {
                // JID is of the form <node@domain/resource>
                ChannelHandler route = routingTable.getRoute(recipientJID);
                route.process(packet);
            }
        }
        catch (NoSuchRouteException e) {
            Log.info("Packet sent to unreachable address " + packet);
            Session session = sessionManager.getSession(packet.getFrom());
            if (session != null) {
                try {
                    packet.setError(PacketError.Condition.service_unavailable);
                    session.getConnection().deliver(packet);
                }
                catch (UnauthorizedException ex) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error.routing"), e);
                }
            }
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
