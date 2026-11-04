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
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * XEP-0198 Stream Manager.
 * Handles the acknowledgement of stanzas on a stream: inbound stanzas are those that Openfire receives from the peer,
 * outbound stanzas are those that Openfire sends to the peer.
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

    /**
     * Whether the stream can be resumed. It is read by the thread of the connection that resumes the stream.
     */
    private volatile boolean resume = false;

    /**
     * An outbound stanza that the peer has not acknowledged yet.
     *
     * @deprecated Stream Management does not use this class, which has no replacement.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
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
     * Session (stream) to the peer.
     */
    private final LocalSession session;

    /**
     * Namespace to be used in stanzas sent to the peer (depending on XEP-0198 version used by the peer). It is read by
     * the threads that deliver stanzas to the session, and by the thread of the connection that resumes the stream.
     */
    private volatile String namespace;

    /**
     * Count of how many inbound stanzas/packets Openfire has handled
     */
    private AtomicLong handledInboundStanzas = new AtomicLong( 0 );

    /**
     * Whether the number of unacknowledged stanzas exceeded the maximum, which causes the session to be closed.
     */
    private boolean exceededMaximumUnacknowledgedStanzas = false;

    /**
     * The value (2^32)-1, used to emulate roll-over and the largest legal value for the 'h' attribute (XEP-0198 § 4).
     */
    static final long MASK = new BigInteger( "2" ).pow( 32 ).longValue() - 1;

    /**
     * Outbound stanzas/packets that haven't been acknowledged, and the count of those that have been.
     */
    private final UnacknowledgedStanzaQueue unacknowledgedOutboundStanzas;

    /**
     * Delegates that can determine if a detached session can be terminated.
     */
    private final Set<TerminationDelegate> terminationDelegates = new HashSet<>();

    /**
     * The parts of resumption that depend on the type of the session.
     */
    private final Resumption<?> resumption;

    /**
     * The SM-ID of the stream, which is set when resumption is enabled (XEP-0198 § 5). It is read by the thread of the
     * connection that resumes the stream.
     */
    private volatile String smId;

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
        this.unacknowledgedOutboundStanzas = new UnacknowledgedStanzaQueue( Log, this::getMaximumUnacknowledgedStanzas );
        this.session = session;
        this.resumption = Resumption.forSession(session, Log);
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
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, e.getMessage() + " Our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }
                processResume( resumeRequest );

                break;
            case "r":
                acknowledgeInboundStanzas();
                break;
            case "a":
                processOutboundAcknowledgement(element);
                break;
            default:
                sendUnexpectedError();
        }
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
        boolean offerResume = resumption.isAllowed();
        if (!session.isAuthenticated()) {
            throw new StreamManagementException(PacketError.Condition.unexpected_request,
                "Stream management cannot be enabled before the session is authenticated.");
        }

        synchronized ( this )
        {
            if ( isEnabled() )
            {
                throw new StreamManagementException(PacketError.Condition.unexpected_request,
                    "Stream management is already enabled for this session.");
            }
            this.namespace = namespace;

            this.resume = resume && offerResume;
            if ( resume && !offerResume ) {
                Log.debug("Peer requested resumption, but session {} may not resume. Enabling Stream Management without resumption.", session);
            }
            if ( this.resume ) {
                // Create SM-ID.
                this.smId = resumption.createId();
            }
        }

        // Build confirmation element.
        Element enabled = new DOMElement(QName.get("enabled", namespace));
        if (this.resume) {
            enabled.addAttribute("resume", "true");
            enabled.addAttribute("id", this.smId);
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

        final ResumeRequestValidationResult<?> validation = validateResumeRequest(resumption, request);
        if (!validation.isSuccess()) {
            assert validation.getFailureCondition() != null; // Per definition of the method contract.
            sendError(new PacketError(validation.getFailureCondition()));
            return;
        }

        final LocalSession otherSession = validation.getTarget();
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
        // Inline resumption is defined for SASL2, which only clients use.
        if (!(resumption instanceof ClientResumption clientResumption)) {
            Log.debug("Refusing inline resumption of {}, as session {} is not a client session.", request.getPrevId(), session);
            return Sasl2ResumeResult.failure(buildFailedElement(request.getNamespace(), PacketError.Condition.unexpected_request));
        }
        final ResumeRequestValidationResult<LocalClientSession> validation = validateResumeRequest(clientResumption, request);
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
    @VisibleForTesting
    void detachIfNeeded(@Nonnull final LocalSession otherSession)
    {
        if (!otherSession.isDetached()) {
            Log.debug("Existing session {} is not detached; detaching.", otherSession.getStreamID());
            final Connection oldConnection = otherSession.getConnection();
            otherSession.setDetached();
            assert oldConnection != null; // If the other session is not detached, the connection can't be null.
            Log.debug("Closing superseded connection {} for session {}", oldConnection, otherSession.getStreamID());
            oldConnection.close(new StreamError(StreamError.Condition.conflict, "The stream previously served over this connection is resumed on a new connection."));
        }
    }

    /**
     * Validates a stream resumption request (traditional or inline SASL2), without performing any of the state
     * changes (detaching/reattaching) needed to actually resume the session. This is shared by {@link #processResume(ResumeRequest)}
     * and {@link #processSasl2Resume(ResumeRequest)}, so that the two flows agree on what is (and is not) an
     * acceptable resumption attempt.
     *
     * @param resumption the parts of resumption that depend on the type of the session (cannot be null).
     * @param request the parsed resume request (cannot be null).
     * @param <S> the type of the session that is to be resumed.
     * @return the outcome of the validation.
     */
    @Nonnull
    private <S extends LocalSession> ResumeRequestValidationResult<S> validateResumeRequest(@Nonnull final Resumption<S> resumption, @Nonnull final ResumeRequest request)
    {
        final String namespace = request.getNamespace();
        final String previd = request.getPrevId();
        final long h = request.getH();

        Log.debug("Attempting resumption for {}, h={}", previd, h);

        // Ensure that resource binding has NOT occurred.
        if (!resumption.isAllowed()) {
            Log.debug("Unable to process session resumption attempt, as session {} is in a state where session resumption is not allowed.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        if (session.isAuthenticated()) {
            Log.debug("Unable to process session resumption attempt, as session {} is already authenticated.", session);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        final ResumeRequestValidationResult<S> located = resumption.locate(previd);
        if (!located.isSuccess()) {
            return located;
        }
        final S otherSession = located.getTarget();
        assert otherSession != null; // Per definition of the method contract.
        final JID address = otherSession.getAddress();

        // OF-2811: Cannot resume a session that's already closed. That session is likely busy firing its 'closeListeners'.
        if (otherSession.isClosed()) {
            Log.debug("Not allowing '{}' to resume a session, as the preexisting session is already in process of being closed.", address);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        // Previd identifies proper session. Now check SM status
        final StreamManager otherStreamManager = otherSession.getStreamManager();
        if (!otherStreamManager.resume) {
            Log.debug("Not allowing '{}' to resume a session, the session to be resumed does not have the stream management resumption feature enabled.", address);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        // Read once, as another thread can disable Stream Management on the other session at any time.
        final String otherNamespace = otherStreamManager.namespace;
        if (otherNamespace == null) {
            Log.debug("Not allowing '{}' to resume a session, the session to be resumed disabled SM functionality as a response to an earlier error.", address);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (!otherNamespace.equals(namespace)) {
            Log.debug("Not allowing '{}' to resume a session, the session to be resumed used a different version ({}) of the session management resumption feature as compared to the version that's requested now: {}.", address, otherNamespace, namespace);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        if (!otherStreamManager.validateOutboundAcknowledgement(h)) {
            Log.debug("Not allowing '{}' to resume a session, as it reports it received more stanzas from us than that we've send it.", address);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }

        return ResumeRequestValidationResult.success(otherSession);
    }

    /**
     * Called when a session receives a closing stream tag, this prevents the
     * session from being detached.
     */
    public void formalClose() {
        Log.debug("formalClose() invoked for session with address {} and streamID {}; resumption is now permanently disabled for this stream.", session.getAddress(), session.getStreamID());
        this.resume = false;
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; of inbound stanzas to the peer
     */
    public void acknowledgeInboundStanzas() {
        // Read once, as another thread can disable Stream Management at any time.
        final String currentNamespace = namespace;
        if(currentNamespace != null) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String ack = String.format("<a xmlns='%s' h='%s' />", currentNamespace, handledInboundStanzas.get() & MASK );
            session.deliverRawText( ack );
        }
    }

    /**
     * Sends XEP-0198 acknowledgement &lt;a /&gt; of inbound stanzas to the peer
     *
     * @deprecated Use {@link #acknowledgeInboundStanzas()} instead.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public void sendServerAcknowledgement() {
        acknowledgeInboundStanzas();
    }

    /**
     * Sends XEP-0198 request <r /> to the peer, for an acknowledgement of outbound stanzas
     */
    private void requestOutboundAcknowledgement() {
        // Read once, as another thread can disable Stream Management at any time.
        final String currentNamespace = namespace;
        if(currentNamespace != null) {
            if (session.isDetached()) {
                Log.debug("Session is detached, won't request an ack.");
                return;
            }
            String request = String.format("<r xmlns='%s' />", currentNamespace);
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
     * Checks if the amount of stanzas that the peer acknowledges is not higher than the amount of stanzas that we've
     * sent to the peer.
     *
     * This holds the lock of this instance, so that the check does not see the queue of unacknowledged stanzas halfway
     * through a sequence of changes that this instance makes under that lock. It is safe to invoke on the stream
     * manager of another session.
     *
     * @param h Then number of stanzas that the peer acknowledges it has received from us.
     * @return false if we sent fewer stanzas to the peer than the number it is acknowledging.
     */
    private synchronized boolean validateOutboundAcknowledgement(long h) {
        return unacknowledgedOutboundStanzas.isValidAcknowledgement(h);
    }

    /**
     * Receive and process acknowledgement packet of outbound stanzas from the peer
     * @param ack XEP-0198 acknowledgement <a /> stanza to process
     */
    private void processOutboundAcknowledgement(Element ack) {
        if(isEnabled()) {
            if (ack.attribute("h") != null) {
                final String hValue = ack.attributeValue("h");
                final long h;
                try {
                    h = Long.parseLong(hValue);
                } catch (NumberFormatException e) {
                    Log.warn( "Closing session. Peer sends non-numeric value for SM 'h': {}, affected session: {}", hValue, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a 'h' value that is not a number (which is illegal). Your Ack h: " + hValue + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }
                if (h < 0) {
                    Log.warn( "Closing session. Peer sends negative value for SM 'h': {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a negative value (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }
                if (h > MASK) {
                    Log.warn( "Closing session. Peer sends value for SM 'h' that is larger than 2^32-1: {}, affected session: {}", h, session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas using a value larger than 2^32-1 (which is illegal). Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return;
                }

                Log.debug( "Received acknowledgement from peer: h={}", h );

                synchronized ( this ) {
                    if (!validateOutboundAcknowledgement(h)) {
                        Log.warn( "Closing session. Peer acknowledges stanzas that we didn't send! Peer Ack h: {}, our last unacknowledged stanza: {}, affected session: {}", h, unacknowledgedOutboundStanzas.describeLast(), session );
                        final StreamError error = new StreamError( StreamError.Condition.undefined_condition, "You acknowledged stanzas that we didn't send. Your Ack h: " + h + ", our last unacknowledged stanza: " + unacknowledgedOutboundStanzas.describeLast() );
                        final long sendCount = unacknowledgedOutboundStanzas.getLastSentNumber();
                        error.getElement().addElement( QName.get( "handled-count-too-high", namespace ) ).addAttribute( "h", String.valueOf( h ) ).addAttribute( "send-count", String.valueOf( sendCount ) );
                        // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                        session.close( error );
                        return;
                    }

                    unacknowledgedOutboundStanzas.acknowledge( h );
                }
            }
        }
    }

    /**
     * Registers that Openfire sends a stanza to the peer (which is expected to be acknowledged later).
     * @param packet The stanza that is sent.
     */
    public void sentStanza(Packet packet) {

        if(isEnabled()) {
            final long requestFrequency = JiveGlobals.getLongProperty( "stream.management.requestFrequency", 5 );
            final int size;
            final boolean closeForExceedingMaximum;

            synchronized (this)
            {
                final Connection connection = session.getConnection();
                if ( exceededMaximumUnacknowledgedStanzas && packet instanceof Message && connection != null && connection.isClosed() && connection.getPacketDeliverer() != null ) {
                    // OF-3434: The fallback of the closed connection stores this message offline. Tracking it as well would deliver it twice.
                    // The fallback drops other stanzas, so they remain tracked, to be handled when the session closes.
                    return;
                }

                final long x = unacknowledgedOutboundStanzas.add( packet.createCopy() );

                size = unacknowledgedOutboundStanzas.size();

                Log.trace( "Added stanza of type '{}' to collection of unacknowledged stanzas (x={}). Collection size is now {}.", packet.getElement().getName(), x, size );

                // Prevent keeping to many stanzas in memory. OF-3434: the stanzas are kept, to be handled when the session closes.
                closeForExceedingMaximum = unacknowledgedOutboundStanzas.exceedsMaximum() && !exceededMaximumUnacknowledgedStanzas;
                if ( closeForExceedingMaximum ) {
                    exceededMaximumUnacknowledgedStanzas = true;
                }
            }

            if ( closeForExceedingMaximum ) {
                closeForExceedingMaximum( size );
                return;
            }

            // When we have a sizable amount of unacknowledged stanzas, request acknowledgement.
            if ( size % requestFrequency == 0 ) {
                Log.debug( "Requesting acknowledgement from peer, as we have {} or more unacknowledged stanzas.", requestFrequency );
                requestOutboundAcknowledgement();
            }
        }

    }

    /**
     * Ends the session after the number of unacknowledged stanzas exceeded the maximum.
     *
     * XEP-0198 defines no such maximum, and no way to stop tracking stanzas on an open stream. Instead, the stream is
     * closed with a stream error, and resumption is disabled. The session then closes, which handles its unacknowledged
     * stanzas as for any other session that closes without being resumed (XEP-0198 § 4). This also applies to a detached
     * session that is resumed before it could be ended.
     *
     * @param size The number of unacknowledged stanzas.
     */
    private void closeForExceedingMaximum( final int size )
    {
        Log.warn( "Closing session, as too many stanzas go unacknowledged: {}, the maximum is {}. Affected session: {}", size, getMaximumUnacknowledgedStanzas(), session );
        formalClose();
        final Connection connection = session.getConnection();
        if ( connection != null ) {
            connection.close( new StreamError( StreamError.Condition.policy_violation, "Too many stanzas went unacknowledged: " + size + ", while the maximum is " + getMaximumUnacknowledgedStanzas() + "." ) );
            // OF-3434: If the connection was already closing, its close listener can have detached the session before resumption was disabled. End it then.
            connection.getCloseFuture().thenRun( () -> {
                if ( session.isDetached() ) {
                    TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
                }
            } );
        } else {
            // OF-3434: A detached session has no connection to close. End it now, rather than after its detach timeout.
            TaskEngine.getInstance().submit( () -> XMPPServer.getInstance().getSessionManager().terminateDetached( session ) );
        }
    }

    /**
     * Handles the stanzas that the peer did not acknowledge, for a session that closed without being resumed, and
     * disables Stream Management for the session.
     *
     * XEP-0198 § 4 has a server treat unacknowledged stanzas in the same way as stanzas sent to an unavailable resource.
     * Messages are routed again, with a delay stamp. The sender of an IQ request of type 'get' or 'set' receives a
     * 'service-unavailable' error, as RFC 6120 § 8.2.3 requires a response to every IQ request, and RFC 6121 § 8.5.3.2.3
     * defines that error for an unavailable resource. Other stanzas are discarded.
     *
     * This must be invoked after the session is removed. While the session is registered, a message routed to its address
     * reaches that session rather than another resource of the user or offline storage, and the IQ router does not accept
     * an error that has the address of the session as its sender, as the session is no longer authenticated.
     *
     * @param router the router used to route the stanzas.
     * @param serverAddress this server's bare-domain address, used to stamp delay information on messages.
     */
    public void onClose(PacketRouter router, JID serverAddress) {
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                namespace = null; // disable stream management.
                for (final UnacknowledgedStanzaQueue.Entry unacked : unacknowledgedOutboundStanzas.getEntries()) {
                    // OF-3442: A failure to handle one stanza must not prevent the others from being handled.
                    try {
                        if (unacked.getPacket() instanceof Message) {
                            Message m = (Message) unacked.getPacket();
                            if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.getTimestamp()));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            router.route(unacked.getPacket());
                        } else if (unacked.getPacket() instanceof IQ iq && iq.isRequest()) {
                            // OF-3442: Every IQ request needs a response (RFC 6120 § 8.2.3), here the one for an unavailable resource (RFC 6121 § 8.5.3.2.3).
                            final IQ error = IQ.createResultIQ(iq);
                            if (error.getFrom() == null) {
                                // A request without a 'to' was addressed to this session.
                                error.setFrom(session.getAddress());
                            }
                            error.setError(PacketError.Condition.service_unavailable);
                            router.route(error);
                        }
                    } catch (Exception e) {
                        // The stanza itself is not logged, as it can have private content.
                        Log.warn("Unable to handle an unacknowledged '{}' stanza of type '{}' with ID '{}', of session {} that closed.", unacked.getPacket().getElement().getName(), unacked.getPacket().getElement().attributeValue("type"), unacked.getPacket().getID(), session, e);
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
        resumed.addAttribute("previd", smId);
        resumed.addAttribute("h", Long.toString(handledInboundStanzas.get() & MASK));
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
        synchronized (this) {
            unacknowledgedOutboundStanzas.acknowledge( h );
        }
        synchronized (this) {
            if (exceededMaximumUnacknowledgedStanzas) {
                // OF-3434: The maximum was exceeded while detached. Closing under this lock keeps a stanza that LocalClientSession#pushPackets() delivers from going untracked.
                closeForExceedingMaximum(unacknowledgedOutboundStanzas.size());
                return;
            }
            // OF-3434: Re-delivering under the same lock keeps a stanza that exceeds the maximum from closing the stream first, which would route the re-delivered stanzas twice.
            Log.debug("Processing remaining unacked stanzas");
            final Connection connection = session.getConnection();
            assert connection != null; // While the client is resuming a session, the connection on which the session is resumed can't be null.
            // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
            if(isEnabled()) {
                for (final UnacknowledgedStanzaQueue.Entry unacked : unacknowledgedOutboundStanzas.getEntries()) {
                    try {
                        if (unacked.getPacket() instanceof Message) {
                            Message m = (Message) unacked.getPacket();
                            if (m.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = m.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.getTimestamp()));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(m);
                        } else if (unacked.getPacket() instanceof Presence) {
                            Presence p = (Presence) unacked.getPacket();
                            if (p.getExtension("delay", "urn:xmpp:delay") == null) {
                                Element delayInformation = p.addChildElement("delay", "urn:xmpp:delay");
                                delayInformation.addAttribute("stamp", XMPPDateTimeFormat.format(unacked.getTimestamp()));
                                delayInformation.addAttribute("from", serverAddress.toBareJID());
                            }
                            connection.deliver(p);
                        } else {
                            connection.deliver(unacked.getPacket());
                        }
                    } catch (UnauthorizedException e) {
                        Log.warn("Caught unauthorized exception, which seems worrying: ", e);
                    }
                }

                requestOutboundAcknowledgement();
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
     * Increments the count of inbound stanzas that Openfire has handled since
     * Stream Management was enabled.
     *
     * This is part of the processing of every inbound stanza. An invocation for a stanza that was not handled makes the
     * count that is reported to the peer incorrect.
     */
    public void incrementHandledInboundStanzas() {
        if(isEnabled()) {
            this.handledInboundStanzas.incrementAndGet();
        }
    }

    /**
     * Increments the count of inbound stanzas that Openfire has handled since
     * Stream Management was enabled.
     *
     * This is part of the processing of every inbound stanza. An invocation for a stanza that was not handled makes the
     * count that is reported to the peer incorrect.
     *
     * @deprecated Use {@link #incrementHandledInboundStanzas()} instead.
     */
    @Deprecated(forRemoval = true, since = "5.2.0") // Remove in or after Openfire 5.3.0
    public void incrementServerProcessedStanzas() {
        incrementHandledInboundStanzas();
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
