/*
 * Copyright (C) 2004-2009 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.session;

import org.dom4j.*;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager;
import org.jivesoftware.openfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Represents a session between the server and a connection manager.<p>
 *
 * Each Connection Manager has its own domain. Each connection from the same connection manager
 * uses a different resource. Unlike any other session, connection manager sessions are not
 * present in the routing table. This means that connection managers are not reachable entities.
 * In other words, entities cannot send packets to connection managers but clients being hosted
 * by them. The main reason behind this design decision is that connection managers are private
 * components of the server so they can only be contacted by the server. Connection Manager
 * sessions are present in {@link SessionManager} but not in {@link org.jivesoftware.openfire.RoutingTable}. Use
 * {@link SessionManager#getConnectionMultiplexerSessions(String)} to get all sessions or
 * {@link org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager#getMultiplexerSession(String)}
 * to get a random session to a given connection manager.
 *
 * @author Gaston Dombiak
 */
public class LocalConnectionMultiplexerSession extends LocalSession implements ConnectionMultiplexerSession {

    private static final Logger Log = LoggerFactory.getLogger(LocalConnectionMultiplexerSession.class);

    public static LocalConnectionMultiplexerSession createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        String domain = xpp.getAttributeValue("", "to");

        // Retrieve list of namespaces declared in current element (OF-2556)
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));

        Log.debug("LocalConnectionMultiplexerSession: [ConMng] Starting registration of new connection manager for domain: " + domain);

        final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
        final Document document = DocumentHelper.createDocument(stream);
        document.setXMLEncoding(StandardCharsets.UTF_8.toString());
        stream.add(Namespace.get("jabber:connectionmanager"));
        stream.addAttribute("version", "1.0");

        // Check that a domain was provided in the stream header
        if (domain == null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Domain not specified in stanza: {}", xpp.getText());
            // Include the bad-format in the response and close the underlying connection.
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.bad_format, "Missing 'to' attribute value."));
            return null;
        }
        stream.addAttribute("from", domain);

        // Get the requested domain
        JID address = new JID(domain);
        // Check that a secret key was configured in the server
        String secretKey = ConnectionMultiplexerManager.getDefaultSecret();
        if (secretKey == null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] A shared secret for connection manager was not found.");
            // Include the internal-server-error in the response and close the underlying connection
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.internal_server_error));
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (SessionManager.getInstance().getConnectionMultiplexerSession(address) != null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Another connection manager is already using domain: {}", domain);
            // Domain already occupied so return a conflict error and close the connection.
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.conflict, "The requested address is already being used by another connection manager."));
            return null;
        }

        // Set the connection manager domain to use delivering a packet fails
        final MultiplexerPacketDeliverer packetDeliverer = (MultiplexerPacketDeliverer) connection.getPacketDeliverer();
        if (packetDeliverer != null) {
            packetDeliverer.setConnectionManagerDomain(address.getDomain());
        }

        // Create a ConnectionMultiplexerSession for the new session originated
        // from the connection manager
        LocalConnectionMultiplexerSession session = SessionManager.getInstance().createMultiplexerSession(connection, address);
        // Set the address of the new session
        session.setAddress(address);
        connection.init(session);

        try {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Send stream header with ID: {} for connection manager with domain: {}", session.getStreamID(), domain);
            // Build the start packet response
            stream.addAttribute("id", session.getStreamID().toString());

            // Announce stream features.
            final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
            document.getRootElement().add(features);
            if (connection.getConfiguration().getTlsPolicy() != Connection.TLSPolicy.disabled && !connection.getConfiguration().getIdentityStore().getAllCertificates().isEmpty()) {
                final Element starttls = DocumentHelper.createElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
                if (connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required) {
                    starttls.addElement("required");
                }
                features.add(starttls);
            }

            // Include Stream features
            final List<Element> specificFeatures = session.getAvailableStreamFeatures();
            if (specificFeatures != null) {
                for (final Element feature : specificFeatures) {
                    features.add(feature);
                }
            }

            connection.deliverRawText(StringUtils.asUnclosedStream(document));

            return session;
        }
        catch (Exception e) {
            Log.error("An error occurred while creating a Connection Manager Session", e);
            // Close the underlying connection
            connection.deliverRawText(StringUtils.asUnclosedStream(document));
            connection.close(new StreamError(StreamError.Condition.internal_server_error));
            return null;
        }
    }

    public LocalConnectionMultiplexerSession(String serverName, Connection connection, StreamID streamID) {
        super(serverName, connection, streamID, Locale.getDefault());
    }

    @Override
    public List<Element> getAvailableStreamFeatures() {
        if (conn.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required && !conn.isEncrypted()) {
            return Collections.emptyList();
        }

        // Include Stream Compression Mechanism
        if (conn.getConfiguration().getCompressionPolicy() != Connection.CompressionPolicy.disabled && !conn.isCompressed()) {
            final Element compression = DocumentHelper.createElement(QName.get("compression", "http://jabber.org/features/compress"));
            compression.addElement("method").addText("zlib");
            return List.of(compression);
        }
        return Collections.emptyList();
    }

    @Override
    public void setDetached() {
        throw new UnsupportedOperationException("Stream management is not supported for multiplexers.");
    }

    @Override
    public void reattach(LocalSession connectionProvider, long h) {
        throw new UnsupportedOperationException("Stream management is not supported for multiplexers.");
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    @Nonnull
    @Override
    public Connection getConnection() {
        final Connection connection = super.getConnection();
        assert connection != null; // Openfire does not implement stream management for multiplex connections. Therefor, the connection cannot be null.
        return connection;
    }

    /**
     * Authenticates the connection manager. Shared secret is validated with the one provided
     * by the connection manager. If everything went fine then the session will have a status
     * of "authenticated" and the connection manager will receive the client configuration
     * options.
     *
     * @param digest the digest provided by the connection manager with the handshake stanza.
     * @return true if the connection manager was sucessfully authenticated.
     */
    public boolean authenticate(String digest) {
        // Perform authentication. Wait for the handshake (with the secret key)
        String anticipatedDigest = AuthFactory.createDigest(getStreamID().getID(), ConnectionMultiplexerManager.getDefaultSecret());
        // Check that the provided handshake (secret key + sessionID) is correct
        if (!anticipatedDigest.equalsIgnoreCase(digest)) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Incorrect handshake for connection manager with domain: {}", getAddress().getDomain());
            // The credentials supplied by the initiator are not valid (answer an error and close the connection)
            conn.close(new StreamError(StreamError.Condition.not_authorized));
            return false;
        }
        else {
            // Component has authenticated fine
            setStatus(Session.Status.AUTHENTICATED);
            // Send empty handshake element to acknowledge success
            conn.deliverRawText("<handshake></handshake>");
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Connection manager was AUTHENTICATED with domain: " + getAddress());
            sendClientOptions();
            return true;
        }
    }

    /**
     * Send to the Connection Manager the connection options available for clients. The info
     * to send includes:
     * <ul>
     *  <li>if TLS is available, optional or required
     *  <li>SASL mechanisms available before TLS is negotiated
     *  <li>if compression is available
     *  <li>if Non-SASL authentication is available
     *  <li>if In-Band Registration is available
     * </ul
     */
    private void sendClientOptions() {

        final ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        final ConnectionConfiguration configuration = connectionManager.getListener( ConnectionType.SOCKET_C2S, false ).generateConnectionConfiguration();

        IQ options = new IQ(IQ.Type.set);
        Element child = options.setChildElement("configuration",
                "http://jabber.org/protocol/connectionmanager");
        // Add info about TLS
        if (configuration.getTlsPolicy() != Connection.TLSPolicy.disabled) {
            Element tls = child.addElement("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
            if (configuration.getTlsPolicy() == Connection.TLSPolicy.required) {
                tls.addElement("required");
            }

        }
        // Add info about SASL mechanisms
        Collection<String> mechanisms = SASLAuthentication.getSupportedMechanisms();
        if (!mechanisms.isEmpty()) {
            Element sasl = child.addElement("mechanisms", "urn:ietf:params:xml:ns:xmpp-sasl");
            for (String mechanism : mechanisms) {
                sasl.addElement("mechanism").setText(mechanism);
            }
        }
        // Add info about Stream Compression
        if (configuration.getCompressionPolicy() == Connection.CompressionPolicy.optional) {
            Element comp = child.addElement("compression", "http://jabber.org/features/compress");
            comp.addElement("method").setText("zlib");
        }
        // Add info about Non-SASL authentication
        if (XMPPServer.getInstance().getIQRouter().supports("jabber:iq:auth")) {
            child.addElement("auth", "http://jabber.org/features/iq-auth");
        }
        // Add info about In-Band Registration
        if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
            child.addElement("register", "http://jabber.org/features/iq-register");
        }
        // Send the options
        process(options);
    }

    @Override
    boolean canProcess(Packet packet) {
        return true;
    }

    @Override
    void deliver(Packet packet) throws UnauthorizedException {
        if (!conn.isClosed()) {
            conn.deliver(packet);
        }
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() +"{" +
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", serverName='" + serverName + '\'' +
            '}';
    }
}
