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

package org.jivesoftware.messenger.spi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.handler.IQHandler;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.dom4j.Element;

/**
 * Generic presence routing base class.
 *
 * @author Iain Shigeoka
 */
public class IQRouterImpl extends BasicModule implements IQRouter {

    private RoutingTable routingTable;
    private LinkedList iqHandlers = new LinkedList();
    private Map namespace2Handlers = new HashMap();
    private SessionManager sessionManager;

    /**
     * Creates a packet router.
     */
    public IQRouterImpl() {
        super("XMPP IQ Router");
    }

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
            try {
                sessionManager.getSession(packet.getFrom()).process(packet);
            }
            catch (UnauthorizedException ue) {
                Log.error(ue);
            }
        }
    }

    private boolean isLocalServer(JID recipientJID) {
        return recipientJID == null || recipientJID.getDomain() == null
                || "".equals(recipientJID.getDomain()) || recipientJID.getResource() == null
                || "".equals(recipientJID.getResource());
    }

    private void handle(IQ packet) {
        JID recipientJID = packet.getTo();
        try {
            if (isLocalServer(recipientJID)) {

                Element childElement = packet.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    childElement.getNamespaceURI();
                }
                if (namespace == null) {
                    // Do nothing. We can't handle queries outside of a valid namespace
                    Log.warn("Unknown packet " + packet);
                }
                else {
                    IQHandler handler = getHandler(namespace);
                    if (handler == null) {
                        // Answer an error if JID is of the form <domain>
                        if (recipientJID.getNode() == null || "".equals(recipientJID.getNode())) {
                            packet.setError(PacketError.Condition.feature_not_implemented);
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
                            packet.setError(PacketError.Condition.service_unavailable);
                        }
                        Session session = sessionManager.getSession(packet.getFrom());
                        if (session != null) {
                            session.getConnection().deliver(packet);
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
        IQHandler handler = null;

        handler = (IQHandler)namespace2Handlers.get(namespace);
        if (handler == null) {
            Iterator handlerIter = iqHandlers.iterator();
            while (handlerIter.hasNext() && handler == null) {
                IQHandler handlerCandidate = (IQHandler)handlerIter.next();
                IQHandlerInfo handlerInfo = handlerCandidate.getInfo();
                if (handlerInfo != null && namespace.equalsIgnoreCase(handlerInfo.getNamespace())) {
                    handler = handlerCandidate;
                }
            }
            if (handler != null) {
                namespace2Handlers.put(namespace, handler);
            }
        }
        return handler;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
        iqHandlers.addAll(server.getIQHandlers());
        sessionManager = server.getSessionManager();
    }
}
