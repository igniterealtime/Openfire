/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.*;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
 * @author Gaston Dombiak
 * @author Alex Gidman
 * @author Matthew Vivian
 */
public class ServerStanzaHandler extends StanzaHandler {

    private static final Logger Log = LoggerFactory.getLogger(ServerStanzaHandler.class);

    /**
     * Domain of the local server and remote server or client.
     */
    private DomainPair domainPair;

    /**
     * Controls if JIDs that are in the addresses of stanzas supplied by remote domains are validated.
     */
    public static final SystemProperty<Boolean> SKIP_JID_VALIDATION = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.server.incoming.skip-jid-validation")
        .setDefaultValue(false)
        .setDynamic(true)
        .build();

    private final boolean directTLS;

    public ServerStanzaHandler(PacketRouter router, Connection connection, boolean directTLS) {
        super(router, connection);
        this.directTLS = directTLS;
    }

    @Override
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

    @Override
    Namespace getNamespace() {
        return new Namespace("", "jabber:server");
    }

    @Override
    boolean validateHost() {
        // Hosts are not currently validated for S2S
        return false;
    }

    @Override
    boolean validateJIDs() {
        return !SKIP_JID_VALIDATION.getValue();
    }

    @Override
    protected void createSession(XmlPullParser xpp) throws XmlPullParserException, IOException {
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        this.domainPair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), xpp.getAttributeValue("", "from"));
        super.createSession(xpp);
    }

    @Override
    void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException
    {
        // The connected client is a server so create an IncomingServerSession
        try {
            session = LocalIncomingServerSession.createSession(serverName, xpp, connection, this.directTLS, this.startedTLS);
        } catch (IOException e) {
            Log.error(e.getMessage(), e);
        }
    }

    @Override
    void startTLS() throws Exception {
        boolean needed = JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_VERIFY, true) &&
                JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_CERTIFICATE_CHAIN_VERIFY, true) &&
                !JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ACCEPT_SELFSIGNED_CERTS, false);
        //needed ? Connection.ClientAuth.needed : Connection.ClientAuth.wanted
        connection.startTLS(false, false);
    }

    @Override
    protected Document getStreamHeader() {
        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(getNamespace());
        if (ServerDialback.isEnabled() && this.session != null) {
            stream.add(Namespace.get("db", "jabber:server:dialback"));
        }
        stream.addAttribute("from", domainPair.getLocal());
        stream.addAttribute("to", domainPair.getRemote());
        stream.addAttribute("id", session.getStreamID().getID());
        stream.addAttribute(QName.get("lang", Namespace.XML_NAMESPACE), session.getLanguage().toLanguageTag());
        stream.addAttribute("version", Session.MAJOR_VERSION + "." + Session.MINOR_VERSION);
        return document;
    }

    @Override
    protected void tlsNegotiated(XmlPullParser xpp) throws XmlPullParserException, IOException {
        // Discard domain values obtained from unencrypted initiating stream
        // in accordance with RFC 6120 § 5.4.3.3. See https://datatracker.ietf.org/doc/html/rfc6120#section-5.4.3.3
        this.domainPair = null;

        // Get remoteDomain from encrypted stream tag
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        this.domainPair = new DomainPair(XMPPServer.getInstance().getServerInfo().getXMPPDomain(), xpp.getAttributeValue("", "from"));

        // Discard session data obtained from unencrypted initiating stream
        // in accordance with RFC 6120 § 5.4.3.3. See https://datatracker.ietf.org/doc/html/rfc6120#section-5.4.3.3
        SessionManager sessionManager = SessionManager.getInstance();
        sessionManager.unregisterIncomingServerSession(session.getStreamID());
        // Re-instate the session with a new ID on the same connection
        createSession(domainPair.getLocal(), xpp, connection);
        this.connection.reinit(session);

        super.tlsNegotiated(xpp);
    }

    @Override
    protected void processIQ(IQ packet) throws UnauthorizedException {
        packetReceived(packet);
        // Actually process the packet
        super.processIQ(packet);
    }

    @Override
    protected void processPresence(Presence packet) throws UnauthorizedException {
        packetReceived(packet);
        // Actually process the packet
        super.processPresence(packet);
    }

    @Override
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
            StreamError error = new StreamError(StreamError.Condition.improper_addressing, "Stanza is missing 'from' and/or 'to' address");
            connection.deliverRawText(error.toXML());
            throw new UnauthorizedException("Packet with no TO or FROM attributes");
        }
        else if (!((LocalIncomingServerSession) session).isValidDomain(packet.getFrom().getDomain())) {
            Log.debug("ServerStanzaHandler: Closing IncomingServerSession due to packet with invalid domain: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes an invalid FROM
            StreamError error = new StreamError(StreamError.Condition.invalid_from);
            connection.deliverRawText(error.toXML());
            throw new UnauthorizedException("Packet with invalid FROM attribute: " + packet.getFrom());
        }
    }

}
