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
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.dom.DOMElement;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;

/**
 * XEP-0198 Stream Management at the end of a stream that answers requests of the peer: the peer asks to enable Stream
 * Management, or to resume an earlier stream, and Openfire grants or refuses that.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class AnsweringStreamManager extends StreamManager
{
    /**
     * The parts of resumption that depend on the type of the session.
     */
    private final Resumption<?> resumption;

    AnsweringStreamManager(@Nonnull final LocalSession session)
    {
        super(session);
        this.resumption = Resumption.forSession(session, Log);
    }

    @Override
    boolean processNegotiation( @Nonnull final Element element )
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
                return true;
            case "resume":
                final ResumeRequest resumeRequest;
                try {
                    resumeRequest = ResumeRequest.from(element);
                } catch (MalformedResumeRequestException e) {
                    Log.info( "Closing client session that sent a malformed 'resume' request. Error message: {}. Affected session: {}", e.getMessage(), session );
                    final StreamError error = new StreamError( StreamError.Condition.undefined_condition, e.getMessage() + " Our last unacknowledged stanza: " + describeLastUnacknowledged() );
                    // OF-3443: Unlike close(), this also makes the stream non-resumable (OF-2751).
                    session.close( error );
                    return true;
                }
                processResume( resumeRequest );
                return true;
            default:
                return false;
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
    @Override
    @SuppressWarnings("removal") // OF-2425: This method stays when the deprecated one that it overrides in StreamManager is removed.
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
            setNamespace( namespace );

            setResume( resume && offerResume );
            if ( resume && !offerResume ) {
                Log.debug("Peer requested resumption, but session {} may not resume. Enabling Stream Management without resumption.", session);
            }
            if ( getResume() ) {
                // Create SM-ID.
                setSmId( resumption.createId() );
            }
        }

        // Build confirmation element.
        Element enabled = new DOMElement(QName.get("enabled", namespace));
        if (getResume()) {
            enabled.addAttribute("resume", "true");
            enabled.addAttribute("id", getSmId());
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
        setNamespace(request.getNamespace());

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
    @Override
    @SuppressWarnings("removal") // OF-2425: This method stays when the deprecated one that it overrides in StreamManager is removed.
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
        if (!otherStreamManager.getResume()) {
            Log.debug("Not allowing '{}' to resume a session, the session to be resumed does not have the stream management resumption feature enabled.", address);
            return ResumeRequestValidationResult.failure(PacketError.Condition.unexpected_request);
        }
        // Read once, as another thread can disable Stream Management on the other session at any time.
        final String otherNamespace = otherStreamManager.getNamespace();
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
    @Override
    public void onClose(PacketRouter router, JID serverAddress) {
        // Re-deliver unacknowledged stanzas from broken stream (XEP-0198)
        synchronized (this) {
            if(isEnabled()) {
                setNamespace(null); // disable stream management.
                for (final UnacknowledgedStanzaQueue.Entry unacked : getUnacknowledgedEntries()) {
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

    @Override
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
        final Element resumed = new DOMElement(QName.get("resumed", getNamespace()));
        resumed.addAttribute("previd", getSmId());
        resumed.addAttribute("h", Long.toString(getHandledInboundStanzas()));
        return resumed;
    }
}
