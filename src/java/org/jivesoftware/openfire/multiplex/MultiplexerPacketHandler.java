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

package org.jivesoftware.openfire.multiplex;

import java.util.List;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ConnectionMultiplexerSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

/**
 * IQ packets sent from Connection Managers themselves to the server will be handled by
 * instances of this class.
 * <p>
 * This class will interact with {@link ConnectionMultiplexerManager} to create, close or
 * get client sessions.</p>
 *
 * @author Gaston Dombiak
 */
public class MultiplexerPacketHandler {

	private static final Logger Log = LoggerFactory.getLogger(MultiplexerPacketHandler.class);

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
                String streamIDValue = child.attributeValue("id");
                if (streamIDValue == null) {
                    // No stream ID was included so return a bad_request error
                    Element extraError = DocumentHelper.createElement(QName.get(
                            "id-required", "http://jabber.org/protocol/connectionmanager#errors"));
                    sendErrorPacket(iq, PacketError.Condition.bad_request, extraError);
                }
                else if ("session".equals(child.getName())) {
                    StreamID streamID = BasicStreamIDFactory.createStreamID( streamIDValue );
                    Element create = child.element( "create" );
                    if (create != null) {
                        // Get the InetAddress of the client
                        Element hostElement = create.element("host");
                        String hostName = hostElement != null ? hostElement.attributeValue("name") : null;
                        String hostAddress = hostElement != null ? hostElement.attributeValue("address") : null;
                        // Connection Manager wants to create a Client Session
                        boolean created = multiplexerManager
                                .createClientSession(connectionManagerDomain, streamID, hostName, hostAddress);
                        if (created) {
                            sendResultPacket(iq);
                        }
                        else {
                            // Send error to CM. The CM should close the new-born connection
                            sendErrorPacket(iq, PacketError.Condition.not_allowed, null);
                        }
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
        StreamID streamID = route.getStreamID();
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
