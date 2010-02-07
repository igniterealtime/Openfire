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
import org.jivesoftware.openfire.multiplex.MultiplexerPacketHandler;
import org.jivesoftware.openfire.multiplex.Route;
import org.jivesoftware.openfire.session.LocalConnectionMultiplexerSession;
import org.jivesoftware.openfire.session.Session;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Message;
import org.xmpp.packet.PacketError;
import org.xmpp.packet.Presence;

/**
 * Handler of XML stanzas sent by Connection Managers.
 *
 * @author Gaston Dombiak
 */
public class MultiplexerStanzaHandler extends StanzaHandler {

    /**
     * Handler of IQ packets sent from the Connection Manager to the server.
     */
    private MultiplexerPacketHandler packetHandler;

    public MultiplexerStanzaHandler(PacketRouter router, String serverName, Connection connection) {
        super(router, serverName, connection);
    }

    @Override
	protected void processIQ(final IQ packet) {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            IQ reply = new IQ();
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        // Process the packet
        packetHandler.handle(packet);
    }

    @Override
	protected void processMessage(final Message packet) throws UnauthorizedException {
        throw new UnauthorizedException("Message packets are not supported. Original packets " +
                "should be wrapped by route packets.");
    }

    @Override
	protected void processPresence(final Presence packet) throws UnauthorizedException {
        throw new UnauthorizedException("Message packets are not supported. Original packets " +
                "should be wrapped by route packets.");
    }

    /**
     * Process stanza sent by a client that is connected to a connection manager. The
     * original stanza is wrapped in the route element. Only a single stanza must be
     * wrapped in the route element.
     *
     * @param packet the route element.
     */
    private void processRoute(final Route packet) {
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Session is not authenticated so return error
            Route reply = new Route(packet.getStreamID());
            reply.setID(packet.getID());
            reply.setTo(packet.getFrom());
            reply.setFrom(packet.getTo());
            reply.setError(PacketError.Condition.not_authorized);
            session.process(reply);
            return;
        }
        // Process the packet
        packetHandler.route(packet);
    }

    @Override
	boolean processUnknowPacket(Element doc) {
        String tag = doc.getName();
        if ("route".equals(tag)) {
            // Process stanza wrapped by the route packet
            processRoute(new Route(doc));
            return true;
        } else if ("handshake".equals(tag)) {
            if (!((LocalConnectionMultiplexerSession) session).authenticate(doc.getStringValue())) {
                session.close();
            }
            return true;
        } else if ("error".equals(tag) && "stream".equals(doc.getNamespacePrefix())) {
            session.close();
            return true;
        }
        return false;
    }

    @Override
	String getNamespace() {
        return "jabber:connectionmanager";
    }

    @Override
	boolean validateHost() {
        return false;
    }

    @Override
	boolean validateJIDs() {
        return false;
    }

    @Override
	boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        if (getNamespace().equals(namespace)) {
            // The connected client is a connection manager so create a ConnectionMultiplexerSession
            session = LocalConnectionMultiplexerSession.createSession(serverName, xpp, connection);
            if (session != null) {
                packetHandler = new MultiplexerPacketHandler(session.getAddress().getDomain());
            }
            return true;
        }
        return false;
    }

    @Override
	void startTLS() throws Exception {
        // TODO Finish implementation. We need to get the name of the CM if we want to validate certificates of the CM that requested TLS
        connection.startTLS(false, "IMPLEMENT_ME", Connection.ClientAuth.disabled);
    }
}
