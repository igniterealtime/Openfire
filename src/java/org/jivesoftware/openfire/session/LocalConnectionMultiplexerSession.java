/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

import java.util.Collection;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.multiplex.ConnectionMultiplexerManager;
import org.jivesoftware.openfire.multiplex.MultiplexerPacketDeliverer;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

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

    private static Connection.TLSPolicy tlsPolicy;
    private static Connection.CompressionPolicy compressionPolicy;

    static {
        // Set the TLS policy stored as a system property
        String policyName = JiveGlobals.getProperty("xmpp.multiplex.tls.policy",
                Connection.TLSPolicy.disabled.toString());
        tlsPolicy = Connection.TLSPolicy.valueOf(policyName);

        // Set the Compression policy stored as a system property
        policyName = JiveGlobals.getProperty("xmpp.multiplex.compression.policy",
                Connection.CompressionPolicy.disabled.toString());
        compressionPolicy = Connection.CompressionPolicy.valueOf(policyName);
    }

    public static LocalConnectionMultiplexerSession createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        String domain = xpp.getAttributeValue("", "to");

        Log.debug("LocalConnectionMultiplexerSession: [ConMng] Starting registration of new connection manager for domain: " + domain);

        // Default answer header in case of an error
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        sb.append("<stream:stream ");
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" ");
        sb.append("xmlns=\"jabber:connectionmanager\" from=\"");
        sb.append(domain);
        sb.append("\" version=\"1.0\">");

        // Check that a domain was provided in the stream header
        if (domain == null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Domain not specified in stanza: " + xpp.getText());
            // Include the bad-format in the response
            StreamError error = new StreamError(StreamError.Condition.bad_format);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Get the requested domain
        JID address = new JID(domain);
        // Check that a secret key was configured in the server
        String secretKey = ConnectionMultiplexerManager.getDefaultSecret();
        if (secretKey == null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] A shared secret for connection manager was not found.");
            // Include the internal-server-error in the response
            StreamError error = new StreamError(StreamError.Condition.internal_server_error);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (SessionManager.getInstance().getConnectionMultiplexerSession(address) != null) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Another connection manager is already using domain: " + domain);
            // Domain already occupied so return a conflict error and close the connection
            // Include the conflict error in the response
            StreamError error = new StreamError(StreamError.Condition.conflict);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Indicate the TLS policy to use for this connection
        connection.setTlsPolicy(tlsPolicy);

        // Indicate the compression policy to use for this connection
        connection.setCompressionPolicy(compressionPolicy);

        // Set the connection manager domain to use delivering a packet fails
        ((MultiplexerPacketDeliverer) connection.getPacketDeliverer())
                .setConnectionManagerDomain(address.getDomain());

        // Create a ConnectionMultiplexerSession for the new session originated
        // from the connection manager
        LocalConnectionMultiplexerSession session =
                SessionManager.getInstance().createMultiplexerSession(connection, address);
        // Set the address of the new session
        session.setAddress(address);
        connection.init(session);

        try {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Send stream header with ID: " + session.getStreamID() +
                    " for connection manager with domain: " +
                    domain);
            // Build the start packet response
            sb = new StringBuilder();
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            sb.append("<stream:stream ");
            sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" ");
            sb.append("xmlns=\"jabber:connectionmanager\" from=\"");
            sb.append(domain);
            sb.append("\" id=\"");
            sb.append(session.getStreamID().toString());
            sb.append("\" version=\"1.0\" >");
            connection.deliverRawText(sb.toString());

            // Announce stream features.

            sb = new StringBuilder(490);
            sb.append("<stream:features>");
            if (tlsPolicy != Connection.TLSPolicy.disabled) {
                sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
                if (tlsPolicy == Connection.TLSPolicy.required) {
                    sb.append("<required/>");
                }
                sb.append("</starttls>");
            }
            // Include Stream features
            String specificFeatures = session.getAvailableStreamFeatures();
            if (specificFeatures != null) {
                sb.append(specificFeatures);
            }
            sb.append("</stream:features>");

            connection.deliverRawText(sb.toString());

            return session;
        }
        catch (Exception e) {
            Log.error("An error occured while creating a Connection Manager Session", e);
            // Close the underlying connection
            connection.close();
            return null;
        }
    }

    public LocalConnectionMultiplexerSession(String serverName, Connection connection, StreamID streamID) {
        super(serverName, connection, streamID);
    }

    public String getAvailableStreamFeatures() {
        if (conn.getTlsPolicy() == Connection.TLSPolicy.required && !conn.isSecure()) {
            return null;
        }

        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            return "<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>";
        }
        return null;
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
        String anticipatedDigest = AuthFactory.createDigest(getStreamID().getID(),
                ConnectionMultiplexerManager.getDefaultSecret());
        // Check that the provided handshake (secret key + sessionID) is correct
        if (!anticipatedDigest.equalsIgnoreCase(digest)) {
            Log.debug("LocalConnectionMultiplexerSession: [ConMng] Incorrect handshake for connection manager with domain: " +
                    getAddress().getDomain());
            //  The credentials supplied by the initiator are not valid (answer an error
            // and close the connection)
            conn.deliverRawText(new StreamError(StreamError.Condition.not_authorized).toXML());
            // Close the underlying connection
            conn.close();
            return false;
        }
        else {
            // Component has authenticated fine
            setStatus(STATUS_AUTHENTICATED);
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
        IQ options = new IQ(IQ.Type.set);
        Element child = options.setChildElement("configuration",
                "http://jabber.org/protocol/connectionmanager");
        // Add info about TLS
        if (LocalClientSession.getTLSPolicy() != Connection.TLSPolicy.disabled) {
            Element tls = child.addElement("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
            if (LocalClientSession.getTLSPolicy() == Connection.TLSPolicy.required) {
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
        if (LocalClientSession.getCompressionPolicy() == Connection.CompressionPolicy.optional) {
            Element comp = child.addElement("compression", "http://jabber.org/features/compress");
            comp.addElement("method").setText("zlib");
        }
        // Add info about Non-SASL authentication
        child.addElement("auth", "http://jabber.org/features/iq-auth");
        // Add info about In-Band Registration
        if (XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled()) {
            child.addElement("register", "http://jabber.org/features/iq-register");
        }
        // Send the options
        process(options);
    }

    boolean canProcess(Packet packet) {
        return true;
    }

    void deliver(Packet packet) throws UnauthorizedException {
        if (conn != null && !conn.isClosed()) {
            conn.deliver(packet);
        }
    }

    /**
     * Returns whether TLS is mandatory, optional or is disabled for clients. When TLS is
     * mandatory clients are required to secure their connections or otherwise their connections
     * will be closed. On the other hand, when TLS is disabled clients are not allowed to secure
     * their connections using TLS. Their connections will be closed if they try to secure the
     * connection. in this last case.
     *
     * @return whether TLS is mandatory, optional or is disabled.
     */
    public static SocketConnection.TLSPolicy getTLSPolicy() {
        return tlsPolicy;
    }

    /**
     * Sets whether TLS is mandatory, optional or is disabled for clients. When TLS is
     * mandatory clients are required to secure their connections or otherwise their connections
     * will be closed. On the other hand, when TLS is disabled clients are not allowed to secure
     * their connections using TLS. Their connections will be closed if they try to secure the
     * connection. in this last case.
     *
     * @param policy whether TLS is mandatory, optional or is disabled.
     */
    public static void setTLSPolicy(SocketConnection.TLSPolicy policy) {
        tlsPolicy = policy;
        JiveGlobals.setProperty("xmpp.multiplex.tls.policy", tlsPolicy.toString());
    }

    /**
     * Returns whether compression is optional or is disabled for clients.
     *
     * @return whether compression is optional or is disabled.
     */
    public static SocketConnection.CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    /**
     * Sets whether compression is optional or is disabled for clients.
     *
     * @param policy whether compression is optional or is disabled.
     */
    public static void setCompressionPolicy(SocketConnection.CompressionPolicy policy) {
        compressionPolicy = policy;
        JiveGlobals.setProperty("xmpp.multiplex.compression.policy", compressionPolicy.toString());
    }

}
