/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.multiplex;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.ClientSession;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.net.SASLAuthentication;
import org.xmpp.packet.*;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * IQ packets sent from Connection Managers themselves to the server will be handled by
 * instances of this class. Each instance of
 * {@link org.jivesoftware.wildfire.net.ConnectionMultiplexerSocketReader} will have an instance
 * of this class so that IQ packets can be routed to this handler.<p>
 * <p/>
 * This class will interact with {@link ConnectionMultiplexerManager} to create, close or
 * get client sessions.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerPacketHandler {

    private String connectionManagerDomain;
    private PacketRouter router;
    private final ConnectionMultiplexerManager multiplexerManager;

    public MultiplexerPacketHandler(String connectionManagerDomain) {
        this.connectionManagerDomain = connectionManagerDomain;
        router = XMPPServer.getInstance().getPacketRouter();
        multiplexerManager = ConnectionMultiplexerManager.getInstance();
    }

    /**
     * Process IQ packet sent by a connection manager indicating that a new session has
     * been created, should be closed or that a packet was failed to be delivered.
     *
     * @param packet the IQ packet.
     */
    public void handle(Packet packet) {
        if (packet instanceof IQ) {
            IQ iq = (IQ) packet;
            if (iq.getType() == IQ.Type.result) {
                // Do nothing with result packets
            }
            else if (iq.getType() == IQ.Type.error) {
                // Log the IQ error packet that the connection manager failed to process
                Log.warn("Connection Manager failed to process IQ packet: " + packet.toXML());
            }
            else if (iq.getType() == IQ.Type.set) {
                Element child = iq.getChildElement();
                String streamID = child.attributeValue("id");
                if (streamID == null) {
                    // No stream ID was included so return a bad_request error
                    Element extraError = DocumentHelper.createElement(QName.get(
                            "id-required", "http://jabber.org/protocol/connectionmanager#errors"));
                    sendErrorPacket(iq, PacketError.Condition.bad_request, extraError);
                }
                else if ("session".equals(child.getName())) {
                    if (child.element("create") != null) {
                        // Connection Manager wants to create a Client Session
                        multiplexerManager.createClientSession(connectionManagerDomain, streamID);
                        sendResultPacket(iq);
                    }
                    else {
                        ClientSession session = multiplexerManager
                                .getClientSession(connectionManagerDomain, streamID);
                        if (session == null) {
                            // Specified Client Session does not exist
                            sendErrorPacket(iq, PacketError.Condition.item_not_found, null);
                        }
                        else if (child.element("close") != null) {
                            // Connection Manager wants to close a Client Session
                            multiplexerManager
                                    .closeClientSession(connectionManagerDomain, streamID);
                            sendResultPacket(iq);
                        }
                        else if (child.element("failed") != null) {
                            // Connection Manager failed to deliver a message
                            // Connection Manager wrapped a packet from a Client Session.
                            List wrappedElements = child.element("failed").elements();
                            if (wrappedElements.size() != 1) {
                                // Wrapper element is wrapping 0 or many items
                                Element extraError = DocumentHelper.createElement(QName.get(
                                        "invalid-payload",
                                        "http://jabber.org/protocol/connectionmanager#errors"));
                                sendErrorPacket(iq, PacketError.Condition.bad_request, extraError);
                            }
                            else {
                                Element wrappedElement = (Element) wrappedElements.get(0);
                                String tag = wrappedElement.getName();
                                if ("message".equals(tag)) {
                                    XMPPServer.getInstance().getOfflineMessageStrategy()
                                            .storeOffline(new Message(wrappedElement));
                                    sendResultPacket(iq);
                                }
                                else {
                                    Element extraError = DocumentHelper.createElement(QName.get(
                                            "unknown-stanza",
                                            "http://jabber.org/protocol/connectionmanager#errors"));
                                    sendErrorPacket(iq, PacketError.Condition.bad_request,
                                            extraError);
                                }
                            }
                        }
                        else {
                            // Unknown IQ packet received so return error to sender
                            sendErrorPacket(iq, PacketError.Condition.bad_request, null);
                        }
                    }
                }
                else {
                    // Unknown IQ packet received so return error to sender
                    sendErrorPacket(iq, PacketError.Condition.bad_request, null);
                }
            }
            else {
                // Unknown IQ packet received so return error to sender
                sendErrorPacket(iq, PacketError.Condition.bad_request, null);
            }
        }
    }

    /**
     * Processes a route packet that is wrapping a stanza sent by a client that is connected
     * to the connection manager.
     *
     * @param route the route packet.
     */
    public void route(Route route) {
        String streamID = route.getStreamID();
        if (streamID == null) {
            // No stream ID was included so return a bad_request error
            Element extraError = DocumentHelper.createElement(QName.get(
                    "id-required", "http://jabber.org/protocol/connectionmanager#errors"));
            sendErrorPacket(route, PacketError.Condition.bad_request, extraError);
        }
        ClientSession session = multiplexerManager
                .getClientSession(connectionManagerDomain, streamID);
        if (session == null) {
            // Specified Client Session does not exist
            sendErrorPacket(route, PacketError.Condition.item_not_found, null);
            return;
        }
        // Connection Manager wrapped a packet from a Client Session.
        Element wrappedElement = route.getChildElement();
        String tag = wrappedElement.getName();
        try {
            if ("auth".equals(tag) || "response".equals(tag)) {
                SASLAuthentication.handle(session, wrappedElement);
            }
            else if ("iq".equals(tag)) {
                processIQ(session, getIQ(wrappedElement));
            }
            else if ("message".equals(tag)) {
                processMessage(session, new Message(wrappedElement));
            }
            else if ("presence".equals(tag)) {
                processPresence(session, new Presence(wrappedElement));
            }
            else {
                Element extraError = DocumentHelper.createElement(QName.get(
                        "unknown-stanza",
                        "http://jabber.org/protocol/connectionmanager#errors"));
                sendErrorPacket(route, PacketError.Condition.bad_request, extraError);
            }
        }
        catch (UnsupportedEncodingException e) {
            Log.error("Error processing wrapped packet: " + wrappedElement.asXML(), e);
            sendErrorPacket(route, PacketError.Condition.internal_server_error, null);
        }
    }

    private void processIQ(ClientSession session, IQ packet) {
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
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

    private void processPresence(ClientSession session, Presence packet) {
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet so answer a not_allowed error
            Presence reply = new Presence();
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

    private void processMessage(ClientSession session, Message packet) {
        packet.setFrom(session.getAddress());
        try {
            // Invoke the interceptors before we process the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    false);
            router.route(packet);
            // Invoke the interceptors after we have processed the read packet
            InterceptorManager.getInstance().invokeInterceptors(packet, session, true,
                    true);
            session.incrementClientPacketCount();
        }
        catch (PacketRejectedException e) {
            // An interceptor rejected this packet
            if (e.getRejectionMessage() != null && e.getRejectionMessage().trim().length() > 0) {
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

    private IQ getIQ(Element doc) {
        Element query = doc.element("query");
        if (query != null && "jabber:iq:roster".equals(query.getNamespaceURI())) {
            return new Roster(doc);
        }
        else {
            return new IQ(doc);
        }
    }

    /**
     * Sends an IQ error with the specified condition to the sender of the original
     * IQ packet.
     *
     * @param packet     the packet to be bounced.
     * @param extraError application specific error or null if none.
     */
    private void sendErrorPacket(IQ packet, PacketError.Condition error, Element extraError) {
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        reply.setError(error);
        if (extraError != null) {
            // Add specific application error if available
            reply.getError().getElement().add(extraError);
        }
        deliver(reply);
    }

    /**
     * Sends an IQ error with the specified condition to the sender of the original
     * IQ packet.
     *
     * @param packet     the packet to be bounced.
     * @param extraError application specific error or null if none.
     */
    private void sendErrorPacket(Route packet, PacketError.Condition error, Element extraError) {
        Route reply = new Route(packet.getStreamID());
        reply.setID(packet.getID());
        reply.setFrom(packet.getTo());
        reply.setTo(packet.getFrom());
        reply.setError(error);
        if (extraError != null) {
            // Add specific application error if available
            reply.getError().getElement().add(extraError);
        }
        deliver(reply);
    }

    /**
     * Sends an IQ result packet confirming that the operation was successful.
     *
     * @param packet the original IQ packet.
     */
    private void sendResultPacket(IQ packet) {
        IQ reply = IQ.createResultIQ(packet);
        reply.setChildElement(packet.getChildElement().createCopy());
        deliver(reply);
    }

    private void deliver(Packet reply) {
        // Get any session of the connection manager to deliver the packet
        ConnectionMultiplexerSession session =
                multiplexerManager.getMultiplexerSession(connectionManagerDomain);
        if (session != null) {
            session.deliver(reply);
        }
        else {
            Log.warn("No multiplexer session found. Packet not delivered: " + reply.toXML());
        }
    }
}
