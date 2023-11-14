/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Stanza handler for responding to incoming stanzas when the server is acting as the client in an S2S scenario.
 *
 * @author Alex Gidman
 * @author Matthew Vivian
 */
public class RespondingServerStanzaHandler extends StanzaHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RespondingServerStanzaHandler.class);
    private final DomainPair domainPair;
    private final CompletableFuture<Void> isSessionAuthenticated = new CompletableFuture<>();
    private final CompletableFuture<Void> attemptedAllAuthenticationMethods = new CompletableFuture<>();

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router     the router for sending packets that were read.
     * @param connection the connection being read.
     * @param domainPair the local and remote domains
     */
    public RespondingServerStanzaHandler(PacketRouter router, Connection connection, DomainPair domainPair) {
        super(router, connection);
        this.domainPair = domainPair;
    }

    private static boolean remoteFeaturesContainsStartTLS(Element doc) {
        return doc.element("starttls") != null;
    }

    private static boolean isSaslExternalOfferred(Element doc) {
        boolean saslEXTERNALoffered = false;
        if (doc.element("mechanisms") != null) {
            Iterator<Element> it = doc.element("mechanisms").elementIterator();
            while (it.hasNext()) {
                Element mechanism = it.next();
                if ("EXTERNAL".equals(mechanism.getTextTrim())) {
                    saslEXTERNALoffered = true;
                    break;
                }
            }
        }
        return saslEXTERNALoffered;
    }

    private static boolean isDialbackOffered(Element doc) {
        return doc.element("dialback") != null;
    }


    /**
     * Transfer an existing connection to a new session when a new streamID is detected. Also transfers existing
     * authentication method if previously set.
     * @param newStreamId new stream ID for the new session
     * @param existingAuthMethod authentication method used previously (possibly null)
     */
    private void transferConnectionToNewSession(String newStreamId, ServerSession.AuthenticationMethod existingAuthMethod) {
        session = createLocalOutgoingServerSession(newStreamId, connection);

        // Transfer new session to existing connection
        connection.reinit(session);
        if (isSessionAuthenticated.isDone()) {
            ((LocalOutgoingServerSession) session).addOutgoingDomainPair(domainPair);
            ((LocalOutgoingServerSession) session).setAuthenticationMethod(existingAuthMethod);
        } else {
            LOG.debug("Session not authenticated yet, unable to setAuthenticationMethod().");
        }
    }

    @Override
    protected void initiateSession(String stanza, XMPPPacketReader reader) throws Exception {
        boolean startOfStream = isStartOfStream(stanza);

        if (startOfStream) {
            // We initiate the stream for a RespondingServerStanzaHandler, so we need to add the stream namespace
            // Pull namespaces off of the stream:stream stanza and add them to the additional
            List<Namespace> receivedNamespaces;
            try {
                Element rootElement = DocumentHelper.parseText(stanza + "</stream:stream>").getRootElement();
                receivedNamespaces = rootElement.declaredNamespaces();
                Set<Namespace> additionalNamespaces = receivedNamespaces
                    .stream()
                    .filter(RespondingServerStanzaHandler::isRelevantNamespace)
                    .collect(Collectors.toSet());
                connection.setAdditionalNamespaces(additionalNamespaces);

                final String streamHeaderId = rootElement.attributeValue("id");
                if (streamHeaderId == null || streamHeaderId.isBlank()) { // OF-2692: the peer is required to send a Stream ID. Some servers do not, when they are sending a stream error.
                    LOG.info("Closing connection {}. As the initiating party in a server-to-server connection, we require the receiving party to supply a stream ID value. The peer that sent this stream element did not: {}", connection, stanza);
                    connection.close(new StreamError(StreamError.Condition.invalid_xml, "Expected a stream ID value, but none was received."));
                    return;
                }

                // Create a new session with a new ID if a new stream has started on an existing connection
                // following TLS negotiation in accordance with RFC 6120 § 5.4.3.3. See https://datatracker.ietf.org/doc/html/rfc6120#section-5.4.3.3
                if (sessionCreated && isNewStreamId(streamHeaderId)) {

                    LocalOutgoingServerSession localOutgoingServerSession = session instanceof LocalOutgoingServerSession ? (LocalOutgoingServerSession) session : null;
                    ServerSession.AuthenticationMethod existingAuthMethod = localOutgoingServerSession != null
                        ? localOutgoingServerSession.getAuthenticationMethod()
                        : null;
                    transferConnectionToNewSession(streamHeaderId, existingAuthMethod);
                }
            } catch (DocumentException e) {
                LOG.error("Failed extract additional namespaces", e);
            }
        }

        if (!startOfStream) {
            // Ignore <?xml version="1.0"?>
            return;
        }

        if (!sessionCreated) {
            sessionCreated = true;
            MXParser parser = reader.getXPPParser();
            parser.setInput(new StringReader(stanza));
            createSession(parser);
        }
    }

    private boolean isNewStreamId(String streamHeaderId) {
        return !streamHeaderId.equals(session.getStreamID().getID());
    }

    private static boolean isRelevantNamespace(Namespace ns) {
        return !XMPPPacketReader.IGNORED_NAMESPACE_ON_STANZA.contains(ns.getURI());
    }

    @Override
    boolean processUnknowPacket(Element doc) {
        String rootTagName = doc.getName();

        // Handle features
        if ("features".equals(rootTagName)) {

            // Prevent falling back to dialback if we are already authenticated
            if (session.isAuthenticated()) {
                return true;
            }

            // Encryption ------
            if (shouldUseTls() && remoteFeaturesContainsStartTLS(doc)) {
                LOG.debug("Both us and the remote server support the STARTTLS feature. Encrypt and authenticate the connection with TLS & SASL...");
                LOG.debug("Indicating we want TLS and wait for response.");
                connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                startedTLS = true;
                return true;
            } else if (mustUseTls() && !connection.isEncrypted()) {
                LOG.debug("I MUST use TLS but I have no StartTLS in features.");
                abandonSessionInitiation();
                return false;
            }

            // Authentication ------
            LOG.debug("Check if both us as well as the remote server have enabled STARTTLS and/or dialback ...");
            final boolean saslExternalOffered = isSaslExternalOfferred(doc);
            final boolean dialbackOffered = isDialbackOffered(doc);
            LOG.debug("Remote server is offering dialback: {}, EXTERNAL SASL: {}", dialbackOffered, saslExternalOffered);

            // First, try SASL
            if (saslExternalOffered) {
                LOG.debug("Trying to authenticate with EXTERNAL SASL.");
                LOG.debug("Starting EXTERNAL SASL for: " + domainPair);

                final Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"));
                auth.addAttribute("mechanism", "EXTERNAL");
                // XMPP does not _require_ an authzid to be sent (see RFC-6120, section 6.3.8). XEP-0178 suggests doing so for backwards compatibility.
                if (SASLAuthentication.EXTERNAL_S2S_SKIP_SENDING_AUTHZID.getValue()) {
                    auth.addText("=");
                } else {
                    auth.addText(StringUtils.encodeBase64(domainPair.getLocal()));
                }
                connection.deliverRawText(auth.asXML());
                startedSASL = true;
                return true;
            } else if (dialbackOffered && (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned())) {
                // Next, try dialback
                LOG.debug("Trying to authenticate using dialback.");
                LOG.debug("[Acting as Originating Server: Authenticate domain: " + domainPair.getLocal() + " with a RS in the domain of: " + domainPair.getRemote() + " (id: " + session.getStreamID() + ")]");
                ServerDialback dialback = new ServerDialback(connection, domainPair);
                dialback.createAndSendDialbackKey(session.getStreamID().getID());
                return true;
            } else {
                LOG.debug("No authentication mechanism available.");
                return false;
            }
        }

        // Handle dialback result
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(rootTagName)) {
            if ("valid".equals(doc.attributeValue("type"))) {
                LOG.debug("Authentication succeeded!");
                LOG.debug("Dialback was successful.");

                connection.init(session);
                // Set the remote domain name as the address of the session.
                session.setAddress(new JID(null, domainPair.getRemote(), null));
                if (session instanceof LocalOutgoingServerSession) {
                    ((LocalOutgoingServerSession) session).setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
                }

                // Make sure to set 'authenticated' only after the internal state of 'session' itself is updated, to avoid race conditions.
                isSessionAuthenticated.complete(null);

                return true;
            } else {
                LOG.debug("Dialback failed");
                LOG.debug("Failed to authenticate domain: the validation response was received, but did not grant authentication.");
                return false;
            }
        }

        // Handles SASL failure
        if ("failure".equals(rootTagName)) {
            LOG.debug("EXTERNAL SASL failed.");

            // Try dialback
            if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {

                LOG.debug("Trying to authenticate using dialback.");
                LOG.debug("[Acting as Originating Server: Authenticate domain: " + domainPair.getLocal() + " with a RS in the domain of: " + domainPair.getRemote() + " (id: " + session.getStreamID() + ")]");
                ServerDialback dialback = new ServerDialback(connection, domainPair);
                dialback.createAndSendDialbackKey(session.getStreamID().getID());
                return true;
            }

            return false;
        }

        // Handles SASL success
        if ("success".equals(rootTagName)) {
            LOG.debug("EXTERNAL SASL was successful.");

            // SASL was successful so initiate a new stream
            final Element stream = DocumentHelper.createElement(QName.get("stream", "stream", "http://etherx.jabber.org/streams"));
            final Document document = DocumentHelper.createDocument(stream);
            document.setXMLEncoding(StandardCharsets.UTF_8.toString());
            stream.add(Namespace.get("jabber:server"));
            stream.addAttribute("from", domainPair.getLocal()); // OF-673
            stream.addAttribute("to", domainPair.getRemote());
            stream.addAttribute("version", "1.0");

            connection.deliverRawText(StringUtils.asUnclosedStream(document));

            connection.init(session);
            // Set the remote domain name as the address of the session.
            session.setAddress(new JID(null, domainPair.getRemote(), null));
            if (session instanceof LocalOutgoingServerSession) {
                ((LocalOutgoingServerSession) session).setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
            } else {
                LOG.warn("Expected session to be a LocalOutgoingServerSession but it isn't, unable to setAuthenticationMethod(). Session: {}", session);
                return false;
            }

            // Make sure to set 'authenticated' only after the internal state of 'session' itself is updated, to avoid race conditions.
            isSessionAuthenticated.complete(null);
            return true;
        }

        // Handles proceed (prior to TLS negotiation)
        if (rootTagName.equals("proceed")) {
            LOG.debug("Received 'proceed' from remote server. Negotiating TLS...");

            try {
                LOG.debug("Encrypting and authenticating connection ...");
                connection.startTLS(true, false);
            } catch (Exception e) {
                LOG.debug("TLS negotiation failed to start: " + e.getMessage());
                return false;
            }
            return true;
        }

        return false;
    }

    private void abandonSessionInitiation() {
        this.setSession(null);
        this.setAttemptedAllAuthenticationMethods();
    }

    private boolean shouldUseTls() {
        return connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.optional || connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required;
    }

    private boolean mustUseTls() {
        return connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.required;
    }

    @Override
    void startTLS() throws Exception {
        connection.startTLS(true, false);
    }

    @Override
    Namespace getNamespace() {
        return new Namespace("", "jabber:server");
    }

    @Override
    boolean validateHost() {
        return false;
    }

    @Override
    boolean validateJIDs() {
        return false;
    }

    public LocalSession getSession() {
        return session;
    }

    public CompletableFuture<Void> isSessionAuthenticated() {
        return isSessionAuthenticated;
    }

    @Override
    void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException {
        String currentStreamId = xpp.getAttributeValue("", "id");
        session = createLocalOutgoingServerSession(currentStreamId,  connection);
    }

    /**
     * Creates a LocalOutgoingServerSession
     *
     * @param streamId id taken from the responding servers latest stream tag
     * @param connection the connection between the servers
     * @return a LocalOutgoingServerSession
     */
    private LocalOutgoingServerSession createLocalOutgoingServerSession(String streamId, Connection connection) {
        return new LocalOutgoingServerSession(domainPair.getLocal(), connection, BasicStreamIDFactory.createStreamID(streamId));
    }

    public void setSessionAuthenticated() {
        this.isSessionAuthenticated.complete(null);
    }

    public CompletableFuture<Void> haveAttemptedAllAuthenticationMethods() {
        return attemptedAllAuthenticationMethods;
    }

    public void setAttemptedAllAuthenticationMethods() {
        this.attemptedAllAuthenticationMethods.complete(null);
    }

    public String getRemoteDomain() {
        return domainPair.getRemote();
    }
}
