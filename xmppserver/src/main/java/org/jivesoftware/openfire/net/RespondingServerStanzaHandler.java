/*
 * Copyright (C) 2005-2008 Jive Software, 2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
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

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RespondingServerStanzaHandler extends StanzaHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RespondingServerStanzaHandler.class);
    private final DomainPair domainPair;

    private boolean isSessionAuthenticated = false;

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

    @Override
    protected boolean isStartOfStream(String xml) {
        final boolean isStartOfStream = super.isStartOfStream(xml);
        if (isStartOfStream) {
            // We initiate the stream for a RespondingServerStanzaHandler, so we need to add the stream namespace
            // Pull namespaces off of the stream:stream stanza and add them to the additional
            List<Namespace> receivedNamespaces = null;
            try {
                receivedNamespaces = DocumentHelper.parseText(xml + "</stream:stream>").getRootElement().declaredNamespaces();
                Set<Namespace> additionalNamespaces = receivedNamespaces
                    .stream()
                    .filter(RespondingServerStanzaHandler::isRelevantNamespace)
                    .collect(Collectors.toSet());
                connection.setAdditionalNamespaces(additionalNamespaces);
            } catch (DocumentException e) {
                LOG.error("Failed extract additional namespaces", e);
            }
        }

        return isStartOfStream;
    }

    @Override
    protected void initiateSession(String stanza, XMPPPacketReader reader) throws Exception {
        if (!sessionCreated) {
            super.initiateSession(stanza, reader);
        }
    }

    private static boolean isRelevantNamespace(Namespace ns) {
        return !XMPPPacketReader.IGNORED_NAMESPACE_ON_STANZA.contains(ns.getURI());
    }

    @Override
    boolean processUnknowPacket(Element doc) throws UnauthorizedException {
        String rootTagName = doc.getName();

        // Handle features
        if ("features".equals(rootTagName)) {
            LOG.debug("Check if both us as well as the remote server have enabled STARTTLS and/or dialback ...");

            // Encryption ------
            if (shouldUseTls() && remoteFeaturesContainsStartTLS(doc)) {
                LOG.debug("Both us and the remote server support the STARTTLS feature. Encrypt and authenticate the connection with TLS & SASL...");
                LOG.debug("Indicating we want TLS and wait for response.");
                connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                startedTLS = true;
                return true;
            } else if (mustUseTls() && !connection.isEncrypted()) {
                LOG.debug("I MUST use TLS but I have no StartTLS in features.");
                return false;
            }

            // Authentication ------
            final boolean saslExternalOffered = isSaslExternalOfferred(doc);
            final boolean dialbackOffered = isDialbackOffered(doc);
            LOG.debug("Remote server is offering dialback: {}, EXTERNAL SASL: {}", dialbackOffered, saslExternalOffered);

            // First, try SASL
            if (saslExternalOffered) {
                LOG.debug("Trying to authenticate with EXTERNAL SASL.");
                LOG.debug("Starting EXTERNAL SASL for: " + domainPair);

                StringBuilder sb = new StringBuilder();
                sb.append("<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"EXTERNAL\">");
                sb.append(StringUtils.encodeBase64(domainPair.getLocal()));
                sb.append("</auth>");
                connection.deliverRawText(sb.toString());
                startedSASL = true;
                return true;
            } else if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
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
                isSessionAuthenticated = true;
                // Set the remote domain name as the address of the session.
                session.setAddress(new JID(null, domainPair.getRemote(), null));
                if (session instanceof LocalOutgoingServerSession) {
                    ((LocalOutgoingServerSession) session).setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
                }

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
            StringBuilder sb = new StringBuilder();
            sb.append("<stream:stream");
            sb.append(" xmlns:db=\"jabber:server:dialback\"");
            sb.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            sb.append(" xmlns=\"jabber:server\"");
            sb.append(" from=\"").append(domainPair.getLocal()).append("\""); // OF-673
            sb.append(" to=\"").append(domainPair.getRemote()).append("\"");
            sb.append(" version=\"1.0\">");
            connection.deliverRawText(sb.toString());

            connection.init(session);
            isSessionAuthenticated = true;
            // Set the remote domain name as the address of the session.
            session.setAddress(new JID(null, domainPair.getRemote(), null));
            if (session instanceof LocalOutgoingServerSession) {
                ((LocalOutgoingServerSession) session).setAuthenticationMethod(ServerSession.AuthenticationMethod.SASL_EXTERNAL);
            } else {
                LOG.debug("Expected session to be a LocalOutgoingServerSession but it isn't, unable to setAuthenticationMethod().");
                return false;
            }
            return true;
        }

        // Handles proceed (prior to TLS negotiation)
        if (rootTagName.equals("proceed")) {
            LOG.debug("Received 'proceed' from remote server. Negotiating TLS...");
            try {
                LOG.debug("Encrypting and authenticating connection ...");
                connection.startTLS(true, false);
            } catch (Exception e) {
                LOG.debug("TLS negotiation failed: " + e.getMessage());
                return false;
            }
            LOG.debug("TLS negotiation was successful. Connection encrypted. Proceeding with authentication...");

            // Verify - TODO does this live here, should we do this when handling features mechanisms?
            if (!SASLAuthentication.verifyCertificates(connection.getPeerCertificates(), domainPair.getRemote(), true)) {
                if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {
                    LOG.debug("Failed to verify certificates for SASL authentication. Will continue with dialback.");
                    // Will continue with dialback when the features stanza comes in and is processed (above)
                } else {
                    LOG.warn("Unable to authenticate the connection: Failed to verify certificates for SASL authentication and dialback is not available.");
                    return false;
                }
            }

            LOG.debug("TLS negotiation was successful so initiate a new stream.");
            StringBuilder sb = new StringBuilder();
            sb.append("<stream:stream");
            sb.append(" xmlns:db=\"jabber:server:dialback\"");
            sb.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            sb.append(" xmlns=\"jabber:server\"");
            sb.append(" from=\"").append(domainPair.getLocal()).append("\""); // OF-673
            sb.append(" to=\"").append(domainPair.getRemote()).append("\"");
            sb.append(" version=\"1.0\">");
            connection.deliverRawText(sb.toString());

            return true;
        }

        return false;
    }

    private boolean shouldUseTls() {
        return connection.getTlsPolicy() == Connection.TLSPolicy.optional || connection.getTlsPolicy() == Connection.TLSPolicy.required;
    }

    private boolean mustUseTls() {
        return connection.getTlsPolicy() == Connection.TLSPolicy.required;
    }

    @Override
    void startTLS() throws Exception {
        connection.startTLS(true, false);
    }

    @Override
    String getNamespace() {
        return "jabber:server";
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

    public boolean isSessionAuthenticated() {
        return isSessionAuthenticated;
    }

    @Override
    void createSession(String serverName, XmlPullParser xpp, Connection connection) throws XmlPullParserException {
        String currentStreamId = xpp.getAttributeValue("", "id");
        session = new LocalOutgoingServerSession(domainPair.getLocal(), connection, BasicStreamIDFactory.createStreamID(currentStreamId));
    }
}
