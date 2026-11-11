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
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ServerSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.RequestingStreamManager;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.StreamError;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * Set when the remote server reports that SASL EXTERNAL succeeded. Authentication is reported as completed only
     * when the remote server has restarted the stream, so that it is reported for the session of that stream (OF-3447).
     */
    private volatile boolean saslExternalSucceeded = false;

    /**
     * The namespaces of the versions of Stream Management that the remote server offered in the stream features that
     * were received last, the most recent version first. Empty if it did not offer Stream Management.
     */
    @Nonnull
    private List<String> offeredStreamManagementNamespaces = List.of();

    /**
     * The detached session of which the stream is to be resumed on this connection, instead of enabling Stream
     * Management on it, or null for a connection that is not used to resume a stream.
     */
    @Nullable
    private final LocalOutgoingServerSession resumeTarget;

    /**
     * Guards the decision to ask the remote server to resume the stream of {@link #resumeTarget}, and giving up on that.
     */
    private final Object resumeMutex = new Object();

    /**
     * Whether Openfire gave up on resuming the stream of {@link #resumeTarget} on this connection (see
     * {@link #abandonResume()}). Guarded by {@link #resumeMutex}.
     */
    private boolean resumeAbandoned = false;

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router     the router for sending packets that were read.
     * @param connection the connection being read.
     * @param domainPair the local and remote domains
     */
    public RespondingServerStanzaHandler(PacketRouter router, Connection connection, DomainPair domainPair) {
        this(router, connection, domainPair, null);
    }

    /**
     * Creates a dedicated reader for a socket, of a connection that can be used to resume the stream of a detached
     * session. The session of this connection is not made a route: when the stream is resumed, the connection is
     * attached to the detached session.
     *
     * @param router       the router for sending packets that were read.
     * @param connection   the connection being read.
     * @param domainPair   the local and remote domains
     * @param resumeTarget the detached session of which the stream is to be resumed on this connection, instead of
     *                     enabling Stream Management on it, or null when no stream is to be resumed.
     */
    public RespondingServerStanzaHandler(PacketRouter router, Connection connection, DomainPair domainPair, @Nullable LocalOutgoingServerSession resumeTarget) {
        super(router, connection);
        this.domainPair = domainPair;
        this.resumeTarget = resumeTarget;
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
     * Returns the namespaces of the versions of Stream Management offered in stream features, the most recent version
     * first.
     *
     * @param doc the stream features.
     * @return the namespaces of the offered versions, empty if Stream Management is not offered.
     */
    @Nonnull
    private static List<String> getOfferedStreamManagementNamespaces(Element doc) {
        final List<String> result = new ArrayList<>();
        if (doc.element(QName.get("sm", StreamManager.NAMESPACE_V3)) != null) {
            result.add(StreamManager.NAMESPACE_V3);
        }
        if (doc.element(QName.get("sm", StreamManager.NAMESPACE_V2)) != null) {
            result.add(StreamManager.NAMESPACE_V2);
        }
        return List.copyOf(result);
    }

    /**
     * Asks the remote server to enable Stream Management on the session, if Stream Management is enabled for servers
     * and the remote server offered it in the stream features that were received last. Otherwise, records on the
     * session that it is not asked, which finishes negotiation. This must only be invoked once the session is
     * authenticated.
     *
     * On a connection that is used to resume the stream of a detached session, the remote server is asked to resume
     * that stream instead (see {@link org.jivesoftware.openfire.streammanagement.RequestingStreamManager#requestResume}).
     */
    private void requestStreamManagementIfOffered() {
        if (!(session instanceof LocalOutgoingServerSession outgoingSession)) {
            LOG.debug("Not asking remote domain '{}' to enable Stream Management, as session {} is not an outgoing server session.", domainPair.getRemote(), session);
            return;
        }
        if (resumeTarget != null) {
            synchronized (resumeMutex) {
                if (resumeAbandoned) {
                    LOG.debug("Not asking remote domain '{}' to resume the stream of session {} on session {}, as Openfire gave up on resuming it on this connection.", domainPair.getRemote(), resumeTarget, session);
                    return;
                }
                // OF-2425: <resume/> replaces <enable/> on this connection, of which the session is never made a route.
                outgoingSession.getStreamManager().requestResume(resumeTarget, offeredStreamManagementNamespaces);
            }
            return;
        }
        final String preferredNamespace = offeredStreamManagementNamespaces.isEmpty() ? null : offeredStreamManagementNamespaces.get(0);
        if (!StreamManager.SERVER_ACTIVE.getValue() || preferredNamespace == null) {
            LOG.debug("Not asking remote domain '{}' to enable Stream Management on session {}. Enabled for servers: {}, offered by the remote domain: {}", domainPair.getRemote(), session, StreamManager.SERVER_ACTIVE.getValue(), preferredNamespace);
            outgoingSession.getStreamManager().skipEnable();
            return;
        }
        outgoingSession.getStreamManager().requestEnable(preferredNamespace);
    }


    /**
     * Gives up on resuming the stream of the detached session for which this connection was opened, if any: a request
     * to resume it that awaits an answer is abandoned (see
     * {@link RequestingStreamManager#abandonResume()}), so that an answer
     * that arrives after this is ignored, and no request is sent after this, also not on a session that a restart of
     * the stream creates later.
     *
     * @return false if the remote server answered the request already, in which case the processing of that answer
     * takes care of the detached session; otherwise true.
     */
    public boolean abandonResume() {
        if (resumeTarget == null) {
            return true;
        }
        synchronized (resumeMutex) {
            resumeAbandoned = true;
            if (session instanceof LocalOutgoingServerSession outgoingSession) {
                final RequestingStreamManager streamManager = outgoingSession.getStreamManager();
                if (!streamManager.abandonResume() && streamManager.getResumeRequestStatus() == RequestingStreamManager.ResumeRequestStatus.ANSWERED) {
                    LOG.debug("Not giving up on resuming the stream of session {} on session {}, as remote domain '{}' answered the request already.", resumeTarget, session, domainPair.getRemote());
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Transfer an existing connection to a new session when a new streamID is detected. When SASL EXTERNAL succeeded
     * on the previous stream, the new session is authenticated with the authentication method that was used previously.
     *
     * This does not add the new session as a server route.
     *
     * @param newStreamId new stream ID for the new session
     * @param existingAuthMethod authentication method used previously (possibly null)
     */
    private void transferConnectionToNewSession(String newStreamId, ServerSession.AuthenticationMethod existingAuthMethod) {
        session = createLocalOutgoingServerSession(newStreamId, connection);

        // Transfer new session to existing connection
        connection.reinit(session);
        if (saslExternalSucceeded && existingAuthMethod != null) {
            session.setAddress(new JID(null, domainPair.getRemote(), null));
            ((LocalOutgoingServerSession) session).setAuthenticationMethod(existingAuthMethod);
        } else {
            LOG.debug("Session not authenticated yet, unable to setAuthenticationMethod().");
        }
    }

    @Override
    protected void initiateSession(String stanza, XMPPPacketReader reader) throws Exception {
        boolean startOfStream = isStartOfStream(stanza);

        // OF-3447: once session initiation is abandoned (e.g. the connection closed after SASL success), a stream header that still arrives is ignored.
        if (startOfStream && sessionCreated && session == null) {
            LOG.debug("Ignoring stream header from remote domain '{}', as session initiation was abandoned. Connection: {}", domainPair.getRemote(), connection);
            return;
        }

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
                    connection.close(new StreamError(StreamError.Condition.invalid_xml, "Expected a stream ID value, but none was received."));
                    return;
                }

                // OF-3447: RFC 6120 requires a new stream ID when the stream is restarted after TLS or SASL negotiation.
                if (sessionCreated && !isNewStreamId(streamHeaderId)) {
                    if (!ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.getValue()) {
                        LOG.info("Closing connection {}. As the initiating party in a server-to-server connection, we require the receiving party (remote domain '{}') to supply a new stream ID when it restarts the stream. The peer reused stream ID '{}' of the previous stream: {}", connection, domainPair.getRemote(), streamHeaderId, stanza);
                        // OF-3447: after TLS, the session is not yet bound to the connection, which then would not send the stream error.
                        connection.init(session);
                        abandonSessionInitiation();
                        connection.close(new StreamError(StreamError.Condition.invalid_xml, "Expected a new stream ID for the restarted stream, but the stream ID of the previous stream was reused."));
                        return;
                    }
                    LOG.debug("Remote domain '{}' restarted the stream reusing stream ID '{}' of the previous stream. Accepted, as '{}' is enabled.", domainPair.getRemote(), streamHeaderId, ConnectionSettings.Server.OUTGOING_ACCEPT_REUSED_STREAM_ID.getKey());
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

                // OF-3447: after SASL success, the restarted stream (and its session) is the one to report.
                if (saslExternalSucceeded && !isSessionAuthenticated.isDone()) {
                    if (session.isAuthenticated()) {
                        reportSessionAuthenticated();
                    } else {
                        // OF-3447: authentication was lost, e.g. as the connection was closed by another thread before the stream was restarted.
                        LOG.debug("Session with stream ID '{}' of remote domain '{}' is not authenticated after the stream was restarted. Session initiation failed. Session: {}", session.getStreamID(), domainPair.getRemote(), session);
                        abandonSessionInitiation();
                    }
                }
            } catch (XmlPullParserException | IOException e) {
                LOG.error("Failed to extract namespaces and stream ID from the peer's stream header", e);
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

        // OF-3447: once session initiation is abandoned (e.g. the connection closed after SASL success), a negotiation element that still arrives is ignored.
        if (session == null) {
            LOG.debug("Ignoring <{}/> from remote domain '{}', as session initiation was abandoned. Connection: {}", rootTagName, domainPair.getRemote(), connection);
            return true;
        }

        // Handle features
        if ("features".equals(rootTagName)) {
            // OF-2425: Dialback does not restart the stream, so the features before authentication tell whether Stream Management is offered.
            offeredStreamManagementNamespaces = getOfferedStreamManagementNamespaces(doc);

            // Prevent falling back to dialback if we are already authenticated
            if (session.isAuthenticated()) {
                // OF-2425: These features follow the stream restart after SASL authentication.
                requestStreamManagementIfOffered();
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
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "Encryption (TLS) required by our server configuration, but not offered by you."));
                return true;
            } else if (cannotUseTls() && remoteFeaturesRequiresStartTLS(doc)) {
                LOG.debug("I CANNOT use TLS but remote domain '{}' requires the STARTTLS feature.", domainPair.getRemote());
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "Encryption (TLS) disabled by our server configuration, but required by you."));
                return true;
            }

            // Authentication ------
            LOG.debug("Check if both us as well as the remote domain '{}' have enabled STARTTLS and/or dialback ...", domainPair.getRemote());
            final boolean saslExternalOffered = isSaslExternalOfferred(doc);
            final boolean dialbackOffered = isDialbackOffered(doc);
            LOG.debug("Remote server is offering dialback: {}, EXTERNAL SASL: {}", dialbackOffered, saslExternalOffered);

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
                LOG.debug("Trying to authenticate using dialback.");
                LOG.debug("[Acting as Originating Server: Authenticate domain: " + domainPair.getLocal() + " with a RS in the domain of: " + domainPair.getRemote() + " (id: " + session.getStreamID() + ")]");
                ServerDialback dialback = new ServerDialback(connection, domainPair);
                dialback.createAndSendDialbackKey(session.getStreamID().getID());
            } else {
                LOG.debug("No authentication mechanism available.");
                abandonSessionInitiation();
                connection.close(new StreamError(StreamError.Condition.policy_violation, "No applicable authentication mechanism available."));
            }
            return true;
        }

        // Handle dialback result
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(rootTagName)) {
            LOG.debug("Received dialback result from remote domain '{}'", domainPair.getRemote());
            // OF-2425: after resumption, the answer for another domain pair that the resumed session verifies again on this connection.
            if (session instanceof LocalOutgoingServerSession outgoingSession && outgoingSession.processDialbackResult(doc)) {
                return true;
            }
            if ("valid".equals(doc.attributeValue("type"))) {
                LOG.debug("Authentication succeeded!");
                LOG.debug("Dialback was successful.");

                connection.init(session);
                // Set the remote domain name as the address of the session.
                session.setAddress(new JID(null, domainPair.getRemote(), null));
                if (session instanceof LocalOutgoingServerSession) {
                    ((LocalOutgoingServerSession) session).setAuthenticationMethod(ServerSession.AuthenticationMethod.DIALBACK);
                }
                requestStreamManagementIfOffered();

                // Make sure to set 'authenticated' only after the internal state of 'session' itself is updated, to avoid race conditions.
                reportSessionAuthenticated();

                return true;
            } else {
                LOG.debug("Dialback failed");
                LOG.debug("Failed to authenticate domain: the validation response was received, but did not grant authentication.");

                session.markNonResumable();
                session.close();
                return true;
            }
        }

        // Handles SASL failure
        if ("failure".equals(rootTagName)) {
            LOG.debug("Remote domain '{}' reports that EXTERNAL SASL failed.", domainPair.getRemote());

            // Try dialback
            if (ServerDialback.isEnabled() || ServerDialback.isEnabledForSelfSigned()) {

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
            // OF-3447: a repeated <success/> must not restart the stream again, nor add another close callback.
            if (saslExternalSucceeded) {
                LOG.debug("Ignoring a repeated <success/> from remote domain '{}', as SASL EXTERNAL already succeeded. Session: {}", domainPair.getRemote(), session);
                return true;
            }

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
            } else {
                LOG.warn("Expected session with remote domain '{}' to be a LocalOutgoingServerSession but it isn't, unable to setAuthenticationMethod(). Session: {}", domainPair.getRemote(), session);
                connection.close(new StreamError(StreamError.Condition.internal_server_error, "Unexpected session type."));
                return true;
            }

            // OF-3447: authentication is reported when the remote server restarts the stream. Should the connection close
            // before that, tell the caller now, rather than leaving it to wait for the session initialisation to time out.
            saslExternalSucceeded = true;
            connection.getCloseFuture().thenRun(() -> {
                if (!isSessionAuthenticated.isDone()) {
                    LOG.debug("Connection to remote domain '{}' closed after SASL EXTERNAL succeeded, before the stream was restarted. Session initiation failed. Connection: {}", domainPair.getRemote(), connection);
                    abandonSessionInitiation();
                }
            });
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

    /**
     * Reports that the session of the current stream is authenticated.
     */
    private void reportSessionAuthenticated() {
        LOG.debug("Authentication with remote domain '{}' completed for session with stream ID '{}': {}", domainPair.getRemote(), session.getStreamID(), session);
        isSessionAuthenticated.complete(null);
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

    /**
     * Returns a future that completes when the session is authenticated. For SASL EXTERNAL, that is when the remote
     * server has restarted the stream after reporting success; {@link #getSession()} then returns the session of the
     * restarted stream.
     *
     * @return a future that completes when the session is authenticated.
     */
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
