/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import com.google.common.annotations.VisibleForTesting;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.dom.DOMElement;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XEP-0198 Stream Manager.
 * Handles client/server messages acknowledgement.
 *
 * @author jonnyheavey
 */
public class StreamManager {

    public static SystemProperty<Boolean> LOCATION_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.location.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> LOCATION_TERMINATE_OTHERS_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.location.terminate-others.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> MAX_SERVER_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.max-server.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static SystemProperty<Boolean> ACTIVE = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("stream.management.active")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    private final Logger Log;
    private boolean resume = false;

    public static class UnackedPacket {
        public final long x;
        public final Date timestamp = new Date();
        public final Packet packet;

        public UnackedPacket(long x, Packet p) {
            this.x = x;
            packet = p;
        }
    }

    public static boolean isStreamManagementActive() {
        return ACTIVE.getValue();
    }

    /**
     * Stanza namespaces
     */
    public static final String NAMESPACE_V2 = "urn:xmpp:sm:2";
    public static final String NAMESPACE_V3 = "urn:xmpp:sm:3";

    /**
     * Session (stream) to client.
     */
    private final LocalSession session;

    /**
     * Namespace to be used in stanzas sent to client (depending on XEP-0198 version used by client)
     */
    private String namespace;

    /**
     * Count of how many stanzas/packets
     * sent from the client that the server has processed
     */
    private AtomicLong serverProcessedStanzas = new AtomicLong( 0 );

    /**
     * Count of how many stanzas/packets
     * sent from the server that the client has processed
     */
    private AtomicLong clientProcessedStanzas = new AtomicLong( 0 );

    /**
     * The value (2^32)-1, used to emulate roll-over and the largest legal value for the 'h' attribute (XEP-0198 § 4).
     */
    static final long MASK = new BigInteger( "2" ).pow( 32 ).longValue() - 1;

    /**
     * Collection of stanzas/packets sent to client that haven't been acknowledged.
     */
    private Deque<UnackedPacket> unacknowledgedServerStanzas = new LinkedList<>();

    /**
     * Delegates that can determine if a detached session can be terminated.
     */
    private final Set<TerminationDelegate> terminationDelegates = new HashSet<>();

    public StreamManager(LocalSession session) {
        String address;
        try {
            final Connection connection = session.getConnection();
            if (connection != null) {
                address = connection.getHostAddress();
            } else {
                // This smells: Why would a stream manager be created for a session without a connection (which typically is a session that is already detached)?
                LoggerFactory.getLogger(StreamManager.class).warn("Connection is null for session: {}", session.getAddress());
                address = null;
            }
        }
        catch ( UnknownHostException e )
        {
            address = null;
        }

        this.Log = LoggerFactory.getLogger(StreamManager.class + "["+ (address == null ? "(unknown address)" : address) +"]" );
        this.session = session;
    }

    /**
     * Returns true if a stream is resumable.
     *
     * @return True if a stream is resumable.
     */
    public boolean getResume() {
        return resume;
    }

    /**
     * Processes a stream management element.
     *
     * Inline resume requests, nested in a SASL2 <authenticate/> element, are not processed here; see processSasl2Resume(ResumeRequest)
     *
     * @param element The stream management element to be processed.
     */
    public void process( Element element )
    {
        switch(element.getName()) {
            case "enable":
                String resumeString = element.attributeValue("resume");
                boolean resume = false;
                if (resumeString != null) {
                    if (resumeString.equalsIgnoreCase("true") || resumeString.equalsIgnoreCase("yes") || resumeString.equals("1")) {
                        resume = true;
                    }
                }
                enable( element.getNamespace().getStringValue(), resume );
                break;
            case "resume":
                final ResumeRequest resumeRequest;
                try {
                    resumeRequest = ResumeRequest.from(element);
                } catch (MalformedResumeRequestException e) {
                    Log.info( "Closing client session that sent a malformed 'resume' request. Error message: {}. Affected session: {}", e.getMessage(), session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, e.getMessage() + " Our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }
                processResume( resumeRequest );

                break;
            case "r":
                sendServerAcknowledgement();
                break;
            case "a":
                processClientAcknowledgement(element);
                break;
            default:
                sendUnexpectedError();
        }
    }

    /**
     * Should this session be allowed to resume?
     * This is used while processed <enable/> and <resume/>
     *
     * @return True if the session is allowed to resume.
     */
    private boolean allowResume() {
        boolean allow = false;
        // Ensure that resource binding has occurred.
        if (session instanceof ClientSession) {
            AuthToken authToken = ((LocalClientSession)session).getAuthToken();
            if (authToken != null) {
                if (!authToken.isAnonymous()) {
                    allow = true;
                }
            }
        }
        return allow;
    }

    /**
     * Attempts to enable Stream Management for the entity identified by the provided JID.
     *
     * @param namespace The namespace that defines what version of SM is to be enabled.
     * @param resume Whether the client is requesting a resumable session.
     */
    private void enable( String namespace, boolean resume )
    {
        final Element outcome;
        try {
            outcome = enableAndBuildElement(namespace, resume);
        } catch (final StreamManagementException e) {
            Log.debug("Unable to enable stream management for session {}: {}", session, e.getMessage());
            session.deliverRawText(buildFailedElement(namespace, e.getCondition()).asXML());
            return;
        }
        session.deliverRawText(outcome.asXML());
    }

    /**
     * Enables stream management, returning the {@code <enabled/>} element rather than sending it.
     *
     * Leaving delivery to the caller allows an inline caller to embed the element in its enclosing response, as
     * XEP-0198 § 9.1 requires of a Bind2 inline request.
     *
     * @param namespace the SM namespace to use
     * @param resume    whether the client requests a resumable session
     * @return the {@code <enabled/>} element
     * @throws StreamManagementException when stream management could not be enabled, carrying the condition to report
     */
    @Nonnull
    public Element enableAndBuildElement( String namespace, boolean resume ) throws StreamManagementException
    {
        boolean offerResume = allowResume();
        if (!session.isAuthenticated()) {
            throw new StreamManagementException(PacketError.Condition.unexpected_request,
                "Stream management cannot be enabled before the session is authenticated.");
        }

        String smId = null;
        synchronized ( this )
        {
            if ( isEnabled() )
            {
                throw new StreamManagementException(PacketError.Condition.unexpected_request,
                    "Stream management is already enabled for this session.");
            }
            this.namespace = namespace;

            this.resume = resume && offerResume;
            if ( this.resume ) {
                // Create SM-ID.
                smId = Base64.getEncoder().encodeToString((session.getAddress().getResource() + "\0" + session.getStreamID().getID()).getBytes(StandardCharsets.UTF_8));
            }
        }

        // Build confirmation element.
        Element enabled = new DOMElement(QName.get("enabled", namespace));
        if (this.resume) {
            enabled.addAttribute("resume", "true");
            enabled.addAttribute("id", smId);
            if ( !namespace.equals(NAMESPACE_V2) && LOCATION_ENABLED.getValue() ) {
                // OF-1925: Hint clients to do resumes at the same cluster node.
                enabled.addAttribute("location", XMPPServer.getInstance().getServerInfo().getHostname());
            }

            // OF-1926: Tell clients how long they can be detached.
            if ( MAX_SERVER_ENABLED.getValue() ) {
                final int sessionDetachTime = XMPPServer.getInstance().getSessionManager().getSessionDetachTime();
                if ( sessionDetachTime > 0 ) {
                    enabled.addAttribute("max", String.valueOf(sessionDetachTime/1000));
                }
            }
        }
        return enabled;
    }

    /**
     * Attempts to process (validate and perform) a traditional {@code <resume/>} request, as defined by XEP-0198.
     *
     * Unlike {@link #processSasl2Resume(ResumeRequest)}, this writes its response (either a stream error, or the
     * effects of {@link LocalSession#reattach(LocalSession, long)}) directly to the connection, rather than
     * returning a result to the caller.
     *
     * @param request the parsed resume request (cannot be null).
     */
    private void processResume(@Nonnull final ResumeRequest request)
    {
        this.namespace = request.getNamespace();

        final ResumeRequestValidationResult validation = validateResumeRequest(request);
        if (!validation.isSuccess()) {
            assert validation.getFailureCondition() != null; // Per definition of the method contract.
            sendError(new PacketError(validation.getFailureCondition()));
            return;
        }

        final LocalClientSession otherSession = validation.getTarget();
        assert otherSession != null; // Per definition of the method contract.
        detachIfNeeded(otherSession);

        // If we're all happy, re-attach the connection from the pre-existing session to the new session, discarding the old session.
        Log.debug("Attaching to other session '{}'.", otherSession.getStreamID());
        otherSession.reattach(session, request.getH());
        Log.debug("Perform resumption of session {}, using connection from session {}", otherSession.getStreamID(), session.getStreamID());
    }

    /**
     * Attempts to process (validate and perform) an inline SASL2 (XEP-0388) resume request, as defined by XEP-0198
     * § 9.2 ("Inline Stream Resumption").
     *
     * Unlike {@link #processResume(ResumeRequest)}, this does not write its response to the connection. Instead, the
     * outcome is returned as a {@link Sasl2ResumeResult}, for the caller to embed in the SASL2 {@code <success/>}
     * response that it is constructing.
     *
     * This method is invoked on the stream manager of the temporary session that is negotiating the SASL2
     * authentication. On success, the connection has been transferred to the resumed session, which is the session
     * that the {@code <success/>} must be delivered to. Having delivered it, the caller completes the resumption by
     * invoking {@link LocalSession#completeSasl2Resume(long)} on that session: everything it delivers must follow the
     * resumption confirmation on the wire, which is why it cannot be done here.
     *
     * On failure, no state is changed: the temporary session remains usable, and the caller is expected to proceed
     * with resource binding, reporting the returned {@code <failed/>} element alongside the outcome of that bind.
     *
     * @param request the parsed inline resume request (cannot be null).
     * @return the outcome of the resume attempt.
     */
    @Nonnull
    public Sasl2ResumeResult processSasl2Resume(@Nonnull final ResumeRequest request)
    {
        final ResumeRequestValidationResult validation = validateResumeRequest(request);
        if (!validation.isSuccess()) {
            assert validation.getFailureCondition() != null; // Per definition of the method contract.
            // Note: deliberately no side effects on this (temporary) session's stream management state. Unlike the
            // traditional flow, a failed inline resume does not abandon the stream: the client proceeds to Bind2,
            // possibly inlining an <enable/> of its own (XEP-0198 § 9.2.1).
            return Sasl2ResumeResult.failure(buildFailedElement(request.getNamespace(), validation.getFailureCondition()));
        }

        final LocalClientSession otherSession = validation.getTarget();
        assert otherSession != null; // Per definition of the method contract.
        detachIfNeeded(otherSession);

        Log.debug("Attaching to other session '{}' via inline SASL2 resume.", otherSession.getStreamID());
        otherSession.reattachForSasl2(session);

        final Element resumed = otherSession.getStreamManager().buildResumedElement();
        return Sasl2ResumeResult.success(resumed, otherSession);
    }

    /**
     * Detaches the connection of a to-be-resumed session, unless it is already detached.
     *
     * @param otherSession the pre-existing session that is about to be resumed.
     */
    private void detachIfNeeded(@Nonnull final LocalClientSession otherSession)
    {
        if (!otherSession.isDetached()) {
            Log.debug("Existing session {} is not detached; detaching.", otherSession.getStreamID());
            final Connection oldConnection = otherSession.getConnection();
            otherSession.setDetached();
            assert oldConnection != null; // If the other session is not detached, the connection can't be null.
            oldConnection.close(new StreamError(StreamError.Condition.conflict, "The stream previously served over this connection is resumed on a new connection."));
        }
    }

    /**
     * Validates a stream resumption request (traditional or inline SASL2), without performing any of the state
     * changes (detaching/reattaching) needed to actually resume the session. This is shared by {@link #processResume(ResumeRequest)}
     * and {@link #processSasl2Resume(ResumeRequest)}, so that the two flows agree on what is (and is not) an
     * acceptable resumption attempt.
     *
     * @param request the parsed resume request (cannot be null).
     * @return the outcome of the validation.
     */
    @Nonnull
    private ResumeRequestValidationResult validateResumeRequest(@Nonnull final ResumeRequest request)
    {
        final String namespace = request.getNamespace();
        final String previd = request.getPrevId();
        final long h = request.getH();

        Log.debug("Attempting resumption for {}, h={}", previd, h);

        // Ensure that resource binding has NOT occurred.
        if (!allowResume()) {
            Log.debug("Unable to process session resumption attempt, as session {} is in a state where session resumption is not allowed.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        if (session.isAuthenticated()) {
            Log.debug("Unable to process session resumption attempt, as session {} is already authenticated.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        AuthToken authToken = null;
        if (session instanceof ClientSession) {
            authToken = ((LocalClientSession) session).getAuthToken();
        }
        if (authToken == null) {
            Log.debug("Unable to process session resumption attempt, as session {} does not provide any auth context.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        // Decode previd.
        String resource;
        String streamId;
        try {
            StringTokenizer toks = new StringTokenizer(new String(Base64.getDecoder().decode(previd), StandardCharsets.UTF_8), "\0");
            resource = toks.nextToken();
            streamId = toks.nextToken();
        } catch (Exception e) {
            Log.debug("Exception from previd decode:", e);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        final JID fullJid;
        if ( authToken.isAnonymous() ){
            fullJid = new JID(resource, session.getServerName(), resource, true);
        } else {
            fullJid = new JID(authToken.getUsername(), session.getServerName(), resource, true);
        }
        Log.debug("Resuming session for '{}'. Current session: {}", fullJid, session.getStreamID());

        // Locate existing session.
        final ClientSession route = XMPPServer.getInstance().getRoutingTable().getClientRoute(fullJid);
        if (route == null) {
            Log.debug("Not able for client of '{}' to resume a session on this cluster node. No session was found for this client.", fullJid);
            if (LOCATION_TERMINATE_OTHERS_ENABLED.getValue()) {
                // When the client tries to resume a connection on this host, it is unlikely to try other hosts. Remove any detached sessions living elsewhere in the cluster. (OF-2753)
                CacheFactory.doClusterTask(new ClientSessionTask(fullJid, RemoteSessionTask.Operation.removeDetached));
            }
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }

        if (!(route instanceof LocalClientSession otherSession)) {
            Log.debug("Not allowing a client of '{}' to resume a session on this cluster node. The session can only be resumed on the Openfire cluster node where the original session was connected.", fullJid);
            if (LOCATION_TERMINATE_OTHERS_ENABLED.getValue()) {
                // When the client tries to resume a connection on this host, it is unlikely to try other hosts. Remove any detached sessions living elsewhere in the cluster. (OF-2753)
                CacheFactory.doClusterTask(new ClientSessionTask(fullJid, RemoteSessionTask.Operation.removeDetached));
            }
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        if (!otherSession.getStreamID().getID().equals(streamId)) {
            return ResumeRequestValidationResult.failure(PacketError.Condition.item_not_found);
        }
        Log.debug("Found existing session for '{}', checking status", fullJid);

        // OF-2811: Cannot resume a session that's already closed. That session is likely busy firing its 'closeListeners'.
        if (route.isClosed()) {
            Log.debug("Not allowing a client of '{}' to resume a session, as the preexisting session is already in process of being closed.", fullJid);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        // Previd identifies proper session. Now check SM status
        if (!otherSession.getStreamManager().resume) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed does not have the stream management resumption feature enabled.", fullJid);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (otherSession.getStreamManager().namespace == null) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed disabled SM functionality as a response to an earlier error.", fullJid);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (!otherSession.getStreamManager().namespace.equals(namespace)) {
            Log.debug("Not allowing a client of '{}' to resume a session, the session to be resumed used a different version ({}) of the session management resumption feature as compared to the version that's requested now: {}.", fullJid, otherSession.getStreamManager().namespace, namespace);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (!otherSession.getStreamManager().validateClientAcknowledgement(h)) {
            Log.debug("Not allowing a client of '{}' to resume a session, as it reports it received more stanzas from us than that we've send it.", fullJid);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        return ResumeRequestValidationResult.success(otherSession);
    }

    /**
     * Called when a session receives a closing stream tag, this prevents the
     * session from being detached.
     */
    public void formalClose() {
        this.resume = false;
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; to client from server
     */
    public void sendServerAcknowledgement() {
        if(isEnabled()) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String ack = String.format("<a xmlns='%s' h='%s' />", namespace, serverProcessedStanzas.get() & MASK );
            session.deliverRawText( ack );
        }
    }

    /**
     * Sends XEP-0198 request <r /> to client from server
     */
    private void sendServerRequest() {
        if(isEnabled()) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String request = String.format("<r xmlns='%s' />", namespace);
            session.deliverRawText( request );
        }
    }

    /**
     * Send an error if a XEP-0198 stanza is received at an unexpected time.
     * e.g. before resource-binding has completed.
     */
    private void sendUnexpectedError() {
        sendError(new PacketError( PacketError.Condition.unexpected_request ));
    }

    /**
     * Send a generic failed error.
     *
     * @param error PacketError describing the failure.
     */
    private void sendError(PacketError error) {
        final Element failed = buildFailedElement(namespace, error.getCondition());
        session.deliverRawText(failed.asXML());
        this.namespace = null; // isEnabled() is testing this.
    }

    /**
     * Constructs an XML element representing a failed stream management negotiation.
     *
     * @param namespace The namespace indicating the version of stream management being used. Must not be null.
     * @param condition The specific error condition that caused the failure. Must not be null.
     * @return An XML {@code <failed>} element containing information about the failure.
     */
    private static Element buildFailedElement(@Nonnull final String namespace, @Nonnull final PacketError.Condition condition) {
        final Element failed = DocumentHelper.createElement(QName.get("failed", namespace));
        failed.addElement(QName.get(condition.toXMPP(), "urn:ietf:params:xml:ns:xmpp-stanzas"));
        return failed;
    }

    /**
     * Checks if the amount of stanzas that the client acknowledges is equal to or less than the amount of stanzas that
     * we've sent to the client.
     *
     * @param h Then number of stanzas that the client acknowledges it has received from us.
     * @return false if we sent fewer stanzas to the client than the number it is acknowledging.
     */
    private synchronized boolean validateClientAcknowledgement(long h) {
        if (h < 0) {
            throw new IllegalArgumentException("Argument 'h' cannot be negative, but was: " + h);
        }
        if (h > MASK) {
            throw new IllegalArgumentException("Argument 'h' cannot be larger than 2^32 -1, but was: " + h);
        }
        final long oldH = clientProcessedStanzas.get();
        final Long lastUnackedX = unacknowledgedServerStanzas.isEmpty() ? null : unacknowledgedServerStanzas.getLast().x;
        return validateClientAcknowledgement(h, oldH, lastUnackedX);
    }

    @VisibleForTesting
    static boolean validateClientAcknowledgement(final long h, final long oldH, final Long lastUnackedX) {
        if (lastUnackedX == null) {
            // No unacked stanzas.
            return h == oldH;
        }

        return h <= lastUnackedX;
    }

    /**
     * Process client acknowledgements for a given value of h.
     *
     * @param h Last handled stanza to be acknowledged.
     */
    private void processClientAcknowledgement(long h) {
        synchronized (this) {

            if ( !validateClientAcknowledgement(h) ) {
                // All paths leading up to here should have checked for this. Race condition?
                throw new IllegalStateException( "Client acknowledges stanzas that we didn't send! Client Ack h: "+h+", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
            }

            clientProcessedStanzas.set( h );

            // Remove stanzas from temporary storage as now acknowledged
            Log.trace( "Before processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size() );

            // Pop all acknowledged stanzas.
            while( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getFirst().x <= h )
            {
                unacknowledgedServerStanzas.removeFirst();
            }

            // Ensure that unacknowledged stanzas are purged after the client rolled over 'h' which occurs at h= (2^32)-1
            final int maxUnacked = getMaximumUnacknowledgedStanzas();
            final boolean clientHadRollOver = h < maxUnacked && !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > MASK - maxUnacked;
            if ( clientHadRollOver )
            {
                Log.info( "Client rolled over 'h'. Purging high-numbered unacknowledged stanzas." );
                while ( !unacknowledgedServerStanzas.isEmpty() && unacknowledgedServerStanzas.getLast().x > MASK - maxUnacked)
                {
                    unacknowledgedServerStanzas.removeLast();
                }
            }

            Log.trace( "After processing client Ack (h={}): {} unacknowledged stanzas.", h, unacknowledgedServerStanzas.size());
        }
    }

    /**
     * Receive and process acknowledgement packet from client
     * @param ack XEP-0198 acknowledgement <a /> stanza to process
     */
    private void processClientAcknowledgement(Element ack) {
        if(isEnabled()) {
            if (ack.attribute("h") != null) {
                final String hValue = ack.attributeValue("h");
                final long h;
                try {
                    h = Long.parseLong(hValue);
                } catch (NumberFormatException e) {
                    Log.warn( "Closing client session. Client sends non-numeric value for SM 'h': {}, affected session: {}", hValue, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a 'h' value that is not a number (which is illegal). Your Ack h: " + hValue + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }
                if (h < 0) {
                    Log.warn( "Closing client session. Client sends negative value for SM 'h': {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a negative value (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                    session.deliverRawText( error.toXML() );
                    session.close();
                    return;
                }

                Log.debug( "Received acknowledgement from client: h={}", h );

                synchronized ( this ) {
                    if (!validateClientAcknowledgement(h)) {
                        Log.warn( "Closing client session. Client acknowledges stanzas that we didn't send! Client Ack h: {}, our last unacknowledged stanza: {}, affected session: {}", h, unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x, session );
                        final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + (unacknowledgedServerStanzas.isEmpty() ? "(none)" : unacknowledgedServerStanzas.getLast().x) );
                        session.deliverRawText( error.toXML() );
                        session.close();
                        return;
                    }

                    processClientAcknowledgement(h);
                }
            }
        }
    }

    /**
     * Registers that Openfire sends a stanza to the client (which is expected to be acknowledged later).
     * @param packet The stanza that is sent.
     */
    public void sentStanza(Packet packet) {

        if(isEnabled()) {
            final long requestFrequency = JiveGlobals.getLongProperty( "stream.management.requestFrequency", 5 );
            final int size;

            synchronized (this)
            {
                // The next ID is one higher than the last stanza that was sent (which might be unacknowledged!)
                final long x = 1 + ( unacknowledgedServerStanzas.isEmpty() ? clientProcessedStanzas.get() : unacknowledgedServerStanzas.getLast().x );
                unacknowledgedServerStanzas.addLast( new StreamManager.UnackedPacket( x, packet.createCopy() ) );

                size = unacknowledgedServerStanzas.size();

                Log.trace( "Added stanza of type '{}' to collection of unacknowledged stanzas (x={}). Collection size is now {}.", packet.getElement().getName(), x, size );

                // Prevent keeping to many stanzas in memory.
                if ( size > getMaximumUnacknowledgedStanzas() )
                {
                    Log.warn( "To many stanzas go unacknowledged for this connection. Clearing queue and disabling functionality." );
                    namespace = null;
                    unacknowledgedServerStanzas.clear();
                    return;
                }
            }

            // When we have a sizable amount of unacknowledged stanzas, request acknowledgement.
            if ( size % requestFrequency == 0 ) {
                Log.debug( "Requesting acknowledgement from peer, as we have {} or more unacknowledged stanzas.", requestFrequency );
                sendServerRequest();
            }
        }

    }

    public void onClose(PacketRouter router, JID serverAddress) {
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                namespace = null; // disable stream management.
                for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
                    if (unacked.packet instanceof Message) {
                        Message m = (Message) unacked.packet;
                        if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                            Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                            delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                            delayInformation.addAttribute("from", serverAddress.toBareJID());
                        }
                        router.route(unacked.packet);
                    }
                }
            }
        }

    }

    public void onResume(JID serverAddress, long h) {
        Log.debug("Agreeing to resume");
        final Element resumed = buildResumedElement();
        final Connection connection = session.getConnection();
        assert connection != null; // While the client is resuming a session, the connection on which the session is resumed can't be null.
        connection.deliverRawText(resumed.asXML());
        redeliverUnackedStanzas(serverAddress, h);
    }

    /**
     * Constructs the XEP-0198 {@code <resumed/>} element for this session's stream manager, without delivering it.
     *
     * This is used by the traditional resume flow (through {@link #onResume(JID, long)}, which sends it directly),
     * as well as by the inline SASL2 resume flow, which instead needs to embed the element in a SASL2
     * {@code <success/>} response, rather than write it to the connection itself.
     *
     * @return the {@code <resumed/>} element.
     */
    @Nonnull
    Element buildResumedElement() {
        final Element resumed = new DOMElement(QName.get("resumed", namespace));
        resumed.addAttribute("previd", Base64.getEncoder().encodeToString((session.getAddress().getResource() + "\0" + session.getStreamID().getID()).getBytes(StandardCharsets.UTF_8)));
        resumed.addAttribute("h", Long.toString(serverProcessedStanzas.get()));
        return resumed;
    }

    /**
     * Processes the client's acknowledgement of 'h' as reported in its (traditional or inline SASL2) resume request,
     * and retransmits any stanzas that remain unacknowledged after that.
     *
     * This is the second half of what {@link #onResume(JID, long)} does for the traditional resume flow. It is
     * split out so that the inline SASL2 resume flow can defer this until after it has delivered its own response
     * (typically, the SASL2 {@code <success/>} that embeds the {@code <resumed/>} element built by
     * {@link #buildResumedElement()}), handled by {@link LocalSession#completeSasl2Resume(long)}.
     *
     * @param serverAddress this server's bare-domain address, used to stamp delay information on redelivered stanzas.
     * @param h the sequence number of the last handled stanza, as reported by the client that is resuming.
     */
    public void redeliverUnackedStanzas(@Nonnull final JID serverAddress, final long h) {
        Log.debug("Resuming session: Ack for {}", h);
        processClientAcknowledgement(h);
        Log.debug("Processing remaining unacked stanzas");
        final Connection connection = session.getConnection();
        assert connection != null; // While the client is resuming a session, the connection on which the session is resumed can't be null.
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                for (StreamManager.UnackedPacket unacked : unacknowledgedServerStanzas) {
                    try {
                        if (unacked.packet instanceof Message) {
                            Message m = (Message) unacked.packet;
                            if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(m);
                        } else if (unacked.packet instanceof Presence) {
                            Presence p = (Presence) unacked.packet;
                            if (p.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = p.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.timestamp));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(p);
                        } else {
                            connection.deliver(unacked.packet);
                        }
                    } catch (UnauthorizedException e) {
                        Log.warn("Caught unauthorized exception, which seems worrying: ", e);
                    }
                }

                sendServerRequest();
            }
        }
    }

    /**
     * Determines whether Stream Management enabled for session this
     * manager belongs to.
     * @return true when stream management is enabled, otherwise false.
     */
    public boolean isEnabled() {
        return namespace != null;
    }

    /**
     * Increments the count of stanzas processed by the server since
     * Stream Management was enabled.
     */
    public void incrementServerProcessedStanzas() {
        if(isEnabled()) {
            this.serverProcessedStanzas.incrementAndGet();
        }
    }

    /**
     * The maximum amount of stanzas we keep, waiting for ack.
     * @return The maximum number of stanzas.
     */
    private int getMaximumUnacknowledgedStanzas()
    {
        return JiveGlobals.getIntProperty( "stream.management.max-unacked", 10000 );
    }

    /**
     * Returns a defensive copy of all delegates that can determine if a detached session can be terminated.
     *
     * @return all delegates that can determine if a detached session can be terminated.
     */
    public Set<TerminationDelegate> getTerminationDelegates()
    {
        return new HashSet<>(terminationDelegates);
    }

    /**
     * Adds a new delegate that can determine if a detached session can be terminated. When no such delegate is
     * registered for a session, the server default behavior will determine if a detached session can be terminated.
     *
     * This method will add delegates, unless the new delegate is equal to a previously registered delegate. In such
     * case, this method will silently ignore the invocation.
     *
     * @param delegate the delegate to register with the session
     */
    public void addTerminationDelegate(@Nonnull final TerminationDelegate delegate)
    {
        terminationDelegates.add(delegate);
    }

    /**
     * Removes a delegate that can determine if a detached session can be terminated. When no such delegate is
     * registered for a session, the server default behavior will determine if a detached session can be terminated.
     *
     * This method will silently ignore an invocation to remove a delegate that was not registered with the session.
     *
     * @param delegate the delegate to register with the session
     */
    public void removeTerminationDelegate(@Nonnull final TerminationDelegate delegate)
    {
        terminationDelegates.remove(delegate);
    }

    /**
     * Returns the element that advertises support for inline stream resumption in the {@code <inline/>} element of
     * the SASL2 stream feature, as defined in XEP-0198 § 9.2.
     *
     * Note that this is distinct from the Bind2 inline feature (XEP-0198 § 9.1) that allows a client to enable
     * stream management as part of a resource bind: that one is advertised through {@link Bind2Request#featureElement()}.
     *
     * @return the {@code <sm/>} feature element.
     */
    @Nonnull
    public static Element sasl2InlineFeatureElement() {
        return DocumentHelper.createElement(QName.get("sm", NAMESPACE_V3));
    }
}
