/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.session;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The <tt>IncomingServerSession</tt> represents the connection to a remote server that will only
 * be used for receiving packets.<p>
 *
 * Currently only the Server Dialback method is being used for authenticating the remote server.
 * Once the remote server has been authenticated incoming packets will be processed by this server.
 * It is also possible for remote servers to authenticate more domains once the session has been
 * established. For optimization reasons the existing connection is used between the servers.
 * Therefore, the incoming server session holds the list of authenticated domains which are allowed
 * to send packets to this server.<p>
 *
 * Using the Server Dialback method it is possible that this server may also act as the
 * Authoritative Server. This implies that an incoming connection will be established with this
 * server for authenticating a domain. This incoming connection will only last for a brief moment
 * and after the domain has been authenticated the connection will be closed and no session will
 * exist.
 *
 * @author Gaston Dombiak
 */
public class LocalIncomingServerSession extends LocalSession implements IncomingServerSession {
    /**
     * List of domains, subdomains and virtual hostnames of the remote server that were
     * validated with this server. The remote server is allowed to send packets to this
     * server from any of the validated domains.
     */
    private Set<String> validatedDomains = new HashSet<String>();

    /**
     * Domains or subdomain of this server that was used by the remote server
     * when validating the new connection. This information is useful to prevent
     * many connections from the same remote server to the same local domain.
     */
    private String localDomain = null;

    /**
     * Creates a new session that will receive packets. The new session will be authenticated
     * before being returned. If the authentication process fails then the answer will be
     * <tt>null</tt>.<p>
     *
     * Currently the Server Dialback method is the only way to authenticate a remote server. Since
     * Server Dialback requires an Authoritative Server, it is possible for this server to receive
     * an incoming connection that will only exist until the requested domain has been validated.
     * In this case, this method will return <tt>null</tt> since the connection is closed after
     * the domain was validated. See
     * {@link org.jivesoftware.openfire.server.ServerDialback#createIncomingSession(org.dom4j.io.XMPPPacketReader)} for more
     * information.
     *
     * @param serverName hostname of this server.
     * @param reader reader on the new established connection with the remote server.
     * @param connection the new established connection with the remote server.
     * @return a new session that will receive packets or null if a problem occured while
     *         authenticating the remote server or when acting as the Authoritative Server during
     *         a Server Dialback authentication process.
     * @throws org.xmlpull.v1.XmlPullParserException if an error occurs while parsing the XML.
     * @throws java.io.IOException if an input/output error occurs while using the connection.
     */
    public static LocalIncomingServerSession createSession(String serverName, XMPPPacketReader reader,
            SocketConnection connection) throws XmlPullParserException, IOException {
        XmlPullParser xpp = reader.getXPPParser();
        if (xpp.getNamespace("db") != null) {
            // Server is trying to establish connection and authenticate using server dialback
            if (ServerDialback.isEnabled()) {
                ServerDialback method = new ServerDialback(connection, serverName);
                return method.createIncomingSession(reader);
            }
            Log.debug("Server dialback is disabled. Rejecting connection: " + connection);
        }
        String version = xpp.getAttributeValue("", "version");
        int[] serverVersion = version != null ? decodeVersion(version) : new int[] {0,0};
        if (serverVersion[0] >= 1) {
            // Remote server is XMPP 1.0 compliant so offer TLS and SASL to establish the connection
            if (JiveGlobals.getBooleanProperty("xmpp.server.tls.enabled", true)) {
                try {
                    return createIncomingSession(connection, serverName);
                }
                catch (Exception e) {
                    Log.error("Error establishing connection from remote server", e);
                }
            }
            else {
                connection.deliverRawText(
                        new StreamError(StreamError.Condition.invalid_namespace).toXML());
                Log.debug("Server TLS is disabled. Rejecting connection: " + connection);
            }
        }
        // Close the connection since remote server is not XMPP 1.0 compliant and is not
        // using server dialback to establish and authenticate the connection
        connection.close();
        return null;
    }

    /**
     * Returns a new incoming server session pending to be authenticated. The remote server
     * will be notified that TLS and SASL are available. The remote server will be able to
     * send packets to this server only after SASL authentication has been finished.
     *
     * @param connection the new established connection with the remote server.
     * @param serverName hostname of this server.
     * @return a new incoming server session pending to be authenticated.
     * @throws org.jivesoftware.openfire.auth.UnauthorizedException if this server is being shutdown.
     */
    private static LocalIncomingServerSession createIncomingSession(SocketConnection connection, String serverName)
            throws UnauthorizedException {
        // Get the stream ID for the new session
        StreamID streamID = SessionManager.getInstance().nextStreamID();
        // Create a server Session for the remote server
        LocalIncomingServerSession session =
                SessionManager.getInstance().createIncomingServerSession(connection, streamID);

        // Send the stream header
        StringBuilder openingStream = new StringBuilder();
        openingStream.append("<stream:stream");
        openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
        openingStream.append(" xmlns=\"jabber:server\"");
        openingStream.append(" from=\"").append(serverName).append("\"");
        openingStream.append(" id=\"").append(streamID).append("\"");
        openingStream.append(" version=\"1.0\">");
        connection.deliverRawText(openingStream.toString());

        // Indicate the TLS policy to use for this connection
        Connection.TLSPolicy tlsPolicy =
                ServerDialback.isEnabled() ? Connection.TLSPolicy.optional :
                        Connection.TLSPolicy.required;
        boolean hasCertificates = false;
        try {
            hasCertificates = SSLConfig.getKeyStore().size() > 0;
        }
        catch (Exception e) {
            Log.error(e);
        }
        if (Connection.TLSPolicy.required == tlsPolicy && !hasCertificates) {
            Log.error("Server session rejected. TLS is required but no certificates " +
                    "were created.");
            return null;
        }
        connection.setTlsPolicy(hasCertificates ? tlsPolicy : Connection.TLSPolicy.disabled);

        // Indicate the compression policy to use for this connection
        String policyName = JiveGlobals.getProperty("xmpp.server.compression.policy",
                Connection.CompressionPolicy.disabled.toString());
        Connection.CompressionPolicy compressionPolicy =
                Connection.CompressionPolicy.valueOf(policyName);
        connection.setCompressionPolicy(compressionPolicy);

        StringBuilder sb = new StringBuilder();
        sb.append("<stream:features>");
        sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
        if (!ServerDialback.isEnabled()) {
            // Server dialback is disabled so TLS is required
            sb.append("<required/>");
        }
        sb.append("</starttls>");
        // Include available SASL Mechanisms
        sb.append(SASLAuthentication.getSASLMechanisms(session));
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());

        // Set the domain or subdomain of the local server targeted by the remote server
        session.setLocalDomain(serverName);
        return session;
    }

    public LocalIncomingServerSession(String serverName, Connection connection, StreamID streamID) {
        super(serverName, connection, streamID);
    }

    boolean canProcess(Packet packet) {
        return true;
    }


    void deliver(Packet packet) throws UnauthorizedException {
        // Do nothing
    }

    /**
     * Returns true if the request of a new domain was valid. Sessions may receive subsequent
     * domain validation request. If the validation of the new domain fails then the session and
     * the underlying TCP connection will be closed.<p>
     *
     * For optimization reasons, the same session may be servicing several domains of a
     * remote server.
     *
     * @param dbResult the DOM stanza requesting the domain validation.
     * @return true if the requested domain was valid.
     */
    public boolean validateSubsequentDomain(Element dbResult) {
        ServerDialback method = new ServerDialback(getConnection(), getServerName());
        if (method.validateRemoteDomain(dbResult, getStreamID())) {
            // Add the validated domain as a valid domain
            addValidatedDomain(dbResult.attributeValue("from"));
            return true;
        }
        return false;
    }

    /**
     * Returns true if the specified domain has been validated for this session. The remote
     * server should send a "db:result" packet for registering new subdomains or even
     * virtual hosts.<p>
     *
     * In the spirit of being flexible we allow remote servers to not register subdomains
     * and even so consider subdomains that include the server domain in their domain part
     * as valid domains.
     *
     * @param domain the domain to validate.
     * @return true if the specified domain has been validated for this session.
     */
    public boolean isValidDomain(String domain) {
        // Check if the specified domain is contained in any of the validated domains
        for (String validatedDomain : getValidatedDomains()) {
            if (domain.contains(validatedDomain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a collection with all the domains, subdomains and virtual hosts that where
     * validated. The remote server is allowed to send packets from any of these domains,
     * subdomains and virtual hosts.
     *
     * @return domains, subdomains and virtual hosts that where validated.
     */
    public Collection<String> getValidatedDomains() {
        return Collections.unmodifiableCollection(validatedDomains);
    }

    /**
     * Adds a new validated domain, subdomain or virtual host to the list of
     * validated domains for the remote server.
     *
     * @param domain the new validated domain, subdomain or virtual host to add.
     */
    public void addValidatedDomain(String domain) {
        if (validatedDomains.add(domain)) {
            // Register the new validated domain for this server session in SessionManager
            SessionManager.getInstance().registerIncomingServerSession(domain, this);
        }
    }

    /**
     * Removes the previously validated domain from the list of validated domains. The remote
     * server will no longer be able to send packets from the removed domain, subdomain or
     * virtual host.
     *
     * @param domain the domain, subdomain or virtual host to remove from the list of
     *        validated domains.
     */
    public void removeValidatedDomain(String domain) {
        validatedDomains.remove(domain);
        // Unregister the validated domain for this server session in SessionManager
        SessionManager.getInstance().unregisterIncomingServerSession(domain, this);
    }

    /**
     * Returns the domain or subdomain of the local server used by the remote server
     * when validating the session. This information is only used to prevent many
     * connections from the same remote server to the same domain or subdomain of
     * the local server.
     *
     * @return the domain or subdomain of the local server used by the remote server
     *         when validating the session.
     */
    public String getLocalDomain() {
        return localDomain;
    }

    /**
     * Sets the domain or subdomain of the local server used by the remote server when asking
     * to validate the session. This information is only used to prevent many connections from
     * the same remote server to the same domain or subdomain of the local server.
     *
     * @param domain the domain or subdomain of the local server used when validating the
     *        session.
     */
    public void setLocalDomain(String domain) {
        localDomain = domain;
    }

    /**
     * Verifies the received key sent by the remote server. This server is trying to generate
     * an outgoing connection to the remote server and the remote server is reusing an incoming
     * connection for validating the key.
     *
     * @param doc the received Element that contains the key to verify.
     */
    public void verifyReceivedKey(Element doc) {
        ServerDialback.verifyReceivedKey(doc, getConnection());
    }

    public String getAvailableStreamFeatures() {
        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            return "<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>";
        }
        // Nothing special to add
        return null;
    }
}
