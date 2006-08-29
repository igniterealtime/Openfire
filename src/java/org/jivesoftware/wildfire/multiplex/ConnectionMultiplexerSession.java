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

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.AuthFactory;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.net.SASLAuthentication;
import org.jivesoftware.wildfire.net.SocketConnection;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;

/**
 * Represents a session between the server and a connection manager.<p>
 *
 * Each Connection Manager has its own domain. Each connection from the same connection manager
 * uses a different resource. Unlike any other session, connection manager sessions are not
 * present in the routing table. This means that connection managers are not reachable entities.
 * In other words, entities cannot send packets to connection managers but clients being hosted
 * by them. The main reason behind this design decision is that connection managers are private
 * components of the server so they can only be contacted by the server. Connection Manager
 * sessions are present in {@link SessionManager} but not in {@link RoutingTable}. Use
 * {@link SessionManager#getConnectionMultiplexerSessions(String)} to get all sessions or
 * {@link ConnectionMultiplexerManager#getMultiplexerSession(String)}
 * to get a random session to a given connection manager.
 *
 * @author Gaston Dombiak
 */
public class ConnectionMultiplexerSession extends Session {

    private static Connection.TLSPolicy tlsPolicy;
    private static Connection.CompressionPolicy compressionPolicy;

    /**
     * Milliseconds a connection has to be idle to be closed. Default is 30 minutes. Sending
     * stanzas to the client is not considered as activity. We are only considering the connection
     * active when the client sends some data or hearbeats (i.e. whitespaces) to the server.
     * The reason for this is that sending data will fail if the connection is closed. And if
     * the thread is blocked while sending data (because the socket is closed) then the clean up
     * thread will close the socket anyway.
     */
    private static long idleTimeout;

    static {
        // Set the TLS policy stored as a system property
        String policyName = JiveGlobals.getProperty("xmpp.multiplex.tls.policy",
                Connection.TLSPolicy.disabled.toString());
        tlsPolicy = Connection.TLSPolicy.valueOf(policyName);

        // Set the Compression policy stored as a system property
        policyName = JiveGlobals.getProperty("xmpp.multiplex.compression.policy",
                Connection.CompressionPolicy.disabled.toString());
        compressionPolicy = Connection.CompressionPolicy.valueOf(policyName);

        // Set the default read idle timeout. If none was set then assume 5 minutes
        idleTimeout = JiveGlobals.getIntProperty("xmpp.multiplex.idle", 5 * 60 * 1000);
    }

    public static Session createSession(String serverName, XMPPPacketReader reader,
            SocketConnection connection) throws XmlPullParserException, IOException,
            UnauthorizedException {
        XmlPullParser xpp = reader.getXPPParser();
        String domain = xpp.getAttributeValue("", "to");

        Log.debug("[ConMng] Starting registration of new connection manager for domain: " + domain);

        Writer writer = connection.getWriter();
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
            Log.debug("[ConMng] Domain not specified in stanza: " + xpp.getText());
            // Include the bad-format in the response
            StreamError error = new StreamError(StreamError.Condition.bad_format);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Get the requested domain
        JID address = new JID(domain);
        // Check that a secret key was configured in the server
        String secretKey = ConnectionMultiplexerManager.getDefaultSecret();
        if (secretKey == null) {
            Log.debug("[ConMng] A shared secret for connection manager was not found.");
            // Include the internal-server-error in the response
            StreamError error = new StreamError(StreamError.Condition.internal_server_error);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
            // Close the underlying connection
            connection.close();
            return null;
        }
        // Check that the requested subdomain is not already in use
        if (SessionManager.getInstance().getConnectionMultiplexerSession(address) != null) {
            Log.debug("[ConMng] Another connection manager is already using domain: " + domain);
            // Domain already occupied so return a conflict error and close the connection
            // Include the conflict error in the response
            StreamError error = new StreamError(StreamError.Condition.conflict);
            sb.append(error.toXML());
            writer.write(sb.toString());
            writer.flush();
            // Close the underlying connection
            connection.close();
            return null;
        }

        // Indicate the TLS policy to use for this connection
        connection.setTlsPolicy(tlsPolicy);

        // Indicate the compression policy to use for this connection
        connection.setCompressionPolicy(compressionPolicy);

        // Set the max number of milliseconds the connection may not receive data from the
        // client before closing the connection
        connection.setIdleTimeout(idleTimeout);

        // Set the connection manager domain to use delivering a packet fails
        ((MultiplexerPacketDeliverer) connection.getPacketDeliverer())
                .setConnectionManagerDomain(address.getDomain());

        // Create a ConnectionMultiplexerSession for the new session originated
        // from the connection manager
        Session session =
                SessionManager.getInstance().createMultiplexerSession(connection, address);
        // Set the address of the new session
        session.setAddress(address);

        try {
            Log.debug("[ConMng] Send stream header with ID: " + session.getStreamID() +
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
            writer.write(sb.toString());
            writer.flush();

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

            writer.write(sb.toString());
            writer.flush();

            return session;
        }
        catch (Exception e) {
            Log.error("An error occured while creating a Connection Manager Session", e);
            // Close the underlying connection
            connection.close();
            return null;
        }
    }

    public ConnectionMultiplexerSession(String serverName, Connection connection, StreamID streamID) {
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

    public void process(Packet packet) {
        deliver(packet);
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
            Log.debug("[ConMng] Incorrect handshake for connection manager with domain: " +
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
            setStatus(Session.STATUS_AUTHENTICATED);
            // Send empty handshake element to acknowledge success
            conn.deliverRawText("<handshake></handshake>");
            Log.debug("[ConMng] Connection manager was AUTHENTICATED with domain: " + getAddress());
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
        if (ClientSession.getTLSPolicy() != Connection.TLSPolicy.disabled) {
            Element tls = child.addElement("starttls", "urn:ietf:params:xml:ns:xmpp-tls");
            if (ClientSession.getTLSPolicy() == Connection.TLSPolicy.required) {
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
        if (ClientSession.getCompressionPolicy() == Connection.CompressionPolicy.optional) {
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
        try {
            conn.deliver(options);
        }
        catch (UnauthorizedException e) {
            // Do nothing. Should never happen
        }
    }

    void deliver(Packet packet) {
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(packet);
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
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

    /**
     * Returns the number of milliseconds a connection has to be idle to be closed. Default is
     * 30 minutes. Sending stanzas to the client is not considered as activity. We are only
     * considering the connection active when the client sends some data or hearbeats
     * (i.e. whitespaces) to the server.
     *
     * @return the number of milliseconds a connection has to be idle to be closed.
     */
    public static long getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * Sets the number of milliseconds a connection has to be idle to be closed. Default is
     * 30 minutes. Sending stanzas to the client is not considered as activity. We are only
     * considering the connection active when the client sends some data or hearbeats
     * (i.e. whitespaces) to the server.
     *
     * @param timeout the number of milliseconds a connection has to be idle to be closed.
     */
    public static void setIdleTimeout(long timeout) {
        idleTimeout = timeout;
        JiveGlobals.setProperty("xmpp.multiplex.idle", Long.toString(idleTimeout));
    }
}
