/**
 * $RCSfile: $
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
package org.jivesoftware.openfire.session;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

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
public class LocalIncomingServerSession extends LocalServerSession implements IncomingServerSession {
	
	private static final Logger Log = LoggerFactory.getLogger(LocalIncomingServerSession.class);

    /**
     * List of domains, subdomains and virtual hostnames of the remote server that were
     * validated with this server. The remote server is allowed to send packets to this
     * server from any of the validated domains.
     */
    private Set<String> validatedDomains = new HashSet<>();

    /**
     * Domains or subdomain of this server that was used by the remote server
     * when validating the new connection. This information is useful to prevent
     * many connections from the same remote server to the same local domain.
     */
    private String localDomain = null;
    
    /**
     * Default domain, as supplied in stream header typically.
     */
    private String fromDomain = null;

    /**
     * Creates a new session that will receive packets. The new session will be authenticated
     * before being returned. If the authentication process fails then the answer will be
     * <tt>null</tt>.<p>
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
                
        String version = xpp.getAttributeValue("", "version");
        String fromDomain = xpp.getAttributeValue("", "from");
        String toDomain = xpp.getAttributeValue("", "to");
        int[] serverVersion = version != null ? decodeVersion(version) : new int[] {0,0};

        if (toDomain == null) {
            toDomain = serverName;
        }
        
        try {
            // Get the stream ID for the new session
            StreamID streamID = SessionManager.getInstance().nextStreamID();
            // Create a server Session for the remote server
            LocalIncomingServerSession session =
                    SessionManager.getInstance().createIncomingServerSession(connection, streamID, fromDomain);

            // Send the stream header
            StringBuilder openingStream = new StringBuilder();
            openingStream.append("<stream:stream");
            openingStream.append(" xmlns:db=\"jabber:server:dialback\"");
            openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            openingStream.append(" xmlns=\"jabber:server\"");
            openingStream.append(" from=\"").append(toDomain).append("\"");
            if (fromDomain != null) {
                openingStream.append(" to=\"").append(fromDomain).append("\"");
            }
            openingStream.append(" id=\"").append(streamID).append("\"");
            
            // OF-443: Not responding with a 1.0 version in the stream header when federating with older
            // implementations appears to reduce connection issues with those domains (patch by Marcin Cieślak).
            if (serverVersion[0] >= 1) {
                openingStream.append(" version=\"1.0\">");
            } else {
                openingStream.append('>');
            }
            
            connection.deliverRawText(openingStream.toString());

            if (serverVersion[0] >= 1) {        	
                // Remote server is XMPP 1.0 compliant so offer TLS and SASL to establish the connection (and server dialback)

	            // Indicate the TLS policy to use for this connection
	            Connection.TLSPolicy tlsPolicy = connection.getTlsPolicy();
	            boolean hasCertificates = false;
	            try {
	                hasCertificates = XMPPServer.getInstance().getCertificateStoreManager().getIdentityStore( ConnectionType.SOCKET_S2S ).getStore().size() > 0;
	            }
	            catch (Exception e) {
	                Log.error(e.getMessage(), e);
	            }
	            if (Connection.TLSPolicy.required == tlsPolicy && !hasCertificates) {
	                Log.error("Server session rejected. TLS is required but no certificates " +
	                        "were created.");
	                return null;
	            }
	            connection.setTlsPolicy(hasCertificates ? tlsPolicy : Connection.TLSPolicy.disabled);
            }

            // Indicate the compression policy to use for this connection
            connection.setCompressionPolicy( connection.getConfiguration().getCompressionPolicy() );

            StringBuilder sb = new StringBuilder();
            
            if (serverVersion[0] >= 1) {        	
                // Remote server is XMPP 1.0 compliant so offer TLS and SASL to establish the connection (and server dialback)
            	// Don't offer stream-features to pre-1.0 servers, as it confuses them. Sending features to Openfire < 3.7.1 confuses it too - OF-443) 
                sb.append("<stream:features>");

	            if (JiveGlobals.getBooleanProperty(ConnectionSettings.Server.TLS_ENABLED, true)) {
	                sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
	                if (!ServerDialback.isEnabled()) {
	                    // Server dialback is disabled so TLS is required
	                    sb.append("<required/>");
	                }
	                sb.append("</starttls>");
	            }
	            
	            // Include available SASL Mechanisms
	            sb.append(SASLAuthentication.getSASLMechanisms(session));
	            
	            if (ServerDialback.isEnabled()) {
	                // Also offer server dialback (when TLS is not required). Server dialback may be offered
	                // after TLS has been negotiated and a self-signed certificate is being used
	                sb.append("<dialback xmlns=\"urn:xmpp:features:dialback\"><errors/></dialback>");
	            }

	            sb.append("</stream:features>");
            }
            
            connection.deliverRawText(sb.toString());

            // Set the domain or subdomain of the local server targeted by the remote server
            session.setLocalDomain(serverName);
            return session;
        }
        catch (Exception e) {
            Log.error("Error establishing connection from remote server:" + connection, e);
            connection.close();
            return null;
        }
    }


    public LocalIncomingServerSession(String serverName, Connection connection, StreamID streamID, String fromDomain) {
        super(serverName, connection, streamID);
        this.fromDomain = fromDomain;
    }
    
    public String getDefaultIdentity() {
        return this.fromDomain;
    }

    @Override
	boolean canProcess(Packet packet) {
        return true;
    }


    @Override
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
            if (domain.equals(validatedDomain)) {
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
            // Set the first validated domain as the address of the session
            if (validatedDomains.size() < 2) {
                setAddress(new JID(null, domain, null));
            }
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
    @Override
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

    @Override
	public String getAvailableStreamFeatures() {
        StringBuilder sb = new StringBuilder();
        
        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            sb.append("<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>");
        }
        
        // Offer server dialback if using self-signed certificates and no authentication has been done yet
        boolean usingSelfSigned;
        final Certificate[] chain = conn.getLocalCertificates();
        if (chain == null || chain.length == 0) {
        	usingSelfSigned = true;
        } else {
        	try {
				usingSelfSigned = CertificateManager.isSelfSignedCertificate((X509Certificate) chain[0]);
			} catch (KeyStoreException ex) {
				Log.warn("Exception occurred while trying to determine whether local certificate is self-signed. Proceeding as if it is.", ex);
				usingSelfSigned = true;
			}
        }
        
        if (usingSelfSigned && ServerDialback.isEnabledForSelfSigned() && validatedDomains.isEmpty()) {
            sb.append("<dialback xmlns=\"urn:xmpp:features:dialback\"><errors/></dialback>");
        }
        
        return sb.toString();
    }
    
    public void tlsAuth() {
        usingServerDialback = false;
    }
}
