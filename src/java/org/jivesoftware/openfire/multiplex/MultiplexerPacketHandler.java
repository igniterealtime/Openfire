/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.multiplex;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.util.List;

/**
 * IQ packets sent from Connection Managers themselves to the server will be handled by
 * instances of this class.<p>
 * <p/>
 * This class will interact with {@link ConnectionMultiplexerManager} to create, close or
 * get client sessions.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerPacketHandler {

    private String connectionManagerDomain;
    private final ConnectionMultiplexerManager multiplexerManager;

    public MultiplexerPacketHandler(String connectionManagerDomain) {
        this.connectionManagerDomain = connectionManagerDomain;
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
        LocalClientSession session = multiplexerManager.getClientSession(connectionManagerDomain, streamID);
        if (session == null) {
            // Specified Client Session does not exist
            sendErrorPacket(route, PacketError.Condition.item_not_found, null);
            return;
        }

        SessionPacketRouter router = new SessionPacketRouter(session);
        // Connection Manager already validate JIDs so just skip this expensive operation
        router.setSkipJIDValidation(true);
        try {
            router.route(route.getChildElement());
        }
        catch (UnknownStanzaException use) {
            Element extraError = DocumentHelper.createElement(QName.get(
                    "unknown-stanza",
                    "http://jabber.org/protocol/connectionmanager#errors"));
            sendErrorPacket(route, PacketError.Condition.bad_request, extraError);
        }
        catch (Exception e) {
            Log.error("Error processing wrapped packet: " + route.getChildElement().asXML(), e);
            sendErrorPacket(route, PacketError.Condition.internal_server_error, null);
        }
    }

    /**
     * Sends an IQ error with the specified condition to the sender of the original
     * IQ packet.
     *
     * @param packet     the packet to be bounced.
     * @param extraError application specific error or null if none.
     * @param error the error.
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
     * @param error the error.
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
            session.process(reply);
        }
        else {
            Log.warn("No multiplexer session found. Packet not delivered: " + reply.toXML());
        }
    }
}
