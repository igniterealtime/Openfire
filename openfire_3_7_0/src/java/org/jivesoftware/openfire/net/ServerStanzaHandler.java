/**
 * $Revision: $
 * $Date: $
 *
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

package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;
import org.xmpp.packet.StreamError;

/**
 * Handler of XML stanzas sent by remote servers. Remote servers that send stanzas
 * with no TO or FROM will get their connections closed. Moreover, remote servers
 * that try to send stanzas from a not validated domain will also get their connections
 * closed.<p>
 *
 * Server-to-server communication requires two TCP connections between the servers where
 * one is used for sending packets whilst the other connection is used for receiving packets.
 * The connection used for receiving packets will use a ServerStanzaHandler since the other
 * connection will not receive packets.<p>
 *
 * TODO Finish migration of s2s to use NIO instead of blocking threads. Migrate from ServerSocketReader.
 *
 * @author Gaston Dombiak
 */
public class ServerStanzaHandler extends StanzaHandler {

	private static final Logger Log = LoggerFactory.getLogger(ServerStanzaHandler.class);

    public ServerStanzaHandler(PacketRouter router, String serverName, Connection connection) {
        super(router, serverName, connection);
    }

    boolean processUnknowPacket(Element doc) throws UnauthorizedException {
        // Handle subsequent db:result packets
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
            if (!((LocalIncomingServerSession) session).validateSubsequentDomain(doc)) {
                throw new UnauthorizedException("Failed to validate domain when using piggyback.");
            }
            return true;
        }
        else if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
            // The Receiving Server is reusing an existing connection for sending the
            // Authoritative Server a request for verification of a key
            ((LocalIncomingServerSession) session).verifyReceivedKey(doc);
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:server";
    }

    boolean validateHost() {
        return true;
    }

    boolean validateJIDs() {
        // TODO Should we trust other servers???
        return false;
    }

    boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        // TODO Finish implementation
        /*if ("jabber:server".equals(namespace)) {
            // The connected client is a server so create an IncomingServerSession
            session = LocalIncomingServerSession.createSession(serverName, reader, connection);
            return true;
        }*/
        return false;
    }

    void startTLS() throws Exception {
        // TODO Finish implementation. We need to get the name of the remote server if we want to validate certificates of the remote server that requested TLS

        boolean needed = JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true) &&
                JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true) &&
                !JiveGlobals.getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false);
        connection.startTLS(false, "IMPLEMENT_ME", needed ? Connection.ClientAuth.needed : Connection.ClientAuth.wanted);
    }
    protected void processIQ(IQ packet) throws UnauthorizedException {
        packetReceived(packet);
        // Actually process the packet
        super.processIQ(packet);
    }

    protected void processPresence(Presence packet) throws UnauthorizedException {
        packetReceived(packet);
        // Actually process the packet
        super.processPresence(packet);
    }

    protected void processMessage(Message packet) throws UnauthorizedException {
        packetReceived(packet);
        // Actually process the packet
        super.processMessage(packet);
    }

    /**
     * Make sure that the received packet has a TO and FROM values defined and that it was sent
     * from a previously validated domain. If the packet does not matches any of the above
     * conditions then a PacketRejectedException will be thrown.
     *
     * @param packet the received packet.
     * @throws UnauthorizedException if the packet does not include a TO or FROM or if the packet
     *                                 was sent from a domain that was not previously validated.
     */
    private void packetReceived(Packet packet) throws UnauthorizedException {
        if (packet.getTo() == null || packet.getFrom() == null) {
            Log.debug("ServerStanzaHandler: Closing IncomingServerSession due to packet with no TO or FROM: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes no TO or FROM
            StreamError error = new StreamError(StreamError.Condition.improper_addressing);
            connection.deliverRawText(error.toXML());
            throw new UnauthorizedException("Packet with no TO or FROM attributes");
        }
        else if (!((LocalIncomingServerSession) session).isValidDomain(packet.getFrom().getDomain())) {
            Log.debug("ServerStanzaHandler: Closing IncomingServerSession due to packet with invalid domain: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes an invalid FROM
            StreamError error = new StreamError(StreamError.Condition.invalid_from);
            connection.deliverRawText(error.toXML());
            throw new UnauthorizedException("Packet with no TO or FROM attributes");
        }
    }

}
