/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2023 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.nio.XMLLightweightParser;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.server.ServerDialbackErrorException;
import org.jivesoftware.openfire.server.ServerDialbackKeyInvalidException;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.StreamErrorException;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-to-server communication is done using two TCP connections between the servers. One
 * connection is used for sending packets while the other connection is used for receiving packets.
 * The {@code IncomingServerSession} represents the connection to a remote server that will only
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
     * {@code null}.<p>
     *
     * @param serverName hostname of this server.
     * @param xpp XML parse that is providing data from the new established connection with the remote server.
     * @param connection the new established connection with the remote server.
     * @param directTLS true of connections are immediately encrypted (as opposed to plain text / startls).
     * @return a new session that will receive packets or null if a problem occured while
     *         authenticating the remote server or when acting as the Authoritative Server during
     *         a Server Dialback authentication process.
     * @throws org.xmlpull.v1.XmlPullParserException if an error occurs while parsing the XML.
     * @throws java.io.IOException if an input/output error occurs while using the connection.
     */
    public static LocalIncomingServerSession createSession(String serverName, XmlPullParser xpp,
                                                           Connection connection, boolean directTLS, boolean doNotSendXMPPStream) throws XmlPullParserException, IOException {

        String version = xpp.getAttributeValue("", "version");
        String fromDomain = xpp.getAttributeValue("", "from");
        String toDomain = xpp.getAttributeValue("", "to");
        int[] serverVersion = version != null ? Session.decodeVersion(version) : new int[] {0,0};

        if (toDomain == null) {
            toDomain = serverName;
        }

        boolean hasCertificates = false;
        try {
            hasCertificates = !connection.getConfiguration().getIdentityStore().getAllCertificates().isEmpty();
        }
        catch (Exception e) {
            Log.error("Unable to find any content in the identity store. This connection won't be able to support TLS.", e);
        }

        if (!hasCertificates && connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required) {
            Log.error("Server session rejected. TLS is required but no certificates " +
                "were created.");
            return null;
        }

        // Retrieve list of namespaces declared in current element (OF-2556)
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));

        try {
            // Get the stream ID for the new session
            StreamID streamID = SessionManager.getInstance().nextStreamID();
            // Create a server Session for the remote server
            LocalIncomingServerSession session = SessionManager.getInstance().createIncomingServerSession(connection, streamID, fromDomain);
            Log.debug("Creating new session with stream ID '{}' for local '{}' to peer '{}'.", streamID, toDomain, fromDomain);

            if (doNotSendXMPPStream) {
                session.setLocalDomain(serverName);
                return session;
            }

            // Send the stream header
            final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
            final Document document = DocumentHelper.createDocument(stream);
            document.setXMLEncoding(StandardCharsets.UTF_8.toString());
            stream.add(Namespace.get("", "jabber:server"));
            if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
                stream.add(Namespace.get("db", "jabber:server:dialback"));
            }
            stream.addAttribute("from", toDomain);
            if (fromDomain != null) {
                stream.addAttribute("to", fromDomain);
            }
            stream.addAttribute("id", streamID.getID());

            // OF-443: Not responding with a 1.0 version in the stream header when federating with older
            // implementations appears to reduce connection issues with those domains (patch by Marcin Cieślak).
            if (serverVersion[0] >= 1) {
                stream.addAttribute("version", "1.0");
            }

            if (serverVersion[0] >= 1) {
                Log.trace("Remote server is XMPP 1.0 compliant so offer TLS and SASL to establish the connection (and server dialback)");

                final Element features = DocumentHelper.createElement(QName.get("features", "stream", "http://etherx.jabber.org/streams"));
                stream.add(features);

                if (!directTLS
                    && (connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required || connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.optional)
                    && !connection.getConfiguration().getIdentityStore().getAllCertificates().isEmpty()
                ) {
                    final Element starttls = DocumentHelper.createElement(QName.get("starttls", "urn:ietf:params:xml:ns:xmpp-tls"));
                    if (connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required) {
                        starttls.addElement("required");
                    } else if (!ServerDialback.isEnabled()) {
                        Log.debug("Server dialback is disabled so TLS is required");
                        starttls.addElement("required");
                    }
                    features.add(starttls);
                }

                // Include available SASL Mechanisms
                features.add(SASLAuthentication.getSASLMechanisms(session));

                if (ServerDialback.isEnabled()) {
                    // Also offer server dialback (when TLS is not required). Server dialback may be offered
                    // after TLS has been negotiated and a self-signed certificate is being used
                    final Element dialback = DocumentHelper.createElement(QName.get("dialback", "urn:xmpp:features:dialback"));
                    dialback.addElement("errors");
                    features.add(dialback);
                }

                if (!ConnectionSettings.Server.STREAM_LIMITS_ADVERTISEMENT_DISABLED.getValue()) {
                    final Element limits = DocumentHelper.createElement(QName.get("limits", "urn:xmpp:stream-limits:0"));
                    limits.addElement("max-bytes").addText(String.valueOf(XMLLightweightParser.XMPP_PARSER_BUFFER_SIZE.getValue()));
                    final Duration timeout = ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY.getValue();
                    if (!timeout.isNegative() && !timeout.isZero()) {
                        limits.addElement("idle-seconds").addText(String.valueOf(timeout.toSeconds()));
                    }
                    features.add(limits);
                }
            } else {
                Log.debug("Don't offer stream-features to pre-1.0 servers, as it confuses them. Sending features to Openfire < 3.7.1 confuses it too - OF-443)");
            }

            final String withoutClosing = StringUtils.asUnclosedStream(document);

            Log.trace("Outbound stream & feature advertisement: {}", withoutClosing);
            connection.deliverRawText(withoutClosing);

            Log.trace("Set the domain or subdomain of the local server targeted by the remote server: {}", serverName);
            session.setLocalDomain(serverName);
            return session;
        }
        catch (Exception e) {
            Log.error("Error establishing connection from remote server: {}", connection, e);
            connection.close(new StreamError(StreamError.Condition.internal_server_error));
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
        final DomainPair domainPair = new DomainPair(getServerName(), fromDomain);
        ServerDialback method = new ServerDialback(getConnection(), domainPair);
        try {
            method.validateRemoteDomain(dbResult, getStreamID());

            final String recipient = dbResult.attributeValue("to");
            final String remoteDomain = dbResult.attributeValue("from");

            // Add the validated domain as a valid domain. Do this before notifying the remote domain of success! (OF-2626)
            setAuthenticationMethod(AuthenticationMethod.DIALBACK);
            addValidatedDomain(remoteDomain);

            // Report success to the peer.
            final Namespace ns = Namespace.get("db", "jabber:server:dialback");
            final Document outbound = DocumentHelper.createDocument();
            final Element root = outbound.addElement("root");
            root.add(ns);
            final Element result = root.addElement(QName.get("result", ns));
            result.addAttribute("from", recipient);
            result.addAttribute("to", remoteDomain);
            result.addAttribute("type", "valid");

            // The namespace was already defined in a parent element that was sent earlier. Strip it from the XML.
            final String send = result.asXML().replaceAll(ns.asXML(), "").replace("  "," ");
            getConnection().deliverRawText(send);

            return true;
        } catch (StreamErrorException e) {
            Log.info("Unable to validate domain '{}' (full stack trace is logged on debug level): {}", fromDomain, e.getStreamError().getText());
            Log.debug("Unable to validate domain '{}'", fromDomain, e);
            getConnection().deliverRawText(e.getStreamError().toXML());

            // Close the underlying connection
            getConnection().close();
        } catch (ServerDialbackErrorException e) {
            Log.debug( "Unable to validate domain '{}': (full stack trace is logged on debug level): {}", fromDomain, e.getError().getText());
            Log.debug("Unable to validate domain '{}'", fromDomain, e);

            // The namespace was already defined in a parent element that was sent earlier. Strip it from the XML.
            final Namespace ns = Namespace.get("db", "jabber:server:dialback");
            final String send = e.toXML().asXML().replaceAll(ns.asXML(), "").replace("  "," ");
            getConnection().deliverRawText(send);
        } catch (ServerDialbackKeyInvalidException e) {
            Log.debug( "Dialback key is invalid. Sending verification result to remote domain." );

            // The namespace was already defined in a parent element that was sent earlier. Strip it from the XML.
            final Namespace ns = Namespace.get("db", "jabber:server:dialback");
            final String send = e.toXML().asXML().replaceAll(ns.asXML(), "").replace("  "," ");
            getConnection().deliverRawText(send);
            Log.debug( "Close the underlying connection as key verification failed." );
            getConnection().close();
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
            setStatus(Status.AUTHENTICATED);
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
    public List<Element> getAvailableStreamFeatures()
    {
        final List<Element> result = new LinkedList<>();

        // Include Stream Compression Mechanism
        if (conn.getConfiguration().getCompressionPolicy() != Connection.CompressionPolicy.disabled && !conn.isCompressed()) {
            final Element compression = DocumentHelper.createElement(QName.get("compression", "http://jabber.org/features/compress"));
            compression.addElement("method").addText("zlib");
            result.add(compression);
        }
        
        // Offer server dialback if using self-signed certificates and no authentication has been done yet
        boolean usingSelfSigned;
        final Certificate[] chain = conn.getLocalCertificates();
        if (chain == null || chain.length == 0) {
            usingSelfSigned = true;
        } else {
            usingSelfSigned = CertificateManager.isSelfSignedCertificate((X509Certificate) chain[0]);
        }
        
        if (usingSelfSigned && ServerDialback.isEnabledForSelfSigned() && validatedDomains.isEmpty()) {
            final Element dialback = DocumentHelper.createElement(QName.get("dialback", "urn:xmpp:features:dialback"));
            dialback.addElement("errors");
            result.add(dialback);
        }

        if (!ConnectionSettings.Server.STREAM_LIMITS_ADVERTISEMENT_DISABLED.getValue()) {
            final Element limits = DocumentHelper.createElement(QName.get("limits", "urn:xmpp:stream-limits:0"));
            limits.addElement("max-bytes").addText(String.valueOf(XMLLightweightParser.XMPP_PARSER_BUFFER_SIZE.getValue()));
            final Duration timeout = ConnectionSettings.Server.IDLE_TIMEOUT_PROPERTY.getValue();
            if (!timeout.isNegative() && !timeout.isZero()) {
                limits.addElement("idle-seconds").addText(String.valueOf(timeout.toSeconds()));
            }
            result.add(limits);
        }
        return result;
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
            ", authenticationMethod=" + authenticationMethod +
            ", localDomain=" + localDomain +
            ", defaultIdentity=" + fromDomain +
            ", validatedDomains=" + validatedDomains.stream().collect( Collectors.joining( ", ", "{", "}")) +
            '}';
    }
}
