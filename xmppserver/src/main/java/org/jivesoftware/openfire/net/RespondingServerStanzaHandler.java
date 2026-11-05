/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.nio.NettyConnection;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Stanza handler for responding to incoming stanzas when the server is acting as the client in an S2S scenario.
 *
 * This handler records each negotiation step (the stream, STARTTLS and authentication) on the attempt's
 * {@link org.jivesoftware.openfire.server.diagnostics.AttemptRecorder} where the step is decided, through
 * {@code record(...)}. A new step or failure path should record its outcome there too, or the S2S connection test and
 * the failed-session page will not show it. See ADR-007.
 *
 * @author Alex Gidman
 * @author Matthew Vivian
 */
public class RespondingServerStanzaHandler extends StanzaHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RespondingServerStanzaHandler.class);
    private static final String TLS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-tls";
    private static final String STREAMS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-streams";

    private final DomainPair domainPair;
    private final CompletableFuture<Void> isSessionAuthenticated = new CompletableFuture<>();
    private final CompletableFuture<Void> attemptedAllAuthenticationMethods = new CompletableFuture<>();

    /**
     * How a Server Dialback attempt is recorded for the connection test if it succeeds, which says why dialback was
     * used; null when the reason is not known.
     */
    private AttemptMilestones.Authentication dialbackOnSuccess;

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

    private static boolean remoteFeaturesRequiresStartTLS(Element doc) {
        return remoteFeaturesContainsStartTLS(doc) && doc.element("starttls").element("required") != null;
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
            // Capture the prefixed namespaces declared on the peer's stream header so that prefixed content in
            // stanzas can be parsed later (OF-2556), using the same pull-parser extraction as every other session type.
            try {
                final MXParser parser = reader.getXPPParser();
                parser.setInput(new StringReader(stanza));
                for (int eventType = parser.getEventType(); eventType != XmlPullParser.START_TAG; ) {
                    eventType = parser.next();
                }
                connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(parser));

                final String streamHeaderId = parser.getAttributeValue("", "id");
                if (streamHeaderId == null || streamHeaderId.isBlank()) { // OF-2692: the peer is required to send a Stream ID. Some servers do not, when they are sending a stream error.
                    LOG.info("Closing connection {}. As the initiating party in a server-to-server connection, we require the receiving party to supply a stream ID value. The peer that sent this stream element did not: {}", connection, stanza);
                    if (!sessionCreated) {
                        record(recorder -> recorder.stream(AttemptMilestones.Stream.missingId()));
                    }
                    connection.close(new StreamError(StreamError.Condition.invalid_xml, "Expected a stream ID value, but none was received."));
                    return;
                }

                if (!sessionCreated) {
                    // The first stream header from the remote: later ones restart the stream after TLS or SASL.
                    final String remoteName = parser.getAttributeValue("", "from");
                    record(recorder -> recorder.stream(AttemptMilestones.Stream.opened(remoteName == null ? null : AttemptMilestones.remoteText(remoteName))));
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
            } catch (XmlPullParserException | IOException e) {
                LOG.error("Failed to extract namespaces and stream ID from the peer's stream header", e);
                record(AttemptRecorder::loggedError);
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
                LOG.debug("Both us and the remote domain '{}' support the STARTTLS feature. Encrypt and authenticate the connection with TLS & SASL...", domainPair.getRemote());
                LOG.debug("Indicating we want TLS and wait for response.");
                connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");
                startedTLS = true;
                return true;
            } else if (mustUseTls() && !connection.isEncrypted()) {
                LOG.debug("I MUST use TLS but I have no StartTLS in features from remote domain '{}'", domainPair.getRemote());
                record(recorder -> recorder.tls(AttemptMilestones.Tls.notOffered()));
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "Encryption (TLS) required by our server configuration, but not offered by you."));
                return true;
            } else if (cannotUseTls() && remoteFeaturesRequiresStartTLS(doc)) {
                LOG.debug("I CANNOT use TLS but remote domain '{}' requires the STARTTLS feature.", domainPair.getRemote());
                record(recorder -> recorder.tls(AttemptMilestones.Tls.requiredButDisabled()));
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "Encryption (TLS) disabled by our server configuration, but required by you."));
                return true;
            }

            if (!connection.isEncrypted()) {
                // Authentication goes ahead on a connection that TLS was not set up on.
                record(recorder -> recorder.tls(AttemptMilestones.Tls.unencrypted()));
            }

            // Authentication ------
            LOG.debug("Check if both us as well as the remote domain '{}' have enabled STARTTLS and/or dialback ...", domainPair.getRemote());
            final boolean saslExternalOffered = isSaslExternalOfferred(doc);
            final boolean dialbackOffered = isDialbackOffered(doc);
            LOG.debug("Remote server is offering dialback: {}, EXTERNAL SASL: {}", dialbackOffered, saslExternalOffered);
            record(AttemptRecorder::authenticationStarted);

            // First, try SASL
            if (saslExternalOffered) {
                LOG.debug("Trying to authenticate with EXTERNAL SASL.");
                final Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"));
                auth.addAttribute("mechanism", "EXTERNAL");
                // XMPP does not _require_ an authzid to be sent (see RFC-6120, section 6.3.8). XEP-0178 suggests doing so for backwards compatibility.
                if (SASLAuthentication.EXTERNAL_S2S_SKIP_SENDING_AUTHZID.getValue()) {
                    auth.addText("=");
                } else {
                    auth.addText(Base64.getEncoder().encodeToString(domainPair.getLocal().getBytes(StandardCharsets.UTF_8)));
                }
                connection.deliverRawText(auth.asXML());
                startedSASL = true;
            } else if (dialbackOffered && (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned())) {
                // Next, try dialback
                dialbackOnSuccess = AttemptMilestones.Authentication.dialbackAfterExternalNotOffered();
                LOG.debug("Trying to authenticate using dialback.");
                LOG.debug("[Acting as Originating Server: Authenticate domain: " + domainPair.getLocal() + " with a RS in the domain of: " + domainPair.getRemote() + " (id: " + session.getStreamID() + ")]");
                ServerDialback dialback = new ServerDialback(connection, domainPair);
                dialback.createAndSendDialbackKey(session.getStreamID().getID());
            } else {
                LOG.debug("No authentication mechanism available.");
                record(recorder -> recorder.authentication(AttemptMilestones.Authentication.noCommonMechanism()));
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "No applicable authentication mechanism available."));
            }
            return true;
        }

        // Handle dialback result
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(rootTagName)) {
            LOG.debug("Received dialback result from remote domain '{}'", domainPair.getRemote());
            if ("valid".equals(doc.attributeValue("type"))) {
                LOG.debug("Authentication succeeded!");
                LOG.debug("Dialback was successful.");
                final AttemptMilestones.Authentication succeeded = dialbackOnSuccess == null ? AttemptMilestones.Authentication.dialback() : dialbackOnSuccess;
                record(recorder -> recorder.authentication(succeeded));

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
                record(recorder -> recorder.authentication(AttemptMilestones.Authentication.dialbackRefused()));

                session.markNonResumable();
                session.close();
                return true;
            }
        }

        // Handles SASL failure
        if ("failure".equals(rootTagName)) {
            final boolean startTlsRefused = TLS_NAMESPACE.equals(doc.getNamespaceURI());
            if (startTlsRefused) {
                // The remote refused the STARTTLS request. Handled as before, below; only recorded here.
                record(recorder -> recorder.tls(AttemptMilestones.Tls.refused()));
            }
            LOG.debug("Remote domain '{}' reports that EXTERNAL SASL failed.", domainPair.getRemote());

            // Try dialback
            final boolean dialbackEnabled = ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned();
            if (!startTlsRefused) {
                if (dialbackEnabled) {
                    dialbackOnSuccess = AttemptMilestones.Authentication.dialbackAfterExternalRefused();
                } else {
                    record(recorder -> recorder.authentication(AttemptMilestones.Authentication.externalRefusedDialbackDisabled()));
                }
            }
            if (dialbackEnabled) {

                LOG.debug("Trying to authenticate using dialback.");
                LOG.debug("[Acting as Originating Server: Authenticate domain: " + domainPair.getLocal() + " with a RS in the domain of: " + domainPair.getRemote() + " (id: " + session.getStreamID() + ")]");
                ServerDialback dialback = new ServerDialback(connection, domainPair);
                dialback.createAndSendDialbackKey(session.getStreamID().getID());
                return true;
            }

            session.markNonResumable();
            session.close();
            return true;
        }

        // Handles SASL success
        if ("success".equals(rootTagName)) {
            LOG.debug("Remote domain '{}' reports that EXTERNAL SASL was successful.", domainPair.getRemote());

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
                record(recorder -> recorder.authentication(AttemptMilestones.Authentication.saslExternal()));
            } else {
                LOG.warn("Expected session with remote domain '{}' to be a LocalOutgoingServerSession but it isn't, unable to setAuthenticationMethod(). Session: {}", domainPair.getRemote(), session);
                record(AttemptRecorder::loggedWarning);
                connection.close(new StreamError(StreamError.Condition.internal_server_error, "Unexpected session type."));
                return true;
            }

            // Make sure to set 'authenticated' only after the internal state of 'session' itself is updated, to avoid race conditions.
            isSessionAuthenticated.complete(null);
            return true;
        }

        // Handles proceed (prior to TLS negotiation)
        if (rootTagName.equals("proceed")) {
            LOG.debug("Received 'proceed' from remote domain '{}'. Negotiating TLS...", domainPair.getRemote());

            try {
                LOG.debug("Encrypting and authenticating connection ...");
                connection.startTLS(true, false);
            } catch (Exception e) {
                LOG.debug("An error occurred while encrypting the connection.", e);
                session.markNonResumable();
                session.close();
                return true;
            }
            return true;
        }

        return false;
    }

    @Override
    protected void streamErrorReceived(Element error) {
        record(recorder -> recorder.remoteStreamError(describeStreamError(error)));
    }

    /**
     * Describes a stream error as its condition, followed by its text when the remote sent one. The text is chosen by the
     * remote server, so it is cut to a length that a results page can show.
     *
     * @param error a stream error element.
     * @return the description.
     */
    static String describeStreamError(Element error) {
        String condition = null;
        String text = null;
        for (final Element child : error.elements()) {
            if ("text".equals(child.getName())) {
                text = child.getTextTrim();
            } else if (condition == null && STREAMS_NAMESPACE.equals(child.getNamespaceURI())) {
                condition = child.getName();
            }
        }
        final StringBuilder description = new StringBuilder(condition == null ? "undefined-condition" : condition);
        if (text != null && !text.isEmpty()) {
            description.append(": ").append(text);
        }
        return AttemptMilestones.remoteText(description.toString());
    }

    /**
     * @return the recorder of the outbound connection attempt that this stream belongs to, if any.
     */
    private Optional<AttemptRecorder> attemptRecorder() {
        return connection instanceof NettyConnection nettyConnection ? nettyConnection.getAttemptRecorder() : Optional.empty();
    }

    /**
     * Records a milestone of the outbound connection attempt that this stream belongs to, if any. A recording that
     * fails never changes how the connection proceeds.
     */
    private void record(final Consumer<AttemptRecorder> recording) {
        attemptRecorder().ifPresent(recorder -> AttemptRecorder.safely(() -> recording.accept(recorder)));
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

    private boolean cannotUseTls() {
        return connection.getConfiguration().getTlsPolicy() == Connection.TLSPolicy.disabled;
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
        try {
            session = createLocalOutgoingServerSession(currentStreamId,  connection);
        } catch (final RuntimeException e) {
            LOG.warn("createLocalOutgoingServerSession() threw for connection {}. session remains: {}.", connection, session, e);
            record(AttemptRecorder::loggedWarning);
            throw e;
        }
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
